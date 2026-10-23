package thaumcraft.common.container;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.event.EventHooks;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchCosts;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchRecipes;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchTransfer;
import thaumcraft.common.lib.network.misc.PacketArcaneRecipeTransfer;
import thaumcraft.common.tiles.crafting.ArcaneWorkbenchRegistries;
import thaumcraft.common.tiles.crafting.TileArcaneWorkbench;

/** Source slot geometry/ordering, real synchronized preview and persistent table inputs.
 * Security adaptation: output clicks pay a freshly reselected recipe before delivery/callbacks,
 * not an old client/result-stack promise. Player crafting callbacks receive a captured source
 * 15-slot matrix after committed costs, so listener reentry cannot manufacture a second output.
 * No C2S costs/output/research grants. Recipe-transfer IDs are resolved in the live server pool.
 */
public final class ContainerArcaneWorkbench extends AbstractContainerMenu {
    private final Container table;
    private final Player viewer;
    private final SimpleContainer result = new SimpleContainer(1);
    private int availableVis;
    private boolean committing;
    private long lastTransferTick = Long.MIN_VALUE;

    public ContainerArcaneWorkbench(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(15)); }
    public ContainerArcaneWorkbench(int id, Inventory inventory, Container table) {
        super(ArcaneWorkbenchRegistries.ARCANE_WORKBENCH_MENU, id);
        this.table = table; this.viewer = inventory.player; checkContainerSize(table, 15); table.startOpen(viewer);
        addSlot(new Slot(result, 0, 160, 64) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; } // All output actions below are transactional.
            @Override public ItemStack remove(int amount) { return ItemStack.EMPTY; }
            @Override public boolean isFake() { return true; }
        });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++)
            addSlot(new Slot(table, column + row * 3, 40 + column * 24, 40 + row * 24));
        for (int index = 0; index < 6; index++) {
            final int aspect = index;
            addSlot(new Slot(table, 9 + index, ArcaneWorkbenchCosts.X[index], ArcaneWorkbenchCosts.Y[index]) {
                @Override public boolean mayPlace(ItemStack stack) { return ArcaneWorkbenchCosts.crystal(stack, aspect); }
            });
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column + row * 9 + 9, 16 + column * 18, 151 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 16 + column * 18, 209));
        // Vanilla menu properties carry unsigned halves; charger 3x3 totals need >16 bits.
        addDataSlot(new DataSlot() {
            @Override public int get() { return availableVis & 65535; }
            @Override public void set(int value) { availableVis = (availableVis & 0xFFFF0000) | (value & 65535); }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return availableVis >>> 16; }
            @Override public void set(int value) { availableVis = (availableVis & 65535) | (value << 16); }
        });
        refresh();
    }
    public Container table() { return table; }
    public int availableVis() { return availableVis; }
    public IArcaneRecipe displayedArcaneRecipe() {
        var holder = arcane(positionedInput().input());
        return holder == null ? null : holder.value();
    }
    private RecipeHolder<IArcaneRecipe> arcane(CraftingInput input) {
        for (var candidate : viewer.level().getRecipeManager().getAllRecipesFor(ArcaneWorkbenchRecipes.TYPE))
            if (candidate.value().matches(input, viewer.level()) && ArcaneWorkbenchCosts.hasCrystals(table, candidate.value())) return candidate;
        return null;
    }
    private CraftingInput.Positioned positionedInput() {
        List<ItemStack> inputs = new ArrayList<>(9);
        for (int index = 0; index < 9; index++) inputs.add(table.getItem(index).copy());
        return CraftingInput.ofPositioned(3, 3, inputs);
    }
    private Selection select() {
        CraftingInput.Positioned positioned = positionedInput();
        if (positioned.input().isEmpty()) return null;
        var arcane = arcane(positioned.input());
        if (arcane != null) {
            IArcaneRecipe recipe = arcane.value();
            if (!ThaumcraftCapabilities.getKnowledge(viewer).isResearchKnown(recipe.research()) || !permitted(arcane)) return null;
            int cost = ArcaneWorkbenchCosts.visCost(recipe.vis(), ArcaneWorkbenchCosts.discount(viewer));
            return new Selection(arcane, recipe, positioned, cost, recipe);
        }
        // Source first tries an arcane match including crystal costs; otherwise an actual vanilla recipe.
        var vanilla = viewer.level().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, positioned.input(), viewer.level()).orElse(null);
        return vanilla == null || !permitted(vanilla) ? null : new Selection(vanilla, vanilla.value(), positioned, 0, null);
    }
    private boolean permitted(RecipeHolder<?> holder) {
        return holder.value().isSpecial() || !viewer.level().getGameRules().getBoolean(GameRules.RULE_LIMITED_CRAFTING)
            || viewer instanceof ServerPlayer owner && owner.getRecipeBook().contains(holder);
    }
    private boolean context(Player player) {
        return player == viewer && player instanceof ServerPlayer owner && owner.containerMenu == this && !player.isRemoved()
            && player.isAlive() && table instanceof TileArcaneWorkbench && table.stillValid(player)
            && owner.getServer().isSameThread();
    }
    /** Arranges only existing inputs. Crafting still uses takeCraft's research/aura/payment path.
     * The packet contains no desired stacks. Resolve the live recipe, plan on copied inventories,
     * then recheck extension callbacks and commit all 51 slots on the owning server thread.
     */
    public boolean transferRecipe(ServerPlayer owner, PacketArcaneRecipeTransfer packet) {
        if (packet == null || committing || !context(owner) || packet.menuId() != containerId || packet.stateId() != getStateId()
            || !getCarried().isEmpty()) return false;
        long tick = owner.level().getGameTime();
        if (lastTransferTick == tick) return false; // At most one bounded planning request per open menu per server tick.
        lastTransferTick = tick;
        var holder = owner.level().getRecipeManager().byKey(packet.recipeId()).orElse(null);
        if (holder == null || !(holder.value() instanceof IArcaneRecipe recipe) || recipe.getType() != ArcaneWorkbenchRecipes.TYPE
            || !ArcaneWorkbenchTransfer.supported(recipe) || !ThaumcraftCapabilities.getKnowledge(owner).isResearchKnown(recipe.research())
            || !permitted(holder)) return false;
        var snapshot = ArcaneWorkbenchTransfer.snapshot(this);
        var plan = ArcaneWorkbenchTransfer.plan(recipe, snapshot, packet.maximum());
        if (!plan.successful()) return false;
        SimpleContainer planned = new SimpleContainer(15);
        for (int slot = 0; slot < 15; slot++) {
            ItemStack stack = plan.table().get(slot);
            Slot destination = slots.get(1 + slot);
            if (!stack.isEmpty() && (!stack.isItemEnabled(owner.level().enabledFeatures()) || !destination.mayPlace(stack)
                || stack.getCount() > destination.getMaxStackSize(stack))) return false;
            if (!ItemStack.matches(stack, snapshot.table().get(slot)) && !destination.mayPickup(owner)) return false;
            planned.setItem(slot, stack.copy());
        }
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = plan.inventory().get(slot);
            Slot destination = slots.get(16 + slot);
            if (!stack.isEmpty() && (!destination.mayPlace(stack) || stack.getCount() > destination.getMaxStackSize(stack))) return false;
            if (!ItemStack.matches(stack, snapshot.inventory().get(slot)) && !destination.mayPickup(owner)) return false;
        }
        CraftingInput input = CraftingInput.ofPositioned(3, 3, plan.table().subList(0, 9).stream().map(ItemStack::copy).toList()).input();
        if (!recipe.matches(input, owner.level()) || !ArcaneWorkbenchCosts.hasCrystals(planned, recipe)) return false;
        // Ingredient/recipe predicates are addon callbacks: their plan is only a proposal until this final check.
        var current = owner.level().getRecipeManager().byKey(packet.recipeId()).orElse(null);
        if (current == null || current.value() != recipe || !context(owner) || packet.stateId() != getStateId() || !getCarried().isEmpty()
            || !snapshot.matches(this) || !ThaumcraftCapabilities.getKnowledge(owner).isResearchKnown(recipe.research()) || !permitted(current)) return false;
        committing = true;
        try {
            for (int slot = 0; slot < 15; slot++) table.setItem(slot, plan.table().get(slot).copy());
            // These are the menu's ordinary main-inventory/hotbar slots, never armor or offhand.
            for (int slot = 0; slot < 36; slot++) {
                int inventorySlot = slot < 27 ? slot + 9 : slot - 27;
                owner.getInventory().setItem(inventorySlot, plan.inventory().get(slot).copy());
            }
            table.setChanged(); owner.getInventory().setChanged();
        } finally { committing = false; }
        broadcastChanges();
        return true;
    }
    private ItemStack output(Selection selection) {
        if (selection == null) return ItemStack.EMPTY;
        ItemStack stack = selection.recipe.assemble(selection.positioned.input(), viewer.registryAccess());
        return stack.isEmpty() || !stack.isItemEnabled(viewer.level().enabledFeatures())
            || stack.getCount() > stack.getMaxStackSize() ? ItemStack.EMPTY : stack;
    }
    private void refresh() {
        if (committing || !(table instanceof TileArcaneWorkbench tile) || viewer.level().isClientSide) return;
        availableVis = tile.availableVis();
        Selection selected = select();
        result.setItem(0, selected != null && (selected.arcane == null || availableVis >= selected.cost) ? output(selected) : ItemStack.EMPTY);
    }
    @Override public void slotsChanged(Container changed) { refresh(); super.slotsChanged(changed); }
    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }
    @Override public boolean stillValid(Player player) { return player == viewer && table.stillValid(player); }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slot.index != 0 && super.canTakeItemForPickAll(stack, slot); }
    @Override public boolean canDragTo(Slot slot) { return slot.index != 0 && super.canDragTo(slot); }

    private boolean fits(ItemStack existing, ItemStack added) {
        return existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, added) && existing.getCount() + added.getCount() <= existing.getMaxStackSize();
    }
    private boolean inventoryFits(ItemStack output) {
        int count = output.getCount();
        for (int index = 51; index >= 16; index--) {
            Slot slot = slots.get(index); ItemStack stack = slot.getItem();
            if (!slot.mayPlace(output)) continue;
            if (stack.isEmpty()) count -= slot.getMaxStackSize(output);
            else if (ItemStack.isSameItemSameComponents(stack, output)) count -= Math.max(0, slot.getMaxStackSize(output) - stack.getCount());
            if (count <= 0) return true;
        }
        return false;
    }

    /** Commits one entire recipe output; no partial or preview-only batch can bypass payment. */
    private ItemStack takeCraft(Player player, ItemStack expected) {
        if (committing || !context(player)) return ItemStack.EMPTY;
        Selection selected = select(); ItemStack made = output(selected);
        if (selected == null || made.isEmpty() || !ItemStack.matches(made, expected)) { refresh(); return ItemStack.EMPTY; }
        TileArcaneWorkbench tile = (TileArcaneWorkbench) table;
        if (selected.arcane != null && tile.availableVis() < selected.cost) { refresh(); return ItemStack.EMPTY; }
        SimpleContainer sourceMatrix = new SimpleContainer(15);
        for (int index = 0; index < 15; index++) sourceMatrix.setItem(index, table.getItem(index).copy());
        committing = true;
        try {
            NonNullList<ItemStack> remainders = selected.recipe.getRemainingItems(selected.positioned.input());
            // Remainder callbacks are extension points: reselect after them before paying any cost.
            Selection now = select();
            if (now == null || now.recipe != selected.recipe || !now.holder.id().equals(selected.holder.id()) || !ItemStack.matches(output(now), made)
                || now.cost != selected.cost || !sameMatrix(sourceMatrix) || !context(player)) return ItemStack.EMPTY;
            if (selected.arcane != null && Boolean.getBoolean(thaumcraft.common.lib.validation.TcpAlchemyAuditIO.ENABLE_PROPERTY))
                thaumcraft.common.lib.validation.TcpAlchemyAudit.observeArcanePaymentBefore((ServerPlayer)player, this, made, selected.cost);
            if (selected.arcane != null && Boolean.getBoolean(thaumcraft.common.lib.validation.TcpCasterAuditIO.ENABLE_PROPERTY))
                thaumcraft.common.lib.validation.TcpCasterAudit.observeArcanePaymentBefore((ServerPlayer)player, this, made, selected.cost);
            if (selected.arcane != null && !tile.spendVis(selected.cost)) return ItemStack.EMPTY;
            // Source decrements every same-aspect crystal stack among its six dedicated slots.
            if (selected.arcane != null) for (int aspect = 0; aspect < 6; aspect++) {
                int count = selected.arcane.crystals().get(aspect);
                if (count > 0) for (int index = 9; index < 15; index++) if (ArcaneWorkbenchCosts.crystal(table.getItem(index), aspect)) table.removeItem(index, count);
            }
            CraftingInput input = selected.positioned.input();
            for (int row = 0; row < input.height(); row++) for (int column = 0; column < input.width(); column++) {
                int local = column + row * input.width();
                int index = column + selected.positioned.left() + (row + selected.positioned.top()) * 3;
                if (!table.getItem(index).isEmpty()) table.removeItem(index, 1);
                ItemStack remainder = local < remainders.size() ? remainders.get(local).copy() : ItemStack.EMPTY;
                if (remainder.isEmpty()) continue;
                ItemStack remaining = table.getItem(index);
                if (remaining.isEmpty()) table.setItem(index, remainder);
                else if (ItemStack.isSameItemSameComponents(remaining, remainder) && remaining.getCount() + remainder.getCount() <= remaining.getMaxStackSize()) {
                    remainder.grow(remaining.getCount()); table.setItem(index, remainder);
                } else if (!player.getInventory().add(remainder)) player.drop(remainder, false);
            }
            made.onCraftedBy(player.level(), player, made.getCount());
            EventHooks.firePlayerCraftingEvent(player, made.copy(), sourceMatrix);
            if (player instanceof ServerPlayer owner) {
                owner.triggerRecipeCrafted(selected.holder, sourceMatrixItems(sourceMatrix));
                if (!selected.recipe.isSpecial()) owner.awardRecipes(List.of(selected.holder));
            }
            table.setChanged(); player.getInventory().setChanged();
            return made;
        } finally { committing = false; refresh(); }
    }
    private boolean sameMatrix(Container captured) {
        for (int index = 0; index < 15; index++) if (!ItemStack.matches(captured.getItem(index), table.getItem(index))) return false;
        return true;
    }
    private static List<ItemStack> sourceMatrixItems(Container captured) {
        List<ItemStack> items = new ArrayList<>(15);
        for (int index = 0; index < 15; index++) items.add(captured.getItem(index));
        return items;
    }
    @Override public void clicked(int index, int button, ClickType type, Player player) {
        if (player != viewer || !viewer.level().isClientSide && !context(player)) return;
        if (index != 0) { super.clicked(index, button, type, player); refresh(); return; }
        if (viewer.level().isClientSide || !context(player) || committing) return;
        refresh(); ItemStack preview = result.getItem(0).copy();
        if (preview.isEmpty()) return;
        if (type == ClickType.QUICK_MOVE) {
            for (int attempt = 0; attempt < 64; attempt++) {
                if (!ItemStack.isSameItemSameComponents(preview, result.getItem(0)) || quickMoveStack(player, 0).isEmpty()) break;
            }
        }
        else if (type == ClickType.PICKUP && (button == 0 || button == 1) && fits(getCarried(), preview)) {
            ItemStack made = takeCraft(player, preview);
            if (!made.isEmpty()) {
                if (!fits(getCarried(), made)) { if (!player.getInventory().add(made)) player.drop(made, false); }
                else if (getCarried().isEmpty()) setCarried(made); else getCarried().grow(made.getCount());
            }
        } else if (type == ClickType.SWAP && (button >= 0 && button < 9 || button == 40) && fits(player.getInventory().getItem(button), preview)) {
            ItemStack made = takeCraft(player, preview);
            if (!made.isEmpty()) {
                ItemStack old = player.getInventory().getItem(button);
                if (!fits(old, made)) { if (!player.getInventory().add(made)) player.drop(made, false); }
                else if (old.isEmpty()) player.getInventory().setItem(button, made); else old.grow(made.getCount());
            }
        } else if (type == ClickType.THROW && (button == 0 || button == 1) && getCarried().isEmpty()) {
            ItemStack made = takeCraft(player, preview); if (!made.isEmpty()) player.drop(made, true);
        } else if (type == ClickType.CLONE && player.getAbilities().instabuild && getCarried().isEmpty()) {
            setCarried(preview.copyWithCount(preview.getMaxStackSize())); // Native creative pick-block permission only.
        }
        broadcastChanges();
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (player != viewer || !viewer.level().isClientSide && !context(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        if (index == 0) {
            if (!context(player) || committing) return ItemStack.EMPTY;
            refresh(); ItemStack preview = result.getItem(0).copy();
            if (preview.isEmpty() || !inventoryFits(preview)) return ItemStack.EMPTY;
            ItemStack made = takeCraft(player, preview); if (made.isEmpty()) return ItemStack.EMPTY;
            ItemStack returned = made.copy();
            if (!moveItemStackTo(made, 16, 52, true) || !made.isEmpty()) player.drop(made, false); // Callback-altered inventory never loses an already paid output.
            broadcastChanges(); return returned;
        }
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index >= 16) {
            boolean crystal = false;
            for (int aspect = 0; aspect < 6; aspect++) if (ArcaneWorkbenchCosts.crystal(stack, aspect)) {
                crystal = moveItemStackTo(stack, 10 + aspect, 11 + aspect, false); break;
            }
            if (!stack.isEmpty() && !(index < 43 ? moveItemStackTo(stack, 43, 52, false) : moveItemStackTo(stack, 16, 43, false)) && !crystal) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 16, 52, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack); refresh(); return original;
    }
    @Override public void removed(Player player) { super.removed(player); table.stopOpen(player); } // NEVER drops/returns the persisted grid on close.
    private record Selection(RecipeHolder<?> holder, Recipe<CraftingInput> recipe, CraftingInput.Positioned positioned, int cost, IArcaneRecipe arcane) {}
}
