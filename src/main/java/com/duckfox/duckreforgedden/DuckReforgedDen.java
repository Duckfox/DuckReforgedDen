package com.duckfox.duckreforgedden;

import com.duckfox.duckapi.DuckPlugin;
import com.duckfox.duckapi.managers.ConfigManager;
import com.duckfox.duckapi.managers.MessageManager;
import com.duckfox.duckreforgedden.bstats.Metrics;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class DuckReforgedDen extends DuckPlugin implements CommandExecutor {
    private static DuckReforgedDen instance;

    public static DuckReforgedDen getInstance() {
        return instance;
    }

    public static MessageManager getMessageManager() {
        return instance.messageManager;
    }

    public static ConfigManager getConfigManager() {
        return instance.configManager;
    }

    @Override
    public void onEnable() {
        instance = this;
        int pluginID = 22739;
        Metrics metrics = new Metrics(this, pluginID);
        getServer().getPluginCommand("duckreforgedden").setExecutor(this);
        getServer().getPluginManager().registerEvents(DenListener.INSTANCE, this);
        DenListener.banPokemonList = configManager.getStringList("BanPokemonInDen.list");
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void reload() {
        super.reload();
        DenListener.banPokemonList = configManager.getStringList("BanPokemonInDen.list");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.hasPermission("duckreforgedden.admin")) {
            if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
                reload();
                messageManager.sendMessage(sender, "reload");
            } else if (args.length >= 1 && "addBanItem".equalsIgnoreCase(args[0]) && sender instanceof Player) {
                Player player = (Player) sender;
                ItemStack mainHand = player.getInventory().getItemInMainHand();
                if (mainHand.getType() != Material.AIR) {
                    List<String> list = configManager.getStringList("BanItemsInDen.list");
                    list.add(mainHand.getType().name());
                    configManager.getConfig().set("BanItemsInDen.list", list);
                    try {
                        configManager.getConfig().save(new File(getDataFolder(), "config.yml"));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    messageManager.sendMessage(sender, "addBanItem", "%item%", mainHand.getType().name());
                }
            }
        }
        if (args.length >= 1 && "about".equalsIgnoreCase(args[0])) {
            messageManager.sendMessage(sender, "about", "%author%", String.valueOf(DuckReforgedDen.getInstance().getDescription().getAuthors()),"%version%", DuckReforgedDen.getInstance().getDescription().getVersion());
        }
        return super.onCommand(sender, command, label, args);
    }
}
