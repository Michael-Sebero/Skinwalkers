package com.michaelsebero.skinwalkers.world;

import java.util.HashMap;
import java.util.Map;

import com.michaelsebero.skinwalkers.Skinwalkers;
import com.michaelsebero.skinwalkers.SkinwalkersConfig;
import com.michaelsebero.skinwalkers.entity.EntitySkinwalker;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Natural spawning. Skinwalkers join the monster spawn list at surface positions in biomes where endermen spawn,
 * weighted relative to the endermen there: as common usually, twice as common under a full moon.
 */
@Mod.EventBusSubscriber(modid = Skinwalkers.MOD_ID)
public final class SpawnHandler {

    /**
     * Reused per weight: the spawner checks that the entry it picked is still in the list for each spawn position,
     * and entries compare by identity.
     */
    private static final Map<Integer, Biome.SpawnListEntry> ENTRIES = new HashMap<>();

    private SpawnHandler() { }

    @SubscribeEvent
    public static void onPotentialSpawns(WorldEvent.PotentialSpawns event) {
        World world = event.getWorld();
        if (event.getType() != EnumCreatureType.MONSTER || !isSpawnDimension(world)
                || !EntitySkinwalker.isOnSurface(world, event.getPos())) {
            return;
        }
        int endermen = 0;
        for (Biome.SpawnListEntry entry : event.getList()) {
            if (entry.entityClass == EntityEnderman.class) {
                endermen += entry.itemWeight;
            }
        }
        double factor = isFullMoon(world)
                ? SkinwalkersConfig.spawning.fullMoonWeightFactor
                : SkinwalkersConfig.spawning.weightFactor;
        int weight = (int) Math.round(endermen * factor);
        if (weight > 0) {
            // Group size as for endermen; it only matters to world generation, which skinwalkers take no part in.
            event.getList().add(ENTRIES.computeIfAbsent(weight, w -> new Biome.SpawnListEntry(EntitySkinwalker.class, w, 1, 4)));
        }
    }

    private static boolean isSpawnDimension(World world) {
        int dimension = world.provider.getDimension();
        for (int allowed : SkinwalkersConfig.spawning.dimensions) {
            if (allowed == dimension) {
                return true;
            }
        }
        return false;
    }

    /** {@code World.getMoonPhase} is client-only; phase 0 is the full moon. */
    private static boolean isFullMoon(World world) {
        return world.provider.getMoonPhase(world.getWorldTime()) == 0;
    }
}
