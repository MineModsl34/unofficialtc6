package thaumcraft.common.blocks.misc;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.common.tiles.crafting.CrucibleRegistries;
import thaumcraft.common.tiles.misc.TileNitor;

/** Real source luminous, non-colliding nitor; its model is intentionally invisible. */
public final class BlockNitor extends BaseEntityBlock {
    public static final MapCodec<BlockNitor> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        DyeColor.CODEC.fieldOf("dye").forGetter(block -> block.dye), propertiesCodec()).apply(instance, BlockNitor::new));
    public final DyeColor dye;
    public BlockNitor(DyeColor dye, Properties properties) { super(properties); this.dye = dye; }
    @Override protected MapCodec<? extends BlockNitor> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.box(.33F, .33F, .33F, .66F, .66F, .66F); }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TileNitor(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, CrucibleRegistries.NITOR, TileNitor::tick) : null;
    }
}
