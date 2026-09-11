package com.michaelsebero.skinwalkers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.michaelsebero.skinwalkers.entity.EntitySkinwalker;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntitySpawnPlacementRegistry;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = Skinwalkers.MOD_ID, name = Skinwalkers.MOD_NAME, version = Tags.VERSION,
     acceptedMinecraftVersions = "[1.12.2]")
public class Skinwalkers {

    public static final String MOD_ID   = "skinwalkers";
    public static final String MOD_NAME = "Skinwalkers";

    public static final Logger LOG = LogManager.getLogger(MOD_ID);

    @SidedProxy(clientSide = "com.michaelsebero.skinwalkers.client.ClientProxy",
                serverSide = "com.michaelsebero.skinwalkers.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LootTableList.register(EntitySkinwalker.LOOT_TABLE);
        EntitySpawnPlacementRegistry.setPlacementType(EntitySkinwalker.class, EntityLiving.SpawnPlacementType.ON_GROUND);
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        SkinwalkersConfig.migrate();
    }
}
