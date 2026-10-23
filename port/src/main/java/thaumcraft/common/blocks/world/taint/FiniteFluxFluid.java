package thaumcraft.common.blocks.world.taint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidType;
import thaumcraft.api.blocks.BlocksTC;

/** Rendering/material bridge only. All finite cellular-automaton updates belong to BlockFluxGoo. */
public final class FiniteFluxFluid extends Fluid {
    public FiniteFluxFluid() { registerDefaultState(stateDefinition.any().setValue(BlockFluxGoo.LEVEL, 7)); }
    @Override protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) { builder.add(BlockFluxGoo.LEVEL); }
    @Override public FluidType getFluidType() { return WarpTaintRegistries.FLUX_TYPE; }
    @Override public Item getBucket() { return Items.AIR; }
    @Override protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid other, Direction direction) { return false; }
    @Override protected Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState state) { return Vec3.ZERO; }
    @Override public int getTickDelay(LevelReader level) { return 30; }
    @Override protected float getExplosionResistance() { return 0; }
    @Override public float getHeight(FluidState state, BlockGetter level, BlockPos pos) {
        return level.getFluidState(pos.above()).isEmpty() ? getOwnHeight(state) : 1;
    }
    @Override public float getOwnHeight(FluidState state) { return getAmount(state) / 9.0F; }
    @Override protected BlockState createLegacyBlock(FluidState state) { return BlocksTC.fluxGoo.defaultBlockState().setValue(BlockFluxGoo.LEVEL, state.getValue(BlockFluxGoo.LEVEL)); }
    @Override public boolean isSource(FluidState state) { return false; } // Even eight quanta never replicate infinite sources.
    @Override public int getAmount(FluidState state) { return state.getValue(BlockFluxGoo.LEVEL) + 1; }
    @Override public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos) { return Shapes.box(0, 0, 0, 1, getHeight(state, level, pos), 1); }
}
