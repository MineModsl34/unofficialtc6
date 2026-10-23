package thaumcraft.common.blocks.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidType;
import thaumcraft.api.blocks.BlocksTC;

/** Native bucket/render bridge. The source four-quanta block automaton owns every update. */
public final class DeathFluid extends FlowingFluid {
    private final boolean source;
    public DeathFluid(boolean source) {
        this.source = source;
        registerDefaultState(stateDefinition.any().setValue(LEVEL, source ? 4 : 3).setValue(FALLING, false));
    }
    @Override protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
        super.createFluidStateDefinition(builder);
        // Both instances declare LEVEL because FlowingFluid's constructor dispatches before source is initialized.
        builder.add(LEVEL);
    }
    @Override public FluidType getFluidType() { return DeathFluidRegistries.TYPE; }
    @Override public Fluid getFlowing() { return DeathFluidRegistries.FLOWING; }
    @Override public Fluid getSource() { return DeathFluidRegistries.SOURCE; }
    @Override public Item getBucket() { return DeathFluidRegistries.BUCKET; }
    @Override public boolean isSame(Fluid fluid) { return fluid == DeathFluidRegistries.SOURCE || fluid == DeathFluidRegistries.FLOWING; }
    @Override public boolean isSource(FluidState state) { return source; }
    @Override public int getAmount(FluidState state) { return source ? DeathFluidRules.QUANTA : Math.min(DeathFluidRules.QUANTA, state.getValue(LEVEL)); }
    @Override protected boolean canConvertToSource(Level level) { return false; }
    @Override protected int getDropOff(LevelReader level) { return 1; }
    @Override protected int getSlopeFindDistance(LevelReader level) { return 2; }
    @Override public int getTickDelay(LevelReader level) { return DeathFluidRules.TICK_RATE; }
    @Override protected float getExplosionResistance() { return 0; }
    @Override protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) { Block.dropResources(state, level, pos, level.getBlockEntity(pos)); }
    @Override protected BlockState createLegacyBlock(FluidState state) {
        return BlocksTC.liquidDeath.defaultBlockState().setValue(BlockFluidDeath.LEVEL, DeathFluidRules.metadata(getAmount(state)));
    }
    @Override protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid other, Direction direction) {
        return !isSame(other) && other.getFluidType().getDensity() > 1000;
    }
    @Override public float getOwnHeight(FluidState state) { return getAmount(state) / 5F; }
    @Override public float getHeight(FluidState state, BlockGetter level, BlockPos pos) {
        return level.getFluidState(pos.above()).isEmpty() ? getOwnHeight(state) : 1;
    }
    @Override public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos) { return Shapes.box(0, 0, 0, 1, getHeight(state, level, pos), 1); }
    @Override public Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState state) {
        return ((BlockFluidDeath) BlocksTC.liquidDeath).sourceFlowVector(level, pos);
    }
    @Override public void tick(Level level, BlockPos pos, FluidState state) {
        // Deliberately no second vanilla eight-quanta update. BlockFluidDeath schedules the source automaton.
    }
}
