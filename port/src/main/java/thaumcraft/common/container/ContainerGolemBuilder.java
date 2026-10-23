package thaumcraft.common.container;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.common.golems.GolemProperties;
import thaumcraft.common.lib.network.golems.*;
import thaumcraft.common.tiles.crafting.*;

/** Source one output plus36 inventory slots. Selection requests are menu/session/revision bound. */
public final class ContainerGolemBuilder extends AbstractContainerMenu {
    private final Container machine;
    private final Player viewer;
    private final long serverSession;
    private BlockPos position;
    private long session,revision = -1,sequence,lastSequence,lastSent = -1,nextSync,nextRequest,selected,available,job = -1;
    private int cost,maximum;
    public ContainerGolemBuilder(int id,Inventory inventory) { this(id,inventory,new SimpleContainer(1)); }
    public ContainerGolemBuilder(int id,Inventory inventory,Container machine) {
        super(Golem9BuilderRegistries.MENU,id); this.machine = machine; viewer = inventory.player;
        long candidate = UUID.randomUUID().getMostSignificantBits(); serverSession = candidate == 0 ? 1 : candidate;
        addSlot(new Slot(machine,0,160,104) { @Override public boolean mayPlace(ItemStack stack) { return false; } });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory,col + row * 9 + 9,24 + col * 18,142 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory,col,24 + col * 18,200));
    }
    public long selected() { return selected; }
    public int cost() { return cost; }
    public int maximum() { return maximum; }
    public boolean busy() { return job != -1; }
    public boolean ready() { return position != null; }
    public boolean available(int component) { return component >= 0 && component < 64 && (available & (1L << component)) != 0; }
    public PacketGolemBuilderSelect request(long properties,boolean craft) { return !ready() || sequence == Long.MAX_VALUE || busy() ? null : new PacketGolemBuilderSelect(containerId,position,session,revision,++sequence,properties,craft); }
    public void acceptState(PacketGolemBuilderState packet) {
        if (machine instanceof TileGolemBuilder || packet.menuId() != containerId || packet.revision() < revision
            || position != null && !position.equals(packet.position()) || session != 0 && session != packet.session()
            || !GolemProperties.isValid(packet.selected()) || packet.job() != -1 && !GolemProperties.isValid(packet.job())) return;
        position = packet.position(); session = packet.session(); revision = packet.revision(); selected = packet.selected(); available = packet.available();
        job = packet.job(); cost = packet.cost(); maximum = packet.maximum();
    }
    private void sendState(ServerPlayer player,TileGolemBuilder tile) {
        var flags = tile.checkCraft(selected,player); long mask = 0;
        for (int i = 0; i < Math.min(64,flags.length); i++) if (flags[i]) mask |= 1L << i;
        PacketDistributor.sendToPlayer(player,new PacketGolemBuilderState(containerId,tile.getBlockPos(),serverSession,tile.revision(),selected,tile.golem,tile.cost,tile.maxCost,mask));
        lastSent = tile.revision(); nextSync = player.level().getGameTime() + 20;
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges(); if (viewer instanceof ServerPlayer player && player.containerMenu == this && machine instanceof TileGolemBuilder tile
            && (lastSent != tile.revision() || player.level().getGameTime() >= nextSync)) sendState(player,tile);
    }
    public boolean handle(ServerPlayer player,PacketGolemBuilderSelect packet) {
        if (player != viewer || player.containerMenu != this || !(machine instanceof TileGolemBuilder tile) || !stillValid(player)
            || packet.menuId() != containerId || packet.session() != serverSession || !packet.position().equals(tile.getBlockPos()) || packet.sequence() <= lastSequence) return false;
        lastSequence = packet.sequence(); if (player.level().getGameTime() < nextRequest) { sendState(player,tile); return false; } nextRequest = player.level().getGameTime() + 1;
        if (packet.revision() != tile.revision() || tile.busy() || !GolemProperties.isValid(packet.properties())) { sendState(player,tile); return false; }
        selected = packet.properties(); boolean accepted = !packet.craft() || tile.startCraft(selected,player); sendState(player,tile); return accepted;
    }
    @Override public boolean stillValid(Player player) { return machine.stillValid(player); }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY; var slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        var item = slot.getItem(); var copy = item.copy();
        if (index == 0 ? !moveItemStackTo(item,1,37,true) : index < 28 ? !moveItemStackTo(item,28,37,false) : !moveItemStackTo(item,1,28,false)) return ItemStack.EMPTY;
        if (item.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged(); if (item.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player,item); return copy;
    }
}
