package me.rising.anticheat;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * Детект X-Ray по статистике добычи: слишком много РАЗНЫХ жил ценной руды
 * за малое число сломанных блоков. Для скрытия руды от клиента дополнительно
 * включите встроенный anti-xray Paper (см. README).
 */
public final class XrayListener implements Listener {

    private static final Set<Material> RARE = EnumSet.of(
            Material.DIAMOND_ORE,
            Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE,
            Material.DEEPSLATE_EMERALD_ORE,
            Material.ANCIENT_DEBRIS);

    private final RisingAntiCheat plugin;
    private final CheatManager cm;

    public XrayListener(RisingAntiCheat plugin, CheatManager cm) {
        this.plugin = plugin;
        this.cm = cm;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL || p.hasPermission("rac.bypass")) {
            return;
        }
        Block b = e.getBlock();
        World.Environment env = b.getWorld().getEnvironment();
        if (env == World.Environment.THE_END) {
            return;
        }
        if (env != World.Environment.NETHER && b.getY() > plugin.getConfig().getInt("checks.xray.max-y", 20)) {
            return;
        }

        PlayerData d = cm.data(p);
        boolean newVein = false;
        if (RARE.contains(b.getType())) {
            Location loc = b.getLocation();
            if (d.lastRareLoc == null || d.lastRareLoc.getWorld() != loc.getWorld()
                    || loc.distanceSquared(d.lastRareLoc) > 36.0) {
                newVein = true;
            }
            d.lastRareLoc = loc;
        }

        int window = plugin.getConfig().getInt("checks.xray.window", 40);
        int minVeins = plugin.getConfig().getInt("checks.xray.min-veins", 3);

        d.mined.addLast(newVein);
        while (d.mined.size() > window) {
            d.mined.pollFirst();
        }
        if (d.mined.size() >= window) {
            long veins = d.mined.stream().filter(Boolean::booleanValue).count();
            if (veins >= minVeins) {
                cm.xrayAlert(p, veins + " жил руды за " + window + " блоков");
                d.mined.clear();
            }
        }
    }
}
