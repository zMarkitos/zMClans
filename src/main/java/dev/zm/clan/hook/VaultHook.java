package dev.zm.clan.hook;

import dev.zm.clan.zMClans;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault integration for clan economy (creation fee, bank, etc).
 */
public final class VaultHook {

    private final zMClans plugin;
    private Economy economy;

    public VaultHook(zMClans plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public boolean hasEconomy() {
        if (economy == null) {
            setup();
        }
        return economy != null;
    }

    public Economy getEconomy() {
        if (economy == null) {
            setup();
        }
        return economy;
    }

    public double getBalance(OfflinePlayer player) {
        if (!hasEconomy())
            return 0.0;
        return economy.getBalance(player);
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (!hasEconomy())
            return false;
        return economy.has(player, amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!hasEconomy())
            return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (!hasEconomy())
            return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }
}
