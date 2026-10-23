package thaumcraft.common.blocks.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.world.taint.BlockFluxGoo;
import thaumcraft.common.blocks.world.taint.WarpTaintRegistries;

/** Real world fluid capability replacing source IFluidBlock/FluidBlockWrapper; each operation revalidates its cell. */
public final class SourceFluidBlockHandler implements IFluidHandler {
    private final Level level;
    private final BlockPos pos;
    private final boolean finite;
    public SourceFluidBlockHandler(Level level, BlockPos pos, boolean finite) { this.level = level; this.pos = pos.immutable(); this.finite = finite; }
    private BlockState state() { return level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos) ? Blocks.AIR.defaultBlockState() : level.getBlockState(pos); }
    private boolean present(BlockState state) { return state.is(finite ? BlocksTC.fluxGoo : BlocksTC.liquidDeath); }
    private FluidStack contents(int amount) { return amount == 0 ? FluidStack.EMPTY : new FluidStack(finite ? WarpTaintRegistries.FLUX : DeathFluidRegistries.SOURCE, amount); }
    @Override public int getTanks() { return 1; }
    @Override public int getTankCapacity(int tank) { return tank == 0 ? 1000 : 0; }
    @Override public FluidStack getFluidInTank(int tank) {
        BlockState state = state(); if (tank != 0 || !present(state)) return FluidStack.EMPTY;
        int amount = finite ? state.getValue(BlockFluxGoo.LEVEL) + 1 : DeathFluidRules.quanta(state.getValue(BlockFluidDeath.LEVEL));
        int total = finite ? 8 : 4;
        if (amount < total && level.getBlockState(pos.above()).is(finite ? BlocksTC.fluxGoo : BlocksTC.liquidDeath)) amount = total;
        // Preserve the source Forge tank-display percentage, distinct from the actual finite drain volume.
        return contents(SourceFluidBlockRules.sourceTankDisplay(amount, total));
    }
    @Override public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 && !stack.isEmpty() && stack.getFluidType() == (finite ? WarpTaintRegistries.FLUX_TYPE : DeathFluidRegistries.TYPE);
    }
    @Override public int fill(FluidStack resource, FluidAction action) {
        BlockState state = state();
        if (!present(state) || !isFluidValid(0, resource) || action.execute() && level.isClientSide) return 0;
        int before = finite ? state.getValue(BlockFluxGoo.LEVEL) + 1 : 0;
        int accepted = finite ? SourceFluidBlockRules.finiteFill(before, resource.getAmount()) : resource.getAmount() >= 1000 ? 1000 : 0;
        if (accepted > 0 && action.execute()) {
            BlockState replacement = finite ? state.setValue(BlockFluxGoo.LEVEL, before + accepted / 125 - 1) : BlocksTC.liquidDeath.defaultBlockState();
            if (!level.setBlock(pos, replacement, 3)) return 0;
            level.scheduleTick(pos, replacement.getBlock(), finite ? 30 : DeathFluidRules.TICK_RATE);
        }
        return accepted;
    }
    @Override public FluidStack drain(FluidStack resource, FluidAction action) {
        if (!isFluidValid(0, resource) || !FluidStack.isSameFluidSameComponents(resource, contents(1))) return FluidStack.EMPTY;
        return drain(resource.getAmount(), action);
    }
    @Override public FluidStack drain(int maxDrain, FluidAction action) {
        BlockState state = state();
        if (!present(state) || maxDrain <= 0 || action.execute() && level.isClientSide) return FluidStack.EMPTY;
        int amount = finite ? SourceFluidBlockRules.finiteDrain(state.getValue(BlockFluxGoo.LEVEL) + 1, maxDrain)
            : SourceFluidBlockRules.classicDrain(state.getValue(BlockFluidDeath.LEVEL) == 0, maxDrain);
        if (amount > 0 && action.execute() && !level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)) return FluidStack.EMPTY;
        return contents(amount);
    }
}
