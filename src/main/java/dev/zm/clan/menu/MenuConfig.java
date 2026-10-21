package dev.zm.clan.menu;

import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Pure data record parsed from a YAML menu section.
 * No Bukkit inventory objects here — separation of data from rendering.
 */
public final class MenuConfig {

    private final String key;
    private final String rawTitle;
    private final int rows;
    private final String openSound;
    private final boolean paginated;
    private final int pageSize;
    private final List<Integer> pageSlots;
    private final Map<String, ItemConfig> items;

    private MenuConfig(String key, String rawTitle, int rows, String openSound,
            boolean paginated, int pageSize, List<Integer> pageSlots,
            Map<String, ItemConfig> items) {
        this.key = key;
        this.rawTitle = rawTitle;
        this.rows = rows;
        this.openSound = openSound;
        this.paginated = paginated;
        this.pageSize = pageSize;
        this.pageSlots = pageSlots;
        this.items = items;
    }

    public static MenuConfig parse(String key, ConfigurationSection s) {
        String title = s.getString("title", "Menu");
        int rows = s.getInt("rows", 3);
        String sound = s.getString("open-sound", null);
        boolean paginated = s.contains("page-size") || s.contains("page-slots");
        int pageSize = s.getInt("page-size", 21);
        List<Integer> pageSlots = new ArrayList<>();
        if (s.contains("page-slots")) {
            pageSlots.addAll(parseSlotExpression(s.get("page-slots")));
        }

        Map<String, ItemConfig> items = new LinkedHashMap<>();
        ConfigurationSection sec = s.getConfigurationSection("items");
        if (sec != null) {
            for (String itemKey : sec.getKeys(false)) {
                ConfigurationSection is = sec.getConfigurationSection(itemKey);
                if (is != null)
                    items.put(itemKey, ItemConfig.parse(itemKey, is));
            }
        }
        return new MenuConfig(key, title, rows, sound, paginated, pageSize, pageSlots, items);
    }

    /**
     * Parses slot expressions supporting ranges, semicolons, commas, lists, and
     * numbers.
     * Examples:
     * - "0-8;45-53"
     * - "0-8, 18, 26, 45-53"
     * - "[0, 1, 2]"
     * - 5
     * - Lists or collections containing any of the above
     */
    public static List<Integer> parseSlotExpression(Object raw) {
        Set<Integer> slots = new LinkedHashSet<>();
        collectSlots(raw, slots);
        return new ArrayList<>(slots);
    }

    private static void collectSlots(Object raw, Set<Integer> slots) {
        if (raw == null)
            return;
        if (raw instanceof Number num) {
            slots.add(num.intValue());
            return;
        }
        if (raw instanceof Iterable<?> iter) {
            for (Object elem : iter) {
                collectSlots(elem, slots);
            }
            return;
        }
        if (raw instanceof Object[] arr) {
            for (Object elem : arr) {
                collectSlots(elem, slots);
            }
            return;
        }

        String str = raw.toString().trim();
        if (str.startsWith("[") && str.endsWith("]")) {
            str = str.substring(1, str.length() - 1).trim();
        }
        if (str.isEmpty())
            return;

        // Split by semicolons or commas
        String[] tokens = str.split("[;,]+");
        for (String token : tokens) {
            String t = token.trim();
            if (t.isEmpty())
                continue;
            if (t.contains("-")) {
                String[] parts = t.split("-");
                if (parts.length == 2) {
                    try {
                        int from = Integer.parseInt(parts[0].trim());
                        int to = Integer.parseInt(parts[1].trim());
                        int start = Math.min(from, to);
                        int end = Math.max(from, to);
                        for (int i = start; i <= end; i++) {
                            slots.add(i);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            } else {
                try {
                    slots.add(Integer.parseInt(t));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    public String getKey() {
        return key;
    }

    public String getRawTitle() {
        return rawTitle;
    }

    public int getRows() {
        return rows;
    }

    public String getOpenSound() {
        return openSound;
    }

    public boolean isPaginated() {
        return paginated;
    }

    public int getPageSize() {
        return pageSize;
    }

    public List<Integer> getPageSlots() {
        return pageSlots;
    }

    public Map<String, ItemConfig> getItems() {
        return items;
    }
}
