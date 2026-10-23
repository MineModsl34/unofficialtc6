package thaumcraft.common.golems.seals;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import thaumcraft.api.golems.*;
import thaumcraft.api.golems.seals.*;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.GolemInteractionHelper;
import thaumcraft.common.golems.tasks.TaskHandler;
public final class SealHarvest implements ISeal,ISealGui,ISealConfigArea,ISealConfigToggles {
 int delay=new Random(System.nanoTime()).nextInt(33),count;final Map<Long,ReplantInfo> replantTasks=new HashMap<>();
 private final SealToggle[] props={new SealToggle(true,"prep","golem.prop.replant"),new SealToggle(false,"ppro","golem.prop.provision")};
 static final class ReplantInfo {final BlockPos pos;final Direction face;int taskid;final ItemStack stack;final boolean farmland;ReplantInfo(BlockPos p,Direction f,int id,ItemStack s,boolean farm){pos=p.immutable();face=f;taskid=id;stack=s.copy();farmland=farm;}}
 public String getKey(){return "thaumcraft:harvest";}public ResourceLocation getSealIcon(){return ResourceLocation.parse("thaumcraft:item/seals/seal_harvest");}
 public void tickSeal(Level world,ISealEntity seal){
  if(delay%100==0){var area=GolemHelper.getBoundsForArea(seal);var it=replantTasks.entrySet().iterator();while(it.hasNext()){var e=it.next();if(!area.contains(Vec3.atCenterOf(e.getValue().pos))){var old=TaskHandler.getTask(world.dimension(),e.getValue().taskid);if(old!=null)old.setSuspended(true);it.remove();SealHandler.markChunkAsDirty(world.dimension(),seal.getSealPos().pos);}}}
  if(delay++%5==0){var p=GolemHelper.getPosInArea(seal,count++);if(SealBlockActions.grown(world,p)){var t=new Task(seal.getSealPos(),p);t.setPriority(seal.getPriority());TaskHandler.addTask(world.dimension(),t);}else if(props[0].value&&replantTasks.containsKey(p.asLong())&&world.hasChunkAt(p)&&world.isEmptyBlock(p)){var info=replantTasks.get(p.asLong());if(TaskHandler.getTask(world.dimension(),info.taskid)==null){var t=new Task(seal.getSealPos(),info.pos);t.setPriority(seal.getPriority());TaskHandler.addTask(world.dimension(),t);info.taskid=t.getId();}}}
 }
 public boolean onTaskCompletion(Level world,IGolemAPI golem,Task task){
  if(!(world instanceof ServerLevel level)||!world.hasChunkAt(task.getPos())){task.setSuspended(true);return true;}
  var pos=task.getPos();var state=world.getBlockState(pos);
  if(SealBlockActions.grown(world,pos)){
   if(SealBlockActions.clickable(state)){GolemInteractionHelper.golemClick(world,golem,pos,task.getSealPos().face,ItemStack.EMPTY,false,true);}
   else {GolemInteractionHelper.golemClick(world,golem,pos,task.getSealPos().face,ItemStack.EMPTY,false,true);
    if(SealBlockActions.grown(world,pos)&&SealBlockActions.harvest(world,golem.getGolemEntity(),pos,false)){
     golem.addRankXp(1);golem.swingArm();if(props[0].value){var seed=SealBlockActions.getSeed(state.getBlock());var below=world.getBlockState(pos.below());Direction face=null;
      if(seed.getItem() instanceof BlockItem plant&&plant.getBlock().defaultBlockState().canSurvive(world,pos))face=Direction.DOWN;
      else if(state.hasProperty(HorizontalDirectionalBlock.FACING))face=state.getValue(HorizontalDirectionalBlock.FACING);
      if(!seed.isEmpty()&&face!=null){var t=new Task(task.getSealPos(),pos);t.setPriority(task.getPriority());t.setLifespan((short)300);replantTasks.put(pos.asLong(),new ReplantInfo(pos,face,t.getId(),seed,below.getBlock() instanceof FarmBlock));TaskHandler.addTask(world.dimension(),t);SealHandler.markChunkAsDirty(world.dimension(),task.getSealPos().pos);}
     }
    }
   }
  }else{var info=replantTasks.get(pos.asLong());if(info!=null&&info.taskid==task.getId()&&world.isEmptyBlock(pos)&&golem.isCarrying(info.stack)){
   var below=world.getBlockState(pos.below());if(info.farmland&&(below.is(Blocks.DIRT)||below.is(Blocks.GRASS_BLOCK)))SealBlockActions.till(level,golem,pos.below());
   var paid=golem.dropItem(info.stack.copyWithCount(1));if(!paid.isEmpty()){var before=world.getBlockState(pos);GolemInteractionHelper.golemClickForReplant(world,golem,pos.relative(info.face),info.face.getOpposite(),paid);if(world.getBlockState(pos)!=before&&!world.isEmptyBlock(pos)){world.levelEvent(2001,pos,Block.getId(world.getBlockState(pos)));golem.addRankXp(1);golem.swingArm();}}
  }}
  task.setSuspended(true);return true;
 }
 public boolean canGolemPerformTask(IGolemAPI golem,Task task){var info=replantTasks.get(task.getPos().asLong());if(info!=null&&info.taskid==task.getId()){boolean carry=golem.isCarrying(info.stack);if(!carry&&props[1].value){var seal=SealHandler.getSealEntity(golem.getGolemWorld().dimension(),task.getSealPos());if(seal!=null)GolemHelper.requestProvisioning(golem.getGolemWorld(),seal,info.stack);}return carry;}return true;}
 public void readCustomNBT(CompoundTag tag,HolderLookup.Provider lookup){replantTasks.clear();var list=tag.getList("replant",Tag.TAG_COMPOUND);for(int i=0;i<list.size();i++){var n=list.getCompound(i);int face=n.getByte("taskface");if(face<0||face>=6)continue;long loc=n.getLong("taskloc");var stack=ItemStack.parseOptional(lookup,n);if(!stack.isEmpty())replantTasks.put(loc,new ReplantInfo(BlockPos.of(loc),Direction.from3DDataValue(face),0,stack,n.getBoolean("farmland")));}}
 public void writeCustomNBT(CompoundTag tag,HolderLookup.Provider lookup){if(!props[0].value)return;var list=new ListTag();for(var info:replantTasks.values()){var n=(CompoundTag)info.stack.save(lookup);n.putLong("taskloc",info.pos.asLong());n.putByte("taskface",(byte)info.face.ordinal());n.putBoolean("farmland",info.farmland);list.add(n);}tag.put("replant",list);}
 public boolean canPlaceAt(Level world,BlockPos pos,Direction side){return !world.isEmptyBlock(pos);}public int[] getGuiCategories(){return new int[]{2,3,0,4};}public SealToggle[] getToggles(){return props;}public void setToggle(int i,boolean b){props[i].value=b;}public EnumGolemTrait[] getRequiredTags(){return new EnumGolemTrait[]{EnumGolemTrait.DEFT,EnumGolemTrait.SMART};}public EnumGolemTrait[] getForbiddenTags(){return null;}
}
