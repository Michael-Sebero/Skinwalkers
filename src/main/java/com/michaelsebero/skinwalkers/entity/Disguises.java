package com.michaelsebero.skinwalkers.entity;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.annotation.Nullable;

import com.michaelsebero.skinwalkers.SkinwalkersConfig;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.monster.IMob;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Chooses which mob a skinwalker poses as. */
final class Disguises {

    private static final int FALLBACK_WEIGHT = 10;

    private Disguises() { }

    /**
     * A disguise for a skinwalker at {@code pos}, weighted like natural spawns: a mob that spawns in the biome if
     * one is allowed, otherwise a fallback. A skinwalker changing shape ({@code current} set) also considers the
     * fallbacks, so it has something to change into in biomes with a single native animal.
     */
    @Nullable
    static ResourceLocation pick(World world, BlockPos pos, @Nullable ResourceLocation current, Random rand) {
        Set<ResourceLocation> allowed = ids(SkinwalkersConfig.behaviour.disguises);
        Map<ResourceLocation, Integer> weights = new LinkedHashMap<>();
        for (Biome.SpawnListEntry entry : world.getBiome(pos).getSpawnableList(EnumCreatureType.CREATURE)) {
            ResourceLocation id = EntityList.getKey(entry.entityClass);
            if (id != null && entry.itemWeight > 0 && allowed.contains(id) && !id.equals(current) && isUsable(id)) {
                weights.merge(id, entry.itemWeight, Integer::sum);
            }
        }
        if (weights.isEmpty() || current != null) {
            for (ResourceLocation id : ids(SkinwalkersConfig.behaviour.fallbackDisguises)) {
                if (!id.equals(current) && isUsable(id)) {
                    weights.putIfAbsent(id, FALLBACK_WEIGHT);
                }
            }
        }

        int total = 0;
        for (int weight : weights.values()) {
            total += weight;
        }
        if (total <= 0) {
            return null;
        }
        int roll = rand.nextInt(total);
        for (Map.Entry<ResourceLocation, Integer> entry : weights.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static Set<ResourceLocation> ids(String[] names) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (String name : names) {
            if (!name.trim().isEmpty()) {
                ids.add(new ResourceLocation(name.trim()));
            }
        }
        return ids;
    }

    /** Registered, a mob, and not a monster, which would give the game away. */
    private static boolean isUsable(ResourceLocation id) {
        EntityEntry entry = ForgeRegistries.ENTITIES.getValue(id);
        if (entry == null) {
            return false;
        }
        Class<?> type = entry.getEntityClass();
        return EntityLiving.class.isAssignableFrom(type) && !IMob.class.isAssignableFrom(type);
    }
}
