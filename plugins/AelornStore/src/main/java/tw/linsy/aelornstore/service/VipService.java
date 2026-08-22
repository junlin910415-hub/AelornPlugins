package tw.linsy.aelornstore.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelornstore.AelornStorePlugin;
import tw.linsy.aelornstore.config.Catalog;
import tw.linsy.aelornstore.config.StoreSettings;
import tw.linsy.aelornstore.db.StoreDao;
import tw.linsy.aelornstore.model.VipRecord;
import tw.linsy.aelornstore.model.VipTier;
import tw.linsy.aelornstore.util.AuditLog;
import tw.linsy.aelornstore.util.Clock;

/**
 * VIP membership: granting, upgrading, extending and expiring.
 *
 * A player holds at most one tier at a time. Buying a different tier does not
 * stack a second membership — it either upgrades (converting unused days at the
 * ratio of the two tiers' prices) or extends, according to
 * {@code vip.upgrade-policy}. That single-row model is what lets an expiry sweep
 * and a refund both know exactly which permissions to take back.
 *
 * <p>All methods block on the database and must be called off the main thread.
 */
public final class VipService {

    /** {@code days <= 0} on a grant means the membership never expires. */
    private static final long PERMANENT = 0L;

    private final AelornStorePlugin plugin;
    private final StoreDao dao;
    private final ActionRunner actions;
    private final AuditLog audit;
    private final Clock clock;

    public VipService(AelornStorePlugin plugin, StoreDao dao, ActionRunner actions,
                      AuditLog audit, Clock clock) {
        this.plugin = plugin;
        this.dao = dao;
        this.actions = actions;
        this.audit = audit;
        this.clock = clock;
    }

    public Optional<VipRecord> current(UUID playerId) throws SQLException {
        Optional<VipRecord> found = dao.findVip(playerId);
        return found.filter(record -> record.active(clock.now()));
    }

    /** Resolved display name, or an empty string when the player has no active tier. */
    public String displayName(@Nullable VipRecord record) {
        if (record == null) {
            return "";
        }
        VipTier tier = plugin.catalog().tier(record.tierId());
        return tier == null ? record.tierId() : tier.displayName();
    }

    /**
     * Grants or extends a membership.
     *
     * @param days the length bought; {@code <= 0} grants a permanent membership
     * @return the resulting record, or empty when the tier id was unknown
     */
    public Optional<VipRecord> grant(UUID playerId, String playerName, String tierId,
                                     long days, @Nullable String sourceOrder) throws SQLException {
        Catalog catalog = plugin.catalog();
        VipTier target = catalog.tier(tierId);
        if (target == null) {
            plugin.getLogger().warning("VIP 等級「" + tierId + "」不存在（訂單 " + sourceOrder + "），已略過。");
            return Optional.empty();
        }
        long now = clock.now();
        StoreSettings.UpgradePolicy policy = plugin.settings().vip().upgradePolicy();

        Optional<VipRecord> existing = dao.findVip(playerId);
        VipTier resolvedTier = target;
        long expiresAt;
        VipTier tierToRevoke = null;

        if (existing.isPresent() && existing.get().active(now)) {
            VipRecord held = existing.get();
            VipTier heldTier = catalog.tier(held.tierId());
            if (held.permanent()) {
                // Nothing bought can improve on permanent; extend nothing, keep the tier.
                resolvedTier = heldTier == null ? target : heldTier;
                expiresAt = PERMANENT;
            } else if (heldTier == null || heldTier.id().equals(target.id())) {
                expiresAt = days <= 0 ? PERMANENT : held.expiresAt() + Clock.daysToMillis(days);
            } else if (target.weight() > heldTier.weight() && policy == StoreSettings.UpgradePolicy.UPGRADE) {
                long carried = heldTier.convertDays(held.daysRemaining(now), target);
                expiresAt = days <= 0 ? PERMANENT : now + Clock.daysToMillis(days + carried);
                tierToRevoke = heldTier;
            } else if (target.weight() > heldTier.weight()) {
                // STACK policy: money buys time on the tier already held, not a jump.
                long carried = target.convertDays(days, heldTier);
                resolvedTier = heldTier;
                expiresAt = held.expiresAt() + Clock.daysToMillis(carried);
            } else {
                // Bought a cheaper tier while holding a dearer one: convert into time
                // on the dearer one rather than silently downgrading them.
                long carried = target.convertDays(days, heldTier);
                resolvedTier = heldTier;
                expiresAt = held.expiresAt() + Clock.daysToMillis(carried);
            }
        } else {
            expiresAt = days <= 0 ? PERMANENT : now + Clock.daysToMillis(days);
        }

        VipRecord record = new VipRecord(playerId, resolvedTier.id(), expiresAt, now, sourceOrder);
        VipTier revoke = tierToRevoke;
        VipTier granted = resolvedTier;
        dao.database().transaction(connection -> {
            dao.saveVip(connection, record);
            audit.record(connection, sourceOrder == null ? "SYSTEM" : sourceOrder, "VIP_GRANT",
                playerId.toString(),
                "tier=" + granted.id() + " days=" + days + " expires=" + expiresAt, now);
            return null;
        });

        ActionRunner.Context context = new ActionRunner.Context(playerId, playerName,
            sourceOrder == null ? "-" : sourceOrder, granted.id(), 1, 0L, 0L);
        // Strip the old rank's permissions before adding the new ones, so an
        // upgrade never leaves the player holding both groups.
        if (revoke != null) {
            actions.run(revoke.onExpire(), context);
        }
        actions.run(granted.onGrant(), context);
        return Optional.of(record);
    }

    /** Ends a membership now, running the tier's revoke actions. */
    public boolean revoke(UUID playerId, String playerName, String actor) throws SQLException {
        Optional<VipRecord> existing = dao.findVip(playerId);
        if (existing.isEmpty()) {
            return false;
        }
        VipRecord held = existing.get();
        long now = clock.now();
        dao.database().transaction(connection -> {
            dao.deleteVip(connection, playerId);
            audit.record(connection, actor, "VIP_REVOKE", playerId.toString(),
                "tier=" + held.tierId() + " source=" + held.sourceOrder(), now);
            return null;
        });
        VipTier tier = plugin.catalog().tier(held.tierId());
        if (tier != null) {
            actions.run(tier.onExpire(), new ActionRunner.Context(playerId, playerName,
                held.sourceOrder() == null ? "-" : held.sourceOrder(), tier.id(), 1, 0L, 0L));
        }
        return true;
    }

    /**
     * Expires everything past its date. Runs on the async poller.
     *
     * @return how many memberships ended
     */
    public int sweepExpired(int limit) {
        int ended = 0;
        try {
            long now = clock.now();
            List<VipRecord> expired = dao.expiredVips(now, limit);
            for (VipRecord record : expired) {
                String name = resolveName(record.playerId());
                if (revoke(record.playerId(), name, "EXPIRY")) {
                    ended++;
                    notifyExpired(record);
                }
            }
        } catch (SQLException failure) {
            plugin.getLogger().log(Level.WARNING, "VIP 到期掃描失敗。", failure);
        }
        return ended;
    }

    private void notifyExpired(VipRecord record) {
        Player player = Bukkit.getPlayer(record.playerId());
        if (player == null) {
            return;
        }
        String tierName = displayName(record);
        player.getScheduler().execute(plugin,
            () -> plugin.messages().send(player, "vip.expired", "tier", tierName), null, 1L);
    }

    /** Warns a player whose membership is close to lapsing; called on join. */
    public void warnIfExpiringSoon(Player player) {
        try {
            Optional<VipRecord> held = current(player.getUniqueId());
            if (held.isEmpty() || held.get().permanent()) {
                return;
            }
            long daysLeft = held.get().daysRemaining(clock.now());
            if (!plugin.settings().vip().expireWarnDays().contains((int) daysLeft)) {
                return;
            }
            String tierName = displayName(held.get());
            String expires = clock.format(held.get().expiresAt());
            player.getScheduler().execute(plugin, () -> plugin.messages().send(player,
                "vip.expiring-soon", "tier", tierName, "days", daysLeft, "expires", expires), null, 1L);
        } catch (SQLException failure) {
            plugin.getLogger().log(Level.WARNING, "檢查 VIP 到期提醒失敗。", failure);
        }
    }

    private String resolveName(UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        String cached = Bukkit.getOfflinePlayer(playerId).getName();
        return cached == null ? playerId.toString() : cached;
    }
}
