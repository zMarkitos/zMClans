package dev.zm.clan.clan.repository;

import dev.zm.clan.clan.model.Clan;
import dev.zm.clan.clan.model.ClanHome;
import dev.zm.clan.storage.DatabaseManager;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * All SQL operations for the {@code clans} table.
 * Returns {@link Optional} for single results and empty collections on failure.
 */
public final class ClanRepository {

    private final DatabaseManager db;
    private final Logger log;

    public ClanRepository(DatabaseManager db, Logger log) {
        this.db = db;
        this.log = log;
    }

    public boolean insert(Clan clan) {
        String sql = """
                    INSERT INTO clans(id,name,tag,description,leader,leader_rank,
                                      friendly_fire,open_invite,home,points,created_at,ally_homes,ally_chat,ally_pvp,public_join,allies,rivals,kills,deaths,level)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (Connection con = db.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, clan.getId().toString());
            ps.setString(2, clan.getName());
            ps.setString(3, persistTag(clan));
            ps.setString(4, clan.getDescription());
            ps.setString(5, clan.getLeader().toString());
            ps.setString(6, clan.getLeaderRankId());
            ps.setInt(7, clan.isFriendlyFire() ? 1 : 0);
            ps.setInt(8, clan.isOpenInvitations() ? 1 : 0);
            ps.setString(9, serializeHomes(clan));
            ps.setLong(10, clan.getPoints());
            ps.setLong(11, clan.getCreatedAt().getEpochSecond());
            bindExtra(ps, 12, clan);
            ps.executeUpdate();
            return true;

        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to insert clan " + clan.getName(), e);
            return false;
        }
    }

    public boolean update(Clan clan) {
        String sql = """
                    UPDATE clans SET name=?,tag=?,description=?,leader=?,leader_rank=?,
                                     friendly_fire=?,open_invite=?,home=?,points=?,ally_homes=?,ally_chat=?,ally_pvp=?,public_join=?,allies=?,rivals=?,kills=?,deaths=?,level=?
                    WHERE id=?
                """;
        int updated;
        boolean exists = false;
        try (Connection con = db.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, clan.getName());
            ps.setString(2, persistTag(clan));
            ps.setString(3, clan.getDescription());
            ps.setString(4, clan.getLeader().toString());
            ps.setString(5, clan.getLeaderRankId());
            ps.setInt(6, clan.isFriendlyFire() ? 1 : 0);
            ps.setInt(7, clan.isOpenInvitations() ? 1 : 0);
            ps.setString(8, serializeHomes(clan));
            ps.setLong(9, clan.getPoints());
            bindExtra(ps, 10, clan);
            ps.setString(19, clan.getId().toString());
            updated = ps.executeUpdate();
            if (updated == 0) {
                try (PreparedStatement check = con.prepareStatement("SELECT 1 FROM clans WHERE id=?")) {
                    check.setString(1, clan.getId().toString());
                    try (ResultSet result = check.executeQuery()) {
                        exists = result.next();
                    }
                }
            }

        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to update clan " + clan.getId(), e);
            return false;
        }
        return updated > 0 || exists || insert(clan);
    }

    public boolean delete(UUID clanId) {
        try (Connection con = db.getConnection();
                PreparedStatement ps = con.prepareStatement("DELETE FROM clans WHERE id=?")) {
            ps.setString(1, clanId.toString());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to delete clan " + clanId, e);
            return false;
        }
    }

    public Optional<Clan> findById(UUID id) {
        try (Connection con = db.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT * FROM clans WHERE id=?")) {
            ps.setString(1, id.toString());
            return Optional.ofNullable(mapRow(ps.executeQuery()));
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to findById " + id, e);
            return Optional.empty();
        }
    }

    public Optional<Clan> findByName(String name) {
        try (Connection con = db.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT * FROM clans WHERE LOWER(name)=LOWER(?)")) {
            ps.setString(1, name);
            return Optional.ofNullable(mapRow(ps.executeQuery()));
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to findByName " + name, e);
            return Optional.empty();
        }
    }

    public Optional<Clan> findByTag(String tag) {
        try (Connection con = db.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT * FROM clans WHERE LOWER(tag)=LOWER(?)")) {
            ps.setString(1, tag);
            return Optional.ofNullable(mapRow(ps.executeQuery()));
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to findByTag " + tag, e);
            return Optional.empty();
        }
    }

    public List<Clan> findAll() {
        List<Clan> list = new ArrayList<>();
        try (Connection con = db.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT * FROM clans");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Clan c = fromResultSet(rs);
                if (c != null)
                    list.add(c);
            }
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to findAll clans", e);
        }
        return list;
    }

    public boolean existsByName(String name) {
        return findByName(name).isPresent();
    }

    public boolean existsByTag(String tag) {
        return findByTag(tag).isPresent();
    }

    private Clan mapRow(ResultSet rs) throws SQLException {
        if (!rs.next())
            return null;
        return fromResultSet(rs);
    }

    private Clan fromResultSet(ResultSet rs) throws SQLException {
        String homeRaw = rs.getString("home");
        Map<String, ClanHome> homes = deserializeHomes(homeRaw);
        ClanHome home = homes.get("default");
        if (home == null && !homes.isEmpty())
            home = homes.values().iterator().next();

        UUID id = UUID.fromString(rs.getString("id"));
        return Clan.builder()
                .id(id)
                .name(rs.getString("name"))
                .tag(readTag(rs.getString("tag"), id))
                .description(rs.getString("description"))
                .leader(UUID.fromString(rs.getString("leader")))
                .leaderRankId(rs.getString("leader_rank"))
                .friendlyFire(rs.getInt("friendly_fire") == 1)
                .openInvitations(rs.getInt("open_invite") == 1)
                .allyHomesVisible(rs.getInt("ally_homes") == 1)
                .allyChatEnabled(rs.getInt("ally_chat") != 0)
                .allyPvpEnabled(rs.getInt("ally_pvp") == 1)
                .publicJoin(rs.getInt("public_join") == 1)
                .allies(parseUuids(rs.getString("allies")))
                .rivals(parseUuids(rs.getString("rivals")))
                .stats(rs.getLong("kills"), rs.getLong("deaths"))
                .home(home)
                .homes(homes)
                .points(rs.getLong("points"))
                .createdAt(Instant.ofEpochSecond(rs.getLong("created_at")))
                .build();
    }

    private static String persistTag(Clan clan) {
        return clan.hasCustomTag() ? clan.getTag() : clan.getId().toString();
    }

    private static String readTag(String stored, UUID id) {
        if (stored == null || stored.isBlank() || stored.equalsIgnoreCase(id.toString()))
            return "";
        return stored;
    }

    private static void bindExtra(PreparedStatement ps, int start, Clan clan) throws SQLException {
        ps.setInt(start, clan.isAllyHomesVisible() ? 1 : 0);
        ps.setInt(start + 1, clan.isAllyChatEnabled() ? 1 : 0);
        ps.setInt(start + 2, clan.isAllyPvpEnabled() ? 1 : 0);
        ps.setInt(start + 3, clan.isPublicJoin() ? 1 : 0);
        ps.setString(start + 4, joinUuids(clan.getAllyIds()));
        ps.setString(start + 5, joinUuids(clan.getRivalIds()));
        ps.setLong(start + 6, clan.getKills());
        ps.setLong(start + 7, clan.getDeaths());
        ps.setInt(start + 8, 1);
    }

    private static String joinUuids(Collection<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(","));
    }

    private static List<UUID> parseUuids(String raw) {
        if (raw == null || raw.isBlank())
            return List.of();
        List<UUID> result = new ArrayList<>();
        for (String value : raw.split(","))
            try {
                result.add(UUID.fromString(value.trim()));
            } catch (IllegalArgumentException ignored) {
            }
        return result;
    }

    private static String serializeHomes(Clan clan) {
        if (clan.getHomes().isEmpty())
            return clan.getHome() != null ? clan.getHome().serialize() : null;
        StringBuilder out = new StringBuilder("ZMCH1;");
        java.util.Base64.Encoder enc = java.util.Base64.getUrlEncoder().withoutPadding();
        clan.getHomes().forEach((name, home) -> {
            if (home != null)
                out.append(enc.encodeToString(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .append('=')
                        .append(enc.encodeToString(home.serialize().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .append(';');
        });
        return out.toString();
    }

    private static Map<String, ClanHome> deserializeHomes(String raw) {
        Map<String, ClanHome> homes = new LinkedHashMap<>();
        if (raw == null || raw.isBlank())
            return homes;
        if (!raw.startsWith("ZMCH1;")) {
            ClanHome legacy = ClanHome.deserialize(raw);
            if (legacy != null)
                homes.put("default", legacy);
            return homes;
        }
        java.util.Base64.Decoder dec = java.util.Base64.getUrlDecoder();
        for (String part : raw.substring(6).split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0)
                continue;
            try {
                String name = new String(dec.decode(part.substring(0, eq)), java.nio.charset.StandardCharsets.UTF_8);
                String value = new String(dec.decode(part.substring(eq + 1)), java.nio.charset.StandardCharsets.UTF_8);
                ClanHome home = ClanHome.deserialize(value);
                if (home != null && !name.isBlank())
                    homes.put(name.toLowerCase(Locale.ROOT), home);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return homes;
    }

}
