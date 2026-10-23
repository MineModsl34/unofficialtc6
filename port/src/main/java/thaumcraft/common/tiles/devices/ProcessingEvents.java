package thaumcraft.common.tiles.devices;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import thaumcraft.Thaumcraft;

/** Native onRemove runs before committing AIR: finish the source lava restoration after commit. */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class ProcessingEvents {
    private static final Map<Level,LinkedHashSet<BlockPos>> RESTORE = new WeakHashMap<>();
    private ProcessingEvents() {}
    static void restoreLava(Level level,BlockPos pos) { if (!level.isClientSide) RESTORE.computeIfAbsent(level,key -> new LinkedHashSet<>()).add(pos.immutable()); }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;
        var positions = RESTORE.remove(level);
        if (positions != null) for (BlockPos pos : positions) if (level.hasChunkAt(pos)) level.setBlock(pos,Blocks.LAVA.defaultBlockState(),3);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { RESTORE.clear(); }
}
