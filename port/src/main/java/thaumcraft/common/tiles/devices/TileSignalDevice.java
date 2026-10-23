package thaumcraft.common.tiles.devices;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import thaumcraft.common.blocks.devices.BlockWorldDevice;
import thaumcraft.common.world.aura.AuraHandler;

/** Arcane ear notes/pulses, independently tunable relay thresholds, and 13x13 aura instrument. */
public final class TileSignalDevice extends DeviceTile {
    public int note, tone, redstoneSignal, in = 1, out = 15;
    public byte[] grid_amt = new byte[169];
    public TileSignalDevice(BlockPos pos,BlockState state) { super(pos,state,0); }
    @Override public void update() {
        if (level == null) return;
        if (kind() == BlockWorldDevice.Kind.DIOPTRA) {
            if (level.isClientSide) { counter = 0; return; }
            if (++counter % 20 != 0) return;
            java.util.Arrays.fill(grid_amt,(byte)0);
            for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) { var chunk = AuraHandler.getAuraChunk(level,(worldPosition.getX() >> 4) + x,(worldPosition.getZ() >> 4) + z); if (chunk != null) grid_amt[x + 6 + (z + 6) * 13] = (byte)Math.min(64,(enabled() ? chunk.getVis() : chunk.getFlux()) / 500F * 64F); }
            changedAndSync(); return;
        }
        if (level.isClientSide || kind() == BlockWorldDevice.Kind.RELAY) return;
        if (redstoneSignal > 0 && --redstoneSignal == 0) { enabled(false); notifyOutput(); }
        for (var event : DeviceEvents.notes(level)) {
            if (tone != event.instrument() || note != event.note() || worldPosition.distSqr(event.pos()) > 4096) continue;
            triggerNote();
            if (kind() == BlockWorldDevice.Kind.EAR_TOGGLE) enabled(!enabled()); else { redstoneSignal = 10; enabled(true); }
            notifyOutput(); break;
        }
    }
    public void notifyOutput() { if (level != null) { level.updateNeighborsAt(worldPosition,getBlockState().getBlock()); level.updateNeighborsAt(worldPosition.relative(facing().getOpposite()),getBlockState().getBlock()); } }
    public void updateTone() {
        BlockState support = level.getBlockState(worldPosition.relative(facing().getOpposite()));
        // TC6's material-based ten instrument mapping, not new 1.21 note-head instruments.
        if (support.is(Blocks.CLAY)) tone = 5;
        else if (support.is(Blocks.GOLD_BLOCK)) tone = 6;
        else if (support.is(net.minecraft.tags.BlockTags.WOOL)) tone = 7;
        else if (support.is(Blocks.PACKED_ICE)) tone = 8;
        else if (support.is(Blocks.BONE_BLOCK)) tone = 9;
        else { int instrument = support.instrument().ordinal(); tone = instrument <= 4 ? instrument : 0; }
        changedAndSync();
    }
    public void triggerNote() { if (level != null) level.blockEvent(worldPosition,getBlockState().getBlock(),tone,note); }
    public void tune() { note = (note + 1) % 25; changedAndSync(); triggerNote(); }
    @Override public boolean triggerEvent(int id,int param) {
        if (kind() != BlockWorldDevice.Kind.EAR && kind() != BlockWorldDevice.Kind.EAR_TOGGLE) return super.triggerEvent(id,param);
        if (level != null && level.isClientSide) { NoteBlockInstrument instrument = NoteBlockInstrument.values()[id >= 0 && id < 10 ? id : 0]; level.playLocalSound(worldPosition.getX() + .5,worldPosition.getY() + .5,worldPosition.getZ() + .5,instrument.getSoundEvent().value(),SoundSource.RECORDS,3,(float)Math.pow(2,(param - 12) / 12.0),false); level.addParticle(net.minecraft.core.particles.ParticleTypes.NOTE,worldPosition.getX() + .5,worldPosition.getY() + .5,worldPosition.getZ() + .5,param / 24.0,0,0); }
        return true;
    }
    @Override protected void writeDevice(CompoundTag tag,HolderLookup.Provider registries) { tag.putByte("note",(byte)note); tag.putByte("tone",(byte)tone); tag.putByte("in",(byte)in); tag.putByte("out",(byte)out); tag.putByteArray("grid_a",grid_amt); }
    @Override protected void readDevice(CompoundTag tag,HolderLookup.Provider registries) { note = Math.max(0,Math.min(24,tag.getByte("note"))); tone = Math.max(0,Math.min(9,tag.getByte("tone"))); in = Math.max(1,Math.min(15,tag.getByte("in"))); out = Math.max(1,Math.min(15,tag.getByte("out"))); grid_amt = java.util.Arrays.copyOf(tag.getByteArray("grid_a"),169); }
}
