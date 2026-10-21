package dev.zm.clan.menu;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model for a single inventory item defined in a GUI YAML section.
 */
public final class ItemConfig {

    private final String key;
    private final String material;
    private final String displayName;
    private final List<String> lore;
    private final List<Integer> slots;
    private final int slot;
    private final boolean glow;
    private final List<String> itemFlags;
    private final String action;
    private final String requireRankPerm;
    private final String requirePerm;

    private ItemConfig(Builder b) {
        this.key = b.key;
        this.material = b.material;
        this.displayName = b.displayName;
        this.lore = b.lore;
        this.slots = b.slots;
        this.slot = b.slot;
        this.glow = b.glow;
        this.itemFlags = b.itemFlags;
        this.action = b.action;
        this.requireRankPerm = b.requireRankPerm;
        this.requirePerm = b.requirePerm;
    }

    public static ItemConfig parse(String key, ConfigurationSection s) {
        Builder b = new Builder(key);
        b.material = s.getString("material", "STONE");
        b.displayName = s.contains("display_name") ? s.getString("display_name", "") : s.getString("display-name", "");
        b.lore = s.getStringList("lore");
        b.glow = s.getBoolean("glow", false);
        b.itemFlags = s.contains("item_flags") ? s.getStringList("item_flags") : s.getStringList("item-flags");
        b.action = s.getString("action", "NONE");
        b.requireRankPerm = s.contains("require_rank_permission") ? s.getString("require_rank_permission")
                : s.getString("require-rank-permission", null);
        b.requirePerm = s.contains("require_permission") ? s.getString("require_permission")
                : s.getString("require-permission", s.getString("permission", null));
        b.slot = s.getInt("slot", -1);

        // Multi-slot support (FILLER pattern, ranges, semicolons, lists)
        if (s.contains("slots")) {
            b.slots.addAll(MenuConfig.parseSlotExpression(s.get("slots")));
        } else if (s.contains("slot")) {
            if (s.isInt("slot")) {
                b.slot = s.getInt("slot");
            } else {
                List<Integer> parsed = MenuConfig.parseSlotExpression(s.get("slot"));
                if (parsed.size() == 1) {
                    b.slot = parsed.get(0);
                } else if (parsed.size() > 1) {
                    b.slots.addAll(parsed);
                }
            }
        }
        return b.build();
    }

    public String getKey() {
        return key;
    }

    public String getMaterial() {
        return material;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLore() {
        return lore;
    }

    public List<Integer> getSlots() {
        return slots;
    }

    public int getSlot() {
        return slot;
    }

    public boolean isGlow() {
        return glow;
    }

    public List<String> getItemFlags() {
        return itemFlags;
    }

    public String getAction() {
        return action;
    }

    public String getRequireRankPerm() {
        return requireRankPerm;
    }

    public String getRequirePerm() {
        return requirePerm;
    }

    public boolean hasMultiSlots() {
        return !slots.isEmpty();
    }

    private static final class Builder {
        String key;
        String material;
        String displayName;
        List<String> lore = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        int slot = -1;
        boolean glow;
        List<String> itemFlags = new ArrayList<>();
        String action;
        String requireRankPerm;
        String requirePerm;

        Builder(String key) {
            this.key = key;
        }

        ItemConfig build() {
            return new ItemConfig(this);
        }
    }
}
