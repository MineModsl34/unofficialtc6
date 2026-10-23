package thaumcraft.common.golems.seals;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.golems.seals.*;
/** Source ghost stacks are samples, never a second transferable inventory. */
public abstract class SealFiltered implements ISeal,ISealGui,ISealConfigFilter {
 protected NonNullList<ItemStack> filter=NonNullList.withSize(getFilterSize(),ItemStack.EMPTY);
 protected NonNullList<Integer> filterSize=NonNullList.withSize(getFilterSize(),0);protected boolean blacklist=true;
 @Override public void readCustomNBT(CompoundTag nbt,HolderLookup.Provider lookup){
  filter=NonNullList.withSize(getFilterSize(),ItemStack.EMPTY);filterSize=NonNullList.withSize(getFilterSize(),0);
  var items=nbt.getList("Items",Tag.TAG_COMPOUND);for(int i=0;i<items.size();i++){var t=items.getCompound(i);int s=t.getByte("Slot")&255;if(s<filter.size())filter.set(s,ItemStack.parseOptional(lookup,t).copyWithCount(1));}
  blacklist=nbt.getBoolean("bl");var sizes=nbt.getList("Sizes",Tag.TAG_COMPOUND);for(int i=0;i<sizes.size();i++){var t=sizes.getCompound(i);int s=t.getByte("Slot")&255;if(s<filterSize.size())filterSize.set(s,Math.max(0,t.getInt("Size")));}
 }
 @Override public void writeCustomNBT(CompoundTag nbt,HolderLookup.Provider lookup){
  var items=new ListTag();var sizes=new ListTag();for(int i=0;i<filter.size();i++){if(!filter.get(i).isEmpty()){var t=(CompoundTag)filter.get(i).copyWithCount(1).save(lookup);t.putByte("Slot",(byte)i);items.add(t);}if(filterSize.get(i)!=0){var t=new CompoundTag();t.putByte("Slot",(byte)i);t.putInt("Size",filterSize.get(i));sizes.add(t);}}
  nbt.put("Items",items);nbt.put("Sizes",sizes);nbt.putBoolean("bl",blacklist);
 }
 @Override public int[] getGuiCategories(){return new int[]{0};}
 @Override public int getFilterSize(){return 1;}@Override public NonNullList<ItemStack> getInv(){return filter;}@Override public NonNullList<Integer> getSizes(){return filterSize;}
 @Override public ItemStack getFilterSlot(int i){return filter.get(i);}@Override public int getFilterSlotSize(int i){return filterSize.get(i);}
 @Override public void setFilterSlot(int i,ItemStack stack){filter.set(i,stack.isEmpty()?ItemStack.EMPTY:stack.copyWithCount(1));}
 @Override public void setFilterSlotSize(int i,int size){filterSize.set(i,Math.max(0,Math.min(1_000_000,size)));}
 @Override public boolean isBlacklist(){return blacklist;}@Override public void setBlacklist(boolean black){blacklist=black;}@Override public boolean hasStacksizeLimiters(){return false;}
}
