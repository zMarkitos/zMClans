package dev.zm.clan.service;

import dev.zm.clan.clan.model.Clan;
import dev.zm.clan.config.ColorUtil;
import dev.zm.clan.zMClans;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

/**
 * Handles customizable titles, subtitles, actionbars, and sounds
 * for clan events (create, disband, promote, demote, kick, join, leave, etc).
 */
public final class NotificationService {

    private final zMClans plugin;

    public NotificationService(zMClans plugin) {
        this.plugin = plugin;
    }

    /**
     * Sends a configured notification (title, subtitle, actionbar, and sound) to a
     * player.
     *
     * @param player       target player
     * @param eventKey     configuration key under 'notifications' in config.yml
     * @param replacements flat key-value pairs
     */
    public void sendNotification(Player player, String eventKey, String... replacements) {
        if (player == null || !player.isOnline())
            return;

        ConfigurationSection section = plugin.getConfigManager().getNotifications().getConfigurationSection(eventKey);
        if (section == null)
            return;

        ConfigurationSection titleSec = section.getConfigurationSection("title");
        if (titleSec != null && titleSec.getBoolean("enabled", true)) {
            String titleRaw = titleSec.getString("title", "");
            String subRaw = titleSec.getString("subtitle", "");
            int fadeIn = titleSec.getInt("fade-in", 10);
            int stay = titleSec.getInt("stay", 50);
            int fadeOut = titleSec.getInt("fade-out", 20);

            String title = ColorUtil.colorize(apply(titleRaw, replacements));
            String subtitle = ColorUtil.colorize(apply(subRaw, replacements));
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }

        ConfigurationSection actionSec = section.getConfigurationSection("actionbar");
        if (actionSec != null && actionSec.getBoolean("enabled", true)) {
            String textRaw = actionSec.getString("text", "");
            if (!textRaw.isBlank()) {
                String colored = ColorUtil.colorize(apply(textRaw, replacements));
                player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(colored));
            }
        }

        ConfigurationSection soundSec = section.getConfigurationSection("sound");
        if (soundSec != null && soundSec.getBoolean("enabled", true)) {
            String soundName = soundSec.getString("sound", "");
            float volume = (float) soundSec.getDouble("volume", 1.0);
            float pitch = (float) soundSec.getDouble("pitch", 1.0);
            if (!soundName.isBlank()) {
                try {
                    Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
                    player.playSound(player.getLocation(), sound, volume, pitch);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    /**
     * Sends a configured notification to all online members of a clan.
     */
    public void sendToClan(Clan clan, String eventKey, String... replacements) {
        if (clan == null)
            return;
        for (UUID uuid : clan.getMemberIds()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                sendNotification(p, eventKey, replacements);
            }
        }
    }

    private String apply(String text, String[] replacements) {
        if (text == null || text.isEmpty() || replacements == null || replacements.length == 0)
            return text;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            String key = replacements[i];
            String val = replacements[i + 1];
            if (key != null && val != null) {
                text = text.replace("{" + key + "}", val);
            }
        }
        return text;
    }
}
