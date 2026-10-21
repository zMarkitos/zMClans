package dev.zm.clan.listeners;

import dev.zm.clan.clan.model.Clan;
import dev.zm.clan.config.ColorUtil;
import dev.zm.clan.member.model.ClanMember;
import dev.zm.clan.rank.model.ClanRank;
import dev.zm.clan.zMClans;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles clan chat toggling and message broadcasting.
 * Uses Paper's AsyncChatEvent (Adventure API) — compatible with Paper 1.20+.
 * Supports MiniMessage hover tooltips showing basic user info.
 */
public final class ClanChatListener implements Listener {

    private final zMClans plugin;
    private final Set<UUID> chatToggledPlayers = ConcurrentHashMap.newKeySet();
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public ClanChatListener(zMClans plugin) {
        this.plugin = plugin;
    }

    public boolean toggleChat(UUID uuid) {
        if (chatToggledPlayers.contains(uuid)) {
            chatToggledPlayers.remove(uuid);
            return false;
        } else {
            chatToggledPlayers.add(uuid);
            return true;
        }
    }

    public boolean isInClanChat(UUID uuid) {
        return chatToggledPlayers.contains(uuid);
    }

    public void removePlayer(UUID uuid) {
        chatToggledPlayers.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!isInClanChat(player.getUniqueId()))
            return;
        if (!player.hasPermission("zmclan.player.chat")) {
            chatToggledPlayers.remove(player.getUniqueId());
            return;
        }

        if (plugin.getChatPromptListener() != null
                && plugin.getChatPromptListener().isPrompting(player.getUniqueId())) {
            return;
        }

        Optional<Clan> optClan = plugin.getClanService().findByPlayer(player.getUniqueId());
        if (optClan.isEmpty()) {
            chatToggledPlayers.remove(player.getUniqueId());
            return;
        }
        if (!plugin.getMemberService().hasPermission(player.getUniqueId(), ClanRank.Permission.USE_CHAT)) {
            chatToggledPlayers.remove(player.getUniqueId());
            return;
        }

        String message = ColorUtil.strip(PlainTextComponentSerializer.plainText().serialize(event.message()));
        event.setCancelled(true);
        Clan senderClan = optClan.get();
        // AsyncChatEvent runs off-thread; Bukkit player operations and mutable clan
        // data
        // are handled on the server thread to avoid unsafe API access.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline())
                sendClanMessage(player, senderClan, message);
        });
    }

    public void sendClanMessage(Player sender, Clan clan, String message) {
        if (!sender.hasPermission("zmclan.player.chat")) {
            sender.sendMessage(plugin.getMessages().get("no-permission"));
            return;
        }
        if (!plugin.getMemberService().hasPermission(sender.getUniqueId(), ClanRank.Permission.USE_CHAT)) {
            sender.sendMessage(plugin.getMessages().get("no-permission-chat"));
            return;
        }
        message = ColorUtil.strip(message);
        if (message.isBlank())
            return;

        String format = plugin.getConfigManager().getChatFormat();
        String rankId = plugin.getMemberService().getMember(sender.getUniqueId())
                .map(ClanMember::getRankId)
                .orElseGet(() -> clan.getMember(sender.getUniqueId())
                        .map(ClanMember::getRankId).orElse("member"));
        String rankName = plugin.getRankService().getRank(rankId).map(ClanRank::getDisplayName).orElse(rankId);
        String prefix = plugin.getRankService().getRank(rankId).map(ClanRank::getPrefix).orElse("");

        String formatted = format
                .replace("{tag}", clan.getTag())
                .replace("{clan}", clan.getName())
                .replace("{rank_prefix}", prefix)
                .replace("{rank}", rankName)
                .replace("{player}", sender.getName())
                .replace("{message}", message);

        String colored = ColorUtil.colorize(formatted);
        Component chatComponent = buildChatComponent(colored, sender, clan, rankName, false);

        for (UUID memberUuid : clan.getMemberIds()) {
            Player target = Bukkit.getPlayer(memberUuid);
            if (target != null && target.isOnline()) {
                target.sendMessage(chatComponent);
            }
        }

        if (clan.isAllyChatEnabled()) {
            String allyFormat = plugin.getMessages().get("ally-chat-format");
            String allyFormatted = (allyFormat != null && !allyFormat.isBlank() ? allyFormat
                    : "&d[Ally] &7{clan} {rank} &f{player}&8: &f{message}")
                    .replace("{tag}", clan.getTag())
                    .replace("{clan}", clan.getName())
                    .replace("{rank_prefix}", prefix)
                    .replace("{rank}", rankName)
                    .replace("{player}", sender.getName())
                    .replace("{message}", message);
            Component allyComponent = buildChatComponent(
                    ColorUtil.colorize(allyFormatted), sender, clan, rankName, true);

            java.util.Set<UUID> alreadySent = new java.util.HashSet<>();

            for (UUID allyId : clan.getAllyIds()) {
                if (allyId.equals(clan.getId()))
                    continue;
                Clan ally = plugin.getClanService().findById(allyId).orElse(null);
                if (ally == null || !ally.isAlly(clan.getId()) || !ally.isAllyChatEnabled())
                    continue;
                for (UUID memberUuid : ally.getMemberIds()) {
                    if (!alreadySent.add(memberUuid))
                        continue;
                    Player target = Bukkit.getPlayer(memberUuid);
                    if (target != null && target.isOnline()) {
                        target.sendMessage(allyComponent);
                    }
                }
            }
        }
    }

    private Component buildChatComponent(String legacyText, Player sender, Clan clan, String rankName, boolean isAlly) {
        Component base = LegacyComponentSerializer.legacySection().deserialize(legacyText);

        String hoverKey = isAlly ? "ally-chat-hover-lines" : "chat-hover-lines";
        List<String> lines = plugin.getMessages().getList(hoverKey);
        if (lines == null || lines.isEmpty()) {
            lines = plugin.getMessages().getList("chat-hover-lines");
        }
        String hoverText = (lines != null && !lines.isEmpty())
                ? String.join("\n", lines)
                : plugin.getMessages().get("chat-hover-default");

        hoverText = hoverText
                .replace("{player}", sender.getName())
                .replace("{clan}", clan.getName())
                .replace("{tag}", clan.getTag())
                .replace("{rank}", rankName)
                .replace("{members}", String.valueOf(clan.getMemberCount()))
                .replace("{kdr}", String.format(Locale.ROOT, "%.2f", clan.getKdr()))
                .replace("{kills}", String.valueOf(clan.getKills()))
                .replace("{deaths}", String.valueOf(clan.getDeaths()))
                .replace("{points}", String.valueOf(clan.getPoints()));

        Component hoverContent = LegacyComponentSerializer.legacySection().deserialize(ColorUtil.colorize(hoverText));

        return base.hoverEvent(HoverEvent.showText(hoverContent))
                .clickEvent(ClickEvent.suggestCommand("/clan info " + clan.getName()));
    }
}
