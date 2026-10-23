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
public class SealPickup extends SealFiltered implements ISealConfigArea {
    int delay = new Random(System.nanoTime()).nextInt(100);
    HashMap<Integer, Integer> itemEntities = new HashMap<>();
    ResourceLocation icon = ResourceLocation.fromNamespaceAndPath("thaumcraft", "item/seals/seal_pickup");
    protected ISealConfigToggles.SealToggle[] props = new ISealConfigToggles.SealToggle[]{
        new ISealConfigToggles.SealToggle(true, "pmeta", "golem.prop.meta"),
        new ISealConfigToggles.SealToggle(true, "pnbt", "golem.prop.nbt"),
        new ISealConfigToggles.SealToggle(false, "pore", "golem.prop.ore"),
        new ISealConfigToggles.SealToggle(false, "pmod", "golem.prop.mod")
    };

    @Override
    public String getKey() {
        return "thaumcraft:pickup";
    }

    @Override
    public void tickSeal(Level world, ISealEntity seal) {
        if (this.delay++ % 5 == 0) {
            AABB area = GolemHelper.getBoundsForArea(seal);
            List list = world.getEntitiesOfClass(ItemEntity.class, area);
            if (list.size() > 0) {
                for (Object e : list) {
                    ItemEntity ent = (ItemEntity)e;
                    if (ent != null
                        && ent.onGround()
                        && !ent.hasPickUpDelay()
                        && ent.getItem() != null
                        && !this.itemEntities.containsValue(ent.getId())) {
                        ItemStack stack = SealInventoryOps.findFirstMatchFromFilter(
                            this.filter,
                            this.filterSize,
                            this.isBlacklist(),
                            NonNullList.withSize(1, ent.getItem()),
                            new SealInventoryOps.Filter(!this.props[0].value, !this.props[1].value, this.props[2].value, this.props[3].value)
                        );
                        if (stack != null && !stack.isEmpty()) {
                            Task task = new Task(seal.getSealPos(), ent);
                            task.setPriority(seal.getPriority());
                            this.itemEntities.put(task.getId(), ent.getId());
                            TaskHandler.addTask(world.dimension(), task);
                            break;
                        }
                    }
                }
            }

            if (this.delay % 100 != 0) {
                Iterator<Integer> it = this.itemEntities.values().iterator();

                while (it.hasNext()) {
                    Entity e = world.getEntity(it.next());
                    if (e == null || !e.isAlive()) {
                        try {
                            it.remove();
                        } catch (Exception var10) {
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
        ItemEntity ei = this.getItemEntity(world, task);
        if (ei != null && !ei.getItem().isEmpty()) {
            ItemStack stack = SealInventoryOps.findFirstMatchFromFilter(
                this.filter,
                this.filterSize,
                this.isBlacklist(),
                NonNullList.withSize(1, ei.getItem()),
                new SealInventoryOps.Filter(!this.props[0].value, !this.props[1].value, this.props[2].value, this.props[3].value)
            );
            if (stack != null && !stack.isEmpty()) {
                ItemStack is = golem.holdItem(ei.getItem());
                if (is != null && !is.isEmpty() && is.getCount() > 0) {
                    ei.setItem(is);
                }

                if (is == null || is.isEmpty() || is.getCount() <= 0) {
                    ei.discard();
                }

                golem.getGolemEntity()
                    .playSound(
                        SoundEvents.ITEM_PICKUP, 0.125F, ((world.random.nextFloat() - world.random.nextFloat()) * 0.7F + 1.0F) * 2.0F
                    );
                golem.swingArm();
            }
        }

        task.setSuspended(true);
        this.itemEntities.remove(task.getId());

        for (Task ticket : TaskHandler.getEntityTasksSorted(world.dimension(), null, golem.getGolemEntity())) {
            if (this.itemEntities.containsKey(ticket.getId())
                && ticket.canGolemPerformTask(golem)
                && ((EntityThaumcraftGolem)golem.getGolemEntity()).isWithinRestriction(ticket.getEntity().blockPosition())) {
                ((EntityThaumcraftGolem)golem.getGolemEntity()).setTask(ticket);
                ((EntityThaumcraftGolem)golem.getGolemEntity()).getTask().setReserved(true);
                if (thaumcraft.common.config.ModConfig.CONFIG_GRAPHICS.showGolemEmotes) {
                    world.broadcastEntityEvent(golem.getGolemEntity(), (byte)5);
                }
                break;
            }
        }

        return true;
    }

    protected ItemEntity getItemEntity(Level world, Task task) {
        Integer ei = this.itemEntities.get(task.getId());
        if (ei != null) {
            Entity ent = world.getEntity(ei);
            if (ent != null && ent instanceof ItemEntity) {
                return (ItemEntity)ent;
            }
        }

        return null;
    }

    @Override
    public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
        ItemEntity ei = this.getItemEntity(golem.getGolemWorld(), task);
        if (ei == null || ei.getItem() == null) {
            return false;
        } else if (!ei.isAlive()) {
            task.setSuspended(true);
            return false;
        } else {
            return golem.canCarry(ei.getItem(), true);
        }
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
        return new int[]{2, 1, 0, 4};
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
    }

    @Override
    public void onRemoval(Level world, BlockPos pos, Direction side) {
    }
}
