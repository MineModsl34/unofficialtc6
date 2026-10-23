package thaumcraft.common.blocks.world.plants;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.world.aura.AuraHandler;

/**
 * TC leaves. The drop (sapling 1/75, plus a quicksilver nugget for silverwood) is in the loot table (datagen),
 * and "placed by a player = does not decay" is handled by LeavesBlock itself in 1.21 (PERSISTENT).
 */
public class BlockLeavesTC extends LeavesBlock {
    private final boolean silverwood;

    public BlockLeavesTC(boolean silverwood, Properties properties) {
        super(properties);
        this.silverwood = silverwood;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return this.silverwood || super.isRandomlyTicking(state);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (this.silverwood && !state.getValue(PERSISTENT)
            && AuraHandler.getVis(level, pos) < AuraHandler.getAuraBase(level, pos)) {
            AuraHandler.addVis(level, pos, 0.01F);
        }
        super.randomTick(state, level, pos, random);
    }
}
