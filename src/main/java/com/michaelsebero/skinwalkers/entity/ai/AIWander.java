package com.michaelsebero.skinwalkers.entity.ai;

import com.michaelsebero.skinwalkers.entity.EntitySkinwalker;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;

/** Ambles about at the pace of the mob it poses as; once revealed, only while it has no one to hunt. */
public class AIWander extends EntityAIWanderAvoidWater {

    private final EntitySkinwalker skinwalker;

    public AIWander(EntitySkinwalker skinwalker) {
        super(skinwalker, 1.0D);
        this.skinwalker = skinwalker;
    }

    @Override
    public boolean shouldExecute() {
        return (!skinwalker.isRevealed() || skinwalker.getAttackTarget() == null) && super.shouldExecute();
    }

    @Override
    public void startExecuting() {
        entity.getNavigator().tryMoveToXYZ(x, y, z, speed * skinwalker.getPaceFactor());
    }
}
