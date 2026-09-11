package com.michaelsebero.skinwalkers.util;

import net.minecraft.entity.player.EntityPlayer;

/** Reads protected player data on either side. Never instantiated; subclassing grants access to the data keys. */
public abstract class PlayerAccess extends EntityPlayer {

    private PlayerAccess() {
        super(null, null);
    }

    /** The skin layers (hat, jacket, sleeves, trouser legs, cape) the player shows. */
    public static byte modelParts(EntityPlayer player) {
        return player.getDataManager().get(PLAYER_MODEL_FLAG);
    }
}
