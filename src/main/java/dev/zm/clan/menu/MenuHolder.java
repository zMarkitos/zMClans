package dev.zm.clan.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom InventoryHolder used by all zMClans GUIs.
 * Allows MenuListener to safely identify plugin inventories and retrieve
 * clicked item actions.
 */
public final class MenuHolder implements InventoryHolder {

    private final MenuConfig config;
    private final int page;
    private final Runnable confirmAction;
    private int maxPages = 1;
    private int totalItems = 0;
    private final Map<Integer, ItemConfig> slotActions = new HashMap<>();
    private final Map<Integer, Object> slotData = new HashMap<>();
    private Inventory inventory;

    public MenuHolder(MenuConfig config, int page, Runnable confirmAction) {
        this.config = config;
        this.page = page;
        this.confirmAction = confirmAction;
    }

    public MenuConfig getConfig() {
        return config;
    }

    public int getPage() {
        return page;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = Math.max(1, maxPages);
    }

    public int getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    public boolean hasPrevPage() {
        return page > 0;
    }

    public boolean hasNextPage() {
        return page < maxPages - 1;
    }

    public Runnable getConfirmAction() {
        return confirmAction;
    }

    public void setSlotAction(int slot, ItemConfig itemConfig) {
        slotActions.put(slot, itemConfig);
    }

    public ItemConfig getSlotAction(int slot) {
        return slotActions.get(slot);
    }

    public void setSlotData(int slot, Object data) {
        slotData.put(slot, data);
    }

    public Object getSlotData(int slot) {
        return slotData.get(slot);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
