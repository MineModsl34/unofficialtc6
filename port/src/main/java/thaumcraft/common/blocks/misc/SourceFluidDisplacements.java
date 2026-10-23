package thaumcraft.common.blocks.misc;

import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Forge 1.12 BlockFluidBase.defaultDisplacements=false families; native partial shape is not source Material. */
public final class SourceFluidDisplacements {
    private SourceFluidDisplacements() {}
    public static boolean protectedBlock(BlockState state) {
        Block block = state.getBlock();
        return block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceBlock || block instanceof FenceGateBlock
            || block instanceof BasePressurePlateBlock || block instanceof LadderBlock || block instanceof IronBarsBlock
            || block instanceof WallBlock || block instanceof AbstractBannerBlock || block instanceof CakeBlock || block instanceof SignBlock
            || block instanceof SugarCaneBlock || block == Blocks.NETHER_PORTAL || block == Blocks.END_PORTAL || block == Blocks.BARRIER || block == Blocks.STRUCTURE_VOID;
    }
    /** Source Block.isExceptBlockForAttachWithPiston; modern Block.isExceptionForConnection is a different table. */
    public static boolean currentException(BlockState state) {
        Block block = state.getBlock();
        return block instanceof ShulkerBoxBlock || block instanceof LeavesBlock || block instanceof TrapDoorBlock
            || block instanceof AbstractCauldronBlock || block instanceof StainedGlassBlock || block instanceof TintedGlassBlock
            || block == Blocks.BEACON || block == Blocks.GLASS || block == Blocks.GLOWSTONE || block == Blocks.SEA_LANTERN
            || block == Blocks.PISTON || block == Blocks.STICKY_PISTON || block == Blocks.PISTON_HEAD;
    }
}
