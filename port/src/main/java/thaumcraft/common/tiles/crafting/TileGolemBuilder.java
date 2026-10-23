package thaumcraft.common.tiles.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.container.ContainerGolemBuilder;
import thaumcraft.common.golems.*;
import thaumcraft.common.lib.SoundsTC;

/** Actual one-slot source press: paid source components and one MECHANISM per five ticks. */
public final class TileGolemBuilder extends BlockEntity implements WorldlyContainer,MenuProvider,IEssentiaTransport {
    public long golem = -1;
    public int cost,maxCost,press;
    public boolean[] hasStuff;
    private ItemStack output = ItemStack.EMPTY;
    private boolean bufferedEssentia,starting;
    private int ticks;
    private long revision;
    public TileGolemBuilder(BlockPos pos,BlockState state) { super(Golem9BuilderRegistries.TYPE,pos,state); }
    public boolean busy() { return golem != -1; }
    public long revision() { return revision; }
    public static boolean knowsParts(Player player,GolemProperties props) {
        return ThaumcraftCapabilities.knowsResearchStrict(player,props.getMaterial().research)
            && ThaumcraftCapabilities.knowsResearchStrict(player,props.getHead().research)
            && ThaumcraftCapabilities.knowsResearchStrict(player,props.getArms().research)
            && ThaumcraftCapabilities.knowsResearchStrict(player,props.getLegs().research)
            && ThaumcraftCapabilities.knowsResearchStrict(player,props.getAddon().research);
    }
    public boolean[] checkCraft(long packed,Player player) {
        if (level == null || !stillValid(player)) return new boolean[0];
        try { var props = GolemProperties.fromLong(packed); if (!GolemPropertyRules.newborn(packed) || !knowsParts(player,props)) return new boolean[0];
            return GolemBuilderPayment.availability(level,worldPosition,player,props.generateComponents());
        } catch (IllegalArgumentException | ArithmeticException invalid) { return new boolean[0]; }
    }
    private boolean room(ItemStack placer) { return output.isEmpty() || output.getCount() < output.getMaxStackSize() && ItemStack.isSameItemSameComponents(output,placer); }
    public boolean startCraft(long packed,Player player) {
        if (level == null || level.isClientSide || !stillValid(player) || busy() || starting) return false;
        final GolemProperties props; final ItemStack[] components; final int work;
        try {
            props = GolemProperties.fromLong(packed); if (!GolemPropertyRules.newborn(packed) || !knowsParts(player,props)) return false;
            components = props.generateComponents(); work = GolemPropertyRules.workCost(props.getTraits().size(),java.util.Arrays.stream(components).mapToInt(ItemStack::getCount).toArray());
        } catch (IllegalArgumentException | ArithmeticException invalid) { return false; }
        var placer = GolemItemData.write(new ItemStack(ItemsTC.golemPlacer),props,0); if (!room(placer)) return false;
        starting = true;
        try { if (!GolemBuilderPayment.pay(level,worldPosition,player,components)) return false;
            golem = packed; cost = maxCost = work; bufferedEssentia = false; changedAndSync();
            level.playSound(null,worldPosition,SoundsTC.wand,SoundSource.BLOCKS,.25F,1); return true;
        } finally { starting = false; }
    }
    public static void tick(Level level,BlockPos pos,BlockState state,TileGolemBuilder tile) {
        if (level.isClientSide) { tile.animate(); return; }
        if (++tile.ticks % 5 != 0 || !tile.busy()) return;
        if (tile.cost > 0 && (tile.bufferedEssentia || tile.drawEssentia())) { tile.bufferedEssentia = false; tile.cost--; tile.changedAndSync(); }
        if (tile.cost == 0) {
            var placer = GolemItemData.write(new ItemStack(ItemsTC.golemPlacer),GolemProperties.fromLong(tile.golem),0);
            // Source could strand an already paid completed job behind a full hopper-fed output. Retry without paying twice.
            if (tile.room(placer)) { if (tile.output.isEmpty()) tile.output = placer; else tile.output.grow(1);
                tile.golem = -1; tile.bufferedEssentia = false; tile.changedAndSync(); level.playSound(null,pos,SoundsTC.wand,SoundSource.BLOCKS,1,1); }
        }
    }
    private void animate() {
        thaumcraft.common.lib.fx.SourceFX.local(level,"golem_builder",net.minecraft.world.phys.Vec3.atLowerCornerOf(worldPosition),net.minecraft.world.phys.Vec3.ZERO,0,0);
    }
    private boolean drawEssentia() {
        for (Direction face : Direction.values()) {
            var tile = ThaumcraftApiHelper.getConnectableTile(level,worldPosition,face);
            if (tile instanceof IEssentiaTransport source) {
                if (!source.canOutputTo(face.getOpposite())) return false; // Exact source early-stop, not skip.
                if (source.getSuctionAmount(face.getOpposite()) < getSuctionAmount(face) && source.takeEssentia(Aspect.MECHANISM,1,face.getOpposite()) == 1) return true;
            }
        }
        return false;
    }
    public void changedAndSync() { setChanged(); if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2); }
    @Override public void setChanged() { super.setChanged(); if (revision < Long.MAX_VALUE) revision++; }
    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return output.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return slot == 0 ? output : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot,int amount) { if (slot != 0 || amount < 1) return ItemStack.EMPTY; var out = output.split(amount); if (!out.isEmpty()) changedAndSync(); return out; }
    @Override public ItemStack removeItemNoUpdate(int slot) { if (slot != 0) return ItemStack.EMPTY; var out = output; output = ItemStack.EMPTY; return out; }
    @Override public void setItem(int slot,ItemStack stack) { if (slot != 0) return; output = stack; stack.limitSize(Math.min(getMaxStackSize(),stack.getMaxStackSize())); changedAndSync(); }
    @Override public void clearContent() { output = ItemStack.EMPTY; changedAndSync(); }
    @Override public boolean canPlaceItem(int slot,ItemStack stack) { return slot == 0 && stack.getItem() instanceof ItemGolemPlacer; }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getCenter()) <= 64; }
    @Override public int[] getSlotsForFace(Direction side) { return new int[]{0}; }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side) { return canPlaceItem(slot,stack); }
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side) { return slot == 0; }
    @Override public Component getDisplayName() { return Component.translatable("block.thaumcraft.golem_builder"); }
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player) { return new ContainerGolemBuilder(id,inventory,this); }
    @Override public boolean isConnectable(Direction face) { return face == null || face != Direction.UP; }
    @Override public boolean canInputFrom(Direction face) { return isConnectable(face); }
    @Override public boolean canOutputTo(Direction face) { return false; }
    @Override public void setSuction(Aspect aspect,int amount) {}
    @Override public int getMinimumSuction() { return 0; }
    @Override public Aspect getSuctionType(Direction face) { return Aspect.MECHANISM; }
    @Override public int getSuctionAmount(Direction face) { return cost > 0 && busy() ? 128 : 0; }
    @Override public Aspect getEssentiaType(Direction face) { return null; }
    @Override public int getEssentiaAmount(Direction face) { return 0; }
    @Override public int takeEssentia(Aspect aspect,int amount,Direction face) { return 0; }
    @Override public int addEssentia(Aspect aspect,int amount,Direction face) {
        if (amount <= 0 || !isConnectable(face) || bufferedEssentia || cost <= 0 || !busy() || aspect != Aspect.MECHANISM) return 0;
        bufferedEssentia = true; changedAndSync(); return 1;
    }
    private void write(CompoundTag tag,HolderLookup.Provider lookup) {
        tag.putLong("golem",golem); tag.putInt("cost",cost); tag.putInt("mcost",maxCost); tag.putBoolean("buffered",bufferedEssentia);
        if (!output.isEmpty()) tag.put("output",output.save(lookup));
    }
    private void read(CompoundTag tag,HolderLookup.Provider lookup) {
        golem = tag.contains("golem") ? tag.getLong("golem") : -1; cost = tag.getInt("cost"); maxCost = tag.getInt("mcost"); bufferedEssentia = tag.getBoolean("buffered");
        output = ItemStack.parseOptional(lookup,tag.getCompound("output")); output.limitSize(Math.min(output.getMaxStackSize(),getMaxStackSize()));
        if (golem != -1 && (!GolemProperties.isValid(golem) || !GolemPropertyRules.newborn(golem) || cost < 0 || maxCost < 1 || cost > maxCost || maxCost > 65535)) { golem = -1; cost = maxCost = 0; bufferedEssentia = false; }
        if (golem == -1) { cost = 0; bufferedEssentia = false; }
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup) { super.saveAdditional(tag,lookup); write(tag,lookup); }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup) { super.loadAdditional(tag,lookup); read(tag,lookup); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) { var tag = new CompoundTag(); write(tag,lookup); return tag; }
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider lookup) { read(tag,lookup); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
