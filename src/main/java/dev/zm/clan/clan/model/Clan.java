package dev.zm.clan.clan.model;

import dev.zm.clan.member.model.ClanMember;
import dev.zm.clan.zMClans;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable-style domain model for a Clan.
 * Use the Builder to construct instances.
 * All mutations go through the service layer.
 */
public final class Clan {

    private final UUID id;
    private String name;
    private String tag;
    private String description;
    private UUID leader;
    private String leaderRankId;
    private final List<UUID> memberIds;
    private final Instant createdAt;

    private boolean friendlyFire;
    private boolean openInvitations;
    private boolean allyHomesVisible;
    private boolean allyChatEnabled = true;
    private boolean allyPvpEnabled;
    private boolean publicJoin;
    private final java.util.Set<UUID> allyIds = new java.util.LinkedHashSet<>();
    private final java.util.Set<UUID> rivalIds = new java.util.LinkedHashSet<>();
    private long kills;
    private long deaths;

    private ClanHome home;
    private final Map<String, ClanHome> homes = new LinkedHashMap<>();

    private long points;

    private Clan(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.tag = b.tag;
        this.description = b.description;
        this.leader = b.leader;
        this.leaderRankId = b.leaderRankId;
        this.memberIds = new ArrayList<>(b.memberIds);
        this.createdAt = b.createdAt;
        this.friendlyFire = b.friendlyFire;
        this.openInvitations = b.openInvitations;
        this.allyHomesVisible = b.allyHomesVisible;
        this.allyChatEnabled = b.allyChatEnabled;
        this.allyPvpEnabled = b.allyPvpEnabled;
        this.publicJoin = b.publicJoin;
        this.allyIds.addAll(b.allyIds);
        this.rivalIds.addAll(b.rivalIds);
        this.kills = Math.max(0, b.kills);
        this.deaths = Math.max(0, b.deaths);
        this.home = b.home;
        if (b.home != null)
            this.homes.put("default", b.home);
        this.homes.putAll(b.homes);
        if (this.home == null && !this.homes.isEmpty())
            this.home = this.homes.values().iterator().next();
        this.points = b.points;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return (tag != null && !tag.isBlank()) ? tag : name;
    }

    public String getRawTag() {
        return tag != null ? tag : "";
    }

    public boolean hasCustomTag() {
        return tag != null && !tag.isBlank();
    }

    public String getDescription() {
        return description;
    }

    public UUID getLeader() {
        return leader;
    }

    public String getLeaderRankId() {
        return leaderRankId;
    }

    public List<UUID> getMemberIds() {
        return memberIds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isFriendlyFire() {
        return friendlyFire;
    }

    public boolean isOpenInvitations() {
        return openInvitations;
    }

    public boolean isAllyHomesVisible() {
        return allyHomesVisible;
    }

    public boolean isAllyChatEnabled() {
        return allyChatEnabled;
    }

    public boolean isAllyPvpEnabled() {
        return allyPvpEnabled;
    }

    public boolean isPublicJoin() {
        return publicJoin;
    }

    public java.util.Set<UUID> getAllyIds() {
        return java.util.Collections.unmodifiableSet(allyIds);
    }

    public java.util.Set<UUID> getRivalIds() {
        return java.util.Collections.unmodifiableSet(rivalIds);
    }

    public boolean isAlly(UUID clanId) {
        return clanId != null && allyIds.contains(clanId);
    }

    public boolean isRival(UUID clanId) {
        return clanId != null && rivalIds.contains(clanId);
    }

    public long getKills() {
        return kills;
    }

    public long getDeaths() {
        return deaths;
    }

    public double getKdr() {
        return (kills + 1.0d) / (deaths + 1.0d);
    }

    public ClanHome getHome() {
        return home != null ? home : (homes.isEmpty() ? null : homes.values().iterator().next());
    }

    public Map<String, ClanHome> getHomes() {
        return java.util.Collections.unmodifiableMap(homes);
    }

    public ClanHome getHome(String name) {
        return name == null ? getHome() : homes.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    public void setNamedHome(String name, ClanHome location) {
        if (name == null || name.isBlank() || location == null)
            return;
        homes.put(name.toLowerCase(java.util.Locale.ROOT), location);
        if (home == null || "default".equalsIgnoreCase(name))
            home = location;
    }

    public boolean removeNamedHome(String name) {
        if (name == null)
            return false;
        ClanHome removed = homes.remove(name.toLowerCase(java.util.Locale.ROOT));
        home = homes.get("default");
        if (home == null && !homes.isEmpty())
            home = homes.values().iterator().next();
        return removed != null;
    }

    public void replaceHomes(Map<String, ClanHome> values) {
        homes.clear();
        if (values != null)
            homes.putAll(values);
        home = homes.get("default");
        if (home == null && !homes.isEmpty())
            home = homes.values().iterator().next();
    }

    public long getPoints() {
        return points;
    }

    public int getMemberCount() {
        return memberIds.size();
    }

    public Clan copyForPersistence() {
        return builder()
                .id(id)
                .name(name)
                .tag(tag)
                .description(description)
                .leader(leader)
                .leaderRankId(leaderRankId)
                .members(memberIds)
                .createdAt(createdAt)
                .friendlyFire(friendlyFire)
                .openInvitations(openInvitations)
                .allyHomesVisible(allyHomesVisible)
                .allyChatEnabled(allyChatEnabled)
                .allyPvpEnabled(allyPvpEnabled)
                .publicJoin(publicJoin)
                .allies(allyIds)
                .rivals(rivalIds)
                .stats(kills, deaths)
                .home(home)
                .homes(homes)
                .points(points)
                .build();
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public void setDescription(String desc) {
        this.description = desc;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public void setLeaderRankId(String rankId) {
        this.leaderRankId = rankId;
    }

    public void setFriendlyFire(boolean ff) {
        this.friendlyFire = ff;
    }

    public void setOpenInvitations(boolean open) {
        this.openInvitations = open;
    }

    public void setAllyHomesVisible(boolean value) {
        this.allyHomesVisible = value;
    }

    public void setAllyChatEnabled(boolean value) {
        this.allyChatEnabled = value;
    }

    public void setAllyPvpEnabled(boolean value) {
        this.allyPvpEnabled = value;
    }

    public void setPublicJoin(boolean value) {
        this.publicJoin = value;
    }

    public void addAlly(UUID id) {
        if (id != null && !id.equals(this.id)) {
            rivalIds.remove(id);
            allyIds.add(id);
        }
    }

    public void addRival(UUID id) {
        if (id != null && !id.equals(this.id)) {
            allyIds.remove(id);
            rivalIds.add(id);
        }
    }

    public void removeRelation(UUID id) {
        allyIds.remove(id);
        rivalIds.remove(id);
    }

    public void replaceAllies(java.util.Collection<UUID> ids) {
        allyIds.clear();
        if (ids != null)
            ids.stream().filter(java.util.Objects::nonNull).filter(v -> !v.equals(id)).forEach(allyIds::add);
    }

    public void replaceRivals(java.util.Collection<UUID> ids) {
        rivalIds.clear();
        if (ids != null)
            ids.stream().filter(java.util.Objects::nonNull).filter(v -> !v.equals(id)).forEach(rivalIds::add);
    }

    public void addKill() {
        if (kills < Long.MAX_VALUE)
            kills++;
    }

    public void addDeath() {
        if (deaths < Long.MAX_VALUE)
            deaths++;
    }

    public void setStats(long kills, long deaths) {
        this.kills = Math.max(0, kills);
        this.deaths = Math.max(0, deaths);
    }

    public void setHome(ClanHome home) {
        this.home = home;
    }

    public void addPoints(long delta) {
        if (delta > 0 && points > Long.MAX_VALUE - delta)
            points = Long.MAX_VALUE;
        else
            points = Math.max(0L, points + delta);
    }

    public void setPoints(long points) {
        this.points = Math.max(0, points);
    }

    public void addMember(UUID uuid) {
        if (!memberIds.contains(uuid))
            memberIds.add(uuid);
    }

    public void removeMember(UUID uuid) {
        memberIds.remove(uuid);
    }

    public boolean hasMember(UUID uuid) {
        return memberIds.contains(uuid);
    }

    public List<ClanMember> getMembers() {
        if (zMClans.get() != null && zMClans.get().getMemberService() != null) {
            return zMClans.get().getMemberService().getMembersOfClan(id);
        }
        return List.of();
    }

    public Optional<ClanMember> getMember(UUID uuid) {
        if (zMClans.get() != null && zMClans.get().getMemberService() != null) {
            return zMClans.get().getMemberService().getMember(uuid);
        }
        return Optional.empty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID id = UUID.randomUUID();
        private String name;
        private String tag;
        private String description = "";
        private UUID leader;
        private String leaderRankId = "leader";
        private List<UUID> memberIds = new ArrayList<>();
        private Instant createdAt = Instant.now();
        private boolean friendlyFire = false;
        private boolean openInvitations = false;
        private boolean allyHomesVisible = false;
        private boolean allyChatEnabled = true;
        private boolean allyPvpEnabled = false;
        private boolean publicJoin = false;
        private java.util.Set<UUID> allyIds = new java.util.LinkedHashSet<>();
        private java.util.Set<UUID> rivalIds = new java.util.LinkedHashSet<>();
        private long kills = 0, deaths = 0;
        private ClanHome home = null;
        private long points = 0L;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder tag(String tag) {
            this.tag = tag;
            return this;
        }

        public Builder description(String d) {
            this.description = d;
            return this;
        }

        public Builder leader(UUID leader) {
            this.leader = leader;
            return this;
        }

        public Builder leaderRankId(String r) {
            this.leaderRankId = r;
            return this;
        }

        public Builder members(List<UUID> m) {
            this.memberIds = new ArrayList<>(m);
            return this;
        }

        public Builder createdAt(Instant t) {
            this.createdAt = t;
            return this;
        }

        public Builder friendlyFire(boolean ff) {
            this.friendlyFire = ff;
            return this;
        }

        public Builder openInvitations(boolean o) {
            this.openInvitations = o;
            return this;
        }

        public Builder allyHomesVisible(boolean v) {
            this.allyHomesVisible = v;
            return this;
        }

        public Builder allyChatEnabled(boolean v) {
            this.allyChatEnabled = v;
            return this;
        }

        public Builder allyPvpEnabled(boolean v) {
            this.allyPvpEnabled = v;
            return this;
        }

        public Builder publicJoin(boolean v) {
            this.publicJoin = v;
            return this;
        }

        public Builder allies(java.util.Collection<UUID> v) {
            this.allyIds = v == null ? new java.util.LinkedHashSet<>() : new java.util.LinkedHashSet<>(v);
            return this;
        }

        public Builder rivals(java.util.Collection<UUID> v) {
            this.rivalIds = v == null ? new java.util.LinkedHashSet<>() : new java.util.LinkedHashSet<>(v);
            return this;
        }

        public Builder stats(long k, long d) {
            this.kills = k;
            this.deaths = d;
            return this;
        }

        public Builder home(ClanHome h) {
            this.home = h;
            return this;
        }

        private Map<String, ClanHome> homes = new LinkedHashMap<>();

        public Builder homes(Map<String, ClanHome> h) {
            this.homes = h == null ? new LinkedHashMap<>() : new LinkedHashMap<>(h);
            return this;
        }

        public Builder points(long p) {
            this.points = p;
            return this;
        }

        public Clan build() {
            if (name == null || tag == null || leader == null)
                throw new IllegalStateException("Clan requires name, tag, and leader.");
            return new Clan(this);
        }
    }
}
