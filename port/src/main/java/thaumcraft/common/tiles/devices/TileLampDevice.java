package thaumcraft.common.tiles.devices;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import thaumcraft.api.aspects.*;
import thaumcraft.api.blocks.BlocksTC;

/** Three original lamp algorithms. Growth schedules ordinary plant ticks; it does not apply bonemeal. */
public final class TileLampDevice extends DeviceTile implements IEssentiaTransport {
    public static final Set<Block> lampBlacklist = new HashSet<>();
    public int charges;
    public boolean reserve;
    private int drawDelay;
    private final List<BlockPos> columns = new ArrayList<>();
    private BlockPos lastPlant;
    private BlockState lastPlantState;
    public TileLampDevice(BlockPos pos,BlockState state) { super(pos,state,0); charges = kind() == thaumcraft.common.blocks.devices.BlockWorldDevice.Kind.LAMP_GROWTH ? -1 : 0; }
    @Override public void update() {
        if (level == null || level.isClientSide) return;
        switch (kind()) {
            case LAMP_ARCANE -> arcane();
            case LAMP_GROWTH -> growth();
            case LAMP_FERTILITY -> fertility();
            default -> {}
        }
    }
    private boolean drawEssentia() {
        if (++drawDelay % 5 != 0) return false;
        Direction side = facing(); Aspect aspect = kind() == thaumcraft.common.blocks.devices.BlockWorldDevice.Kind.LAMP_GROWTH ? Aspect.PLANT : Aspect.DESIRE;
        IEssentiaTransport transport = thaumcraft.common.tiles.essentia.TransportConnections.connectable(level,worldPosition,side);
        if (transport != null && transport.canOutputTo(side.getOpposite()) && transport.getSuctionAmount(side.getOpposite()) < getSuctionAmount(side)) return transport.takeEssentia(aspect,1,side.getOpposite()) == 1;
        return false;
    }
    private void growth() {
        if (charges <= 0) {
            if (reserve) { charges = 20; reserve = false; changedAndSync(); }
            else if (drawEssentia()) { charges = 20; changedAndSync(); }
            if (charges <= 0) enabled(false); else if (!level.hasNeighborSignal(worldPosition) && !enabled()) enabled(true);
        }
        if (!reserve && drawEssentia()) reserve = true;
        if (charges == 0) { charges = -1; changedAndSync(); }
        if (!level.hasNeighborSignal(worldPosition) && charges > 0) updatePlant();
    }
    private void updatePlant() {
        if (lastPlant != null && !level.getBlockState(lastPlant).equals(lastPlantState)) {
            if(level instanceof ServerLevel&&level.getNearestPlayer(lastPlant.getX(),lastPlant.getY(),lastPlant.getZ(),32,false)!=null)thaumcraft.common.lib.fx.SourceFX.emit(level,thaumcraft.common.lib.fx.SourceFX.event(thaumcraft.common.lib.network.fx.PacketSourceFX.Kind.MIST,net.minecraft.world.phys.Vec3.atLowerCornerOf(lastPlant),net.minecraft.world.phys.Vec3.ZERO,4259648,0,0,-1,""));
            lastPlantState = level.getBlockState(lastPlant);
        }
        if (columns.isEmpty()) { for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) columns.add(worldPosition.offset(x,6,z)); for (int i = columns.size() - 1; i > 0; i--) Collections.swap(columns,i,level.random.nextInt(i + 1)); }
        BlockPos top = columns.remove(0);
        for (int dy = 0; dy <= 12; dy++) {
            BlockPos pos = top.below(dy); BlockState state = level.getBlockState(pos);
            if (pos.distSqr(worldPosition) >= 36 || !isPlant(state) || thaumcraft.common.lib.utils.CropUtils.isGrownCrop(level,pos) || lampBlacklist.contains(state.getBlock()) || !thaumcraft.common.lib.utils.CropUtils.doesLampGrow(level,pos)) continue;
            charges--; lastPlant = pos; lastPlantState = state; DeviceEvents.schedulePlant((ServerLevel)level,pos); setChanged(); return;
        }
    }
    private static boolean isPlant(BlockState state) { return !state.is(Blocks.GRASS_BLOCK) && (state.getBlock() instanceof BonemealableBlock || state.getBlock() instanceof BushBlock || state.is(Blocks.CACTUS) || state.is(Blocks.SUGAR_CANE) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)); }
    private boolean grown(BlockState state,BlockPos pos) {
        if (state.getBlock() instanceof CropBlock crop) return crop.isMaxAge(state);
        // Source stems deliberately remain eligible; stacked crops are grown only with the same crop below.
        if (state.getBlock() instanceof StemBlock) return false;
        if (state.is(Blocks.CACTUS) || state.is(Blocks.SUGAR_CANE)) return level.getBlockState(pos.below()).is(state.getBlock());
        return state.getBlock() instanceof BonemealableBlock plant && !plant.isValidBonemealTarget(level,pos,state);
    }
    private void fertility() {
        if (charges < 10) { if (drawEssentia()) { charges++; changedAndSync(); } if (charges <= 1) enabled(false); else if (!level.hasNeighborSignal(worldPosition)) enabled(true); }
        if (level.hasNeighborSignal(worldPosition) || charges <= 1 || counter++ % 300 != 0) return;
        List<Animal> animals = level.getEntitiesOfClass(Animal.class,new AABB(worldPosition).inflate(7));
        for (Animal animal : animals) {
            if (animal.getAge() != 0 || animal.isInLove()) continue;
            List<Animal> matching = animals.stream().filter(other -> other.getClass() == animal.getClass()).toList();
            if (matching.size() > 9) continue;
            List<Animal> adults = matching.stream().filter(other -> other.getAge() == 0 && !other.isInLove()).limit(2).toList();
            if (adults.size() == 2) { charges -= 5; adults.forEach(other -> other.setInLove(null)); changedAndSync(); return; }
        }
    }
    private void arcane() {
        if (level.getGameTime() % 5 != 0 || level.hasNeighborSignal(worldPosition)) return;
        BlockPos pos = worldPosition.offset(level.random.nextInt(16) - level.random.nextInt(16),level.random.nextInt(16) - level.random.nextInt(16),level.random.nextInt(16) - level.random.nextInt(16));
        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING,pos.getX(),pos.getZ());
        pos = new BlockPos(pos.getX(),Math.max(5,Math.min(pos.getY(),height + 4)),pos.getZ());
        if (!level.isEmptyBlock(pos) || level.getBrightness(LightLayer.BLOCK,pos) >= 11) return;
        Vec3 start = worldPosition.getCenter(), end = pos.getCenter();
        // Source ray trace skips the emitter block, rather than treating its own collision as an obstruction.
        Vec3 direction = end.subtract(start); double boundary = .501 / Math.max(Math.max(Math.abs(direction.x),Math.abs(direction.y)),Math.abs(direction.z));
        HitResult hit = level.clip(new ClipContext(start.add(direction.scale(boundary)),end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS || hit instanceof BlockHitResult block && block.getBlockPos().equals(pos)) level.setBlock(pos,BlocksTC.effectGlimmer.defaultBlockState(),3);
    }
    public void removeLights() { if (level != null && !level.isClientSide) for (int x = -15; x <= 15; x++) for (int y = -15; y <= 15; y++) for (int z = -15; z <= 15; z++) { BlockPos pos = worldPosition.offset(x,y,z); if (level.hasChunkAt(pos) && level.getBlockState(pos).is(BlocksTC.effectGlimmer)) level.removeBlock(pos,false); } }
    @Override public boolean isConnectable(Direction side) { return kind() != thaumcraft.common.blocks.devices.BlockWorldDevice.Kind.LAMP_ARCANE && side == facing(); }
    @Override public boolean canInputFrom(Direction side) { return isConnectable(side); }
    @Override public boolean canOutputTo(Direction side) { return false; }
    @Override public void setSuction(Aspect aspect,int amount) {}
    @Override public Aspect getSuctionType(Direction side) { return kind() == thaumcraft.common.blocks.devices.BlockWorldDevice.Kind.LAMP_GROWTH ? Aspect.PLANT : Aspect.DESIRE; }
    @Override public int getSuctionAmount(Direction side) { if (!isConnectable(side)) return 0; return kind() == thaumcraft.common.blocks.devices.BlockWorldDevice.Kind.LAMP_GROWTH ? reserve && charges > 0 ? 0 : 128 : 128 - charges * 10; }
    @Override public int takeEssentia(Aspect aspect,int amount,Direction side) { return 0; }
    @Override public int addEssentia(Aspect aspect,int amount,Direction side) { return 0; }
    @Override public Aspect getEssentiaType(Direction side) { return null; }
    @Override public int getEssentiaAmount(Direction side) { return 0; }
    @Override public int getMinimumSuction() { return 0; }
    @Override protected void writeDevice(CompoundTag tag,HolderLookup.Provider registries) { tag.putInt("charges",charges); tag.putBoolean("reserve",reserve); }
    @Override protected void readDevice(CompoundTag tag,HolderLookup.Provider registries) { charges = tag.getInt("charges"); reserve = tag.getBoolean("reserve"); }
}
