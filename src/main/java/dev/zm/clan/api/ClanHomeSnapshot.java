package dev.zm.clan.api;

public record ClanHomeSnapshot(String name, String world, double x, double y, double z,
                               float yaw, float pitch) {}