package dev.zm.clan.menu;

/**
 * Tracks the state of an actively open menu for a player.
 */
public final class ActiveMenu {

    private final String menuKey;
    private final Runnable confirmAction;
    private int page;

    public ActiveMenu(String menuKey, Runnable confirmAction) {
        this(menuKey, confirmAction, 0);
    }

    public ActiveMenu(String menuKey, Runnable confirmAction, int page) {
        this.menuKey = menuKey;
        this.confirmAction = confirmAction;
        this.page = page;
    }

    public String getMenuKey() {
        return menuKey;
    }

    public Runnable getConfirmAction() {
        return confirmAction;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    private java.util.UUID targetUuid;
    private java.util.UUID targetClanId;
    private String backMenuKey;
    private int backPage;
    private String confirmPermission;

    public java.util.UUID getTargetUuid() {
        return targetUuid;
    }

    public void setTargetUuid(java.util.UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    public java.util.UUID getTargetClanId() {
        return targetClanId;
    }

    public void setTargetClanId(java.util.UUID targetClanId) {
        this.targetClanId = targetClanId;
    }

    public String getBackMenuKey() {
        return backMenuKey;
    }

    public void setBackMenuKey(String backMenuKey) {
        this.backMenuKey = backMenuKey;
    }

    public int getBackPage() {
        return backPage;
    }

    public void setBackPage(int backPage) {
        this.backPage = backPage;
    }

    public String getConfirmPermission() {
        return confirmPermission;
    }

    public void setConfirmPermission(String confirmPermission) {
        this.confirmPermission = confirmPermission;
    }
}
