package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.lib.SoundsTC;

/** Real 39-count source centrifuge: consumes one compound and outputs ONE random component. */
public class TileCentrifuge extends TransportTile implements IAspectContainer, IEssentiaTransport {
    public Aspect aspectOut, aspectIn;
    private int count, process;
    public float rotationSpeed, rotation;
    public TileCentrifuge(BlockPos pos, BlockState state) { super(TransportRegistries.CENTRIFUGE, pos, state); }
    @Override public void readSyncNBT(CompoundTag tag) { aspectIn = Aspect.getAspect(tag.getString("aspectIn")); aspectOut = Aspect.getAspect(tag.getString("aspectOut")); }
    @Override public CompoundTag writeSyncNBT(CompoundTag tag) { if (aspectIn != null) tag.putString("aspectIn", aspectIn.getTag()); if (aspectOut != null) tag.putString("aspectOut", aspectOut.getTag()); return tag; }
    @Override public AspectList getAspects() { return aspectOut == null ? new AspectList() : new AspectList().add(aspectOut, 1); }
    @Override public void setAspects(AspectList list) {}
    @Override public int addToContainer(Aspect aspect, int amount) { if (amount > 0 && aspectOut == null) { aspectOut = aspect; changedAndSync(); amount--; } return amount; }
    @Override public boolean takeFromContainer(Aspect aspect, int amount) { if (aspectOut != null && aspect == aspectOut) { aspectOut = null; changedAndSync(); return true; } return false; }
    @Override public boolean takeFromContainer(AspectList list) { return false; }
    @Override public boolean doesContainerContainAmount(Aspect aspect, int amount) { return amount == 1 && aspect == aspectOut; }
    @Override public boolean doesContainerContain(AspectList list) { for (Aspect aspect : list.getAspects()) if (aspect == aspectOut) return true; return false; }
    @Override public int containerContains(Aspect aspect) { return aspect == aspectOut ? 1 : 0; }
    @Override public boolean doesContainerAccept(Aspect aspect) { return true; }
    @Override public boolean isConnectable(Direction side) { return side == Direction.UP || side == Direction.DOWN; }
    @Override public boolean canInputFrom(Direction side) { return side == Direction.DOWN; }
    @Override public boolean canOutputTo(Direction side) { return side == Direction.UP; }
    @Override public void setSuction(Aspect aspect, int amount) {}
    @Override public int getMinimumSuction() { return 0; }
    @Override public Aspect getSuctionType(Direction side) { return null; }
    @Override public int getSuctionAmount(Direction side) { return side == Direction.DOWN ? gettingPower() ? 0 : aspectIn == null ? 128 : 64 : 0; }
    @Override public Aspect getEssentiaType(Direction side) { return aspectOut; }
    @Override public int getEssentiaAmount(Direction side) { return aspectOut == null ? 0 : 1; }
    @Override public int takeEssentia(Aspect aspect, int amount, Direction side) { return canOutputTo(side) && takeFromContainer(aspect, amount) ? amount : 0; }
    @Override public int addEssentia(Aspect aspect, int amount, Direction side) {
        // Side/quantity deliberately follow source method; network peers gate input before calling.
        if (aspectIn == null && aspect != null && !aspect.isPrimal()) { aspectIn = aspect; process = 39; changedAndSync(); return 1; } return 0;
    }
    @Override public void update() {
        if (level == null) return;
        if (!level.isClientSide) {
            if (!gettingPower()) {
                if (aspectOut == null && aspectIn == null && ++count % 5 == 0) drawEssentia();
                if (process > 0) process--;
                if (aspectOut == null && aspectIn != null && process == 0) {
                    Aspect[] components = aspectIn.getComponents(); if (components != null && components.length == 2) { aspectOut = components[level.random.nextInt(2)]; aspectIn = null; changedAndSync(); }
                }
            }
        } else {
            if (aspectIn != null && !gettingPower() && rotationSpeed < 20) rotationSpeed += 2;
            if ((aspectIn == null || gettingPower()) && rotationSpeed > 0) rotationSpeed -= .5F;
            int previous = (int)rotation; rotation += rotationSpeed;
            if (rotation % 180 <= 20 && previous % 180 >= 160 && rotationSpeed > 0) level.playLocalSound(worldPosition, SoundsTC.pump, SoundSource.BLOCKS, 1, 1, false);
        }
    }
    private void drawEssentia() {
        IEssentiaTransport neighbor = TransportConnections.connectable(level, worldPosition, Direction.DOWN);
        if (neighbor == null || !neighbor.canOutputTo(Direction.UP)) return;
        Aspect aspect = null;
        if (neighbor.getEssentiaAmount(Direction.UP) > 0 && neighbor.getSuctionAmount(Direction.UP) < getSuctionAmount(Direction.DOWN) && getSuctionAmount(Direction.DOWN) >= neighbor.getMinimumSuction()) aspect = neighbor.getEssentiaType(Direction.UP);
        if (aspect != null && !aspect.isPrimal() && neighbor.getSuctionAmount(Direction.UP) < getSuctionAmount(Direction.DOWN) && neighbor.takeEssentia(aspect, 1, Direction.UP) == 1) { aspectIn = aspect; process = 39; changedAndSync(); }
    }
}
