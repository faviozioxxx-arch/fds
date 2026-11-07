package me.rising.anticheat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

public final class Msg {

    public static final MiniMessage MM = MiniMessage.miniMessage();

    /** Название с жёлто-оранжевым градиентом. */
    public static final String NAME = "<gradient:#FFD700:#FF6A00><bold>RISING ANTICHEAT</bold></gradient>";
    public static final String PREFIX = NAME + " <dark_gray>»</dark_gray> ";

    private Msg() {
    }

    public static Component parse(String mini) {
        return MM.deserialize(mini);
    }

    public static Component prefixed(String mini) {
        return MM.deserialize(PREFIX + mini);
    }

    public static void send(CommandSender to, String mini) {
        to.sendMessage(prefixed(mini));
    }

    public static String esc(String raw) {
        return MM.escapeTags(raw);
    }
}
