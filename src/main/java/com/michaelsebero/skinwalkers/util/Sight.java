package com.michaelsebero.skinwalkers.util;

import java.util.function.Predicate;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Line of sight for skinwalkers. It matches vanilla {@code canEntityBeSeen}, except that glass blocks and glass panes
 * do not block it.
 */
public final class Sight {

    private Sight() { }

    /** Clear glass of any kind: glass, stained glass and their panes. Glowstone and sea lanterns are opaque. */
    public static boolean isSeeThrough(IBlockState state) {
        return state.getMaterial() == Material.GLASS && !state.isOpaqueCube();
    }

    public static boolean canSee(World world, Vec3d from, Vec3d to) {
        return traverse(from, to, pos -> blocksSight(world, pos, from, to));
    }

    public static boolean canSee(World world, Entity viewer, Entity target) {
        return canSee(world, eyes(viewer), eyes(target));
    }

    public static Vec3d eyes(Entity entity) {
        return new Vec3d(entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ);
    }

    /** Same test as {@code World.rayTraceBlocks(from, to, false, true, false)} for one block, with glass skipped. */
    private static boolean blocksSight(World world, BlockPos pos, Vec3d from, Vec3d to) {
        if (!world.isBlockLoaded(pos)) {
            return true;
        }
        IBlockState state = world.getBlockState(pos);
        if (isSeeThrough(state)
                || state.getCollisionBoundingBox(world, pos) == Block.NULL_AABB
                || !state.getBlock().canCollideCheck(state, false)) {
            return false;
        }
        return state.collisionRayTrace(world, pos, from, to) != null;
    }

    /**
     * Walks every block the segment passes through, in order (Amanatides and Woo). Returns false as soon as
     * {@code blocking} reports a block, true when the segment reaches its end unobstructed.
     */
    static boolean traverse(Vec3d from, Vec3d to, Predicate<BlockPos> blocking) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        int x = MathHelper.floor(from.x);
        int y = MathHelper.floor(from.y);
        int z = MathHelper.floor(from.z);
        int stepX = (int) Math.signum(dx);
        int stepY = (int) Math.signum(dy);
        int stepZ = (int) Math.signum(dz);
        // t runs from 0 at 'from' to 1 at 'to'; tMax is the t at which the next block boundary on each axis is crossed.
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dx);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dy);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dz);
        double tMaxX = boundary(from.x, x, stepX, tDeltaX);
        double tMaxY = boundary(from.y, y, stepY, tDeltaY);
        double tMaxZ = boundary(from.z, z, stepZ, tDeltaZ);

        // A segment crosses at most one boundary per whole block travelled on each axis, plus one per axis.
        int maxSteps = (int) (Math.abs(dx) + Math.abs(dy) + Math.abs(dz)) + 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i <= maxSteps; i++) {
            if (blocking.test(pos.setPos(x, y, z))) {
                return false;
            }
            if (tMaxX >= 1.0 && tMaxY >= 1.0 && tMaxZ >= 1.0) {
                return true;
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
        }
        return true;
    }

    private static double boundary(double start, int block, int step, double tDelta) {
        if (step > 0) {
            return (block + 1 - start) * tDelta;
        }
        if (step < 0) {
            return (start - block) * tDelta;
        }
        return Double.POSITIVE_INFINITY;
    }

    /**
     * The part of a mob a player can look at. Hitboxes are square, but heads, beaks, snouts, necks and tails stick
     * out of them: a side-on chicken's head is a third of a block outside its box, a pig's snout half a block.
     * Widening the box covers the whole model however the mob is turned.
     */
    public static AxisAlignedBB lookTarget(AxisAlignedBB hitbox) {
        return hitbox.grow(0.6D, 0.0D, 0.6D).expand(0.0D, 0.2D, 0.0D);
    }

    /**
     * Whether a view ray points at the box. The box is padded more with distance, so the test reads as "crosshair on
     * the mob" at any range without demanding pixel precision on a distant chicken.
     */
    public static boolean isAimedAt(Vec3d eye, Vec3d look, AxisAlignedBB box) {
        Vec3d centre = new Vec3d((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2);
        double distance = eye.distanceTo(centre);
        AxisAlignedBB padded = box.grow(0.1 + distance * 0.012);
        if (padded.contains(eye)) {
            return true;
        }
        RayTraceResult hit = padded.calculateIntercept(eye, eye.add(look.scale(distance + 4)));
        return hit != null;
    }
}
