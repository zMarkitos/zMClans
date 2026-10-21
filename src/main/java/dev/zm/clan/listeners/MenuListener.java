package dev.zm.clan.listeners;

import dev.zm.clan.clan.model.Clan;
import dev.zm.clan.command.PermissionNodes;
import dev.zm.clan.invitation.model.Invitation;
import dev.zm.clan.member.model.ClanMember;
import dev.zm.clan.menu.ActiveMenu;
import dev.zm.clan.menu.ItemConfig;
import dev.zm.clan.menu.MenuHolder;
import dev.zm.clan.rank.model.ClanRank;
import dev.zm.clan.service.HomeService;
import dev.zm.clan.service.InviteService;
import dev.zm.clan.service.MemberService;
import dev.zm.clan.service.RelationService;
import dev.zm.clan.zMClans;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.*;

/**
 * Handles interactions inside all zMClans GUI menus.
 * Fully protects menus from drag/item exploits, routes all button clicks,
 * and uses localized messages without hardcoding.
 */
public final class MenuListener implements Listener {

    private final zMClans plugin;

    public MenuListener(zMClans plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }

        // Cancel all clicks in and around the menu to avoid exploits
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        boolean rightClick = event.isRightClick();
        ActiveMenu activeMenu = plugin.getMenuManager().getOpenMenu(player).orElse(null);
        if (activeMenu != null && "clan_homes".equals(activeMenu.getMenuKey())
                && (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP)) {
            Object homeData = holder.getSlotData(slot);
            if (homeData instanceof String homeName) {
                if (plugin.getHomeService().deleteHome(player, homeName)) {
                    player.sendMessage(plugin.getMessages().get("home-deleted", "home", homeName));
                    plugin.getMenuManager().openHomesMenu(player, holder.getPage());
                } else
                    player.sendMessage(plugin.getMessages().get("home-delete-failed", "home", homeName));
            }
            return;
        }

        Object data = holder.getSlotData(slot);
        if (data != null) {
            ActiveMenu active = plugin.getMenuManager().getOpenMenu(player).orElse(null);
            String menuKey = active == null ? holder.getConfig().getKey() : active.getMenuKey();
            if (!checkPermission(player, PermissionNodes.forDynamicMenuClick(data, rightClick, menuKey)))
                return;
            handleDynamicSlotClick(player, holder, data, rightClick);
            return;
        }

        ItemConfig itemConfig = holder.getSlotAction(slot);
        if (itemConfig == null) {
            return;
        }

        if (itemConfig.getRequirePerm() != null && !itemConfig.getRequirePerm().isBlank()
                && !player.hasPermission(itemConfig.getRequirePerm())) {
            player.sendMessage(plugin.getMessages().get("gui-action-no-permission"));
            return;
        }
        if (itemConfig.getRequireRankPerm() != null && !itemConfig.getRequireRankPerm().isBlank()) {
            try {
                ClanRank.Permission rankPermission = ClanRank.Permission
                        .valueOf(itemConfig.getRequireRankPerm().toUpperCase(Locale.ROOT));
                if (!plugin.getMemberService().hasPermission(player.getUniqueId(), rankPermission)) {
                    player.sendMessage(plugin.getMessages().get("gui-action-no-permission"));
                    return;
                }
            } catch (IllegalArgumentException exception) {
                player.sendMessage(plugin.getMessages().get("gui-action-no-permission"));
                return;
            }
        }

        String action = itemConfig.getAction();
        if (action == null || action.isBlank() || "NONE".equalsIgnoreCase(action)) {
            return;
        }

        handleAction(player, holder, action, rightClick);
    }

    private void handleDynamicSlotClick(Player player, MenuHolder holder, Object data, boolean rightClick) {
        if (data instanceof dev.zm.clan.menu.AllyHomeEntry allyHome) {
            Clan own = plugin.getClanService().findByPlayer(player.getUniqueId()).orElse(null);
            Clan ally = plugin.getClanService().findById(allyHome.clanId()).orElse(null);
            if (own == null || ally == null || !own.isAlly(ally.getId()) || !ally.isAlly(own.getId())
                    || !ally.isAllyHomesVisible()) {
                player.sendMessage(plugin.getMessages().get("ally-home-unavailable"));
                plugin.getMenuManager().openAllyHomesMenu(player, holder.getPage());
                return;
            }
            HomeService.TeleportResult result = plugin.getHomeService().teleportSharedHome(player, allyHome.clanId(),
                    allyHome.homeName());
            switch (result) {
                case NO_PERMISSION -> player.sendMessage(plugin.getMessages().get("no-permission-home"));
                case NO_CLAN -> player.sendMessage(plugin.getMessages().get("no-clan"));
                case NO_HOME -> player.sendMessage(plugin.getMessages().get("ally-home-unavailable"));
                case WORLD_UNLOADED -> player.sendMessage(plugin.getMessages().get("home-world-unloaded"));
                default -> {
                }
            }
            player.closeInventory();
            return;
        }
        // Home entry: left-click teleports; right-click deletes (Q is also supported).
        if (data instanceof String homeName) {
            if (rightClick) {
                if (plugin.getHomeService().deleteHome(player, homeName)) {
                    player.sendMessage(plugin.getMessages().get("home-deleted", "home", homeName));
                    plugin.getMenuManager().openHomesMenu(player, holder.getPage());
                } else
                    player.sendMessage(plugin.getMessages().get("home-delete-failed", "home", homeName));
            } else {
                HomeService.TeleportResult result = plugin.getHomeService().teleport(player, homeName);
                switch (result) {
                    case NO_CLAN -> player.sendMessage(plugin.getMessages().get("no-clan"));
                    case NO_HOME -> player.sendMessage(plugin.getMessages().get("home-not-set"));
                    case WORLD_UNLOADED -> player.sendMessage(plugin.getMessages().get("home-world-unloaded"));
                    case NO_PERMISSION -> player.sendMessage(plugin.getMessages().get("no-permission-home"));
                    default -> {
                    }
                }
            }
            return;
        }
        if (data instanceof ClanMember member) {
            Clan own = plugin.getClanService().findByPlayer(player.getUniqueId()).orElse(null);
            if (own == null || !own.getId().equals(member.getClanId())) {
                player.sendMessage(plugin.getMessages().get("no-permission"));
                return;
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            plugin.getMenuManager().openMemberManage(player, member.getPlayerUUID());
            return;
        }

        // B) Clicked a player in clan_invite (to invite them)
        if (data instanceof UUID targetPlayerUuid) {
            Player target = Bukkit.getPlayer(targetPlayerUuid);
            if (target == null || !target.isOnline()) {
                player.sendMessage(plugin.getMessages().get("player-not-found", "player", "Unknown"));
                return;
            }

            InviteService.InviteResult result = plugin.getInviteService().sendInvite(player.getUniqueId(),
                    targetPlayerUuid);
            switch (result) {
                case OK -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    player.sendMessage(plugin.getMessages().get("invite-sent", "player", target.getName()));
                    plugin.getClanService().findByPlayer(player.getUniqueId()).ifPresent(clan -> {
                        target.sendMessage(plugin.getMessages().get("invite-received", "clan", clan.getName(), "sender",
                                player.getName()));
                    });
                    // Refresh invite menu
                    plugin.getMenuManager().openInviteMenu(player, holder.getPage());
                }
                case NO_PERMISSION -> player.sendMessage(plugin.getMessages().get("no-permission"));
                case ALREADY_HAS_CLAN ->
                    player.sendMessage(plugin.getMessages().get("invite-player-has-clan", "player", target.getName()));
                case ALREADY_INVITED ->
                    player.sendMessage(plugin.getMessages().get("invite-already-sent", "player", target.getName()));
                case CLAN_FULL -> {
                    Clan c = plugin.getClanService().findByPlayer(player.getUniqueId()).orElse(null);
                    String cur = c != null ? String.valueOf(c.getMemberCount()) : "0";
                    player.sendMessage(plugin.getMessages().get("invite-clan-full", "amount", cur, "max",
                            String.valueOf(plugin.getMemberService().getMaxMembers(c))));
                }
                default -> player.sendMessage(plugin.getMessages().get("player-not-found", "player", target.getName()));
            }
            return;
        }

        // C) Clicked an invitation in clan_invites
        if (data instanceof Invitation inv) {
            if (rightClick) {
                // Deny invitation
                plugin.getInviteService().deny(player.getUniqueId(), inv.getClanId());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                plugin.getClanService().findById(inv.getClanId()).ifPresentOrElse(
                        clan -> player.sendMessage(
                                plugin.getMessages().get("invite-denied-receiver", "clan", clan.getName())),
                        () -> player.sendMessage(plugin.getMessages().get("invite-denied")));
                plugin.getMenuManager().openInvitesMenu(player, holder.getPage());
            } else {
                // Accept invitation
                if (plugin.getInviteService().accept(player.getUniqueId(), inv.getClanId())) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    Clan clan = plugin.getClanService().findById(inv.getClanId()).orElse(null);
                    player.sendMessage(plugin.getMessages().get("invite-accepted-receiver", "clan",
                            clan != null ? clan.getName() : ""));
                    plugin.getMenuManager().openMainMenu(player);
                } else {
                    player.sendMessage(plugin.getMessages().get("invite-expired", "clan", ""));
                    plugin.getMenuManager().openInvitesMenu(player, holder.getPage());
                }
            }
            return;
        }

        // D) Clicked a clan in clan_list
        if (data instanceof Clan clan) {
            ActiveMenu active = plugin.getMenuManager().getOpenMenu(player).orElse(null);
            String menuKey = active == null ? "" : active.getMenuKey();
            if ("clan_invites".equals(menuKey)) {
                handlePublicJoinClick(player, clan);
                return;
            }
            if ("clan_allies".equals(menuKey) || "clan_rivals".equals(menuKey)) {
                handleRelationClick(player, holder, clan, menuKey);
            } else {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                plugin.getMenuManager().openClanInfo(player, clan, "clan_list");
            }
            return;
        }
    }

    private void handleAction(Player player, MenuHolder holder, String action, boolean rightClick) {
        ActiveMenu currentMenu = plugin.getMenuManager().getOpenMenu(player).orElse(null);
        String menuKey = currentMenu == null ? holder.getConfig().getKey() : currentMenu.getMenuKey();
        String requiredPermission = PermissionNodes.forMenuAction(action, rightClick, menuKey);
        if (("CONFIRM".equalsIgnoreCase(action) || "CONFIRM_ACTION".equalsIgnoreCase(action))
                && currentMenu != null) {
            requiredPermission = currentMenu.getConfirmPermission();
        }
        if (!checkPermission(player, requiredPermission))
            return;

        if ("CLOSE".equalsIgnoreCase(action)) {
            player.closeInventory();
            return;
        }

        if ("OPEN_CLANS".equalsIgnoreCase(action)) {
            plugin.getMenuManager().openClanList(player, 0);
            return;
        }
        if ("TOP_TOGGLE".equalsIgnoreCase(action)) {
            plugin.getMenuManager().toggleLeaderboardMode(player);
            return;
        }

        if ("CREATE_CLAN".equalsIgnoreCase(action)) {
            player.closeInventory();
            player.sendMessage(plugin.getMessages().get("gui-create-clan-instruction"));
            return;
        }

        if ("SHOW_HELP".equalsIgnoreCase(action)) {
            player.closeInventory();
            player.performCommand("clan help");
            return;
        }

        if (action.startsWith("CMD_PLAYER:")) {
            player.closeInventory();
            String cmd = action.substring("CMD_PLAYER:".length()).trim().replaceFirst("^/", "");
            player.performCommand(cmd);
            return;
        }

        if (action.startsWith("CMD_CONSOLE:")) {
            player.closeInventory();
            String cmd = action.substring("CMD_CONSOLE:".length()).trim().replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            return;
        }

        if ("EDIT_TAG".equalsIgnoreCase(action)) {
            if (!plugin.getMemberService().hasPermission(player.getUniqueId(), ClanRank.Permission.MODTAG)) {
                player.sendMessage(plugin.getMessages().get("no-permission-modtag"));
                return;
            }
            player.closeInventory();
            plugin.getChatPromptListener().begin(player, dev.zm.clan.listeners.ChatPromptListener.PromptType.TAG);
            return;
        }

        if ("EDIT_DESCRIPTION".equalsIgnoreCase(action)) {
            if (!plugin.getMemberService().hasPermission(player.getUniqueId(), ClanRank.Permission.EDIT_DESCRIPTION)) {
                player.sendMessage(plugin.getMessages().get("no-permission-description"));
                return;
            }
            player.closeInventory();
            plugin.getChatPromptListener().begin(player,
                    dev.zm.clan.listeners.ChatPromptListener.PromptType.DESCRIPTION);
            return;
        }

        if (action.startsWith("OPEN_GUI:")) {
            String guiName = action.substring("OPEN_GUI:".length()).trim().toLowerCase(Locale.ROOT);
            switch (guiName) {
                case "clan_main", "clan_start", "clan_home", "no_clan", "general" ->
                    plugin.getMenuManager().openMainMenu(player);
                case "clan_members", "members" -> plugin.getMenuManager().openMembersMenu(player, 0);
                case "clan_settings", "settings" -> plugin.getMenuManager().openSettingsMenu(player);
                case "clan_list", "clanlist" -> plugin.getMenuManager().openClanList(player, 0);
                case "clan_invite", "invite" -> plugin.getMenuManager().openInviteMenu(player, 0);
                case "clan_invites", "invites" -> plugin.getMenuManager().openInvitesMenu(player, 0);
                case "clan_stats", "stats", "clan_info" -> plugin.getMenuManager().openStatsMenu(player);
                case "clan_homes", "homes" -> plugin.getMenuManager().openHomesMenu(player, 0);
                case "clan_allies", "clan_rivals" -> plugin.getMenuManager().openRelationsMenu(player, guiName, 0);
                default -> player.sendMessage(plugin.getMessages().get("gui-error-no-menu"));
            }
            return;
        }

        if (action.startsWith("OPEN_MEMBER_MANAGE:")) {
            String rawUuid = action.substring("OPEN_MEMBER_MANAGE:".length()).trim();
            try {
                UUID u = UUID.fromString(rawUuid);
                plugin.getMenuManager().openMemberManage(player, u);
            } catch (IllegalArgumentException ignored) {
            }
            return;
        }

        if (action.startsWith("OPEN_CLAN_MEMBERS:")) {
            String rawId = action.substring("OPEN_CLAN_MEMBERS:".length()).trim();
            UUID clanId = null;
            try {
                clanId = UUID.fromString(rawId);
            } catch (IllegalArgumentException ignored) {
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            plugin.getMenuManager().openMembersMenu(player, 0, clanId);
            return;
        }

        if (action.startsWith("OPEN_CLAN_INFO:")) {
            String rawId = action.substring("OPEN_CLAN_INFO:".length()).trim();
            Optional<Clan> optClan = Optional.empty();
            try {
                UUID u = UUID.fromString(rawId);
                optClan = plugin.getClanService().findById(u);
            } catch (IllegalArgumentException e) {
                optClan = plugin.getClanService().findByName(rawId);
                if (optClan.isEmpty())
                    optClan = plugin.getClanService().findByTag(rawId);
            }
            if (optClan.isPresent()) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                plugin.getMenuManager().openClanInfo(player, optClan.get(), "clan_list");
            } else {
                player.sendMessage(plugin.getMessages().get("clan-not-found", "clan", rawId));
            }
            return;
        }

        if ("BACK_PREVIOUS".equalsIgnoreCase(action)) {
            ActiveMenu active = plugin.getMenuManager().getOpenMenu(player).orElse(null);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            navigateBack(player, active);
            return;
        }

        if ("OPEN_HOMES".equalsIgnoreCase(action)) {
            plugin.getMenuManager().openHomesMenu(player, 0);
            return;
        }

        if ("CLAN_HOME_OR_INFO".equalsIgnoreCase(action)) {
            ActiveMenu active = plugin.getMenuManager().getOpenMenu(player).orElse(null);
            UUID clanId = active != null ? active.getTargetClanId() : null;
            boolean isMember = false;
            if (clanId != null) {
                isMember = plugin.getClanService().findByPlayer(player.getUniqueId())
                        .map(c -> c.getId().equals(clanId)).orElse(false);
            }
            if (isMember) {
                player.closeInventory();
                HomeService.TeleportResult res = plugin.getHomeService().teleport(player);
                switch (res) {
                    case NO_CLAN -> player.sendMessage(plugin.getMessages().get("no-clan"));
                    case NO_HOME -> player.sendMessage(plugin.getMessages().get("home-not-set"));
                    case WORLD_UNLOADED -> player.sendMessage(plugin.getMessages().get("home-world-unloaded"));
                    case NO_PERMISSION -> player.sendMessage(plugin.getMessages().get("no-permission-home"));
                    default -> {
                    }
                }
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
            }
            return;
        }

        if ("TOGGLE_CHAT".equalsIgnoreCase(action)) {
            if (!plugin.getClanService().isInClan(player.getUniqueId())) {
                player.sendMessage(plugin.getMessages().get("no-clan"));
                return;
            }
            if (!plugin.getMemberService().hasPermission(player.getUniqueId(), ClanRank.Permission.USE_CHAT)) {
                player.sendMessage(plugin.getMessages().get("no-permission-chat"));
                return;
            }
            boolean enabled = plugin.getClanChatListener().toggleChat(player.getUniqueId());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            player.sendMessage(plugin.getMessages().get(enabled ? "chat-toggled-on" : "chat-toggled-off"));
            plugin.getMenuManager().openMainMenu(player);
            return;
        }

        if ("CLAN_HOME_OR_SETHOME".equalsIgnoreCase(action)) {
            if (rightClick) {
                // Set home
                HomeService.SetHomeResult res = plugin.getHomeService().setHome(player);
                switch (res) {
                    case OK -> {
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        player.sendMessage(plugin.getMessages().get("home-set", "home", "default"));
                        plugin.getMenuManager().openMainMenu(player);
                    }
                    case NO_PERMISSION -> player.sendMessage(plugin.getMessages().get("no-permission-sethome"));
                    case NO_CLAN -> player.sendMessage(plugin.getMessages().get("no-clan"));
                    case LIMIT_REACHED -> player.sendMessage(plugin.getMessages().get("home-limit", "max",
                            String.valueOf(plugin.getConfigManager().getMaxHomes())));
                }
            } else {
                plugin.getMenuManager().openHomesMenu(player, 0);
            }
            return;
        }

        if ("TOGGLE_FRIENDLY_FIRE".equalsIgnoreCase(action)
                || "TOGGLE_SETTING:friendly_fire".equalsIgnoreCase(action)) {
            if (!plugin.getMemberService().hasPermission(player.getUniqueId(), ClanRank.Permission.EDIT_SETTINGS)) {
                player.sendMessage(plugin.getMessages().get("no-permission"));
                return;
            }
            Optional<Clan> optClan = plugin.getClanService().findByPlayer(player.getUniqueId());
            optClan.ifPresent(clan -> {
                clan.setFriendlyFire(!clan.isFriendlyFire());
                plugin.getClanService().saveAsync(clan);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage(plugin.getMessages()
                        .get(clan.isFriendlyFire() ? "friendly-fire-enabled" : "friendly-fire-disabled"));
                plugin.getMenuManager().openSettingsMenu(player);
            });
            return;
        }

        if ("TOGGLE_OPEN_INVITES".equalsIgnoreCase(action)
                || "TOGGLE_SETTING:open_invitations".equalsIgnoreCase(action)) {
            if (!plugin.getMemberService().hasPermission(player.getUniqueId(), ClanRank.Permission.EDIT_SETTINGS)) {
                player.sendMessage(plugin.getMessages().get("no-permission"));
                return;
            }
            Optional<Clan> optClan = plugin.getClanService().findByPlayer(player.getUniqueId());
            optClan.ifPresent(clan -> {
                clan.setOpenInvitations(!clan.isOpenInvitations());
                plugin.getClanService().saveAsync(clan);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                plugin.getMenuManager().openSettingsMenu(player);
            });
            return;
        }

        if (action != null && action.toUpperCase(Locale.ROOT).startsWith("TOGGLE_SETTING:")) {
            String setting = action.substring("TOGGLE_SETTING:".length()).toLowerCase(Locale.ROOT);
            if (!plugin.getMemberService().hasPermission(player.getUniqueId(), ClanRank.Permission.EDIT_SETTINGS)) {
                player.sendMessage(plugin.getMessages().get("no-permission"));
                return;
            }
            Clan clan = plugin.getClanService().findByPlayer(player.getUniqueId()).orElse(null);
            if (clan == null) {
                player.sendMessage(plugin.getMessages().get("no-clan"));
                return;
            }
            switch (setting) {
                case "ally_homes" -> clan.setAllyHomesVisible(!clan.isAllyHomesVisible());
                case "ally_chat" -> clan.setAllyChatEnabled(!clan.isAllyChatEnabled());
                case "ally_pvp" -> clan.setAllyPvpEnabled(!clan.isAllyPvpEnabled());
                case "public_join" -> clan.setPublicJoin(!clan.isPublicJoin());
                default -> {
                    return;
                }
            }
            plugin.getClanService().saveAsync(clan);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            plugin.getMenuManager().openSettingsMenu(player);
            return;
        }

        if ("SHOW_RELATION_HELP".equalsIgnoreCase(action)) {
            for (String line : plugin.getMessages().getList("relation-help-lines"))
                player.sendMessage(line);
            return;
        }
        if ("OPEN_ALLY_HOMES".equalsIgnoreCase(action)) {
            plugin.getMenuManager().openAllyHomesMenu(player, 0);
            return;
        }

        if ("CONFIRM".equalsIgnoreCase(action) || "CONFIRM_ACTION".equalsIgnoreCase(action)) {
            Runnable callback = holder.getConfirmAction();
            player.closeInventory();
            if (callback != null) {
                callback.run();
            }
            return;
        }

        if ("CANCEL".equalsIgnoreCase(action)) {
            ActiveMenu active = plugin.getMenuManager().getOpenMenu(player).orElse(null);
            navigateBack(player, active);
            return;
        }

        if ("PAGE_NEXT".equalsIgnoreCase(action) || "NEXT_PAGE".equalsIgnoreCase(action)) {
            ActiveMenu active = plugin.getMenuManager().getOpenMenu(player).orElse(null);
            if (active != null && active.getPage() < holder.getMaxPages() - 1) {
                active.setPage(active.getPage() + 1);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                refreshMenuPage(player, active);
            }
            return;
        }

        if ("PAGE_PREV".equalsIgnoreCase(action) || "PREV_PAGE".equalsIgnoreCase(action)) {
            ActiveMenu active = plugin.getMenuManager().getOpenMenu(player).orElse(null);
            if (active != null && active.getPage() > 0) {
                active.setPage(active.getPage() - 1);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                refreshMenuPage(player, active);
            }
            return;
        }

        // Member Management Actions
        if ("MEMBER_PROMOTE".equalsIgnoreCase(action)) {
            handleMemberPromote(player);
            return;
        }

        if ("MEMBER_DEMOTE".equalsIgnoreCase(action)) {
            handleMemberDemote(player);
            return;
        }

        if ("MEMBER_KICK".equalsIgnoreCase(action)) {
            handleMemberKick(player);
            return;
        }

        if ("MEMBER_TRANSFER_LEADER".equalsIgnoreCase(action)) {
            handleMemberTransferLeader(player);
            return;
        }

        if ("CLAN_HOME".equalsIgnoreCase(action)) {
            player.closeInventory();
            HomeService.TeleportResult res = plugin.getHomeService().teleport(player);
            switch (res) {
                case NO_CLAN -> player.sendMessage(plugin.getMessages().get("no-clan"));
                case NO_HOME -> player.sendMessage(plugin.getMessages().get("home-not-set"));
                case WORLD_UNLOADED -> player.sendMessage(plugin.getMessages().get("home-world-unloaded"));
                case NO_PERMISSION -> player.sendMessage(plugin.getMessages().get("no-permission-home"));
                default -> {
                }
            }
            return;
        }

        if ("CLAN_SETHOME".equalsIgnoreCase(action)) {
            HomeService.SetHomeResult res = plugin.getHomeService().setHome(player);
            switch (res) {
                case OK -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    player.sendMessage(plugin.getMessages().get("home-set", "home", "default"));
                }
                case NO_PERMISSION -> player.sendMessage(plugin.getMessages().get("no-permission-sethome"));
                case NO_CLAN -> player.sendMessage(plugin.getMessages().get("no-clan"));
                case LIMIT_REACHED -> player.sendMessage(plugin.getMessages().get("home-limit", "max",
                        String.valueOf(plugin.getConfigManager().getMaxHomes())));
            }
            player.closeInventory();
            return;
        }

        if ("CLAN_LEAVE".equalsIgnoreCase(action)) {
            Optional<Clan> currentClan = plugin.getClanService().findByPlayer(player.getUniqueId());
            if (currentClan.isEmpty()) {
                player.sendMessage(plugin.getMessages().get("no-clan"));
                return;
            }
            if (currentClan.get().getLeader().equals(player.getUniqueId())) {
                player.sendMessage(plugin.getMessages().get("leader-cannot-leave"));
                return;
            }
            String title = plugin.getMessages().get("gui-confirm-leave-title");
            plugin.getMenuManager().openConfirm(player, "leave", title, () -> {
                Optional<Clan> optClan = plugin.getClanService().findByPlayer(player.getUniqueId());
                if (optClan.isPresent()) {
                    String cName = optClan.get().getName();
                    if (plugin.getMemberService().leave(player)) {
                        player.sendMessage(plugin.getMessages().get("left-clan", "clan", cName));
                    } else {
                        player.sendMessage(plugin.getMessages().get("leader-cannot-leave"));
                    }
                }
            });
            return;
        }

        if ("CLAN_DISBAND".equalsIgnoreCase(action)) {
            if (!plugin.getMemberService().hasPermission(player.getUniqueId(), ClanRank.Permission.DISBAND)) {
                player.sendMessage(plugin.getMessages().get("no-permission"));
                return;
            }
            Optional<Clan> optClan = plugin.getClanService().findByPlayer(player.getUniqueId());
            if (optClan.isEmpty()) {
                player.sendMessage(plugin.getMessages().get("no-clan"));
                return;
            }
            Clan clan = optClan.get();
            String title = plugin.getMessages().get("gui-confirm-disband-title", "clan", clan.getName());
            plugin.getMenuManager().openConfirm(player, "disband", title, () -> {
                plugin.getClanService().disband(clan.getId(), player.getName());
                player.sendMessage(plugin.getMessages().get("clan-disbanded", "clan", clan.getName()));
            });
        }
    }

    private boolean checkPermission(Player player, String permission) {
        if (permission == null || player.hasPermission(permission))
            return true;
        player.sendMessage(plugin.getMessages().get("gui-action-no-permission"));
        return false;
    }

    private void refreshMenuPage(Player player, ActiveMenu active) {
        String key = active.getMenuKey();
        switch (key) {
            case "clan_members" ->
                plugin.getMenuManager().openMembersMenu(player, active.getPage(), active.getTargetClanId());
            case "clan_list" -> plugin.getMenuManager().openClanList(player, active.getPage());
            case "clan_allies", "clan_rivals" ->
                plugin.getMenuManager().openRelationsMenu(player, key, active.getPage());
            case "clan_invite" -> plugin.getMenuManager().openInviteMenu(player, active.getPage());
            case "clan_invites" -> plugin.getMenuManager().openInvitesMenu(player, active.getPage());
            case "clan_homes" -> plugin.getMenuManager().openHomesMenu(player, active.getPage());
            case "clan_ally_homes" -> plugin.getMenuManager().openAllyHomesMenu(player, active.getPage());
        }
    }

    private void handleMemberPromote(Player player) {
        ActiveMenu active = plugin.getMenuManager().getOpenMenu(player).orElse(null);
        if (active == null || active.getTargetUuid() == null)
            return;
        UUID targetUuid = active.getTargetUuid();

        Optional<ClanMember> optTarget = plugin.getMemberService().getMember(targetUuid);
        if (optTarget.isEmpty())
            return;

        ClanMember target = optTarget.get();
        List<ClanRank> ranks = new ArrayList<>(plugin.getRankService().allRanks());
        ranks.sort(Comparator.comparingInt(ClanRank::getPriority));

        // Find next higher rank
        ClanRank nextRank = null;
        for (ClanRank r : ranks) {
            ClanRank curRank = plugin.getRankService().getRank(target.getRankId()).orElse(null);
            if (curRank != null && r.getPriority() > curRank.getPriority() && !r.isLeader()) {
                nextRank = r;
                break;
            }
        }

        if (nextRank == null) {
            player.sendMessage(plugin.getMessages().get("rank-highest"));
            return;
        }

        MemberService.RankResult res = plugin.getMemberService().assignRank(player.getUniqueId(), targetUuid,
                nextRank.getId());
        if (res == MemberService.RankResult.OK) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            player.sendMessage(plugin.getMessages().get("rank-updated", "player",
                    Bukkit.getOfflinePlayer(targetUuid).getName(), "rank", nextRank.getDisplayName()));
            plugin.getMenuManager().openMemberManage(player, targetUuid);
        } else {
            player.sendMessage(plugin.getMessages().get("cannot-assign-higher-rank"));
        }
    }

    private void handleMemberDemote(Player player) {
        ActiveMenu active = plugin.getMenuManager().getOpenMenu(player).orElse(null);
        if (active == null || active.getTargetUuid() == null)
            return;
        UUID targetUuid = active.getTargetUuid();

        Optional<ClanMember> optTarget = plugin.getMemberService().getMember(targetUuid);
        if (optTarget.isEmpty())
            return;

        ClanMember target = optTarget.get();
        List<ClanRank> ranks = new ArrayList<>(plugin.getRankService().allRanks());
        ranks.sort(Comparator.comparingInt(ClanRank::getPriority).reversed());

        ClanRank prevRank = null;
        for (ClanRank r : ranks) {
            ClanRank curRank = plugin.getRankService().getRank(target.getRankId()).orElse(null);
            if (curRank != null && r.getPriority() < curRank.getPriority()) {
                prevRank = r;
                break;
            }
        }

        if (prevRank == null) {
            player.sendMessage(plugin.getMessages().get("rank-lowest"));
            return;
        }

        MemberService.RankResult res = plugin.getMemberService().assignRank(player.getUniqueId(), targetUuid,
                prevRank.getId());
        if (res == MemberService.RankResult.OK) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            player.sendMessage(plugin.getMessages().get("rank-updated", "player",
                    Bukkit.getOfflinePlayer(targetUuid).getName(), "rank", prevRank.getDisplayName()));
            plugin.getMenuManager().openMemberManage(player, targetUuid);
        } else {
            player.sendMessage(plugin.getMessages().get("cannot-assign-higher-rank"));
        }
    }

    private void handleMemberKick(Player player) {
        ActiveMenu active = plugin.getMenuManager().getOpenMenu(player).orElse(null);
        if (active == null || active.getTargetUuid() == null)
            return;
        UUID targetUuid = active.getTargetUuid();
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetUuid);
        String targetName = op.getName() != null ? op.getName() : "Unknown";

        String title = plugin.getMessages().get("gui-confirm-kick-title", "player", targetName);
        plugin.getMenuManager().openConfirm(player, "kick", title, () -> {
            MemberService.KickResult res = plugin.getMemberService().kick(player.getUniqueId(), targetUuid);
            if (res == MemberService.KickResult.OK) {
                player.sendMessage(plugin.getMessages().get("player-kicked", "player", targetName));
                plugin.getMenuManager().openMembersMenu(player, 0);
            } else if (res == MemberService.KickResult.CANNOT_KICK_LEADER) {
                player.sendMessage(plugin.getMessages().get("cannot-kick-leader"));
            } else if (res == MemberService.KickResult.RANK_TOO_HIGH) {
                player.sendMessage(plugin.getMessages().get("cannot-kick-higher-rank"));
            } else {
                player.sendMessage(plugin.getMessages().get("no-permission"));
            }
        });
    }

    private void handleMemberTransferLeader(Player player) {
        ActiveMenu active = plugin.getMenuManager().getOpenMenu(player).orElse(null);
        if (active == null || active.getTargetUuid() == null)
            return;
        UUID targetUuid = active.getTargetUuid();
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetUuid);
        String targetName = op.getName() != null ? op.getName() : "Unknown";

        Clan clan = plugin.getClanService().findByPlayer(player.getUniqueId()).orElse(null);
        if (clan == null || !clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getMessages().get("no-permission-transfer"));
            return;
        }
        // Cannot transfer leadership to yourself
        if (targetUuid.equals(player.getUniqueId())) {
            player.sendMessage(plugin.getMessages().get("cannot-transfer-self"));
            return;
        }

        String title = plugin.getMessages().get("gui-confirm-transfer-title", "player", targetName);
        plugin.getMenuManager().openConfirm(player, "transfer", title, () -> {
            if (!plugin.getMemberService().transferLeadership(player.getUniqueId(), targetUuid)) {
                player.sendMessage(plugin.getMessages().get("no-permission-transfer"));
                return;
            }
            player.sendMessage(
                    plugin.getMessages().get("leader-transferred", "clan", clan.getName(), "player", targetName));
            if (op.isOnline() && op.getPlayer() != null) {
                op.getPlayer().sendMessage(plugin.getMessages().get("new-leader-notify", "clan", clan.getName()));
            }
            plugin.getMenuManager().openMainMenu(player);
        });
    }

    private void handlePublicJoinClick(Player player, Clan clan) {
        if (plugin.getClanService().isInClan(player.getUniqueId())) {
            player.sendMessage(plugin.getMessages().get("already-in-clan"));
            return;
        }
        if (!clan.isPublicJoin()) {
            player.sendMessage(plugin.getMessages().get("public-join-disabled"));
            return;
        }
        int maxMembers = plugin.getMemberService().getMaxMembers(clan);
        if (clan.getMemberCount() >= maxMembers) {
            player.sendMessage(plugin.getMessages().get("invite-clan-full", "amount",
                    String.valueOf(clan.getMemberCount()), "max", String.valueOf(maxMembers)));
            return;
        }
        String rank = plugin.getRankService().getDefaultRank().map(ClanRank::getId).orElse("member");
        if (!plugin.getMemberService().joinClan(player.getUniqueId(), clan.getId(), rank)) {
            player.sendMessage(plugin.getMessages().get("already-in-clan"));
            return;
        }
        player.sendMessage(plugin.getMessages().get("public-join-success", "clan", clan.getName()));
        plugin.getMenuManager().openMainMenu(player);
    }

    private void navigateBack(Player player, ActiveMenu active) {
        if (active == null || active.getBackMenuKey() == null) {
            plugin.getMenuManager().openMainMenu(player);
            return;
        }

        int page = active.getBackPage();
        UUID targetClanId = active.getTargetClanId();
        switch (active.getBackMenuKey()) {
            case "clan_list" -> plugin.getMenuManager().openClanList(player, page);
            case "clan_info" -> {
                if (targetClanId == null) {
                    plugin.getMenuManager().openMainMenu(player);
                } else {
                    plugin.getClanService().findById(targetClanId).ifPresentOrElse(
                            clan -> plugin.getMenuManager().openClanInfo(player, clan, "clan_list"),
                            () -> plugin.getMenuManager().openMainMenu(player));
                }
            }
            case "clan_members", "member_manage" ->
                plugin.getMenuManager().openMembersMenu(player, page, targetClanId);
            case "clan_settings" -> plugin.getMenuManager().openSettingsMenu(player);
            case "clan_invites" -> plugin.getMenuManager().openInvitesMenu(player, page);
            case "clan_invite" -> plugin.getMenuManager().openInviteMenu(player, page);
            case "clan_homes" -> plugin.getMenuManager().openHomesMenu(player, page);
            case "clan_ally_homes" -> plugin.getMenuManager().openAllyHomesMenu(player, page);
            case "clan_allies", "clan_rivals" ->
                plugin.getMenuManager().openRelationsMenu(player, active.getBackMenuKey(), page);
            case "clan_main", "clan_start", "clan_home", "no_clan" ->
                plugin.getMenuManager().openMainMenu(player);
            default -> plugin.getMenuManager().openMainMenu(player);
        }
    }

    private void handleRelationClick(Player player, MenuHolder holder, Clan clan, String menuKey) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        plugin.getMenuManager().openClanInfo(player, clan, menuKey);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) {
            if (event.getPlayer() instanceof Player player) {
                // Opening another plugin GUI fires a close event for the previous one.
                // Keep the newly registered ActiveMenu when another zMClans menu is now open.
                if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder)) {
                    plugin.getMenuManager().onClose(player);
                }
            }
        }
    }
}
