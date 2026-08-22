package tw.linsy.aelorn.plugins.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.plugins.audit.AuditLog;
import tw.linsy.aelorn.plugins.config.SettingsStore;
import tw.linsy.aelorn.plugins.model.JarDescriptor;
import tw.linsy.aelorn.plugins.model.PluginRef;
import tw.linsy.aelorn.plugins.model.Reply;
import tw.linsy.aelorn.plugins.model.ScanEntry;
import tw.linsy.aelorn.plugins.model.ScanReport;
import tw.linsy.aelorn.plugins.platform.MessageCatalog;
import tw.linsy.aelorn.plugins.platform.Platform;

/**
 * The read-only commands: status, list, info, scan, audit.
 *
 * Separated from the services that change things because the split is real —
 * nothing here needs a guard, an audit record, or the global region, and grouping
 * them made it obvious that {@code list} and {@code scan} were the only commands
 * paying for a confirmation check they could never fail.
 *
 * <p>Every line comes from a key in messages.yml. This class is where the previous
 * version's largest concentration of hard-coded Chinese lived: seven multi-line
 * report builders with colour codes and labels compiled into string concatenation,
 * which is why changing the word "受保護" used to need a rebuild.
 */
public final class ReportService {

    private final Platform platform;
    private final SettingsStore settings;
    private final MessageCatalog messages;
    private final AuditLog audit;
    private final PluginLookup lookup;
    private final ProtectionService protection;
    private final DependencyService dependencies;
    private final JarIndex jars;

    public ReportService(Platform platform, SettingsStore settings, MessageCatalog messages,
                         AuditLog audit, PluginLookup lookup, ProtectionService protection,
                         DependencyService dependencies, JarIndex jars) {
        this.platform = platform;
        this.settings = settings;
        this.messages = messages;
        this.audit = audit;
        this.lookup = lookup;
        this.protection = protection;
        this.dependencies = dependencies;
        this.jars = jars;
    }

    // ── 狀態 ──────────────────────────────────────────────────────────────

    /**
     * What this plugin decided about its surroundings, plus the headline counts.
     *
     * Deliberately verbose about the platform: when something behaves unexpectedly
     * the first question is which scheduler, renderer and internals adapter are in
     * use, and having to read the startup log to find out is why this report exists.
     */
    public Reply status() {
        Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
        long enabled = 0;
        for (Plugin plugin : plugins) {
            if (plugin.isEnabled()) {
                enabled++;
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add(messages.raw("status.header"));
        lines.add(messages.raw("status.server",
            "name", platform.profile().serverName(),
            "version", platform.profile().serverVersion()));
        lines.add(messages.raw("status.threading",
            "model", messages.raw(platform.profile().regionised()
                ? "status.threading-regionised" : "status.threading-single")));
        lines.add(messages.raw("status.core",
            "state", messages.raw(platform.coreBacked() ? "status.core-yes" : "status.core-no"),
            "scheduler", platform.sched().describe(),
            "renderer", messages.rendererName()));
        lines.add(messages.raw("status.internals", "detail", platform.internals().describe()));
        lines.add(messages.raw("status.plugins", "enabled", enabled, "total", plugins.length));
        lines.add(messages.raw("status.protected", "count", protection.effectiveNames().size()));
        lines.add(messages.raw("status.groups", "count", settings.groups().groups().size()));
        lines.add(messages.raw("status.files", "count", settings.managedFileNames().size()));
        lines.add(messages.raw("status.confirmation",
            "value", yesNo(settings.manager().guards().requireConfirmation())));
        lines.add(messages.raw("status.unload",
            "value", yesNo(settings.manager().guards().allowUnload())));
        lines.add(messages.raw("status.audit", "storage", audit.describe()));
        return Reply.ok(lines);
    }

    // ── 清單 ──────────────────────────────────────────────────────────────

    /**
     * Loaded plugins, filtered and paginated.
     *
     * @param page 1-based; clamped into range rather than rejected, so paging past
     *             the end shows the last page instead of an error
     */
    public Reply list(@Nullable String filter, int page) {
        String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        List<Plugin> matching = new ArrayList<>();
        for (Plugin plugin : lookup.allSorted()) {
            if (needle.isEmpty() || plugin.getName().toLowerCase(Locale.ROOT).contains(needle)) {
                matching.add(plugin);
            }
        }
        int pageSize = settings.manager().display().pageSize();
        int pages = Math.max(1, (int) Math.ceil((double) matching.size() / pageSize));
        int current = Math.max(1, Math.min(page, pages));
        int from = Math.min((current - 1) * pageSize, matching.size());
        int to = Math.min(from + pageSize, matching.size());

        List<String> lines = new ArrayList<>();
        lines.add(messages.raw("list.header",
            "page", current, "pages", pages, "count", matching.size()));
        for (Plugin plugin : matching.subList(from, to)) {
            lines.add(messages.raw("list.row",
                "state", stateOf(plugin),
                "plugin", plugin.getName(),
                "version", plugin.getPluginMeta().getVersion(),
                "protected", protection.isProtected(plugin) ? messages.raw("state.protected") : "",
                "region", plugin.getPluginMeta().isFoliaSupported()
                    ? messages.raw("state.region-safe") : ""));
        }
        if (matching.isEmpty()) {
            lines.add(messages.raw("list.empty"));
        }
        return Reply.ok(lines);
    }

    // ── 詳情 ──────────────────────────────────────────────────────────────

    public Reply info(@Nullable String query) {
        PluginRef ref = lookup.resolve(query);
        if (!ref.resolved()) {
            return PluginLookup.unresolved(messages, ref, query);
        }
        Plugin plugin = ref.require();
        var meta = plugin.getPluginMeta();
        Path jar = PluginLookup.jarOf(plugin);

        List<String> lines = new ArrayList<>();
        lines.add(messages.raw("info.header", "plugin", plugin.getName()));
        lines.add(messages.raw("info.state", "state", stateOf(plugin)));
        lines.add(messages.raw("info.version", "version", meta.getVersion()));
        lines.add(messages.raw("info.main", "main", meta.getMainClass()));
        lines.add(messages.raw("info.api", "api", display(meta.getAPIVersion())));
        lines.add(messages.raw("info.region-safe", "value", yesNo(meta.isFoliaSupported())));
        lines.add(messages.raw("info.authors", "authors", Texts.join(messages, meta.getAuthors())));
        lines.add(messages.raw("info.provides", "provides",
            Texts.join(messages, meta.getProvidedPlugins())));
        lines.add(messages.raw("info.depends", "plugins",
            Texts.join(messages, DependencyService.declaredHard(plugin))));
        lines.add(messages.raw("info.soft-depends", "plugins",
            Texts.join(messages, DependencyService.declaredSoft(plugin))));
        lines.add(messages.raw("info.dependants", "plugins",
            Texts.join(messages, dependencies.dependantNames(plugin, true, true))));
        lines.add(messages.raw("info.soft-dependants", "plugins",
            Texts.join(messages, dependencies.dependantNames(plugin, false, true))));
        lines.add(messages.raw("info.protected", "value", yesNo(protection.isProtected(plugin))));
        lines.add(messages.raw("info.jar", "jar",
            jar == null ? messages.raw("common.unknown") : jar.getFileName().toString()));
        return Reply.ok(lines);
    }

    // ── 掃描 ──────────────────────────────────────────────────────────────

    /**
     * Formats a scan report.
     *
     * The scan itself happens on an async thread and is passed in, rather than being
     * run here: hashing every jar in the folder is the one read-only operation in
     * this class that must not touch a region thread.
     */
    public Reply scan(ScanReport report, @Nullable String filter) {
        List<ScanEntry> matching = report.matching(filter);
        int limit = settings.manager().scanner().maxScanLines();

        List<String> lines = new ArrayList<>();
        lines.add(messages.raw("scan.header",
            "shown", matching.size(), "total", report.entries().size(),
            "errors", report.errors().size(), "duplicates", report.duplicateNames().size()));
        int shown = Math.min(limit, matching.size());
        for (int index = 0; index < shown; index++) {
            ScanEntry entry = matching.get(index);
            JarDescriptor descriptor = entry.descriptor();
            lines.add(messages.raw("scan.row",
                "state", messages.raw(entry.loaded() ? "state.loaded" : "state.not-loaded"),
                "plugin", display(descriptor.name()),
                "version", display(descriptor.version()),
                "jar", entry.fileName(),
                "descriptor", descriptor.source().fileName(),
                "region", messages.raw(descriptor.foliaSupported()
                    ? "state.region-safe" : "state.region-unknown"),
                "hash", entry.fingerprint().shortHash()));
        }
        if (matching.size() > shown) {
            lines.add(messages.raw("common.truncated", "remaining", matching.size() - shown));
        }
        for (Map.Entry<String, List<String>> duplicate : report.duplicateNames().entrySet()) {
            lines.add(messages.raw("scan.duplicate",
                "plugin", duplicate.getKey(),
                "jars", Texts.join(messages, duplicate.getValue())));
        }
        // Errors are capped: a folder of unreadable jars would otherwise push the
        // listing itself out of the chat buffer.
        List<String> errors = report.errors();
        for (int index = 0; index < Math.min(3, errors.size()); index++) {
            lines.add(messages.raw("scan.error", "detail", errors.get(index)));
        }
        if (errors.size() > 3) {
            lines.add(messages.raw("scan.more-errors", "remaining", errors.size() - 3));
        }
        return Reply.ok(lines);
    }

    /** Runs the scan; call from an async thread. */
    public ScanReport runScan() {
        return jars.scan(lookup.loadedNames());
    }

    // ── 稽核 ──────────────────────────────────────────────────────────────

    /** The audit tail; call from an async thread, since it reads a file. */
    public Reply auditTail(int requested) {
        int count = Math.min(Math.max(1, requested), settings.manager().audit().maxTailLines());
        List<String> entries = audit.tail(count);
        if (entries.isEmpty()) {
            return Reply.fail(messages.raw("audit.empty"));
        }
        List<String> lines = new ArrayList<>();
        lines.add(messages.raw("audit.header", "count", entries.size()));
        for (String entry : entries) {
            lines.add(messages.raw("audit.row", "entry", entry));
        }
        return Reply.ok(lines);
    }

    // ── 設定 ──────────────────────────────────────────────────────────────

    public Reply configCheck() {
        List<String> lines = new ArrayList<>();
        lines.add(messages.raw("config.check-header"));
        lines.addAll(settings.validationReport());
        return Reply.ok(lines);
    }

    // ── 共用 ──────────────────────────────────────────────────────────────

    private String stateOf(Plugin plugin) {
        return messages.raw(plugin.isEnabled() ? "state.enabled" : "state.disabled");
    }

    private String yesNo(boolean value) {
        return messages.raw(value ? "common.yes-label" : "common.no-label");
    }

    private String display(@Nullable String value) {
        return value == null || value.isBlank() ? messages.raw("common.unknown") : value;
    }
}
