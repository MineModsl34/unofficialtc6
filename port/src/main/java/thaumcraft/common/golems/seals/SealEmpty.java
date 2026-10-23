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
public class SealEmpty extends SealFiltered {
    int delay = new Random(System.nanoTime()).nextInt(30);
    int filterInc = 0;
    HashMap<Integer, ItemStack> cache = new HashMap<>();
    ResourceLocation icon = ResourceLocation.fromNamespaceAndPath("thaumcraft", "item/seals/seal_empty");
    protected ISealConfigToggles.SealToggle[] props = new ISealConfigToggles.SealToggle[]{
        new ISealConfigToggles.SealToggle(true, "pmeta", "golem.prop.meta"),
        new ISealConfigToggles.SealToggle(true, "pnbt", "golem.prop.nbt"),
        new ISealConfigToggles.SealToggle(false, "pore", "golem.prop.ore"),
        new ISealConfigToggles.SealToggle(false, "pmod", "golem.prop.mod"),
        new ISealConfigToggles.SealToggle(false, "pcycle", "golem.prop.cycle"),
        new ISealConfigToggles.SealToggle(false, "pleave", "golem.prop.leave")
    };

    @Override
    public String getKey() {
        return "thaumcraft:empty";
    }

    @Override
    public void tickSeal(Level world, ISealEntity seal) {
        if (this.delay % 100 == 0) {
            Iterator<Integer> it = this.cache.keySet().iterator();

            while (it.hasNext()) {
                Task t = TaskHandler.getTask(world.dimension(), it.next());
                if (t == null) {
                    it.remove();
                }
            }
        }

        if (this.delay++ % 20 == 0) {
            ItemStack stack = SealInventoryOps.findFirstMatchFromFilter(
                this.getInv(this.filterInc),
                this.isBlacklist(),
                SealInventoryOps.getItemHandlerAt(world, seal.getSealPos().pos, seal.getSealPos().face),
                seal.getSealPos().face,
                new SealInventoryOps.Filter(!this.props[0].value, !this.props[1].value, this.props[2].value, this.props[3].value),
                this.props[5].value
            );
            if (stack != null && !stack.isEmpty()) {
                Task task = new Task(seal.getSealPos(), seal.getSealPos().pos);
                task.setPriority(seal.getPriority());
                task.setLifespan((short)5);
                TaskHandler.addTask(world.dimension(), task);
                this.cache.put(task.getId(), stack);
            }
        }
    }

    @Override
    public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
        ItemStack stack = this.cache.get(task.getId());
        int sa = SealInventoryOps.countTotalItemsIn(
            world,
            task.getSealPos().pos,
            task.getSealPos().face,
            stack,
            new SealInventoryOps.Filter(!this.props[0].value, !this.props[1].value, this.props[2].value, this.props[3].value)
        );
        if (stack != null && !stack.isEmpty() && this.props[5].value && sa <= stack.getCount()) {
            stack = stack.copy();
            stack.setCount(sa - 1);
        }

        if (stack != null && !stack.isEmpty()) {
            int limit = golem.canCarryAmount(stack);
            if (limit > 0) {
                ItemStack s = golem.holdItem(
                    SealInventoryOps.removeStackFrom(
                        world,
                        task.getSealPos().pos,
                        task.getSealPos().face,
                        SealInventoryOps.copyLimitedStack(stack, limit),
                        new SealInventoryOps.Filter(!this.props[0].value, !this.props[1].value, this.props[2].value, this.props[3].value),
                        false
                    )
                );
                if (!s.isEmpty()) {
                    SealInventoryOps.ejectStackAt(world, task.getSealPos().pos.relative(task.getSealPos().face), task.getSealPos().face.getOpposite(), s);
                }

                golem.getGolemEntity()
                    .playSound(
                        SoundEvents.ITEM_PICKUP, 0.125F, ((world.random.nextFloat() - world.random.nextFloat()) * 0.7F + 1.0F) * 2.0F
                    );
                golem.swingArm();
            }
        }

        this.cache.remove(task.getId());
        this.filterInc++;
        task.setSuspended(true);
        return true;
    }

    @Override
    public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
        ItemStack stack = this.cache.get(task.getId());
        return stack != null && !stack.isEmpty() && golem.canCarry(stack, true);
    }

    @Override
    public boolean canPlaceAt(Level world, BlockPos pos, Direction side) {
        IItemHandler inv = SealInventoryOps.getItemHandlerAt(world, pos, side);
        return inv != null;
    }

    public NonNullList<ItemStack> getInv(int c) {
        return super.getInv();
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
    public void onTaskStarted(Level world, IGolemAPI golem, Task task) {
    }

    @Override
    public void onTaskSuspension(Level world, Task task) {
        this.cache.remove(task.getId());
    }

    @Override
    public void onRemoval(Level world, BlockPos pos, Direction side) {
    }
}

