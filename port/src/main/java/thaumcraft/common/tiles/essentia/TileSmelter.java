package thaumcraft.common.tiles.essentia;

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
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.blocks.essentia.BlockSmelter;
import thaumcraft.common.container.ContainerSmelter;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

/** Source aspect smelter, real fuel/input inventories, bellows, auxiliary alembics and vents. */
public class TileSmelter extends BlockEntity implements WorldlyContainer, MenuProvider {
    // EnumFacing.HORIZONTALS was indexed by its old horizontal index, not enum ordinal.
    private static final Direction[] SOURCE_HORIZONTAL = {Direction.SOUTH,Direction.WEST,Direction.NORTH,Direction.EAST};
    private final NonNullList<ItemStack> items = NonNullList.withSize(2,ItemStack.EMPTY);
    public final int maxVis = 256;
    public int vis, smeltTime = 100, furnaceBurnTime, currentItemBurnTime, furnaceCookTime;
    public AspectList aspects = new AspectList();
    public boolean speedBoost;
    private int count, bellows = -1;
    public final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return switch(index) { case 0 -> furnaceCookTime; case 1 -> furnaceBurnTime; case 2 -> currentItemBurnTime; case 3 -> vis; case 4 -> smeltTime; default -> 0; }; }
        @Override public void set(int index,int value) { switch(index) { case 0 -> furnaceCookTime = value; case 1 -> furnaceBurnTime = value; case 2 -> currentItemBurnTime = value; case 3 -> vis = value; case 4 -> smeltTime = value; } }
        @Override public int getCount() { return 5; }
    };
    public TileSmelter(BlockPos pos, BlockState state) { super(EssentiaRegistries.SMELTER,pos,state); }
    // The source name getType() is occupied by native BlockEntity.getType().
    public int getSmelterType() { return getBlockState().is(BlocksTC.smelterThaumium) ? 1 : getBlockState().is(BlocksTC.smelterVoid) ? 2 : 0; }
    public int getSpeed() { return thaumcraft.common.lib.crafting.EssentiaSmelterRules.outputPeriod(getSmelterType(),false); }
    public float getEfficiency() { return thaumcraft.common.lib.crafting.EssentiaSmelterRules.efficiency(getSmelterType()); }
    public void checkNeighbours() {
        if (level == null) { bellows = 0; return; }
        Direction front = getBlockState().getValue(BlockSmelter.FACING);
        Direction[] faces = switch(front) {
            case NORTH -> new Direction[]{Direction.SOUTH,Direction.EAST,Direction.WEST};
            case SOUTH -> new Direction[]{Direction.NORTH,Direction.EAST,Direction.WEST};
            case EAST -> new Direction[]{Direction.SOUTH,Direction.NORTH,Direction.WEST};
            case WEST -> new Direction[]{Direction.SOUTH,Direction.EAST,Direction.NORTH};
            default -> SOURCE_HORIZONTAL;
        };
        bellows = thaumcraft.common.tiles.devices.TileBellows.getBellows(level,worldPosition,faces);
    }
    public void update() {
        if (level == null) return;
        boolean wasBurning = furnaceBurnTime > 0, dirty = false;
        count++;
        if (furnaceBurnTime > 0) furnaceBurnTime--;
        if (level.isClientSide) return;
        if (bellows < 0) checkNeighbours();
        int period = thaumcraft.common.lib.crafting.EssentiaSmelterRules.outputPeriod(getSmelterType(),speedBoost);
        if (count % period == 0 && aspects.size() > 0) {
            for (Aspect aspect : aspects.getAspects()) {
                if (aspects.getAmount(aspect) > 0 && TileAlembic.processAlembics(level,worldPosition,aspect)) { takeFromContainer(aspect,1); dirty = true; break; }
            }
            for (Direction face : SOURCE_HORIZONTAL) if (face != getBlockState().getValue(BlockSmelter.FACING)) {
                BlockState aux = level.getBlockState(worldPosition.relative(face));
                if (aux.is(BlocksTC.smelterAux) && aux.getValue(thaumcraft.common.blocks.essentia.BlockSmelterAux.FACING) == face.getOpposite()) {
                    for (Aspect aspect : aspects.getAspects()) if (aspects.getAmount(aspect) > 0 && TileAlembic.processAlembics(level,worldPosition.relative(face),aspect)) { takeFromContainer(aspect,1); dirty = true; break; }
                }
            }
        }
        if (furnaceBurnTime == 0) {
            if (canSmelt()) {
                furnaceBurnTime = currentItemBurnTime = getItemBurnTime(items.get(1));
                if (furnaceBurnTime > 0) {
                    BlockSmelter.setFurnaceState(level,worldPosition,true); speedBoost = items.get(1).is(ItemsTC.alumentum);
                    ItemStack fuel = items.get(1); ItemStack remainder = fuel.getCraftingRemainingItem(); fuel.shrink(1);
                    if (fuel.isEmpty()) items.set(1,remainder); dirty = true;
                } else BlockSmelter.setFurnaceState(level,worldPosition,false);
            } else BlockSmelter.setFurnaceState(level,worldPosition,false);
        }
        // Source gate is block ENABLED, not a second burnTime condition.
        if (getBlockState().getValue(BlockSmelter.ENABLED) && canSmelt()) {
            furnaceCookTime++;
            if (furnaceCookTime >= smeltTime) { furnaceCookTime = 0; smeltItem(); dirty = true; }
        } else furnaceCookTime = 0;
        if (wasBurning != (furnaceBurnTime > 0)) dirty = true;
        if (dirty) { setChanged(); level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2); }
    }
    public boolean canSmelt() {
        if (items.get(0).isEmpty()) return false;
        AspectList tags = ThaumcraftCraftingManager.getObjectTags(items.get(0));
        if (tags == null || tags.size() == 0 || tags.visSize() > maxVis - vis) return false;
        smeltTime = thaumcraft.common.lib.crafting.EssentiaSmelterRules.cookTime(tags.visSize(),bellows); return true;
    }
    public void smeltItem() {
        if (!canSmelt()) return;
        AspectList tags = ThaumcraftCraftingManager.getObjectTags(items.get(0)).copy();
        int flux = 0;
        for (Aspect aspect : tags.getAspects()) {
            float efficiency = aspect == Aspect.FLUX ? getEfficiency() * .66F : getEfficiency();
            int original = tags.getAmount(aspect);
            for (int index = 0; index < original; index++) if (level.random.nextFloat() > efficiency) { tags.reduce(aspect,1); flux++; }
            aspects.add(aspect,tags.getAmount(aspect));
        }
        if (flux > 0) {
            int pollution = 0;
            fluxPoints: for (int point = 0; point < flux; point++) {
                for (Direction face : SOURCE_HORIZONTAL) if (face != getBlockState().getValue(BlockSmelter.FACING)) {
                    BlockState vent = level.getBlockState(worldPosition.relative(face));
                    if (vent.is(BlocksTC.smelterVent) && vent.getValue(thaumcraft.common.blocks.essentia.BlockSmelterVent.FACING) == face.getOpposite()
                        && level.random.nextFloat() < .333) { level.blockEvent(worldPosition,getBlockState().getBlock(),1,face.getOpposite().ordinal()); continue fluxPoints; }
                }
                pollution++;
            }
            AuraHelper.polluteAura(level,worldPosition,pollution,true);
        }
        vis = aspects.visSize(); items.get(0).shrink(1);
    }
    public static int getItemBurnTime(ItemStack stack) { return stack.isEmpty() ? 0 : stack.getBurnTime(RecipeType.SMELTING); }
    public boolean takeFromContainer(Aspect aspect,int amount) { if (aspects.getAmount(aspect) < amount) return false; aspects.remove(aspect,amount); vis = aspects.visSize(); setChanged(); return true; }
    public static boolean isItemFuel(ItemStack stack) { return getItemBurnTime(stack) > 0; }
    public int getCookProgressScaled(int scale) { return furnaceCookTime * scale / (smeltTime == 0 ? 1 : smeltTime); }
    public int getVisScaled(int scale) { return vis * scale / maxVis; }
    public int getBurnTimeRemainingScaled(int scale) { return furnaceBurnTime * scale / (currentItemBurnTime == 0 ? 200 : currentItemBurnTime); }
    @Override public boolean triggerEvent(int event,int parameter) {
        if (event != 1) return super.triggerEvent(event,parameter);
        if (level != null && level.isClientSide && parameter >= 0 && parameter < Direction.values().length) {
            Direction direction = Direction.values()[parameter].getOpposite();
            level.playLocalSound(worldPosition.getX() + .5 + direction.getStepX(),worldPosition.getY() + .5,worldPosition.getZ() + .5 + direction.getStepZ(),
                net.minecraft.sounds.SoundEvents.LAVA_EXTINGUISH,net.minecraft.sounds.SoundSource.BLOCKS,.25F,2.6F + (level.random.nextFloat() - level.random.nextFloat()) * .8F,true);
            thaumcraft.common.lib.fx.SourceFX.local(level,"smelter_vent",net.minecraft.world.phys.Vec3.atLowerCornerOf(worldPosition),net.minecraft.world.phys.Vec3.ZERO,0,parameter);
        }
        return true;
    }
    @Override public int getContainerSize() { return 2; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot,int amount) { ItemStack out = ContainerHelper.removeItem(items,slot,amount); if (!out.isEmpty()) setChanged(); return out; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items,slot); }
    @Override public void setItem(int slot,ItemStack stack) { items.set(slot,stack); stack.limitSize(getMaxStackSize()); setChanged(); }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getCenter()) <= 64; }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean canPlaceItem(int slot,ItemStack stack) { if (slot == 1) return isItemFuel(stack); var tags = ThaumcraftCraftingManager.getObjectTags(stack); return slot == 0 && tags != null && tags.size() > 0; }
    @Override public int[] getSlotsForFace(Direction side) { return side == Direction.DOWN ? new int[]{1} : side == Direction.UP ? new int[0] : new int[]{0}; }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side) { return side != Direction.UP && canPlaceItem(slot,stack); }
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side) { return side != Direction.UP || slot != 1 || stack.is(Items.BUCKET); }
    @Override public Component getDisplayName() { return Component.translatable(getBlockState().getBlock().getDescriptionId()); }
    @Override public AbstractContainerMenu createMenu(int window,Inventory inventory,Player player) { return new ContainerSmelter(window,inventory,this,data); }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) { super.saveAdditional(tag,registries); ContainerHelper.saveAllItems(tag,items,registries); tag.putShort("BurnTime",(short)furnaceBurnTime); tag.putShort("CookTime",(short)furnaceCookTime); tag.putBoolean("speedBoost",speedBoost); aspects.writeToNBT(tag); }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) { super.loadAdditional(tag,registries); items.clear(); ContainerHelper.loadAllItems(tag,items,registries); furnaceBurnTime = tag.getShort("BurnTime"); furnaceCookTime = tag.getShort("CookTime"); speedBoost = tag.getBoolean("speedBoost"); aspects = new AspectList(); aspects.readFromNBT(tag); vis = aspects.visSize(); currentItemBurnTime = getItemBurnTime(items.get(1)); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { CompoundTag tag = new CompoundTag(); tag.putShort("BurnTime",(short)furnaceBurnTime); return tag; }
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries) { furnaceBurnTime = tag.getShort("BurnTime"); }
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries) { furnaceBurnTime = packet.getTag().getShort("BurnTime"); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
