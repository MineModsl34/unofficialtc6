package thaumcraft.common.golems.seals;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import thaumcraft.api.golems.*;
import thaumcraft.api.golems.seals.*;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.tasks.TaskHandler;
public final class SealLumber implements ISeal,ISealGui,ISealConfigArea {
 int delay=new Random(System.nanoTime()).nextInt(33);final Map<Integer,Long> cache=new HashMap<>();
 public String getKey(){return "thaumcraft:lumber";}public ResourceLocation getSealIcon(){return ResourceLocation.parse("thaumcraft:item/seals/seal_lumber");}
 public void tickSeal(Level world,ISealEntity seal){if(delay%100==0)cache.keySet().removeIf(id->TaskHandler.getTask(world.dimension(),id)==null);var p=GolemHelper.getPosInArea(seal,++delay);if(!cache.containsValue(p.asLong())&&SealBlockActions.isWoodLog(world,p)){var t=new Task(seal.getSealPos(),p);t.setPriority(seal.getPriority());TaskHandler.addTask(world.dimension(),t);cache.put(t.getId(),p.asLong());}}
 public boolean onTaskCompletion(Level world,IGolemAPI golem,Task task){if(cache.containsKey(task.getId())&&SealBlockActions.isWoodLog(world,task.getPos())){golem.swingArm();if(SealBlockActions.breakFurthestBlock(world,task.getPos(),world.getBlockState(task.getPos()),golem.getGolemEntity())){task.setLifespan((short)Math.max(task.getLifespan(),10));golem.addRankXp(1);return false;}cache.remove(task.getId());}task.setSuspended(true);return true;}
 public boolean canGolemPerformTask(IGolemAPI golem,Task task){if(cache.containsKey(task.getId())&&SealBlockActions.isWoodLog(golem.getGolemWorld(),task.getPos()))return true;task.setSuspended(true);return false;}
 public void onTaskSuspension(Level world,Task task){cache.remove(task.getId());}
 public boolean canPlaceAt(Level world,BlockPos pos,Direction side){return !world.isEmptyBlock(pos);}
 public int[] getGuiCategories(){return new int[]{2,0,4};}public EnumGolemTrait[] getRequiredTags(){return new EnumGolemTrait[]{EnumGolemTrait.BREAKER,EnumGolemTrait.SMART};}public EnumGolemTrait[] getForbiddenTags(){return null;}
}
