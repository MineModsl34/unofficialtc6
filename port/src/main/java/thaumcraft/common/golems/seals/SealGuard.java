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
public class SealGuard implements ISeal, ISealGui, ISealConfigArea {
    int delay = new Random(System.nanoTime()).nextInt(22);
    protected ISealConfigToggles.SealToggle[] props = new ISealConfigToggles.SealToggle[]{
        new ISealConfigToggles.SealToggle(true, "pmob", "golem.prop.mob"),
        new ISealConfigToggles.SealToggle(false, "panimal", "golem.prop.animal"),
        new ISealConfigToggles.SealToggle(false, "pplayer", "golem.prop.player")
    };
    ResourceLocation icon = ResourceLocation.fromNamespaceAndPath("thaumcraft", "item/seals/seal_guard");

    @Override
    public String getKey() {
        return "thaumcraft:guard";
    }

    @Override
    public void tickSeal(Level world, ISealEntity seal) {
        if (this.delay++ % 20 == 0) {
            AABB area = GolemHelper.getBoundsForArea(seal);
            List list = world.getEntitiesOfClass(LivingEntity.class, area);
            if (list.size() > 0) {
                for (Object e : list) {
                    LivingEntity target = (LivingEntity)e;
                    if (this.isValidTarget(target)) {
                        Task task = new Task(seal.getSealPos(), target);
                        task.setPriority(seal.getPriority());
                        task.setLifespan((short)10);
                        TaskHandler.addTask(world.dimension(), task);
                    }
                }
            }
        }
    }

    private boolean isValidTarget(LivingEntity target) {
        boolean valid = false;
        if (this.props[0].value && (target instanceof Enemy || target instanceof Monster)) {
            valid = true;
        }

        if (this.props[1].value && (target instanceof Animal || target instanceof PathfinderMob)) {
            valid = true;
        }

        if (this.props[2].value && (target.level().getServer()!=null && target.level().getServer().isPvpAllowed()) && target instanceof Player) {
            valid = true;
        }

        return valid;
    }

    @Override
    public void onTaskStarted(Level world, IGolemAPI golem, Task task) {
        if (task.getEntity() != null && task.getEntity() instanceof LivingEntity && this.isValidTarget((LivingEntity)task.getEntity())) {
            ((Mob)golem.getGolemEntity()).setTarget((LivingEntity)task.getEntity());
            golem.addRankXp(1);
        }

        task.setSuspended(true);
    }

    @Override
    public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
        task.setSuspended(true);
        return true;
    }

    @Override
    public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
        // Native PathfinderMob includes the golem itself in the animal toggle.
        // Entity.isAlliedTo does not reject self without a scoreboard team.
        return task.getEntity() instanceof LivingEntity target && target.isAlive()
            && target != golem.getGolemEntity() && !golem.getGolemEntity().isAlliedTo(target);
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
        return new EnumGolemTrait[]{EnumGolemTrait.FIGHTER};
    }

    @Override
    public EnumGolemTrait[] getForbiddenTags() {
        return null;
    }

    @Override
    public void onTaskSuspension(Level world, Task task) {
    }

    @Override
    public void readCustomNBT(CompoundTag nbt,HolderLookup.Provider lookup) {
    }

    @Override
    public void writeCustomNBT(CompoundTag nbt,HolderLookup.Provider lookup) {
    }

    @Override
    public void onRemoval(Level world, BlockPos pos, Direction side) {
    }




}
