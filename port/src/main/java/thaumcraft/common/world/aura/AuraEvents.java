package thaumcraft.common.world.aura;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import thaumcraft.Thaumcraft;

/**
 * Aura lifecycle: ChunkEvents (load/save), WorldEvents (load/unload) and part of TC6's ServerEvents.worldTick.
 * Saving the chunk separately is not needed: the thaumcraft:aura attachment is serialised with the chunk.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class AuraEvents {
    private static final Map<ResourceKey<Level>, AuraThread> THREADS = new HashMap<>();

    private AuraEvents() {
    }

    @SubscribeEvent
    public static void levelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            AuraHandler.addAuraWorld(level.dimension());
        }
    }

    @SubscribeEvent
    public static void levelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            AuraHandler.removeAuraWorld(level.dimension());
            THREADS.remove(level.dimension());
        }
    }

    @SubscribeEvent
    public static void chunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            AuraHandler.loadAuraChunk(level, chunk);
        }
    }

    @SubscribeEvent
    public static void chunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            AuraHandler.removeAuraChunk(level, event.getChunk().getPos().x, event.getChunk().getPos().z);
        }
    }

    @SubscribeEvent
    public static void levelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 20 != 0) {
            return;
        }
        THREADS.computeIfAbsent(level.dimension(), k -> new AuraThread()).tick(level);
        var trigger = AuraHandler.riftTrigger.remove(level.dimension());
        if (trigger != null && !thaumcraft.common.config.ModConfig.CONFIG_MISC.wussMode) {
            thaumcraft.common.entities.EntityFluxRift.createRift(level, trigger);
        }
    }
}
