package thaumcraft.common.golems.seals;
import java.util.Random;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.api.golems.*;
import thaumcraft.api.golems.seals.*;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.GolemInteractionHelper;
import thaumcraft.common.golems.tasks.TaskHandler;
public final class SealUse extends SealFiltered implements ISealConfigToggles {
 int delay=new Random(System.nanoTime()).nextInt(49),watchedTask=Integer.MIN_VALUE;
 private final SealToggle[] props={new SealToggle(true,"pmeta","golem.prop.meta"),new SealToggle(true,"pnbt","golem.prop.nbt"),new SealToggle(false,"pore","golem.prop.ore"),new SealToggle(false,"pmod","golem.prop.mod"),new SealToggle(false,"pleft","golem.prop.left"),new SealToggle(false,"pempty","golem.prop.empty"),new SealToggle(false,"pemptyhand","golem.prop.emptyhand"),new SealToggle(false,"psneak","golem.prop.sneak"),new SealToggle(false,"ppro","golem.prop.provision.wl")};
 public String getKey(){return "thaumcraft:use";}public ResourceLocation getSealIcon(){return ResourceLocation.parse("thaumcraft:item/seals/seal_use");}
 private SealInventoryOps.Filter flags(){return new SealInventoryOps.Filter(!props[0].value,!props[1].value,props[2].value,props[3].value);}
 public void tickSeal(Level world,ISealEntity seal){if(delay++%5==0){var old=TaskHandler.getTask(world.dimension(),watchedTask);if(old==null||old.isSuspended()||old.isCompleted()){if(props[5].value!=world.isEmptyBlock(seal.getSealPos().pos))return;var t=new Task(seal.getSealPos(),seal.getSealPos().pos);t.setPriority(seal.getPriority());TaskHandler.addTask(world.dimension(),t);watchedTask=t.getId();}}}
 public boolean onTaskCompletion(Level world,IGolemAPI golem,Task task){if(props[5].value==world.isEmptyBlock(task.getPos())){var click=golem.getCarrying().get(0);if(!filter.get(0).isEmpty())click=SealInventoryOps.findFirstMatchFromFilter(filter,filterSize,blacklist,golem.getCarrying(),flags());if(!click.isEmpty()||props[6].value){var paid=props[6].value?ItemStack.EMPTY:golem.dropItem(click.copy());if(props[6].value||!paid.isEmpty())GolemInteractionHelper.golemClick(world,golem,task.getPos(),task.getSealPos().face,paid,props[7].value,!props[4].value);}}task.setSuspended(true);return true;}
 public boolean canGolemPerformTask(IGolemAPI golem,Task task){if(props[6].value)return true;boolean found=!SealInventoryOps.findFirstMatchFromFilter(filter,filterSize,blacklist,golem.getCarrying(),flags()).isEmpty();if(!found&&props[8].value&&!blacklist&&!filter.get(0).isEmpty()){var seal=SealHandler.getSealEntity(golem.getGolemWorld().dimension(),task.getSealPos());if(seal!=null){var sample=filter.get(0).copy();if(!props[0].value)sample.set(net.minecraft.core.component.DataComponents.DAMAGE,32767);GolemHelper.requestProvisioning(golem.getGolemWorld(),seal,sample);}}return found;}
 public boolean canPlaceAt(Level world,BlockPos pos,Direction side){return true;}public int[] getGuiCategories(){return new int[]{1,3,0,4};}public SealToggle[] getToggles(){return props;}public void setToggle(int i,boolean b){props[i].value=b;}public EnumGolemTrait[] getRequiredTags(){return new EnumGolemTrait[]{EnumGolemTrait.DEFT,EnumGolemTrait.SMART};}public EnumGolemTrait[] getForbiddenTags(){return null;}
}
