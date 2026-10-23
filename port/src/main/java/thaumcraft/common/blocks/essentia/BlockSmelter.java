package thaumcraft.common.blocks.essentia;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.common.tiles.essentia.EssentiaRegistries;
import thaumcraft.common.tiles.essentia.TileSmelter;

public class BlockSmelter extends BaseEntityBlock {
    public static final MapCodec<BlockSmelter> CODEC = simpleCodec(BlockSmelter::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");
    public BlockSmelter(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(ENABLED,false)); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING,ENABLED); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite()).setValue(ENABLED,false); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new TileSmelter(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) { return createTickerHelper(type,EssentiaRegistries.SMELTER,(world,pos,block,tile) -> tile.update()); }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving) { if (level.getBlockEntity(pos) instanceof TileSmelter smelter) smelter.checkNeighbours(); }
    public static void setFurnaceState(Level level,BlockPos pos,boolean enabled) { BlockState state = level.getBlockState(pos); if (state.hasProperty(ENABLED) && state.getValue(ENABLED) != enabled) level.setBlock(pos,state.setValue(ENABLED,enabled),3); }
    private static void open(Level level,BlockPos pos,Player player) { if (!level.isClientSide && !player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof TileSmelter smelter) player.openMenu(smelter); }
    @Override protected ItemInteractionResult useItemOn(ItemStack held,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) { open(level,pos,player); return ItemInteractionResult.sidedSuccess(level.isClientSide); }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) { open(level,pos,player); return InteractionResult.sidedSuccess(level.isClientSide); }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos) { return net.minecraft.world.inventory.AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos)); }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if (!state.is(next.getBlock())) { if (!level.isClientSide && level.getBlockEntity(pos) instanceof TileSmelter smelter && smelter.vis > 0) AuraHelper.polluteAura(level,pos,smelter.vis,true); net.minecraft.world.Containers.dropContentsOnDestroy(state,next,level,pos); }
        super.onRemove(state,level,pos,next,moving);
    }
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,net.minecraft.util.RandomSource random) {
        if (!state.getValue(ENABLED)) return;
        Direction face = state.getValue(FACING); double offset = random.nextFloat() * .5 - .25;
        double x = pos.getX() + .5 + face.getStepX() * .52 + (face.getAxis() == Direction.Axis.Z ? offset : 0);
        double z = pos.getZ() + .5 + face.getStepZ() * .52 + (face.getAxis() == Direction.Axis.X ? offset : 0);
        double y = pos.getY() + .2 + random.nextFloat() * 5 / 16;
        level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,x,y,z,0,0,0); level.addParticle(net.minecraft.core.particles.ParticleTypes.FLAME,x,y,z,0,0,0);
    }
}
