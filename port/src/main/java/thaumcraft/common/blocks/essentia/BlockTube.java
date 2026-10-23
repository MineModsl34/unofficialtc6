package thaumcraft.common.blocks.essentia;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.casters.ICaster;
import thaumcraft.common.items.tools.ItemResonator;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.tiles.essentia.*;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.lib.fx.SourceFX;
import net.minecraft.world.phys.Vec3;
import thaumcraft.common.lib.network.fx.PacketSourceFX;

public class BlockTube extends BaseEntityBlock {
    public enum Kind { TUBE, VALVE, RESTRICT, ONEWAY, FILTER, BUFFER }
    public static final MapCodec<BlockTube> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        propertiesCodec(), Codec.STRING.fieldOf("variant").forGetter(block -> block.kind.name())).apply(instance, (properties, variant) -> new BlockTube(properties, Kind.valueOf(variant))));
    public static final BooleanProperty DOWN = BooleanProperty.create("down"), UP = BooleanProperty.create("up"), NORTH = BooleanProperty.create("north"),
        SOUTH = BooleanProperty.create("south"), WEST = BooleanProperty.create("west"), EAST = BooleanProperty.create("east");
    private static final BooleanProperty[] SIDES = {DOWN, UP, NORTH, SOUTH, WEST, EAST};
    public final Kind kind;
    public BlockTube(Properties properties, Kind kind) { super(properties); this.kind = kind; BlockState state = stateDefinition.any(); for (var side : SIDES) state = state.setValue(side, false); registerDefaultState(state); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(SIDES); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return switch (kind) {
        case TUBE -> new TileTube(pos, state); case VALVE -> new TileTubeValve(pos, state); case RESTRICT -> new TileTubeRestrict(pos, state);
        case ONEWAY -> new TileTubeOneway(pos, state); case FILTER -> new TileTubeFilter(pos, state); case BUFFER -> new TileTubeBuffer(pos, state);
    }; }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return (world, pos, current, tile) -> { if (tile instanceof TransportTile transport) transport.update(); }; }
    /** Source getActualState converted to real synchronized connection state. */
    public void refreshConnections(Level level, BlockPos pos) {
        BlockState old = level.getBlockState(pos); if (old.getBlock() != this) return;
        BlockState state = old;
        if (TransportConnections.tile(level, pos) instanceof TileTube tile) for (Direction side : Direction.values()) state = state.setValue(SIDES[side.get3DDataValue()], tile.isConnectable(side) && TransportConnections.connectable(level, pos, side) != null);
        if (state != old) level.setBlock(pos, state, 2);
    }
    @Override protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (world instanceof Level level && TransportConnections.tile(level, pos) instanceof TileTube tile)
            return state.setValue(SIDES[side.get3DDataValue()], tile.isConnectable(side) && TransportConnections.connectable(level, pos, side) != null);
        return state;
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moved) { refreshConnections(level, pos); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof TileTube tube) { tube.facing = Direction.from3DDataValue(TransportRules.placementFacing(placer.getX(), placer.getEyeY(), placer.getZ(), pos.getX(), pos.getY(), pos.getZ(), placer.getDirection().getOpposite().get3DDataValue())); tube.markDirty(); tube.syncTile(true); }
        super.setPlacedBy(level, pos, state, placer, stack);
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        VoxelShape shape = box(5, 5, 5, 11, 11, 11);
        if (TransportConnections.tile(world, pos) instanceof TileTube tube) {
            shape = Shapes.empty(); for (var cuboid : tube.traceableCuboids()) shape = Shapes.or(shape, Shapes.create(cuboid.box().move(-pos.getX(), -pos.getY(), -pos.getZ())));
        }
        return shape;
    }
    @Override protected VoxelShape getOcclusionShape(BlockState state, BlockGetter world, BlockPos pos) { return Shapes.empty(); }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) { return level.getBlockEntity(pos) instanceof TileTubeBuffer buffer ? TransportRules.comparator(buffer.aspects.visSize()) : 0; }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (state.getBlock() != replacement.getBlock() && level.getBlockEntity(pos) instanceof TileTube tube && tube.getEssentiaAmount(Direction.UP) > 0) {
            if(!level.isClientSide){
                AuraHelper.polluteAura(level, pos, tube.getEssentiaAmount(Direction.UP), true);
                // Native LevelChunk does not invoke onRemove on the client. Describe only this
                // source presentation event S2C; all sound/particle RNG remains client-side.
                SourceFX.emit(level,SourceFX.event(PacketSourceFX.Kind.LOCAL,Vec3.atLowerCornerOf(pos),Vec3.ZERO,Aspect.FLUX.getColor(),0,0,-1,"tube_break"));
            }
        }
        super.onRemove(state, level, pos, replacement, moved);
    }
    private boolean activate(ItemStack held, Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof TileTubeValve valve) {
            if (held.getItem() instanceof ICaster || held.getItem() instanceof ItemResonator || held.getItem() == asItem()) return false;
            if (!level.isClientSide) { valve.allowFlow = !valve.allowFlow; valve.markDirty(); valve.syncTile(true); level.playSound(null, pos, SoundsTC.squeek, SoundSource.BLOCKS, .7F, .9F + level.random.nextFloat() * .2F); }
            return true;
        }
        if (level.getBlockEntity(pos) instanceof TileTubeFilter filter) {
            if (player.isShiftKeyDown() && filter.aspectFilter != null) { if (!level.isClientSide) { filter.aspectFilter = null; filter.changedAndSync(); level.playSound(null, pos, SoundsTC.key, SoundSource.BLOCKS, 1, 1); } return true; }
            if (filter.aspectFilter == null && held.getItem() instanceof IEssentiaContainerItem container) {
                var aspects = container.getAspects(held);
                if (aspects != null && aspects.size() > 0 && !level.isClientSide) { filter.aspectFilter = aspects.getAspects()[0]; filter.changedAndSync(); level.playSound(null, pos, SoundsTC.key, SoundSource.BLOCKS, 1, 1); }
                return true;
            }
        }
        return false;
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) { return activate(held, level, pos, player) ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) { return activate(ItemStack.EMPTY, level, pos, player) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS; }
}
