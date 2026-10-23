package thaumcraft.common.blocks.devices;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.common.tiles.devices.MirrorRegistries;
import thaumcraft.common.tiles.devices.TileMirror;
import thaumcraft.common.tiles.devices.TileMirrorBase;
import thaumcraft.common.tiles.devices.TileMirrorEssentia;

public final class BlockMirror extends BaseEntityBlock {
    public static final MapCodec<BlockMirror> CODEC=simpleCodec(BlockMirror::new);
    public static final DirectionProperty FACING=BlockStateProperties.FACING;
    public BlockMirror(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.DOWN));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> builder){builder.add(FACING);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return state.is(BlocksTC.mirrorEssentia)?new TileMirrorEssentia(pos,state):new TileMirror(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        if(type==MirrorRegistries.ESSENTIA)return (world,pos,block,tile)->((TileMirrorEssentia)tile).update();
        return createTickerHelper(type,MirrorRegistries.MIRROR,(world,pos,block,tile)->tile.update());
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return switch(state.getValue(FACING)){
        case UP->box(0,0,0,16,2,16);case DOWN->box(0,14,0,16,16,16);
        case NORTH->box(0,0,14,16,16,16);case SOUTH->box(0,0,0,16,16,2);
        case WEST->box(14,0,0,16,16,16);case EAST->box(0,0,0,2,16,16);};}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getClickedFace());}
    @Override protected boolean canSurvive(BlockState state,LevelReader world,BlockPos pos){Direction face=state.getValue(FACING);return world.getBlockState(pos.relative(face.getOpposite())).isFaceSturdy(world,pos.relative(face.getOpposite()),face);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,net.minecraft.world.level.block.Block changed,BlockPos origin,boolean moved){if(!canSurvive(state,level,pos))level.destroyBlock(pos,true);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity player,ItemStack stack){
        if(!level.isClientSide && level.getBlockEntity(pos) instanceof TileMirrorBase mirror){mirror.destination=stack.get(TCDataComponents.MIRROR_LINK);if(mirror.destination!=null)mirror.restoreLink();}
    }
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder builder){
        ItemStack result=new ItemStack(this);
        if(builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof TileMirrorBase mirror && mirror.linked && mirror.destination!=null)result.set(TCDataComponents.MIRROR_LINK,mirror.destination);
        return List.of(result);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState replacement,boolean moved){
        if(!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof TileMirrorBase mirror && !level.isClientSide){mirror.invalidateLink();if(mirror instanceof TileMirror items)items.dropQueue();}
        super.onRemove(state,level,pos,replacement,moved);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return InteractionResult.sidedSuccess(level.isClientSide);}
    @Override protected void entityInside(BlockState state,Level level,BlockPos pos,Entity entity){if(!level.isClientSide && entity instanceof ItemEntity item && item.isAlive() && !item.isOnPortalCooldown() && level.getBlockEntity(pos) instanceof TileMirror mirror)mirror.transport(item);}
}
