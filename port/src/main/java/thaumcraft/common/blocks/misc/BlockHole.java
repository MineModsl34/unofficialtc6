package thaumcraft.common.blocks.misc;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import thaumcraft.common.items.casters.CasterRegistries;
import thaumcraft.common.tiles.misc.TileHole;
/** Source temporary, luminous, unbreakable but collision-free passage. No player-obtainable item. */
public final class BlockHole extends BaseEntityBlock {
    public static final MapCodec<BlockHole> CODEC=simpleCodec(BlockHole::new);
    public BlockHole(Properties properties){super(properties);}
    @Override protected MapCodec<? extends BlockHole> codec(){return CODEC;}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return Shapes.empty();}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return Shapes.empty();}
    @Override protected VoxelShape getBlockSupportShape(BlockState state,BlockGetter world,BlockPos pos){return Shapes.block();}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new TileHole(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,CasterRegistries.HOLE,TileHole::tick);}
    @Override public net.minecraft.world.item.ItemStack getCloneItemStack(BlockState state,net.minecraft.world.phys.HitResult hit,LevelReader level,BlockPos pos,net.minecraft.world.entity.player.Player player){return net.minecraft.world.item.ItemStack.EMPTY;}
}
