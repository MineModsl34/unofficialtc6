package thaumcraft.common.tiles.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.common.entities.EntitySpecialItem;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.crafting.CrucibleRecipes;
import thaumcraft.common.lib.crafting.CrucibleRules;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

/** Server-owned TC6 alchemy. The old shrinking batch bound and cold direct API are retained. */
public final class TileCrucible extends BlockEntity implements IAspectContainer {
    public short heat;
    public AspectList aspects = new AspectList();
    private long counter = -100;
    private boolean loading;
    // Forge 1.12 retained WATER with amount zero in a new tank. After being emptied it
    // retained null, accepted any fluid, and still counted that fluid for boiling/crafting.
    // Native FluidStack normalizes zero to EMPTY, so retain that initial affinity explicitly.
    private boolean initialWaterAffinity = true;
    public final FluidTank tank = new FluidTank(CrucibleRules.CAPACITY, stack -> !initialWaterAffinity || stack.is(Fluids.WATER)) {
        @Override public void setFluid(FluidStack stack) { super.setFluid(stack); initialWaterAffinity = false; if (!loading) changedAndSync(); }
        @Override protected void onContentsChanged() {
            if (getFluidAmount() == 0) initialWaterAffinity = false;
            if (!loading) changedAndSync();
        }
    };
    public TileCrucible(BlockPos pos, BlockState state) { super(CrucibleRegistries.CRUCIBLE, pos, state); }
    public static void tick(Level level, BlockPos pos, BlockState state, TileCrucible tile) {
        tile.counter++;
        if (level.isClientSide) {
            if(tile.tank.getFluidAmount()>0)thaumcraft.common.lib.fx.SourceFX.local(level,"crucible",net.minecraft.world.phys.Vec3.atLowerCornerOf(pos),net.minecraft.world.phys.Vec3.ZERO,0,0);
            return;
        }
        BlockState below = level.getBlockState(pos.below());
        // Only source-era fire/lava/magma and real nitor; campfires are not silently added.
        boolean source = below.is(Blocks.LAVA) || below.is(Blocks.FIRE) || below.is(Blocks.MAGMA_BLOCK) || BlocksTC.nitor.containsValue(below.getBlock());
        boolean water = tile.tank.getFluidAmount() > 0;
        short previous = tile.heat;
        tile.heat = CrucibleRules.heat(previous, water, source);
        if (tile.heat != previous) tile.setChanged();
        if (CrucibleRules.syncHeat(previous, tile.heat, water, source)) tile.changedAndSync();
        if (tile.aspects.visSize() > 500) tile.spillRandom();
        if (tile.counter >= 100) { tile.spillRandom(); tile.counter = 0; }
    }
    public boolean boiling() { return heat > 150 && tank.getFluidAmount() > 0; }
    public float getFluidHeight() { return CrucibleRules.fluidHeight(tank.getFluidAmount(), aspects.visSize()); }
    public long decayCounter() { return counter; }
    public void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
            level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
        }
    }
    public void spillRandom() {
        if (level == null || level.isClientSide) return;
        if (aspects.size() > 0) {
            Aspect[] keys = aspects.getAspects();
            Aspect aspect = keys[level.random.nextInt(keys.length)];
            aspects.remove(aspect, 1);
            AuraHelper.polluteAura(level, worldPosition, CrucibleRules.decayFlux(aspect == Aspect.FLUX), true);
        }
        changedAndSync();
    }
    public void spillRemnants() {
        if (level == null || level.isClientSide || tank.getFluidAmount() <= 0 && aspects.visSize() <= 0) return;
        tank.setFluid(FluidStack.EMPTY);
        AuraHelper.polluteAura(level, worldPosition, aspects.visSize() * .25F, true);
        if (aspects.getAmount(Aspect.FLUX) > 0) AuraHelper.polluteAura(level, worldPosition, aspects.getAmount(Aspect.FLUX) * .75F, false);
        aspects = new AspectList();
        level.blockEvent(worldPosition, getBlockState().getBlock(), 2, 5);
        changedAndSync();
    }
    public void attemptSmelt(ItemEntity entity) {
        ItemStack remainder = attemptSmelt(entity.getItem(), entity.getPersistentData().getString("thrower"));
        if (remainder == null) entity.discard(); else entity.setItem(remainder);
    }
    /** No temperature precondition here: the original public API leaves that to its caller. */
    public ItemStack attemptSmelt(ItemStack item, String username) {
        if (level == null || level.isClientSide || item == null || item.isEmpty()) return item;
        Player owner = level.players().stream().filter(player -> player.getGameProfile().getName().equals(username)).findFirst().orElse(null);
        boolean dissolved = false, crafted = false;
        int remaining = item.getCount();
        for (int a = 0; a < remaining; a++) {
            CrucibleRecipe recipe = CrucibleRecipes.findMatching(owner, aspects, item);
            if (recipe != null && tank.getFluidAmount() > 0) {
                ItemStack output = recipe.getRecipeOutput().copy();
                // Source native craft receipt is BEFORE payment/output, for the real throwing owner.
                if (owner != null) NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(owner, output, new SimpleContainer(item)));
                aspects = recipe.removeMatching(aspects);
                tank.drain(50, FluidAction.EXECUTE); // Even 1..49 remaining water permits a source craft.
                ejectItem(output);
                crafted = true; remaining--; counter = -250;
            } else {
                AspectList tags = ThaumcraftCraftingManager.getObjectTags(item);
                if (tags != null && tags.size() != 0) {
                    for (Aspect aspect : tags.getAspects()) aspects.add(aspect, tags.getAmount(aspect));
                    dissolved = true; remaining--; counter = -150;
                }
            }
        }
        if (dissolved) {
            level.playSound(null, worldPosition, SoundsTC.bubble, SoundSource.BLOCKS, .2F, 1F + level.random.nextFloat() * .4F);
            changedAndSync(); level.blockEvent(worldPosition, getBlockState().getBlock(), 2, 1);
        }
        if (crafted) { changedAndSync(); level.blockEvent(worldPosition, getBlockState().getBlock(), 99, 0); }
        setChanged();
        if (remaining <= 0) return null;
        item.setCount(remaining); return item;
    }
    public void ejectItem(ItemStack stack) {
        if (level == null || level.isClientSide || stack.isEmpty()) return;
        int index = 0;
        do {
            ItemStack part = stack.copyWithCount(Math.min(stack.getCount(), stack.getMaxStackSize())); stack.shrink(part.getCount());
            EntitySpecialItem entity = new EntitySpecialItem(level, worldPosition.getX() + .5, worldPosition.getY() + .71, worldPosition.getZ() + .5, part);
            entity.setDeltaMovement(index == 0 ? 0 : (level.random.nextFloat() - level.random.nextFloat()) * .01F,
                .075F, index == 0 ? 0 : (level.random.nextFloat() - level.random.nextFloat()) * .01F);
            level.addFreshEntity(entity); index++;
        } while (!stack.isEmpty());
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.putShort("Heat", heat); tank.writeToNBT(registries, tag);
        tag.putBoolean("InitialWaterAffinity", initialWaterAffinity); aspects.writeToNBT(tag);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); loading = true;
        try {
            heat = tag.getShort("Heat"); tank.readFromNBT(registries, tag);
            initialWaterAffinity = tag.getBoolean("InitialWaterAffinity");
            // Recognize real TC6/Forge tank NBT from imported tiles, not only modern saves.
            if (!tag.contains("Fluid", 10) && tag.contains("FluidName", 8)) {
                var fluidId = net.minecraft.resources.ResourceLocation.tryParse(tag.getString("FluidName"));
                var fluid = fluidId == null ? java.util.Optional.<net.minecraft.world.level.material.Fluid>empty()
                    : net.minecraft.core.registries.BuiltInRegistries.FLUID.getOptional(fluidId);
                int amount = Math.min(CrucibleRules.CAPACITY, Math.max(0, tag.getInt("Amount")));
                if (fluid.isPresent() && fluid.get() != Fluids.EMPTY) {
                    if (amount > 0) tank.setFluid(new FluidStack(fluid.get(), amount));
                    initialWaterAffinity = amount == 0 && fluid.get() == Fluids.WATER;
                }
            }
            if (tag.contains("Empty")) tank.setFluid(FluidStack.EMPTY);
            aspects.readFromNBT(tag);
        }
        finally { loading = false; }
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public boolean triggerEvent(int id, int param) {
        if (id != 1 && id != 2 && id != 99) return super.triggerEvent(id, param);
        if (level != null && level.isClientSide) {
            thaumcraft.common.lib.fx.SourceFX.local(level,"crucible_event",net.minecraft.world.phys.Vec3.atLowerCornerOf(worldPosition),net.minecraft.world.phys.Vec3.ZERO,id,param);
            if(id==2||id==99)level.playLocalSound(worldPosition, SoundsTC.spill, SoundSource.BLOCKS, .2F, 1F, false);
        }
        return true; // Server must acknowledge before native block-event transport broadcasts it.
    }
    // These deliberately inert TC6 methods are NOT an essentia input/output pipe.
    @Override public AspectList getAspects() { return aspects; }
    @Override public void setAspects(AspectList values) {}
    @Override public boolean doesContainerAccept(Aspect aspect) { return true; }
    @Override public int addToContainer(Aspect aspect, int amount) { return 0; }
    @Override public boolean takeFromContainer(Aspect aspect, int amount) { return false; }
    @Override public boolean takeFromContainer(AspectList values) { return false; }
    @Override public boolean doesContainerContainAmount(Aspect aspect, int amount) { return false; }
    @Override public boolean doesContainerContain(AspectList values) { return false; }
    @Override public int containerContains(Aspect aspect) { return 0; }
}
