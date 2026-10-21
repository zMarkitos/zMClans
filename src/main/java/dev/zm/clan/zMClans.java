package dev.zm.clan;

import dev.zm.clan.bootstrap.PluginBootstrap;
import dev.zm.clan.api.ZMClansAPI;
import dev.zm.clan.config.ConfigManager;
import dev.zm.clan.config.Messages;
import dev.zm.clan.hook.HookManager;
import dev.zm.clan.menu.MenuManager;
import dev.zm.clan.service.ClanService;
import dev.zm.clan.service.HomeService;
import dev.zm.clan.service.InviteService;
import dev.zm.clan.service.MemberService;
import dev.zm.clan.service.RankService;
import dev.zm.clan.storage.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import java.util.logging.Level;
import java.io.File;
import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;

/**
 * zMClans — Main plugin class.
 * Delegates all initialization to {@link PluginBootstrap}.
 */
public final class zMClans extends JavaPlugin {

    private static zMClans instance;
    private ZMClansAPI api;

    private ConfigManager configManager;
    private Messages messages;
    private DatabaseManager databaseManager;
    private ClanService clanService;
    private MemberService memberService;
    private RankService rankService;
    private InviteService inviteService;
    private HomeService homeService;
    private dev.zm.clan.service.ProgressionService progressionService;
    private dev.zm.clan.service.RelationService relationService;
    private dev.zm.clan.service.NotificationService notificationService;
    private MenuManager menuManager;
    private HookManager hookManager;
    private dev.zm.clan.listeners.ClanChatListener clanChatListener;
    private dev.zm.clan.listeners.ChatPromptListener chatPromptListener;
    private dev.zm.clan.listeners.FriendlyFireListener friendlyFireListener;
    private dev.zm.clan.task.AutoSaveTask autoSaveTask;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
        instance = this;

        printBanner();

        try {
            api = new ZMClansAPI(this);
            File levelsFile = new File(getDataFolder(), "levels.yml");

            if (!levelsFile.exists()) {
                saveResource("levels.yml", false);
            }
            new PluginBootstrap(this).boot();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Critical error during zMClans startup. Disabling plugin...", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        int pluginId = 34844;
        Metrics metrics = new Metrics(this, pluginId);
        long loadTime = System.currentTimeMillis() - startTime;
        printStartupComplete(loadTime);
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down zMClans...");

        if (databaseManager != null) {
            databaseManager.stopAsyncWritesAndAwait();
        }
        if (clanService != null) {
            clanService.saveAllSync();
        }
        if (memberService != null) {
            memberService.saveAllSync();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        printShutdownComplete();
    }

    private void printBanner() {
    }

    private void printStartupComplete(long ms) {
        org.bukkit.command.ConsoleCommandSender console = Bukkit.getConsoleSender();

        String version = getDescription().getVersion();
        String author = getDescription().getAuthors().isEmpty()
                ? "zMarkitos_"
                : getDescription().getAuthors().get(0);

        String lang = configManager != null
                ? configManager.getLanguage()
                : "Unknown";

        String storage = databaseManager != null
                ? "&#2ECC71> Connected"
                : "&#E74C3C> Unavailable";

        boolean papiEnabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

        String papi = papiEnabled
                ? "&#2ECC71> Hooked"
                : "&#E74C3C> Not found";

        int ranks = rankService != null
                ? rankService.getClass() != null ? 1 : 0
                : 0;

        int menus = 0;
        if (menuManager != null) {
            menus = 1;
        }

        console.sendMessage(dev.zm.clan.config.ColorUtil.colorize(
                "<dark_gray>──────────────────────────────────────────"));

        console.sendMessage(dev.zm.clan.config.ColorUtil.colorize(
                "<color:#64C6FF>zMClans <gray>v" + version
                        + " <dark_gray>by <white>" + author));

        console.sendMessage(dev.zm.clan.config.ColorUtil.colorize(""));

        console.sendMessage(dev.zm.clan.config.ColorUtil.colorize(
                "  <dark_gray>• <gray>Language: <white>" + lang));

        console.sendMessage(dev.zm.clan.config.ColorUtil.colorize(
                "  <dark_gray>• <gray>Storage: " + storage));

        console.sendMessage(dev.zm.clan.config.ColorUtil.colorize(
                "  <dark_gray>• <gray>PlaceholderAPI: " + papi));

        console.sendMessage(dev.zm.clan.config.ColorUtil.colorize(
                "  <dark_gray>• <gray>Server: <white>"
                        + Bukkit.getMinecraftVersion()));

        console.sendMessage(dev.zm.clan.config.ColorUtil.colorize(
                "  <dark_gray>• <gray>Java: <white>"
                        + System.getProperty("java.version")));

        console.sendMessage(dev.zm.clan.config.ColorUtil.colorize(""));

        console.sendMessage(dev.zm.clan.config.ColorUtil.colorize(
                "  <dark_gray>• <gray>Menu manager: <white>"
                        + (menus > 0 ? "Initialized" : "Unavailable")));

        console.sendMessage(dev.zm.clan.config.ColorUtil.colorize(
                "  <dark_gray>• <gray>Rank manager: <white>"
                        + (rankService != null ? "Initialized" : "Unavailable")));

        console.sendMessage(dev.zm.clan.config.ColorUtil.colorize(""));

        console.sendMessage(dev.zm.clan.config.ColorUtil.colorize(
                "<color:#2ECC71>✓ <white>Plugin enabled successfully "
                        + "<gray>(" + ms + "ms)"));

        console.sendMessage(dev.zm.clan.config.ColorUtil.colorize(
                "<dark_gray>──────────────────────────────────────────"));
    }

    private void printShutdownComplete() {
        printShutdownMessage(0L);
    }

    private void printShutdownMessage(long ms) {
        org.bukkit.command.ConsoleCommandSender console = Bukkit.getConsoleSender();

        String version = getDescription().getVersion();

        console.sendMessage(dev.zm.clan.config.ColorUtil.colorize(
                "<dark_gray>──────────────────────────────────────────"));

        console.sendMessage(dev.zm.clan.config.ColorUtil.colorize(
                "<color:#64C6FF>zMClans <gray>v" + version
                        + " <dark_gray>is shutting down..."));

        console.sendMessage(dev.zm.clan.config.ColorUtil.colorize(
                "<color:#E74C3C>✗ <white>Plugin disabled successfully"));

        console.sendMessage(dev.zm.clan.config.ColorUtil.colorize(
                "<dark_gray>──────────────────────────────────────────"));
    }

    public static zMClans get() {
        return instance;
    }

    /** Public API facade for other plugins. Available after this plugin enables. */
    public ZMClansAPI getAPI() {
        return api;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public void setConfigManager(ConfigManager cm) {
        this.configManager = cm;
    }

    public Messages getMessages() {
        return messages;
    }

    public void setMessages(Messages m) {
        this.messages = m;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public void setDatabaseManager(DatabaseManager db) {
        this.databaseManager = db;
    }

    public ClanService getClanService() {
        return clanService;
    }

    public void setClanService(ClanService cs) {
        this.clanService = cs;
    }

    public MemberService getMemberService() {
        return memberService;
    }

    public void setMemberService(MemberService ms) {
        this.memberService = ms;
    }

    public RankService getRankService() {
        return rankService;
    }

    public void setRankService(RankService rs) {
        this.rankService = rs;
    }

    public InviteService getInviteService() {
        return inviteService;
    }

    public void setInviteService(InviteService is) {
        this.inviteService = is;
    }

    public dev.zm.clan.service.ProgressionService getProgressionService() {
        return progressionService;
    }

    public void setProgressionService(dev.zm.clan.service.ProgressionService service) {
        this.progressionService = service;
    }

    public HomeService getHomeService() {
        return homeService;
    }

    public void setHomeService(HomeService hs) {
        this.homeService = hs;
    }

    public dev.zm.clan.service.RelationService getRelationService() {
        return relationService;
    }

    public void setRelationService(dev.zm.clan.service.RelationService service) {
        this.relationService = service;
    }

    public dev.zm.clan.service.NotificationService getNotificationService() {
        return notificationService;
    }

    public void setNotificationService(dev.zm.clan.service.NotificationService service) {
        this.notificationService = service;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public void setMenuManager(MenuManager mm) {
        this.menuManager = mm;
    }

    public HookManager getHookManager() {
        return hookManager;
    }

    public void setHookManager(HookManager hm) {
        this.hookManager = hm;
    }

    public dev.zm.clan.listeners.ClanChatListener getClanChatListener() {
        return clanChatListener;
    }

    public void setClanChatListener(dev.zm.clan.listeners.ClanChatListener ccl) {
        this.clanChatListener = ccl;
    }

    public dev.zm.clan.listeners.ChatPromptListener getChatPromptListener() {
        return chatPromptListener;
    }

    public void setChatPromptListener(dev.zm.clan.listeners.ChatPromptListener listener) {
        this.chatPromptListener = listener;
    }

    public dev.zm.clan.listeners.FriendlyFireListener getFriendlyFireListener() {
        return friendlyFireListener;
    }

    public void setFriendlyFireListener(dev.zm.clan.listeners.FriendlyFireListener listener) {
        this.friendlyFireListener = listener;
    }

    public dev.zm.clan.task.AutoSaveTask getAutoSaveTask() {
        return autoSaveTask;
    }

    public void setAutoSaveTask(dev.zm.clan.task.AutoSaveTask task) {
        this.autoSaveTask = task;
    }
}
