package tw.linsy.aelorn.plugins.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * {@code commands.yml}, parsed once.
 *
 * The command index is the one feature here that can break other plugins on
 * purpose, so it has its own file and its own kill switch rather than sharing
 * config.yml with the harmless read-only features.
 */
public record CommandSettings(boolean enabled,
                              boolean allowUnregister,
                              boolean requireConfirmation,
                              boolean hideDuplicates,
                              int maxLines,
                              boolean showAliases,
                              boolean showPermission) {

    static CommandSettings from(FileConfiguration file) {
        return new CommandSettings(
            file.getBoolean("command-control.enabled", true),
            file.getBoolean("command-control.allow-unregister", true),
            file.getBoolean("command-control.require-confirmation", true),
            file.getBoolean("command-control.hide-duplicates-in-list", true),
            Math.max(1, Math.min(200, file.getInt("display.max-command-lines", 30))),
            file.getBoolean("display.show-aliases", true),
            file.getBoolean("display.show-permission", true));
    }
}
