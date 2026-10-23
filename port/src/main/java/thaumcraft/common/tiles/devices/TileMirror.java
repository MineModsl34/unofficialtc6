package thaumcraft.common.tiles.devices;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import thaumcraft.common.lib.crafting.MirrorLinkRules;

/** Source item queue, one random unit ejected per tick after initial 20 ticks.
 * Capability is insertion-only; simulation never performs transport or pays instability.
 */
public final class TileMirror extends TileMirrorBase {
    private static final int MAX_QUEUED_STACKS = 4096;
    private final List<ItemStack> outputStacks = new ArrayList<>();
    public TileMirror(BlockPos pos,BlockState state) { super(MirrorRegistries.MIRROR,pos,state); }
    @Override protected boolean essentia() { return false; }
    public boolean transport(ItemEntity item) {
        if (!isLinkValid() || !(destinationTile() instanceof TileMirror other) || item.getItem().isEmpty()) return false;
        int amount = item.getItem().getCount();
        if (!other.transportDirect(item.getItem())) return false;
        addInstability(amount); item.discard(); event(); return true;
    }
    public boolean transportDirect(ItemStack stack) {
        if (level == null || level.isClientSide || stack.isEmpty() || outputStacks.size() >= MAX_QUEUED_STACKS) return false;
        outputStacks.add(stack.copy()); setChanged(); return true;
    }
    public void update() {
        if (level == null || level.isClientSide) return;
        if (MirrorLinkRules.canEject(count,outputStacks.size())) {
            int index = level.random.nextInt(outputStacks.size()); ItemStack queued = outputStacks.get(index);
            if (queued.isEmpty()) outputStacks.remove(index);
            else if (spawnItem(queued.copyWithCount(1))) { queued.shrink(1); addInstability(1); event(); if (queued.isEmpty()) outputStacks.remove(index); }
            setChanged();
        }
        updateLink();
    }
    private boolean spawnItem(ItemStack stack) {
        ItemEntity entity = new ItemEntity(level,worldPosition.getX()+.5,worldPosition.getY()+.25,worldPosition.getZ()+.5,stack);
        var side = facing(); entity.setDeltaMovement(side.getStepX()*.15F,side.getStepY()*.15F,side.getStepZ()*.15F);
        entity.setPortalCooldown(20);
        return level.addFreshEntity(entity);
    }
    private void event() { if(level!=null) level.blockEvent(worldPosition,getBlockState().getBlock(),1,0); }
    @Override public boolean triggerEvent(int id,int value) {
        if(id!=1) return super.triggerEvent(id,value);
        if(level!=null&&level.isClientSide)thaumcraft.common.lib.fx.SourceFX.local(level,"mirror",net.minecraft.world.phys.Vec3.atLowerCornerOf(worldPosition),net.minecraft.world.phys.Vec3.ZERO,0,0);
        return true;
    }
    /** Explicit conservation adaptation: source loses its hidden queue on block removal. */
    public void dropQueue() { if(level!=null && !level.isClientSide) for(ItemStack stack:outputStacks) net.minecraft.world.Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,stack); outputStacks.clear(); }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries); ListTag items = new ListTag();
        for(ItemStack stack:outputStacks) if(!stack.isEmpty()) items.add(stack.save(registries));
        tag.put("Items",items);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries); outputStacks.clear(); ListTag items=tag.getList("Items",10);
        if(items.size()>MAX_QUEUED_STACKS) throw new IllegalArgumentException("Oversized mirror queue");
        for(int index=0;index<items.size();index++) { ItemStack stack=ItemStack.parseOptional(registries,items.getCompound(index)); if(!stack.isEmpty())outputStacks.add(stack); }
    }
    public final IItemHandler insertion = new IItemHandler() {
        @Override public int getSlots(){return 1;}
        @Override public ItemStack getStackInSlot(int slot){return ItemStack.EMPTY;}
        @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){
            if(slot!=0 || stack.isEmpty() || !(destinationTile() instanceof TileMirror other) || other.outputStacks.size()>=MAX_QUEUED_STACKS) return stack;
            if(!simulate) { if(!other.transportDirect(stack)) return stack; addInstability(stack.getCount()); event(); }
            return ItemStack.EMPTY;
        }
        @Override public ItemStack extractItem(int slot,int amount,boolean simulate){return ItemStack.EMPTY;}
        @Override public int getSlotLimit(int slot){return 64;}
        @Override public boolean isItemValid(int slot,ItemStack stack){return slot==0 && destinationTile() instanceof TileMirror;}
    };
}
