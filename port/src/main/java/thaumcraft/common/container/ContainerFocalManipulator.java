package thaumcraft.common.container;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.common.items.casters.ItemFocus;
import thaumcraft.common.lib.network.playerdata.*;
import thaumcraft.common.tiles.crafting.*;

/** One actual focus slot; editor packet bound to player/menu/position/session/revision/sequence. */
public final class ContainerFocalManipulator extends AbstractContainerMenu {
    private final Container machine;
    private final Player viewer;
    private final long serverSession;
    private BlockPos position;
    private long session,revision = -1,sequence,lastSequence,lastSent = -1,nextSync,nextRequest;
    private CompoundTag view = new CompoundTag();
    private long stateVersion;
    private boolean rejected;
    public ContainerFocalManipulator(int id,Inventory inventory) { this(id,inventory,new SimpleContainer(1)); }
    public ContainerFocalManipulator(int id,Inventory inventory,Container machine) {
        super(FocalRegistries.MENU,id); this.machine = machine; viewer = inventory.player; long candidate = UUID.randomUUID().getMostSignificantBits(); serverSession = candidate == 0 ? 1 : candidate;
        addSlot(new Slot(machine,0,146,222) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof ItemFocus && (!(machine instanceof TileFocalManipulator tile) || !tile.busy()); }
            @Override public boolean mayPickup(Player player) { return !(machine instanceof TileFocalManipulator tile) || !tile.busy(); }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory,col + row * 9 + 9,178 + col * 18,174 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory,col,178 + col * 18,232));
    }
    public CompoundTag view() { return view.copy(); }
    public long revision() { return revision; }
    public long stateVersion() { return stateVersion; }
    public boolean ready() { return position != null; }
    public boolean rejected() { return rejected; }
    public boolean busy() { return view.getFloat("vis") > 0; }
    public PacketFocusEdit request(CompoundTag graph,String name,boolean craft) { if (!ready() || sequence == Long.MAX_VALUE || busy()) return null; rejected = false; return new PacketFocusEdit(containerId,position,session,revision,++sequence,craft,name,graph); }
    public void acceptState(PacketFocalState packet) {
        if (machine instanceof TileFocalManipulator || packet.menuId() != containerId || packet.revision() < revision
            || position != null && !position.equals(packet.position()) || session != 0 && session != packet.session()) return;
        position = packet.position(); session = packet.session(); revision = packet.revision(); view = packet.view(); rejected = packet.rejected(); stateVersion++;
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (viewer instanceof ServerPlayer player && player.containerMenu == this && machine instanceof TileFocalManipulator tile
            && (lastSent != tile.revision() || player.level().getGameTime() >= nextSync)) sendState(player,tile,false);
    }
    private void sendState(ServerPlayer player,TileFocalManipulator tile,boolean rejected) {
        PacketDistributor.sendToPlayer(player,new PacketFocalState(containerId,tile.getBlockPos(),serverSession,tile.revision(),tile.view(player),rejected)); lastSent = tile.revision(); nextSync = player.level().getGameTime() + 20;
    }
    public boolean handle(ServerPlayer player,PacketFocusEdit packet) {
        if (player != viewer || player.containerMenu != this || !(machine instanceof TileFocalManipulator tile) || !stillValid(player)
            || packet.menuId() != containerId || packet.session() != serverSession || !packet.position().equals(tile.getBlockPos()) || packet.sequence() <= lastSequence) return false;
        lastSequence = packet.sequence();
        if (player.level().getGameTime() < nextRequest) return false; nextRequest = player.level().getGameTime() + 2;
        if (packet.revision() != tile.revision()) { sendState(player,tile,true); return false; }
        boolean accepted = tile.apply(player,packet.graph(),packet.name(),packet.craft()); sendState(player,tile,!accepted); return accepted;
    }
    @Override public boolean stillValid(Player player) { return machine.stillValid(player); }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY; Slot slot = slots.get(index); if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(),copy = stack.copy();
        if (index == 0 ? !moveItemStackTo(stack,1,37,false) : stack.getItem() instanceof ItemFocus ? !moveItemStackTo(stack,0,1,false)
            : index < 28 ? !moveItemStackTo(stack,28,37,false) : !moveItemStackTo(stack,1,28,false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged(); if (copy.getCount() == stack.getCount()) return ItemStack.EMPTY; slot.onTake(player,stack); return copy;
    }
}
