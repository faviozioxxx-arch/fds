package me.rising.anticheat;

import org.bukkit.Location;

import java.util.ArrayDeque;
import java.util.UUID;

public final class PlayerData {

    public final UUID uuid;

    public double vl;
    public long lastAlert;

    public boolean frozen;
    public boolean wasInvulnerable;

    // бой
    public long lastRotTime;
    public float lastDeltaYaw;
    public UUID lastTarget;
    public long lastTargetTime;
    public final ArrayDeque<Long> hits = new ArrayDeque<>();

    // x-ray
    public final ArrayDeque<Boolean> mined = new ArrayDeque<>();
    public Location lastRareLoc;
    public long lastXrayAlert;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }
}
