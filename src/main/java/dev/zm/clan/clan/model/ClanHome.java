package dev.zm.clan.clan.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Serializable clan home location.
 * Stored as world name + coordinates in the database.
 */
public final class ClanHome {

    private final String worldName;
    private final double x, y, z;
    private final float yaw, pitch;

    public ClanHome(Location location) {
        this.worldName = location.getWorld() != null ? location.getWorld().getName() : "world";
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
    }

    public ClanHome(String worldName, double x, double y, double z, float yaw, float pitch) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /**
     * Returns the Bukkit {@link Location}, or {@code null} if the world is not
     * loaded.
     */
    public Location toBukkitLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null)
            return null;
        return new Location(world, x, y, z, yaw, pitch);
    }

    /** @return {@code true} if the world is currently loaded. */
    public boolean isWorldLoaded() {
        return Bukkit.getWorld(worldName) != null;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getWorld() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    /** Serialize to a compact string for database storage. */
    public String serialize() {
        return worldName + ":" + x + ":" + y + ":" + z + ":" + yaw + ":" + pitch;
    }

    /** Deserialize from the compact string format. */
    public static ClanHome deserialize(String raw) {
        if (raw == null || raw.isBlank())
            return null;
        String[] p = raw.split(":");
        if (p.length < 4)
            return null;
        try {
            double x = Double.parseDouble(p[1]), y = Double.parseDouble(p[2]), z = Double.parseDouble(p[3]);
            float yaw = p.length > 4 ? Float.parseFloat(p[4]) : 0f;
            float pitch = p.length > 5 ? Float.parseFloat(p[5]) : 0f;
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)
                    || !Float.isFinite(pitch))
                return null;
            return new ClanHome(p[0], x, y, z, yaw, pitch);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
