package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;

/** Source filter's IAspectContainer is a read-only -1 descriptor, not an extra essentia store. */
public class TileTubeFilter extends TileTube implements IAspectContainer {
    public Aspect aspectFilter;
    public TileTubeFilter(BlockPos pos, BlockState state) { super(TransportRegistries.FILTER, pos, state); }
    @Override public void readSyncNBT(CompoundTag tag) { super.readSyncNBT(tag); aspectFilter = Aspect.getAspect(tag.getString("AspectFilter")); }
    @Override public CompoundTag writeSyncNBT(CompoundTag tag) { super.writeSyncNBT(tag); if (aspectFilter != null) tag.putString("AspectFilter", aspectFilter.getTag()); return tag; }
    @Override protected void calculateSuction(Aspect filter, boolean restrict, boolean directional) { super.calculateSuction(aspectFilter, restrict, directional); }
    @Override public AspectList getAspects() { return aspectFilter == null ? null : new AspectList().add(aspectFilter, -1); }
    @Override public void setAspects(AspectList aspects) {}
    @Override public boolean doesContainerAccept(Aspect aspect) { return false; }
    @Override public int addToContainer(Aspect aspect, int amount) { return 0; }
    @Override public boolean takeFromContainer(Aspect aspect, int amount) { return false; }
    @Override public boolean takeFromContainer(AspectList aspects) { return false; }
    @Override public boolean doesContainerContainAmount(Aspect aspect, int amount) { return false; }
    @Override public boolean doesContainerContain(AspectList aspects) { return false; }
    @Override public int containerContains(Aspect aspect) { return 0; }
}
