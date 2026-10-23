package thaumcraft.common.container;

import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import thaumcraft.api.items.IScribeTools;
import thaumcraft.common.tiles.crafting.ResearchTableRegistries;
import thaumcraft.common.lib.research.LegacyScanIdentity;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.common.tiles.crafting.TileResearchTable;
import thaumcraft.common.lib.network.research.PacketTheorycraftAction;
import thaumcraft.common.lib.network.research.PacketTheorycraftState;
import thaumcraft.common.lib.research.theorycraft.TheorycraftInventory;

/** Source theory actions plus vanilla server-validated container transactions.
 * Coordinates/player-slot registration order follow the original 255x255 GUI. The source
 * transfer bug is intentional: player menu index>=2 is passed
 * as a TABLE slot index, so quick-move from player to table always fails. Manual placing works.
 * Table-to-player quick-move retains reverse source registration order and slot validation. */
public final class ContainerResearchTable extends AbstractContainerMenu {
    private final Container table;
    private final Player viewer;
    private ResearchTableData clientData;
    private BlockPos clientPosition;
    private long clientRevision = -1, lastSentRevision = -1, clientSequence, lastRequestSequence;
    private long nextCardTick = Long.MIN_VALUE, nextAidCheck;
    private int clientInspiration;
    private boolean clientOwned;
    private Set<String> currentAids = Set.of();
    private final long serverSession;
    private long clientSession, nextRequestTick = Long.MIN_VALUE;
    private long receivedStateGeneration;

    public ContainerResearchTable(int window, Inventory inventory) { this(window, inventory, clientInventory()); }

    private static Container clientInventory() {
        return new SimpleContainer(2) {
            @Override public int getMaxStackSize() { return 64; }
        };
    }

    public ContainerResearchTable(int window, Inventory inventory, Container table) {
        super(ResearchTableRegistries.RESEARCH_TABLE_MENU, window);
        this.table = table;
        viewer = inventory.player;
        long session = UUID.randomUUID().getMostSignificantBits();
        serverSession = session == 0 ? 1L : session;
        checkContainerSize(table, 2);
        table.startOpen(inventory.player);
        addSlot(new Slot(table, 0, 16, 15) {
            @Override public boolean mayPlace(ItemStack stack) { return validSlot(0, stack); }
        });
        addSlot(new Slot(table, 1, 224, 16) {
            @Override public boolean mayPlace(ItemStack stack) { return validSlot(1, stack); }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column + row * 9 + 9, 77 + column * 18, 190 + row * 18));
        }
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 3; row++) addSlot(new Slot(inventory, column + row * 3, 20 + column * 18, 190 + row * 18));
        }
    }

    public Container table() { return table; }
    public ResearchTableData theoryData() { return table instanceof TileResearchTable tile ? tile.data : clientData; }
    public Set<String> theoryAids() { return currentAids; }
    public int availableInspiration() { return table instanceof TileResearchTable ? ResearchTableData.getAvailableInspiration(viewer) : clientInspiration; }
    public boolean theoryOwned() { return table instanceof TileResearchTable tile ? tile.data == null || tile.data.ownedBy(viewer) : clientOwned; }
    public boolean theoryReady() { return table instanceof TileResearchTable || clientPosition != null; }
    public long viewRevision() { return table instanceof TileResearchTable tile ? tile.theoryRevision() : clientRevision; }
    public long receivedStateGeneration() { return receivedStateGeneration; }
    public PacketTheorycraftAction prepareTheoryAction(int button, Set<String> aids) {
        if (clientPosition == null || clientRevision < 0 || clientSequence == Long.MAX_VALUE) return null;
        return new PacketTheorycraftAction(containerId, clientPosition, clientSession, clientRevision, ++clientSequence, button, aids);
    }
    public void acceptTheoryState(PacketTheorycraftState state, HolderLookup.Provider registries) {
        if (table instanceof TileResearchTable || state.menuId() != containerId || state.revision() < clientRevision) return;
        if (clientPosition != null && !clientPosition.equals(state.position())) return;
        if (clientSession != 0 && clientSession != state.session()) return;
        ResearchTableData next = null;
        if (!state.note().isEmpty()) { next = new ResearchTableData(null); next.deserialize(state.note(), registries); }
        clientPosition = state.position(); clientRevision = state.revision(); clientInspiration = state.availableInspiration();
        clientSession = state.session();
        clientOwned = state.owned(); currentAids = state.aids(); clientData = next;
        receivedStateGeneration++;
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (!(viewer instanceof ServerPlayer owner) || !(table instanceof TileResearchTable tile) || owner.containerMenu != this) return;
        long tick = owner.level().getGameTime();
        boolean aidsChanged = false;
        if (tick >= nextAidCheck) { Set<String> next = Set.copyOf(tile.checkSurroundingAids()); aidsChanged = !next.equals(currentAids); currentAids = next; nextAidCheck = tick + 100; }
        if (lastSentRevision != tile.theoryRevision() || aidsChanged) sendTheoryState(owner, tile);
    }
    private void sendTheoryState(ServerPlayer player, TileResearchTable tile) {
        boolean owned = tile.data == null || tile.data.ownedBy(player);
        CompoundTag note = tile.data != null && owned ? tile.data.serialize(player.registryAccess()) : new CompoundTag();
        PlayerDataSync.sendTheoryState(player, new PacketTheorycraftState(containerId, tile.getBlockPos(), serverSession, tile.theoryRevision(),
            ResearchTableData.getAvailableInspiration(player), owned, currentAids, note));
        lastSentRevision = tile.theoryRevision();
    }
    public boolean handleTheoryAction(ServerPlayer owner, PacketTheorycraftAction request) {
        if (owner != viewer || owner.containerMenu != this || !(table instanceof TileResearchTable tile) || !stillValid(owner)
            || request.menuId() != containerId || request.session() != serverSession || !request.position().equals(tile.getBlockPos()) || request.sequence() <= lastRequestSequence) return false;
        lastRequestSequence = request.sequence();
        if (owner.level().getGameTime() < nextRequestTick) return false;
        nextRequestTick = owner.level().getGameTime() + 1;
        if (request.revision() != tile.theoryRevision() || tile.data != null && !tile.data.ownedBy(owner)) { sendTheoryState(owner, tile); return false; }
        int button = request.button(); boolean applied = false;
        if (button == 0) {
            Set<String> available = tile.checkSurroundingAids(); currentAids = Set.copyOf(available);
            if (tile.data == null && tile.hasInk() && tile.hasPaper() && available.containsAll(request.aids())
                && request.aids().size() < ResearchTableData.getAvailableInspiration(owner)) { tile.startNewTheory(owner, request.aids()); applied = true; }
        } else if (tile.data != null) {
            ResearchTableData data = tile.data;
            if (button == 1) {
                var selected = data.cardChoices.stream().filter(choice -> choice.selected).findFirst();
                if (selected.isPresent()) {
                    if (data.lastDraw != null) data.savedCards.add(data.lastDraw.card.getSeed());
                    data.lastDraw = selected.get(); data.cardChoices.clear(); tile.setChanged(); applied = true;
                }
            } else if ((button == 2 || button == 3) && !data.isComplete() && data.cardChoices.isEmpty() && tile.consumepaperFromTable()) {
                data.drawCards(button, owner); tile.setChanged(); applied = true;
            } else if (button >= 4 && button <= 6 && !data.isComplete() && tile.hasInk()
                && owner.level().getGameTime() >= nextCardTick && data.cardChoices.stream().noneMatch(choice -> choice.selected)
                && button - 4 < data.cardChoices.size()) {
                nextCardTick = owner.level().getGameTime() + 7; // Source 333ms, now deterministic server ticks.
                var choice = data.cardChoices.get(button - 4); var card = choice.card;
                int[] cost = TheorycraftInventory.payment(owner.getInventory().items, card.getRequiredItems(), card.getRequiredItemsConsumed());
                if (card.getInspirationCost() <= data.inspiration && card.requirementsResolved() && cost != null) {
                    // Source pays inventory before activate; only genuine carried stacks can reach it.
                    for (int slot = 0; slot < cost.length; slot++) if (cost[slot] > 0) owner.getInventory().removeItem(slot, cost[slot]);
                    if (card.activate(owner, data)) {
                        tile.consumeInkFromTable(); choice.selected = true; data.addInspiration(-card.getInspirationCost()); tile.setChanged(); applied = true;
                    }
                }
            } else if (button == 7 && data.isComplete()) { tile.finishTheory(owner); applied = true; }
            else if (button == 9 && !data.isComplete()) { tile.data = null; tile.setChanged(); applied = true; }
        }
        sendTheoryState(owner, tile); broadcastChanges(); owner.getInventory().setChanged();
        return applied;
    }

    static boolean validSlot(int slot, ItemStack stack) {
        return switch (slot) {
            case 0 -> !stack.isEmpty() && stack.getItem() instanceof IScribeTools;
            case 1 -> !stack.isEmpty() && stack.is(Items.PAPER) && LegacyScanIdentity.rawDamage(stack) == 0;
            default -> false;
        };
    }

    @Override public boolean stillValid(Player player) { return table.stillValid(player); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem(), result = original.copy();
        if (index < 2) {
            if (!validSlot(index, original) || !moveItemStackTo(original, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            // Do not 'fix' source isItemValidForSlot(playerMenuIndex,...): all index>=2 fail.
            if (!validSlot(index, original) || !moveItemStackTo(original, 0, 2, false)) return ItemStack.EMPTY;
        }
        if (original.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override public void removed(Player player) { super.removed(player); table.stopOpen(player); }

    @Override public boolean clickMenuButton(Player player, int button) { return false; } // Unrevisioned vanilla buttons cannot bypass the checked request route.
}
