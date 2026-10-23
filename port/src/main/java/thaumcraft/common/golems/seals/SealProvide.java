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
public class SealProvide extends SealFiltered implements ISealConfigToggles {
    int delay = new Random(System.nanoTime()).nextInt(88);
    ResourceLocation icon = ResourceLocation.fromNamespaceAndPath("thaumcraft", "item/seals/seal_provider");
    protected ISealConfigToggles.SealToggle[] props = new ISealConfigToggles.SealToggle[]{
        new ISealConfigToggles.SealToggle(true, "pmeta", "golem.prop.meta"),
        new ISealConfigToggles.SealToggle(true, "pnbt", "golem.prop.nbt"),
        new ISealConfigToggles.SealToggle(false, "pore", "golem.prop.ore"),
        new ISealConfigToggles.SealToggle(false, "pmod", "golem.prop.mod"),
        new ISealConfigToggles.SealToggle(false, "psing", "golem.prop.single"),
        new ISealConfigToggles.SealToggle(false, "pleave", "golem.prop.leave")
    };

    @Override
    public String getKey() {
        return "thaumcraft:provider";
    }

    @Override
    public int getFilterSize() {
        return 9;
    }

    @Override
    public void tickSeal(Level world, ISealEntity seal) {
        if (this.delay % 100 == 0 && GolemHelper.provisionRequests.containsKey(world.dimension())) {
            Iterator<ProvisionRequest> it = GolemHelper.provisionRequests.get(world.dimension()).iterator();

            while (it.hasNext()) {
                ProvisionRequest pr = it.next();
                if (pr.isInvalid()
                    || pr.getLinkedTask() == null
                    || pr.getLinkedTask().isSuspended()
                    || pr.getLinkedTask().isCompleted()
                    || pr.getTimeout() < System.currentTimeMillis()) {
                    it.remove();
                }
            }
        }

        if (this.delay++ % 20 == 0) {
            IItemHandler inv = SealInventoryOps.getItemHandlerAt(world, seal.getSealPos().pos, seal.getSealPos().face);
            if (inv != null && GolemHelper.provisionRequests.containsKey(world.dimension())) {
                ListIterator<ProvisionRequest> it = GolemHelper.provisionRequests.get(world.dimension()).listIterator();

                while (it.hasNext()) {
                    ProvisionRequest pr = it.next();
                    if (pr.isInvalid()) {
                        it.remove();
                    } else if (pr.getLinkedTask() == null
                        && (
                            pr.getSeal() != null && pr.getSeal().getSealPos().pos.distSqr(seal.getSealPos().pos) < 4096.0
                                || pr.getEntity() != null
                                    && seal.getSealPos()
                                            .pos
                                            .distToLowCornerSqr(pr.getEntity().getX(), pr.getEntity().getY(), pr.getEntity().getZ())
                                        < 4096.0
                                || pr.getPos() != null && seal.getSealPos().pos.distSqr(pr.getPos()) < 4096.0
                        )) {
                        NonNullList<ItemStack> stacks = NonNullList.withSize(1, pr.getStack());
                        if (!SealInventoryOps.findFirstMatchFromFilter(
                                    this.getInv(),
                                    this.getSizes(),
                                    this.blacklist,
                                    stacks,
                                    new SealInventoryOps.Filter(!this.props[0].value, !this.props[1].value, this.props[2].value, this.props[3].value)
                                )
                                .isEmpty()
                            && SealInventoryOps.countTotalItemsIn(inv, pr.getStack(), SealInventoryOps.Filter.STRICT) > (this.props[5].value ? 1 : 0)) {
                            Task task = new Task(seal.getSealPos(), seal.getSealPos().pos);
                            task.setPriority(pr.getSeal() != null ? pr.getSeal().getPriority() : 5);
                            task.setLifespan((short)(pr.getSeal() != null ? 10 : 31000));
                            TaskHandler.addTask(world.dimension(), task);
                            pr.setLinkedTask(task);
                            task.setLinkedProvision(pr);
                            break;
                        }
                    }
                }
            }
        }
    }

    public boolean matchesFilters(ItemStack stack) {
        return SealInventoryOps.matchesFilters(
            this.getInv(),
            this.blacklist,
            stack,
            new SealInventoryOps.Filter(!this.props[0].value, !this.props[1].value, this.props[2].value, this.props[3].value)
        );
    }

    @Override
    public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
        if (task.getLinkedProvision() != null) {
            if (task.getData() == 0) {
                IItemHandler inv = SealInventoryOps.getItemHandlerAt(world, task.getSealPos().pos, task.getSealPos().face);
                if (inv != null) {
                    ItemStack stack = ItemStack.EMPTY;

                    try {
                        stack = task.getLinkedProvision().getStack().copy();
                    } catch (Exception var11) {
                    }

                    if (stack != null && this.props[4].value) {
                        stack.setCount(1);
                    }

                    int sa = 0;
                    if (stack != null
                        && !stack.isEmpty()
                        && this.props[5].value
                        && (sa = SealInventoryOps.countTotalItemsIn(inv, stack, SealInventoryOps.Filter.STRICT)) <= stack.getCount()) {
                        stack.setCount(sa - 1);
                    }

                    if (stack != null && !stack.isEmpty()) {
                        int limit = golem.canCarryAmount(stack);
                        if (limit > 0) {
                            ItemStack s = golem.holdItem(
                                SealInventoryOps.removeStackFrom(inv, SealInventoryOps.copyLimitedStack(stack, limit), SealInventoryOps.Filter.STRICT, false)
                            );
                            if (s != null && !s.isEmpty()) {
                                SealInventoryOps.ejectStackAt(
                                    world, task.getSealPos().pos.relative(task.getSealPos().face), task.getSealPos().face.getOpposite(), s
                                );
                            }

                            golem.getGolemEntity()
                                .playSound(
                                    SoundEvents.ITEM_PICKUP,
                                    0.125F,
                                    ((world.random.nextFloat() - world.random.nextFloat()) * 0.7F + 1.0F) * 2.0F
                                );
                            golem.addRankXp(1);
                            golem.swingArm();
                            ProvisionRequest pr2 = task.getLinkedProvision();
                            if (pr2.getEntity() != null || pr2.getPos() != null) {
                                Task task2 = null;
                                if (pr2.getEntity() != null) {
                                    task2 = new Task(task.getSealPos(), pr2.getEntity());
                                } else {
                                    task2 = new Task(task.getSealPos(), pr2.getPos());
                                }

                                task2.setPriority(task.getPriority());
                                task2.setData(pr2.getEntity() != null ? 1 : 2);
                                task2.setLifespan((short)31000);
                                TaskHandler.addTask(world.dimension(), task2);
                                pr2.setLinkedTask(task2);
                                task2.setLinkedProvision(pr2);
                            }
                        }
                    }
                }
            } else if (task.getLinkedProvision() != null) {
                ProvisionRequest pr = task.getLinkedProvision();
                ItemStack cs = pr.getStack();
                ItemStack s = golem.dropItem(cs);
                if (s.getCount() < cs.getCount()) {
                    ItemStack ps = cs.copy();
                    ps.setCount(cs.getCount() - s.getCount());
                    if (task.getData() == 1) {
                        GolemHelper.requestProvisioning(world, pr.getEntity(), ps);
                    } else {
                        GolemHelper.requestProvisioning(world, pr.getPos(), pr.getSide(), ps);
                    }
                }

                if (task.getData() == 1) {
                    SealInventoryOps.dropItemAtEntity(world, s, pr.getEntity());
                } else {
                    ItemStack back = SealInventoryOps.ejectStackAt(world, pr.getPos().relative(pr.getSide()), pr.getSide().getOpposite(), s, true);
                    if (!back.isEmpty()) {
                        golem.holdItem(back);
                    }
                }

                golem.getGolemEntity()
                    .playSound(
                        SoundEvents.ITEM_PICKUP, 0.125F, ((world.random.nextFloat() - world.random.nextFloat()) * 0.7F + 1.0F) * 1.0F
                    );
                golem.swingArm();
                pr.setInvalid(true);
            }
        }

        task.setSuspended(true);
        return true;
    }

    @Override
    public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
        ProvisionRequest pr = task.getLinkedProvision();
        boolean b = pr != null
            && (
                pr.getSeal() != null && ((EntityThaumcraftGolem)golem.getGolemEntity()).isWithinRestriction(pr.getSeal().getSealPos().pos)
                    || pr.getEntity() != null && ((EntityThaumcraftGolem)golem.getGolemEntity()).isWithinRestriction(pr.getEntity().blockPosition())
                    || pr.getPos() != null && ((EntityThaumcraftGolem)golem.getGolemEntity()).isWithinRestriction(pr.getPos())
            );
        return task.getData() == 0
            ? b
                && this.areGolemTagsValidForTask(pr.getSeal(), golem)
                && pr.getStack() != null
                && !golem.isCarrying(pr.getStack())
                && golem.canCarry(pr.getStack(), true)
            : b && this.areGolemTagsValidForTask(pr.getSeal(), golem) && pr.getStack() != null && golem.isCarrying(pr.getStack());
    }

    private boolean areGolemTagsValidForTask(ISealEntity se, IGolemAPI golem) {
        if (se == null) {
            return true;
        }

        if (se.isLocked() && (! (golem.getGolemEntity() instanceof OwnableEntity owned) || owned.getOwnerUUID()==null || !owned.getOwnerUUID().toString().equals(se.getOwner()))) {
            return false;
        }

        if (se.getSeal().getRequiredTags() != null && !golem.getProperties().getTraits().containsAll(Arrays.asList(se.getSeal().getRequiredTags()))) {
            return false;
        }

        if (se.getSeal().getForbiddenTags() != null) {
            for (EnumGolemTrait tag : se.getSeal().getForbiddenTags()) {
                if (golem.getProperties().getTraits().contains(tag)) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public void onTaskSuspension(Level world, Task task) {
        if (task.getLinkedProvision() != null) {
            task.getLinkedProvision().setLinkedTask(null);
        }

        task.setLinkedProvision(null);
    }

    @Override
    public boolean canPlaceAt(Level world, BlockPos pos, Direction side) {
        IItemHandler inv = SealInventoryOps.getItemHandlerAt(world, pos, side);
        return inv != null;
    }

    @Override
    public ResourceLocation getSealIcon() {
        return this.icon;
    }

    @Override
    public int[] getGuiCategories() {
        return new int[]{1, 3, 0, 4};
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
    public void onTaskStarted(Level world, IGolemAPI golem, Task task) {
    }

    @Override
    public void onRemoval(Level world, BlockPos pos, Direction side) {
    }

    @Override
    public ISealConfigToggles.SealToggle[] getToggles() {
        return this.props;
    }

    @Override
    public void setToggle(int indx, boolean value) {
        this.props[indx].setValue(value);
    }
}
