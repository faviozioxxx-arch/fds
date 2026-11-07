package me.rising.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class RacCommand implements TabExecutor {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.\\-]{1,32}");
    private static final List<String> SUB = List.of("pass", "ban", "check", "vl", "reload");

    private final RisingAntiCheat plugin;
    private final CheatManager cm;

    public RacCommand(RisingAntiCheat plugin, CheatManager cm) {
        this.plugin = plugin;
        this.cm = cm;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("rac.admin")) {
            Msg.send(s, "<red>У вас нет прав.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("setcheter")) {
            if (!(s instanceof Player p)) {
                Msg.send(s, "<red>Команда только для игроков.");
                return true;
            }
            cm.setCheckLocation(p.getLocation());
            Msg.send(s, "<green>Точка проверки установлена на вашей позиции.");
            return true;
        }

        if (a.length == 0) {
            Msg.send(s, "<gray>/rac check <игрок> <dark_gray>-</dark_gray> отправить на проверку");
            Msg.send(s, "<gray>/rac pass <игрок> <dark_gray>-</dark_gray> прошёл проверку (на спавн)");
            Msg.send(s, "<gray>/rac ban <игрок> <dark_gray>-</dark_gray> бан за читы");
            Msg.send(s, "<gray>/rac vl <игрок> <dark_gray>-</dark_gray> текущие нарушения");
            Msg.send(s, "<gray>/rac reload <dark_gray>-</dark_gray> перезагрузить конфиг");
            Msg.send(s, "<gray>/setcheter <dark_gray>-</dark_gray> задать точку проверки");
            return true;
        }

        String sub = a[0].toLowerCase(Locale.ROOT);

        if (sub.equals("reload")) {
            plugin.reloadConfig();
            Msg.send(s, "<green>Конфиг перезагружен.");
            return true;
        }

        if (a.length < 2 || !NAME.matcher(a[1]).matches()) {
            Msg.send(s, "<red>Укажите ник игрока: /rac " + sub + " <игрок>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(a[1]);

        switch (sub) {
            case "pass" -> {
                if (target == null || !cm.isFrozen(target.getUniqueId())) {
                    Msg.send(s, "<red>Игрок не находится на проверке.");
                } else {
                    cm.pass(s, target);
                }
            }
            case "ban" -> {
                boolean onCheck = (target != null && cm.isFrozen(target.getUniqueId())) || cm.consumePendingBan(a[1]);
                if (!onCheck) {
                    Msg.send(s, "<red>Игрок не находится на проверке.");
                } else {
                    cm.ban(s, target != null ? target.getName() : a[1]);
                }
            }
            case "check" -> {
                if (target == null) {
                    Msg.send(s, "<red>Игрок не в сети.");
                } else if (cm.isFrozen(target.getUniqueId())) {
                    Msg.send(s, "<yellow>Игрок уже на проверке.");
                } else {
                    cm.startCheck(target, "ручная проверка (" + s.getName() + ")");
                }
            }
            case "vl" -> {
                if (target == null) {
                    Msg.send(s, "<red>Игрок не в сети.");
                } else {
                    Msg.send(s, "<yellow>" + Msg.esc(target.getName()) + "</yellow> <gray>VL: "
                            + String.format(Locale.US, "%.1f", cm.getVl(target)) + "</gray>");
                }
            }
            default -> Msg.send(s, "<red>Неизвестная подкоманда.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("rac.admin") || cmd.getName().equalsIgnoreCase("setcheter")) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String sub : SUB) {
                if (sub.startsWith(a[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
        } else if (a.length == 2 && !a[0].equalsIgnoreCase("reload")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
