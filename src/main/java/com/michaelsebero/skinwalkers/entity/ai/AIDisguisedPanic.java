package com.michaelsebero.skinwalkers.entity.ai;

import com.michaelsebero.skinwalkers.entity.EntitySkinwalker;
import net.minecraft.entity.ai.EntityAIPanic;

/** Runs from anything but a player that hurts it, as the animal it poses as would. Players get revealed to. */
public class AIDisguisedPanic extends EntityAIPanic {

    private final EntitySkinwalker skinwalker;

    public AIDisguisedPanic(EntitySkinwalker skinwalker, double speed) {
        super(skinwalker, speed);
        this.skinwalker = skinwalker;
    }

    @Override
    public boolean shouldExecute() {
        return !skinwalker.isRevealed() && super.shouldExecute();
    }

    @Override
    public boolean shouldContinueExecuting() {
        return !skinwalker.isRevealed() && super.shouldContinueExecuting();
    }
}
