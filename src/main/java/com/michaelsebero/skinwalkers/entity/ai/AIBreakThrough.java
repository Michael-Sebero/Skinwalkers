package com.michaelsebero.skinwalkers.entity.ai;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.michaelsebero.skinwalkers.SkinwalkersConfig;
import com.michaelsebero.skinwalkers.entity.EntitySkinwalker;
import com.michaelsebero.skinwalkers.util.HandBreaking;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * A revealed skinwalker that cannot reach its target digs towards it, block by block, as fast as a player's bare
 * hand: through walls, up to a target above it and down to one below.
 */
public class AIBreakThrough extends EntityAIBase {

    private final EntitySkinwalker skinwalker;
    private final World world;
    @Nullable
    private BlockPos block;
    @Nullable
    private IBlockState blockState;
    private float progress;
    private int shownStage;
    private int ticks;

    public AIBreakThrough(EntitySkinwalker skinwalker) {
        this.skinwalker = skinwalker;
        this.world = skinwalker.world;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (!skinwalker.isRevealed() || !skinwalker.isStuck() || !mayGrief()) {
            return false;
        }
        EntityLivingBase target = skinwalker.getAttackTarget();
        if (target == null || !target.isEntityAlive()) {
            return false;
        }
        block = findObstacle(target);
        return block != null;
    }

    @Override
    public boolean shouldContinueExecuting() {
        EntityLivingBase target = skinwalker.getAttackTarget();
        return block != null && skinwalker.isRevealed() && target != null && target.isEntityAlive() && mayGrief();
    }

    @Override
    public void startExecuting() {
        skinwalker.getNavigator().clearPath();
        restart();
    }

    @Override
    public void resetTask() {
        if (block != null) {
            world.sendBlockBreakProgress(skinwalker.getEntityId(), block, -1);
            block = null;
        }
        skinwalker.clearStuck();
    }

    @Override
    public void updateTask() {
        IBlockState state = world.getBlockState(block);
        if (state != blockState) {
            // Something else removed or changed it.
            world.sendBlockBreakProgress(skinwalker.getEntityId(), block, -1);
            next();
            return;
        }
        skinwalker.getLookHelper().setLookPosition(block.getX() + 0.5D, block.getY() + 0.5D, block.getZ() + 0.5D, 30.0F, 30.0F);
        progress += HandBreaking.strength(state, world, block);

        // A player's swing and hit sound, every four ticks.
        if (ticks++ % 4 == 0) {
            skinwalker.swingArm(EnumHand.MAIN_HAND);
            SoundType sound = state.getBlock().getSoundType(state, world, block, skinwalker);
            world.playSound(null, block, sound.getHitSound(), skinwalker.getSoundCategory(),
                    (sound.getVolume() + 1.0F) / 8.0F, sound.getPitch() * 0.5F);
        }

        int stage = (int) (progress * 10.0F);
        if (stage != shownStage) {
            world.sendBlockBreakProgress(skinwalker.getEntityId(), block, stage);
            shownStage = stage;
        }

        if (HandBreaking.isBroken(progress)) {
            world.sendBlockBreakProgress(skinwalker.getEntityId(), block, -1);
            // A hand only gets drops from blocks that need no tool, so stone yields nothing.
            world.destroyBlock(block, state.getMaterial().isToolNotRequired());
            next();
        }
    }

    /** Moves on to the next block in the way, so a wall two blocks high comes down in one go. */
    private void next() {
        EntityLivingBase target = skinwalker.getAttackTarget();
        block = target == null ? null : findObstacle(target);
        restart();
    }

    private void restart() {
        blockState = block == null ? null : world.getBlockState(block);
        progress = 0.0F;
        shownStage = -1;
        ticks = 0;
    }

    private boolean mayGrief() {
        return SkinwalkersConfig.behaviour.breakBlocks && ForgeEventFactory.getMobGriefingEvent(world, skinwalker);
    }

    /**
     * The first block to clear on the way to the target. Level with it, the skinwalker digs a two-high tunnel; above
     * it, a staircase up, clearing headroom to jump onto the step ahead; below it, a staircase down.
     */
    @Nullable
    private BlockPos findObstacle(EntityLivingBase target) {
        BlockPos feet = new BlockPos(skinwalker.posX, skinwalker.posY + 0.2D, skinwalker.posZ);
        double dx = target.posX - skinwalker.posX;
        double dy = target.posY - skinwalker.posY;
        double dz = target.posZ - skinwalker.posZ;

        List<BlockPos> candidates = new ArrayList<>(3);
        if (dx * dx + dz * dz < 1.0D) {
            candidates.add(dy > 0.0D ? feet.up(2) : feet.down());
        } else {
            BlockPos ahead = feet.offset(EnumFacing.getFacingFromVector((float) dx, 0.0F, (float) dz));
            if (dy > 1.0D) {
                candidates.add(feet.up(2));
                candidates.add(ahead.up());
                candidates.add(ahead.up(2));
            } else if (dy < -1.0D) {
                candidates.add(ahead.up());
                candidates.add(ahead);
                candidates.add(ahead.down());
            } else {
                candidates.add(ahead.up());
                candidates.add(ahead);
            }
        }
        for (BlockPos pos : candidates) {
            if (isBreakableObstacle(pos)) {
                return pos;
            }
        }
        return null;
    }

    private boolean isBreakableObstacle(BlockPos pos) {
        if (!world.isBlockLoaded(pos)) {
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        Block type = state.getBlock();
        return !type.isAir(state, world, pos)
                && !state.getMaterial().isLiquid()
                && state.getCollisionBoundingBox(world, pos) != Block.NULL_AABB
                && HandBreaking.strength(state, world, pos) > 0.0F
                && type.canEntityDestroy(state, world, pos, skinwalker)
                && ForgeEventFactory.onEntityDestroyBlock(skinwalker, pos, state);
    }
}
