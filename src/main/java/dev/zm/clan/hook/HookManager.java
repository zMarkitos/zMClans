package dev.zm.clan.hook;

import dev.zm.clan.zMClans;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Manages soft dependencies and integrations.
 */
public final class HookManager {

    private final zMClans plugin;
    private VaultHook vaultHook;
    private boolean papiPresent;

    public HookManager(zMClans plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        // Vault hook
        this.vaultHook = new VaultHook(plugin);
        if (vaultHook.setup()) {
            plugin.getLogger().info("Hooked into Vault (Economy).");
        } else {
            plugin.getLogger().info("Vault not found or no economy provider present. Economy features disabled.");
        }

        // PlaceholderAPI hook
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            ClanPlaceholderExpansion placeholders = new ClanPlaceholderExpansion(plugin);
            placeholders.register();
            this.papiPresent = true;
            plugin.getLogger().info("Hooked into PlaceholderAPI (registered %zmclans_% and %rel_zmclans_relation_%).");
        }
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public boolean isPapiPresent() {
        return papiPresent;
    }

    /**
     * Parses PlaceholderAPI placeholders on map values if PAPI is enabled.
     */
    public void parsePapi(Player player, Map<String, String> placeholders) {
        if (!papiPresent || player == null || placeholders == null)
            return;
        placeholders.replaceAll((k, v) -> PlaceholderAPI.setPlaceholders(player, v));
    }
}
