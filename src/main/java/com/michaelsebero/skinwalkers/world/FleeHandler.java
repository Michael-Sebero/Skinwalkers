package com.michaelsebero.skinwalkers.world;

import com.michaelsebero.skinwalkers.Skinwalkers;
import com.michaelsebero.skinwalkers.entity.ai.AIFleeSkinwalker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Teaches animals and villagers to keep away from skinwalkers. */
@Mod.EventBusSubscriber(modid = Skinwalkers.MOD_ID)
public final class FleeHandler {

    /** Alongside vanilla's own fear tasks, such as a villager's of zombies. */
    private static final int PRIORITY = 1;

    private FleeHandler() { }

    @SubscribeEvent
    public static void onJoinWorld(EntityJoinWorldEvent event) {
        Entity entity = event.getEntity();
        if (event.getWorld().isRemote || !(entity instanceof EntityAnimal || entity instanceof EntityVillager)) {
            return;
        }
        EntityCreature creature = (EntityCreature) entity;
        for (EntityAITasks.EntityAITaskEntry task : creature.tasks.taskEntries) {
            if (task.action instanceof AIFleeSkinwalker) {
                return;
            }
        }
        // Villagers flee zombies at 0.6; animals at about the speed they panic at.
        boolean villager = entity instanceof EntityVillager;
        creature.tasks.addTask(PRIORITY, new AIFleeSkinwalker(creature, villager ? 0.6D : 1.0D, villager ? 0.6D : 1.3D));
    }
}
