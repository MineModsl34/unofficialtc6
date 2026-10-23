package thaumcraft.common.blocks.world.taint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.entities.WarpEntities;
import thaumcraft.common.blocks.misc.SourceFluidDisplacements;
import thaumcraft.common.entities.monster.EntityThaumicSlime;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.potions.WarpEffects;
import thaumcraft.common.lib.warp.WarpGameplay;
import thaumcraft.common.lib.warp.WarpTaintPresentationHooks;

/** TC6 goo branches and Forge1.12 finite8-quanta cellular automaton. LEVEL means quanta-1. */
public final class BlockFluxGoo extends Block {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 7);
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    public BlockFluxGoo() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0,0).noCollission().noOcclusion()
            .randomTicks().sound(SoundsTC.GORE).pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 7));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(LEVEL); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected FluidState getFluidState(BlockState state) { return WarpTaintRegistries.FLUX.defaultFluidState().setValue(LEVEL, state.getValue(LEVEL)); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) { return state.getValue(LEVEL) < 4; }
    @Override protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState previous, boolean moved) { world.scheduleTick(pos, this, 30); }
    @Override protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block neighbor, BlockPos other, boolean moved) { world.scheduleTick(pos, this, 30); }
    @Override public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        int meta = state.getValue(LEVEL);
        if (entity instanceof EntityThaumicSlime slime) {
            if (slime.getSize() < meta && world.getRandom().nextBoolean()) {
                slime.setSize(slime.getSize() + 1, true);
                if (meta > 1) world.setBlock(pos, state.setValue(LEVEL, meta - 1), 2); else clearSourceCell(world, pos);
            }
        } else {
            Vec3 motion = entity.getDeltaMovement(); float factor = 1 - (meta + 1) / 8.0F;
            entity.setDeltaMovement(motion.x * factor, motion.y, motion.z * factor);
            if (entity instanceof LivingEntity living) WarpGameplay.addEffect(living, WarpEffects.VIS_EXHAUST, 600, meta / 3, true, true, true);
        }
    }
    @Override protected void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) { tick(state, world, pos, random); }
    @Override public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) { WarpTaintPresentationHooks.fluxBubble(world,pos,state.getValue(LEVEL),random); }
    @Override public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        int meta = state.getValue(LEVEL);
        if ((meta >= 2 && meta < 6 || meta >= 6) && world.isEmptyBlock(pos.above()) && random.nextInt(50) == 0) {
            clearSourceCell(world, pos);
            var slime = WarpEntities.THAUMIC_SLIME.create(world);
            slime.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0); slime.setSize(meta < 6 ? 1 : 2, true);
            world.addFreshEntity(slime); slime.playSound(SoundsTC.gore, 1, 1);
        } else if (random.nextInt(4) == 0) {
            if (meta == 0) {
                if (random.nextBoolean()) { AuraHelper.polluteAura(world, pos, 1, true); clearSourceCell(world, pos); }
                else world.setBlockAndUpdate(pos, BlocksTC.taintFibre.defaultBlockState());
            } else { world.setBlock(pos, state.setValue(LEVEL, meta - 1), 2); AuraHelper.polluteAura(world, pos, 1, true); }
        } else finiteFlow(world, pos, state, random);
    }
    public int quanta(BlockGetter world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isAir() ? 0 : state.is(this) ? state.getValue(LEVEL) + 1 : -1;
    }
    private static void clearSourceCell(Level world, BlockPos pos) {
        // Source World.setBlockToAir removed the finite fluid as well. Modern removeBlock
        // deliberately preserves FluidState.createLegacyBlock(), which would restore this goo.
        world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }
    private int below(BlockGetter world, BlockPos pos, int threshold) { int value = quanta(world, pos); return value >= threshold ? -1 : value; }
    private boolean displace(ServerLevel world, BlockPos pos) {
        if (world.isOutsideBuildHeight(pos) || !world.hasChunkAt(pos)) return false;
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return true;
        if (state.is(this)) return false;
        boolean fibre = state.getBlock() == BlocksTC.taintFibre;
        FluidState fluid=state.getFluidState();
        if (!fibre && (TaintHelper.isTaint(state) || state.blocksMotion() || SourceFluidDisplacements.protectedBlock(state))) return false;
        if(!fluid.isEmpty() && fluid.getFluidType().getDensity(fluid,world,pos)>=8) return false;
        if (fluid.isEmpty()&&!state.is(Blocks.SNOW)) Block.dropResources(state, world, pos);
        return true;
    }
    public void finiteFlow(ServerLevel world, BlockPos pos, BlockState state, RandomSource random) {
        int original = state.getValue(LEVEL) + 1, remaining = flowDown(world, pos, original);
        if (remaining < 1) return;
        boolean changed = original != remaining;
        if (remaining == 1) { if (changed) world.setBlock(pos, state.setValue(LEVEL, 0), 2); return; }
        int lower = remaining - 1, total = remaining, count = 1;
        for (Direction side : HORIZONTAL) {
            BlockPos other = pos.relative(side);
            if (!world.hasChunkAt(other)) continue;
            if (displace(world, other)) clearSourceCell(world, other);
            int value = below(world, other, lower);
            if (value >= 0) { count++; total += value; }
        }
        if (count == 1) { if (changed) world.setBlock(pos, state.setValue(LEVEL, remaining - 1), 2); return; }
        int each = total / count, remainder = total % count;
        for (Direction side : HORIZONTAL) {
            BlockPos other = pos.relative(side);
            if (!world.hasChunkAt(other)) continue;
            int value = below(world, other, lower);
            if (value >= 0) {
                int amount = each;
                if (remainder == count || remainder > 1 && random.nextInt(count - remainder) != 0) { amount++; remainder--; }
                if (amount != value) {
                    if (amount == 0) clearSourceCell(world, other); else world.setBlock(other, defaultBlockState().setValue(LEVEL, amount - 1), 2);
                    world.scheduleTick(other, this, 30);
                }
                count--;
            }
        }
        if (remainder > 0) each++;
        world.setBlock(pos, state.setValue(LEVEL, each - 1), 2);
    }
    private int flowDown(ServerLevel world, BlockPos pos, int input) {
        BlockPos other = pos.below();
        if (world.isOutsideBuildHeight(other)) { clearSourceCell(world, pos); return 0; }
        if (!world.hasChunkAt(other)) return input;
        int amount = below(world, other, 8);
        if (amount >= 0) {
            amount += input;
            world.setBlockAndUpdate(other, defaultBlockState().setValue(LEVEL, Math.min(8, amount) - 1)); world.scheduleTick(other, this, 30);
            if (amount > 8) return amount - 8;
            clearSourceCell(world, pos); return 0;
        }
        if (world.getFluidState(other).isEmpty() && displace(world, other)) {
            world.setBlockAndUpdate(other, defaultBlockState().setValue(LEVEL, input - 1)); world.scheduleTick(other, this, 30);
            clearSourceCell(world, pos); return 0;
        }
        FluidState otherFluid=world.getFluidState(other);
        if(!otherFluid.isEmpty() && otherFluid.getFluidType().getDensity(otherFluid,world,other)<8) {
            // Original finite-fluid density swap, including registered lighter fluids from other mods.
            BlockState displaced=world.getBlockState(other);
            world.setBlockAndUpdate(other,defaultBlockState().setValue(LEVEL,input-1));
            world.setBlockAndUpdate(pos,displaced); world.scheduleTick(other,this,30);
            world.scheduleTick(pos,displaced.getBlock(),otherFluid.getType().getTickDelay(world)); return 0;
        }
        // Source density8 is below water1000/lava3000: neither can be displaced downward.
        return input;
    }
}
