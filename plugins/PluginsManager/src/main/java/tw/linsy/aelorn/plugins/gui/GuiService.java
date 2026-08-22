package tw.linsy.aelorn.plugins.gui;

import org.bukkit.entity.Player;

/**
 * The menu interface to plugin management.
 *
 * <p><b>No AelornLib type appears in this signature, deliberately.</b> The menu
 * framework lives in {@code core.ui}, which only exists when the core is installed,
 * and this plugin is a {@code softdepend} precisely so it still runs when the core is
 * not. Naming a core type here would drag it onto the class path of every server —
 * the same rule that keeps {@code CoreSched} and {@code CoreRenderer} isolated.
 *
 * <p>So this interface is the seam: {@link Unavailable} on a server with no core, and
 * the core-backed implementation everywhere else, chosen once at enable.
 *
 * <h2>The GUI is not a second way in</h2>
 * Every destructive action a menu offers runs through the same services and the same
 * {@code OperationGuards} as the command that does it. A menu that reached the plugin
 * manager by another path would be a second set of protection rules to keep in sync,
 * and the one that gets forgotten is always the one nobody is looking at.
 *
 * <p>What replaces {@code --confirm} is a second screen, not a weaker check: the guard
 * still refuses an unconfirmed call, and the confirmation screen is what supplies the
 * confirmation.
 */
public interface GuiService {

    /** False when the menu cannot be opened here; {@link #unavailableReason} says why. */
    boolean available();

    /** A message key explaining why the menu is unavailable. */
    String unavailableReason();

    /**
     * Opens the plugin overview.
     *
     * @param actor recorded in the audit trail for anything the viewer then does,
     *              passed in rather than derived so the menu and the command produce
     *              identical records for identical operations
     */
    void openOverview(Player viewer, String actor);

    /** Used when AelornLib is absent, so the command can explain rather than fail. */
    final class Unavailable implements GuiService {

        private final String reasonKey;

        public Unavailable(String reasonKey) {
            this.reasonKey = reasonKey;
        }

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public String unavailableReason() {
            return reasonKey;
        }

        @Override
        public void openOverview(Player viewer, String actor) {
            throw new IllegalStateException("GUI 不可用時不應該被開啟；呼叫端要先檢查 available()。");
        }
    }
}
