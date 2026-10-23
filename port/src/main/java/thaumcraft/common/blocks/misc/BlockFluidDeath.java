package thaumcraft.common.blocks.misc;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.damagesource.DamageSourceThaumcraft;
import thaumcraft.common.blocks.world.taint.TaintHelper;

/** Genuine BETA26 liquid death and Forge's source classic automaton; never an eight-quanta water surrogate. */
public final class BlockFluidDeath extends Block implements BucketPickup {
    public static final MapCodec<BlockFluidDeath> CODEC = simpleCodec(BlockFluidDeath::new);
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 3);
    private static final Direction[] SIDES = {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};
    public BlockFluidDeath() { this(Properties.ofFullCopy(Blocks.WATER)); }
    public BlockFluidDeath(Properties properties) {
        super(properties.mapColor(MapColor.COLOR_PURPLE).strength(0, 0).randomTicks().noLootTable().noCollission().noOcclusion()
            .liquid().replaceable().forceSolidOff().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 0));
    }
    @Override public MapCodec<BlockFluidDeath> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(LEVEL); }
    @Override protected FluidState getFluidState(BlockState state) {
        int amount = DeathFluidRules.quanta(state.getValue(LEVEL));
        return amount == 4 ? DeathFluidRegistries.SOURCE.getSource(false) : DeathFluidRegistries.FLOWING.getFlowing(amount, false);
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override protected boolean isPathfindable(BlockState state, PathComputationType type) { return true; }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) { return List.of(); }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) { level.scheduleTick(pos, this, DeathFluidRules.TICK_RATE); }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos other, boolean moved) { level.scheduleTick(pos, this, DeathFluidRules.TICK_RATE); }
    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) { tick(state, level, pos, random); }
    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        float slowdown = DeathFluidRules.slowdown(state.getValue(LEVEL));
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.x * slowdown, motion.y, motion.z * slowdown);
        if (!level.isClientSide && entity instanceof LivingEntity) entity.hurt(DamageSourceThaumcraft.dissolve(level), DeathFluidRules.dissolveDamage(state.getValue(LEVEL)));
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        DeathFluidPresentationHooks.bubble(level, pos, state.getValue(LEVEL), random);
        if (random.nextInt(50) == 0) level.playLocalSound(pos.getX() + random.nextFloat(), pos.getY(), pos.getZ() + random.nextFloat(), SoundEvents.LAVA_POP, SoundSource.BLOCKS, .1F + random.nextFloat() * .1F, .9F + random.nextFloat() * .15F, false);
    }
    @Override public ItemStack pickupBlock(Player player, LevelAccessor level, BlockPos pos, BlockState state) {
        if (state.getValue(LEVEL) != 0) return ItemStack.EMPTY;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
        return new ItemStack(DeathFluidRegistries.BUCKET);
    }
    @Override public Optional<SoundEvent> getPickupSound() { return Optional.of(SoundEvents.BUCKET_FILL); }
    public int quanta(BlockGetter level, BlockPos pos) {
        if (level instanceof LevelReader reader && !reader.hasChunkAt(pos)) return -1;
        BlockState state = level.getBlockState(pos);
        return state.isAir() ? 0 : state.is(this) ? DeathFluidRules.quanta(state.getValue(LEVEL)) : -1;
    }
    private boolean verticalFeed(BlockGetter level, BlockPos pos) { return level.getBlockState(pos.above()).is(this); }
    private int effectiveQuanta(BlockGetter level, BlockPos pos) {
        int amount = quanta(level, pos);
        return amount > 0 && amount < 4 && verticalFeed(level, pos) ? 4 : amount;
    }
    private boolean canDisplace(LevelReader level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        // MaterialTaint.blocksMovement is true even for thin fibre. The source death fluid has no fibre override.
        if (state.is(this) || TaintHelper.isTaint(state) || SourceFluidDisplacements.protectedBlock(state) || state.blocksMotion()) return false;
        FluidState other = state.getFluidState();
        return other.isEmpty() || other.getFluidType().getDensity(other, level, pos) < 1000;
    }
    private boolean canFlowInto(LevelReader level, BlockPos pos) { return level.hasChunkAt(pos) && (level.getBlockState(pos).is(this) || canDisplace(level, pos)); }
    private boolean sourceAt(BlockGetter level, BlockPos pos) { BlockState state = level.getBlockState(pos); return state.is(this) && state.getValue(LEVEL) == 0; }
    private void flowInto(ServerLevel level, BlockPos pos, int metadata) {
        if (!canDisplace(level, pos)) return;
        BlockState before = level.getBlockState(pos);
        if (!before.isAir() && before.getFluidState().isEmpty() && !before.is(Blocks.SNOW)) Block.dropResources(before, level, pos);
        level.setBlock(pos, defaultBlockState().setValue(LEVEL, metadata), 3);
    }
    private int flowCost(LevelReader level, BlockPos pos, int depth, Direction previous) {
        int cost = 1000;
        for (Direction side : SIDES) {
            if (side == previous.getOpposite()) continue;
            BlockPos next = pos.relative(side);
            if (!canFlowInto(level, next) || sourceAt(level, next)) continue;
            if (canFlowInto(level, next.below())) return depth;
            if (depth < 2) cost = Math.min(cost, flowCost(level, next, depth + 1, side));
        }
        return cost;
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int remaining = DeathFluidRules.quanta(state.getValue(LEVEL));
        boolean vertical = verticalFeed(level, pos);
        if (remaining < 4) {
            int adjacentSources = 0;
            if (net.neoforged.neoforge.event.EventHooks.canCreateFluidSource(level, pos, state)) for (Direction side : SIDES) {
                BlockPos other = pos.relative(side);
                if (level.hasChunkAt(other) && sourceAt(level, other)) adjacentSources++;
            }
            int max = -100;
            for (Direction side : SIDES) {
                BlockPos other = pos.relative(side);
                if (!level.hasChunkAt(other)) continue;
                int amount = effectiveQuanta(level, other);
                if (amount > 0) max = Math.max(max, amount);
            }
            int expected = adjacentSources >= 2 && (level.getBlockState(pos.below()).isSolid() || sourceAt(level, pos.below()))
                ? 4 : DeathFluidRules.expectedQuanta(vertical, max);
            if (expected != remaining) {
                remaining = expected;
                if (expected <= 0) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                else {
                    level.setBlock(pos, state.setValue(LEVEL, DeathFluidRules.metadata(expected)), 2);
                    level.scheduleTick(pos, this, DeathFluidRules.TICK_RATE);
                    level.updateNeighborsAt(pos, this);
                }
            }
        }
        if (canDisplace(level, pos.below())) { flowInto(level, pos.below(), 1); return; }
        int metadata = DeathFluidRules.outwardMetadata(remaining, vertical);
        if (metadata >= 4 || metadata < 0) return;
        boolean flowingVertically = level.getBlockState(pos.below()).is(this) || level.getBlockState(pos).is(this) && canFlowInto(level, pos.below());
        if (!sourceAt(level, pos) && flowingVertically) return;
        int[] costs = new int[4]; int minimum = 1000;
        for (int i = 0; i < SIDES.length; i++) {
            BlockPos next = pos.relative(SIDES[i]);
            costs[i] = !canFlowInto(level, next) || sourceAt(level, next) ? 1000 : canFlowInto(level, next.below()) ? 0 : flowCost(level, next, 1, SIDES[i]);
            minimum = Math.min(minimum, costs[i]);
        }
        for (int i = 0; i < SIDES.length; i++) if (costs[i] == minimum) flowInto(level, pos.relative(SIDES[i]), metadata);
    }
    /** Forge 1.12 flow-current calculation, including vertical feed's downward wall current. */
    public Vec3 sourceFlowVector(BlockGetter level, BlockPos pos) {
        int decay = 4 - effectiveQuanta(level, pos); Vec3 flow = Vec3.ZERO;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos other = pos.relative(side);
            if (level instanceof LevelReader reader && !reader.hasChunkAt(other)) continue;
            int otherDecay = 4 - effectiveQuanta(level, other);
            if (otherDecay >= 4) {
                if (!level.getBlockState(other).blocksMotion()) {
                    otherDecay = 4 - effectiveQuanta(level, other.below());
                    if (otherDecay < 4) { int power = otherDecay - (decay - 4); flow = flow.add(side.getStepX() * power, 0, side.getStepZ() * power); }
                }
            } else { int power = otherDecay - decay; flow = flow.add(side.getStepX() * power, 0, side.getStepZ() * power); }
        }
        if (verticalFeed(level, pos)) for (Direction side : Direction.Plane.HORIZONTAL) {
            if (downwardCurrent(level, pos.relative(side), side) || downwardCurrent(level, pos.relative(side).above(), side)) {
                flow = flow.normalize().add(0, -6, 0); break;
            }
        }
        return flow.normalize();
    }
    private boolean downwardCurrent(BlockGetter level, BlockPos pos, Direction side) {
        if (level instanceof LevelReader reader && !reader.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        // Source excludes Material.ICE but not Material.PACKED_ICE: using the broad native ICE tag would be wrong.
        return DeathFluidRules.downwardCurrent(state.is(this), state.getBlock() instanceof IceBlock, SourceFluidDisplacements.currentException(state), state.getBlock() instanceof StairBlock, state.isFaceSturdy(level, pos, side));
    }
}
