package thaumcraft.common.golems.seals;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
@EventBusSubscriber(modid="thaumcraft")
public final class SealLifecycleEvents {
 @SubscribeEvent public static void load(ChunkEvent.Load event){if(event.getLevel() instanceof ServerLevel level&&event.getChunk() instanceof LevelChunk chunk){var data=chunk.getExistingDataOrNull(Golem9SealRegistries.SEALS);if(data!=null){SealRecovery.load(level,chunk,data);for(var s:data.seals.values())if(new net.minecraft.world.level.ChunkPos(s.sealPos.pos).equals(chunk.getPos()))SealHandler.server(level).put(s.sealPos,s);}}}
 @SubscribeEvent public static void unload(ChunkEvent.Unload event){if(event.getLevel() instanceof ServerLevel level){var pos=event.getChunk().getPos();SealHandler.server(level).entrySet().removeIf(e->new net.minecraft.world.level.ChunkPos(e.getKey().pos).equals(pos));for(var t:thaumcraft.common.golems.tasks.TaskHandler.getTasks(level.dimension()).values())if(t.getSealPos()!=null&&new net.minecraft.world.level.ChunkPos(t.getSealPos().pos).equals(pos))t.setSuspended(true);}else if(event.getLevel() instanceof net.minecraft.world.level.Level level&&level.isClientSide){var chunk=event.getChunk().getPos();SealHandler.client(level.dimension()).entrySet().removeIf(e->new net.minecraft.world.level.ChunkPos(e.getKey().pos).equals(chunk));}}
 @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level){SealHandler.unload(level);SealRecovery.unload(level);}else if(event.getLevel() instanceof net.minecraft.world.level.Level level)SealHandler.clearClient(level.dimension());}
 @SubscribeEvent public static void tick(LevelTickEvent.Post event){if(event.getLevel() instanceof ServerLevel level){SealHandler.tickSealEntities(level);SealRecovery.tick(level);}}
 @SubscribeEvent public static void watch(ChunkWatchEvent.Sent event){Golem9SealNetwork.chunk(event.getPlayer(),event.getLevel(),event.getPos());}
 @SubscribeEvent public static void unwatch(ChunkWatchEvent.UnWatch event){Golem9SealNetwork.forget(event.getPlayer(),event.getLevel(),event.getPos());}
 @SubscribeEvent public static void recoveryUnload(ChunkEvent.Unload event){if(event.getLevel() instanceof ServerLevel level&&event.getChunk() instanceof LevelChunk chunk)SealRecovery.unload(level,chunk);}
}
