package com.michaelsebero.skinwalkers.entity;

import javax.annotation.Nullable;

import com.michaelsebero.skinwalkers.Skinwalkers;
import com.michaelsebero.skinwalkers.util.PlayerAccess;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

/**
 * What a skinwalker looks like: a mob's saved NBT, or a player's profile and skin settings. Kept as NBT so one data
 * parameter syncs it to clients and one tag saves it.
 */
public final class Appearance {

    private static final String KIND        = "Kind";
    private static final String MOB         = "mob";
    private static final String PLAYER      = "player";
    private static final String ID          = "Id";
    private static final String DATA        = "Data";
    private static final String PROFILE     = "Profile";
    private static final String PARTS       = "Parts";
    private static final String LEFT_HANDED = "LeftHanded";

    /** Saved state that describes where a mob is or what it is doing rather than how it looks. */
    private static final String[] TRANSIENT_TAGS = {
        "Pos", "Motion", "Rotation", "FallDistance", "Fire", "Air", "OnGround", "Dimension", "Invulnerable",
        "PortalCooldown", "UUIDMost", "UUIDLeast", "Attributes", "HurtTime", "HurtByTimestamp", "DeathTime",
        "AbsorptionAmount", "ActiveEffects", "FallFlying", "Leashed", "Leash", "PersistenceRequired", "CanPickUpLoot",
        "HandDropChances", "ArmorDropChances", "LeftHanded", "InLove", "ForgeData", "ForgeCaps", "Passengers", "Tags"
    };

    private Appearance() { }

    public static boolean isEmpty(NBTTagCompound appearance) {
        return !appearance.hasKey(KIND);
    }

    public static boolean isPlayer(NBTTagCompound appearance) {
        return PLAYER.equals(appearance.getString(KIND));
    }

    @Nullable
    public static ResourceLocation mobId(NBTTagCompound appearance) {
        return MOB.equals(appearance.getString(KIND)) ? new ResourceLocation(appearance.getString(ID)) : null;
    }

    public static NBTTagCompound ofMob(EntityLiving mob) {
        NBTTagCompound data = mob.writeToNBT(new NBTTagCompound());
        for (String key : TRANSIENT_TAGS) {
            data.removeTag(key);
        }
        NBTTagCompound appearance = new NBTTagCompound();
        appearance.setString(KIND, MOB);
        appearance.setString(ID, String.valueOf(EntityList.getKey(mob)));
        appearance.setTag(DATA, data);
        return appearance;
    }

    public static NBTTagCompound ofPlayer(EntityPlayer player) {
        NBTTagCompound appearance = new NBTTagCompound();
        appearance.setString(KIND, PLAYER);
        // The profile carries the signed skin textures, so the skin survives its owner logging off.
        appearance.setTag(PROFILE, NBTUtil.writeGameProfile(new NBTTagCompound(), player.getGameProfile()));
        appearance.setByte(PARTS, PlayerAccess.modelParts(player));
        appearance.setBoolean(LEFT_HANDED, player.getPrimaryHand() == EnumHandSide.LEFT);
        return appearance;
    }

    /**
     * The entity to stand in for the skinwalker. On servers this is a mob used for its size and sounds, and players
     * have none; on clients it is what gets drawn.
     */
    @Nullable
    public static EntityLivingBase create(World world, NBTTagCompound appearance) {
        if (isPlayer(appearance)) {
            // Single-player servers run beside the client proxy, so check the world rather than the proxy.
            if (!world.isRemote) {
                return null;
            }
            GameProfile profile = NBTUtil.readGameProfileFromNBT(appearance.getCompoundTag(PROFILE));
            if (profile == null || profile.getId() == null) {
                return null;
            }
            return Skinwalkers.proxy.createPlayerDouble(world, profile, appearance.getByte(PARTS),
                    appearance.getBoolean(LEFT_HANDED));
        }
        ResourceLocation id = mobId(appearance);
        return id == null ? null : createMob(world, id, appearance.getCompoundTag(DATA));
    }

    @Nullable
    static EntityLiving createMob(World world, ResourceLocation id, @Nullable NBTTagCompound data) {
        try {
            Entity entity = EntityList.createEntityByIDFromName(id, world);
            if (!(entity instanceof EntityLiving)) {
                return null;
            }
            if (data != null) {
                entity.readFromNBT(data);
            }
            return (EntityLiving) entity;
        } catch (RuntimeException e) {
            Skinwalkers.LOG.warn("Could not create disguise {}", id, e);
            return null;
        }
    }
}
