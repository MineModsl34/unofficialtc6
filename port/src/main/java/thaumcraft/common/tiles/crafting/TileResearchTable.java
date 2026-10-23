package thaumcraft.common.tiles.crafting;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import thaumcraft.api.items.IScribeTools;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.theorycraft.ITheorycraftAid;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftManager;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.research.theorycraft.TheorycraftAids;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import thaumcraft.common.container.ContainerResearchTable;
import thaumcraft.common.lib.research.LegacyScanIdentity;

/** Source two-slot physical storage and persisted, server-owned theorycraft note.
 * Items use vanilla BE/update/menu transport instead of the old arbitrary tile-NBT packets.
 * Getters remain ordinary live Container stacks; setItem copies external inputs and source
 * conversion separately copies the FULL held stack. Invalid command/NBT contents are not
 * silently removed; only normal slot/automation insertion is filtered like the source. */
public final class TileResearchTable extends BlockEntity implements WorldlyContainer, MenuProvider {
    private NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private final EnumMap<Direction, IItemHandler> handlers = new EnumMap<>(Direction.class);
    @Nullable private String customName; // Retain source storage; its table name ignored customName.
    @Nullable public ResearchTableData data;
    private boolean renderNote;
    private long theoryRevision;

    public long theoryRevision() { return theoryRevision; }
    /** Only presence is public for the source scroll BER; private note contents stay owner-routed. */
    public boolean hasTheoryNote() { return level != null && level.isClientSide ? renderNote : data != null; }
    public boolean hasInk() { ItemStack stack = getItem(0); return !stack.isEmpty() && stack.getItem() instanceof IScribeTools && stack.getDamageValue() < stack.getMaxDamage(); }
    public boolean hasPaper() { ItemStack stack = getItem(1); return stack.is(Items.PAPER) && stack.getCount() > 0; }
    public boolean consumeInkFromTable() {
        if (!hasInk()) return false;
        getItem(0).setDamageValue(getItem(0).getDamageValue() + 1); setChanged(); return true;
    }
    public boolean consumepaperFromTable() { if (!hasPaper()) return false; removeItem(1, 1); return true; }
    public void startNewTheory(Player player, Set<String> aids) { data = new ResearchTableData(player, this); data.initialize(player, aids); setChanged(); }
    public void finishTheory(Player player) {
        if (data == null || !data.isComplete() || !data.ownedBy(player)) return;
        LinkedHashMap<String, Integer> sorted = new LinkedHashMap<>();
        data.categoryTotals.entrySet().stream().sorted((a, b) -> b.getValue().compareTo(a.getValue())).forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        int index = 0;
        for (var entry : sorted.entrySet()) {
            int total = theoryAward(entry.getValue(), index++, data.penaltyStart);
            ResearchManager.addKnowledge(player, EnumKnowledgeType.THEORY, ResearchCategories.getResearchCategory(entry.getKey()), total);
        }
        data = null; setChanged();
    }
    public static int theoryAward(int percentage, int index, int penaltyStart) {
        int total = Math.round(percentage / 100.0F * EnumKnowledgeType.THEORY.getProgression());
        return index > penaltyStart ? (int)Math.max(1.0, total * .666666667) : total;
    }
    public Set<String> checkSurroundingAids() {
        Set<String> found = new HashSet<>(); if (level == null) return found;
        for (int y = -1; y <= 1; y++) for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            BlockPos pos = worldPosition.offset(x, y, z);
            if (!level.hasChunkAt(pos)) continue;
            Block block = level.getBlockState(pos).getBlock();
            for (var entry : TheorycraftManager.aids.entrySet()) {
                Object object = entry.getValue().getAidObject();
                if (object instanceof Block aid && aid == block) found.add(entry.getKey());
                else if (object instanceof ItemStack aid && !aid.isEmpty() && block.asItem() == aid.getItem()) found.add(entry.getKey());
            }
        }
        double cx = worldPosition.getX() + .5, cy = worldPosition.getY() + .5, cz = worldPosition.getZ() + .5;
        for (var entity : level.getEntities(null, new AABB(cx, cy, cz, cx, cy, cz).inflate(5))) {
            for (var entry : TheorycraftManager.aids.entrySet()) {
                ITheorycraftAid aid = entry.getValue(); Object object = aid.getAidObject();
                // Preserve TC6's reversed class-assignability for addon Class aids.
                if (object instanceof Class<?> type && entity.getClass().isAssignableFrom(type)) found.add(entry.getKey());
                else if (aid instanceof TheorycraftAids.Aid builtin && builtin.matchesEntity(entity)) found.add(entry.getKey());
            }
        }
        return found;
    }

    public TileResearchTable(BlockPos pos, BlockState state) {
        super(ResearchTableRegistries.RESEARCH_TABLE, pos, state);
        for (Direction direction : Direction.values()) handlers.put(direction, new SidedInvWrapper(this, direction));
    }

    @Override public int getContainerSize() { return 2; }

    @Override public int getMaxStackSize() { return 64; } // Modern Container's default is 99.

    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }

    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack result = ContainerHelper.removeItem(items, slot, count);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(items, slot);
        setChanged(); // Source removeStackFromSlot also marks/syncs the slot.
        return result;
    }

    @Override public void setItem(int slot, ItemStack stack) {
        ItemStack stored = stack.copy();
        // TileThaumcraftInventory clamps to 64, not to a tool's ordinary max stack size1.
        stored.setCount(Math.min(stored.getCount(), 64));
        items.set(slot, stored);
        setChanged();
    }

    @Override public void clearContent() {} // Source TileThaumcraftInventory.clear() was a no-op.

    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
            && player.distanceToSqr(worldPosition.getX() + .5D, worldPosition.getY() + .5D, worldPosition.getZ() + .5D) <= 64.0D;
    }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case 0 -> !stack.isEmpty() && stack.getItem() instanceof IScribeTools;
            case 1 -> !stack.isEmpty() && stack.is(Items.PAPER) && LegacyScanIdentity.rawDamage(stack) == 0;
            default -> false;
        };
    }

    @Override public int[] getSlotsForFace(Direction side) { return new int[]{0, 1}; }

    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return canPlaceItem(slot, stack); }

    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return true; }

    /** Source sided capability returned no handler for the null-direction request. */
    @Nullable public IItemHandler itemHandler(@Nullable Direction side) { return side == null ? null : handlers.get(side); }

    @Override public Component getDisplayName() { return Component.translatable("block.thaumcraft.research_table"); }

    @Override public AbstractContainerMenu createMenu(int window, Inventory inventory, Player player) {
        return new ContainerResearchTable(window, inventory, this);
    }

    @Override public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            theoryRevision = theoryRevision == Long.MAX_VALUE ? 1L : theoryRevision + 1L;
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(2, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        if (tag.contains("CustomName", 8)) customName = tag.getString("CustomName");
        theoryRevision = Math.max(0L, tag.getLong("theoryRevision"));
        renderNote = tag.getBoolean("renderNote");
        data = null;
        if (tag.contains("note", 10)) { data = new ResearchTableData(this); data.deserialize(tag.getCompound("note"), registries); }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        if (customName != null && !customName.isEmpty()) tag.putString("CustomName", customName);
        tag.putLong("theoryRevision", theoryRevision);
        if (data != null) tag.put("note", data.serialize(registries));
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag visible = saveWithoutMetadata(registries);
        // Chunk/BE updates are broadcast to everyone nearby, unlike an owner's menu.
        // Persist the FULL note on disk, but deliver its contents only through the checked
        // PacketTheorycraftState route. Public inventory/revision remain ordinary BE data.
        visible.remove("note");
        visible.putBoolean("renderNote",data != null);
        return visible;
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
