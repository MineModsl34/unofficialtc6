package thaumcraft.common.blocks.world.taint;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.potions.WarpEffects;

/** TC6 fibre geometry/drop behavior and stage8 real seed-gated spreading. */
public final class BlockTaintFibre extends Block {
    public static final BooleanProperty NORTH = BooleanProperty.create("north"), EAST = BooleanProperty.create("east"),
        SOUTH = BooleanProperty.create("south"), WEST = BooleanProperty.create("west"), UP = BooleanProperty.create("up"), DOWN = BooleanProperty.create("down"),
        GROWTH1 = BooleanProperty.create("growth1"), GROWTH2 = BooleanProperty.create("growth2"), GROWTH3 = BooleanProperty.create("growth3"), GROWTH4 = BooleanProperty.create("growth4");
    private static final BooleanProperty[] FACES = {DOWN, UP, NORTH, SOUTH, WEST, EAST};
    private static final VoxelShape[] FACE_SHAPES = {
        Shapes.box(0,0,0,1,.05F,1), Shapes.box(0,.95F,0,1,1,1), Shapes.box(0,0,0,1,1,.05F),
        Shapes.box(0,0,.95F,1,1,1), Shapes.box(0,0,0,.05F,1,1), Shapes.box(.95F,0,0,1,1,1)};
    public BlockTaintFibre() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(1).noOcclusion().dynamicShape()
            .replaceable().randomTicks().sound(SoundsTC.GORE).pushReaction(PushReaction.BLOCK).lightLevel(state -> state.getValue(GROWTH3) ? 12 : state.getValue(GROWTH2) || state.getValue(GROWTH4) ? 6 : 0));
        registerDefaultState(stateDefinition.any().setValue(NORTH,false).setValue(EAST,false).setValue(SOUTH,false).setValue(WEST,false)
            .setValue(UP,false).setValue(DOWN,false).setValue(GROWTH1,false).setValue(GROWTH2,false).setValue(GROWTH3,false).setValue(GROWTH4,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(NORTH,EAST,SOUTH,WEST,UP,DOWN,GROWTH1,GROWTH2,GROWTH3,GROWTH4); }
    public static long sourcePositionSeed(BlockPos pos) { return ((long)pos.getX() & 0x3ffffffL) << 38 | ((long)pos.getY() & 0xfffL) << 26 | (long)pos.getZ() & 0x3ffffffL; }
    private boolean drawsAt(BlockGetter world, BlockPos pos, Direction side) {
        BlockState state = world.getBlockState(pos);
        return !state.is(this) && state.getBlock() != BlocksTC.taintFeature && state.isFaceSturdy(world, pos, side.getOpposite());
    }
    public BlockState actualState(BlockState state, BlockGetter world, BlockPos pos) {
        for (Direction face : Direction.values()) state = state.setValue(FACES[face.ordinal()], drawsAt(world, pos.relative(face), face));
        int q = new Random(sourcePositionSeed(pos)).nextInt(50), growth = 0;
        if (state.getValue(DOWN)) growth = q < 4 ? 1 : q == 4 || q == 5 ? 2 : q == 6 ? 3 : 0;
        if (state.getValue(UP) && q > 47) growth = 4;
        return state.setValue(GROWTH1,growth==1).setValue(GROWTH2,growth==2).setValue(GROWTH3,growth==3).setValue(GROWTH4,growth==4);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return actualState(defaultBlockState(), context.getLevel(), context.getClickedPos()); }
    @Override protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState previous, boolean moved) {
        BlockState actual = actualState(state, world, pos); if (actual != state) world.setBlock(pos, actual, 2);
    }
    @Override protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor world, BlockPos pos, BlockPos other) { return actualState(state, world, pos); }
    @Override protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos other, boolean moved) {
        BlockState actual = actualState(state, world, pos);
        if (!hasGrowth(actual) && isOnlyAdjacentToTaint(world,pos)) world.removeBlock(pos,false);
        else if (actual != state) world.setBlock(pos,actual,2);
    }
    private static boolean hasGrowth(BlockState state) { return state.getValue(GROWTH1)||state.getValue(GROWTH2)||state.getValue(GROWTH3)||state.getValue(GROWTH4); }
    public static boolean isOnlyAdjacentToTaint(BlockGetter world, BlockPos pos) {
        for (Direction side : Direction.values()) { BlockPos other = pos.relative(side); BlockState state = world.getBlockState(other);
            if (TaintSpreadRules.isNonTaintSupport(state.isAir(),TaintHelper.isTaint(state),state.isFaceSturdy(world,other,side.getOpposite()))) return false; }
        return true;
    }
    @Override protected void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        BlockState actual=actualState(state,world,pos);
        if(!hasGrowth(actual)&&isOnlyAdjacentToTaint(world,pos)||!TaintHelper.isNearTaintSeed(world,pos))world.removeBlock(pos,false);
        else TaintHelper.spreadFibres(world,pos);
    }
    @Override public void stepOn(Level world, BlockPos pos, BlockState state, Entity entity) {
        if (!world.isClientSide && entity instanceof LivingEntity living && !living.getType().is(EntityTypeTags.UNDEAD) && world.random.nextInt(750)==0)
            living.addEffect(new MobEffectInstance(WarpEffects.FLUX_TAINT,200,0,false,true));
        super.stepOn(world,pos,state,entity);
    }
    private VoxelShape faces(BlockState state, BlockGetter world, BlockPos pos) {
        BlockState actual = actualState(state,world,pos); VoxelShape shape = Shapes.empty();
        for(int i=0;i<FACES.length;i++) if(actual.getValue(FACES[i])) shape = Shapes.or(shape,FACE_SHAPES[i]); return shape;
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { return faces(state,world,pos); }
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type) { return true; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        BlockState actual=actualState(state,world,pos); VoxelShape shape=faces(state,world,pos);
        if(actual.getValue(GROWTH1)) return Shapes.or(shape,Shapes.box(.1F,0,.1F,.9F,.4F,.9F));
        if(actual.getValue(GROWTH2)) return Shapes.or(shape,Shapes.box(.2F,0,.2F,.8F,1,.8F));
        if(actual.getValue(GROWTH3)) return Shapes.or(shape,Shapes.box(.25,0,.25,.75,.3125,.75));
        if(actual.getValue(GROWTH4)) return Shapes.or(shape,Shapes.box(.1F,.3F,.1F,.9F,1,.9F)); return shape;
    }
    @Override public int getFlammability(BlockState state, BlockGetter world, BlockPos pos, Direction face) { return 3; }
    @Override public int getFireSpreadSpeed(BlockState state, BlockGetter world, BlockPos pos, Direction face) { return 3; }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ServerLevel world=params.getLevel(); BlockPos pos=BlockPos.containing(params.getParameter(LootContextParams.ORIGIN));
        List<ItemStack> drops=new ArrayList<>();
        if(actualState(state,world,pos).getValue(GROWTH3)) {
            ItemStack tool=params.getOptionalParameter(LootContextParams.TOOL); int fortune=tool==null?0:EnchantmentHelper.getItemEnchantmentLevel(world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE),tool);
            if(world.random.nextInt(5)<=fortune) drops.add(ThaumcraftApiHelper.makeCrystal(Aspect.FLUX));
            AuraHelper.polluteAura(world,pos,1,true);
        } return drops;
    }
    @Override protected boolean triggerEvent(BlockState state, Level world, BlockPos pos, int id, int parameter) {
        if(id!=1) return super.triggerEvent(state,world,pos,id,parameter);
        if(world.isClientSide) world.playLocalSound(pos,SoundEvents.CHORUS_FLOWER_DEATH,SoundSource.BLOCKS,.1F,.9F+world.random.nextFloat()*.2F,false); return true;
    }
}
