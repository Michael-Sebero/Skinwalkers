package com.michaelsebero.skinwalkers;

import javax.annotation.Nullable;

import com.michaelsebero.skinwalkers.entity.EntitySkinwalker;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;

/** Server side of the proxy; the client proxy adds rendering. */
public class CommonProxy {

    public void preInit() { }

    /** The stand-in drawn for a skinwalker wearing a player's skin, in a client world. Servers draw nothing. */
    @Nullable
    public EntityLivingBase createPlayerDouble(World world, GameProfile profile, byte modelParts, boolean leftHanded) {
        return null;
    }

    /** Called every client tick for each skinwalker, to animate the parts of its double that vanilla animates. */
    public void tickDouble(EntitySkinwalker skinwalker) { }
}
