package com.michaelsebero.skinwalkers;

import com.michaelsebero.skinwalkers.entity.EntitySkinwalker;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;

@Mod.EventBusSubscriber(modid = Skinwalkers.MOD_ID)
public final class ModRegistry {

    // Vanilla sounds, pitched down in sounds.json; each layer plays alongside its sound.
    public static final SoundEvent SCREAM       = sound("entity.skinwalker.scream");
    public static final SoundEvent SCREAM_LAYER = sound("entity.skinwalker.scream_layer");
    public static final SoundEvent SHIFT        = sound("entity.skinwalker.shift");
    public static final SoundEvent SHIFT_LAYER  = sound("entity.skinwalker.shift_layer");

    private ModRegistry() { }

    @SubscribeEvent
    public static void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        event.getRegistry().register(EntityEntryBuilder.create()
                .entity(EntitySkinwalker.class)
                .id(new ResourceLocation(Skinwalkers.MOD_ID, "skinwalker"), 0)
                .name(Skinwalkers.MOD_ID + ".skinwalker")
                // Vanilla mobs' tracking range.
                .tracker(80, 3, true)
                .egg(0x4A3B30, 0xC9A27E)
                .build());
    }

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> event) {
        event.getRegistry().registerAll(SCREAM, SCREAM_LAYER, SHIFT, SHIFT_LAYER);
    }

    private static SoundEvent sound(String name) {
        ResourceLocation id = new ResourceLocation(Skinwalkers.MOD_ID, name);
        return new SoundEvent(id).setRegistryName(id);
    }
}
