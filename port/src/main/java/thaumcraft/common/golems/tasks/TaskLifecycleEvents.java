package thaumcraft.common.golems.tasks;

import java.util.HashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import thaumcraft.api.golems.GolemHelper;

/** BETA26 ServerEvents: cleanup every 20 END ticks, before seal ticking. */
@EventBusSubscriber(modid = "thaumcraft")
public final class TaskLifecycleEvents {
    private static final HashMap<ResourceKey<Level>, Long> ticks = new HashMap<>();
    private TaskLifecycleEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void tick(LevelTickEvent.Post event) {
        Level world = event.getLevel();
        if (world.isClientSide) return;
        long counter = ticks.getOrDefault(world.dimension(), 0L);
        if (TaskRules.cleanupTick(counter)) TaskHandler.clearSuspendedOrExpiredTasks(world);
        ticks.put(world.dimension(), counter + 1);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) return;
        ResourceKey<Level> dimension = ((Level) event.getLevel()).dimension();
        ticks.remove(dimension);
        TaskHandler.tasks.remove(dimension);
        GolemHelper.provisionRequests.remove(dimension);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        ticks.clear();
        TaskHandler.tasks.clear();
        GolemHelper.provisionRequests.clear();
    }
}
