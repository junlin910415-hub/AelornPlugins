package tw.linsy.aelorn.plugins.service;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.plugins.audit.AuditLog;
import tw.linsy.aelorn.plugins.config.ManagerSettings;
import tw.linsy.aelorn.plugins.config.SettingsStore;
import tw.linsy.aelorn.plugins.model.JarDescriptor;
import tw.linsy.aelorn.plugins.model.JarFingerprint;
import tw.linsy.aelorn.plugins.platform.MessageCatalog;
import tw.linsy.aelorn.plugins.platform.PlatformProfile;
import tw.linsy.aelorn.plugins.platform.Sched;

/**
 * Noticing that the plugins folder changed, and optionally loading what appeared.
 *
 * <h2>Two detectors, on purpose</h2>
 * A {@link WatchService} reports a dropped-in jar within milliseconds, and a
 * periodic sweep catches what it misses. Both are needed: filesystem events are
 * unreliable on network shares and some container mounts, and they report the write
 * <em>starting</em>, not finishing. So events trigger an immediate sweep and the
 * timer is the safety net, rather than either being trusted alone.
 *
 * <p>The sweep runs on the plugin's own async scheduler, so it is cancelled with the
 * plugin. The previous version ran its own {@code ScheduledExecutorService} and had
 * to remember to shut it down — and on a failed disable, did not.
 *
 * <h2>Waiting for the write to finish</h2>
 * A jar being copied in is visible long before it is complete, and loading a partial
 * jar produces a corrupt-archive error that looks like a broken plugin. So a changed
 * jar must hold the same content hash and be untouched for
 * {@code auto-load-stable-seconds} before it is loaded.
 */
public final class JarWatchService {

    private final Plugin owner;
    private final SettingsStore settings;
    private final MessageCatalog messages;
    private final AuditLog audit;
    private final JarIndex jars;
    private final PlatformProfile platform;
    private final Sched sched;
    private final Logger logger;

    /** Hash per jar file name as of the last sweep; see {@link JarFingerprint}. */
    private volatile Map<String, JarFingerprint> previous = Map.of();

    /** Jars already queued for auto-load, keyed by name and hash, so a retry is idempotent. */
    private final java.util.Set<String> attempted = ConcurrentHashMap.newKeySet();

    /**
     * How many times each jar has been deferred for being unstable.
     *
     * Bounded because the retry is otherwise unconditional: a jar that something keeps
     * rewriting — a build tool syncing on save, a half-finished upload — would be
     * re-queued every few seconds for as long as the server runs, each round writing an
     * audit record. After {@link #MAX_STABILITY_RETRIES} the jar is left alone until it
     * changes again, which the next sweep detects as a new hash.
     */
    private final java.util.Map<String, Integer> deferrals = new ConcurrentHashMap<>();

    /** Roughly a minute of waiting at the default three-second stability window. */
    private static final int MAX_STABILITY_RETRIES = 20;

    private @Nullable ScheduledTask sweepTask;
    private @Nullable WatchService watchService;
    private @Nullable Thread watchThread;

    public JarWatchService(Plugin owner, SettingsStore settings, MessageCatalog messages,
                           AuditLog audit, JarIndex jars,
                           PlatformProfile platform, Sched sched, Logger logger) {
        this.owner = owner;
        this.settings = settings;
        this.messages = messages;
        this.audit = audit;
        this.jars = jars;
        this.platform = platform;
        this.sched = sched;
        this.logger = logger;
    }

    // ── 生命週期 ──────────────────────────────────────────────────────────

    public synchronized void start() {
        stop();
        ManagerSettings.Scanner config = settings.manager().scanner();
        if (!config.watchEnabled()) {
            logger.info("plugins 資料夾監看已停用。");
            return;
        }
        // Baseline first: without it the first sweep reports every installed jar as
        // newly added and, with auto-load on, tries to load all of them.
        previous = jars.fingerprints(Map.of());
        attempted.clear();
        deferrals.clear();

        long seconds = config.watchIntervalSeconds();
        sweepTask = sched.asyncRepeating(this::sweep, seconds, seconds, TimeUnit.SECONDS);
        if (config.useFileEvents()) {
            startFileEvents();
        }
        logger.info("plugins 資料夾監看已啟動（每 " + seconds + " 秒掃描"
            + (config.useFileEvents() && watchThread != null ? " + 檔案事件" : "")
            + "，自動載入=" + config.autoLoadNewJars() + "）。");
    }

    public synchronized void stop() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        // Closing the service is what unblocks the thread's take(); interrupting it
        // alone leaves the native watch handle open.
        WatchService service = watchService;
        watchService = null;
        if (service != null) {
            try {
                service.close();
            } catch (IOException ignored) {
                // Shutting down; the thread exits on ClosedWatchServiceException.
            }
        }
        Thread thread = watchThread;
        watchThread = null;
        if (thread != null) {
            thread.interrupt();
        }
    }

    /** Re-reads settings and restarts both detectors. */
    public void restart() {
        start();
    }

    private void startFileEvents() {
        try {
            WatchService service = FileSystems.getDefault().newWatchService();
            jars.pluginsFolder().register(service,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
            watchService = service;

            Thread thread = new Thread(() -> watchLoop(service), owner.getName() + "-jar-events");
            thread.setDaemon(true);
            thread.start();
            watchThread = thread;
        } catch (IOException | RuntimeException unavailable) {
            // Not fatal: the periodic sweep still detects everything, just later.
            logger.warning("無法監看檔案事件（" + Texts.summarise(unavailable)
                + "），改為只靠定時掃描。");
            watchService = null;
        }
    }

    /**
     * Blocks on filesystem events and asks for a sweep when one arrives.
     *
     * The events themselves are discarded beyond "something changed": a single jar
     * copy produces a burst of CREATE and MODIFY events, and comparing fingerprints
     * once is both cheaper and more accurate than interpreting them.
     */
    private void watchLoop(WatchService service) {
        while (!Thread.currentThread().isInterrupted()) {
            WatchKey key;
            try {
                key = service.take();
            } catch (ClosedWatchServiceException | InterruptedException stopping) {
                return;
            }
            boolean relevant = key.pollEvents().stream()
                .map(event -> String.valueOf(event.context()).toLowerCase(java.util.Locale.ROOT))
                .anyMatch(name -> name.endsWith(".jar"));
            if (!key.reset()) {
                logger.warning("plugins 資料夾的監看鍵已失效，改為只靠定時掃描。");
                return;
            }
            if (relevant) {
                sched.async(this::sweep);
            }
        }
    }

    // ── 掃描 ──────────────────────────────────────────────────────────────

    /**
     * Compares the folder against the last sweep and reports what moved.
     *
     * Runs on an async thread: it is pure IO, and hashing on a region thread would
     * stall a tick for as long as the folder takes to read.
     */
    private void sweep() {
        try {
            Map<String, JarFingerprint> current = jars.fingerprints(previous);
            List<String> changes = new ArrayList<>();
            List<String> loadable = new ArrayList<>();

            for (Map.Entry<String, JarFingerprint> entry : current.entrySet()) {
                JarFingerprint before = previous.get(entry.getKey());
                if (before == null) {
                    changes.add(messages.plain("watch.added", "jar", entry.getKey()));
                    loadable.add(entry.getKey());
                } else if (!before.sha256().equals(entry.getValue().sha256())) {
                    changes.add(messages.plain("watch.changed", "jar", entry.getKey()));
                    loadable.add(entry.getKey());
                }
            }
            for (String name : previous.keySet()) {
                if (!current.containsKey(name)) {
                    changes.add(messages.plain("watch.removed", "jar", name));
                }
            }

            previous = current;
            if (!changes.isEmpty()) {
                String summary = String.join(", ", changes);
                logger.info("偵測到 plugins 資料夾變更：" + summary);
                audit.record("watcher", "scan-change", "plugins", "NOTICE", summary);
            }
            if (settings.manager().scanner().autoLoadNewJars()) {
                for (String name : loadable) {
                    queueAutoLoad(name, current.get(name));
                }
            }
        } catch (RuntimeException failure) {
            // A sweep that throws must not kill the repeating task.
            logger.log(Level.WARNING, "plugins 資料夾掃描失敗。", failure);
        }
    }

    // ── 自動載入 ──────────────────────────────────────────────────────────

    private void queueAutoLoad(String fileName, @Nullable JarFingerprint fingerprint) {
        if (fingerprint == null) {
            return;
        }
        if (fingerprint.unreadable()) {
            audit.record("watcher", "auto-load", fileName, "WAIT",
                messages.plain("audit.auto-load-unreadable"));
            return;
        }
        // Keyed by hash as well as name: a jar replaced twice must be considered
        // twice, but the same content must not be queued repeatedly by every sweep.
        String token = fileName.toLowerCase(java.util.Locale.ROOT) + "@" + fingerprint.sha256();
        if (!attempted.add(token)) {
            return;
        }
        sched.global(() -> autoLoad(fileName, fingerprint, token));
    }

    /**
     * Loads one jar that just appeared, if it is safe to.
     *
     * Runs on the global region because it registers a plugin. The stability and
     * hash re-checks are cheap file reads, kept here rather than on the async thread
     * so the answer cannot go stale between the check and the load.
     */
    private void autoLoad(String fileName, JarFingerprint fingerprint, String token) {
        Path jar = jars.pluginsFolder().resolve(fileName);
        ManagerSettings.Scanner config = settings.manager().scanner();
        try {
            if (!Files.isRegularFile(jar)) {
                return;
            }
            long stableMillis = TimeUnit.SECONDS.toMillis(config.autoLoadStableSeconds());
            long sinceWrite = System.currentTimeMillis() - Files.getLastModifiedTime(jar).toMillis();
            if (sinceWrite < stableMillis) {
                retryLater(fileName, fingerprint, token, config.autoLoadStableSeconds(),
                    "audit.auto-load-unstable");
                return;
            }
            if (!fingerprint.sha256().equals(JarIndex.sha256(jar))) {
                // Still being written: drop the token so the next sweep re-queues it
                // with whatever the content settles on.
                attempted.remove(token);
                audit.record("watcher", "auto-load", fileName, "WAIT",
                    messages.plain("audit.auto-load-changed"));
                return;
            }

            JarDescriptor descriptor = jars.readDescriptor(jar);
            if (!descriptor.hasName()) {
                audit.record("watcher", "auto-load", fileName, "SKIP",
                    messages.plain("audit.auto-load-no-name"));
                logger.warning("略過自動載入 " + fileName + "：描述檔缺少 name。");
                return;
            }
            if (Bukkit.getPluginManager().getPlugin(descriptor.name()) != null) {
                audit.record("watcher", "auto-load", descriptor.name(), "SKIP",
                    messages.plain("audit.auto-load-present"));
                return;
            }
            // Only asked on a regionised server, where a plugin that has not declared
            // it can cope will throw on its first scheduler call. On a single-threaded
            // core the declaration is meaningless and blocking on it would refuse
            // perfectly good plugins.
            if (platform.regionised() && config.autoLoadRegionSafeOnly()
                && !descriptor.foliaSupported()) {
                audit.record("watcher", "auto-load", descriptor.name(), "SKIP",
                    messages.plain("audit.auto-load-not-region-safe"));
                logger.warning("略過自動載入 " + descriptor.name()
                    + "：未宣告 folia-supported: true。");
                return;
            }

            Plugin loaded = Bukkit.getPluginManager().loadPlugin(jar.toFile());
            if (loaded == null) {
                audit.record("watcher", "auto-load", fileName, "FAIL",
                    messages.plain("audit.load-null"));
                logger.warning("自動載入 " + fileName + " 失敗：伺服器回傳 null。");
                return;
            }
            Bukkit.getPluginManager().enablePlugin(loaded);
            audit.record("watcher", "auto-load", loaded.getName(), "SUCCESS", fileName);
            logger.info("已自動載入新插件 " + loaded.getName() + "（" + fileName + "）。");
        } catch (Throwable failure) {
            audit.record("watcher", "auto-load", fileName, "FAIL", Texts.summarise(failure));
            logger.log(Level.WARNING, "自動載入 " + fileName + " 失敗。", failure);
        }
    }

    private void retryLater(String fileName, JarFingerprint fingerprint, String token,
                            long delaySeconds, String reasonKey) {
        int attempt = deferrals.merge(token, 1, Integer::sum);
        if (attempt > MAX_STABILITY_RETRIES) {
            // Left queued (token not removed) so it is not retried again for this
            // content; a genuine new write changes the hash and gets a fresh token.
            audit.record("watcher", "auto-load", fileName, "SKIP",
                messages.plain("audit.auto-load-gave-up"));
            logger.warning("放棄自動載入 " + fileName + "：檔案持續變更，已重試 "
                + MAX_STABILITY_RETRIES + " 次。");
            return;
        }
        attempted.remove(token);
        audit.record("watcher", "auto-load", fileName, "WAIT", messages.plain(reasonKey));
        sched.asyncDelayed(() -> queueAutoLoad(fileName, fingerprint),
            Math.max(1L, delaySeconds), TimeUnit.SECONDS);
    }
}
