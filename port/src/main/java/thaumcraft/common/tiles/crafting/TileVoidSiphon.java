package thaumcraft.common.tiles.crafting;

import java.util.List;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.blocks.crafting.BlockVoidSiphon;
import thaumcraft.common.container.ContainerVoidSiphon;
import thaumcraft.common.entities.*;
import thaumcraft.common.lib.utils.EntityUtils;

/** Actual source one-slot rift tap: integer truncation PER rift, 2000 work per void seed. */
public final class TileVoidSiphon extends BlockEntity implements WorldlyContainer,MenuProvider {
    public static final int PROGREQ=2000;
    public int progress;
    private int counter;
    private final NonNullList<ItemStack> items=NonNullList.withSize(1,ItemStack.EMPTY);
    public TileVoidSiphon(BlockPos pos,BlockState state){super(Phase10RiftRegistries.VOID_SIPHON,pos,state);}
    private boolean room(){var stack=items.get(0);return stack.isEmpty()||stack.is(ItemsTC.voidSeed)&&stack.getCount()<Math.min(getMaxStackSize(),stack.getMaxStackSize());}
    public List<EntityFluxRift> getValidRifts(){
        if(level==null)return List.of();Vec3 source=new Vec3(worldPosition.getX()+.5,worldPosition.getY()+1,worldPosition.getZ()+.5);
        return EntityUtils.getEntitiesInRange(level,worldPosition,null,EntityFluxRift.class,8).stream().filter(rift->{
            if(!rift.isAlive()||rift.getRiftSize()<2)return false;Vec3 start=source.add(rift.position().subtract(source).normalize());
            return level.clip(new ClipContext(start,rift.position(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,rift)).getType()==HitResult.Type.MISS;
        }).toList();
    }
    public static void tick(Level level,BlockPos pos,BlockState state,TileVoidSiphon tile){
        tile.counter++;if(level.isClientSide||!state.getValue(BlockVoidSiphon.ENABLED)||tile.counter%20!=0||!tile.room())return;
        boolean changed=false,active=false;
        if(tile.progress<PROGREQ)for(var rift:tile.getValidRifts()){
            tile.progress=FluxRiftRules.siphonProgress(tile.progress,rift.getRiftSize());rift.setRiftStability(FluxRiftRules.siphonStability(rift.getRiftStability(),rift.getRiftSize()));
            if(level.random.nextInt(33)==0)rift.setRiftSize(rift.getRiftSize()-1);active=true;changed=true;
        }
        if(active&&tile.counter%40==0)level.blockEvent(pos,state.getBlock(),5,tile.counter);
        while(tile.progress>=PROGREQ&&tile.room()){tile.progress-=PROGREQ;if(tile.items.get(0).isEmpty())tile.items.set(0,new ItemStack(ItemsTC.voidSeed));else tile.items.get(0).grow(1);changed=true;}
        if(changed)tile.changedAndSync();
    }
    private void changedAndSync(){setChanged();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    @Override public boolean triggerEvent(int id,int value){if(id!=5)return super.triggerEvent(id,value);if(level!=null&&level.isClientSide)RiftPresentationHooks.siphon.accept(this,value);return true;}
    @Override public int getContainerSize(){return 1;}
    @Override public boolean isEmpty(){return items.get(0).isEmpty();}
    @Override public ItemStack getItem(int slot){return slot==0?items.get(0):ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int count){if(slot!=0||count<=0)return ItemStack.EMPTY;var result=items.get(0).split(count);if(!result.isEmpty())changedAndSync();return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){if(slot!=0)return ItemStack.EMPTY;var result=items.get(0);items.set(0,ItemStack.EMPTY);return result;}
    @Override public void setItem(int slot,ItemStack stack){if(slot!=0)return;items.set(0,stack);stack.limitSize(Math.min(getMaxStackSize(),stack.getMaxStackSize()));changedAndSync();}
    @Override public void clearContent(){items.set(0,ItemStack.EMPTY);changedAndSync();}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot==0&&stack.is(ItemsTC.voidSeed);}
    @Override public boolean stillValid(Player player){return level!=null&&level.getBlockEntity(worldPosition)==this&&player.distanceToSqr(worldPosition.getCenter())<=64;}
    @Override public int[] getSlotsForFace(Direction side){return new int[]{0};}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side){return false;}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){return slot==0;}
    @Override public Component getDisplayName(){return Component.translatable("block.thaumcraft.void_siphon");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return new ContainerVoidSiphon(id,inventory,this);}
    private void write(CompoundTag tag,HolderLookup.Provider lookup){tag.putInt("progress",progress);ContainerHelper.saveAllItems(tag,items,lookup);}
    private void read(CompoundTag tag,HolderLookup.Provider lookup){progress=Math.max(0,Math.min(32767,tag.getInt("progress")));items.set(0,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,items,lookup);var stack=items.get(0);stack.limitSize(Math.min(getMaxStackSize(),stack.getMaxStackSize()));}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);write(tag,lookup);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);read(tag,lookup);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup){var tag=new CompoundTag();write(tag,lookup);return tag;}
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider lookup){read(tag,lookup);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
