package thaumcraft.common.tiles.crafting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.common.blocks.crafting.BlockThaumatorium;
import thaumcraft.common.blocks.devices.BlockBrainBox;
import thaumcraft.common.container.ContainerThaumatorium;
import thaumcraft.common.lib.crafting.CrucibleRecipes;
import thaumcraft.common.lib.crafting.ProcessingInventory;
import thaumcraft.common.tiles.devices.ProcessingRegistries;
import thaumcraft.common.lib.fx.SourceFX;
import net.minecraft.world.phys.Vec3;

/** Source automatic alchemy; all recipe choices/payment stay on the actual server. */
public final class TileThaumatorium extends BlockEntity implements IAspectContainer,IEssentiaTransport,WorldlyContainer,MenuProvider {
    public static final int MAX_RECIPES = 17,MAX_VIEWS = 512;
    public record Selection(ResourceLocation id,@Nullable UUID owner,String ownerName) {}
    private final ArrayList<Selection> selected = new ArrayList<>();
    private final ArrayList<ItemStack> clientOutputs = new ArrayList<>();
    private ItemStack catalyst = ItemStack.EMPTY;
    public AspectList essentia = new AspectList();
    public int currentCraft = -1,maxRecipes = 1;
    public Aspect currentSuction;
    private int counter,venting;
    private boolean heated;
    private CrucibleRecipe currentRecipe;
    private long revision;
    public TileThaumatorium(BlockPos pos,BlockState state) { super(ProcessingRegistries.THAUMATORIUM,pos,state); }
    public List<Selection> selections() { return List.copyOf(selected); }
    public long revision() { return revision; }
    public ItemStack renderOutput(int index) {
        if (index < 0 || index >= selected.size()) return ItemStack.EMPTY;
        if (level != null && level.isClientSide && index < clientOutputs.size()) return clientOutputs.get(index).copy();
        CrucibleRecipe recipe = resolve(selected.get(index).id()); return recipe == null ? ItemStack.EMPTY : recipe.getRecipeOutput();
    }
    public Direction facing() { return getBlockState().getValue(BlockThaumatorium.FACING); }
    @Nullable public CrucibleRecipe resolve(ResourceLocation id) { return CrucibleRecipes.all(level).stream().filter(holder -> holder.id().equals(id)).map(RecipeHolder::value).findFirst().orElse(null); }
    public boolean gettingPower() { return level.hasNeighborSignal(worldPosition) || level.hasNeighborSignal(worldPosition.below()) || level.hasNeighborSignal(worldPosition.above()); }
    public boolean checkHeat() { BlockState heat = level.getBlockState(worldPosition.below(2)); return heat.is(Blocks.LAVA) || heat.is(Blocks.FIRE) || heat.is(Blocks.MAGMA_BLOCK) || BlocksTC.nitor.containsValue(heat.getBlock()); }
    public static void tick(Level level,BlockPos pos,BlockState state,TileThaumatorium tile) {
        if (level.isClientSide) {
            if(tile.venting>0){
                tile.venting--;
                float x=.1F-level.random.nextFloat()*.2F,z=.1F-level.random.nextFloat()*.2F,y=.1F-level.random.nextFloat()*.2F;
                float vx=.1F-level.random.nextFloat()*.2F,vz=.1F-level.random.nextFloat()*.2F,vy=.1F-level.random.nextFloat()*.2F;
                Direction face=tile.facing();
                SourceFX.local(level,"tube_vent",new Vec3(pos.getX()+.5F+x+face.getStepX()/2F,pos.getY()+.5F+y,pos.getZ()+.5F+z+face.getStepZ()/2F),
                    new Vec3(face.getStepX()/4F+vx,vy,face.getStepZ()/4F+vz),0xffffff,0);
            }
            return;
        }
        if (tile.counter == 0 || tile.counter % 40 == 0) { tile.heated = tile.checkHeat(); tile.getUpgrades(); tile.refreshSelections(); }
        tile.counter++;
        if (!tile.heated || tile.gettingPower() || tile.counter % 5 != 0 || tile.selected.isEmpty()) return;
        if (tile.catalyst.isEmpty()) { tile.currentSuction = null; return; }
        if (tile.currentCraft < 0 || tile.currentCraft >= tile.selected.size() || tile.currentRecipe == null || !tile.currentRecipe.catalystMatches(tile.catalyst)) {
            tile.currentCraft = -1; tile.currentRecipe = null;
            for (int index = 0; index < tile.selected.size(); index++) {
                CrucibleRecipe recipe = tile.resolve(tile.selected.get(index).id());
                if (recipe != null && recipe.catalystMatches(tile.catalyst)) { tile.currentCraft = index; tile.currentRecipe = recipe; break; }
            }
        }
        if (tile.currentCraft < 0) { tile.currentSuction = null; return; }
        // /reload/add-on replacement must never keep a stale executable object.
        tile.currentRecipe = tile.resolve(tile.selected.get(tile.currentCraft).id());
        if (tile.currentRecipe == null || !tile.currentRecipe.catalystMatches(tile.catalyst)) { tile.currentCraft = -1; tile.currentSuction = null; return; }
        tile.currentSuction = null;
        AspectList cost = tile.currentRecipe.getAspects();
        for (Aspect aspect : cost.getAspectsSortedByName()) if (tile.essentia.getAmount(aspect) < cost.getAmount(aspect)) { tile.currentSuction = aspect; break; }
        if (tile.currentSuction == null) tile.completeRecipe(); else tile.fill();
    }
    /** Dormant in BETA26 itself: the source accepts every nonnegative block event, no producer invented. */
    @Override public boolean triggerEvent(int id,int value){if(id>=0){if(level!=null&&level.isClientSide)venting=7;return true;}return super.triggerEvent(id,value);}
    public void getUpgrades() {
        int slots = 1;
        for (int y = 0; y <= 1; y++) for (Direction dir : Direction.values()) if (dir != Direction.DOWN && dir != facing()) {
            BlockState brain = level.getBlockState(worldPosition.above(y).relative(dir));
            if (brain.is(BlocksTC.brainBox) && brain.getValue(BlockBrainBox.FACING) == dir.getOpposite()) slots += 2;
        }
        slots = Math.min(MAX_RECIPES,slots);
        if (slots != maxRecipes) {
            maxRecipes = slots;
            while (selected.size() > maxRecipes) selected.remove(selected.size() - 1);
            currentCraft = -1; currentRecipe = null; changedAndSync();
        }
    }
    private void refreshSelections() {
        boolean removed = selected.removeIf(selection -> resolve(selection.id()) == null);
        if (removed) { currentCraft = -1; currentRecipe = null; changedAndSync(); }
    }
    /** Source updateRecipes adds known matching outputs plus selected outputs regardless of catalyst.
     * The modern ResourceLocation is persistent identity; the 1.12 hash was never stable across ports.
     */
    public List<RecipeHolder<CrucibleRecipe>> recipesFor(Player player) {
        if (catalyst.isEmpty()) {
            ArrayList<RecipeHolder<CrucibleRecipe>> chosen = new ArrayList<>();
            for (Selection selection : selected) { CrucibleRecipe recipe = resolve(selection.id()); if (recipe != null) chosen.add(new RecipeHolder<>(selection.id(),recipe)); }
            return List.copyOf(chosen);
        }
        ArrayList<RecipeHolder<CrucibleRecipe>> visible = new ArrayList<>();
        for (var holder : CrucibleRecipes.all(level)) if ((ThaumcraftCapabilities.knowsResearchStrict(player,holder.value().getResearch()) && holder.value().catalystMatches(catalyst))
            || selected.stream().anyMatch(selection -> selection.id().equals(holder.id()))) visible.add(holder);
        visible.sort(Comparator.comparing(holder -> holder.value().getRecipeOutput().getHoverName().getString()));
        return visible.size() > MAX_VIEWS ? List.copyOf(visible.subList(0,MAX_VIEWS)) : List.copyOf(visible);
    }
    public boolean toggleRecipe(ServerPlayer player,ResourceLocation id) {
        if (level == null || level.isClientSide || !stillValid(player)) return false;
        for (int index = 0; index < selected.size(); index++) if (selected.get(index).id().equals(id)) {
            selected.remove(index); currentCraft = -1; currentRecipe = null; currentSuction = null; changedAndSync(); return true;
        }
        getUpgrades();
        if (selected.size() >= maxRecipes) return false;
        CrucibleRecipe recipe = resolve(id);
        if (recipe == null || !ThaumcraftCapabilities.knowsResearchStrict(player,recipe.getResearch()) || !recipe.catalystMatches(catalyst)) return false;
        selected.add(new Selection(id,player.getUUID(),player.getGameProfile().getName())); changedAndSync(); return true;
    }
    private void completeRecipe() {
        if (currentRecipe == null || currentCraft < 0 || currentCraft >= selected.size() || !currentRecipe.matches(essentia,catalyst)) return;
        ItemStack output = currentRecipe.getRecipeOutput();
        if (output.isEmpty()) return;
        Selection selection = selected.get(currentCraft);
        catalyst.shrink(1); essentia = new AspectList(); // Source clears ALL stored essentia, not just costs.
        Player owner = selection.owner() == null ? null : level.getPlayerByUUID(selection.owner());
        if (owner == null && level.getServer() != null) owner = level.getServer().getPlayerList().getPlayerByName(selection.ownerName());
        if (owner != null && owner.level() == level) NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(owner,output,new SimpleContainer(catalyst)));
        ProcessingInventory.eject(level,worldPosition,facing(),output);
        level.playSound(null,worldPosition,SoundEvents.LAVA_EXTINGUISH,SoundSource.BLOCKS,.25F,2.6F + (level.random.nextFloat() - level.random.nextFloat()) * .8F);
        currentCraft = -1; currentRecipe = null; currentSuction = null; changedAndSync();
    }
    private void fill() {
        for (int y = 0; y <= 1; y++) for (Direction dir : Direction.values()) if (dir != facing() && dir != Direction.DOWN && (y != 0 || dir != Direction.UP)) {
            var tile = ThaumcraftApiHelper.getConnectableTile(level,worldPosition.above(y),dir);
            if (tile instanceof IEssentiaTransport source && source.getEssentiaAmount(dir.getOpposite()) > 0 && source.getSuctionAmount(dir.getOpposite()) < getSuctionAmount(null)
                && getSuctionAmount(null) >= source.getMinimumSuction()) {
                int taken = source.takeEssentia(currentSuction,1,dir.getOpposite());
                if (taken > 0) { addToContainer(currentSuction,taken); return; }
            }
        }
    }
    public ItemStack getCurrentOutputRecipe() { return currentRecipe == null ? ItemStack.EMPTY : currentRecipe.getRecipeOutput(); }
    public CompoundTag view(Player viewer,HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag(); tag.putInt("maxrec",maxRecipes); essentia.writeToNBT(tag);
        ListTag recipes = new ListTag();
        int byteEstimate = tag.sizeInBytes();
        for (var holder : recipesFor(viewer)) {
            CompoundTag row = new CompoundTag(); row.putString("id",holder.id().toString());
            row.put("output",holder.value().getRecipeOutput().save(registries)); holder.value().getAspects().writeToNBT(row);
            row.putBoolean("selected",selected.stream().anyMatch(selection -> selection.id().equals(holder.id()))); recipes.add(row);
            byteEstimate += row.sizeInBytes();
            if (byteEstimate > 131072) { recipes.remove(recipes.size() - 1); break; }
        }
        tag.put("views",recipes); return tag;
    }
    public void changedAndSync() { setChanged(); if (level != null && !level.isClientSide) { level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2); level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock()); } }
    @Override public void setChanged() { super.setChanged(); if (revision < Long.MAX_VALUE) revision++; }
    @Override public AspectList getAspects() { return essentia; }
    @Override public void setAspects(AspectList aspects) { essentia = aspects == null ? new AspectList() : aspects; changedAndSync(); }
    @Override public boolean doesContainerAccept(Aspect aspect) { return true; }
    @Override public int addToContainer(Aspect aspect,int amount) {
        if (aspect == null || amount <= 0 || currentRecipe == null) return amount;
        int need = currentRecipe.getAspects().getAmount(aspect) - essentia.getAmount(aspect);
        if (need <= 0) return amount;
        int added = Math.min(need,amount); essentia.add(aspect,added); changedAndSync(); return amount - added;
    }
    @Override public boolean takeFromContainer(Aspect aspect,int amount) { if (aspect == null || amount <= 0 || essentia.getAmount(aspect) < amount) return false; essentia.remove(aspect,amount); changedAndSync(); return true; }
    @Override public boolean takeFromContainer(AspectList aspects) { return false; }
    @Override public boolean doesContainerContain(AspectList aspects) { return false; }
    @Override public boolean doesContainerContainAmount(Aspect aspect,int amount) { return essentia.getAmount(aspect) >= amount; }
    @Override public int containerContains(Aspect aspect) { return essentia.getAmount(aspect); }
    @Override public boolean isConnectable(Direction face) { return face != facing(); }
    @Override public boolean canInputFrom(Direction face) { return face != facing(); }
    @Override public boolean canOutputTo(Direction face) { return false; }
    @Override public void setSuction(Aspect aspect,int amount) { currentSuction = aspect; }
    @Override public Aspect getSuctionType(Direction face) { return currentSuction; }
    @Override public int getSuctionAmount(Direction face) { return currentSuction == null ? 0 : 128; }
    @Override public Aspect getEssentiaType(Direction face) { return null; }
    @Override public int getEssentiaAmount(Direction face) { return 0; }
    @Override public int takeEssentia(Aspect aspect,int amount,Direction face) { return 0; }
    @Override public int addEssentia(Aspect aspect,int amount,Direction face) { return canInputFrom(face) ? amount - addToContainer(aspect,amount) : 0; }
    @Override public int getMinimumSuction() { return 0; }
    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return catalyst.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return slot == 0 ? catalyst : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot,int amount) { if (slot != 0 || amount <= 0) return ItemStack.EMPTY; ItemStack result = catalyst.split(amount); if (!result.isEmpty()) changedAndSync(); return result; }
    @Override public ItemStack removeItemNoUpdate(int slot) { if (slot != 0) return ItemStack.EMPTY; ItemStack result = catalyst; catalyst = ItemStack.EMPTY; return result; }
    @Override public void setItem(int slot,ItemStack stack) { if (slot == 0) { catalyst = stack; stack.limitSize(getMaxStackSize()); changedAndSync(); } }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getCenter()) <= 64; }
    @Override public void clearContent() { catalyst = ItemStack.EMPTY; changedAndSync(); }
    @Override public int[] getSlotsForFace(Direction side) { return new int[]{0}; }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side) { return slot == 0; }
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side) { return slot == 0; }
    @Override public Component getDisplayName() { return Component.translatable("block.thaumcraft.thaumatorium"); }
    @Override public AbstractContainerMenu createMenu(int window,Inventory inventory,Player player) { return new ContainerThaumatorium(window,inventory,this); }
    private void writeSync(CompoundTag tag,HolderLookup.Provider registries) {
        essentia.writeToNBT(tag); tag.putInt("maxrec",maxRecipes);
        ListTag recipes = new ListTag();
        for (Selection selection : selected) {
            CompoundTag row = new CompoundTag(); row.putString("id",selection.id().toString());
            CrucibleRecipe recipe = resolve(selection.id()); if (recipe != null) row.put("output",recipe.getRecipeOutput().save(registries));
            recipes.add(row);
        }
        tag.put("selected",recipes);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries); writeSync(tag,registries); if (!catalyst.isEmpty()) tag.put("catalyst",catalyst.save(registries));
        ListTag owners = new ListTag();
        for (Selection selection : selected) { CompoundTag row = new CompoundTag(); row.putString("name",selection.ownerName()); if (selection.owner() != null) row.putUUID("uuid",selection.owner()); owners.add(row); }
        tag.put("OutputPlayer",owners); tag.putInt("nativeRecipeIdentityVersion",1);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries); readSync(tag,registries); catalyst = ItemStack.parseOptional(registries,tag.getCompound("catalyst"));
        ListTag owners = tag.getList("OutputPlayer",Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(owners.size(),selected.size()); i++) {
            CompoundTag owner = owners.getCompound(i); Selection selection = selected.get(i);
            String name = owner.getString("name"); if (name.length() > 64) name = "";
            selected.set(i,new Selection(selection.id(),owner.hasUUID("uuid") ? owner.getUUID("uuid") : null,name));
        }
        // A legacy int[] 'recipes' hash is intentionally not interpreted as a modern id.
        currentCraft = -1; currentRecipe = null; currentSuction = null;
    }
    private void readSync(CompoundTag tag,HolderLookup.Provider registries) {
        essentia = new AspectList(); essentia.readFromNBT(tag); maxRecipes = Math.max(1,Math.min(MAX_RECIPES,tag.getInt("maxrec")));
        selected.clear(); clientOutputs.clear(); ListTag recipes = tag.getList("selected",Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(MAX_RECIPES,recipes.size()); i++) {
            String text = recipes.getCompound(i).getString("id"); ResourceLocation id = text.length() <= 256 ? ResourceLocation.tryParse(text) : null;
            if (id != null && selected.stream().noneMatch(selection -> selection.id().equals(id))) { selected.add(new Selection(id,null,"")); clientOutputs.add(ItemStack.parseOptional(registries,recipes.getCompound(i).getCompound("output"))); }
        }
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { CompoundTag tag = new CompoundTag(); writeSync(tag,registries); return tag; }
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries) { readSync(tag,registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
