package dev.zm.clan.command;

import dev.zm.clan.clan.model.Clan;
import dev.zm.clan.invitation.model.Invitation;
import dev.zm.clan.member.model.ClanMember;
import dev.zm.clan.menu.AllyHomeEntry;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

public final class PermissionNodes {

    private PermissionNodes() {
    }

    public static String forPlayerCommand(String[] args) {
        if (args.length == 0)
            return "zmclan.player.menu";
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "menu", "gui" -> "zmclan.player.menu";
            case "create" -> "zmclan.player.create";
            case "invite" -> "zmclan.player.invite";
            case "invites" -> "zmclan.player.invites";
            case "accept" -> "zmclan.player.accept";
            case "deny" -> "zmclan.player.deny";
            case "join" -> "zmclan.player.join";
            case "members" -> "zmclan.player.members";
            case "settings", "ff", "friendlyfire" -> "zmclan.player.settings";
            case "stats", "info" -> "zmclan.player.info";
            case "list" -> "zmclan.player.list";
            case "ally", "rival", "relation" -> "zmclan.player.relations";
            case "kick" -> "zmclan.player.kick";
            case "leave" -> "zmclan.player.leave";
            case "disband" -> "zmclan.player.disband";
            case "setrank", "rank", "promote" -> "zmclan.player.rank";
            case "home" -> args.length == 1 ? "zmclan.player.homes" : "zmclan.player.home";
            case "homes" -> "zmclan.player.homes";
            case "sethome" -> "zmclan.player.sethome";
            case "delhome", "deletehome" -> "zmclan.player.delhome";
            case "chat", "c" -> "zmclan.player.chat";
            case "desc", "description" -> "zmclan.player.description";
            case "modtag", "tag" -> "zmclan.player.modtag";
            case "help" -> "zmclan.player.help";
            default -> null;
        };
    }

    public static String forAdminCommand(String[] args) {
        if (args.length == 0)
            return "zmclans.admin.help";
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> "zmclans.admin.help";
            case "reload" -> "zmclans.admin.reload";
            case "disband", "delete" -> "zmclans.admin.disband";
            case "setleader" -> "zmclans.admin.setleader";
            case "points" -> "zmclans.admin.points";
            case "info" -> "zmclans.admin.info";
            case "list" -> "zmclans.admin.list";
            case "members" -> "zmclans.admin.members";
            default -> null;
        };
    }

    public static String forMenuAction(String action, boolean rightClick, String menuKey) {
        if (action == null)
            return null;
        String normalized = action.toUpperCase(Locale.ROOT);
        if (normalized.startsWith("OPEN_GUI:")) {
            return forMenu(normalized.substring("OPEN_GUI:".length()).toLowerCase(Locale.ROOT));
        }
        if (normalized.startsWith("CMD_CONSOLE:"))
            return "zmclans.admin.console";
        if (normalized.startsWith("CMD_PLAYER:")) {
            String command = action.substring("CMD_PLAYER:".length()).trim().replaceFirst("^/", "");
            String[] args = command.split("\\s+");
            return args.length > 0 && "clan".equalsIgnoreCase(args[0])
                    ? forPlayerCommand(Arrays.copyOfRange(args, 1, args.length))
                    : "zmclan.player.menu";
        }
        if (normalized.startsWith("OPEN_MEMBER_MANAGE:"))
            return "zmclan.player.members";
        if (normalized.startsWith("OPEN_CLAN_MEMBERS:"))
            return "zmclan.player.members";
        if (normalized.startsWith("OPEN_CLAN_INFO:"))
            return "zmclan.player.info";

        return switch (normalized) {
            case "CREATE_CLAN" -> "zmclan.player.create";
            case "SHOW_HELP", "SHOW_RELATION_HELP" -> "zmclan.player.help";
            case "OPEN_CLANS", "TOP_TOGGLE" -> "zmclan.player.list";
            case "EDIT_TAG" -> "zmclan.player.modtag";
            case "EDIT_DESCRIPTION" -> "zmclan.player.description";
            case "OPEN_HOMES", "OPEN_ALLY_HOMES" -> "zmclan.player.homes";
            case "CLAN_HOME", "CLAN_HOME_OR_INFO" -> "zmclan.player.home";
            case "CLAN_SETHOME" -> "zmclan.player.sethome";
            case "CLAN_HOME_OR_SETHOME" -> rightClick ? "zmclan.player.sethome" : "zmclan.player.homes";
            case "TOGGLE_CHAT" -> "zmclan.player.chat";
            case "TOGGLE_FRIENDLY_FIRE", "TOGGLE_OPEN_INVITES" -> "zmclan.player.settings";
            case "CLAN_LEAVE" -> "zmclan.player.leave";
            case "CLAN_DISBAND" -> "zmclan.player.disband";
            case "MEMBER_PROMOTE", "MEMBER_DEMOTE", "MEMBER_TRANSFER_LEADER" -> "zmclan.player.rank";
            case "MEMBER_KICK" -> "zmclan.player.kick";
            case "TOGGLE_SETTING:FRIENDLY_FIRE", "TOGGLE_SETTING:OPEN_INVITATIONS",
                    "TOGGLE_SETTING:ALLY_HOMES", "TOGGLE_SETTING:ALLY_CHAT",
                    "TOGGLE_SETTING:ALLY_PVP", "TOGGLE_SETTING:PUBLIC_JOIN" ->
                "zmclan.player.settings";
            default -> null;
        };
    }

    public static String forConfirmationAction(String actionKey) {
        if (actionKey == null)
            return null;
        return switch (actionKey.toLowerCase(Locale.ROOT)) {
            case "leave" -> "zmclan.player.leave";
            case "disband" -> "zmclan.player.disband";
            case "kick" -> "zmclan.player.kick";
            case "transfer" -> "zmclan.player.rank";
            default -> null;
        };
    }

    public static String forDynamicMenuClick(Object data, boolean rightClick, String menuKey) {
        if (data instanceof AllyHomeEntry)
            return "zmclan.player.home";
        if (data instanceof String)
            return rightClick ? "zmclan.player.delhome" : "zmclan.player.home";
        if (data instanceof ClanMember)
            return "zmclan.player.members";
        if (data instanceof UUID)
            return "zmclan.player.invite";
        if (data instanceof Invitation)
            return rightClick ? "zmclan.player.deny" : "zmclan.player.accept";
        if (data instanceof Clan) {
            return switch (menuKey == null ? "" : menuKey) {
                case "clan_allies", "clan_rivals" -> "zmclan.player.relations";
                case "clan_invites" -> "zmclan.player.join";
                default -> "zmclan.player.info";
            };
        }
        return null;
    }

    public static String forMenu(String menuKey) {
        if (menuKey == null)
            return null;
        return switch (menuKey.toLowerCase(Locale.ROOT)) {
            case "clan_main", "clan_start", "clan_home", "no_clan", "general" -> "zmclan.player.menu";
            case "clan_members", "members", "member_manage" -> "zmclan.player.members";
            case "clan_settings", "settings" -> "zmclan.player.settings";
            case "clan_list", "clanlist" -> "zmclan.player.list";
            case "clan_invite", "invite" -> "zmclan.player.invite";
            case "clan_invites", "invites" -> "zmclan.player.invites";
            case "clan_stats", "stats", "clan_info", "info" -> "zmclan.player.info";
            case "clan_homes", "clan_ally_homes", "homes" -> "zmclan.player.homes";
            case "clan_allies", "clan_rivals" -> "zmclan.player.relations";
            default -> null;
        };
    }
}
