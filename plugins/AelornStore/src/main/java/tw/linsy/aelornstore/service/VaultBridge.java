package tw.linsy.aelornstore.service;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Optional access to the server economy.
 *
 * This class is only ever loaded when Vault is actually installed — see
 * {@link #attach} — so a server without Vault never triggers a
 * {@code NoClassDefFoundError} for {@code net.milkbowl.vault}. Products priced in
 * {@code VAULT} simply become unpurchasable, and everything priced in store
 * credit keeps working.
 */
public final class VaultBridge {

    private final Economy economy;

    private VaultBridge(Economy economy) {
        this.economy = economy;
    }

    /** Returns a bridge, or {@code null} when Vault or an economy provider is absent. */
    public static @Nullable VaultBridge attach(Logger logger) {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        try {
            RegisteredServiceProvider<Economy> provider =
                Bukkit.getServicesManager().getRegistration(Economy.class);
            if (provider == null || provider.getProvider() == null) {
                logger.info("已偵測到 Vault，但沒有經濟插件註冊服務；以 VAULT 計價的商品將無法購買。");
                return null;
            }
            return new VaultBridge(provider.getProvider());
        } catch (RuntimeException | NoClassDefFoundError unavailable) {
            logger.log(Level.WARNING, "無法連接 Vault 經濟服務。", unavailable);
            return null;
        }
    }

    public String name() {
        return economy.getName();
    }

    public double balance(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return economy.getBalance(player);
    }

    public boolean has(UUID playerId, double amount) {
        return economy.has(Bukkit.getOfflinePlayer(playerId), amount);
    }

    /** True only when the money actually left the account. */
    public boolean withdraw(UUID playerId, double amount) {
        EconomyResponse response = economy.withdrawPlayer(Bukkit.getOfflinePlayer(playerId), amount);
        return response != null && response.transactionSuccess();
    }

    public boolean deposit(UUID playerId, double amount) {
        EconomyResponse response = economy.depositPlayer(Bukkit.getOfflinePlayer(playerId), amount);
        return response != null && response.transactionSuccess();
    }
}
