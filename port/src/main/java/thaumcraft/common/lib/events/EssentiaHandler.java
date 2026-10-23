package thaumcraft.common.lib.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IAspectSource;

/** Source remote-essentia ordered cache and 10s failed-search backoff.
 * World identity in cache keys prevents a stopped/reopened server reusing old source coordinates.
 * Source essentia streams cross a sided, S2C-only presentation bridge.
 */
public final class EssentiaHandler {
    private record Key(Level world, BlockPos pos) {}
    private static final Map<Key,List<BlockPos>> sources = new HashMap<>();
    private static final Map<Key,Long> sourceDelay = new HashMap<>();
    private static BlockEntity lastTarget, lastSource;
    private static Aspect lastAspect;
    private static int lastDelay;
    private EssentiaHandler() {}
    public static void clear() { sources.clear(); sourceDelay.clear(); lastTarget = lastSource = null; lastAspect = null; }
    public static void refreshSources(BlockEntity tile) { if (tile.getLevel() != null) sources.remove(new Key(tile.getLevel(), tile.getBlockPos())); }
    /** Fix the original API reflection bridge's missing four-argument overload. */
    public static boolean drainEssentia(BlockEntity tile, Aspect aspect, Direction direction, int range) { return drainEssentia(tile,aspect,direction,range,0); }
    public static boolean drainEssentia(BlockEntity tile, Aspect aspect, Direction direction, int range, int delay) { return drainEssentia(tile,aspect,direction,range,delay,false); }
    public static boolean drainEssentia(BlockEntity tile,Aspect aspect,Direction direction,int range,boolean ignoreMirror,int delay) { return drainEssentia(tile,aspect,direction,range,delay,ignoreMirror); }
    public static boolean drainEssentia(BlockEntity tile, Aspect aspect, Direction direction, int range, int delay, boolean ignoreMirror) {
        if (tile.getLevel() == null || tile.getLevel().isClientSide || aspect == null) return false;
        if (delayed(tile)) return false;
        Key key = new Key(tile.getLevel(),tile.getBlockPos());
        for (BlockPos pos : getSources(tile,direction,range)) {
            BlockEntity source = tile.getLevel().hasChunkAt(pos) ? tile.getLevel().getBlockEntity(pos) : null;
            if (!(source instanceof IAspectSource container)) break; // Source stops on a stale cache entry.
            if (!container.isBlocked() && allowedMirror(source,ignoreMirror) && container.takeFromContainer(aspect,1)) { fx(tile,source,aspect,delay); return true; }
        }
        sources.remove(key); sourceDelay.put(key,System.currentTimeMillis() + 10_000);
        return false;
    }
    public static boolean drainEssentiaWithConfirmation(BlockEntity tile, Aspect aspect, Direction direction, int range, int delay) {
        return drainEssentiaWithConfirmation(tile,aspect,direction,range,false,delay);
    }
    public static boolean drainEssentiaWithConfirmation(BlockEntity tile,Aspect aspect,Direction direction,int range,boolean ignoreMirror,int delay) {
        if (tile.getLevel() == null || tile.getLevel().isClientSide) return false;
        if (delayed(tile)) return false;
        Key key = new Key(tile.getLevel(),tile.getBlockPos());
        for (BlockPos pos : getSources(tile,direction,range)) {
            BlockEntity source = tile.getLevel().hasChunkAt(pos) ? tile.getLevel().getBlockEntity(pos) : null;
            if (!(source instanceof IAspectSource container)) break;
            if (!container.isBlocked() && allowedMirror(source,ignoreMirror) && container.doesContainerContainAmount(aspect,1)) { lastTarget = tile; lastSource = source; lastAspect = aspect; lastDelay = delay; return true; }
        }
        sources.remove(key); sourceDelay.put(key,System.currentTimeMillis() + 10_000); return false;
    }
    public static void confirmDrain() {
        if (lastTarget != null && lastSource instanceof IAspectSource container && container.takeFromContainer(lastAspect,1)) fx(lastTarget,lastSource,lastAspect,lastDelay);
        lastSource = lastTarget = null; lastAspect = null;
    }
    public static boolean findEssentia(BlockEntity tile, Aspect aspect, Direction direction, int range) {
        return findEssentia(tile,aspect,direction,range,false);
    }
    public static boolean findEssentia(BlockEntity tile,Aspect aspect,Direction direction,int range,boolean ignoreMirror) {
        if (tile.getLevel() == null || aspect == null) return false;
        for (BlockPos pos : getSources(tile,direction,range)) {
            BlockEntity source = tile.getLevel().hasChunkAt(pos) ? tile.getLevel().getBlockEntity(pos) : null;
            if (!(source instanceof IAspectSource container)) break;
            if (!container.isBlocked() && allowedMirror(source,ignoreMirror) && container.doesContainerContainAmount(aspect,1)) return true;
        }
        return false;
    }
    public static boolean canAcceptEssentia(BlockEntity tile, Aspect aspect, Direction direction, int range) {
        return canAcceptEssentia(tile,aspect,direction,range,false);
    }
    public static boolean canAcceptEssentia(BlockEntity tile,Aspect aspect,Direction direction,int range,boolean ignoreMirror) {
        if (tile.getLevel() == null) return false;
        Key key = new Key(tile.getLevel(),tile.getBlockPos());
        if (!sources.containsKey(key)) { getSources(tile,direction,range); return findEssentia(tile,aspect,direction,range,ignoreMirror); } // Original recursion typo retained.
        for (BlockPos pos : sources.get(key)) {
            BlockEntity source = tile.getLevel().hasChunkAt(pos) ? tile.getLevel().getBlockEntity(pos) : null;
            if (!(source instanceof IAspectSource container)) break;
            if (!container.isBlocked() && allowedMirror(source,ignoreMirror) && container.doesContainerAccept(aspect)) return true;
        }
        return false;
    }
    public static boolean addEssentia(BlockEntity tile, Aspect aspect, Direction direction, int range, int delay) { return addEssentia(tile,aspect,direction,range,delay,false); }
    public static boolean addEssentia(BlockEntity tile,Aspect aspect,Direction direction,int range,boolean ignoreMirror,int delay) { return addEssentia(tile,aspect,direction,range,delay,ignoreMirror); }
    public static boolean addEssentia(BlockEntity tile, Aspect aspect, Direction direction, int range, int delay, boolean ignoreMirror) {
        if (tile.getLevel() == null || tile.getLevel().isClientSide || aspect == null) return false;
        if (delayed(tile)) return false;
        List<BlockEntity> empty = new ArrayList<>();
        Key key = new Key(tile.getLevel(),tile.getBlockPos());
        for (BlockPos pos : getSources(tile,direction,range)) {
            BlockEntity source = tile.getLevel().hasChunkAt(pos) ? tile.getLevel().getBlockEntity(pos) : null;
            if (!(source instanceof IAspectSource container)) break;
            if (container.isBlocked() || !allowedMirror(source,ignoreMirror) || !container.doesContainerAccept(aspect)) continue;
            if (container.getAspects() == null || container.getAspects().visSize() == 0) { empty.add(source); continue; }
            if (container.addToContainer(aspect,1) == 0) { fx(source,tile,aspect,delay); return true; }
        }
        for (BlockEntity source : empty) if (((IAspectSource)source).addToContainer(aspect,1) == 0) { fx(source,tile,aspect,delay); return true; }
        sources.remove(key); sourceDelay.put(key,System.currentTimeMillis() + 10_000); return false;
    }
    private static List<BlockPos> getSources(BlockEntity tile, Direction direction, int range) {
        Level level = tile.getLevel();
        if (level == null || range < 0 || range > 64) return List.of();
        Key key = new Key(level,tile.getBlockPos());
        List<BlockPos> cached = sources.get(key); if (cached != null) return cached;
        Long delayed = sourceDelay.get(key);
        if (delayed != null) { if (delayed > System.currentTimeMillis()) return List.of(); sourceDelay.remove(key); }
        int start = 0; if (direction == null) { direction = Direction.UP; start = -range; }
        List<BlockPos> found = new ArrayList<>();
        for (int aa = -range; aa <= range; aa++) for (int bb = -range; bb <= range; bb++) for (int cc = start; cc < range; cc++) {
            if (aa == 0 && bb == 0 && cc == 0) continue;
            BlockPos pos = switch (direction.getAxis()) {
                case Y -> tile.getBlockPos().offset(aa,cc * direction.getStepY(),bb);
                case Z -> tile.getBlockPos().offset(aa,bb,cc * direction.getStepZ());
                case X -> tile.getBlockPos().offset(cc * direction.getStepX(),aa,bb);
            };
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof IAspectSource)) continue;
            int insert = 0; double distance = pos.distSqr(tile.getBlockPos());
            while (insert < found.size() && found.get(insert).distSqr(tile.getBlockPos()) <= distance) insert++;
            found.add(insert,pos);
        }
        if (found.isEmpty()) sourceDelay.put(key,System.currentTimeMillis() + 10_000); else sources.put(key,found);
        return found;
    }
    private static boolean delayed(BlockEntity tile) {
        Key key = new Key(tile.getLevel(),tile.getBlockPos());
        Long deadline = sourceDelay.get(key);
        return !sources.containsKey(key) && deadline != null && deadline > System.currentTimeMillis();
    }
    private static boolean allowedMirror(BlockEntity source,boolean ignoreMirror) { return !ignoreMirror || thaumcraft.api.blocks.BlocksTC.mirrorEssentia == null || !source.getBlockState().is(thaumcraft.api.blocks.BlocksTC.mirrorEssentia); }
    private static void fx(BlockEntity target, BlockEntity source, Aspect aspect, int delay) {
        if (target.getLevel() instanceof ServerLevel level)
            thaumcraft.common.lib.fx.SourceFX.essentia(level,target.getBlockPos(),source.getBlockPos(),aspect.getColor(),delay);
    }
}
