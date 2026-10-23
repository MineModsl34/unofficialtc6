package thaumcraft.api.research;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.lib.research.LegacyScanIdentity;

/** Exact state scan; flattened item identity is supplied by the shared legacy adapter. */
public class ScanBlockState implements IScanThing {
    String research;
    BlockState blockState;
    public ScanBlockState(BlockState state) { this("!" + state, state); }
    public ScanBlockState(String research, BlockState state) { this.research = research; blockState = state; }
    public ScanBlockState(String research, BlockState state, boolean item) {
        this(research, state);
        if (item) ScanningManager.addScannableThing(new ScanItem(research, LegacyScanIdentity.directBlockStack(state)));
    }
    @Override public boolean checkThing(Player player, Object object) {
        return object instanceof BlockPos pos && player.level().getBlockState(pos) == blockState;
    }
    @Override public String getResearchKey(Player player, Object object) { return research; }
}
