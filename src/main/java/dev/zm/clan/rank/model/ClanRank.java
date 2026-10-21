package dev.zm.clan.rank.model;

import java.util.EnumMap;
import java.util.Map;

/**
 * Represents a configurable clan rank loaded from ranks.yml.
 */
public final class ClanRank {

    /** All permissions a rank can grant or deny. */
    public enum Permission {
        USE_HOME,
        USE_CHAT,
        INVITE,
        KICK,
        PROMOTE,
        SET_HOME,
        EDIT_DESCRIPTION,
        EDIT_SETTINGS,
        TRANSFER_LEADER,
        DISBAND,
        MODTAG
    }

    private final String id;
    private String displayName;
    private String color;
    private String prefix;
    private int priority; // Higher = more powerful
    private boolean isLeader;
    private final Map<Permission, Boolean> permissions;

    public ClanRank(String id) {
        this.id = id;
        this.permissions = new EnumMap<>(Permission.class);
        // All permissions default to false
        for (Permission p : Permission.values()) {
            permissions.put(p, false);
        }
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    public String getPrefix() {
        return prefix;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isLeader() {
        return isLeader;
    }

    /** @return {@code true} if this rank has the given permission. */
    public boolean hasPermission(Permission perm) {
        if (isLeader)
            return true;
        return Boolean.TRUE.equals(permissions.get(perm));
    }

    public void setDisplayName(String n) {
        this.displayName = n;
    }

    public void setColor(String c) {
        this.color = c;
    }

    public void setPrefix(String p) {
        this.prefix = p;
    }

    public void setPriority(int p) {
        this.priority = p;
    }

    public void setLeader(boolean l) {
        this.isLeader = l;
    }

    public void setPermission(Permission p, boolean v) {
        permissions.put(p, v);
    }

    /** Colorized prefix + display name, ready to show in chat/GUI. */
    public String formatted() {
        return color + displayName;
    }
}
