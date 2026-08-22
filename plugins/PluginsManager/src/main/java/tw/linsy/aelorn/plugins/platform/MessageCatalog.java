package tw.linsy.aelorn.plugins.platform;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Every string a player or console operator ever sees.
 *
 * The whole plugin talks to this and nothing else, which is what makes
 * CONVENTIONS.md §4 checkable rather than aspirational: a service names a key, and
 * if the key is missing the admin sees the key name and knows which line to add.
 * Below this class there is no sentence to translate.
 *
 * <p>The bundled copy inside the jar is installed as the file's defaults, so a key
 * added in a later version resolves even when the admin's file predates it. An
 * upgrade never produces a blank line, and admins keep only what they changed.
 *
 * <p>{@link #raw} returns <em>markup</em> rather than a component because report
 * builders concatenate and pad before anything renders. Rendering happens once, at
 * the send site, through the injected {@link Renderer}.
 */
public final class MessageCatalog {

    /** Placeholder pairs are {@code {name}}, matching AelornLib's convention. */
    private static final String PREFIX_TOKEN = "{prefix}";

    private final Plugin owner;
    private final String fileName;
    private final Renderer renderer;

    private volatile YamlConfiguration file;
    private volatile String prefix;

    public MessageCatalog(Plugin owner, String fileName, Renderer renderer) {
        this.owner = owner;
        this.fileName = fileName;
        this.renderer = renderer;
        reload();
    }

    public void reload() {
        File target = new File(owner.getDataFolder(), fileName);
        if (!target.exists() && owner.getResource(fileName) != null) {
            try {
                owner.saveResource(fileName, false);
            } catch (RuntimeException failure) {
                owner.getLogger().log(Level.WARNING,
                    "無法寫出預設 " + fileName + "，將僅使用內建訊息。", failure);
            }
        }
        YamlConfiguration loaded = target.exists()
            ? YamlConfiguration.loadConfiguration(target)
            : new YamlConfiguration();
        YamlConfiguration bundled = bundled();
        if (bundled != null) {
            loaded.setDefaults(bundled);
            // Written back so a key added in a later version appears in the admin's
            // file where they can find and edit it, rather than only existing as an
            // in-memory default they have no way to discover. Their edits survive:
            // copyDefaults only fills in what is absent.
            loaded.options().copyDefaults(true);
            try {
                loaded.save(target);
            } catch (IOException unwritable) {
                owner.getLogger().log(Level.WARNING,
                    "無法補齊 " + fileName + "，將僅在記憶體中使用內建預設值。", unwritable);
            }
        }
        this.file = loaded;
        // Single-argument getString for the same reason as raw(): the two-argument
        // form would bypass the bundled default.
        String configuredPrefix = loaded.getString("prefix");
        this.prefix = configuredPrefix == null ? "" : configuredPrefix;
        renderer.refresh();
    }

    private @Nullable YamlConfiguration bundled() {
        InputStream stream = owner.getResource(fileName);
        if (stream == null) {
            return null;
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException unreadable) {
            owner.getLogger().log(Level.WARNING, "無法讀取內建 " + fileName + "。", unreadable);
            return null;
        }
    }

    /**
     * The configured line with placeholders applied.
     *
     * <p><b>Two-step lookup, and the reason is a trap in Bukkit's config API.</b>
     * {@code getString(key, fallback)} does <em>not</em> consult the configured
     * defaults — {@code MemorySection.get(path, def)} returns the caller's
     * {@code def} the moment the path is absent, and never looks at
     * {@link org.bukkit.configuration.Configuration#getDefaults()}. So the obvious
     * one-liner {@code getString(key, key)} silently defeats
     * {@link #reload}'s {@code setDefaults(bundled)}: on every server upgrading
     * from an older messages.yml, each newly added key rendered as its own key
     * name. The single-argument form is the one that falls through to the bundled
     * copy.
     *
     * <p>The key name remains the last resort — a missing line should be visible
     * in game so it can be reported, rather than producing a blank message nobody
     * can diagnose.
     */
    public String raw(String key, Object... placeholders) {
        String value = file.getString(key);
        return apply(value != null ? value : key, placeholders);
    }

    /** A list-valued key — help blocks, multi-line status — with placeholders applied. */
    public List<String> rawList(String key, Object... placeholders) {
        List<String> lines = file.getStringList(key);
        List<String> resolved = new ArrayList<>(lines.size());
        for (String line : lines) {
            resolved.add(apply(line, placeholders));
        }
        return resolved;
    }

    /** Formatting stripped, for audit records and log lines that must not carry markup. */
    public String plain(String key, Object... placeholders) {
        return renderer.plain(raw(key, placeholders));
    }

    /** Strips an already-resolved markup string; for assembled report lines. */
    public String plainMarkup(String markup) {
        return renderer.plain(markup);
    }

    public boolean has(String key) {
        String value = file.getString(key);
        return value != null && !value.isBlank();
    }

    /** The prefix, so line assembly can apply it without knowing how it is stored. */
    public String prefix() {
        return prefix;
    }

    public Component render(String markup) {
        return renderer.render(markup);
    }

    public String rendererName() {
        return renderer.describe();
    }

    private String apply(String template, Object... placeholders) {
        if (template.isEmpty()) {
            return template;
        }
        String out = template.contains(PREFIX_TOKEN) ? template.replace(PREFIX_TOKEN, prefix) : template;
        for (int index = 0; index + 1 < placeholders.length; index += 2) {
            out = out.replace("{" + placeholders[index] + "}", String.valueOf(placeholders[index + 1]));
        }
        return out;
    }
}
