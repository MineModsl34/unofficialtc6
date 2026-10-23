package thaumcraft.common.tiles.devices;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.NoteBlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import thaumcraft.Thaumcraft;

@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class DeviceEvents {
    public record Note(BlockPos pos,int instrument,int note) {}
    private record PlantTick(BlockPos pos,net.minecraft.world.level.block.Block block,long time) {}
    private static final Map<Level,List<Note>> NOTES = new WeakHashMap<>();
    private static final Map<ServerLevel,List<PlantTick>> PLANTS = new WeakHashMap<>();
    private DeviceEvents() {}
    public static List<Note> notes(Level level) { return NOTES.getOrDefault(level,List.of()); }
    @SubscribeEvent public static void note(NoteBlockEvent.Play event) { if (event.getLevel() instanceof ServerLevel level && !event.isCanceled() && event.getInstrument().ordinal() < 10) NOTES.computeIfAbsent(level,ignored -> new ArrayList<>()).add(new Note(event.getPos().immutable(),event.getInstrument().ordinal(),event.getVanillaNoteId())); }
    public static void schedulePlant(ServerLevel level,BlockPos pos) { PLANTS.computeIfAbsent(level,ignored -> new ArrayList<>()).add(new PlantTick(pos.immutable(),level.getBlockState(pos).getBlock(),level.getGameTime() + 1)); }
    @SubscribeEvent public static void end(LevelTickEvent.Post event) {
        NOTES.remove(event.getLevel());
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        List<PlantTick> ticks = PLANTS.get(level); if (ticks == null) return;
        // In 1.12 plant updateTick handled both random and scheduled ticks. In 1.21 only randomTick grows crops.
        for (Iterator<PlantTick> iterator = ticks.iterator(); iterator.hasNext();) { PlantTick tick = iterator.next(); if (tick.time > level.getGameTime()) continue; iterator.remove(); if (level.hasChunkAt(tick.pos)) { var state = level.getBlockState(tick.pos); if (state.is(tick.block) && state.isRandomlyTicking()) state.randomTick(level,tick.pos,level.random); } }
        if (ticks.isEmpty()) PLANTS.remove(level);
    }
}
