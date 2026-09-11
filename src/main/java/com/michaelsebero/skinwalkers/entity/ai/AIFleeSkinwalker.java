package com.michaelsebero.skinwalkers.entity.ai;

import com.michaelsebero.skinwalkers.entity.EntitySkinwalker;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.passive.EntityTameable;

/**
 * Given to passive mobs: they shy away from skinwalkers in any form, which gives a disguised one away. Mobs that are
 * fighting, carrying a rider or told to sit stand their ground.
 */
public class AIFleeSkinwalker extends EntityAIAvoidEntity<EntitySkinwalker> {

    private static final float DISTANCE = 8.0F;

    public AIFleeSkinwalker(EntityCreature creature, double farSpeed, double nearSpeed) {
        super(creature, EntitySkinwalker.class, DISTANCE, farSpeed, nearSpeed);
    }

    @Override
    public boolean shouldExecute() {
        if (entity.getAttackTarget() != null || entity.isBeingRidden()
                || entity instanceof EntityTameable && ((EntityTameable) entity).isSitting()) {
            return false;
        }
        return super.shouldExecute();
    }
}
