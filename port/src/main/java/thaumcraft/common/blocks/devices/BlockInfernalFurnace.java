package thaumcraft.common.blocks.devices;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.tiles.devices.ProcessingRegistries;
import thaumcraft.common.tiles.devices.TileInfernalFurnace;

public final class BlockInfernalFurnace extends BaseEntityBlock {
    public static final MapCodec<BlockInfernalFurnace> CODEC = simpleCodec(BlockInfernalFurnace::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Source recursion guard is a server-thread operation, not per-tile state. */
    public static boolean ignore;
    public BlockInfernalFurnace(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH)); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite()); }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return box(0,0,0,16,8,16); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new TileInfernalFurnace(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) { return createTickerHelper(type,ProcessingRegistries.INFERNAL_FURNACE,TileInfernalFurnace::tick); }
    public static void destroyFurnace(Level level,BlockPos pos,BlockState state,BlockPos broken) {
        if (ignore || level.isClientSide) return;
        ignore = true;
        try {
            for (BlockPos neighbor : BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,1,1))) if (!neighbor.equals(broken)) {
                BlockState part = level.getBlockState(neighbor);
                if (part.is(BlocksTC.placeholderNetherbrick)) level.setBlock(neighbor,Blocks.NETHER_BRICKS.defaultBlockState(),3);
                if (part.is(BlocksTC.placeholderObsidian)) level.setBlock(neighbor,Blocks.OBSIDIAN.defaultBlockState(),3);
            }
            BlockPos mouth = pos.relative(state.getValue(FACING).getOpposite());
            if (level.isEmptyBlock(mouth)) level.setBlock(mouth,Blocks.IRON_BARS.defaultBlockState(),3);
            if (!pos.equals(broken)) level.setBlock(pos,Blocks.LAVA.defaultBlockState(),3);
            // The core's onRemove queues restoration after LevelChunk commits its replacement.
        } finally { ignore = false; }
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if (!state.is(next.getBlock())) {
            net.minecraft.world.Containers.dropContentsOnDestroy(state,next,level,pos);
            // Core destruction is completed by a scoped server helper, including returning lava.
            if (!ignore && !level.isClientSide) { destroyFurnace(level,pos,state,pos); ProcessingRegistries.restoreLava(level,pos); }
        }
        super.onRemove(state,level,pos,next,moving);
    }
    @Override protected void entityInside(BlockState state,Level level,BlockPos pos,Entity entity) {
        var motion = entity.getDeltaMovement();
        double x = entity.getX() < pos.getX() + .3 ? .0001 : entity.getX() > pos.getX() + .7 ? -.0001 : 0;
        double z = entity.getZ() < pos.getZ() + .3 ? .0001 : entity.getZ() > pos.getZ() + .7 ? -.0001 : 0;
        entity.setDeltaMovement(motion.x + x,motion.y,motion.z + z);
        if (!level.isClientSide && entity.tickCount % 10 == 0) {
            if (entity instanceof ItemEntity item && level.getBlockEntity(pos) instanceof TileInfernalFurnace furnace) {
                item.setDeltaMovement(item.getDeltaMovement().x,.025,item.getDeltaMovement().z);
                if (item.onGround()) { ItemStackResult.set(item,furnace.addItemsToInventory(item.getItem())); }
            } else if (entity instanceof LivingEntity && !entity.fireImmune()) { entity.hurt(level.damageSources().lava(),3); entity.igniteForSeconds(10); }
        }
    }
    private static final class ItemStackResult {
        static void set(ItemEntity entity,net.minecraft.world.item.ItemStack stack) { if (stack.isEmpty()) entity.discard(); else entity.setItem(stack); }
    }
}
