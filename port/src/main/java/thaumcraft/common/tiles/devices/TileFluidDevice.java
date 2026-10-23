package thaumcraft.common.tiles.devices;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import thaumcraft.api.aspects.*;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.blocks.devices.BlockWorldDevice;
import thaumcraft.common.container.ContainerWorldDevice;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.network.chat.Component;

/** Source everfull urn / spa / potion sprayer. Native fluid transactions, no client-authoritative actions. */
public final class TileFluidDevice extends DeviceTile implements IEssentiaTransport,IAspectContainer,IFluidHandler {
    public final FluidTank tank;
    public boolean mix = true, activated;
    public int charges, color = 3355443, venting, zone;
    public AspectList recipe = new AspectList(), recipeProgress = new AspectList();
    private Aspect currentSuction;
    private int urnFXTicks,urnFXZone;
    private final List<Integer> handlers = new ArrayList<>();
    public TileFluidDevice(BlockPos pos,BlockState state) {
        super(pos,state,((BlockWorldDevice)state.getBlock()).kind == BlockWorldDevice.Kind.URN ? 0 : 1);
        tank = new FluidTank(kind() == BlockWorldDevice.Kind.URN ? 1000 : 5000,fluid -> kind() != BlockWorldDevice.Kind.URN || fluid.is(Fluids.WATER)) {
            @Override protected void onContentsChanged() { if (level != null && !level.isClientSide) { setChanged(); if (kind() != BlockWorldDevice.Kind.URN || getFluidAmount() == 1000 || getFluidAmount() == 0) changedAndSync(); } }
        };
    }
    @Override public void update() {
        if (level == null) return;
        counter++;
        if (kind() == BlockWorldDevice.Kind.SPRAYER) { sprayer(); return; }
        if(level.isClientSide){
            if(kind()==BlockWorldDevice.Kind.URN&&urnFXTicks>0){if(urnFXTicks%5==0)thaumcraft.common.lib.fx.SourceFX.local(level,"water",net.minecraft.world.phys.Vec3.atLowerCornerOf(worldPosition),net.minecraft.world.phys.Vec3.atLowerCornerOf(urnPosition(worldPosition,urnFXZone)),0,counter);urnFXTicks--;}
            return;
        }
        if (kind() == BlockWorldDevice.Kind.URN && counter % 5 == 0) urn();
        else if (kind() == BlockWorldDevice.Kind.SPA && counter % 40 == 0 && !level.hasNeighborSignal(worldPosition)) spa();
    }
    public static BlockPos urnPosition(BlockPos center,int index) { int[] offset = DeviceRules.urnOffset(index); return center.offset(offset[0],offset[1],offset[2]); }
    private void urn() {
        // The original unbounded index repeats these 75 positions but leaks duplicate handlers. Bound only the index.
        zone = (zone + 1) % 75; BlockPos target = urnPosition(worldPosition,zone);
        if (!target.equals(worldPosition) && (level.getCapability(Capabilities.FluidHandler.BLOCK,target,Direction.UP) != null || level.getBlockState(target).is(Blocks.CAULDRON) || level.getBlockState(target).is(Blocks.WATER_CAULDRON)) && !handlers.contains(zone)) handlers.add(zone);
        if (tank.getFluidAmount() >= 25) for (Iterator<Integer> iterator = handlers.iterator(); iterator.hasNext();) {
            int index = iterator.next(); BlockPos pos = urnPosition(worldPosition,index); var handler = level.getCapability(Capabilities.FluidHandler.BLOCK,pos,Direction.UP);
            int accepted = 0;
            BlockState state = level.getBlockState(pos);
            // NeoForge's vanilla cauldron capability accepts whole layers, not 25 mB.
            // Preserve source cauldron pricing before the generic capability path.
            if (state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON)) {
                if (tank.getFluidAmount() < 333) { iterator.remove(); continue; }
                int current = state.is(Blocks.CAULDRON) ? 0 : state.getValue(LayeredCauldronBlock.LEVEL);
                if (current < 3) { level.setBlock(pos,Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL,current + 1),3); level.updateNeighbourForOutputSignal(pos,Blocks.WATER_CAULDRON); accepted = 333; }
            } else if (handler != null) accepted = Math.max(0,Math.min(25,handler.fill(new FluidStack(Fluids.WATER,25),IFluidHandler.FluidAction.EXECUTE)));
            else { iterator.remove(); continue; }
            if (accepted > 0) { tank.drain(accepted,IFluidHandler.FluidAction.EXECUTE); setChanged(); level.blockEvent(worldPosition,getBlockState().getBlock(),1,index); break; }
        }
        if (tank.getFluidAmount() < 1000) { float request = DeviceRules.urnVisRequest(tank.getFluidAmount()); int amount = (int)(1000 * AuraHelper.drainVis(level,worldPosition,request,false)); tank.fill(new FluidStack(Fluids.WATER,amount),IFluidHandler.FluidAction.EXECUTE); }
    }
    private void spa() {
        if (tank.getFluidAmount() < 1000 || mix && (!tank.getFluid().is(Fluids.WATER) || !getItem(0).is(ItemsTC.bathSalts))) return;
        Block block = mix ? BlocksTC.purifyingFluid : tank.getFluid().getFluid().defaultFluidState().createLegacyBlock().getBlock();
        if (block == null || block == Blocks.AIR) return;
        BlockPos above = worldPosition.above();
        if (sameSource(above,block)) { for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) if (placeSpa(above.offset(x,0,z),block,true)) return; }
        else placeSpa(above,block,false);
    }
    private boolean sameSource(BlockPos pos,Block block) { BlockState state = level.getBlockState(pos); return state.is(block) && state.getFluidState().isSource(); }
    private boolean placeSpa(BlockPos pos,Block block,boolean adjacent) {
        BlockState output = block.defaultBlockState();
        if (output.getFluidState().is(Fluids.WATER) && level.dimensionType().ultraWarm()) return false;
        if (!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP) || !level.getBlockState(pos).canBeReplaced() || sameSource(pos,block)) return false;
        if (adjacent && Arrays.stream(Direction.values()).noneMatch(side -> sameSource(pos.relative(side),block))) return false;
        if (mix) removeItem(0,1); tank.drain(1000,IFluidHandler.FluidAction.EXECUTE); level.setBlock(pos,output,3); changedAndSync(); return true;
    }
    private void sprayer() {
        if(level.isClientSide){if(venting>0){venting--;for(int i=0;i<venting/2;i++)thaumcraft.common.lib.fx.SourceFX.local(level,"sprayer",net.minecraft.world.phys.Vec3.atLowerCornerOf(worldPosition),net.minecraft.world.phys.Vec3.ZERO,color,facing().ordinal());}return;}
        if (counter % 5 == 0) { currentSuction = null; if (getItem(0).isEmpty() || charges >= 8) return; for (Aspect aspect : recipe.getAspectsSortedByName()) if (recipeProgress.getAmount(aspect) < recipe.getAmount(aspect)) { currentSuction = aspect; break; } if (currentSuction == null) { recipeProgress = new AspectList(); charges++; changedAndSync(); } else fillEssentia(); }
        if (!enabled()) {
            if (!activated && charges > 0) {
                charges--; var contents = getItem(0).getOrDefault(DataComponents.POTION_CONTENTS,PotionContents.EMPTY);
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,new AABB(worldPosition.relative(facing(),2)).inflate(1))) if (target.isAlive() && target.isAffectedByPotions()) for (MobEffectInstance effect : contents.getAllEffects()) { if (effect.getEffect().value().isInstantenous()) effect.getEffect().value().applyInstantenousEffect(null,null,target,effect.getAmplifier(),1); else target.addEffect(new MobEffectInstance(effect.getEffect(),effect.getDuration(),effect.getAmplifier())); }
                level.playSound(null,worldPosition,SoundEvents.LAVA_EXTINGUISH,SoundSource.BLOCKS,.25F,2.6F + (level.random.nextFloat() - level.random.nextFloat()) * .8F); level.blockEvent(worldPosition,getBlockState().getBlock(),0,0); changedAndSync();
            }
            activated = true;
        } else if (activated) activated = false;
    }
    private void fillEssentia() { for (int y = 0; y <= 1; y++) for (Direction side : Direction.values()) { if (side == facing()) continue; IEssentiaTransport transport = thaumcraft.common.tiles.essentia.TransportConnections.connectable(level,worldPosition.above(y),side); if (transport != null && transport.getEssentiaAmount(side.getOpposite()) > 0 && transport.getSuctionAmount(side.getOpposite()) < 128 && transport.getMinimumSuction() <= 128) { int drawn = transport.takeEssentia(currentSuction,1,side.getOpposite()); if (drawn > 0) { addToContainer(currentSuction,drawn); return; } } } }
    public static boolean validPotion(ItemStack stack) { if (!stack.is(Items.POTION) && !stack.is(Items.SPLASH_POTION) && !stack.is(Items.LINGERING_POTION)) return false; var potion = stack.getOrDefault(DataComponents.POTION_CONTENTS,PotionContents.EMPTY).potion(); return potion.isPresent() && !potion.get().is(Potions.WATER) && !potion.get().is(Potions.AWKWARD) && !potion.get().is(Potions.MUNDANE) && !potion.get().is(Potions.THICK); }
    @Override public boolean canPlaceItem(int slot,ItemStack stack) { return kind() == BlockWorldDevice.Kind.SPA ? stack.is(ItemsTC.bathSalts) : kind() == BlockWorldDevice.Kind.SPRAYER && validPotion(stack); }
    @Override protected void inventoryChanged() { if (level != null && !level.isClientSide && kind() == BlockWorldDevice.Kind.SPRAYER) { recipe = getItem(0).isEmpty() ? new AspectList() : thaumcraft.common.config.ConfigAspects.getPotionAspects(getItem(0)); var potion = getItem(0).getOrDefault(DataComponents.POTION_CONTENTS,PotionContents.EMPTY).potion(); color = potion.isPresent() ? new PotionContents(potion.get()).getColor() : 3355443; recipe = AspectHelper.cullTags(recipe,10); recipeProgress = new AspectList(); charges = 0; } super.inventoryChanged(); }
    public void toggleMix() { mix = !mix; changedAndSync(); }
    public boolean activate(Player player,InteractionHand hand) {
        if (level.isClientSide) return true;
        if (kind() == BlockWorldDevice.Kind.SPRAYER) { openMenu(player); return true; }
        if (kind() == BlockWorldDevice.Kind.SPA && player.isShiftKeyDown()) return true;
        ItemStack held = player.getItemInHand(hand);
        boolean hasFluid = FluidUtil.getFluidContained(held).isPresent();
        if (kind() == BlockWorldDevice.Kind.URN || hasFluid) {
            if (FluidUtil.interactWithFluidHandler(player,hand,tank)) { changedAndSync(); player.inventoryMenu.broadcastChanges(); level.playSound(null,worldPosition,SoundEvents.BOTTLE_FILL,SoundSource.BLOCKS,.33F,1 + (level.random.nextFloat() - level.random.nextFloat()) * .3F); return true; }
            if (kind() == BlockWorldDevice.Kind.URN && held.is(Items.GLASS_BOTTLE) && tank.getFluidAmount() >= 333) { ItemStack water = new ItemStack(Items.POTION); water.set(DataComponents.POTION_CONTENTS,new PotionContents(Potions.WATER)); if (!player.getAbilities().instabuild) { held.shrink(1); if (held.isEmpty()) player.setItemInHand(hand,water); else if (!player.getInventory().add(water)) player.drop(water,false); } tank.drain(333,IFluidHandler.FluidAction.EXECUTE); changedAndSync(); level.playSound(null,worldPosition,SoundEvents.BOTTLE_FILL,SoundSource.BLOCKS,.33F,1 + (level.random.nextFloat() - level.random.nextFloat()) * .3F); } return true;
        }
        openMenu(player); return true;
    }
    private void openMenu(Player player) { player.openMenu(new SimpleMenuProvider((id,inventory,owner) -> new ContainerWorldDevice(id,inventory,this),Component.translatable(kind() == BlockWorldDevice.Kind.SPA ? "block.thaumcraft.spa" : "block.thaumcraft.potion_sprayer")),buffer -> buffer.writeBlockPos(worldPosition)); }
    @Override public boolean triggerEvent(int id,int param){if(kind()==BlockWorldDevice.Kind.SPRAYER&&id>=0){if(level!=null&&level.isClientSide)venting=15;return true;}if(kind()==BlockWorldDevice.Kind.URN&&id==1){if(level!=null&&level.isClientSide){urnFXZone=param;urnFXTicks=5;}return true;}return super.triggerEvent(id,param);}
    @Override public boolean isConnectable(Direction side) { return kind() == BlockWorldDevice.Kind.SPRAYER && side != facing(); }
    @Override public boolean canInputFrom(Direction side) { return isConnectable(side); }
    @Override public boolean canOutputTo(Direction side) { return false; }
    @Override public void setSuction(Aspect aspect,int amount) { currentSuction = aspect; }
    @Override public Aspect getSuctionType(Direction side) { return currentSuction; }
    @Override public int getSuctionAmount(Direction side) { return currentSuction == null ? 0 : 128; }
    @Override public Aspect getEssentiaType(Direction side) { return null; }
    @Override public int getEssentiaAmount(Direction side) { return 0; }
    @Override public int takeEssentia(Aspect aspect,int amount,Direction side) { return 0; }
    @Override public int addEssentia(Aspect aspect,int amount,Direction side) { return canInputFrom(side) ? amount - addToContainer(aspect,amount) : 0; }
    @Override public int getMinimumSuction() { return 0; }
    @Override public int addToContainer(Aspect aspect,int amount) { int added = Math.min(amount,Math.max(0,recipe.getAmount(aspect) - recipeProgress.getAmount(aspect))); if (added > 0) { recipeProgress.add(aspect,added); changedAndSync(); } return amount - added; }
    @Override public boolean takeFromContainer(Aspect aspect,int amount) { return false; }
    @Override public boolean takeFromContainer(AspectList aspects) { return false; }
    @Override public boolean doesContainerContain(AspectList aspects) { return false; }
    @Override public boolean doesContainerContainAmount(Aspect aspect,int amount) { return recipeProgress.getAmount(aspect) >= amount; }
    @Override public int containerContains(Aspect aspect) { return recipeProgress.getAmount(aspect); }
    @Override public boolean doesContainerAccept(Aspect aspect) { return true; }
    @Override public AspectList getAspects() { return recipeProgress; }
    @Override public void setAspects(AspectList aspects) { recipeProgress = aspects; }
    @Override public int getTanks() { return tank.getTanks(); }
    @Override public FluidStack getFluidInTank(int index) { return tank.getFluidInTank(index); }
    @Override public int getTankCapacity(int index) { return tank.getTankCapacity(index); }
    @Override public boolean isFluidValid(int index,FluidStack fluid) { return tank.isFluidValid(index,fluid); }
    @Override public int fill(FluidStack fluid,FluidAction action) { if (kind() != BlockWorldDevice.Kind.SPA) return 0; setChanged(); int accepted = tank.fill(fluid,action); changedAndSync(); return accepted; }
    @Override public FluidStack drain(FluidStack fluid,FluidAction action) { boolean full = tank.getFluidAmount() >= tank.getCapacity(); FluidStack drained = tank.drain(fluid,action); setChanged(); if (kind() == BlockWorldDevice.Kind.SPA || full && tank.getFluidAmount() < tank.getCapacity()) changedAndSync(); return drained; }
    @Override public FluidStack drain(int amount,FluidAction action) { boolean full = tank.getFluidAmount() >= tank.getCapacity(); FluidStack drained = tank.drain(amount,action); setChanged(); if (kind() == BlockWorldDevice.Kind.SPA || full && tank.getFluidAmount() < tank.getCapacity()) changedAndSync(); return drained; }
    @Override protected void writeDevice(CompoundTag tag,HolderLookup.Provider registries) { tank.writeToNBT(registries,tag); tag.putBoolean("mix",mix); recipe.writeToNBT(tag,"recipe"); recipeProgress.writeToNBT(tag,"progress"); tag.putInt("charges",charges); tag.putInt("color",color); tag.put("handlers",new ListTag()); }
    @Override protected void readDevice(CompoundTag tag,HolderLookup.Provider registries) { tank.readFromNBT(registries,tag.contains("tank") ? tag.getCompound("tank") : tag); if (tag.contains("FluidName")) { String name = tag.getString("FluidName"); var id = net.minecraft.resources.ResourceLocation.tryParse(name.contains(":") ? name : "minecraft:" + name); if (id != null) { var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(id); if (fluid != null && fluid != Fluids.EMPTY) tank.setFluid(new FluidStack(fluid,Math.max(0,Math.min(tank.getCapacity(),tag.getInt("Amount"))))); } } mix = !tag.contains("mix") || tag.getBoolean("mix"); recipe.readFromNBT(tag,"recipe"); recipeProgress.readFromNBT(tag,"progress"); charges = Math.max(0,Math.min(8,tag.getInt("charges"))); color = tag.getInt("color"); }
}
