package com.duckfox.duckreforgedden;

import catserver.api.bukkit.event.ForgeEvent;
import com.duckfox.duckapi.nms.ItemStackProxy;
import com.duckfox.duckapi.utils.PokemonUtil;
import com.duckfox.duckapi.utils.RandomUtil;
import com.duckfox.duckapi.utils.StringUtil;
import com.duckfox.duckreforgedden.util.EntityUtil;
import com.pixelmonmod.pixelmon.api.events.CaptureEvent;
import com.pixelmonmod.pixelmon.api.events.DropEvent;
import com.pixelmonmod.pixelmon.api.events.moveskills.UseMoveSkillEvent;
import com.pixelmonmod.pixelmon.api.events.raids.*;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.battles.raids.RaidData;
import com.pixelmonmod.pixelmon.config.PixelmonItems;
import com.pixelmonmod.pixelmon.entities.EntityDen;
import com.pixelmonmod.pixelmon.enums.EnumSpecies;
import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityPortalEvent;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

public class DenListener implements Listener {
    public static final DenListener INSTANCE = new DenListener();
    public static final List<String> antiMoveSkills = Arrays.asList("mega_evolve", "primal_reversion", "ultra_burst", "crowned", "change_form");
    private final Map<UUID, Boolean> canStartRaid = new HashMap<>();
    public static List<String> banPokemonList;
    public static File DATA_FILE;
    public static FileConfiguration configuration;

    public DenListener() {
        DATA_FILE = new File(DuckReforgedDen.getInstance().getDataFolder(), "data.yml");
        if (!DATA_FILE.exists()) {
            try {
                DATA_FILE.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        configuration = YamlConfiguration.loadConfiguration(DATA_FILE);
    }

    @EventHandler
    public void onForgeEvent(ForgeEvent forgeEvent) {
        if (forgeEvent.getForgeEvent() instanceof CaptureEvent.SuccessfulRaidCapture && DuckReforgedDen.getConfigManager().getBoolean("CatchMessage.enable")) {
            CaptureEvent.SuccessfulRaidCapture event = (CaptureEvent.SuccessfulRaidCapture) forgeEvent.getForgeEvent();
            Player player = Bukkit.getPlayer(event.player.func_110124_au());
            Bukkit.getScheduler().runTaskLater(DuckReforgedDen.getInstance(), () -> {
                DuckReforgedDen.getMessageManager().sendToAll("catchMessage", player,
                        "%star%", String.valueOf(event.getRaid().getStars()),
                        "%pokemon%", event.getRaidPokemon().getLocalizedName(),
                        "%ball%", event.getRaidPokemon().getCaughtBall().getLocalizedName());
            }, DuckReforgedDen.getConfigManager().getInteger("CatchMessage.delay"));
        } else if (forgeEvent.getForgeEvent() instanceof UseMoveSkillEvent) {
            UseMoveSkillEvent event = (UseMoveSkillEvent) forgeEvent.getForgeEvent();
            Player player = Bukkit.getPlayer(event.pixelmon.getPlayerParty().getPlayer().func_110124_au());
            if (antiMoveSkills.contains(event.moveSkill.id)) {
                if (canStartRaid.getOrDefault(player.getUniqueId(), true)) {
                    int coolDown = DuckReforgedDen.getConfigManager().getInteger("FixExplodedDen.time");
                    canStartRaid.put(player.getUniqueId(), false);
                    DuckReforgedDen.getMessageManager().sendMessage(player, player, "FixExplodedDen.warn", "%time%", String.valueOf(coolDown));
                    Timer timer = new Timer();
                    TimerTask task = new TimerTask() {
                        @Override
                        public void run() {
                            canStartRaid.put(player.getUniqueId(), true);
                            DuckReforgedDen.getMessageManager().sendMessage(player, player, "FixExplodedDen.canStart");
                            timer.cancel();
                        }
                    };
                    timer.schedule(task, coolDown * 1000L);
                } else {
                    event.setCanceled(true);
                    DuckReforgedDen.getMessageManager().sendMessage(player, player, "FixExplodedDen.remega");
                }
            }
        } else if (forgeEvent.getForgeEvent() instanceof DenEvent.Interact) {
            DenEvent.Interact event = (DenEvent.Interact) forgeEvent.getForgeEvent();
            EntityDen den = event.getDen();
            Player player = Bukkit.getPlayer(event.getPlayer().func_110124_au());
            if (event.wasRightClick()) {
                /*↓↓↓↓↓↓↓↓↓↓禁止炸巢↓↓↓↓↓↓↓↓↓↓*/
                if (!canStartRaid.getOrDefault(player.getUniqueId(), true)) {
                    event.setCanceled(true);
                    DuckReforgedDen.getMessageManager().sendMessage(player, "FixExplodedDen.cantStart");
                }
                /*↑↑↑↑↑↑↑↑↑↑禁止炸巢↑↑↑↑↑↑↑↑↑↑*/

                /*↓↓↓↓↓↓↓↓↓↓禁止生成↓↓↓↓↓↓↓↓↓↓*/
                if (DuckReforgedDen.getConfigManager().getBoolean("BanPokemonInDen.enable")) {
                    if (event.wasRightClick() && den.getServerData() != null) {
                        EnumSpecies species = den.getServerData().getSpecies();
                        if (species != null && ((species.isLegendary() && (DuckReforgedDen.getConfigManager().getBoolean("BanPokemonInDen.banLegendary"))) || banPokemonList.contains(species.getPokemonName()) || banPokemonList.contains(species.getLocalizedName()))) {
                            den.clearData();
                            event.setCanceled(true);
                        }
                    }
                    Item item = event.getPlayer().func_184614_ca().func_77973_b();
                    if (item == PixelmonItems.waterdudeWishingPiece) {
                        org.bukkit.inventory.ItemStack mainHand = player.getInventory().getItemInMainHand();
                        org.bukkit.inventory.ItemStack clone = mainHand.clone();
                        int ori = mainHand.getAmount();
                        Bukkit.getScheduler().runTaskLater(DuckReforgedDen.getInstance(), () -> {

                            if (den.getServerData() != null) {
                                EnumSpecies species = den.getServerData().getSpecies();
                                if (species != null && ((species.isLegendary() && (DuckReforgedDen.getConfigManager().getBoolean("BanPokemonInDen.banLegendary"))) || banPokemonList.contains(species.getPokemonName()) || banPokemonList.contains(species.getLocalizedName()))) {
                                    den.clearData();
                                    DuckReforgedDen.getMessageManager().sendMessage(player, "BanPokemonInDen.WaterudeWishingPiece.message");
                                    if (DuckReforgedDen.getConfigManager().getBoolean("BanPokemonInDen.compensatePieceAfterAnti")) {
                                        int now = mainHand.getAmount();
                                        if (now < ori) {
                                            clone.setAmount(ori - now);
                                            player.getInventory().addItem(clone);
                                        }
                                    }
                                }
                            }
                        }, DuckReforgedDen.getConfigManager().getInteger("BanPokemonInDen.WaterudeWishingPiece.delay"));
                    }
                }
                /*↑↑↑↑↑↑↑↑↑↑禁止生成↑↑↑↑↑↑↑↑↑↑*/


            }
        } else if (forgeEvent.getForgeEvent() instanceof RaidDropsEvent && DuckReforgedDen.getConfigManager().getBoolean("BanItemsInDen.enable")) {
            RaidDropsEvent event = (RaidDropsEvent) forgeEvent.getForgeEvent();
            ArrayList<ItemStack> drops = event.getDrops();
            ArrayList<ItemStack> finalDrops = new ArrayList<>(drops);
            List<String> list = DuckReforgedDen.getConfigManager().getStringList("BanItemsInDen.list");
            for (ItemStack drop : drops) {
                org.bukkit.inventory.ItemStack bukkitCopy = ItemStackProxy.asBukkitCopy(drop);
                if (list.contains(bukkitCopy.getType().name())) {
                    finalDrops.remove(drop);
                }
            }
            event.setDrops(finalDrops);

        } else if (forgeEvent.getForgeEvent() instanceof RandomizeRaidEvent.ChooseSpecies && DuckReforgedDen.getConfigManager().getBoolean("BanPokemonInDen.enable")) {
            RandomizeRaidEvent.ChooseSpecies event = (RandomizeRaidEvent.ChooseSpecies) forgeEvent.getForgeEvent();
            RaidData raid = event.getRaid();
            if (raid != null && raid.getSpecies() != null) {
                if ((raid.getSpecies().isLegendary() && (DuckReforgedDen.getConfigManager().getBoolean("BanPokemonInDen.banLegendary"))) || banPokemonList.contains(raid.getSpecies().getPokemonName()) || banPokemonList.contains(raid.getSpecies().getLocalizedName())) {
                    event.setCanceled(true);
                }
            }
        } else if (forgeEvent.getForgeEvent() instanceof EndRaidEvent) {
            EndRaidEvent event = (EndRaidEvent) forgeEvent.getForgeEvent();
            if (event.didRaidersWin()) {
                /*↓挑战限制↓*/
                if (DuckReforgedDen.getConfigManager().getBoolean("RaidBattleLimit.enable")) {
                    for (RaidData.RaidPlayer player : event.getRaid().getPlayers()) {
                        UUID uuid = player.player;
                        Player bukkitPlayer = Bukkit.getPlayer(uuid);
                        if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                            int anInt = configuration.getInt("limit." + uuid);
                            configuration.set("limit." + uuid, anInt + 1);
                            DuckReforgedDen.getMessageManager().sendMessage(bukkitPlayer, "RaidBattleLimit.win", "%remain%", String.valueOf((getMaxRaidBattlesPerDay(bukkitPlayer) - (anInt + 1))));
                            if (DuckReforgedDen.getConfigManager().getBoolean("RaidBattleLimit.save")) {
                                try {
                                    configuration.save(DATA_FILE);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }
                }
                /*↑挑战限制↑*/
                /*↓战后指令↓*/
                if (DuckReforgedDen.getConfigManager().getBoolean("ExecCommandAfterRaid.enable")) {
                    ConfigurationSection section = DuckReforgedDen.getConfigManager().getSection("ExecCommandAfterRaid.configs");
                    if (section != null) {
                        for (RaidData.RaidPlayer player : event.getRaid().getPlayers()) {
                            if (player.isPlayer()) {
                                execute(Bukkit.getPlayer(player.player), section.getConfigurationSection(String.valueOf(event.getRaid().getStars())));
                                execute(Bukkit.getPlayer(player.player), section.getConfigurationSection("all"));
                            }
                        }
                    }
                }
                /*↑战后指令↑*/
            }
        } else if (forgeEvent.getForgeEvent() instanceof JoinRaidEvent) {
            JoinRaidEvent event = (JoinRaidEvent) forgeEvent.getForgeEvent();
            Player player = Bukkit.getPlayer(event.getPlayer().func_110124_au());
            if (DuckReforgedDen.getConfigManager().getBoolean("BanPokemonJoinRaid.enable")) {
                PlayerPartyStorage party = PokemonUtil.getParty(player);
                Pokemon[] all = party.getAll();
                List<String> list = DuckReforgedDen.getConfigManager().getStringList("BanPokemonJoinRaid.list");
                for (Pokemon pokemon : all) {
                    if (pokemon == null) continue;
                    EnumSpecies species = pokemon.getSpecies();
                    if (list.contains(species.getPokemonName()) || list.contains(species.getLocalizedName())) {
                        DuckReforgedDen.getMessageManager().sendMessage(player, "BanPokemonJoinRaid.message", "%pokemon%", pokemon.getLocalizedName());
                        event.setCanceled(true);
                        return;
                    }
                }
            }
            /*↓↓↓↓↓↓↓挑战限制↓↓↓↓↓↓↓*/
            if (DuckReforgedDen.getConfigManager().getBoolean("RaidBattleLimit.enable")) {
                if (configuration.contains("limit." + player.getUniqueId()) && configuration.getInt("limit." + player.getUniqueId()) >= getMaxRaidBattlesPerDay(player)) {
                    event.setCanceled(true);
                    DuckReforgedDen.getMessageManager().sendMessage(player, "RaidBattleLimit.Reached");
                }
            }
            /*↑↑↑↑↑↑↑挑战限制↑↑↑↑↑↑↑*/

        }
    }

    public void execute(Player player, ConfigurationSection sec) {
        if (sec == null) return;
        Map<List<String>, Integer> weightMap = new HashMap<>();
        for (String key : sec.getKeys(false)) {
            if (!sec.contains(key + ".world") || sec.getStringList(key + ".world").contains(player.getWorld().getName())) {
                int weight = sec.getInt(key + ".weight");
                List<String> commands = sec.getStringList(key + ".commands");
                weightMap.put(commands, weight);
            }
        }
        if (weightMap.isEmpty()) return;
        List<String> list = RandomUtil.weightedRandomKey(weightMap);
        for (String s : list) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), StringUtil.format(s, player));
        }
    }

    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        if ("PIXELMON_DEN".equalsIgnoreCase(event.getEntity().getType().name()) && DuckReforgedDen.getConfigManager().getBoolean("AntiDenPortal.enable")) {
            event.setCancelled(true);
            Location location = event.getFrom();
            if (DuckReforgedDen.getConfigManager().getBoolean("AntiDenPortal.warn.enable")) {
                int range = DuckReforgedDen.getConfigManager().getInteger("AntiDenPortal.warn.range");
                Collection<Entity> nearbyEntities = location.getWorld().getNearbyEntities(location, range, range, range);
                for (Entity entity : nearbyEntities) {
                    if (entity instanceof Player) {
                        Player player = (Player) entity;
                        DuckReforgedDen.getMessageManager().sendMessage(player, player, "AntiDenPortal");
                    }
                }
            }
            if (DuckReforgedDen.getConfigManager().getBoolean("AntiDenPortal.remove")) {
                event.getEntity().remove();
            }
        }
    }

    public int getMaxRaidBattlesPerDay(Player player) {
        int maxRaidPerDay = 0;
        ConfigurationSection section = DuckReforgedDen.getConfigManager().getSection("RaidBattleLimit.permissions");
        if (section != null) {
            Set<String> keys = section.getKeys(false);
            if (!keys.isEmpty()) {
                for (String key : keys) {
                    if (player.hasPermission("duckreforgedden." + key)) {
                        maxRaidPerDay = DuckReforgedDen.getConfigManager().getInteger("RaidBattleLimit.permissions." + key);
                    }
                }
            }
        }
        return maxRaidPerDay;
    }
}
