package thaumcraft.common.tiles.essentia;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.api.casters.IInteractWithCaster;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.lib.fx.SourceFX;

/** Original one-unit pipe. Native ray/AABB selection replaces CodeChicken subHit only. */
public class TileTube extends TransportTile implements IEssentiaTransport, IInteractWithCaster {
    public static final int freq = 5;
    public Direction facing = Direction.NORTH;
    public boolean[] openSides = {true, true, true, true, true, true};
    protected Aspect essentiaType, suctionType;
    protected int essentiaAmount, suction, venting, count, ventColor;
    public TileTube(BlockPos pos, BlockState state) { this(TransportRegistries.TUBE, pos, state); }
    protected TileTube(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    @Override public void readSyncNBT(CompoundTag tag) {
        essentiaType = Aspect.getAspect(tag.getString("type")); essentiaAmount = Math.max(0, Math.min(1, tag.getInt("amount")));
        facing = Direction.from3DDataValue(Math.max(0, Math.min(5, tag.getInt("side"))));
        byte[] sides = tag.getByteArray("open"); if (sides.length == 6) for (int i = 0; i < 6; i++) openSides[i] = sides[i] == 1;
    }
    @Override public CompoundTag writeSyncNBT(CompoundTag tag) {
        if (essentiaType != null) tag.putString("type", essentiaType.getTag());
        tag.putInt("amount", essentiaAmount); tag.putInt("side", facing.get3DDataValue());
        byte[] sides = new byte[6]; for (int i = 0; i < 6; i++) sides[i] = (byte)(openSides[i] ? 1 : 0); tag.putByteArray("open", sides); return tag;
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider); suctionType = Aspect.getAspect(tag.getString("stype")); suction = Math.max(0, tag.getInt("samount"));
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider); if (suctionType != null) tag.putString("stype", suctionType.getTag()); tag.putInt("samount", suction);
    }
    @Override public void update() {
        if (level == null) return;
        if (venting > 0) venting--;
        if (count == 0) count = level.random.nextInt(10);
        if (!level.isClientSide) {
            if (venting <= 0) {
                if (++count % 2 == 0) { calculateSuction(null, false, false); checkVenting(); if (essentiaType != null && essentiaAmount == 0) essentiaType = null; }
                if (count % 5 == 0 && suction > 0) equalizeWithNeighbours(false);
            }
        } else if (venting > 0) {
            java.util.Random random = new java.util.Random(hashCode() * 4);
            // Original float degree conversion and lookup-table trig, not double radians.
            float pitch = random.nextFloat() * 360F, yaw = random.nextFloat() * 360F;
            double x = -Mth.sin(yaw / 180F * (float)Math.PI) * Mth.cos(pitch / 180F * (float)Math.PI);
            double z = Mth.cos(yaw / 180F * (float)Math.PI) * Mth.cos(pitch / 180F * (float)Math.PI);
            double y = -Mth.sin(pitch / 180F * (float)Math.PI);
            SourceFX.local(level,"tube_vent",Vec3.atCenterOf(worldPosition),new Vec3(x/5,y/5,z/5),ventColor,0);
        }
    }
    protected void calculateSuction(Aspect filter, boolean restrict, boolean directional) {
        suction = 0; suctionType = null;
        for (Direction side : Direction.values()) {
            try {
                if ((directional && facing != side.getOpposite()) || !isConnectable(side)) continue;
                IEssentiaTransport neighbor = TransportConnections.connectable(level, worldPosition, side); if (neighbor == null) continue;
                Direction opposite = side.getOpposite();
                if ((filter == null || neighbor.getSuctionType(opposite) == null || neighbor.getSuctionType(opposite) == filter)
                    && (filter != null || getEssentiaAmount(side) <= 0 || neighbor.getSuctionType(opposite) == null || getEssentiaType(side) == neighbor.getSuctionType(opposite))
                    && (filter == null || getEssentiaAmount(side) <= 0 || getEssentiaType(side) == null || neighbor.getSuctionType(opposite) == null || getEssentiaType(side) == neighbor.getSuctionType(opposite))) {
                    int strength = neighbor.getSuctionAmount(opposite);
                    if (TransportRules.supersedes(strength, suction)) {
                        Aspect type = neighbor.getSuctionType(opposite); if (type == null) type = filter;
                        setSuction(type, TransportRules.propagatedSuction(strength, restrict));
                    }
                }
            } catch (RuntimeException incompatibleAddon) { /* Source isolates each malformed addon endpoint. */ }
        }
    }
    protected void checkVenting() {
        for (Direction side : Direction.values()) {
            try {
                if (!isConnectable(side)) continue;
                IEssentiaTransport neighbor = TransportConnections.connectable(level, worldPosition, side); if (neighbor == null) continue;
                if (TransportRules.ventConflict(suction, neighbor.getSuctionAmount(side.getOpposite()), suctionType, neighbor.getSuctionType(side.getOpposite()),
                    TransportConnections.tile(level, worldPosition.relative(side)) instanceof TileTubeFilter)) {
                    int index = suctionType == null ? -1 : ModConfig.aspectOrder.indexOf(suctionType);
                    level.blockEvent(worldPosition, getBlockState().getBlock(), 1, index); venting = 40;
                }
            } catch (RuntimeException incompatibleAddon) { }
        }
    }
    protected void equalizeWithNeighbours(boolean directional) {
        if (essentiaAmount > 0) return;
        for (Direction side : Direction.values()) {
            try {
                if ((directional && facing == side.getOpposite()) || !isConnectable(side)) continue;
                IEssentiaTransport neighbor = TransportConnections.connectable(level, worldPosition, side); if (neighbor == null) continue;
                Direction opposite = side.getOpposite();
                if (neighbor.canOutputTo(opposite) && (getSuctionType(null) == null || getSuctionType(null) == neighbor.getEssentiaType(opposite) || neighbor.getEssentiaType(opposite) == null)
                    && getSuctionAmount(null) > neighbor.getSuctionAmount(opposite) && getSuctionAmount(null) >= neighbor.getMinimumSuction()) {
                    Aspect type = getSuctionType(null); if (type == null) { type = neighbor.getEssentiaType(opposite); if (type == null) type = neighbor.getEssentiaType(null); }
                    int added = addEssentia(type, neighbor.takeEssentia(type, 1, opposite), side);
                    if (added > 0) { if (level.random.nextInt(100) == 0) level.blockEvent(worldPosition, getBlockState().getBlock(), 0, 0); return; }
                }
            } catch (RuntimeException incompatibleAddon) { }
        }
    }
    @Override public boolean isConnectable(Direction side) { return side != null && openSides[side.get3DDataValue()]; }
    @Override public boolean canInputFrom(Direction side) { return side != null && openSides[side.get3DDataValue()]; }
    @Override public boolean canOutputTo(Direction side) { return side != null && openSides[side.get3DDataValue()]; }
    @Override public void setSuction(Aspect aspect, int amount) { suctionType = aspect; suction = amount; }
    @Override public Aspect getSuctionType(Direction side) { return suctionType; }
    @Override public int getSuctionAmount(Direction side) { return suction; }
    @Override public Aspect getEssentiaType(Direction side) { return essentiaType; }
    @Override public int getEssentiaAmount(Direction side) { return essentiaAmount; }
    @Override public int getMinimumSuction() { return 0; }
    @Override public int takeEssentia(Aspect aspect, int amount, Direction side) {
        if (canOutputTo(side) && essentiaType == aspect && essentiaAmount > 0 && amount > 0) { essentiaAmount--; if (essentiaAmount <= 0) essentiaType = null; markDirty(); return 1; } return 0;
    }
    @Override public int addEssentia(Aspect aspect, int amount, Direction side) {
        if (canInputFrom(side) && essentiaAmount == 0 && amount > 0) { essentiaType = aspect; essentiaAmount++; markDirty(); return 1; } return 0;
    }
    @Override public boolean triggerEvent(int event, int data) {
        if (event == 0) { if (level != null && level.isClientSide) level.playLocalSound(worldPosition, SoundsTC.creak, SoundSource.AMBIENT, 1, 1.3F + level.random.nextFloat() * .2F, false); return true; }
        if (event == 1) {
            if (level != null && level.isClientSide) {
                if (venting <= 0) level.playLocalSound(worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, .1F, 1 + level.random.nextFloat() * .1F, false);
                venting = 50; var order = ModConfig.aspectOrder; ventColor = data >= 0 && data < order.size() ? order.get(data).getColor() : 11184810;
            }
            return true;
        }
        return super.triggerEvent(event, data);
    }
    public boolean canConnectSide(Direction side) { return level != null && TransportConnections.at(level, worldPosition.relative(side), side.getOpposite()) != null; }
    public record TraceCuboid(int index, AABB box) {}
    public List<TraceCuboid> traceableCuboids() {
        List<TraceCuboid> result = new ArrayList<>(); boolean buffer = this instanceof TileTubeBuffer; double end = buffer ? .5 : .375, start = buffer ? .5 : .625;
        for (Direction side : Direction.values()) if (canConnectSide(side)) {
            AABB box = switch (side) {
                case DOWN -> new AABB(.375, 0, .375, .625, end, .625);
                case UP -> new AABB(.375, start, .375, .625, 1, .625);
                case NORTH -> new AABB(.375, .375, 0, .625, .625, end);
                case SOUTH -> new AABB(.375, .375, start, .625, .625, 1);
                case WEST -> new AABB(0, .375, .375, end, .625, .625);
                case EAST -> new AABB(start, .375, .375, 1, .625, .625);
            }; result.add(new TraceCuboid(side.get3DDataValue(), box.move(worldPosition)));
        }
        result.add(new TraceCuboid(6, (buffer ? new AABB(.25, .25, .25, .75, .75, .75) : new AABB(.375, .375, .375, .625, .625, .625)).move(worldPosition)));
        return result;
    }
    public int retraceSubHit(Player player) {
        Vec3 from = player.getEyePosition(), to = from.add(player.getViewVector(1).scale(player.blockInteractionRange()));
        int index = -1; double distance = Double.MAX_VALUE;
        for (TraceCuboid cuboid : traceableCuboids()) { var hit = cuboid.box.clip(from, to); if (hit.isPresent() && from.distanceToSqr(hit.get()) < distance) { distance = from.distanceToSqr(hit.get()); index = cuboid.index; } }
        return index;
    }
    protected void toggleSide(int index) {
        openSides[index] = !openSides[index]; Direction side = Direction.from3DDataValue(index);
        if (TransportConnections.tile(level, worldPosition.relative(side)) instanceof TileTube other) { other.openSides[side.getOpposite().get3DDataValue()] = openSides[index]; other.markDirty(); other.syncTile(true); }
        markDirty(); syncTile(true);
    }
    protected boolean cycleFacing() {
        for (int index = facing.get3DDataValue() + 1; index < 20; index++) {
            Direction candidate = Direction.from3DDataValue(index % 6);
            if (canConnectSide(candidate.getOpposite()) && isConnectable(candidate.getOpposite())) { facing = candidate; return true; }
        }
        return false;
    }
    @Override public boolean onCasterRightClick(Level world, ItemStack wand, Player player, BlockPos pos, Direction side, InteractionHand hand) {
        int subHit = retraceSubHit(player); if (subHit < 0 || subHit > 6) return false;
        if (!world.isClientSide) { if (subHit < 6) toggleSide(subHit); else { cycleFacing(); markDirty(); syncTile(true); } world.playSound(null, pos, SoundsTC.tool, SoundSource.BLOCKS, .5F, .9F + world.random.nextFloat() * .2F); }
        player.swing(hand); return true;
    }
}
