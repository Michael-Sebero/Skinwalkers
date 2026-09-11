package com.michaelsebero.skinwalkers.entity.ai;

import com.michaelsebero.skinwalkers.entity.EntitySkinwalker;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.player.EntityPlayer;

/** Target selection, which only a revealed skinwalker does; disguised, it has no enemies. */
public final class AIRevealedTarget {

    private AIRevealedTarget() { }

    public static class HurtBy extends EntityAIHurtByTarget {

        private final EntitySkinwalker skinwalker;

        public HurtBy(EntitySkinwalker skinwalker) {
            super(skinwalker, false);
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
    }

    /** Sight is checked through {@code canEntityBeSeen}, so players behind glass are found too. */
    public static class NearestPlayer extends EntityAINearestAttackableTarget<EntityPlayer> {

        private final EntitySkinwalker skinwalker;

        public NearestPlayer(EntitySkinwalker skinwalker) {
            super(skinwalker, EntityPlayer.class, true);
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
    }
}
