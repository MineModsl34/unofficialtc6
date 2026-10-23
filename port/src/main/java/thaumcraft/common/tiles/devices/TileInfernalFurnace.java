package thaumcraft.common.tiles.devices;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.internal.CommonInternals;
import thaumcraft.common.blocks.devices.BlockBellows;
import thaumcraft.common.blocks.devices.BlockInfernalFurnace;
import thaumcraft.common.lib.crafting.ProcessingInventory;
import thaumcraft.common.lib.crafting.ProcessingRules;
import thaumcraft.common.lib.crafting.SourceSmeltingBonusCatalog;

/** Real 32-slot source furnace; no fuel, input from above, automatic output and experience. */
public final class TileInfernalFurnace extends BlockEntity implements WorldlyContainer {
    private final NonNullList<ItemStack> items = NonNullList.withSize(32,ItemStack.EMPTY);
    public int furnaceCookTime,furnaceMaxCookTime,speedyTime;
    public TileInfernalFurnace(BlockPos pos,BlockState state) { super(ProcessingRegistries.INFERNAL_FURNACE,pos,state); }
    private Direction exit() { return getBlockState().getValue(BlockInfernalFurnace.FACING).getOpposite(); }
    private RecipeHolder<SmeltingRecipe> recipe(ItemStack stack) {
        return stack.isEmpty() || level == null ? null : level.getRecipeManager().getRecipeFor(RecipeType.SMELTING,new SingleRecipeInput(stack),level).orElse(null);
    }
    public boolean canSmelt(ItemStack stack) { var recipe = recipe(stack); return recipe != null && !recipe.value().getResultItem(level.registryAccess()).isEmpty(); }
    public int getBellows() {
        int count = 0;
        for (Direction dir : Direction.values()) if (dir != Direction.UP) {
            BlockPos pos = worldPosition.relative(dir,2);
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (level.getBlockEntity(pos) instanceof TileBellows && state.hasProperty(BlockBellows.FACING)
                && state.getValue(BlockBellows.FACING) == dir.getOpposite() && !level.hasNeighborSignal(pos)) count++;
        }
        return Math.min(4,count);
    }
    public int calcCookTime() { return ProcessingRules.infernalCookTime(speedyTime > 0,getBellows()); }
    public static void tick(Level level,BlockPos pos,BlockState state,TileInfernalFurnace tile) {
        if (level.isClientSide) return;
        boolean cooked = false,dirty = false;
        if (tile.furnaceCookTime > 0) { tile.furnaceCookTime--; cooked = true; dirty = true; }
        if (tile.furnaceMaxCookTime <= 0) tile.furnaceMaxCookTime = tile.calcCookTime();
        if (tile.furnaceCookTime > tile.furnaceMaxCookTime) tile.furnaceCookTime = tile.furnaceMaxCookTime;
        if (tile.furnaceCookTime <= 0 && cooked) for (int slot = 0; slot < tile.items.size(); slot++) {
            ItemStack input = tile.items.get(slot);
            if (input.isEmpty()) continue;
            var recipe = tile.recipe(input);
            if (recipe != null) {
                ItemStack output = recipe.value().getResultItem(level.registryAccess());
                if (!output.isEmpty()) {
                    if (tile.speedyTime > 0) tile.speedyTime--;
                    tile.ejectItem(output.copy(),input,recipe.value().getExperience());
                    level.blockEvent(pos,state.getBlock(),3,0);
                    if (level.random.nextInt(20) == 0) AuraHelper.polluteAura(level,pos.relative(tile.exit()),1,true);
                    input.shrink(1);
                    break;
                }
            }
            tile.items.set(slot,ItemStack.EMPTY); // Source destroys invalid inputs queued by automation.
        }
        if (tile.speedyTime <= 0) {
            int vis = (int)AuraHelper.drainVis(level,pos,20,false);
            if (tile.speedyTime != vis) { tile.speedyTime = vis; dirty = true; }
        }
        if (tile.furnaceCookTime == 0 && !cooked) for (ItemStack input : tile.items) if (tile.canSmelt(input)) {
            tile.furnaceMaxCookTime = tile.calcCookTime(); tile.furnaceCookTime = tile.furnaceMaxCookTime; dirty = true; break;
        }
        if (dirty) tile.setChanged();
    }
    public ItemStack addItemsToInventory(ItemStack stack) {
        if (level == null || level.isClientSide) return stack;
        if (canSmelt(stack)) return ItemHandlerHelper.insertItem(new SidedInvWrapper(this,Direction.UP),stack,false);
        level.playSound(null,worldPosition,SoundEvents.LAVA_EXTINGUISH,SoundSource.BLOCKS,.3F,2.6F + (level.random.nextFloat() - level.random.nextFloat()) * .8F);
        if (level instanceof net.minecraft.server.level.ServerLevel server) server.sendParticles(ParticleTypes.LAVA,worldPosition.getX() + level.random.nextFloat(),worldPosition.getY() + 1,worldPosition.getZ() + level.random.nextFloat(),1,0,0,0,0);
        return ItemStack.EMPTY;
    }
    /** One chance trial per matching logical source row, repeated 1+bellows times, ordered. */
    public ItemStack[] getSmeltingBonus(ItemStack input) {
        ArrayList<ItemStack> result = new ArrayList<>();
        for (var bonus : CommonInternals.smeltingBonus) if (SourceSmeltingBonusCatalog.matches(input,bonus)
            && ProcessingRules.bonus(level.random.nextFloat(),bonus.chance)) {
            ItemStack output = bonus.out.copy();
            if (output.getCount() < 1) output.setCount(1);
            result.add(output);
        }
        return result.toArray(ItemStack[]::new);
    }
    public void ejectItem(ItemStack output,ItemStack input,float experience) {
        if (output.isEmpty() || level == null || level.isClientSide) return;
        ProcessingInventory.eject(level,worldPosition,exit(),output);
        for (int repeat = 0; repeat < getBellows() + 1; repeat++) for (ItemStack bonus : getSmeltingBonus(input))
            if (!bonus.isEmpty() && bonus.getCount() > 0) ProcessingInventory.eject(level,worldPosition,exit(),bonus);
        int xp = ProcessingRules.experience(output.getCount(),experience,Math::random);
        while (xp > 0) {
            int split = ExperienceOrb.getExperienceValue(xp); xp -= split;
            ExperienceOrb orb = new ExperienceOrb(level,worldPosition.getX() + .5 + exit().getStepX() * 1.2,
                worldPosition.getY() + .4,worldPosition.getZ() + .5 + exit().getStepZ() * 1.2,split);
            orb.setDeltaMovement(exit().getStepX() == 0 ? (level.random.nextFloat() - level.random.nextFloat()) * .025 : exit().getStepX() * .13,0,
                exit().getStepZ() == 0 ? (level.random.nextFloat() - level.random.nextFloat()) * .025 : exit().getStepZ() * .13);
            level.addFreshEntity(orb);
        }
    }
    public void ejectItem(ItemStack output,ItemStack input) { var recipe = recipe(input); ejectItem(output,input,recipe == null ? 0 : recipe.value().getExperience()); }
    @Override public boolean triggerEvent(int event,int parameter) {
        if (event != 3) return super.triggerEvent(event,parameter);
        if (level != null && level.isClientSide) {
            thaumcraft.common.lib.fx.SourceFX.local(level,"infernal_lava",net.minecraft.world.phys.Vec3.atLowerCornerOf(worldPosition),net.minecraft.world.phys.Vec3.ZERO,0,exit().ordinal());
        }
        return true;
    }
    @Override public int getContainerSize() { return 32; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot,int amount) { ItemStack result = ContainerHelper.removeItem(items,slot,amount); if (!result.isEmpty()) setChanged(); return result; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items,slot); }
    @Override public void setItem(int slot,ItemStack stack) { items.set(slot,stack); stack.limitSize(getMaxStackSize()); setChanged(); }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getCenter()) <= 64; }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public int[] getSlotsForFace(Direction side) { return side == Direction.UP ? java.util.stream.IntStream.range(0,32).toArray() : new int[0]; }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side) { return side == Direction.UP; }
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side) { return false; }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) { super.saveAdditional(tag,registries); ContainerHelper.saveAllItems(tag,items,registries); tag.putShort("CookTime",(short)furnaceCookTime); tag.putShort("SpeedyTime",(short)speedyTime); }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) { super.loadAdditional(tag,registries); items.clear(); ContainerHelper.loadAllItems(tag,items,registries); furnaceCookTime = tag.getShort("CookTime"); speedyTime = tag.getShort("SpeedyTime"); furnaceMaxCookTime = 0; }
}
