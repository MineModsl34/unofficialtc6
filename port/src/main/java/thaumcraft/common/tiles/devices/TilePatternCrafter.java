package thaumcraft.common.tiles.devices;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.*;
import thaumcraft.api.aura.AuraHelper;

/** Original ten single-material patterns, real native crafting recipes, cached vis and craft remainders. */
public final class TilePatternCrafter extends DeviceTile {
    public static final int[][] PATTERNS = DeviceRules.PATTERNS;
    public int type,rotTicks;
    public float power,rot,rp;
    private boolean crafting;
    public TilePatternCrafter(BlockPos pos,BlockState state) { super(pos,state,0); counter = net.minecraft.util.RandomSource.create().nextInt(20); }
    public void cycle() { type = (type + 1) % 10; changedAndSync(); }
    @Override public void update() {
        if (level == null) return;
        if (level.isClientSide) { if (rotTicks > 0) { rotTicks--; if (rotTicks % Math.max(1,(int)rp) == 0) level.playLocalSound(worldPosition,thaumcraft.common.lib.SoundsTC.clack,net.minecraft.sounds.SoundSource.BLOCKS,.2F,1.7F,false); rp++; } else rp *= .8F; rot += rp; return; }
        if (counter++ % 20 != 0 || !enabled() || crafting) return;
        if (power <= 0) { power += AuraHelper.drainVis(level,worldPosition,5,false); setChanged(); }
        var above = level.getCapability(Capabilities.ItemHandler.BLOCK,worldPosition.above(),Direction.DOWN); var below = level.getCapability(Capabilities.ItemHandler.BLOCK,worldPosition.below(),Direction.UP);
        if (above == null || below == null || power < 1) return;
        crafting = true;
        try { for (int slot = 0; slot < above.getSlots(); slot++) if (tryCraft(above,below,slot)) break; } finally { crafting = false; }
    }
    private boolean tryCraft(IItemHandler above,IItemHandler below,int slot) {
        ItemStack exemplar = above.getStackInSlot(slot).copy(); if (exemplar.isEmpty()) return false;
        int needed = PATTERNS[type].length; List<Integer> extractionSlots = new ArrayList<>(); List<Integer> extractionAmounts = new ArrayList<>();
        for (int i = 0; i < above.getSlots() && needed > 0; i++) { ItemStack current = above.getStackInSlot(i); if (!sameBaseOre(exemplar,current)) continue; int amount = Math.min(needed,current.getCount()); ItemStack simulated = above.extractItem(i,amount,true); if (!sameBaseOre(exemplar,simulated)) continue; amount = Math.min(amount,simulated.getCount()); if (amount > 0) { extractionSlots.add(i); extractionAmounts.add(amount); needed -= amount; } }
        if (needed != 0) return false;
        List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9,ItemStack.EMPTY)); for (int index : PATTERNS[type]) grid.set(index,exemplar.copyWithCount(1));
        CraftingInput input = CraftingInput.of(3,3,grid); var holder = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level); if (holder.isEmpty()) return false;
        ItemStack output = holder.get().value().assemble(input,level.registryAccess()); if (output.isEmpty()) return false;
        List<ItemStack> results = new ArrayList<>(); results.add(output); for (ItemStack remainder : holder.get().value().getRemainingItems(input)) if (!remainder.isEmpty()) results.add(remainder);
        if (!fitsTogether(below,results)) return false;
        // Secure adaptation: debit actual inputs before any output callback, rollback on an extraction mismatch.
        // TC6 inserted outputs before removing inputs, permitting callback re-entry and combined-remainder overflow.
        List<ItemStack> removed = new ArrayList<>();
        for (int i = 0; i < extractionSlots.size(); i++) { ItemStack actual = above.extractItem(extractionSlots.get(i),extractionAmounts.get(i),false); removed.add(actual); if (actual.getCount() != extractionAmounts.get(i) || !sameBaseOre(exemplar,actual)) { for (int j = 0; j < removed.size(); j++) { ItemStack rollback = above.insertItem(extractionSlots.get(j),removed.get(j),false); if (!rollback.isEmpty()) net.minecraft.world.Containers.dropItemStack(level,worldPosition.getX(),worldPosition.getY() + 1,worldPosition.getZ(),rollback); } return false; } }
        for (ItemStack result : results) { ItemStack remainder = ItemHandlerHelper.insertItem(below,result,false); if (!remainder.isEmpty()) net.minecraft.world.Containers.dropItemStack(level,worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),remainder); }
        power--; setChanged(); level.blockEvent(worldPosition,getBlockState().getBlock(),1,0); return true;
    }
    private static boolean sameBaseOre(ItemStack exemplar,ItemStack current) { if (current.isEmpty()) return false; if (ItemStack.isSameItemSameComponents(exemplar,current)) return true; if (thaumcraft.common.lib.research.theorycraft.TheorycraftOreEquivalence.matches(current,exemplar) || thaumcraft.common.lib.research.theorycraft.TheorycraftOreEquivalence.matches(exemplar,current)) return true; Set<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>> tags = exemplar.getTags().filter(tag -> DeviceRules.baseOreTag(tag.location().getNamespace(),tag.location().getPath())).collect(java.util.stream.Collectors.toSet()); return current.getTags().anyMatch(tags::contains); }
    private static boolean fitsTogether(IItemHandler target,List<ItemStack> results) {
        List<ItemStack> simulated = new ArrayList<>(); for (int slot = 0; slot < target.getSlots(); slot++) simulated.add(target.getStackInSlot(slot).copy());
        for (ItemStack result : results) { int remaining = result.getCount(); for (int slot = 0; slot < target.getSlots() && remaining > 0; slot++) { ItemStack existing = simulated.get(slot); if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing,result)) continue; int room = Math.min(target.getSlotLimit(slot),result.getMaxStackSize()) - existing.getCount(); int nativeAccepted = result.getCount() - target.insertItem(slot,result.copy(),true).getCount(); int accepted = Math.min(remaining,Math.min(Math.max(0,room),nativeAccepted)); if (accepted > 0) { simulated.set(slot,result.copyWithCount(existing.getCount() + accepted)); remaining -= accepted; } } if (remaining > 0) return false; }
        return true;
    }
    @Override public boolean triggerEvent(int id,int param) { if (id == 1) { if (level != null && level.isClientSide) rotTicks = 10; return true; } return super.triggerEvent(id,param); }
    @Override protected void writeDevice(CompoundTag tag,HolderLookup.Provider registries) { tag.putByte("type",(byte)type); tag.putFloat("power",power); }
    @Override protected void readDevice(CompoundTag tag,HolderLookup.Provider registries) { type = Math.floorMod(tag.getByte("type"),10); power = tag.getFloat("power"); }
}
