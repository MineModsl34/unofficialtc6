package thaumcraft.common.blocks.essentia;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BlockSmelterVent extends BlockSmelterAux {
    public static final MapCodec<BlockSmelterVent> CODEC = simpleCodec(BlockSmelterVent::new);
    public BlockSmelterVent(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return switch(state.getValue(FACING)) {
            case SOUTH -> box(2,2,8,14,14,16); case WEST -> box(0,2,2,8,14,14);
            case EAST -> box(8,2,2,16,14,14); default -> box(2,2,0,14,14,8);
        };
    }
}
