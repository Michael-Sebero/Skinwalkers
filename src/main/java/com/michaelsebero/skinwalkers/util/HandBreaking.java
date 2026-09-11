package com.michaelsebero.skinwalkers.util;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Block breaking at the speed of a bare-handed player, as computed by {@code ForgeHooks.blockStrength}. */
public final class HandBreaking {

    /** Allows for float error, so 15 steps of 1/15 break dirt in 15 ticks rather than 16. */
    private static final float EPSILON = 1.0E-4F;

    private HandBreaking() { }

    public static boolean isBroken(float progress) {
        return progress >= 1.0F - EPSILON;
    }

    /** Fraction of the block broken per tick; 0 for unbreakable blocks such as bedrock. */
    public static float strength(IBlockState state, World world, BlockPos pos) {
        return strength(state.getBlockHardness(world, pos), state.getMaterial().isToolNotRequired());
    }

    /**
     * A hand breaks blocks that need no tool at 1/(30 * hardness) per tick. Blocks that need a tool still break,
     * without drops, at 1/(100 * hardness): stone by hand takes 7.5 seconds.
     */
    public static float strength(float hardness, boolean harvestableByHand) {
        if (hardness < 0.0F) {
            return 0.0F;
        }
        if (hardness == 0.0F) {
            return 1.0F;
        }
        return 1.0F / hardness / (harvestableByHand ? 30.0F : 100.0F);
    }
}
