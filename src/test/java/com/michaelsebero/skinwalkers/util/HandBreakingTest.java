package com.michaelsebero.skinwalkers.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Break times by hand, in ticks, as listed for players on the Minecraft wiki. */
class HandBreakingTest {

    @Test
    void dirtTakesThreeQuartersOfASecond() {
        assertEquals(15, ticks(0.5F, true));
    }

    @Test
    void planksTakeThreeSeconds() {
        assertEquals(60, ticks(2.0F, true));
    }

    @Test
    void stoneNeedsAPickaxeSoTakesSevenAndAHalfSeconds() {
        assertEquals(150, ticks(1.5F, false));
    }

    @Test
    void obsidianTakesOverFourMinutes() {
        assertEquals(5000, ticks(50.0F, false));
    }

    @Test
    void instantAndUnbreakable() {
        assertEquals(1, ticks(0.0F, true));
        assertEquals(0.0F, HandBreaking.strength(-1.0F, true));
    }

    /** Ticks of accumulated strength until the block breaks, as the dig task counts them. */
    private static int ticks(float hardness, boolean harvestable) {
        float strength = HandBreaking.strength(hardness, harvestable);
        float progress = 0.0F;
        int ticks = 0;
        while (!HandBreaking.isBroken(progress)) {
            progress += strength;
            ticks++;
        }
        return ticks;
    }
}
