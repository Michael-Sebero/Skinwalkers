package com.michaelsebero.skinwalkers.entity.ai;

import javax.annotation.Nullable;

import com.michaelsebero.skinwalkers.SkinwalkersConfig;
import com.michaelsebero.skinwalkers.entity.EntitySkinwalker;
import com.michaelsebero.skinwalkers.util.Sight;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * At night a disguised skinwalker hounds the nearest player, wanting to be looked at. Whenever the player's crosshair
 * is not on it, it makes for the spot in front of them, at their height, where the crosshair would fall on it. It
 * only runs while the player can't see it: out of view it closes in and circles round their side, and once in view
 * it walks, sideways round them at the distance it is already at, never towards them. It stops when it is being
 * looked at, and stares back.
 */
public class AIStalk extends EntityAIBase {

    private static final double STALK_RANGE = 64.0D;
    /** Nearest and furthest it will put itself in front of the player. */
    private static final double MIN_FRONT = 3.0D;
    private static final double MAX_FRONT = 12.0D;
    /** Blocks above or below the player's feet it may stand at: their own height. */
    private static final int HEIGHT_TOLERANCE = 1;
    /** How far beyond its spot's distance it may swing out while circling round the player. */
    private static final double CIRCLE_MARGIN = 3.0D;
    /** Degrees of arc round the player covered per waypoint when circling; small, so the arc stays round. */
    private static final double CIRCLE_STEP = 30.0D;
    /** Field-of-view half-angle used to tell whether the player can see it. */
    private static final double VIEW_COS = Math.cos(Math.toRadians(65.0D));
    /** Speed multipliers: a run out of sight, an ordinary walk in it. */
    private static final double RUN = 2.0D;
    private static final double WALK = 1.0D;
    private static final int RETARGET_INTERVAL = 3;
    private static final int REPATH_INTERVAL = 5;
    /** Degrees per tick the body turns to face the player while standing. */
    private static final float BODY_TURN = 10.0F;

    private final EntitySkinwalker skinwalker;
    private final World world;
    @Nullable
    private EntityPlayer target;
    @Nullable
    private Vec3d spot;
    @Nullable
    private Vec3d pathGoal;
    private int retargetCooldown;
    private int repathCooldown;
    private boolean seen;

    public AIStalk(EntitySkinwalker skinwalker) {
        this.skinwalker = skinwalker;
        this.world = skinwalker.world;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (skinwalker.isRevealed() || world.isDaytime()) {
            return false;
        }
        target = nearestPlayer();
        return target != null;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return !skinwalker.isRevealed() && !world.isDaytime() && isStalkable(target)
                && skinwalker.getDistanceSq(target) < STALK_RANGE * STALK_RANGE;
    }

    @Override
    public void startExecuting() {
        spot = null;
        pathGoal = null;
        retargetCooldown = 0;
        repathCooldown = 0;
        skinwalker.setStalked(target);
    }

    @Override
    public void resetTask() {
        // A reveal hands the navigator straight to the chase.
        if (!skinwalker.isRevealed()) {
            skinwalker.getNavigator().clearPath();
        }
        skinwalker.setStalked(null);
        target = null;
        spot = null;
        pathGoal = null;
    }

    @Override
    public void updateTask() {
        move();
        // Whatever it is doing, it never takes its eyes off the player. Clients that are being stalked aim the
        // head at their own camera every frame as well; this keeps it right for everyone else.
        skinwalker.getLookHelper().setLookPositionWithEntity(target, 180.0F, 90.0F);
        if (skinwalker.getNavigator().noPath()) {
            // Standing still, the whole animal turns to face the player, not just its head.
            float facing = (float) (MathHelper.atan2(target.posZ - skinwalker.posZ, target.posX - skinwalker.posX) * (180.0D / Math.PI)) - 90.0F;
            skinwalker.rotationYaw += MathHelper.clamp(MathHelper.wrapDegrees(facing - skinwalker.rotationYaw), -BODY_TURN, BODY_TURN);
        }
    }

    private void move() {
        if (skinwalker.isWatchedBy(target)) {
            // Got what it wanted: stand still in the crosshair and stare back.
            hold();
            return;
        }
        boolean nowSeen = isInView();
        if (--retargetCooldown <= 0 || nowSeen != seen) {
            retargetCooldown = RETARGET_INTERVAL;
            seen = nowSeen;
            spot = povSpot();
            repathCooldown = 0;
        }
        if (spot == null || horizontalDistanceSq(spot.x, spot.z) < 1.0D) {
            hold();
            return;
        }
        double speed = (seen ? WALK : RUN) * skinwalker.getPaceFactor();
        Vec3d next = waypointTowards(spot);
        if (--repathCooldown <= 0 || pathGoal == null || next.squareDistanceTo(pathGoal) > 1.0D || skinwalker.getNavigator().noPath()) {
            repathCooldown = REPATH_INTERVAL;
            pathGoal = next;
            skinwalker.getNavigator().tryMoveToXYZ(next.x, next.y, next.z, speed);
        }
    }

    private void hold() {
        pathGoal = null;
        skinwalker.getNavigator().clearPath();
    }

    /**
     * Ground in front of the player, at their height, in plain view. Out of view, it picks its distance: where the
     * crosshair passes through its body if the player is looking a little down, otherwise the configured distance.
     * In view, it keeps the distance it is at, so getting in front means walking round, not coming closer.
     */
    @Nullable
    private Vec3d povSpot() {
        Vec3d look = target.getLook(1.0F);
        double level = Math.sqrt(look.x * look.x + look.z * look.z);
        if (level < 0.2D) {
            // Looking nearly straight up or down: there is no "in front".
            return null;
        }
        double dirX = look.x / level;
        double dirZ = look.z / level;
        Vec3d eye = target.getPositionEyes(1.0F);

        double distance = SkinwalkersConfig.behaviour.ambushDistance;
        if (seen) {
            distance = Math.max(MIN_FRONT, Math.sqrt(skinwalker.getDistanceSq(target.posX, skinwalker.posY, target.posZ)));
        } else if (look.y < -0.01D) {
            double drop = -look.y / level;
            double eyeHeight = eye.y - target.posY;
            // Between where the crosshair comes down to its back and where it reaches the ground.
            distance = MathHelper.clamp(distance, (eyeHeight - skinwalker.height) / drop, eyeHeight / drop);
        }
        if (!seen) {
            distance = MathHelper.clamp(distance, MIN_FRONT, MAX_FRONT);
        }

        for (double offset : new double[] {0.0D, 1.0D, -1.0D, 2.0D, -2.0D, 3.0D}) {
            double d = distance + offset;
            if (d < 2.0D) {
                continue;
            }
            Vec3d ground = groundNear(target.posX + dirX * d, target.posZ + dirZ * d);
            if (ground != null && Sight.canSee(world, eye, ground.add(0.0D, skinwalker.getEyeHeight(), 0.0D))) {
                return ground;
            }
        }
        return null;
    }

    /** Whether it is within the player's field of view and in sight through air or glass. */
    private boolean isInView() {
        Vec3d eye = target.getPositionEyes(1.0F);
        Vec3d centre = skinwalker.getPositionVector().add(0.0D, skinwalker.height / 2.0F, 0.0D);
        Vec3d toward = centre.subtract(eye);
        double distance = toward.length();
        return distance > 0.5D && toward.dotProduct(target.getLook(1.0F)) / distance > VIEW_COS
                && Sight.canSee(world, eye, centre);
    }

    /**
     * Rather than cutting past the player to reach a spot on their other side, it circles round them, 30 degrees at
     * a time, closing in to about the spot's distance as it goes.
     */
    private Vec3d waypointTowards(Vec3d point) {
        double ours = Math.atan2(skinwalker.posZ - target.posZ, skinwalker.posX - target.posX);
        double theirs = Math.atan2(point.z - target.posZ, point.x - target.posX);
        double turn = MathHelper.wrapDegrees(Math.toDegrees(theirs - ours));
        if (Math.abs(turn) <= CIRCLE_STEP) {
            return point;
        }
        double spotDistance = Math.sqrt((point.x - target.posX) * (point.x - target.posX) + (point.z - target.posZ) * (point.z - target.posZ));
        double current = Math.sqrt(skinwalker.getDistanceSq(target.posX, skinwalker.posY, target.posZ));
        double radius = MathHelper.clamp(current, spotDistance, spotDistance + CIRCLE_MARGIN);
        double angle = ours + Math.toRadians(Math.copySign(CIRCLE_STEP, turn));
        Vec3d ground = groundNear(target.posX + Math.cos(angle) * radius, target.posZ + Math.sin(angle) * radius);
        return ground != null ? ground : point;
    }

    private double horizontalDistanceSq(double x, double z) {
        double dx = x - skinwalker.posX;
        double dz = z - skinwalker.posZ;
        return dx * dx + dz * dz;
    }

    /**
     * Somewhere to stand in this column at the player's height: solid, dry ground with two blocks of room above.
     * Searched outward from the player's feet, so it works under roofs and in caves as well as in the open.
     */
    @Nullable
    private Vec3d groundNear(double x, double z) {
        int feet = MathHelper.floor(target.posY + 0.1D);
        for (int i = 0; i <= 2 * HEIGHT_TOLERANCE; i++) {
            int y = feet + (i + 1) / 2 * (i % 2 == 1 ? 1 : -1);
            BlockPos pos = new BlockPos(x, y, z);
            if (!world.isBlockLoaded(pos)) {
                return null;
            }
            if (isStandable(pos)) {
                return new Vec3d(x, y, z);
            }
        }
        return null;
    }

    private boolean isStandable(BlockPos feet) {
        IBlockState floor = world.getBlockState(feet.down());
        return !floor.getMaterial().isLiquid() && floor.isSideSolid(world, feet.down(), EnumFacing.UP)
                && isOpen(feet) && isOpen(feet.up());
    }

    private boolean isOpen(BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        return !state.getMaterial().isLiquid() && state.getCollisionBoundingBox(world, pos) == Block.NULL_AABB;
    }

    @Nullable
    private EntityPlayer nearestPlayer() {
        EntityPlayer nearest = null;
        double best = STALK_RANGE * STALK_RANGE;
        for (EntityPlayer player : world.playerEntities) {
            double distance = skinwalker.getDistanceSq(player);
            if (distance < best && isStalkable(player)) {
                nearest = player;
                best = distance;
            }
        }
        return nearest;
    }

    private boolean isStalkable(@Nullable EntityPlayer player) {
        return player != null && player.isEntityAlive() && !player.isSpectator() && player.world == world;
    }
}
