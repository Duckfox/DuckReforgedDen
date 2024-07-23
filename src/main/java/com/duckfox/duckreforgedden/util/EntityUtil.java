package com.duckfox.duckreforgedden.util;

import net.minecraft.entity.Entity;
import net.minecraft.world.WorldServer;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;

public class EntityUtil {
    public static Entity getEntity(org.bukkit.entity.Entity entity) {
        return ((CraftWorld) entity.getWorld()).getHandle().getWorld().getHandle().func_73045_a(entity.getEntityId());
    }
}
