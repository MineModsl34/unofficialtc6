package thaumcraft.common.container;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.network.misc.PacketSelectThaumotoriumRecipeToServer;
import thaumcraft.common.lib.network.misc.PacketThaumatoriumState;
import thaumcraft.common.tiles.crafting.TileThaumatorium;
import thaumcraft.common.tiles.devices.ProcessingRegistries;

/** Source 175x216 menu with one catalyst slot and six paged selectable outputs. */
public final class ContainerThaumatorium extends AbstractContainerMenu {
    public record View(ResourceLocation id,ItemStack output,AspectList cost,boolean selected) {}
    private final Container machine;
    private final Player viewer;
    private final long serverSession;
    private BlockPos clientPosition;
    private long clientSession,clientRevision = -1,clientSequence,lastRequestSequence,lastSentRevision = -1,nextPeriodic,nextRequestTick;
    private List<View> views = List.of();
    private AspectList contents = new AspectList();
    private int capacity = 1;
    public ContainerThaumatorium(int window,Inventory inventory) { this(window,inventory,new SimpleContainer(1)); }
    public ContainerThaumatorium(int window,Inventory inventory,Container machine) {
        super(ProcessingRegistries.THAUMATORIUM_MENU,window); this.machine = machine; viewer = inventory.player;
        long session = UUID.randomUUID().getMostSignificantBits(); serverSession = session == 0 ? 1 : session;
        checkContainerSize(machine,1); addSlot(new Slot(machine,0,55,24));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory,col + row * 9 + 9,8 + col * 18,135 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory,col,8 + col * 18,193));
    }
    public List<View> views() { return views; }
    public AspectList contents() { return contents; }
    public int capacity() { return capacity; }
    public boolean ready() { return clientPosition != null; }
    public PacketSelectThaumotoriumRecipeToServer selection(ResourceLocation id) {
        if (clientPosition == null || clientRevision < 0 || clientSequence == Long.MAX_VALUE) return null;
        return new PacketSelectThaumotoriumRecipeToServer(containerId,clientPosition,clientSession,clientRevision,++clientSequence,id);
    }
    public void acceptState(PacketThaumatoriumState packet,HolderLookup.Provider registries) {
        if (machine instanceof TileThaumatorium || packet.menuId() != containerId || packet.revision() < clientRevision
            || clientPosition != null && !clientPosition.equals(packet.position()) || clientSession != 0 && clientSession != packet.session()) return;
        CompoundTag tag = packet.view();
        int nextCapacity = tag.getInt("maxrec"); if (nextCapacity < 1 || nextCapacity > TileThaumatorium.MAX_RECIPES) return;
        var rows = tag.getList("views",Tag.TAG_COMPOUND); if (rows.size() > TileThaumatorium.MAX_VIEWS) return;
        ArrayList<View> next = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i); String key = row.getString("id"); ResourceLocation id = key.length() <= 256 ? ResourceLocation.tryParse(key) : null;
            if (id == null || next.stream().anyMatch(view -> view.id().equals(id))) return;
            ItemStack output = ItemStack.parseOptional(registries,row.getCompound("output")); if (output.isEmpty()) continue;
            AspectList cost = new AspectList(); cost.readFromNBT(row); next.add(new View(id,output,cost,row.getBoolean("selected")));
        }
        contents = new AspectList(); contents.readFromNBT(tag); views = List.copyOf(next); capacity = nextCapacity;
        clientPosition = packet.position(); clientSession = packet.session(); clientRevision = packet.revision();
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (viewer instanceof ServerPlayer player && player.containerMenu == this && machine instanceof TileThaumatorium tile
            && (lastSentRevision != tile.revision() || player.level().getGameTime() >= nextPeriodic)) sendState(player,tile);
    }
    private void sendState(ServerPlayer player,TileThaumatorium tile) {
        PacketDistributor.sendToPlayer(player,new PacketThaumatoriumState(containerId,tile.getBlockPos(),serverSession,tile.revision(),tile.view(player,player.registryAccess())));
        lastSentRevision = tile.revision(); nextPeriodic = player.level().getGameTime() + 20;
    }
    public boolean handleSelection(ServerPlayer player,PacketSelectThaumotoriumRecipeToServer packet) {
        if (player != viewer || player.containerMenu != this || !(machine instanceof TileThaumatorium tile) || !stillValid(player)
            || packet.menuId() != containerId || packet.session() != serverSession || !packet.position().equals(tile.getBlockPos()) || packet.sequence() <= lastRequestSequence) return false;
        lastRequestSequence = packet.sequence();
        if (player.level().getGameTime() < nextRequestTick) return false;
        nextRequestTick = player.level().getGameTime() + 1;
        if (packet.revision() != tile.revision()) { sendState(player,tile); return false; }
        boolean applied = tile.toggleRecipe(player,packet.recipe()); sendState(player,tile); return applied;
    }
    @Override public boolean stillValid(Player player) { return machine.stillValid(player); }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack item = slot.getItem(),copy = item.copy();
        if (index == 0 ? !moveItemStackTo(item,1,37,false) : !moveItemStackTo(item,0,1,false)) return ItemStack.EMPTY;
        if (item.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (item.getCount() == copy.getCount()) return ItemStack.EMPTY; slot.onTake(player,item); return copy;
    }
}
