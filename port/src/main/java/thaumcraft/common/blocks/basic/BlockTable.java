package thaumcraft.common.blocks.basic;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.event.EventHooks;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.IScribeTools;
import thaumcraft.common.tiles.crafting.TileResearchTable;

/** Table. In TC6 the shape is a full block, but only the top counted as a support face; the model is a tabletop and 4 legs. */
public class BlockTable extends Block {
    public static final MapCodec<BlockTable> CODEC = simpleCodec(BlockTable::new);
    private static final VoxelShape SUPPORT = box(0, 12, 0, 16, 16, 16);

    public BlockTable(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SUPPORT;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        // Source consumes every table activation, even stone/non-tool/client-side clicks.
        if (!level.isClientSide && this == BlocksTC.tableWood
            && player.getItemInHand(hand).getItem() instanceof IScribeTools) {
            BlockState research = BlocksTC.researchTable.defaultBlockState()
                .setValue(BlockResearchTable.FACING, BlockResearchTable.legacyFacing(player.getYRot()));
            if (level.setBlock(pos, research, Block.UPDATE_ALL)) {
                if (!(level.getBlockEntity(pos) instanceof TileResearchTable table)) {
                    throw new IllegalStateException("Research-table conversion did not create its registered inventory");
                }
                // Full held stack copy, then empty the entire chosen hand, even creative.
                // NOT shrink(1), no paper/research requirement and no tool/ink damage.
                table.setItem(0, player.getItemInHand(hand).copy());
                player.setItemInHand(hand, ItemStack.EMPTY);
                player.getInventory().setChanged();
                table.setChanged();
                level.sendBlockUpdated(pos, research, research, Block.UPDATE_ALL);
                EventHooks.firePlayerCraftingEvent(player, new ItemStack(BlocksTC.researchTable), new SimpleContainer(1));
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
