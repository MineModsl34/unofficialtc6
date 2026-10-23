package thaumcraft.common.golems.seals;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.util.Tuple;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.*;
import thaumcraft.api.golems.*;
import thaumcraft.api.golems.seals.*;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import thaumcraft.common.golems.tasks.TaskHandler;
public class SealButcher implements ISeal, ISealGui, ISealConfigArea {
    int delay = new Random(System.nanoTime()).nextInt(200);
    boolean wait = false;
    ResourceLocation icon = ResourceLocation.fromNamespaceAndPath("thaumcraft", "item/seals/seal_butcher");

    @Override
    public String getKey() {
        return "thaumcraft:butcher";
    }

    @Override
    public void tickSeal(Level world, ISealEntity seal) {
        if (this.delay++ % 200 == 0 && !this.wait) {
            AABB area = GolemHelper.getBoundsForArea(seal);
            List list = world.getEntitiesOfClass(LivingEntity.class, area);
            if (list.size() > 0) {
                for (Object e : list) {
                    LivingEntity target = (LivingEntity)e;
                    if (this.isValidTarget(target)) {
                        List var55 = world.getEntitiesOfClass(target.getClass(), area);
                        Iterator var22 = var55.iterator();
                        int count = 0;

                        while (var22.hasNext() && count < 3) {
                            LivingEntity var33 = (LivingEntity)var22.next();
                            if (this.isValidTarget(var33)) {
                                count++;
                            }
                        }

                        if (count > 2) {
                            Task task = new Task(seal.getSealPos(), target);
                            task.setPriority(seal.getPriority());
                            task.setLifespan((short)10);
                            TaskHandler.addTask(world.dimension(), task);
                            this.wait = true;
                            break;
                        }
                    }
                }
            }
        }
    }

    private boolean isValidTarget(LivingEntity target) {
        return (target instanceof Animal || target instanceof PathfinderMob)
                && !(target instanceof Enemy)
                && (!(target instanceof TamableAnimal) || !((TamableAnimal)target).isTame())
                && !(target instanceof AbstractGolem)
            ? !(target instanceof Animal) || !((Animal)target).isBaby()
            : false;
    }

    @Override
    public void onTaskStarted(Level world, IGolemAPI golem, Task task) {
        if (task.getEntity() != null && task.getEntity() instanceof LivingEntity && this.isValidTarget((LivingEntity)task.getEntity())) {
            ((Mob)golem.getGolemEntity()).setTarget((LivingEntity)task.getEntity());
            golem.addRankXp(1);
        }

        task.setSuspended(true);
        this.wait = false;
    }

    @Override
    public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
        task.setSuspended(true);
        this.wait = false;
        return true;
    }

    @Override
    public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
        return true;
    }

    @Override
    public boolean canPlaceAt(Level world, BlockPos pos, Direction side) {
        return !world.isEmptyBlock(pos);
    }

    @Override
    public ResourceLocation getSealIcon() {
        return this.icon;
    }

    @Override
    public int[] getGuiCategories() {
        return new int[]{2, 0, 4};
    }

    @Override
    public EnumGolemTrait[] getRequiredTags() {
        return new EnumGolemTrait[]{EnumGolemTrait.FIGHTER, EnumGolemTrait.SMART};
    }

    @Override
    public EnumGolemTrait[] getForbiddenTags() {
        return null;
    }

    @Override
    public void onTaskSuspension(Level world, Task task) {
        this.wait = false;
    }

    @Override
    public void readCustomNBT(CompoundTag nbt,HolderLookup.Provider lookup) {
    }

    @Override
    public void writeCustomNBT(CompoundTag nbt,HolderLookup.Provider lookup) {
    }

    @Override
    public void onRemoval(Level world, BlockPos pos, Direction side) {
        this.wait = false;
    }




}
