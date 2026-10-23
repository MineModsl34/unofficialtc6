package thaumcraft.common.golems;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import thaumcraft.common.golems.seals.SealInventoryOps;

/** Source preference: one complete component from DOWN/horizontal inventories, otherwise the
 * complete component from player's main inventory. Does not silently combine partial pools.
 * Actual native capabilities are preflighted, then extracted once; changed/reentrant handlers
 * abort and return every ACTUAL extracted stack, never a reconstructed recipe stack. */
public final class GolemBuilderPayment {
    private static final SealInventoryOps.Filter ADJACENT = new SealInventoryOps.Filter(false,false,true,false);
    private static final SealInventoryOps.Filter PLAYER = new SealInventoryOps.Filter(false,true,true,false);
    private record Debit(IItemHandler inventory,int slot,ItemStack expected,int amount) {}
    private record Paid(IItemHandler inventory,ItemStack stack) {}
    private GolemBuilderPayment() {}
    private static List<IItemHandler> adjacent(Level level,BlockPos pos) {
        var out = new ArrayList<IItemHandler>();
        for (Direction dir : Direction.values()) if (dir != Direction.UP) {
            var inventory = SealInventoryOps.getItemHandlerAt(level,pos.relative(dir),dir.getOpposite());
            if (inventory != null && inventory.getSlots() > 0 && inventory.getSlots() <= 4096) out.add(inventory);
        }
        return out;
    }
    private static boolean reserve(List<IItemHandler> inventories,ItemStack required,SealInventoryOps.Filter filter,
        IdentityHashMap<IItemHandler,Map<Integer,Integer>> used,List<Debit> plan) {
        int remaining = required.getCount(); var attempt = new ArrayList<Debit>();
        for (var inventory : inventories) for (int slot = 0; slot < inventory.getSlots() && remaining > 0; slot++) {
            var actual = inventory.getStackInSlot(slot); if (!SealInventoryOps.areItemStacksEqual(actual,required,filter)) continue;
            int reserved = used.getOrDefault(inventory,Map.of()).getOrDefault(slot,0);
            int amount = Math.min(remaining,Math.max(0,actual.getCount() - reserved)); if (amount == 0) continue;
            var simulated = inventory.extractItem(slot,amount,true);
            if (simulated.getCount() != amount || !ItemStack.isSameItemSameComponents(actual,simulated)) continue;
            attempt.add(new Debit(inventory,slot,actual.copy(),amount)); remaining -= amount;
        }
        if (remaining > 0) return false;
        for (var debit : attempt) { used.computeIfAbsent(debit.inventory(),key -> new LinkedHashMap<>()).merge(debit.slot(),debit.amount(),Integer::sum); plan.add(debit); }
        return true;
    }
    private static List<Debit> plan(Level world,BlockPos pos,Player player,ItemStack[] components) {
        if (components.length > 64) return null;
        var adjacent = adjacent(world,pos); var main = new PlayerMainInvWrapper(player.getInventory());
        var used = new IdentityHashMap<IItemHandler,Map<Integer,Integer>>(); var result = new ArrayList<Debit>();
        for (var required : components) {
            if (required.isEmpty() || required.getCount() < 1 || required.getCount() > 4096) return null;
            if (!reserve(adjacent,required,ADJACENT,used,result) && !reserve(List.of(main),required,PLAYER,used,result)) return null;
        }
        return result;
    }
    public static boolean[] availability(Level world,BlockPos pos,Player player,ItemStack[] components) {
        boolean[] out = new boolean[Math.min(64,components.length)];
        for (int i = 0; i < out.length; i++) {
            try { out[i] = plan(world,pos,player,new ItemStack[]{components[i]}) != null; }
            catch (RuntimeException brokenSimulation) { out[i] = false; }
        }
        return out;
    }
    public static boolean pay(Level world,BlockPos pos,Player player,ItemStack[] components) {
        final List<Debit> plan;
        try { plan = plan(world,pos,player,components); }
        catch (RuntimeException brokenSimulation) { return false; }
        if (plan == null) return false;
        var paid = new ArrayList<Paid>();
        try { for (var debit : plan) {
            var actual = debit.inventory().getStackInSlot(debit.slot());
            if (actual.getCount() < debit.amount() || !ItemStack.isSameItemSameComponents(actual,debit.expected())) { rollback(paid,player); return false; }
            var extracted = debit.inventory().extractItem(debit.slot(),debit.amount(),false);
            if (!extracted.isEmpty()) paid.add(new Paid(debit.inventory(),extracted.copy()));
            if (extracted.getCount() != debit.amount() || !ItemStack.isSameItemSameComponents(extracted,debit.expected())) { rollback(paid,player); return false; }
        } } catch (RuntimeException brokenHandler) { rollback(paid,player); return false; }
        player.getInventory().setChanged(); return true;
    }
    private static void rollback(List<Paid> paid,Player player) {
        for (var entry : paid) {
            final ItemStack remaining;
            try { remaining = ItemHandlerHelper.insertItemStacked(entry.inventory(),entry.stack(),false); }
            catch (RuntimeException brokenHandler) {
                // Unknown mutate-before-throw cannot be recovered generically without fabricating/duplicating
                // items. No second insertion/drop is attempted after that unknown partial mutation.
                thaumcraft.Thaumcraft.log.warn("Golem builder external capability threw while returning known extracted items; cannot safely reconstruct an unknown partial insertion",brokenHandler);
                continue;
            }
            if (!remaining.isEmpty() && !player.getInventory().add(remaining)) SealInventoryOps.dropItemAtEntity(player.level(),remaining,player);
        }
    }
}
