package dev.zm.clan.member.repository;

import dev.zm.clan.member.model.ClanMember;
import dev.zm.clan.storage.DatabaseManager;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * All SQL operations for the {@code clan_members} table.
 */
public final class MemberRepository {

    private final DatabaseManager db;
    private final Logger log;

    public MemberRepository(DatabaseManager db, Logger log) {
        this.db = db;
        this.log = log;
    }

    public boolean insert(ClanMember member) {
        String sql = "INSERT INTO clan_members(player_uuid,clan_id,rank_id,joined_at,kills,deaths) VALUES (?,?,?,?,?,?)";
        try (Connection con = db.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, member.getPlayerUUID().toString());
            ps.setString(2, member.getClanId().toString());
            ps.setString(3, member.getRankId());
            ps.setLong(4, member.getJoinedAt().getEpochSecond());
            ps.setLong(5, member.getKills());
            ps.setLong(6, member.getDeaths());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to insert member " + member.getPlayerUUID(), e);
            return false;
        }
    }

    public boolean save(ClanMember member) {
        String sql = "UPDATE clan_members SET clan_id=?,rank_id=?,joined_at=?,kills=?,deaths=? WHERE player_uuid=?";
        int updated;
        boolean exists = false;
        try (Connection con = db.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, member.getClanId().toString());
            ps.setString(2, member.getRankId());
            ps.setLong(3, member.getJoinedAt().getEpochSecond());
            ps.setLong(4, member.getKills());
            ps.setLong(5, member.getDeaths());
            ps.setString(6, member.getPlayerUUID().toString());
            updated = ps.executeUpdate();
            if (updated == 0) {
                try (PreparedStatement check = con.prepareStatement(
                        "SELECT 1 FROM clan_members WHERE player_uuid=?")) {
                    check.setString(1, member.getPlayerUUID().toString());
                    try (ResultSet result = check.executeQuery()) {
                        exists = result.next();
                    }
                }
            }
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to save member " + member.getPlayerUUID(), e);
            return false;
        }
        return updated > 0 || exists || insert(member);
    }

    public boolean updateRank(UUID playerUUID, String newRankId) {
        try (Connection con = db.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE clan_members SET rank_id=? WHERE player_uuid=?")) {
            ps.setString(1, newRankId);
            ps.setString(2, playerUUID.toString());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to updateRank for " + playerUUID, e);
            return false;
        }
    }

    public boolean incrementKills(UUID playerUUID) {
        return incrementStat(playerUUID, "kills");
    }

    public boolean incrementDeaths(UUID playerUUID) {
        return incrementStat(playerUUID, "deaths");
    }

    private boolean incrementStat(UUID playerUUID, String column) {
        if (!"kills".equals(column) && !"deaths".equals(column))
            return false;
        try (Connection con = db.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE clan_members SET " + column + "=" + column + "+1 WHERE player_uuid=?")) {
            ps.setString(1, playerUUID.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to increment " + column + " for " + playerUUID, e);
            return false;
        }
    }

    public boolean delete(UUID playerUUID) {
        try (Connection con = db.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM clan_members WHERE player_uuid=?")) {
            ps.setString(1, playerUUID.toString());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to delete member " + playerUUID, e);
            return false;
        }
    }

    public boolean deleteByClan(UUID clanId) {
        try (Connection con = db.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM clan_members WHERE clan_id=?")) {
            ps.setString(1, clanId.toString());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to deleteAll for clan " + clanId, e);
            return false;
        }
    }

    public Optional<ClanMember> findByPlayer(UUID playerUUID) {
        try (Connection con = db.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT * FROM clan_members WHERE player_uuid=?")) {
            ps.setString(1, playerUUID.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next())
                return Optional.of(fromResultSet(rs));
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to findByPlayer " + playerUUID, e);
        }
        return Optional.empty();
    }

    public List<ClanMember> findByClan(UUID clanId) {
        List<ClanMember> list = new ArrayList<>();
        try (Connection con = db.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT * FROM clan_members WHERE clan_id=?")) {
            ps.setString(1, clanId.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                list.add(fromResultSet(rs));
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to findByClan " + clanId, e);
        }
        return list;
    }

    public boolean hasAnyClan(UUID playerUUID) {
        return findByPlayer(playerUUID).isPresent();
    }

    private ClanMember fromResultSet(ResultSet rs) throws SQLException {
        ClanMember member = new ClanMember(
                UUID.fromString(rs.getString("player_uuid")),
                UUID.fromString(rs.getString("clan_id")),
                rs.getString("rank_id"),
                Instant.ofEpochSecond(rs.getLong("joined_at")));
        member.setStats(rs.getLong("kills"), rs.getLong("deaths"));
        return member;
    }
}
