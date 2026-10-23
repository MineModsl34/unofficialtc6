package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.tiles.devices.TileBellows;

/** Exact ten-unit mixed buffer. Stronger eligible output wins; choking changes suction only. */
public class TileTubeBuffer extends TileTube implements IAspectContainer {
    public AspectList aspects = new AspectList();
    public final int MAXAMOUNT = 10;
    public byte[] chokedSides = new byte[6];
    private int bufferCount, bellows = -1;
    public TileTubeBuffer(BlockPos pos, BlockState state) { super(TransportRegistries.BUFFER, pos, state); }
    @Override public void readSyncNBT(CompoundTag tag) {
        aspects.readFromNBT(tag);
        byte[] sides = tag.getByteArray("open"); if (sides.length == 6) for (int i = 0; i < 6; i++) openSides[i] = sides[i] == 1;
        chokedSides = tag.getByteArray("choke"); if (chokedSides.length != 6) chokedSides = new byte[6];
        for (int i = 0; i < 6; i++) chokedSides[i] = (byte)Math.max(0, Math.min(2, chokedSides[i]));
        facing = Direction.from3DDataValue(Math.max(0, Math.min(5, tag.getInt("side"))));
    }
    @Override public CompoundTag writeSyncNBT(CompoundTag tag) {
        aspects.writeToNBT(tag); byte[] sides = new byte[6]; for (int i = 0; i < 6; i++) sides[i] = (byte)(openSides[i] ? 1 : 0);
        tag.putByteArray("open", sides); tag.putByteArray("choke", chokedSides); tag.putInt("side", facing.get3DDataValue()); return tag;
    }
    @Override public AspectList getAspects() { return aspects; }
    @Override public void setAspects(AspectList values) {}
    @Override public int addToContainer(Aspect aspect, int amount) {
        if (amount != 1 || aspect == null) return amount;
        if (aspects.visSize() < MAXAMOUNT) { aspects.add(aspect, amount); changedAndSync(); return 0; } return amount;
    }
    @Override public boolean takeFromContainer(Aspect aspect, int amount) {
        if (amount >= 0 && aspects.getAmount(aspect) >= amount) { aspects.remove(aspect, amount); changedAndSync(); return true; } return false;
    }
    @Override public boolean takeFromContainer(AspectList list) { return false; }
    @Override public boolean doesContainerContainAmount(Aspect aspect, int amount) { return aspects.getAmount(aspect) >= amount; }
    @Override public boolean doesContainerContain(AspectList list) { return false; }
    @Override public int containerContains(Aspect aspect) { return aspects.getAmount(aspect); }
    @Override public boolean doesContainerAccept(Aspect aspect) { return true; }
    @Override public void setSuction(Aspect aspect, int amount) {}
    @Override public Aspect getSuctionType(Direction side) { return null; }
    @Override public int getSuctionAmount(Direction side) { return side == null ? 0 : TransportRules.bufferSuction(bellows, chokedSides[side.get3DDataValue()]); }
    @Override public Aspect getEssentiaType(Direction side) { return aspects.size() > 0 && level != null ? aspects.getAspects()[level.random.nextInt(aspects.size())] : null; }
    @Override public int getEssentiaAmount(Direction side) { return aspects.visSize(); }
    @Override public int takeEssentia(Aspect aspect, int amount, Direction side) {
        if (!canOutputTo(side) || amount < 0) return 0;
        IEssentiaTransport neighbor = TransportConnections.connectable(level, worldPosition, side);
        int requestSuction = neighbor == null ? 0 : neighbor.getSuctionAmount(side.getOpposite());
        for (Direction otherSide : Direction.values()) if (canOutputTo(otherSide) && otherSide != side) {
            neighbor = TransportConnections.connectable(level, worldPosition, otherSide); if (neighbor == null) continue;
            int strength = neighbor.getSuctionAmount(otherSide.getOpposite()); Aspect type = neighbor.getSuctionType(otherSide.getOpposite());
            if ((type == aspect || type == null) && requestSuction < strength && getSuctionAmount(otherSide) < strength) return 0;
        }
        amount = Math.min(amount, aspects.getAmount(aspect)); return takeFromContainer(aspect, amount) ? amount : 0;
    }
    @Override public int addEssentia(Aspect aspect, int amount, Direction side) { return canInputFrom(side) ? amount - addToContainer(aspect, amount) : 0; }
    @Override public void update() {
        if (level == null) return; bufferCount++;
        if (bellows < 0 || bufferCount % 20 == 0) getBellows();
        if (!level.isClientSide && bufferCount % 5 == 0 && aspects.visSize() < MAXAMOUNT) fillBuffer();
    }
    public void getBellows() { bellows = TileBellows.getBellows(level, worldPosition, Direction.values()); }
    private void fillBuffer() {
        for (Direction side : Direction.values()) if (canInputFrom(side)) {
            IEssentiaTransport neighbor = TransportConnections.connectable(level, worldPosition, side); if (neighbor == null) continue;
            if (neighbor.getEssentiaAmount(side.getOpposite()) > 0 && neighbor.getSuctionAmount(side.getOpposite()) < getSuctionAmount(side) && getSuctionAmount(side) >= neighbor.getMinimumSuction()) {
                Aspect aspect = neighbor.getEssentiaType(side.getOpposite()); addToContainer(aspect, neighbor.takeEssentia(aspect, 1, side.getOpposite())); return;
            }
        }
    }
    @Override public boolean onCasterRightClick(Level world, ItemStack wand, Player player, BlockPos pos, Direction side, InteractionHand hand) {
        int subHit = retraceSubHit(player); if (subHit < 0 || subHit >= 6) return false;
        if (!world.isClientSide) {
            if (player.isShiftKeyDown()) { chokedSides[subHit] = (byte)TransportRules.nextChoke(chokedSides[subHit]); markDirty(); syncTile(true); }
            else toggleSide(subHit);
            world.playSound(null, pos, player.isShiftKeyDown() ? SoundsTC.squeek : SoundsTC.tool, SoundSource.BLOCKS, player.isShiftKeyDown() ? .6F : .5F,
                (player.isShiftKeyDown() ? 2 : .9F) + world.random.nextFloat() * .2F);
        }
        player.swing(hand); return true;
    }
}
