package thaumcraft.common.world;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.config.ModConfig;

/** Persistent API/IMC additions plus explicit user config overrides, atomically published to worldgen workers. */
public final class WorldgenBlacklists {
    private static final Store STORE = new Store();

    private WorldgenBlacklists() {
    }

    public static synchronized void setConfig(WorldgenPolicy config) {
        ModConfig.CONFIG_WORLD.worldgenPolicy = STORE.setConfig(config);
    }

    public static synchronized void registerDimension(ResourceLocation dimension, int mode) {
        ModConfig.CONFIG_WORLD.worldgenPolicy = STORE.registerDimension(dimension, mode);
    }

    public static synchronized void registerBiome(ResourceLocation biome, int mode) {
        ModConfig.CONFIG_WORLD.worldgenPolicy = STORE.registerBiome(biome, mode);
    }

    /** Independently testable state; constructing one never changes mod-global config. */
    public static final class Store {
        private WorldgenPolicy additions = WorldgenPolicy.EMPTY;
        private WorldgenPolicy config = WorldgenPolicy.EMPTY;
        private volatile WorldgenPolicy policy = WorldgenPolicy.EMPTY;

        public WorldgenPolicy policy() {
            return policy;
        }

        public synchronized WorldgenPolicy setConfig(WorldgenPolicy config) {
            this.config = Objects.requireNonNull(config, "config");
            return publish();
        }

        public synchronized WorldgenPolicy registerDimension(ResourceLocation dimension, int mode) {
            additions = additions.withDimension(Objects.requireNonNull(dimension, "dimension"), mode);
            return publish();
        }

        public synchronized WorldgenPolicy registerBiome(ResourceLocation biome, int mode) {
            additions = additions.withBiome(Objects.requireNonNull(biome, "biome"), mode);
            return publish();
        }

        private WorldgenPolicy publish() {
            policy = additions.overriddenBy(config);
            return policy;
        }
    }
}
