package com.michaelsebero.skinwalkers.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

class SightTest {

    @Test
    void blockOnTheLineBlocksSight() {
        Set<BlockPos> solid = new HashSet<>();
        solid.add(new BlockPos(5, 64, 0));
        assertFalse(Sight.traverse(new Vec3d(0.5, 64.5, 0.5), new Vec3d(10.5, 64.5, 0.5), solid::contains));
    }

    @Test
    void blockBesideTheLineDoesNot() {
        Set<BlockPos> solid = new HashSet<>();
        solid.add(new BlockPos(5, 64, 1));
        solid.add(new BlockPos(5, 65, 0));
        assertTrue(Sight.traverse(new Vec3d(0.5, 64.5, 0.5), new Vec3d(10.5, 64.5, 0.5), solid::contains));
    }

    @Test
    void blocksPastTheEndAreIgnored() {
        Set<BlockPos> solid = new HashSet<>();
        solid.add(new BlockPos(11, 64, 0));
        assertTrue(Sight.traverse(new Vec3d(0.5, 64.5, 0.5), new Vec3d(10.0, 64.5, 0.5), solid::contains));
    }

    @Test
    void zeroLengthSegmentChecksItsOwnBlock() {
        Set<BlockPos> visited = new LinkedHashSet<>();
        assertTrue(Sight.traverse(new Vec3d(3.2, 4.7, 5.9), new Vec3d(3.2, 4.7, 5.9), pos -> !visited.add(pos.toImmutable())));
        assertEquals(1, visited.size());
    }

    /** Every block the segment touches, found by dense sampling, is visited, in order, and nothing far off it. */
    @Test
    void visitsExactlyTheBlocksOnRandomSegments() {
        Random random = new Random(1234);
        for (int i = 0; i < 2000; i++) {
            Vec3d from = new Vec3d(random.nextDouble() * 40 - 20, random.nextDouble() * 40 + 40, random.nextDouble() * 40 - 20);
            Vec3d to = from.add(random.nextGaussian() * 15, random.nextGaussian() * 8, random.nextGaussian() * 15);

            Set<BlockPos> visited = new LinkedHashSet<>();
            assertTrue(Sight.traverse(from, to, pos -> !visited.add(pos.toImmutable())), "revisited a block");

            int samples = 20000;
            for (int s = 0; s <= samples; s++) {
                double t = (double) s / samples;
                BlockPos sampled = new BlockPos(MathHelper.floor(from.x + (to.x - from.x) * t),
                        MathHelper.floor(from.y + (to.y - from.y) * t), MathHelper.floor(from.z + (to.z - from.z) * t));
                assertTrue(visited.contains(sampled), () -> "missed " + sampled + " between " + from + " and " + to);
            }
            for (BlockPos pos : visited) {
                assertTrue(distanceToSegment(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), from, to) < 0.87,
                        () -> "visited " + pos + " off the segment " + from + " to " + to);
            }
        }
    }

    @Test
    void stopsAtTheFirstBlockingBlock() {
        Set<BlockPos> visited = new LinkedHashSet<>();
        BlockPos wall = new BlockPos(3, 64, 0);
        assertFalse(Sight.traverse(new Vec3d(0.5, 64.5, 0.5), new Vec3d(10.5, 64.5, 0.5), pos -> {
            visited.add(pos.toImmutable());
            return pos.equals(wall);
        }));
        assertEquals(4, visited.size());
    }

    @Test
    void aimedStraightAtABoxCounts() {
        AxisAlignedBB cow = new AxisAlignedBB(19.55, 64, -0.45, 20.45, 65.4, 0.45);
        Vec3d eye = new Vec3d(0, 65.62, 0);
        Vec3d atCentre = new Vec3d(20, 64.7, 0).subtract(eye).normalize();
        assertTrue(Sight.isAimedAt(eye, atCentre, cow));
    }

    @Test
    void aimedAwayDoesNotCount() {
        AxisAlignedBB cow = new AxisAlignedBB(19.55, 64, -0.45, 20.45, 65.4, 0.45);
        Vec3d eye = new Vec3d(0, 65.62, 0);
        // Five degrees to the side is about 1.7 blocks off at 20 blocks.
        double angle = Math.toRadians(5);
        Vec3d aside = new Vec3d(Math.cos(angle), -0.046, Math.sin(angle)).normalize();
        assertFalse(Sight.isAimedAt(eye, aside, cow));
        assertFalse(Sight.isAimedAt(eye, new Vec3d(-1, 0, 0), cow));
    }

    @Test
    void justOffTheEdgeOfADistantMobStillCounts() {
        // A chicken 30 blocks out: aiming a hair beside it is still looking at it.
        AxisAlignedBB chicken = new AxisAlignedBB(29.8, 64, -0.2, 30.2, 64.7, 0.2);
        Vec3d eye = new Vec3d(0, 65.62, 0);
        Vec3d beside = new Vec3d(30, 64.35, 0.45).subtract(eye).normalize();
        assertTrue(Sight.isAimedAt(eye, beside, chicken));
    }

    @Test
    void aChickensHeadCountsThoughItSticksOutOfItsHitbox() {
        // A chicken five blocks ahead, side-on, facing +X: its head is about 0.45 blocks out from the box centre.
        AxisAlignedBB chicken = new AxisAlignedBB(-0.2, 64, 4.8, 0.2, 64.7, 5.2);
        Vec3d eye = new Vec3d(0, 65.62, 0);
        Vec3d atHead = new Vec3d(0.45, 64.55, 5).subtract(eye).normalize();
        assertFalse(Sight.isAimedAt(eye, atHead, chicken), "the bare hitbox misses the head");
        assertTrue(Sight.isAimedAt(eye, atHead, Sight.lookTarget(chicken)));
    }

    @Test
    void aimingBesideAMobStillDoesNotCount() {
        AxisAlignedBB chicken = new AxisAlignedBB(-0.2, 64, 4.8, 0.2, 64.7, 5.2);
        Vec3d eye = new Vec3d(0, 65.62, 0);
        Vec3d beside = new Vec3d(1.7, 64.35, 5).subtract(eye).normalize();
        assertFalse(Sight.isAimedAt(eye, beside, Sight.lookTarget(chicken)));
    }

    private static double distanceToSegment(Vec3d point, Vec3d a, Vec3d b) {
        Vec3d ab = b.subtract(a);
        double lengthSq = ab.lengthSquared();
        double t = lengthSq == 0 ? 0 : MathHelper.clamp(point.subtract(a).dotProduct(ab) / lengthSq, 0, 1);
        return point.distanceTo(a.add(ab.scale(t)));
    }
}
