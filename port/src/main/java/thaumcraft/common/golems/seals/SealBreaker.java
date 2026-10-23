package thaumcraft.common.golems.seals;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.api.golems.*;
import thaumcraft.api.golems.seals.*;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.tasks.TaskHandler;
public class SealBreaker extends SealFiltered implements ISealConfigArea,ISealConfigToggles {
 int delay=new Random(System.nanoTime()).nextInt(42);final Map<Integer,Long> cache=new HashMap<>();
 protected SealToggle[] props={new SealToggle(true,"pmeta","golem.prop.meta")};
 public String getKey(){return "thaumcraft:breaker";}public ResourceLocation getSealIcon(){return ResourceLocation.parse("thaumcraft:item/seals/seal_breaker");}
 public void tickSeal(Level world,ISealEntity seal){if(delay%100==0)cache.keySet().removeIf(id->TaskHandler.getTask(world.dimension(),id)==null);var p=GolemHelper.getPosInArea(seal,++delay);if(!cache.containsValue(p.asLong())&&valid(world,p)){var task=new Task(seal.getSealPos(),p);task.setPriority(seal.getPriority());task.setData((int)(world.getBlockState(p).getDestroySpeed(world,p)*10));TaskHandler.addTask(world.dimension(),task);cache.put(task.getId(),p.asLong());}}
 protected boolean valid(Level world,BlockPos pos){if(!world.hasChunkAt(pos))return false;var state=world.getBlockState(pos);if(state.isAir()||state.getDestroySpeed(world,pos)<0)return false;
  // BETA26 whitelist rejects ANY unmatched nonempty entry (not an invented OR).
  var actual=new ItemStack(state.getBlock());var flags=new SealInventoryOps.Filter(!getToggles()[0].value,true,false,false);
  for(var sample:getInv())if(!sample.isEmpty()){boolean same=SealInventoryOps.areItemStacksEqual(actual,sample,flags);if(isBlacklist()?same:!same)return false;}return true;
 }
 public boolean onTaskCompletion(Level world,IGolemAPI golem,Task task){if(cache.containsKey(task.getId())&&valid(world,task.getPos())){var state=world.getBlockState(task.getPos());golem.swingArm();boolean silk=getToggles().length>1&&getToggles()[1].value;int speed=silk?7:21;
  if(task.getData()>speed){float hardness=state.getDestroySpeed(world,task.getPos())*10;task.setLifespan((short)Math.max(task.getLifespan(),10));task.setData(task.getData()-speed);int progress=(int)(9*(1-task.getData()/hardness));var sound=state.getSoundType(world,task.getPos(),golem.getGolemEntity());world.playSound(null,task.getPos(),sound.getBreakSound(),SoundSource.BLOCKS,(sound.getVolume()+.7F)/8,sound.getPitch()*.5F);world.destroyBlockProgress(golem.getGolemEntity().getId(),task.getPos(),progress);return false;}
  world.destroyBlockProgress(golem.getGolemEntity().getId(),task.getPos(),10);if(SealBlockActions.harvest(world,golem.getGolemEntity(),task.getPos(),silk))golem.addRankXp(1);cache.remove(task.getId());
 }task.setSuspended(true);return true;}
 public boolean canGolemPerformTask(IGolemAPI golem,Task task){if(cache.containsKey(task.getId())&&valid(golem.getGolemWorld(),task.getPos()))return true;task.setSuspended(true);return false;}
 public void onTaskSuspension(Level world,Task task){cache.remove(task.getId());}
 public boolean canPlaceAt(Level world,BlockPos pos,Direction side){return !world.isEmptyBlock(pos);}
 public int[] getGuiCategories(){return new int[]{2,1,3,0,4};}public EnumGolemTrait[] getRequiredTags(){return new EnumGolemTrait[]{EnumGolemTrait.BREAKER};}public EnumGolemTrait[] getForbiddenTags(){return null;}
 public SealToggle[] getToggles(){return props;}public void setToggle(int index,boolean value){getToggles()[index].value=value;}
}
