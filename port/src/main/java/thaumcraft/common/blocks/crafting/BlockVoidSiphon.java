package thaumcraft.common.blocks.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import thaumcraft.common.tiles.crafting.*;

/** Source enabled/redstone state and three distinct collision boxes, not a full cube. */
public final class BlockVoidSiphon extends BaseEntityBlock {
    public static final MapCodec<BlockVoidSiphon> CODEC=simpleCodec(BlockVoidSiphon::new);
    public static final BooleanProperty ENABLED=BooleanProperty.create("enabled");
    private static final VoxelShape SHAPE=Block.box(3,0,3,13,16,13);
    private static final VoxelShape COLLISION=Shapes.or(Block.box(3,0,3,13,2,13),Block.box(4,2,4,12,11,12),Block.box(5,12,5,10,16,10));
    public BlockVoidSiphon(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(ENABLED,true));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(ENABLED);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return COLLISION;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new TileVoidSiphon(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,Phase10RiftRegistries.VOID_SIPHON,TileVoidSiphon::tick);}
    private void updateState(BlockState state,Level level,BlockPos pos){if(!level.isClientSide){boolean enabled=!level.hasNeighborSignal(pos);if(enabled!=state.getValue(ENABLED))level.setBlock(pos,state.setValue(ENABLED,enabled),3);}}
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){if(!old.is(this))updateState(state,level,pos);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block source,BlockPos from,boolean moving){updateState(state,level,pos);}
    private static void open(Level level,BlockPos pos,Player player){if(!level.isClientSide&&level.getBlockEntity(pos) instanceof TileVoidSiphon tile)player.openMenu(tile);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){open(level,pos,player);return ItemInteractionResult.sidedSuccess(level.isClientSide);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){open(level,pos,player);return InteractionResult.sidedSuccess(level.isClientSide);}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(!state.is(next.getBlock()))Containers.dropContentsOnDestroy(state,next,level,pos);super.onRemove(state,level,pos,next,moving);}
}
