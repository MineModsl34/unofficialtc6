package thaumcraft.common.tiles.devices;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.common.entities.EntityFluxRift;
import thaumcraft.common.tiles.crafting.InfusionRegistries;

/** Source reservoir: 15 energy, +1/20 ticks costing .25 flux; matrix and real rift mitigation. */
public class TileStabilizer extends BlockEntity {
    public final int capacity = 15;
    protected int energy;
    private int ticks, delay;
    public TileStabilizer(BlockPos pos, BlockState state) { super(InfusionRegistries.STABILIZER, pos, state); }
    public int getEnergy() { return energy; }
    public boolean mitigate(int amount) {
        if (energy < amount) return false;
        energy -= amount;
        if (level != null) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        // Persist immediate consumption (old source notified only; native save correctness).
        changedAndSync();
        return true;
    }
    public void update() {
        if (level == null || level.isClientSide) return;
        ticks++;
        if (energy < capacity && ticks % 20 == 0) {
            energy++;
            AuraHelper.polluteAura(level, worldPosition, .25F, true);
            changedAndSync();
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
        if (energy > 0 && delay <= 0 && ticks % 5 == 0) {
            // Source searches an eight-block-expanded block AABB; facing is read but unused.
            for (EntityFluxRift rift : level.getEntitiesOfClass(EntityFluxRift.class, new AABB(worldPosition).inflate(8))) {
                if (!rift.isRemoved() && rift.getStability() != EntityFluxRift.EnumStability.VERY_STABLE && mitigate(1)) {
                    rift.addStability();
                    delay += 5;
                    if (energy <= 0) break;
                }
            }
        }
        if (delay > 0) delay--;
    }
    private void changedAndSync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag, registries); tag.putInt("energy", energy); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.loadAdditional(tag, registries); energy = Math.min(tag.getInt("energy"), capacity); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
