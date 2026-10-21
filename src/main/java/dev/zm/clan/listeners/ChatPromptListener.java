package dev.zm.clan.listeners;

import dev.zm.clan.zMClans;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captures the next chat line after a settings GUI action (tag / description).
 */
public final class ChatPromptListener implements Listener {

    public enum PromptType {
        TAG, DESCRIPTION
    }

    private record Prompt(PromptType type, long expiresAt) {
    }

    private final zMClans plugin;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public ChatPromptListener(zMClans plugin) {
        this.plugin = plugin;
    }

    public void begin(Player player, PromptType type) {
        prompts.put(player.getUniqueId(), new Prompt(type, System.currentTimeMillis() + 30_000L));
        player.sendMessage(
                plugin.getMessages().get(type == PromptType.TAG ? "chat-prompt-tag" : "chat-prompt-description"));
    }

    public boolean isPrompting(UUID uuid) {
        Prompt prompt = prompts.get(uuid);
        if (prompt == null)
            return false;
        if (System.currentTimeMillis() > prompt.expiresAt) {
            prompts.remove(uuid);
            return false;
        }
        return true;
    }

    public void clear(UUID uuid) {
        if (uuid != null)
            prompts.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Prompt prompt = prompts.get(player.getUniqueId());
        if (prompt == null)
            return;
        if (System.currentTimeMillis() > prompt.expiresAt) {
            prompts.remove(player.getUniqueId());
            return;
        }

        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline())
                return;
            prompts.remove(player.getUniqueId());
            if (text.equalsIgnoreCase("cancel") || text.equalsIgnoreCase("cancelar")) {
                player.sendMessage(plugin.getMessages().get("chat-prompt-cancelled"));
                return;
            }
            if (prompt.type == PromptType.TAG) {
                player.performCommand("clan modtag " + text);
            } else {
                player.performCommand("clan desc " + text);
            }
        });
    }
}
