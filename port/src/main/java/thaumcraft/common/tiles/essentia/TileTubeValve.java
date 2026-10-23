package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.lib.SoundsTC;

/** Redstone transitions change valve state, not a permanent powered override of manual state. */
public class TileTubeValve extends TileTube {
    public boolean allowFlow = true;
    public boolean wasPoweredLastTick;
    public float rotation;
    public TileTubeValve(BlockPos pos, BlockState state) { super(TransportRegistries.VALVE, pos, state); }
    @Override public void update() {
        if (level == null) return;
        if (!level.isClientSide && count % 5 == 0) {
            boolean powered = gettingPower();
            if (wasPoweredLastTick && !powered && !allowFlow || !wasPoweredLastTick && powered && allowFlow) {
                allowFlow = !allowFlow; level.playSound(null, worldPosition, SoundsTC.squeek, SoundSource.BLOCKS, .7F, .9F + level.random.nextFloat() * .2F); markDirty(); syncTile(true);
            }
            wasPoweredLastTick = powered;
        }
        if (level.isClientSide) { if (!allowFlow && rotation < 360) rotation += 20; else if (allowFlow && rotation > 0) rotation -= 20; }
        super.update();
    }
    @Override protected boolean cycleFacing() {
        for (int index = facing.get3DDataValue() + 1; index < 20; index++) {
            Direction candidate = Direction.from3DDataValue(index % 6); if (!canConnectSide(candidate)) { facing = candidate; return true; }
        }
        return false;
    }
    @Override public void readSyncNBT(CompoundTag tag) { super.readSyncNBT(tag); allowFlow = tag.getBoolean("flow"); wasPoweredLastTick = tag.getBoolean("hadpower"); }
    @Override public CompoundTag writeSyncNBT(CompoundTag tag) { super.writeSyncNBT(tag); tag.putBoolean("flow", allowFlow); tag.putBoolean("hadpower", wasPoweredLastTick); return tag; }
    @Override public boolean isConnectable(Direction side) { return side != facing && super.isConnectable(side); }
    @Override public void setSuction(Aspect aspect, int amount) { if (allowFlow) super.setSuction(aspect, amount); }
}
