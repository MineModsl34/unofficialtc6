package thaumcraft.common.blocks.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidUtil;
import thaumcraft.api.casters.ICaster;
import thaumcraft.common.entities.EntitySpecialItem;
import thaumcraft.common.lib.crafting.CrucibleRules;
import thaumcraft.common.tiles.crafting.CrucibleRegistries;
import thaumcraft.common.tiles.crafting.TileCrucible;

/** Source hollow five-box crucible and item/fluid/emptying interactions. */
public final class BlockCrucible extends BaseEntityBlock {
    public static final MapCodec<BlockCrucible> CODEC = simpleCodec(BlockCrucible::new);
    private static final VoxelShape COLLISION = Shapes.or(box(0, 0, 0, 16, 5, 16),
        box(0, 0, 0, 16, 16, 2), box(0, 0, 14, 16, 16, 16),
        box(14, 0, 0, 16, 16, 16), box(0, 0, 0, 2, 16, 16));
    private int delay; // Source shared Block-instance collision counter, NOT per creature.
    public BlockCrucible(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BlockCrucible> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.block(); }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return COLLISION; }
    @Override protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TileCrucible(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, CrucibleRegistries.CRUCIBLE, TileCrucible::tick);
    }
    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TileCrucible tile) {
            if (entity instanceof ItemEntity item && !(entity instanceof EntitySpecialItem) && tile.boiling()) tile.attemptSmelt(item);
            else {
                delay++;
                if (delay < 10) return;
                delay = 0;
                if (entity instanceof LivingEntity && tile.boiling()) {
                    entity.hurt(level.damageSources().inFire(), 1F);
                    level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, .4F, 2F + level.random.nextFloat() * .4F);
                }
            }
        }
        super.entityInside(state, level, pos, entity);
    }
    private static boolean activate(ItemStack held, Level level, BlockPos pos, Player player, InteractionHand hand, Direction side) {
        if (level.isClientSide) return true;
        if (!(level.getBlockEntity(pos) instanceof TileCrucible tile)) return false;
        var fluid = FluidUtil.getFluidContained(held);
        if (fluid.isPresent() && fluid.get().is(Fluids.WATER) && fluid.get().getAmount() >= 1000) {
            if (tile.tank.getFluidAmount() < tile.tank.getCapacity()) {
                if (FluidUtil.interactWithFluidHandler(player, hand, tile.tank)) {
                    player.containerMenu.broadcastChanges(); tile.changedAndSync();
                    level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, .33F,
                        1F + (level.random.nextFloat() - level.random.nextFloat()) * .3F);
                }
                return true;
            }
        } else if (!player.isShiftKeyDown() && !(held.getItem() instanceof ICaster) && side == Direction.UP) {
            if (tile.boiling() && !held.isEmpty() && tile.attemptSmelt(held.copyWithCount(1), player.getGameProfile().getName()) == null) {
                // Preserve original currentItem debit even if off-hand was used (not a modern free fix).
                player.getInventory().removeItem(player.getInventory().selected, 1); player.containerMenu.broadcastChanges(); return true;
            }
        } else if (held.isEmpty() && player.isShiftKeyDown()) { tile.spillRemnants(); return true; }
        return false;
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
        Player player, InteractionHand hand, BlockHitResult hit) {
        return activate(stack, level, pos, player, hand, hit.getDirection()) ? ItemInteractionResult.sidedSuccess(level.isClientSide)
            : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return activate(player.getMainHandItem(), level, pos, player, InteractionHand.MAIN_HAND, hit.getDirection())
            ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof TileCrucible tile) tile.spillRemnants();
        super.onRemove(state, level, pos, replacement, moving);
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof TileCrucible tile ? CrucibleRules.comparator(tile.aspects.visSize()) : 0;
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(10) == 0 && level.getBlockEntity(pos) instanceof TileCrucible tile && tile.boiling())
            level.playLocalSound(pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS, .1F + random.nextFloat() * .1F, 1.2F + random.nextFloat() * .2F, false);
    }
}
