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
public class SealFill extends SealFiltered {
    int delay = new Random(System.nanoTime()).nextInt(50);
    int watchedTask = Integer.MIN_VALUE;
    protected ISealConfigToggles.SealToggle[] props = new ISealConfigToggles.SealToggle[]{
        new ISealConfigToggles.SealToggle(true, "pmeta", "golem.prop.meta"),
        new ISealConfigToggles.SealToggle(true, "pnbt", "golem.prop.nbt"),
        new ISealConfigToggles.SealToggle(false, "pore", "golem.prop.ore"),
        new ISealConfigToggles.SealToggle(false, "pmod", "golem.prop.mod"),
        new ISealConfigToggles.SealToggle(false, "pexist", "golem.prop.exist")
    };
    ResourceLocation icon = ResourceLocation.fromNamespaceAndPath("thaumcraft", "item/seals/seal_fill");

    @Override
    public String getKey() {
        return "thaumcraft:fill";
    }

    @Override
    public void tickSeal(Level world, ISealEntity seal) {
        if (this.delay++ % 20 == 0) {
            Task oldTask = TaskHandler.getTask(world.dimension(), this.watchedTask);
            if (oldTask == null || oldTask.isReserved() || oldTask.isSuspended() || oldTask.isCompleted()) {
                Task task = new Task(seal.getSealPos(), seal.getSealPos().pos);
                task.setPriority(seal.getPriority());
                TaskHandler.addTask(world.dimension(), task);
                this.watchedTask = task.getId();
            }
        }
    }

    @Override
    public void onTaskStarted(Level world, IGolemAPI golem, Task task) {
        ISealEntity se = SealHandler.getSealEntity(world.dimension(), task.getSealPos());
        if (se != null && !se.isStoppedByRedstone(world)) {
            Task newTask = new Task(task.getSealPos(), task.getSealPos().pos);
            newTask.setPriority(se.getPriority());
            TaskHandler.addTask(world.dimension(), newTask);
            this.watchedTask = newTask.getId();
        }
    }

    @Override
    public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
        SealInventoryOps.Filter filter = new SealInventoryOps.Filter(
            !this.props[0].value, !this.props[1].value, this.props[2].value, this.props[3].value
        );
        Tuple<ItemStack, Integer> tuple = SealInventoryOps.findFirstMatchFromFilterTuple(
            this.getInv(), this.getSizes(), this.isBlacklist(), golem.getCarrying(), filter
        );
        if (tuple.getA() != null && !((ItemStack)tuple.getA()).isEmpty()) {
            IItemHandler inv = SealInventoryOps.getItemHandlerAt(world, task.getSealPos().pos, task.getSealPos().face);
            int limit = ((ItemStack)tuple.getA()).getCount();
            if (this.hasStacksizeLimiters() && tuple.getB() != null && (Integer)tuple.getB() > 0) {
                int c = inv == null
                    ? SealInventoryOps.countStackInWorld(golem.getGolemWorld(), task.getSealPos().pos, (ItemStack)tuple.getA(), 1.5, filter)
                    : SealInventoryOps.countTotalItemsIn(inv, (ItemStack)tuple.getA(), filter);
                if (c < (Integer)tuple.getB()) {
                    limit = (Integer)tuple.getB() - c;
                } else {
                    limit = 0;
                }
            }

            if (limit > 0) {
                ItemStack t = ((ItemStack)tuple.getA()).copy();
                t.setCount(limit);
                ItemStack s = golem.dropItem(t);
                if (inv == null) {
                    ItemEntity ie = new ItemEntity(
                        world,
                        task.getSealPos().pos.getX() + 0.5 + task.getSealPos().face.getStepX(),
                        task.getSealPos().pos.getY() + 0.5 + task.getSealPos().face.getStepY(),
                        task.getSealPos().pos.getZ() + 0.5 + task.getSealPos().face.getStepZ(),
                        s
                    );
                    ie.setDeltaMovement(ie.getDeltaMovement().multiply(.2,.5,.2));
                    if (!world.addFreshEntity(ie)) golem.holdItem(s);
                } else {
                    golem.holdItem(ItemHandlerHelper.insertItemStacked(inv, s, false));
                }

                golem.getGolemEntity()
                    .playSound(
                        SoundEvents.ITEM_PICKUP, 0.125F, ((world.random.nextFloat() - world.random.nextFloat()) * 0.7F + 1.0F) * 1.0F
                    );
                golem.addRankXp(1);
                golem.swingArm();
            }
        }

        task.setSuspended(true);
        return true;
    }

    @Override
    public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
        SealInventoryOps.Filter filter = new SealInventoryOps.Filter(
            !this.props[0].value, !this.props[1].value, this.props[2].value, this.props[3].value
        );
        Tuple<ItemStack, Integer> tuple = SealInventoryOps.findFirstMatchFromFilterTuple(
            this.getInv(), this.getSizes(), this.isBlacklist(), golem.getCarrying(), filter
        );
        if (tuple.getA() != null && !((ItemStack)tuple.getA()).isEmpty()) {
            IItemHandler inv = SealInventoryOps.getItemHandlerAt(golem.getGolemWorld(), task.getSealPos().pos, task.getSealPos().face);
            if (inv != null) {
                if (tuple.getA() != null
                    && !((ItemStack)tuple.getA()).isEmpty()
                    && this.props[4].value
                    && SealInventoryOps.countTotalItemsIn(inv, (ItemStack)tuple.getA(), filter) <= 0) {
                    return false;
                }

                if (tuple.getA() != null
                    && !((ItemStack)tuple.getA()).isEmpty()
                    && SealInventoryOps.hasRoomForSome(golem.getGolemWorld(), task.getSealPos().pos, task.getSealPos().face, (ItemStack)tuple.getA())
                    )
                 {
                    if (!this.hasStacksizeLimiters() || tuple.getB() == null || (Integer)tuple.getB() <= 0) {
                        return true;
                    }

                    if (SealInventoryOps.countTotalItemsIn(inv, (ItemStack)tuple.getA(), filter) < (Integer)tuple.getB()) {
                        return true;
                    }
                }
            } else if (tuple.getA() != null && !((ItemStack)tuple.getA()).isEmpty()) {
                if (this.hasStacksizeLimiters() && tuple.getB() != null && (Integer)tuple.getB() > 0) {
                    return SealInventoryOps.countStackInWorld(golem.getGolemWorld(), task.getSealPos().pos, (ItemStack)tuple.getA(), 1.5, filter)
                        < (Integer)tuple.getB();
                }

                return true;
            }
        }

        return false;
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
        return new int[]{1, 0, 4};
    }

    @Override
    public EnumGolemTrait[] getRequiredTags() {
        return null;
    }

    @Override
    public EnumGolemTrait[] getForbiddenTags() {
        return new EnumGolemTrait[]{EnumGolemTrait.CLUMSY};
    }

    @Override
    public void onTaskSuspension(Level world, Task task) {
    }

    @Override
    public void onRemoval(Level world, BlockPos pos, Direction side) {
    }

    @Override
    public boolean hasStacksizeLimiters() {
        return !this.isBlacklist();
    }
}
