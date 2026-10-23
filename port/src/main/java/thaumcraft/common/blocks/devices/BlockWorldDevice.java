package thaumcraft.common.blocks.devices;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.ticks.TickPriority;
import net.neoforged.neoforge.capabilities.Capabilities;
import thaumcraft.api.items.IRechargable;
import thaumcraft.common.tiles.devices.*;
import thaumcraft.common.lib.fx.SourceFX;

/** Source world-device shapes, placement, redstone and activation. Shared properties replace old metadata. */
public final class BlockWorldDevice extends BaseEntityBlock {
    public enum Kind { GENERATOR, RECHARGE, LEVITATOR, LAMP_ARCANE, LAMP_GROWTH, LAMP_FERTILITY, URN, CHEST, BRAIN, EAR, EAR_TOGGLE, RELAY, DIOPTRA, SPA, SPRAYER, CONDENSER, CRAFTER }
    public static final MapCodec<BlockWorldDevice> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(Codec.STRING.xmap(name -> Kind.valueOf(name.toUpperCase(java.util.Locale.ROOT)),value -> value.name().toLowerCase(java.util.Locale.ROOT)).fieldOf("kind").forGetter((BlockWorldDevice block) -> block.kind),propertiesCodec()).apply(instance,BlockWorldDevice::new));
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");
    public final Kind kind;
    public BlockWorldDevice(Kind kind,Properties properties) { super(properties); this.kind = kind; registerDefaultState(stateDefinition.any().setValue(FACING,Direction.UP).setValue(ENABLED,true)); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING,ENABLED); }
    @Override protected RenderShape getRenderShape(BlockState state) { return kind == Kind.CHEST ? RenderShape.INVISIBLE : RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return DeviceTile.create(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) { return createTickerHelper(type,DeviceRegistries.DEVICE,(world,pos,block,tile) -> tile.update()); }
    private boolean horizontal() { return kind == Kind.CHEST || kind == Kind.RELAY || kind == Kind.CRAFTER; }
    private boolean lamp() { return kind == Kind.LAMP_ARCANE || kind == Kind.LAMP_GROWTH || kind == Kind.LAMP_FERTILITY; }
    private boolean ear() { return kind == Kind.EAR || kind == Kind.EAR_TOGGLE; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = horizontal() ? context.getHorizontalDirection().getOpposite() : sourcePlacementFacing(context);
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) face = face.getOpposite();
        if (lamp()) face = context.getClickedFace().getOpposite();
        if (ear()) face = context.getClickedFace();
        if (kind == Kind.CRAFTER) face = context.getHorizontalDirection();
        if (kind == Kind.CHEST) face = context.getHorizontalDirection().getOpposite();
        if (kind == Kind.GENERATOR) for (Direction side : Direction.values()) { var energy = context.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,context.getClickedPos().relative(side),side.getOpposite()); if (energy != null && energy.canReceive()) { face = side; break; } }
        return defaultBlockState().setValue(FACING,face).setValue(ENABLED,!lamp() && !ear() && kind != Kind.RELAY);
    }
    private static Direction sourcePlacementFacing(BlockPlaceContext context) { var player = context.getPlayer(); BlockPos pos = context.getClickedPos(); if (player == null) return context.getClickedFace(); if (Math.abs(player.getX() - pos.getX()) < 2 && Math.abs(player.getZ() - pos.getZ()) < 2) { if (player.getEyeY() - pos.getY() > 2) return Direction.UP; if (pos.getY() - player.getEyeY() > 0) return Direction.DOWN; } return player.getDirection().getOpposite(); }
    @Override protected BlockState rotate(BlockState state,Rotation rotation) { return state.setValue(FACING,rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state,Mirror mirror) { return rotate(state,mirror.getRotation(state.getValue(FACING))); }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        if (lamp()) return box(4,2,4,12,14,12);
        if (kind == Kind.URN) return box(3,0,3,13,16,13);
        if (kind == Kind.BRAIN) return box(3,0,3,13,12,13);
        if (kind == Kind.CHEST) return box(1,0,1,15,14,15);
        if (kind == Kind.RELAY) return box(0,0,0,16,2,16);
        if (kind == Kind.GENERATOR) return Shapes.create(rotate(new AABB(.25,0,.25,.75,.875,.75),state.getValue(FACING)));
        if (kind == Kind.LEVITATOR) { Direction face = state.getValue(FACING); return Shapes.create(new AABB(face == Direction.EAST ? .125 : 0,face == Direction.UP ? .125 : 0,face == Direction.SOUTH ? .125 : 0,face == Direction.WEST ? .875 : 1,face == Direction.DOWN ? .875 : 1,face == Direction.NORTH ? .875 : 1)); }
        if (ear()) return Shapes.create(rotate(new AABB(.125,0,.125,.875,.375,.875),state.getValue(FACING)));
        return Shapes.block();
    }
    public static AABB rotate(AABB box,Direction face) {
        if (face == Direction.UP) return box;
        return switch (face) { case DOWN -> new AABB(box.minX,1 - box.maxY,box.minZ,box.maxX,1 - box.minY,box.maxZ); case NORTH -> new AABB(box.minX,box.minZ,1 - box.maxY,box.maxX,box.maxZ,1 - box.minY); case SOUTH -> new AABB(box.minX,box.minZ,box.minY,box.maxX,box.maxZ,box.maxY); case WEST -> new AABB(1 - box.maxY,box.minX,box.minZ,1 - box.minY,box.maxX,box.maxZ); case EAST -> new AABB(box.minY,box.minX,box.minZ,box.maxY,box.maxX,box.maxZ); default -> box; };
    }
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) { if (kind == Kind.RELAY) return level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP); if (ear()) { Direction face = state.getValue(FACING); BlockPos support = pos.relative(face.getOpposite()); return level.getBlockState(support).isFaceSturdy(level,support,face); } return !lamp() || !level.isEmptyBlock(pos.relative(state.getValue(FACING))); }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving) { if (!old.is(this)) updateState(state,level,pos); }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block source,BlockPos from,boolean moving) {
        if (!canSurvive(state,level,pos)) { if (!level.isClientSide) { level.destroyBlock(pos,true); } return; }
        if (ear() && level.getBlockEntity(pos) instanceof TileSignalDevice tile) tile.updateTone();
        updateState(state,level,pos);
    }
    private void updateState(BlockState state,Level level,BlockPos pos) {
        if (level.isClientSide || ear() || kind == Kind.DIOPTRA || kind == Kind.CHEST || kind == Kind.BRAIN || kind == Kind.URN || kind == Kind.RECHARGE) return;
        if (kind == Kind.RELAY) { if (state.getValue(ENABLED) != relayInput(state,level,pos) && !level.getBlockTicks().hasScheduledTick(pos,this)) { Direction output = state.getValue(FACING).getOpposite(); BlockState neighbor = level.getBlockState(pos.relative(output)); boolean diode = neighbor.getBlock() instanceof DiodeBlock && neighbor.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && neighbor.getValue(BlockStateProperties.HORIZONTAL_FACING) != output; level.scheduleTick(pos,this,2,diode ? TickPriority.EXTREMELY_HIGH : state.getValue(ENABLED) ? TickPriority.VERY_HIGH : TickPriority.HIGH); } return; }
        boolean active = !level.hasNeighborSignal(pos);
        if (lamp() && active && kind != Kind.LAMP_ARCANE && level.getBlockEntity(pos) instanceof TileLampDevice tile && tile.charges <= 0) return;
        if (kind == Kind.LAMP_ARCANE && !active && state.getValue(ENABLED) && level.getBlockEntity(pos) instanceof TileLampDevice tile) tile.removeLights();
        if (active != state.getValue(ENABLED)) level.setBlock(pos,state.setValue(ENABLED,active),3);
    }
    public boolean relayInput(BlockState state,Level level,BlockPos pos) { if (!(level.getBlockEntity(pos) instanceof TileSignalDevice tile)) return false; Direction face = state.getValue(FACING); BlockPos input = pos.relative(face); int power = level.getSignal(input,face); BlockState neighbor = level.getBlockState(input); if (power < 15 && neighbor.is(Blocks.REDSTONE_WIRE)) power = Math.max(power,neighbor.getValue(RedStoneWireBlock.POWER)); return power >= tile.in; }
    public void changedRelay(Level level,BlockPos pos) { updateState(level.getBlockState(pos),level,pos); Direction output = level.getBlockState(pos).getValue(FACING).getOpposite(); if (!net.neoforged.neoforge.event.EventHooks.onNeighborNotify(level,pos,level.getBlockState(pos),java.util.EnumSet.of(output),false).isCanceled()) { BlockPos target = pos.relative(output); level.neighborChanged(target,this,pos); level.updateNeighborsAtExceptFromFacing(target,this,output.getOpposite()); } }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) { if (kind != Kind.RELAY) return; boolean desired = relayInput(state,level,pos); if (state.getValue(ENABLED) && !desired) level.setBlock(pos,state.setValue(ENABLED,false),3); else if (!state.getValue(ENABLED)) { level.setBlock(pos,state.setValue(ENABLED,true),3); if (!desired) level.scheduleTick(pos,this,2,TickPriority.HIGH); } changedRelay(level,pos); }
    @Override protected boolean isSignalSource(BlockState state) { return ear() || kind == Kind.RELAY; }
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side) { if (!state.getValue(ENABLED)) return 0; if (ear()) return 15; return kind == Kind.RELAY && side == state.getValue(FACING) && level.getBlockEntity(pos) instanceof TileSignalDevice tile ? tile.out : 0; }
    @Override protected int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side) { return getSignal(state,level,pos,side); }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return kind == Kind.CHEST || kind == Kind.BRAIN || kind == Kind.DIOPTRA; }
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos) { if (level.getBlockEntity(pos) instanceof TileStorageDevice tile) return kind == Kind.BRAIN ? (int)(tile.xp / 2000F * 14) + (tile.xp > 0 ? 1 : 0) : AbstractContainerMenu.getRedstoneSignalFromContainer(tile); if (level.getBlockEntity(pos) instanceof TileSignalDevice tile && kind == Kind.DIOPTRA) return (int)(tile.grid_amt[84] / 64F * 14) + (tile.grid_amt[84] > 0 ? 1 : 0); return 0; }
    @Override public float getEnchantPowerBonus(BlockState state,LevelReader level,BlockPos pos) { return kind == Kind.BRAIN ? 5 : 0; }
    @Override protected boolean triggerEvent(BlockState state,Level level,BlockPos pos,int id,int param) { BlockEntity tile = level.getBlockEntity(pos); return tile != null && tile.triggerEvent(id,param); }
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,RandomSource random){
        if(kind==Kind.BRAIN&&level.getBlockEntity(pos) instanceof TileStorageDevice tile&&tile.xp>=2000){
            float red=.2F+random.nextFloat()*.2F,blue=.3F+random.nextFloat()*.2F;
            SourceFX.local(level,"block_spark",new Vec3(pos.getX()+.5F,pos.getY()+.8F,pos.getZ()+.5F),new Vec3(1,blue,.5F),Float.floatToRawIntBits(red),Float.floatToRawIntBits(3F));
        }
    }
    @Override protected void entityInside(BlockState state,Level level,BlockPos pos,Entity entity) { if (kind == Kind.CHEST && entity instanceof ItemEntity item && level.getBlockEntity(pos) instanceof TileStorageDevice tile) tile.swallow(item); }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) { if (!next.is(this)) { if (level.getBlockEntity(pos) instanceof DeviceTile tile) { if (kind == Kind.LAMP_ARCANE) ((TileLampDevice)tile).removeLights(); if (!level.isClientSide && tile instanceof thaumcraft.api.aspects.IEssentiaTransport transport && transport.getEssentiaAmount(Direction.UP) > 0) thaumcraft.api.aura.AuraHelper.polluteAura(level,pos,transport.getEssentiaAmount(Direction.UP),true); Containers.dropContents(level,pos,tile); level.updateNeighbourForOutputSignal(pos,this); } if (ear() || kind == Kind.RELAY) level.updateNeighborsAt(pos,this); } super.onRemove(state,level,pos,next,moving); }
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder builder) { if (kind == Kind.BRAIN) { ItemStack stack = new ItemStack(this); if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof TileStorageDevice tile && tile.xp > 0) BlockJarBrainItem.setXp(stack,tile.xp); return List.of(stack); } return super.getDrops(state,builder); }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack) { if (level.getBlockEntity(pos) instanceof TileStorageDevice tile && kind == Kind.BRAIN) { tile.xp = BlockJarBrainItem.getXp(stack); tile.changedAndSync(); } if (ear() && level.getBlockEntity(pos) instanceof TileSignalDevice tile) tile.updateTone(); if (kind == Kind.RELAY && relayInput(state,level,pos)) level.scheduleTick(pos,this,1); }
    private boolean activate(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DeviceTile tile)) return false;
        if (kind == Kind.LEVITATOR || kind == Kind.RELAY || kind == Kind.CRAFTER) { int button = button(player,pos,state); if (button < 0) return kind == Kind.RELAY; if (!level.isClientSide && player.getAbilities().mayBuild) { if (tile instanceof TileEnergyDevice energy) energy.increaseRange(player); else if (tile instanceof TileSignalDevice signal) { if (button == 0) signal.out = signal.out % 15 + 1; else signal.in = signal.in % 15 + 1; signal.changedAndSync(); changedRelay(level,pos); } else ((TilePatternCrafter)tile).cycle(); level.playSound(null,pos,thaumcraft.common.lib.SoundsTC.key,SoundSource.BLOCKS,.5F,1); } return true; }
        if (ear()) { if (!level.isClientSide) ((TileSignalDevice)tile).tune(); return true; }
        if (kind == Kind.DIOPTRA) { if (!level.isClientSide) tile.enabled(!tile.enabled()); return true; }
        if (kind == Kind.BRAIN) { ((TileStorageDevice)tile).releaseXp(); if (level.isClientSide) level.playLocalSound(pos,thaumcraft.common.lib.SoundsTC.jar,SoundSource.BLOCKS,.2F,1,false); return true; }
        if (kind == Kind.RECHARGE) { if (!level.isClientSide) { float pitch = 1.6F; if (tile.isEmpty() && player.getMainHandItem().getItem() instanceof IRechargable && !player.getItemInHand(hand).isEmpty()) { tile.setItem(0,player.getItemInHand(hand).copyWithCount(1)); player.getItemInHand(hand).shrink(1); } else if (!tile.isEmpty()) { ItemStack stack = tile.removeItem(0,tile.getItem(0).getCount()); level.addFreshEntity(new ItemEntity(level,player.getX(),player.getY() + player.getEyeHeight() / 2,player.getZ(),stack)); pitch = 1.5F; } else return true; player.inventoryMenu.broadcastChanges(); level.playSound(null,pos,SoundEvents.ITEM_PICKUP,SoundSource.BLOCKS,.2F,(1 + (level.random.nextFloat() - level.random.nextFloat()) * .7F) * pitch); } return true; }
        if (kind == Kind.CHEST) { if (!level.isClientSide) player.openMenu(new SimpleMenuProvider((id,inventory,owner) -> ChestMenu.threeRows(id,inventory,tile),net.minecraft.network.chat.Component.translatable("container.chest"))); return true; }
        if (tile instanceof TileFluidDevice fluid) return fluid.activate(player,hand);
        return false;
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) { return activate(state,level,pos,player,hand,hit) ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) { return activate(state,level,pos,player,InteractionHand.MAIN_HAND,hit) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS; }
    private int button(Player player,BlockPos pos,BlockState state) {
        Direction face = state.getValue(FACING); AABB[] buttons;
        if (kind == Kind.LEVITATOR) buttons = new AABB[]{rotate(new AABB(.375,.0625,.375,.625,.125,.625),face)};
        else if (kind == Kind.CRAFTER) buttons = new AABB[]{switch (face) { case EAST -> new AABB(.125,.125,.375,.25,.375,.625); case NORTH -> new AABB(.375,.125,.75,.625,.375,.875); case SOUTH -> new AABB(.375,.125,.125,.625,.375,.25); default -> new AABB(.75,.125,.375,.875,.375,.625); }};
        else { buttons = new AABB[]{new AABB(.125,.0625,.125,.375,.25,.375),new AABB(.375,.0625,.625,.625,.25,.875)}; int turns = switch (face) { case WEST -> 1; case NORTH -> 2; case EAST -> 3; default -> 0; }; for (int i = 0; i < buttons.length; i++) for (int turn = 0; turn < turns; turn++) { AABB box = buttons[i]; buttons[i] = new AABB(1 - box.maxZ,box.minY,box.minX,1 - box.minZ,box.maxY,box.maxX); } }
        Vec3 start = player.getEyePosition(), end = start.add(player.getViewVector(1).scale(player.blockInteractionRange())); double nearest = Double.MAX_VALUE; int index = -1;
        for (int i = 0; i < buttons.length; i++) { var point = buttons[i].move(pos).clip(start,end); if (point.isPresent() && point.get().distanceToSqr(start) < nearest) { nearest = point.get().distanceToSqr(start); index = i; } }
        return index;
    }
}
