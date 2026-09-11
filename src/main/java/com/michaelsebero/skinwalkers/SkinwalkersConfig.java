package com.michaelsebero.skinwalkers;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = Skinwalkers.MOD_ID)
public final class SkinwalkersConfig {

    private static final int CURRENT_VERSION = 1;

    @Config.Comment("Lets new releases update settings still at an old default. Leave it alone.")
    public static int configVersion = 0;

    @Config.Comment("Where and how often skinwalkers spawn.")
    public static final Spawning spawning = new Spawning();

    @Config.Comment("How skinwalkers hunt.")
    public static final Behaviour behaviour = new Behaviour();

    @Config.Comment("Attributes, applied to skinwalkers as they spawn.")
    public static final Stats stats = new Stats();

    public static final class Spawning {
        @Config.Comment({"Spawn weight relative to endermen in the same biome, outside a full moon.",
                "1.0 is as common as endermen; 0 stops them spawning outside a full moon."})
        @Config.RangeDouble(min = 0.0, max = 10.0)
        public double weightFactor = 1.0;

        @Config.Comment("Spawn weight relative to endermen in the same biome during a full moon; 2.0 is twice as common.")
        @Config.RangeDouble(min = 0.0, max = 10.0)
        public double fullMoonWeightFactor = 2.0;

        @Config.Comment("Dimension IDs skinwalkers spawn in. They only spawn on the surface, in biomes where endermen spawn.")
        public int[] dimensions = {0};
    }

    public static final class Behaviour {
        @Config.Comment("Seconds a player must look at a disguised skinwalker before it takes their form.")
        @Config.RangeDouble(min = 0.5, max = 60.0)
        public double gazeSeconds = 1.0;

        @Config.Comment("Minimum seconds between a disguised skinwalker changing into another animal.")
        @Config.RangeInt(min = 1, max = 3600)
        public int shapeshiftCooldownSeconds = 50;

        @Config.Comment({"How far in front of a player, in blocks, a stalking skinwalker tries to stand.",
                "When the player looks down it moves to wherever their crosshair would land on it, 3 to 12 blocks out."})
        @Config.RangeInt(min = 3, max = 12)
        public int ambushDistance = 5;

        @Config.Comment("Revealed skinwalkers dig through blocks at bare-hand speed to reach their target. Also needs the mobGriefing gamerule.")
        public boolean breakBlocks = true;

        @Config.Comment("Seconds a revealed skinwalker without a target waits before disguising itself again. 0 keeps it revealed.")
        @Config.RangeInt(min = 0, max = 3600)
        public int redisguiseSeconds = 60;

        @Config.Comment({"Mobs a skinwalker may disguise itself as. It prefers those that naturally spawn in its biome.",
                "Use passive ground mobs; modded mobs whose rendering depends on server state may look wrong."})
        public String[] disguises = {
                "minecraft:cow", "minecraft:pig", "minecraft:sheep", "minecraft:chicken",
                "minecraft:horse", "minecraft:donkey", "minecraft:llama"};

        @Config.Comment("Disguises used where none of the above spawn naturally, such as deserts and snowy plains.")
        public String[] fallbackDisguises = {"minecraft:cow", "minecraft:pig", "minecraft:sheep", "minecraft:chicken"};
    }

    public static final class Stats {
        @Config.Comment("Witches have 26.")
        @Config.RangeDouble(min = 1.0, max = 1024.0)
        public double maxHealth = 30.0;

        @Config.Comment("Zombies deal 3.")
        @Config.RangeDouble(min = 0.0, max = 1024.0)
        public double attackDamage = 4.0;

        @Config.Comment("Zombies have 0.23.")
        @Config.RangeDouble(min = 0.0, max = 1.0)
        public double movementSpeed = 0.25;
    }

    /**
     * Brings settings written by older releases up to date. A value still at an old default was never chosen, so it
     * takes the new default; anything else was set on purpose and is kept. Runs once Forge writes field values back
     * to the file, after loading.
     */
    static void migrate() {
        if (configVersion >= CURRENT_VERSION) {
            return;
        }
        if (behaviour.gazeSeconds == 5.0 || behaviour.gazeSeconds == 2.0) {
            behaviour.gazeSeconds = 1.0;
        }
        if (spawning.weightFactor == 0.8) {
            spawning.weightFactor = 1.0;
        }
        if (spawning.fullMoonWeightFactor == 1.0) {
            spawning.fullMoonWeightFactor = 2.0;
        }
        configVersion = CURRENT_VERSION;
        ConfigManager.sync(Skinwalkers.MOD_ID, Config.Type.INSTANCE);
    }

    @Mod.EventBusSubscriber(modid = Skinwalkers.MOD_ID)
    public static final class Sync {

        private Sync() { }

        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (Skinwalkers.MOD_ID.equals(event.getModID())) {
                ConfigManager.sync(Skinwalkers.MOD_ID, Config.Type.INSTANCE);
            }
        }
    }

    private SkinwalkersConfig() { }
}
