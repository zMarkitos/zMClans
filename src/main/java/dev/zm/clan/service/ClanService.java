package dev.zm.clan.service;

import dev.zm.clan.clan.model.Clan;
import dev.zm.clan.clan.repository.ClanRepository;
import dev.zm.clan.config.ColorUtil;
import dev.zm.clan.api.event.ClanCreatedEvent;
import dev.zm.clan.api.event.ClanLevelChangeEvent;
import dev.zm.clan.api.event.ClanPointsChangeEvent;
import dev.zm.clan.member.model.ClanMember;
import dev.zm.clan.rank.model.ClanRank;
import dev.zm.clan.zMClans;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core business logic for clan lifecycle: create, disband, info, search.
 * All DB writes are executed asynchronously during runtime; in-memory cache
 * is updated immediately so reads are always instant on the main thread.
 * Synchronous save is provided for server shutdown.
 */
public final class ClanService {

    private final zMClans plugin;
    private final ClanRepository clanRepo;

    /** clan ID → Clan */
    private final Map<UUID, Clan> clanById = new ConcurrentHashMap<>();
    /** lowercase name → clan ID */
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();
    /** lowercase clean tag → clan ID */
    private final Map<String, UUID> tagIndex = new ConcurrentHashMap<>();
    /** player UUID → clan ID */
    private final Map<UUID, UUID> playerIndex = new ConcurrentHashMap<>();
    private final Object createLock = new Object();
    private final Map<String, LeaderboardCache> leaderboardCache = new ConcurrentHashMap<>();

    private record LeaderboardCache(List<Clan> clans, long expiresAt) {
    }

    public ClanService(zMClans plugin) {
        this.plugin = plugin;
        this.clanRepo = new ClanRepository(plugin.getDatabaseManager(), plugin.getLogger());
        loadAll();
    }

    public void loadAll() {
        invalidateLeaderboardCache();
        clanById.clear();
        nameIndex.clear();
        tagIndex.clear();
        playerIndex.clear();

        List<Clan> all = clanRepo.findAll();
        for (Clan c : all) {
            clanById.put(c.getId(), c);
            nameIndex.put(c.getName().toLowerCase(Locale.ROOT), c.getId());
            tagIndex.put(ColorUtil.strip(c.getTag()).toLowerCase(Locale.ROOT), c.getId());
        }
    }

    public enum CreateResult {
        OK, NAME_TAKEN, TAG_TAKEN, NAME_INVALID, NAME_BLACKLISTED, TAG_INVALID, TAG_FORMAT_BLOCKED,
        NO_MONEY, ALREADY_IN_CLAN, NO_PERMISSION
    }

    public CreateResult create(Player player, String name, String tag) {
        if (player == null || !player.hasPermission("zmclan.player.create")) {
            return CreateResult.NO_PERMISSION;
        }
        synchronized (createLock) {
            if (playerIndex.containsKey(player.getUniqueId()))
                return CreateResult.ALREADY_IN_CLAN;

            var cfg = plugin.getConfigManager();
            String cleanName = name == null ? null : name.trim();
            if (cleanName == null || !cleanName.matches(cfg.getNamePattern())
                    || cleanName.length() < cfg.getNameMinLength()
                    || cleanName.length() > cfg.getNameMaxLength())
                return CreateResult.NAME_INVALID;
            String normalizedName = cleanName.toLowerCase(Locale.ROOT);
            if (cfg.getNameBlacklist().stream().filter(Objects::nonNull)
                    .map(blocked -> blocked.trim().toLowerCase(Locale.ROOT))
                    .anyMatch(normalizedName::equals))
                return CreateResult.NAME_BLACKLISTED;
            if (nameIndex.containsKey(normalizedName))
                return CreateResult.NAME_TAKEN;

            String storedTag;
            String indexedTag;
            boolean customTag = tag != null && !tag.isBlank();

            if (!customTag) {
                storedTag = "";
            } else {
                if (ColorUtil.containsBlockedFormat(tag, cfg.getBlockedTagFormatCodes())) {
                    return CreateResult.TAG_FORMAT_BLOCKED;
                }
                storedTag = ColorUtil.colorize(tag);
            }

            String cleanTag = customTag ? ColorUtil.strip(storedTag) : cleanName;
            if (!cleanTag.matches(cfg.getTagPattern()) || cleanTag.length() < cfg.getTagMinLength()
                    || cleanTag.length() > cfg.getTagMaxLength())
                return CreateResult.TAG_INVALID;
            indexedTag = cleanTag.toLowerCase(Locale.ROOT);

            if (tagIndex.containsKey(indexedTag))
                return CreateResult.TAG_TAKEN;

            double cost = cfg.getCreationCost();
            if (!chargeCreationCost(player, cost))
                return CreateResult.NO_MONEY;

            // Find the leader rank
            String leaderRankId = plugin.getRankService().getLeaderRank()
                    .map(ClanRank::getId).orElse("leader");

            Clan clan = Clan.builder()
                    .name(cleanName)
                    .tag(storedTag)
                    .leader(player.getUniqueId())
                    .leaderRankId(leaderRankId)
                    .friendlyFire(cfg.isFriendlyFire())
                    .points(plugin.getProgressionService() == null ? 0L
                            : plugin.getProgressionService().getPointsForAction("create-clan"))
                    .createdAt(Instant.now())
                    .build();

            ClanMember leaderMember = new ClanMember(
                    player.getUniqueId(), clan.getId(), leaderRankId, Instant.now());

            // Update cache first
            clanById.put(clan.getId(), clan);
            nameIndex.put(normalizedName, clan.getId());
            tagIndex.put(indexedTag, clan.getId());
            clan.addMember(player.getUniqueId());
            playerIndex.put(player.getUniqueId(), clan.getId());
            invalidateLeaderboardCache();

            // Register member in MemberService immediately!
            if (plugin.getMemberService() != null) {
                plugin.getMemberService().registerMember(leaderMember);
            }

            Clan clanToPersist = clan.copyForPersistence();
            ClanMember memberToPersist = leaderMember.copyForPersistence();
            plugin.getDatabaseManager().executeAsync("create clan " + cleanName, () -> {
                if (!clanRepo.insert(clanToPersist))
                    return;
                if (plugin.getMemberService() != null) {
                    plugin.getMemberService().getMemberRepo().insert(memberToPersist);
                }
            });
            Bukkit.getPluginManager()
                    .callEvent(new ClanCreatedEvent(plugin.getAPI().snapshot(clan), player.getUniqueId()));

            // Trigger create notification (title, actionbar, sound)
            if (plugin.getNotificationService() != null) {
                plugin.getNotificationService().sendNotification(player, "clan-create",
                        "clan", cleanName,
                        "tag", storedTag,
                        "player", player.getName());
            }

            Bukkit.broadcastMessage(plugin.getMessages().get("clan-created-global",
                    "clan", clan.getName(),
                    "tag", clan.getTag().isBlank() ? clan.getName() : clan.getTag(),
                    "player", player.getName()));
            if (clan.getPoints() > 0) {
                int level = plugin.getProgressionService() == null ? 1
                        : plugin.getProgressionService().getLevel(clan);
                int startLevel = plugin.getConfigManager().raw().getInt("progression.start-level", 1);
                notifyPointsEarned(clan, clan.getPoints(), level > startLevel, level);
            }

            return CreateResult.OK;
        }
    }

    private boolean chargeCreationCost(Player player, double cost) {
        if (!Double.isFinite(cost) || cost < 0)
            return false;
        if (cost == 0 || (plugin.getConfigManager().isCreationCostBypassEnabled()
                && player.hasPermission("zmclans.clan.bypass.cost"))
                || !plugin.getConfigManager().raw().getBoolean("economy.enabled", true))
            return true;

        String mode = plugin.getConfigManager().raw().getString("economy.mode", "VAULT");
        if (mode == null)
            return false;
        return switch (mode.trim().toUpperCase(Locale.ROOT)) {
            case "NONE" -> true;
            case "VAULT", "AUTO" -> chargeVault(player, cost);
            case "CUSTOM" -> chargeCustom(player, cost);
            default -> {
                plugin.getLogger().warning("Invalid economy.mode '" + mode + "'; clan creation denied.");
                yield false;
            }
        };
    }

    private boolean chargeVault(Player player, double cost) {
        var vault = plugin.getHookManager().getVaultHook();
        if (vault == null || !vault.hasEconomy()) {
            plugin.getLogger().warning("Clan creation requires Vault and a registered economy provider.");
            return false;
        }
        return vault.has(player, cost) && vault.withdraw(player, cost);
    }

    private boolean chargeCustom(Player player, double cost) {
        if (!plugin.getHookManager().isPapiPresent()) {
            plugin.getLogger().warning("economy.mode=CUSTOM requires PlaceholderAPI.");
            return false;
        }
        var config = plugin.getConfigManager().raw();
        String placeholder = config.getString("economy.custom.balance-placeholder", "").trim();
        String command = config.getString("economy.custom.take-command", "").trim();
        if (placeholder.isEmpty() || command.isEmpty())
            return false;

        String rawBalance = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, placeholder);
        double balance;
        try {
            balance = Double.parseDouble(rawBalance.replace(",", "").trim());
        } catch (NumberFormatException ex) {
            plugin.getLogger()
                    .warning("Custom economy balance placeholder returned a non-numeric value for " + player.getName());
            return false;
        }
        if (!Double.isFinite(balance) || balance < cost)
            return false;

        String amount = java.math.BigDecimal.valueOf(cost).stripTrailingZeros().toPlainString();
        command = command.replace("{player}", player.getName()).replace("{amount}", amount).replaceFirst("^/", "");
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    public enum ModTagResult {
        OK,
        TAG_INVALID,
        TAG_FORMAT_BLOCKED,
        TAG_TAKEN,
        NO_CLAN,
        NO_PERMISSION
    }

    public ModTagResult modifyTag(Player player, String rawTag) {
        if (!player.hasPermission("zmclan.player.modtag")) {
            return ModTagResult.NO_PERMISSION;
        }
        Clan clan = findByPlayer(player.getUniqueId()).orElse(null);
        if (clan == null)
            return ModTagResult.NO_CLAN;

        if (!clan.getLeader().equals(player.getUniqueId())
                && !plugin.getMemberService().hasPermission(player.getUniqueId(), ClanRank.Permission.MODTAG)) {
            return ModTagResult.NO_PERMISSION;
        }

        var cfg = plugin.getConfigManager();
        if (ColorUtil.containsBlockedFormat(rawTag, cfg.getBlockedTagFormatCodes())) {
            return ModTagResult.TAG_FORMAT_BLOCKED;
        }
        String formattedTag = ColorUtil.colorize(rawTag);
        String cleanTag = ColorUtil.strip(formattedTag);

        if (cleanTag.isEmpty()
                || cleanTag.length() < cfg.getTagMinLength()
                || cleanTag.length() > cfg.getTagMaxLength()
                || !cleanTag.matches(cfg.getTagPattern())) {
            return ModTagResult.TAG_INVALID;
        }

        String cleanLower = cleanTag.toLowerCase(Locale.ROOT);
        UUID existingClanId = tagIndex.get(cleanLower);
        if (existingClanId != null && !existingClanId.equals(clan.getId())) {
            return ModTagResult.TAG_TAKEN;
        }

        // Remove previous clean tag from index
        String oldClean = ColorUtil.strip(clan.getTag()).toLowerCase(Locale.ROOT);
        tagIndex.remove(oldClean);

        // Update clan tag with formatted tag (MiniMessage/hex converted to legacy
        // section)
        clan.setTag(formattedTag);
        tagIndex.put(cleanLower, clan.getId());

        saveAsync(clan);

        if (plugin.getNotificationService() != null) {
            plugin.getNotificationService().sendNotification(player, "tag-updated",
                    "clan", clan.getName(),
                    "tag", formattedTag);
        }

        return ModTagResult.OK;
    }

    /**
     * Adds points to a clan and notifies its online members about gains and
     * level-ups.
     * Always call this instead of clan.addPoints() directly when points changes
     * matter for progression.
     */
    public void addPointsAndCheckLevelUp(Clan clan, long delta) {
        if (clan == null || plugin.getProgressionService() == null) {
            if (clan != null)
                clan.addPoints(delta);
            return;
        }
        int levelBefore = plugin.getProgressionService().getLevel(clan);
        long pointsBefore = clan.getPoints();
        clan.addPoints(delta);
        invalidateLeaderboardCache();
        if (clan.getPoints() != pointsBefore) {
            Bukkit.getPluginManager().callEvent(new ClanPointsChangeEvent(clan.getId(), pointsBefore,
                    clan.getPoints(), clan.getPoints() - pointsBefore));
        }
        int levelAfter = plugin.getProgressionService().getLevel(clan);
        if (levelAfter != levelBefore) {
            Bukkit.getPluginManager().callEvent(new ClanLevelChangeEvent(clan.getId(), levelBefore, levelAfter));
        }
        long pointsGained = Math.max(0L, clan.getPoints() - pointsBefore);
        if (pointsGained > 0) {
            notifyPointsEarned(clan, pointsGained, levelAfter > levelBefore, levelAfter);
        }
        if (levelAfter > levelBefore) {
            String msg = plugin.getMessages().get("clan-levelup-chat",
                    "clan", clan.getName(),
                    "level", String.valueOf(levelAfter),
                    "points", String.valueOf(clan.getPoints()));
            sendToOnlineMembers(clan, msg);
        }
        saveAsync(clan);
    }

    private void notifyPointsEarned(Clan clan, long gained, boolean leveledUp, int level) {
        if (gained <= 0) {
            return;
        }
        String eventKey = leveledUp ? "clan-levelup" : "clan-points-earned";
        for (UUID uuid : clan.getMemberIds()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && plugin.getNotificationService() != null) {
                plugin.getNotificationService().sendNotification(player, eventKey,
                        "clan", clan.getName(),
                        "tag", clan.getTag(),
                        "gained", String.valueOf(gained),
                        "level", String.valueOf(level),
                        "points", String.valueOf(clan.getPoints()));
            }
        }
        String message = plugin.getMessages().get("clan-points-earned-chat",
                "clan", clan.getName(),
                "gained", String.valueOf(gained),
                "level", String.valueOf(level),
                "points", String.valueOf(clan.getPoints()));
        sendToOnlineMembers(clan, message);
    }

    private void sendToOnlineMembers(Clan clan, String message) {
        for (UUID uuid : clan.getMemberIds()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    public void setPointsAndCheckLevelUp(Clan clan, long points) {
        if (clan == null)
            return;
        long target = Math.max(0L, points);
        addPointsAndCheckLevelUp(clan, target - clan.getPoints());
    }

    public boolean disband(UUID clanId) {
        return disband(clanId, plugin.getMessages().get("system-actor"));
    }

    public boolean disband(UUID clanId, String actorName) {
        Player actorPlayer = actorName == null ? null : Bukkit.getPlayerExact(actorName);
        if (actorPlayer != null && !actorPlayer.hasPermission("zmclans.admin.disband")
                && (!actorPlayer.hasPermission("zmclan.player.disband")
                        || !plugin.getMemberService().hasPermission(actorPlayer.getUniqueId(),
                                ClanRank.Permission.DISBAND))) {
            return false;
        }
        Clan clan = clanById.remove(clanId);
        if (clan == null)
            return false;
        invalidateLeaderboardCache();

        if (plugin.getNotificationService() != null) {
            plugin.getNotificationService().sendToClan(clan, "clan-disband",
                    "clan", clan.getName(),
                    "tag", clan.getTag());
        }

        String actor = actorName == null || actorName.isBlank()
                ? plugin.getMessages().get("system-actor")
                : actorName;
        Bukkit.broadcastMessage(plugin.getMessages().get("clan-disbanded-global",
                "clan", clan.getName(),
                "tag", clan.getTag().isBlank() ? clan.getName() : clan.getTag(),
                "player", actor));

        nameIndex.remove(clan.getName().toLowerCase(Locale.ROOT));
        tagIndex.remove(ColorUtil.strip(clan.getTag()).toLowerCase(Locale.ROOT));
        // Remove reciprocal relations from surviving clans before deleting this clan.
        Set<UUID> relatedIds = new HashSet<>(clan.getAllyIds());
        relatedIds.addAll(clan.getRivalIds());
        for (UUID relatedId : relatedIds) {
            Clan related = clanById.get(relatedId);
            if (related != null) {
                related.removeRelation(clanId);
                saveAsync(related);
            }
        }

        if (plugin.getRelationService() != null) {
            plugin.getRelationService().forgetClan(clanId);
        }
        if (plugin.getInviteService() != null) {
            plugin.getInviteService().forgetClan(clanId);
        }

        List<UUID> members = new ArrayList<>(clan.getMemberIds());
        for (UUID mUuid : members) {
            playerIndex.remove(mUuid);
            if (plugin.getMemberService() != null) {
                plugin.getMemberService().removeMemberForDisband(mUuid);
            }
        }

        plugin.getDatabaseManager().executeAsync("disband clan " + clanId, () -> {
            if (plugin.getMemberService() != null) {
                plugin.getMemberService().getMemberRepo().deleteByClan(clanId);
            }
            clanRepo.delete(clanId);
        });

        return true;
    }

    public void saveAll() {
        for (Clan clan : clanById.values()) {
            Clan snapshot = clan.copyForPersistence();
            plugin.getDatabaseManager().executeAsync("save clan " + clan.getId(),
                    () -> clanRepo.update(snapshot));
        }
    }

    public void saveAllSync() {
        for (Clan c : clanById.values()) {
            clanRepo.update(c);
        }
    }

    public void saveAsync(Clan clan) {
        if (clan != null) {
            Clan snapshot = clan.copyForPersistence();
            plugin.getDatabaseManager().executeAsync("save clan " + clan.getId(),
                    () -> clanRepo.update(snapshot));
        }
    }

    public Optional<Clan> findById(UUID id) {
        if (id == null)
            return Optional.empty();
        return Optional.ofNullable(clanById.get(id));
    }

    public Optional<Clan> findByName(String name) {
        if (name == null)
            return Optional.empty();
        UUID id = nameIndex.get(name.toLowerCase(Locale.ROOT));
        return id == null ? Optional.empty() : findById(id);
    }

    public Optional<Clan> findByTag(String tag) {
        if (tag == null)
            return Optional.empty();
        UUID id = tagIndex.get(ColorUtil.strip(tag).toLowerCase(Locale.ROOT));
        return id == null ? Optional.empty() : findById(id);
    }

    public Optional<Clan> findByPlayer(UUID uuid) {
        if (uuid == null)
            return Optional.empty();
        UUID id = playerIndex.get(uuid);
        return id == null ? Optional.empty() : findById(id);
    }

    public boolean isInClan(UUID uuid) {
        return uuid != null && playerIndex.containsKey(uuid);
    }

    public boolean nameExists(String name) {
        return name != null && nameIndex.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public boolean tagExists(String tag) {
        return tag != null && tagIndex.containsKey(ColorUtil.strip(tag).toLowerCase(Locale.ROOT));
    }

    public List<Clan> allClans() {
        return new ArrayList<>(clanById.values());
    }

    public List<Clan> findAll() {
        return findAll("MEMBERS");
    }

    public List<Clan> findAll(String sortBy) {
        String mode = sortBy == null ? "MEMBERS" : sortBy.toUpperCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        LeaderboardCache cached = leaderboardCache.get(mode);
        if (cached != null && cached.expiresAt() > now)
            return new ArrayList<>(cached.clans());
        Comparator<Clan> comparator = switch (mode) {
            case "POINTS" -> Comparator.comparingLong(Clan::getPoints).reversed();
            case "KILLS" -> Comparator.comparingLong(Clan::getKills).reversed();
            case "KDR" -> Comparator.comparingDouble(Clan::getKdr).reversed();
            case "DEATHS" -> Comparator.comparingLong(Clan::getDeaths).reversed();
            default -> Comparator.comparingInt(Clan::getMemberCount).reversed();
        };
        List<Clan> clans = allClans();
        clans.sort(comparator.thenComparing(Clan::getName, String.CASE_INSENSITIVE_ORDER));
        long ttl = plugin.getConfigManager() == null ? 300L
                : plugin.getConfigManager().getLeaderboardRefreshInterval();
        leaderboardCache.put(mode, new LeaderboardCache(List.copyOf(clans), now + ttl * 1000L));
        return clans;
    }

    public void invalidateLeaderboardCache() {
        leaderboardCache.clear();
    }

    public void indexPlayer(UUID player, UUID clan) {
        if (player != null && clan != null) {
            playerIndex.put(player, clan);
            invalidateLeaderboardCache();
        }
    }

    public void unindexPlayer(UUID player) {
        if (player != null) {
            playerIndex.remove(player);
            invalidateLeaderboardCache();
        }
    }
}
