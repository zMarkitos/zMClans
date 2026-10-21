package dev.zm.clan.bootstrap;

import dev.zm.clan.command.AdminCommand;
import dev.zm.clan.command.ClanCommand;
import dev.zm.clan.config.ConfigManager;
import dev.zm.clan.config.Messages;
import dev.zm.clan.hook.HookManager;
import dev.zm.clan.listeners.ClanChatListener;
import dev.zm.clan.listeners.FriendlyFireListener;
import dev.zm.clan.listeners.MenuListener;
import dev.zm.clan.listeners.PlayerConnectionListener;
import dev.zm.clan.menu.MenuManager;
import dev.zm.clan.service.ClanService;
import dev.zm.clan.service.HomeService;
import dev.zm.clan.service.InviteService;
import dev.zm.clan.service.MemberService;
import dev.zm.clan.service.RankService;
import dev.zm.clan.storage.DatabaseManager;
import dev.zm.clan.task.AutoSaveTask;
import dev.zm.clan.task.InviteExpireTask;
import dev.zm.clan.zMClans;
import org.bukkit.Bukkit;

import java.util.Objects;

/**
 * Orchestrates the ordered startup of all plugin components.
 * Order matters: Config → DB → Services → Commands → Listeners → Tasks.
 */
public final class PluginBootstrap {

    private final zMClans plugin;

    public PluginBootstrap(zMClans plugin) {
        this.plugin = plugin;
    }

    public void boot() {

        ConfigManager config = new ConfigManager(plugin);
        config.load();
        plugin.setConfigManager(config);

        Messages messages = new Messages(plugin);
        messages.load();
        plugin.setMessages(messages);

        DatabaseManager db = new DatabaseManager(plugin);
        if (!db.connect()) {
            plugin.getLogger().severe("Could not connect to database! Disabling plugin.");
            plugin.setDatabaseManager(db);
            Bukkit.getPluginManager().disablePlugin(plugin);
            return;
        }
        plugin.setDatabaseManager(db);
        db.runMigrations();

        RankService rankService = new RankService(plugin);
        rankService.load();
        plugin.setRankService(rankService);

        ClanService clanService = new ClanService(plugin);
        plugin.setClanService(clanService);

        MemberService memberService = new MemberService(plugin);
        plugin.setMemberService(memberService);
        memberService.loadAll();

        InviteService inviteService = new InviteService(plugin);
        plugin.setInviteService(inviteService);

        HomeService homeService = new HomeService(plugin);
        plugin.setHomeService(homeService);
        plugin.setProgressionService(new dev.zm.clan.service.ProgressionService(plugin));
        plugin.setRelationService(new dev.zm.clan.service.RelationService(plugin));
        plugin.setNotificationService(new dev.zm.clan.service.NotificationService(plugin));

        MenuManager menuManager = new MenuManager(plugin);
        menuManager.loadAll();
        plugin.setMenuManager(menuManager);

        HookManager hookManager = new HookManager(plugin);
        hookManager.registerAll();
        plugin.setHookManager(hookManager);

        ClanCommand clanCommand = new ClanCommand(plugin);
        Objects.requireNonNull(plugin.getCommand("clan")).setExecutor(clanCommand);
        Objects.requireNonNull(plugin.getCommand("clan")).setTabCompleter(clanCommand);
        AdminCommand adminCommand = new AdminCommand(plugin);
        Objects.requireNonNull(plugin.getCommand("zmclan")).setExecutor(adminCommand);
        Objects.requireNonNull(plugin.getCommand("zmclan")).setTabCompleter(adminCommand);

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerConnectionListener(plugin), plugin);
        ClanChatListener chatListener = new ClanChatListener(plugin);
        plugin.setClanChatListener(chatListener);
        pm.registerEvents(chatListener, plugin);
        dev.zm.clan.listeners.ChatPromptListener promptListener = new dev.zm.clan.listeners.ChatPromptListener(plugin);
        plugin.setChatPromptListener(promptListener);
        FriendlyFireListener ffListener = new FriendlyFireListener(plugin);
        plugin.setFriendlyFireListener(ffListener);
        pm.registerEvents(ffListener, plugin);
        pm.registerEvents(new MenuListener(plugin), plugin);

        AutoSaveTask autoSaveTask = new AutoSaveTask(plugin);
        plugin.setAutoSaveTask(autoSaveTask);
        autoSaveTask.start();
        new InviteExpireTask(plugin).start();

        dev.zm.clan.task.VersionChecker versionChecker = new dev.zm.clan.task.VersionChecker(plugin);
        pm.registerEvents(versionChecker, plugin);
        versionChecker.check();

    }
}
