package me.rising.anticheat;

import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.UUID;

public final class CombatListener implements Listener {

    private final RisingAntiCheat plugin;
    private final CheatManager cm;

    public CombatListener(RisingAntiCheat plugin, CheatManager cm) {
        this.plugin = plugin;
        this.cm = cm;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) {
            return;
        }
        float dy = Math.abs(wrap(to.getYaw() - from.getYaw()));
        if (dy > 0.01f) {
            PlayerData d = cm.data(e.getPlayer());
            d.lastDeltaYaw = dy;
            d.lastRotTime = System.currentTimeMillis();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) {
            return;
        }
        if (!(e.getEntity() instanceof LivingEntity t) || t == p || t instanceof ArmorStand) {
            return;
        }
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        if (t.hasMetadata("NPC") || p.hasPermission("rac.bypass")) {
            return;
        }
        if (cm.isFrozen(p.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
            return;
        }

        FileConfiguration c = plugin.getConfig();
        PlayerData d = cm.data(p);
        long now = System.currentTimeMillis();

        Location eyeLoc = p.getEyeLocation();
        Vector eye = eyeLoc.toVector();
        Vector dir = eyeLoc.getDirection();
        BoundingBox bb = t.getBoundingBox();
        Vector closest = new Vector(
                clamp(eye.getX(), bb.getMinX(), bb.getMaxX()),
                clamp(eye.getY(), bb.getMinY(), bb.getMaxY()),
                clamp(eye.getZ(), bb.getMinZ(), bb.getMaxZ()));
        double dist = eye.distance(closest);
        double lagExtra = Math.min(Math.max(p.getPing(), 0), 400) / 1000.0;

        boolean failed = false;

        // 1. Reach
        double maxReach = c.getDouble("checks.reach.max", 3.3) + lagExtra * 1.5;
        if (dist > maxReach) {
            cm.flag(p, "Reach", c.getDouble("checks.reach.vl", 1.5),
                    String.format(Locale.US, "%.2f блока", dist));
            failed = true;
        }

        // 2. Удар не в направлении цели (KillAura) + резкий снап (AimAssist)
        if (dist > 1.2) {
            double expand = c.getDouble("checks.aura.expand", 0.35) + lagExtra * 0.8;
            RayTraceResult rt = bb.clone().expand(expand).rayTrace(eye, dir, maxReach + 2.0);
            double angle = Math.toDegrees(dir.angle(bb.getCenter().subtract(eye)));
            if (rt == null) {
                cm.flag(p, "KillAura (угол)", c.getDouble("checks.aura.vl", 1.0),
                        String.format(Locale.US, "угол до цели %.0f°", angle));
                failed = true;
            } else if (now - d.lastRotTime < 80
                    && d.lastDeltaYaw > c.getDouble("checks.aim.snap-degrees", 55.0)
                    && angle < c.getDouble("checks.aim.snap-error", 2.0)) {
                cm.flag(p, "AimAssist (снап)", c.getDouble("checks.aim.vl", 0.75),
                        String.format(Locale.US, "поворот %.0f° -> ошибка %.1f°", d.lastDeltaYaw, angle));
            }
        }

        // 3. Два разных существа почти одновременно (Multi-Aura)
        UUID tid = t.getUniqueId();
        if (d.lastTarget != null && !d.lastTarget.equals(tid)
                && now - d.lastTargetTime < c.getLong("checks.multi.window-ms", 45L)) {
            cm.flag(p, "KillAura (multi)", c.getDouble("checks.multi.vl", 2.0), "2 цели за " + (now - d.lastTargetTime) + " мс");
            failed = true;
        }
        d.lastTarget = tid;
        d.lastTargetTime = now;

        // 4. Слишком много ударов в секунду
        d.hits.addLast(now);
        while (!d.hits.isEmpty() && now - d.hits.peekFirst() > 1000L) {
            d.hits.pollFirst();
        }
        if (d.hits.size() > c.getInt("checks.cps.max-hits", 15)) {
            cm.flag(p, "Скорость ударов", c.getDouble("checks.cps.vl", 1.0), d.hits.size() + " ударов/с");
            failed = true;
        }

        // 5. Удар сквозь блоки
        if (dist > 0.6) {
            Vector toTarget = closest.clone().subtract(eye).normalize();
            RayTraceResult br = p.getWorld().rayTraceBlocks(eyeLoc, toTarget, dist - 0.15,
                    FluidCollisionMode.NEVER, true);
            if (br != null && br.getHitBlock() != null) {
                cm.flag(p, "Удар сквозь блок", c.getDouble("checks.wall.vl", 1.5), br.getHitBlock().getType().name());
                failed = true;
            }
        }

        // Решение: не регистрировать удар
        if ((failed && c.getBoolean("cancel-failed-hits", true)) || cm.shouldCancel(p)) {
            e.setCancelled(true);
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float wrap(float deg) {
        deg = deg % 360.0f;
        if (deg >= 180.0f) {
            deg -= 360.0f;
        }
        if (deg < -180.0f) {
            deg += 360.0f;
        }
        return deg;
    }
}
