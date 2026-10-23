package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;

public class TileTubeRestrict extends TileTube {
    public TileTubeRestrict(BlockPos pos, BlockState state) { super(TransportRegistries.RESTRICT, pos, state); }
    @Override protected void calculateSuction(Aspect filter, boolean restrict, boolean directional) { super.calculateSuction(filter, true, directional); }
}
