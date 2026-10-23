package thaumcraft.common.blocks.misc;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.blocks.BlocksTC;

/** Invisible, replaceable source barrier: players and player-ridden mobs can cross it. */
public final class BlockBarrier extends Block {
    public static final MapCodec<BlockBarrier> CODEC = simpleCodec(BlockBarrier::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public BlockBarrier(Properties properties) {
        super(properties.dynamicShape().replaceable().noOcclusion().noLootTable());
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(POWERED); }
    @Override protected MapCodec<? extends BlockBarrier> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entities && entities.getEntity() instanceof LivingEntity entity
                && !(entity instanceof Player) && entity.countPlayerPassengers() == 0
                && level instanceof Level world && !powered(world, pos)) return Shapes.block();
        return Shapes.empty();
    }
    public static boolean powered(Level level, BlockPos pos) {
        BlockPos paving = level.getBlockState(pos.below()).is(BlocksTC.pavingStoneBarrier) ? pos.below() : pos.below(2);
        return level.hasNeighborSignal(paving);
    }
    @Override public PathType getBlockPathType(BlockState state, BlockGetter level, BlockPos pos,
            net.minecraft.world.entity.Mob mob) {
        for (int height = 1; height < 3; height++) {
            if (level.getBlockEntity(pos.below(height)) instanceof thaumcraft.common.tiles.misc.TileBarrierStone tile)
                return state.getValue(POWERED) ? PathType.OPEN : PathType.BLOCKED;
        }
        return PathType.OPEN;
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos other, boolean moving) {
        if (!level.getBlockState(pos.below()).is(BlocksTC.pavingStoneBarrier)
                && !level.getBlockState(pos.below()).is(this)) level.removeBlock(pos, false);
        else if (state.getValue(POWERED) != powered(level,pos)) level.setBlock(pos,state.setValue(POWERED,powered(level,pos)),3);
    }
    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) { return ItemStack.EMPTY; }
}
