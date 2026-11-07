package me.rising.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class RisingAntiCheat extends JavaPlugin {

    private CheatManager cheatManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cheatManager = new CheatManager(this);

        getServer().getPluginManager().registerEvents(new CombatListener(this, cheatManager), this);
        getServer().getPluginManager().registerEvents(new XrayListener(this, cheatManager), this);
        getServer().getPluginManager().registerEvents(new FreezeListener(cheatManager), this);

        RacCommand executor = new RacCommand(this, cheatManager);
        PluginCommand rac = getCommand("rac");
        if (rac != null) {
            rac.setExecutor(executor);
            rac.setTabCompleter(executor);
        }
        PluginCommand setcheter = getCommand("setcheter");
        if (setcheter != null) {
            setcheter.setExecutor(executor);
        }

        Bukkit.getScheduler().runTaskTimer(this, cheatManager::decay, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, cheatManager::titleTick, 20L, 30L);

        getServer().getConsoleSender().sendMessage(Msg.parse(Msg.NAME + " <green>включён"));
    }

    @Override
    public void onDisable() {
        if (cheatManager != null) {
            cheatManager.releaseAll();
        }
    }
}
