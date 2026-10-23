package thaumcraft.common.blocks.devices;

import com.mojang.serialization.MapCodec;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.common.tiles.devices.TileCondenser;

public final class BlockCondenserLattice extends Block {
    public static final MapCodec<BlockCondenserLattice> CODEC = simpleCodec(BlockCondenserLattice::new);
    public static final BooleanProperty[] CONNECTIONS = {BooleanProperty.create("down"),BooleanProperty.create("up"),BooleanProperty.create("north"),BooleanProperty.create("south"),BooleanProperty.create("west"),BooleanProperty.create("east")};
    public BlockCondenserLattice(Properties properties) { super(properties); BlockState state = stateDefinition.any(); for (var property : CONNECTIONS) state = state.setValue(property,false); registerDefaultState(state); }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(CONNECTIONS); }
    private static boolean connected(BlockGetter level,BlockPos pos,Direction side) { BlockState neighbor = level.getBlockState(pos.relative(side)); return neighbor.getBlock() instanceof BlockCondenserLattice || side == Direction.DOWN && neighbor.is(BlocksTC.condenser); }
    private BlockState connectedState(BlockState state,BlockGetter level,BlockPos pos) { for (Direction side : Direction.values()) state = state.setValue(CONNECTIONS[side.ordinal()],connected(level,pos,side)); return state; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return connectedState(defaultBlockState(),context.getLevel(),context.getClickedPos()); }
    @Override protected BlockState updateShape(BlockState state,Direction side,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos neighborPos) { return connectedState(state,level,pos); }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return Shapes.create(new AABB(connected(level,pos,Direction.WEST) ? 0 : .3125,connected(level,pos,Direction.DOWN) ? 0 : .3125,connected(level,pos,Direction.NORTH) ? 0 : .3125,connected(level,pos,Direction.EAST) ? 1 : .6875,connected(level,pos,Direction.UP) ? 1 : .6875,connected(level,pos,Direction.SOUTH) ? 1 : .6875)); }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving) { if (old.getBlock() != this) triggerUpdate(level,pos); }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block source,BlockPos from,boolean moving) { if (source instanceof BlockCondenserLattice || source == BlocksTC.condenser) triggerUpdate(level,pos); }
    private static final class Walk { final BlockPos pos; int index; Walk(BlockPos pos) { this.pos = pos; } }
    public static void triggerUpdate(Level level,BlockPos origin) {
        if (level.isClientSide || !(level.getBlockState(origin).getBlock() instanceof BlockCondenserLattice)) return;
        Set<BlockPos> visited = new HashSet<>(); visited.add(origin); Deque<Walk> stack = new ArrayDeque<>(); stack.push(new Walk(origin)); BlockPos source = null;
        while (!stack.isEmpty() && source == null) { Walk current = stack.peek(); if (current.index == 6) { stack.pop(); continue; } Direction side = Direction.values()[current.index++]; BlockPos next = current.pos.relative(side); if (!level.hasChunkAt(next) || visited.contains(next)) continue; BlockState state = level.getBlockState(next); if (state.getBlock() instanceof BlockCondenserLattice) { visited.add(next); stack.push(new Walk(next)); } else if (side == Direction.DOWN && state.is(BlocksTC.condenser)) { source = next; if (level.getBlockEntity(next) instanceof TileCondenser tile) tile.latticeCount = -1; } }
        if (source == null || source.distSqr(origin) > 74) level.destroyBlock(origin,true);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (state.is(BlocksTC.condenserlatticeDirty) && stack.is(ItemsTC.filter)) { stack.shrink(1); if (level.random.nextBoolean()) level.addFreshEntity(new ItemEntity(level,pos.getX() + .5 + hit.getDirection().getStepX() / 3.0,pos.getY() + .5,pos.getZ() + .5 + hit.getDirection().getStepZ() / 3.0,ThaumcraftApiHelper.makeCrystal(Aspect.FLUX))); level.playSound(null,pos,SoundEvents.ITEM_PICKUP,SoundSource.BLOCKS,.2F,(1 + (level.random.nextFloat() - level.random.nextFloat()) * .7F) * 1.6F); level.setBlock(pos,BlocksTC.condenserlattice.defaultBlockState(),3); triggerUpdate(level,pos); }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
