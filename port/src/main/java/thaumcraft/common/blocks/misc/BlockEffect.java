package thaumcraft.common.blocks.misc;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Source persistent glimmer only. Shock/sap are later focus effects, not fabricated here. */
public final class BlockEffect extends Block {
    public static final MapCodec<BlockEffect> CODEC = simpleCodec(BlockEffect::new);
    public BlockEffect(Properties properties) { super(properties.air()); }
    @Override protected MapCodec<? extends BlockEffect> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) { return ItemStack.EMPTY; }
}
