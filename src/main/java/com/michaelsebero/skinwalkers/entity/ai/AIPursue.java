package com.michaelsebero.skinwalkers.entity.ai;

import com.michaelsebero.skinwalkers.entity.EntitySkinwalker;
import net.minecraft.entity.ai.EntityAIAttackMelee;

/** Chases and strikes the target once revealed. */
public class AIPursue extends EntityAIAttackMelee {

    private final EntitySkinwalker skinwalker;

    public AIPursue(EntitySkinwalker skinwalker, double speed) {
        super(skinwalker, speed, true);
        this.skinwalker = skinwalker;
    }

    @Override
    public boolean shouldExecute() {
        return skinwalker.isRevealed() && super.shouldExecute();
    }

    @Override
    public boolean shouldContinueExecuting() {
        return skinwalker.isRevealed() && super.shouldContinueExecuting();
    }

    /** After a strike made outside this task, wait the usual second before the next. */
    public void delayNextAttack() {
        attackTick = 20;
    }
}
