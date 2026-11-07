package me.rising.anticheat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CheatManager {

    private final RisingAntiCheat plugin;
    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();
    private final Set<String> pendingBan = ConcurrentHashMap.newKeySet();
    private final File storeFile;
    private final YamlConfiguration store;

    public CheatManager(RisingAntiCheat plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.storeFile = new File(plugin.getDataFolder(), "data.yml");
        this.store = YamlConfiguration.loadConfiguration(storeFile);
    }

    // ---------- данные игроков ----------

    public PlayerData data(Player p) {
        return data.computeIfAbsent(p.getUniqueId(), PlayerData::new);
    }

    public PlayerData peek(UUID uuid) {
        return data.get(uuid);
    }

    public void remove(UUID uuid) {
        data.remove(uuid);
    }

    public boolean isFrozen(UUID uuid) {
        PlayerData d = data.get(uuid);
        return d != null && d.frozen;
    }

    public boolean shouldCancel(Player p) {
        PlayerData d = data(p);
        return d.frozen || d.vl >= plugin.getConfig().getDouble("thresholds.cancel", 4.0);
    }

    public double getVl(Player p) {
        return data(p).vl;
    }

    // ---------- точка проверки ----------

    public void setCheckLocation(Location loc) {
        store.set("check-location", loc);
        try {
            store.save(storeFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить data.yml: " + e.getMessage());
        }
    }

    public Location getCheckLocation() {
        return store.getLocation("check-location");
    }

    // ---------- нарушения ----------

    public void flag(Player p, String check, double add, String info) {
        PlayerData d = data(p);
        FileConfiguration c = plugin.getConfig();
        d.vl = Math.min(100.0, d.vl + add);
        plugin.getLogger().info(p.getName() + " -> " + check + " (" + info + ") VL=" + String.format(Locale.US, "%.1f", d.vl));
        if (d.frozen) {
            return;
        }
        long now = System.currentTimeMillis();
        if (d.vl >= c.getDouble("thresholds.check", 12.0)) {
            startCheck(p, check + ": " + info);
            return;
        }
        long cooldown = c.getLong("alert-cooldown-seconds", 15) * 1000L;
        if (d.vl >= c.getDouble("thresholds.alert", 7.0) && now - d.lastAlert >= cooldown) {
            d.lastAlert = now;
            Component msg = Msg.prefixed("<yellow>" + Msg.esc(p.getName()) + "</yellow> <red>ведёт себя подозрительно</red>, "
                    + "есть подозрения на читы <gray>(" + Msg.esc(check) + ", "
                    + String.format(Locale.US, "VL %.1f", d.vl) + ", пинг " + p.getPing() + ")</gray>");
            Component actions = Component.text().append(button("<aqua><bold>[ПРОВЕРИТЬ]</bold></aqua>",
                    "/rac check " + p.getName(), "<aqua>Телепортировать игрока на проверку")).build();
            broadcastAdmins(msg, actions);
        }
    }

    public void xrayAlert(Player p, String info) {
        PlayerData d = data(p);
        long now = System.currentTimeMillis();
        if (now - d.lastXrayAlert < 30_000L) {
            return;
        }
        d.lastXrayAlert = now;
        plugin.getLogger().info(p.getName() + " -> X-Ray (" + info + ")");
        Component msg = Msg.prefixed("<yellow>" + Msg.esc(p.getName()) + "</yellow> <red>подозрение на X-Ray</red> <gray>("
                + Msg.esc(info) + ")</gray>");
        Component actions = Component.text()
                .append(button("<aqua><bold>[ПРОВЕРИТЬ]</bold></aqua>", "/rac check " + p.getName(),
                        "<aqua>Телепортировать игрока на проверку"))
                .append(Component.text("  "))
                .append(button("<gold><bold>[ТП К ИГРОКУ]</bold></gold>", "/tp " + p.getName(),
                        "<gold>Телепортироваться к игроку"))
                .build();
        broadcastAdmins(msg, actions);
    }

    // ---------- проверка ----------

    public void startCheck(Player p, String reason) {
        PlayerData d = data(p);
        if (d.frozen) {
            return;
        }
        d.frozen = true;
        d.wasInvulnerable = p.isInvulnerable();
        p.setInvulnerable(true);
        p.closeInventory();

        Location loc = getCheckLocation();
        if (loc != null && loc.getWorld() != null) {
            p.teleport(loc);
        }

        sendTitle(p);
        p.sendMessage(Msg.prefixed("<red>Ваше поведение подозрительно. Ожидайте администратора и не выходите из игры!"));

        Component head = Msg.prefixed("<yellow>" + Msg.esc(p.getName()) + "</yellow> <red>заморожен на проверке</red> <gray>("
                + Msg.esc(reason) + ")</gray>");
        Component buttons = Component.text()
                .append(button("<green><bold>[✔ ПРОШЁЛ ПРОВЕРКУ]</bold></green>", "/rac pass " + p.getName(),
                        "<green>Игрок будет телепортирован на спавн"))
                .append(Component.text("  "))
                .append(button("<red><bold>[✖ ЧИТЕР — БАН]</bold></red>", "/rac ban " + p.getName(),
                        "<red>Бан по причине: читы"))
                .build();
        broadcastAdmins(head, buttons);
        if (loc == null) {
            broadcastAdmins(Msg.prefixed("<red>Точка проверки не задана! Игрок заморожен на месте. Используйте /setcheter"));
        }
    }

    public void pass(CommandSender by, Player p) {
        PlayerData d = data(p);
        release(p, d);
        d.vl = 0;
        p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        p.clearTitle();
        p.sendMessage(Msg.prefixed("<green>Проверка пройдена. Приятной игры!"));
        broadcastAdmins(Msg.prefixed("<green>" + Msg.esc(by.getName()) + " подтвердил: " + Msg.esc(p.getName())
                + " прошёл проверку."));
    }

    public void ban(CommandSender by, String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) {
            release(p, data(p));
            p.clearTitle();
        }
        pendingBan.remove(name.toLowerCase(Locale.ROOT));
        String cmd = plugin.getConfig().getString("ban-command", "ban %player% Читы").replace("%player%", name);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        if (p != null) {
            remove(p.getUniqueId());
        }
        broadcastAdmins(Msg.prefixed("<red>" + Msg.esc(by.getName()) + " забанил " + Msg.esc(name) + " за читы."));
    }

    public boolean consumePendingBan(String name) {
        return pendingBan.remove(name.toLowerCase(Locale.ROOT));
    }

    /** Игрок вышел, пока был на проверке. */
    public void handleQuitWhileFrozen(Player p) {
        PlayerData d = data(p);
        release(p, d);
        if (plugin.getConfig().getBoolean("ban-on-quit", true)) {
            String cmd = plugin.getConfig().getString("ban-command", "ban %player% Читы").replace("%player%", p.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            broadcastAdmins(Msg.prefixed("<red>" + Msg.esc(p.getName()) + " вышел во время проверки и был забанен за читы."));
        } else {
            pendingBan.add(p.getName().toLowerCase(Locale.ROOT));
            broadcastAdmins(Msg.prefixed("<red>" + Msg.esc(p.getName()) + " вышел во время проверки."),
                    button("<red><bold>[✖ ЧИТЕР — БАН]</bold></red>", "/rac ban " + p.getName(), "<red>Бан по причине: читы"));
        }
    }

    private void release(Player p, PlayerData d) {
        d.frozen = false;
        p.setInvulnerable(d.wasInvulnerable);
    }

    public void releaseAll() {
        for (PlayerData d : data.values()) {
            if (d.frozen) {
                Player p = Bukkit.getPlayer(d.uuid);
                if (p != null) {
                    release(p, d);
                } else {
                    d.frozen = false;
                }
            }
        }
    }

    // ---------- периодические задачи ----------

    public void decay() {
        double dec = plugin.getConfig().getDouble("vl-decay-per-second", 0.2);
        for (PlayerData d : data.values()) {
            if (!d.frozen && d.vl > 0) {
                d.vl = Math.max(0.0, d.vl - dec);
            }
        }
    }

    public void titleTick() {
        for (PlayerData d : data.values()) {
            if (d.frozen) {
                Player p = Bukkit.getPlayer(d.uuid);
                if (p != null) {
                    sendTitle(p);
                }
            }
        }
    }

    // ---------- вспомогательное ----------

    private void sendTitle(Player p) {
        FileConfiguration c = plugin.getConfig();
        Component title = Msg.parse("<red><bold>" + Msg.esc(c.getString("messages.title", "ВАШЕ ПОВЕДЕНИЕ ПОДОЗРИТЕЛЬНО")));
        Component sub = Msg.parse("<yellow>" + Msg.esc(c.getString("messages.subtitle", "Подождите администратора")));
        p.showTitle(Title.title(title, sub,
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(250))));
    }

    private Component button(String mini, String command, String hoverMini) {
        return Msg.parse(mini)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Msg.parse(hoverMini)));
    }

    private void broadcastAdmins(Component... lines) {
        for (Player a : Bukkit.getOnlinePlayers()) {
            if (a.hasPermission("rac.admin")) {
                for (Component line : lines) {
                    a.sendMessage(line);
                }
            }
        }
        for (Component line : lines) {
            Bukkit.getConsoleSender().sendMessage(line);
        }
    }
}
