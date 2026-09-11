package com.michaelsebero.skinwalkers.entity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.annotation.Nullable;

import com.michaelsebero.skinwalkers.Skinwalkers;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;

/**
 * The sounds of the mob a skinwalker poses as. Mobs choose their sounds in protected methods, so these are called
 * reflectively on the skinwalker's stand-in mob.
 */
final class DisguiseSounds {

    private static final Method AMBIENT = find(EntityLiving.class, "func_184639_G", "getAmbientSound");
    private static final Method HURT    = find(EntityLivingBase.class, "func_184601_bQ", "getHurtSound", DamageSource.class);
    private static final Method DEATH   = find(EntityLivingBase.class, "func_184615_bR", "getDeathSound");
    private static final Method VOLUME  = find(EntityLivingBase.class, "func_70599_aP", "getSoundVolume");
    private static final Method STEP    = find(Entity.class, "func_180429_a", "playStepSound", BlockPos.class, Block.class);

    private DisguiseSounds() { }

    @Nullable
    static SoundEvent ambient(EntityLivingBase mob) {
        return (SoundEvent) call(AMBIENT, mob);
    }

    @Nullable
    static SoundEvent hurt(EntityLivingBase mob, DamageSource source) {
        return (SoundEvent) call(HURT, mob, source);
    }

    @Nullable
    static SoundEvent death(EntityLivingBase mob) {
        return (SoundEvent) call(DEATH, mob);
    }

    static float volume(EntityLivingBase mob) {
        Object volume = call(VOLUME, mob);
        return volume instanceof Float ? (Float) volume : 1.0F;
    }

    /** Plays the mob's footstep where {@code at} stands. Returns false if the step could not be played. */
    static boolean step(EntityLivingBase mob, Entity at, BlockPos pos, Block block) {
        if (STEP == null) {
            return false;
        }
        mob.setPosition(at.posX, at.posY, at.posZ);
        call(STEP, mob, pos, block);
        return true;
    }

    @Nullable
    private static Object call(@Nullable Method method, EntityLivingBase mob, Object... args) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(mob, args);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            Skinwalkers.LOG.debug("{} failed on {}", method.getName(), mob, e);
            return null;
        }
    }

    /** Looks the method up by its runtime (SRG) name, then by its development (MCP) name. */
    @Nullable
    private static Method find(Class<?> owner, String srgName, String mcpName, Class<?>... parameters) {
        for (String name : new String[] {srgName, mcpName}) {
            try {
                Method method = owner.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Try the other name.
            }
        }
        Skinwalkers.LOG.error("Missing {}.{}; disguised skinwalkers will use generic sounds", owner.getSimpleName(), mcpName);
        return null;
    }
}
