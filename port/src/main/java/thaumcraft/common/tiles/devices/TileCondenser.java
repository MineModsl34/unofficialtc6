package thaumcraft.common.tiles.devices;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.*;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.devices.BlockCondenserLattice;

public final class TileCondenser extends DeviceTile implements IEssentiaTransport {
    public int essentia, flux, interval, cost;
    public float latticeCount = -1;
    public final List<BlockPos> blockList = new ArrayList<>(), uncloggedList = new ArrayList<>();
    private static final Direction[] HORIZONTALS = {Direction.SOUTH,Direction.WEST,Direction.NORTH,Direction.EAST};
    public TileCondenser(BlockPos pos,BlockState state) { super(pos,state,0); }
    @Override public void update() {
        if (level == null) return;
        if (latticeCount < 0) triggerCheck(); counter++;
        if (!enabled() || latticeCount <= 0) return;
        if (level.isClientSide) { if (essentia > 0 && !uncloggedList.isEmpty() && counter % Math.max(3,interval / 50) == 0) { BlockPos pos = uncloggedList.get(level.random.nextInt(uncloggedList.size())); thaumcraft.common.lib.fx.SourceFX.local(level,"condenser",net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.world.phys.Vec3.ZERO,0,0); } return; }
        if (counter % 5 == 0 && essentia < 100) fill();
        if (interval > 0 && essentia >= cost && flux < 100 && counter % interval == 0 && AuraHelper.getFlux(level,worldPosition) >= 1) { AuraHelper.drainFlux(level,worldPosition,1,false); essentia -= cost; flux++; if (level.random.nextInt(50) == 0) makeLatticeDirty(); changedAndSync(); }
    }
    private void fill() {
        for (Direction side : HORIZONTALS) {
            IEssentiaTransport transport = thaumcraft.common.tiles.essentia.TransportConnections.connectable(level,worldPosition,side); if (transport == null) continue;
            if (!transport.canOutputTo(side.getOpposite())) return;
            if (transport.getEssentiaAmount(side.getOpposite()) > 0 && transport.getSuctionAmount(side.getOpposite()) < getSuctionAmount(side) && transport.getMinimumSuction() <= getSuctionAmount(side)) { Aspect aspect = transport.getEssentiaType(side.getOpposite()); if (aspect != null) { if (aspect != Aspect.FLUX) essentia += transport.takeEssentia(aspect,1,side.getOpposite()); else makeLatticeDirty(); changedAndSync(); if (essentia >= 100) return; } }
        }
    }
    private void makeLatticeDirty() { if (uncloggedList.isEmpty()) return; int index = level.random.nextInt(uncloggedList.size()); if (index == 0) index = level.random.nextInt(uncloggedList.size()); BlockPos pos = uncloggedList.get(index); if (level.getBlockState(pos).is(BlocksTC.condenserlattice)) { level.setBlock(pos,BlocksTC.condenserlatticeDirty.defaultBlockState(),3); BlockCondenserLattice.triggerUpdate(level,pos); } }
    private static final class Frame { final BlockPos pos; final boolean skip; boolean clogged,found; int index,sides; Frame(BlockPos pos,boolean skip,boolean clogged) { this.pos = pos; this.skip = skip; this.clogged = clogged; } }
    public void triggerCheck() {
        blockList.clear(); uncloggedList.clear(); latticeCount = 0; interval = 0;
        Set<BlockPos> visited = new HashSet<>(); visited.add(worldPosition); Deque<Frame> frames = new ArrayDeque<>(); frames.push(new Frame(worldPosition,true,false));
        while (!frames.isEmpty() && latticeCount >= 0) {
            Frame frame = frames.peek();
            if (frame.index >= Direction.values().length) { if (frame.found && !frame.clogged) latticeCount += 1 - .15F * frame.sides; frames.pop(); continue; }
            Direction side = Direction.values()[frame.index++]; if (frame.skip && side != Direction.UP) continue;
            BlockPos pos = frame.pos.relative(side); BlockState state = level.getBlockState(pos); boolean clean = state.is(BlocksTC.condenserlattice), dirty = state.is(BlocksTC.condenserlatticeDirty);
            if (frame.skip && dirty) frame.clogged = true; if (clean || dirty) frame.sides++;
            if (visited.contains(pos)) continue;
            if (side == Direction.DOWN && state.is(BlocksTC.condenser)) { latticeCount = -99; break; }
            if (pos.getY() > worldPosition.getY() && pos.distSqr(worldPosition) <= 74 && (clean || dirty)) { visited.add(pos); blockList.add(pos); if (clean) uncloggedList.add(pos); frame.found = true; if (frame.skip) frame.index = 6; frames.push(new Frame(pos,false,frame.clogged || dirty)); }
        }
        latticeCount = Math.max(0,Math.min(40,latticeCount));
        if (latticeCount > 0) { interval = DeviceRules.condenserInterval(latticeCount); cost = DeviceRules.condenserCost(blockList.size()); }
    }
    @Override public boolean isConnectable(Direction side) { return side != Direction.UP; }
    @Override public boolean canInputFrom(Direction side) { return side != Direction.UP && side != Direction.DOWN; }
    @Override public boolean canOutputTo(Direction side) { return side == Direction.DOWN; }
    @Override public void setSuction(Aspect aspect,int amount) {}
    @Override public Aspect getSuctionType(Direction side) { return null; }
    @Override public int getSuctionAmount(Direction side) { return side != Direction.DOWN && essentia < 100 ? 128 : 0; }
    @Override public int takeEssentia(Aspect aspect,int amount,Direction side) { int taken = canOutputTo(side) && (aspect == null || aspect == Aspect.FLUX) ? Math.min(Math.max(0,amount),flux) : 0; if (taken > 0) { flux -= taken; changedAndSync(); } return taken; }
    /** Preserved TC6 quirk: push acknowledges units but does not store them. Actual feeding uses fill() pull. */
    @Override public int addEssentia(Aspect aspect,int amount,Direction side) { int added = canInputFrom(side) ? Math.min(Math.max(0,amount),100 - essentia) : 0; if (added > 0) changedAndSync(); return added; }
    @Override public Aspect getEssentiaType(Direction side) { return Aspect.FLUX; }
    @Override public int getEssentiaAmount(Direction side) { return flux; }
    @Override public int getMinimumSuction() { return 0; }
    @Override protected void writeDevice(CompoundTag tag,HolderLookup.Provider registries) { tag.putShort("essentia",(short)essentia); tag.putShort("flux",(short)flux); }
    @Override protected void readDevice(CompoundTag tag,HolderLookup.Provider registries) { essentia = Math.max(0,Math.min(100,tag.getShort("essentia"))); flux = Math.max(0,Math.min(100,tag.getShort("flux"))); latticeCount = -1; }
}
