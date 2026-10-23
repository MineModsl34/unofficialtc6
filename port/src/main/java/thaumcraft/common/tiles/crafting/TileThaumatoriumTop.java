package thaumcraft.common.tiles.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.tiles.devices.ProcessingRegistries;

/** Upper half is a live forwarding view, not another owned inventory or copied essentia tank. */
public final class TileThaumatoriumTop extends BlockEntity implements IAspectContainer,IEssentiaTransport,WorldlyContainer {
    public TileThaumatoriumTop(BlockPos pos,BlockState state) { super(ProcessingRegistries.THAUMATORIUM_TOP,pos,state); }
    public TileThaumatorium base() { return level != null && level.getBlockEntity(worldPosition.below()) instanceof TileThaumatorium tile ? tile : null; }
    @Override public AspectList getAspects() { return base() == null ? null : base().getAspects(); }
    @Override public void setAspects(AspectList aspects) { if (base() != null) base().setAspects(aspects); }
    @Override public boolean doesContainerAccept(Aspect aspect) { return true; }
    @Override public int addToContainer(Aspect aspect,int amount) { return base() == null ? amount : base().addToContainer(aspect,amount); }
    @Override public boolean takeFromContainer(Aspect aspect,int amount) { return base() != null && base().takeFromContainer(aspect,amount); }
    @Override public boolean takeFromContainer(AspectList aspects) { return false; }
    @Override public boolean doesContainerContain(AspectList aspects) { return false; }
    @Override public boolean doesContainerContainAmount(Aspect aspect,int amount) { return base() != null && base().doesContainerContainAmount(aspect,amount); }
    @Override public int containerContains(Aspect aspect) { return base() == null ? 0 : base().containerContains(aspect); }
    @Override public boolean isConnectable(Direction face) { return base() != null && base().isConnectable(face); }
    @Override public boolean canInputFrom(Direction face) { return base() != null && base().canInputFrom(face); }
    @Override public boolean canOutputTo(Direction face) { return false; }
    @Override public void setSuction(Aspect aspect,int amount) { if (base() != null) base().setSuction(aspect,amount); }
    @Override public Aspect getSuctionType(Direction face) { return base() == null ? null : base().getSuctionType(face); }
    @Override public int getSuctionAmount(Direction face) { return base() == null ? 0 : base().getSuctionAmount(face); }
    @Override public Aspect getEssentiaType(Direction face) { return null; }
    @Override public int getEssentiaAmount(Direction face) { return 0; }
    @Override public int takeEssentia(Aspect aspect,int amount,Direction face) { return 0; }
    @Override public int addEssentia(Aspect aspect,int amount,Direction face) { return base() == null ? 0 : base().addEssentia(aspect,amount,face); }
    @Override public int getMinimumSuction() { return 0; }
    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return base() == null || base().isEmpty(); }
    @Override public ItemStack getItem(int slot) { return base() == null ? ItemStack.EMPTY : base().getItem(slot); }
    @Override public ItemStack removeItem(int slot,int amount) { return base() == null ? ItemStack.EMPTY : base().removeItem(slot,amount); }
    @Override public ItemStack removeItemNoUpdate(int slot) { return base() == null ? ItemStack.EMPTY : base().removeItemNoUpdate(slot); }
    @Override public void setItem(int slot,ItemStack stack) { if (base() != null) base().setItem(slot,stack); }
    @Override public void setChanged() { super.setChanged(); if (base() != null) base().setChanged(); }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getCenter()) <= 64; }
    @Override public void clearContent() { if (base() != null) base().clearContent(); }
    @Override public int[] getSlotsForFace(Direction side) { return new int[]{0}; }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side) { return slot == 0; }
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side) { return slot == 0; }
}
