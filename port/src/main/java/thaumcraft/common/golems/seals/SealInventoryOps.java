package thaumcraft.common.golems.seals;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.*;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.items.wrapper.*;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import thaumcraft.common.lib.research.LegacyScanIdentity;

/** Narrow source seal inventory bridge. Extraction retains ACTUAL component-bearing items. */
public final class SealInventoryOps {
 private SealInventoryOps(){}
 public static final class Filter {
  public boolean igDmg,igNBT,useOre,useMod;
  public static final Filter STRICT=new Filter(false,false,false,false);
  public Filter(boolean ignoreDamage,boolean ignoreNBT,boolean ore,boolean mod){igDmg=ignoreDamage;igNBT=ignoreNBT;useOre=ore;useMod=mod;}
 }
 public static IItemHandler getItemHandlerAt(Level world,BlockPos pos,Direction side){
  if(!world.hasChunkAt(pos))return null;var cap=world.getCapability(Capabilities.ItemHandler.BLOCK,pos,side);if(cap!=null)return cap;
  var tile=world.getBlockEntity(pos);return tile instanceof WorldlyContainer sided?new SidedInvWrapper(sided,side):tile instanceof Container inv?new InvWrapper(inv):null;
 }
 public static boolean areItemStacksEqual(ItemStack a,ItemStack b,Filter f){
  if(a==null||b==null)return a==b;if(a.isEmpty()||b.isEmpty())return a.isEmpty()==b.isEmpty();
  if(f.useMod)return BuiltInRegistries.ITEM.getKey(a.getItem()).getNamespace().equals(BuiltInRegistries.ITEM.getKey(b.getItem()).getNamespace());
  if(f.useOre && b.getComponentsPatch().forget(type->type==DataComponents.DAMAGE).isEmpty() && oreMatch(a,b))return true;
  var x=LegacyScanIdentity.identify(a);var y=LegacyScanIdentity.identify(b);int left=LegacyScanIdentity.rawDamage(a)==32767?32767:x.metadata(),right=LegacyScanIdentity.rawDamage(b)==32767?32767:y.metadata();
  return x.itemId().equals(y.itemId()) && SealRules.metadataMatches(left,right,f.igDmg) && (f.igNBT||componentsEqualExceptDamage(a,b));
 }
 /** Native DAMAGE replaces source metadata, not source item NBT. Compare it only above. */
 private static boolean componentsEqualExceptDamage(ItemStack a,ItemStack b){return a.getComponentsPatch().forget(type->type==DataComponents.DAMAGE).equals(b.getComponentsPatch().forget(type->type==DataComponents.DAMAGE));}
 private static boolean oreMatch(ItemStack a,ItemStack b){
  if(SealOreRules.sameVanillaGroup(BuiltInRegistries.ITEM.getKey(a.getItem()).toString(),BuiltInRegistries.ITEM.getKey(b.getItem()).toString()))return true;
  return a.getTags().filter(t->SealOreRules.isReviewedTag(t.location().toString())).anyMatch(b::is);
 }
 public static boolean matchesFilters(NonNullList<ItemStack> samples,boolean black,ItemStack stack,Filter f){if(stack==null||stack.isEmpty())return false;boolean matched=samples.stream().filter(s->!s.isEmpty()).anyMatch(s->areItemStacksEqual(s,stack,f));return black?!matched:matched;}
 public static Tuple<ItemStack,Integer> findFirstMatchFromFilterTuple(NonNullList<ItemStack> samples,NonNullList<Integer> sizes,boolean black,NonNullList<ItemStack> stacks,Filter f){
  for(var stack:stacks){if(!matchesFilters(samples,black,stack,f))continue;int limit=0;if(!black)for(int i=0;i<samples.size();i++)if(!samples.get(i).isEmpty()&&areItemStacksEqual(samples.get(i),stack,f)){limit=sizes.get(i);break;}return new Tuple<>(stack,limit);}return new Tuple<>(ItemStack.EMPTY,0);
 }
 public static ItemStack findFirstMatchFromFilter(NonNullList<ItemStack>s,NonNullList<Integer>z,boolean b,NonNullList<ItemStack>i,Filter f){return findFirstMatchFromFilterTuple(s,z,b,i,f).getA();}
 public static ItemStack findFirstMatchFromFilter(NonNullList<ItemStack>s,boolean black,IItemHandler inv,Direction face,Filter f,boolean leave){if(inv!=null)for(int i=0;i<inv.getSlots();i++){var stack=inv.getStackInSlot(i);if(matchesFilters(s,black,stack,f)&&(!leave||countTotalItemsIn(inv,stack,f)>=2))return stack;}return ItemStack.EMPTY;}
 public static int countTotalItemsIn(IItemHandler inv,ItemStack stack,Filter f){int count=0;if(inv!=null&&stack!=null&&!stack.isEmpty())for(int i=0;i<inv.getSlots();i++)if(areItemStacksEqual(stack,inv.getStackInSlot(i),f))count+=inv.getStackInSlot(i).getCount();return count;}
 public static int countTotalItemsIn(Level w,BlockPos p,Direction d,ItemStack s,Filter f){return countTotalItemsIn(getItemHandlerAt(w,p,d),s,f);}
 public static ItemStack hasRoomFor(Level w,BlockPos p,Direction d,ItemStack s){var inv=getItemHandlerAt(w,p,d);if(inv==null)return ItemStack.EMPTY;var rem=ItemHandlerHelper.insertItemStacked(inv,s.copy(),true);return s.copyWithCount(Math.max(0,s.getCount()-rem.getCount()));}
 public static boolean hasRoomForSome(Level w,BlockPos p,Direction d,ItemStack s){return !hasRoomFor(w,p,d,s).isEmpty();}
 public static ItemStack copyLimitedStack(ItemStack s,int limit){return s==null||s.isEmpty()||limit<=0?ItemStack.EMPTY:s.copyWithCount(Math.min(s.getCount(),limit));}
 public static ItemStack removeStackFrom(Level w,BlockPos p,Direction d,ItemStack s,Filter f,boolean sim){return removeStackFrom(getItemHandlerAt(w,p,d),s,f,sim);}
 public static ItemStack removeStackFrom(IItemHandler inv,ItemStack requested,Filter filter,boolean simulate){
  ItemStack out=ItemStack.EMPTY;if(inv==null||requested==null||requested.isEmpty())return out;
  for(int i=0;i<inv.getSlots()&&out.getCount()<requested.getCount();i++){var actual=inv.getStackInSlot(i);if(!areItemStacksEqual(requested,actual,filter)||!out.isEmpty()&&!ItemStack.isSameItemSameComponents(out,actual))continue;
   var got=inv.extractItem(i,requested.getCount()-out.getCount(),simulate);if(got.isEmpty())continue;if(out.isEmpty())out=got.copy();else if(ItemStack.isSameItemSameComponents(out,got))out.grow(got.getCount());else if(!simulate)ItemHandlerHelper.insertItemStacked(inv,got,false);
  }return out;
 }
 /** Preserve the original inverted isEmpty test: ground stack counting returns zero. */
 public static int countStackInWorld(Level world,BlockPos pos,ItemStack stack,double range,Filter f){int count=0;for(var e:world.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(range)))if(e.getItem().isEmpty()&&areItemStacksEqual(stack,e.getItem(),f))count+=e.getItem().getCount();return count;}
 public static boolean dropItemAtEntity(Level world,ItemStack stack,Entity target){if(stack==null||stack.isEmpty())return true;var origin=target.blockPosition();var item=new ItemEntity(world,target.getX(),target.getY()+.25,target.getZ(),stack.copy());if(world.addFreshEntity(item))return true;boolean stored=SealRecovery.store(world,origin,item.getItem());if(stored)item.setItem(ItemStack.EMPTY);return stored;}
 public static ItemStack ejectStackAt(Level world,BlockPos pos,Direction side,ItemStack stack,boolean smart){
  if(stack==null||stack.isEmpty())return ItemStack.EMPTY;var dest=pos.relative(side);var inv=getItemHandlerAt(world,dest,side.getOpposite());var out=inv==null?stack.copy():ItemHandlerHelper.insertItemStacked(inv,stack.copy(),false);if(smart&&inv!=null||out.isEmpty())return out;
  if(world.hasChunkAt(dest)&&world.getBlockState(dest).isCollisionShapeFullBlock(world,dest))pos=pos.relative(side.getOpposite());var e=new ItemEntity(world,pos.getX()+.5+side.getStepX(),pos.getY()+side.getStepY(),pos.getZ()+.5+side.getStepZ(),out);e.setDeltaMovement(.3*side.getStepX(),.3*side.getStepY(),.3*side.getStepZ());return world.addFreshEntity(e)?ItemStack.EMPTY:out;
 }
 public static void ejectStackAt(Level world,BlockPos pos,Direction side,ItemStack stack){var remainder=ejectStackAt(world,pos,side,stack,false);if(!remainder.isEmpty())SealRecovery.store(world,pos,remainder);}
}
