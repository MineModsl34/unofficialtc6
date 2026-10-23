package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;

public class TileTubeOneway extends TileTube {
    public TileTubeOneway(BlockPos pos, BlockState state) { super(TransportRegistries.ONEWAY, pos, state); }
    @Override protected void calculateSuction(Aspect filter, boolean restrict, boolean directional) { super.calculateSuction(filter, restrict, true); }
    @Override protected void equalizeWithNeighbours(boolean directional) { super.equalizeWithNeighbours(true); }
}
