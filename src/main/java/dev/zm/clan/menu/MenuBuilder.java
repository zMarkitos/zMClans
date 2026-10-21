package dev.zm.clan.menu;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.zm.clan.clan.model.Clan;
import dev.zm.clan.config.ColorUtil;
import dev.zm.clan.member.model.ClanMember;
import dev.zm.clan.rank.model.ClanRank;
import dev.zm.clan.service.RelationService;
import dev.zm.clan.zMClans;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * Builds Bukkit inventories from MenuConfig objects and opens them for players.
 */
public final class MenuBuilder {

    private MenuBuilder() {
    }

    public static void buildAndOpen(zMClans plugin, Player player, MenuConfig cfg, Map<String, String> placeholders,
            MenuManager manager) {
        ActiveMenu activeMenu = manager.getOpenMenu(player).orElse(null);
        int page = activeMenu == null ? 0 : activeMenu.getPage();
        Runnable confirmAction = activeMenu == null ? null : activeMenu.getConfirmAction();

        int maxPages = 1;
        int totalItems = 0;
        // Pre-compute pagination metadata so PAGE_INFO and titles have access to {page}
        // and {max_pages}
        if (cfg.isPaginated()) {
            int[] meta = computePaginationPlaceholders(plugin, player, cfg, page, placeholders, manager);
            totalItems = meta[0];
            maxPages = meta[1];
            page = Math.max(0, Math.min(page, maxPages - 1));
            if (activeMenu != null && activeMenu.getPage() != page) {
                activeMenu.setPage(page);
            }
            placeholders.put("page", String.valueOf(page + 1));
            placeholders.put("max_pages", String.valueOf(maxPages));
        }

        MenuHolder holder = new MenuHolder(cfg, page, confirmAction);
        holder.setTotalItems(totalItems);
        holder.setMaxPages(maxPages);

        String rawTitle = cfg.getRawTitle();
        String title = ColorUtil.colorize(applyPlaceholders(rawTitle, placeholders));
        int size = Math.max(9, Math.min(54, cfg.getRows() * 9));

        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        boolean hasPrev = holder.hasPrevPage();
        boolean hasNext = holder.hasNextPage();

        // Place static items (ignore dynamic templates like MEMBER_ENTRY or CLAN_ENTRY
        // that have slot < 0 and no slots list)
        for (ItemConfig itemCfg : cfg.getItems().values()) {
            if (itemCfg.getSlot() < 0 && (!itemCfg.hasMultiSlots() || itemCfg.getSlots().isEmpty())) {
                continue;
            }

            String action = itemCfg.getAction();
            if ("OPEN_ALLY_HOMES".equalsIgnoreCase(action)
                    && manager.getOpenMenu(player).map(a -> "clan_ally_homes".equals(a.getMenuKey())).orElse(false)) {
                continue;
            }
            if (cfg.isPaginated()) {
                if (("PAGE_PREV".equalsIgnoreCase(action) || "PREV_PAGE".equalsIgnoreCase(action)) && !hasPrev) {
                    placeFillerIfAvailable(cfg, inv, itemCfg.getSlot(), placeholders, player);
                    continue;
                }
                if (("PAGE_NEXT".equalsIgnoreCase(action) || "NEXT_PAGE".equalsIgnoreCase(action)) && !hasNext) {
                    placeFillerIfAvailable(cfg, inv, itemCfg.getSlot(), placeholders, player);
                    continue;
                }
            }

            ItemStack stack = createItemStack(itemCfg, placeholders, player);
            if (itemCfg.hasMultiSlots()) {
                for (int slot : itemCfg.getSlots()) {
                    if (slot >= 0 && slot < size) {
                        inv.setItem(slot, stack.clone());
                        holder.setSlotAction(slot, itemCfg);
                    }
                }
            } else if (itemCfg.getSlot() >= 0 && itemCfg.getSlot() < size) {
                inv.setItem(itemCfg.getSlot(), stack);
                holder.setSlotAction(itemCfg.getSlot(), itemCfg);
            }
        }

        // Handle pagination contents
        if (cfg.isPaginated()) {
            handlePagination(plugin, player, cfg, holder, inv, page, placeholders, manager);
        }

        // Play sound if configured
        if (cfg.getOpenSound() != null && !cfg.getOpenSound().isBlank()) {
            try {
                Sound sound = Sound.valueOf(cfg.getOpenSound().toUpperCase(Locale.ROOT));
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException ignored) {
            }
        }

        player.openInventory(inv);
    }

    private static void placeFillerIfAvailable(MenuConfig cfg, org.bukkit.inventory.Inventory inv, int slot,
            Map<String, String> placeholders, Player player) {
        if (slot < 0 || slot >= inv.getSize())
            return;
        ItemConfig filler = cfg.getItems().get("FILLER");
        if (filler == null)
            filler = cfg.getItems().get("FILLER_GRAY");
        if (filler == null) {
            for (ItemConfig it : cfg.getItems().values()) {
                if (it.getKey().toUpperCase(Locale.ROOT).startsWith("FILLER")) {
                    filler = it;
                    break;
                }
            }
        }
        if (filler != null) {
            inv.setItem(slot, createItemStack(filler, placeholders, player));
        }
    }

    private static int[] computePaginationPlaceholders(zMClans plugin, Player player, MenuConfig cfg, int page,
            Map<String, String> ph, MenuManager manager) {
        int totalItems = 0;
        int pageSize = Math.max(1, cfg.getPageSlots().isEmpty() ? cfg.getPageSize() : cfg.getPageSlots().size());
        Clan ownClan = plugin.getClanService().findByPlayer(player.getUniqueId()).orElse(null);

        switch (cfg.getKey()) {
            case "clan_members" -> {
                UUID targetClanId = manager.getOpenMenu(player).map(ActiveMenu::getTargetClanId).orElse(null);
                Optional<Clan> optClan;
                if (targetClanId != null) {
                    optClan = plugin.getClanService().findById(targetClanId);
                } else {
                    optClan = plugin.getClanService().findByPlayer(player.getUniqueId());
                }
                if (optClan.isPresent()) {
                    totalItems = plugin.getMemberService().getMembersOfClan(optClan.get().getId()).size();
                }
            }
            case "clan_list" -> totalItems = plugin.getClanService().allClans().size();
            case "clan_allies" -> {
                if (ownClan != null)
                    totalItems = ownClan.getAllyIds().size();
            }
            case "clan_rivals" -> {
                if (ownClan != null)
                    totalItems = ownClan.getRivalIds().size();
            }
            case "clan_invite" -> totalItems = (int) Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !plugin.getClanService().isInClan(p.getUniqueId())
                            && !p.getUniqueId().equals(player.getUniqueId()))
                    .count();
            case "clan_invites" -> {
                int pending = plugin.getInviteService().getPending(player.getUniqueId()).size();
                int open = plugin.getClanService().isInClan(player.getUniqueId()) ? 0
                        : (int) plugin.getClanService().allClans().stream().filter(Clan::isPublicJoin).count();
                totalItems = pending + open;
            }
            case "clan_homes" -> {
                boolean allyMode = manager.getOpenMenu(player).map(a -> "clan_ally_homes".equals(a.getMenuKey()))
                        .orElse(false);
                if (allyMode)
                    totalItems = getAllyHomeEntries(plugin, player).size();
                else
                    totalItems = plugin.getClanService().findByPlayer(player.getUniqueId())
                            .map(c -> c.getHomes().size()).orElse(0);
            }
        }

        int maxPages = Math.max(1, (int) Math.ceil((double) totalItems / pageSize));
        ph.put("page", String.valueOf(page + 1));
        ph.put("max_pages", String.valueOf(maxPages));
        ph.put("total_items", String.valueOf(totalItems));
        return new int[] { totalItems, maxPages };
    }

    private static void handlePagination(zMClans plugin, Player player, MenuConfig cfg, MenuHolder holder,
            org.bukkit.inventory.Inventory inv, int page, Map<String, String> placeholders, MenuManager manager) {
        List<Integer> slots = cfg.getPageSlots();
        if (slots.isEmpty())
            return;

        if ("clan_members".equals(cfg.getKey())) {
            UUID targetClanId = manager.getOpenMenu(player).map(ActiveMenu::getTargetClanId).orElse(null);
            Optional<Clan> optClan;
            if (targetClanId != null) {
                optClan = plugin.getClanService().findById(targetClanId);
            } else {
                optClan = plugin.getClanService().findByPlayer(player.getUniqueId());
            }
            if (optClan.isEmpty())
                return;
            Clan clan = optClan.get();
            List<ClanMember> members = new ArrayList<>(plugin.getMemberService().getMembersOfClan(clan.getId()));
            // Sort members: leaders and higher ranks first
            members.sort(Comparator.comparingInt((ClanMember m) -> {
                return plugin.getRankService().getRank(m.getRankId()).map(ClanRank::getPriority).orElse(0);
            }).reversed());

            boolean isOwnClan = plugin.getClanService().findByPlayer(player.getUniqueId())
                    .map(c -> c.getId().equals(clan.getId())).orElse(false);
            boolean canManage = isOwnClan
                    && (plugin.getMemberService().hasPermission(player.getUniqueId(), ClanRank.Permission.PROMOTE)
                            || plugin.getMemberService().hasPermission(player.getUniqueId(), ClanRank.Permission.KICK)
                            || plugin.getMemberService().hasPermission(player.getUniqueId(),
                                    ClanRank.Permission.TRANSFER_LEADER));

            int pageSize = slots.size();
            int startIndex = page * pageSize;
            int endIndex = Math.min(startIndex + pageSize, members.size());

            ItemConfig template = cfg.getItems().get("MEMBER_ENTRY");

            for (int i = startIndex; i < endIndex; i++) {
                ClanMember m = members.get(i);
                int slot = slots.get(i - startIndex);
                OfflinePlayer op = Bukkit.getOfflinePlayer(m.getPlayerUUID());
                String pName = op.getName() != null ? op.getName() : "Unknown";

                ClanRank rank = plugin.getRankService().getRank(m.getRankId()).orElse(null);
                String rankDisplay = rank != null ? rank.getDisplayName() : m.getRankId();
                String rankColor = rank != null ? rank.getColor() : "&f";
                String joinDate = m.getJoinedAt().toString().length() >= 10
                        ? m.getJoinedAt().toString().substring(0, 10)
                        : m.getJoinedAt().toString();

                Map<String, String> itemPh = new HashMap<>(placeholders);
                itemPh.put("player", pName);
                itemPh.put("rank", rankDisplay);
                itemPh.put("rank_color", rankColor);
                itemPh.put("joined", joinDate);
                itemPh.put("online", op.isOnline() ? "&aOnline" : "&cOffline");
                itemPh.put("uuid", m.getPlayerUUID().toString());
                itemPh.put("kills", String.valueOf(m.getKills()));
                itemPh.put("deaths", String.valueOf(m.getDeaths()));
                itemPh.put("kdr", String.format(Locale.ROOT, "%.2f", m.getKdr()));

                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) skull.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(op);
                    if (template != null) {
                        meta.setDisplayName(ColorUtil.colorize(applyPlaceholders(template.getDisplayName(), itemPh)));
                        List<String> lore = new ArrayList<>();
                        for (String l : template.getLore()) {
                            // If player cannot manage, don't show manage prompt
                            if (!canManage && l.toLowerCase(Locale.ROOT).contains("click to manage")) {
                                continue;
                            }
                            lore.add(ColorUtil.colorize(applyPlaceholders(l, itemPh)));
                        }
                        meta.setLore(lore);
                    } else {
                        meta.setDisplayName(ColorUtil.colorize(rankColor + "&l" + pName));
                        List<String> lore = new ArrayList<>();
                        lore.add(ColorUtil.colorize(" &8| &7Rank: " + rankColor + rankDisplay));
                        lore.add(ColorUtil.colorize(" &8| &7Status: " + (op.isOnline() ? "&aOnline" : "&cOffline")));
                        lore.add(ColorUtil.colorize(" &8| &7Joined: &f" + joinDate));
                        if (canManage) {
                            lore.add("");
                            lore.add(ColorUtil.colorize("&#55FFAA➜ &fClick to manage member"));
                        }
                        meta.setLore(lore);
                    }
                    skull.setItemMeta(meta);
                }

                inv.setItem(slot, skull);
                holder.setSlotData(slot, m);
            }
        } else if ("clan_list".equals(cfg.getKey()) || "clan_allies".equals(cfg.getKey())
                || "clan_rivals".equals(cfg.getKey())) {
            Clan ownClan = plugin.getClanService().findByPlayer(player.getUniqueId()).orElse(null);

            List<Clan> allClans;
            if ("clan_allies".equals(cfg.getKey())) {
                allClans = ownClan == null ? new ArrayList<>()
                        : ownClan.getAllyIds().stream()
                                .map(id -> plugin.getClanService().findById(id).orElse(null))
                                .filter(Objects::nonNull)
                                .sorted(Comparator.comparing(Clan::getName, String.CASE_INSENSITIVE_ORDER))
                                .collect(java.util.stream.Collectors.toList());
            } else if ("clan_rivals".equals(cfg.getKey())) {
                allClans = ownClan == null ? new ArrayList<>()
                        : ownClan.getRivalIds().stream()
                                .map(id -> plugin.getClanService().findById(id).orElse(null))
                                .filter(Objects::nonNull)
                                .sorted(Comparator.comparing(Clan::getName, String.CASE_INSENSITIVE_ORDER))
                                .collect(java.util.stream.Collectors.toList());
            } else {
                // clan_list: show all, sorted by leaderboard mode
                String sortMode = manager.getLeaderboardMode(player);
                allClans = plugin.getClanService().findAll(sortMode);
            }

            int pageSize = slots.size();
            int startIndex = page * pageSize;
            int endIndex = Math.min(startIndex + pageSize, allClans.size());

            ItemConfig template = cfg.getItems().get("CLAN_ENTRY");

            for (int i = startIndex; i < endIndex; i++) {
                Clan c = allClans.get(i);
                int slot = slots.get(i - startIndex);
                OfflinePlayer leader = Bukkit.getOfflinePlayer(c.getLeader());
                String leaderName = leader.getName() != null ? leader.getName() : "Unknown";

                Map<String, String> itemPh = new HashMap<>(placeholders);
                itemPh.put("position", String.valueOf(i + 1));
                itemPh.put("clan", c.getName());
                itemPh.put("tag", c.getTag());
                itemPh.put("leader", leaderName);
                itemPh.put("members", String.valueOf(c.getMemberCount()));
                itemPh.put("max", String.valueOf(plugin.getMemberService().getMaxMembers(c)));
                itemPh.put("points", String.valueOf(c.getPoints()));
                itemPh.put("kills", String.valueOf(c.getKills()));
                itemPh.put("deaths", String.valueOf(c.getDeaths()));
                itemPh.put("kdr", String.format(Locale.ROOT, "%.2f", c.getKdr()));
                itemPh.put("clan_kdr", String.format(Locale.ROOT, "%.2f", c.getKdr()));
                itemPh.put("clan_id", c.getId().toString());
                itemPh.put("relation_action", relationActionText(plugin, ownClan, c, cfg.getKey()));

                ItemStack banner = new ItemStack(Material.SHIELD);
                ItemMeta meta = banner.getItemMeta();
                if (meta != null) {
                    if (template != null) {
                        meta.setDisplayName(ColorUtil.colorize(applyPlaceholders(template.getDisplayName(), itemPh)));
                        List<String> lore = new ArrayList<>();
                        for (String l : template.getLore()) {
                            lore.add(ColorUtil.colorize(applyPlaceholders(l, itemPh)));
                        }
                        meta.setLore(lore);
                    } else {
                        meta.setDisplayName(ColorUtil.colorize(
                                "&#55FFAA&l#" + (i + 1) + " &f" + c.getName() + " &8[&e" + c.getTag() + "&8]"));
                        List<String> lore = new ArrayList<>();
                        lore.add(ColorUtil.colorize(" &8| &7Leader: &f" + leaderName));
                        lore.add(ColorUtil.colorize(" &8| &7Members: &a" + c.getMemberCount() + "&7/&a"
                                + plugin.getMemberService().getMaxMembers(c)));
                        lore.add(ColorUtil.colorize(" &8| &7Points: &e" + c.getPoints()));
                        lore.add("");
                        lore.add(ColorUtil.colorize("&#55FFAA➜ &fClick for more info"));
                        meta.setLore(lore);
                    }
                    banner.setItemMeta(meta);
                }

                inv.setItem(slot, banner);
                holder.setSlotData(slot, c);
            }
        } else if ("clan_homes".equals(cfg.getKey())) {
            boolean allyMode = manager.getOpenMenu(player).map(a -> "clan_ally_homes".equals(a.getMenuKey()))
                    .orElse(false);
            int pageSize = slots.size();
            int startIndex = page * pageSize;
            ItemConfig template = cfg.getItems().get("HOME_ENTRY");
            if (allyMode) {
                List<AllyHomeEntry> homes = getAllyHomeEntries(plugin, player);
                int endIndex = Math.min(startIndex + pageSize, homes.size());
                for (int i = startIndex; i < endIndex; i++) {
                    AllyHomeEntry entry = homes.get(i);
                    int slot = slots.get(i - startIndex);
                    Map<String, String> itemPh = new HashMap<>(placeholders);
                    itemPh.put("home", entry.clanName() + ": " + entry.homeName());
                    itemPh.put("world", entry.home().getWorldName());
                    itemPh.put("x", String.valueOf((int) entry.home().getX()));
                    itemPh.put("y", String.valueOf((int) entry.home().getY()));
                    itemPh.put("z", String.valueOf((int) entry.home().getZ()));
                    itemPh.put("clan", entry.clanName());
                    ItemStack item = template != null ? createItemStack(template, itemPh, player)
                            : new ItemStack(Material.RED_BED);
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(
                                ColorUtil.colorize("&#D8A7FF" + entry.clanName() + " &8» &f" + entry.homeName()));
                        meta.setLore(List.of(
                                ColorUtil.colorize("&7" + entry.home().getWorldName() + " &8(" + itemPh.get("x") + ", "
                                        + itemPh.get("y") + ", " + itemPh.get("z") + ")"),
                                ColorUtil.colorize("&aClick to visit shared home")));
                        item.setItemMeta(meta);
                    }
                    inv.setItem(slot, item);
                    holder.setSlotData(slot, entry);
                }
            } else {
                Clan clan = plugin.getClanService().findByPlayer(player.getUniqueId()).orElse(null);
                if (clan == null)
                    return;
                List<Map.Entry<String, dev.zm.clan.clan.model.ClanHome>> homes = new ArrayList<>(
                        clan.getHomes().entrySet());
                homes.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));
                int endIndex = Math.min(startIndex + pageSize, homes.size());
                for (int i = startIndex; i < endIndex; i++) {
                    Map.Entry<String, dev.zm.clan.clan.model.ClanHome> entry = homes.get(i);
                    int slot = slots.get(i - startIndex);
                    Map<String, String> itemPh = new HashMap<>(placeholders);
                    itemPh.put("home", entry.getKey());
                    itemPh.put("world", entry.getValue().getWorldName());
                    itemPh.put("x", String.valueOf((int) entry.getValue().getX()));
                    itemPh.put("y", String.valueOf((int) entry.getValue().getY()));
                    itemPh.put("z", String.valueOf((int) entry.getValue().getZ()));
                    ItemStack item = template != null ? createItemStack(template, itemPh, player)
                            : new ItemStack(Material.RED_BED);
                    if (template == null) {
                        ItemMeta meta = item.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName(ColorUtil.colorize("&#55FFAA&l" + entry.getKey()));
                            meta.setLore(List.of(
                                    ColorUtil.colorize("&7" + entry.getValue().getWorldName() + " &8(" + itemPh.get("x")
                                            + ", " + itemPh.get("y") + ", " + itemPh.get("z") + ")"),
                                    ColorUtil.colorize("&aLeft-click to teleport"),
                                    ColorUtil.colorize("&cRight-click or Q to delete")));
                            item.setItemMeta(meta);
                        }
                    }
                    inv.setItem(slot, item);
                    holder.setSlotData(slot, entry.getKey());
                }
            }
        } else if ("clan_invite".equals(cfg.getKey())) {
            List<? extends Player> available = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !plugin.getClanService().isInClan(p.getUniqueId())
                            && !p.getUniqueId().equals(player.getUniqueId()))
                    .sorted(Comparator.comparing(Player::getName))
                    .toList();

            int pageSize = slots.size();
            int startIndex = page * pageSize;
            int endIndex = Math.min(startIndex + pageSize, available.size());

            ItemConfig template = cfg.getItems().get("PLAYER_ENTRY");

            for (int i = startIndex; i < endIndex; i++) {
                Player target = available.get(i);
                int slot = slots.get(i - startIndex);

                Map<String, String> itemPh = new HashMap<>(placeholders);
                itemPh.put("player", target.getName());
                itemPh.put("ping", String.valueOf(target.getPing()));

                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) skull.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(target);
                    if (template != null) {
                        meta.setDisplayName(ColorUtil.colorize(applyPlaceholders(template.getDisplayName(), itemPh)));
                        List<String> lore = new ArrayList<>();
                        for (String l : template.getLore()) {
                            lore.add(ColorUtil.colorize(applyPlaceholders(l, itemPh)));
                        }
                        meta.setLore(lore);
                    } else {
                        meta.setDisplayName(ColorUtil.colorize("&#55FFAA&l" + target.getName()));
                        List<String> lore = new ArrayList<>();
                        lore.add(ColorUtil.colorize(" &8| &7Status: &aOnline"));
                        lore.add(ColorUtil.colorize(" &8| &7Ping: &f" + target.getPing() + "ms"));
                        lore.add("");
                        lore.add(ColorUtil.colorize("&#55FFAA➜ &fClick to invite to clan"));
                        meta.setLore(lore);
                    }
                    skull.setItemMeta(meta);
                }

                inv.setItem(slot, skull);
                holder.setSlotData(slot, target.getUniqueId());
            }
        } else if ("clan_invites".equals(cfg.getKey())) {
            List<dev.zm.clan.invitation.model.Invitation> pending = plugin.getInviteService()
                    .getPending(player.getUniqueId());

            int pageSize = slots.size();
            int startIndex = page * pageSize;
            int endIndex = Math.min(startIndex + pageSize, pending.size());

            ItemConfig template = cfg.getItems().get("INVITATION_ENTRY");
            if (template == null)
                template = cfg.getItems().get("INVITE_ENTRY");

            for (int i = startIndex; i < endIndex; i++) {
                dev.zm.clan.invitation.model.Invitation invItem = pending.get(i);
                int slot = slots.get(i - startIndex);

                Optional<Clan> optC = plugin.getClanService().findById(invItem.getClanId());
                if (optC.isEmpty())
                    continue;
                Clan c = optC.get();
                OfflinePlayer senderOp = Bukkit.getOfflinePlayer(invItem.getSenderUuid());
                String senderName = senderOp.getName() != null ? senderOp.getName() : "Unknown";

                Map<String, String> itemPh = new HashMap<>(placeholders);
                itemPh.put("clan", c.getName());
                itemPh.put("tag", c.getTag());
                itemPh.put("sender", senderName);
                itemPh.put("members", String.valueOf(c.getMemberCount()));
                itemPh.put("max", String.valueOf(plugin.getMemberService().getMaxMembers(c)));

                ItemStack shield = new ItemStack(Material.SHIELD);
                ItemMeta meta = shield.getItemMeta();
                if (meta != null) {
                    if (template != null) {
                        meta.setDisplayName(ColorUtil.colorize(applyPlaceholders(template.getDisplayName(), itemPh)));
                        List<String> lore = new ArrayList<>();
                        for (String l : template.getLore()) {
                            lore.add(ColorUtil.colorize(applyPlaceholders(l, itemPh)));
                        }
                        meta.setLore(lore);
                    } else {
                        meta.setDisplayName(ColorUtil.colorize("&#55FFAA&l[" + c.getTag() + "] &e" + c.getName()));
                        List<String> lore = new ArrayList<>();
                        lore.add(ColorUtil.colorize(" &8| &7Invited by: &f" + senderName));
                        lore.add(ColorUtil.colorize(" &8| &7Members: &a" + c.getMemberCount() + "&7/&a"
                                + plugin.getMemberService().getMaxMembers(c)));
                        lore.add("");
                        lore.add(ColorUtil.colorize("&a&l[LEFT-CLICK] &a➜ Accept Invitation"));
                        lore.add(ColorUtil.colorize("&c&l[RIGHT-CLICK] &c➜ Deny Invitation"));
                        meta.setLore(lore);
                    }
                    shield.setItemMeta(meta);
                }

                inv.setItem(slot, shield);
                holder.setSlotData(slot, invItem);
            }
        }
    }

    private static ItemStack createItemStack(ItemConfig itemCfg, Map<String, String> placeholders, Player player) {
        String matStr = itemCfg.getMaterial();
        ItemStack stack;
        OfflinePlayer skullOwner = null;

        if ("playerhead".equalsIgnoreCase(matStr)) {
            stack = new ItemStack(Material.PLAYER_HEAD);
            if (placeholders != null && placeholders.containsKey("target_name")) {
                skullOwner = Bukkit.getOfflinePlayer(placeholders.get("target_name"));
            } else {
                skullOwner = player;
            }
        } else if (matStr.toLowerCase(Locale.ROOT).startsWith("playerhead:")) {
            stack = new ItemStack(Material.PLAYER_HEAD);
            String target = matStr.substring("playerhead:".length()).trim();
            target = applyPlaceholders(target, placeholders);
            try {
                UUID uuid = UUID.fromString(target);
                skullOwner = Bukkit.getOfflinePlayer(uuid);
            } catch (IllegalArgumentException e) {
                skullOwner = Bukkit.getOfflinePlayer(target);
            }
        } else if (matStr.startsWith("basehead-")) {
            stack = new ItemStack(Material.PLAYER_HEAD);
            String b64 = matStr.substring("basehead-".length());
            applyBase64Head(stack, b64);
        } else {
            Material mat = Material.matchMaterial(matStr.toUpperCase(Locale.ROOT));
            if (mat == null)
                mat = Material.STONE;
            stack = new ItemStack(mat);
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (skullOwner != null && meta instanceof SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(skullOwner);
            }
            if (!itemCfg.getDisplayName().isBlank()) {
                meta.setDisplayName(ColorUtil.colorize(applyPlaceholders(itemCfg.getDisplayName(), placeholders)));
            }

            if (!itemCfg.getLore().isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : itemCfg.getLore()) {
                    coloredLore.add(ColorUtil.colorize(applyPlaceholders(line, placeholders)));
                }
                meta.setLore(coloredLore);
            }

            if (itemCfg.isGlow()) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            for (String flagName : itemCfg.getItemFlags()) {
                try {
                    ItemFlag flag = ItemFlag.valueOf(flagName.toUpperCase(Locale.ROOT));
                    meta.addItemFlags(flag);
                } catch (IllegalArgumentException ignored) {
                }
            }

            stack.setItemMeta(meta);
        }

        return stack;
    }

    private static void applyBase64Head(ItemStack head, String base64) {
        try {
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta == null)
                return;
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", base64));
            meta.setPlayerProfile(profile);
            head.setItemMeta(meta);
        } catch (Throwable ignored) {
        }
    }

    public static String applyPlaceholders(String text, Map<String, String> placeholders) {
        if (text == null || text.isBlank() || placeholders == null)
            return text;
        String res = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                res = res.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return res;
    }

    private static String relationActionText(zMClans plugin, Clan own, Clan target, String menuKey) {
        if (own == null || target == null || own.getId().equals(target.getId()))
            return "";
        if (own.isAlly(target.getId()) || own.isRival(target.getId())) {
            return plugin.getMessages().get("relation-open-info-hint");
        }
        if ("clan_allies".equals(menuKey))
            return plugin.getMessages().get("relation-add-ally-hint");
        if ("clan_rivals".equals(menuKey))
            return plugin.getMessages().get("relation-add-rival-hint");
        return "";
    }

    private static int relationScore(Clan own, Clan target, RelationService relations, boolean allyMenu) {
        if (own == null || target == null) {
            return 0;
        }
        if (allyMenu && own.isAlly(target.getId()))
            return 2;
        if (!allyMenu && own.isRival(target.getId()))
            return 2;
        return 0;
    }

    private static List<AllyHomeEntry> getAllyHomeEntries(zMClans plugin, Player player) {
        Clan own = plugin.getClanService().findByPlayer(player.getUniqueId()).orElse(null);
        if (own == null)
            return List.of();
        List<AllyHomeEntry> entries = new ArrayList<>();
        for (UUID allyId : own.getAllyIds()) {
            Clan ally = plugin.getClanService().findById(allyId).orElse(null);
            if (ally == null || !ally.isAlly(own.getId()) || !ally.isAllyHomesVisible())
                continue;
            ally.getHomes().entrySet().stream().sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                    .forEach(e -> entries
                            .add(new AllyHomeEntry(ally.getId(), ally.getName(), e.getKey(), e.getValue())));
        }
        entries.sort(Comparator.comparing(AllyHomeEntry::clanName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(AllyHomeEntry::homeName, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }
}
