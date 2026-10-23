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
public class SealStock extends SealFiltered implements ISealConfigToggles {
    int delay = new Random(System.nanoTime()).nextInt(50);
    protected ISealConfigToggles.SealToggle[] props = new ISealConfigToggles.SealToggle[]{
        new ISealConfigToggles.SealToggle(true, "pmeta", "golem.prop.meta"),
        new ISealConfigToggles.SealToggle(true, "pnbt", "golem.prop.nbt"),
        new ISealConfigToggles.SealToggle(false, "pore", "golem.prop.ore"),
        new ISealConfigToggles.SealToggle(false, "pmod", "golem.prop.mod")
    };
    ResourceLocation icon = ResourceLocation.fromNamespaceAndPath("thaumcraft", "item/seals/seal_stock");

    @Override
    public String getKey() {
        return "thaumcraft:stock";
    }

    @Override
    public int getFilterSize() {
        return 9;
    }

    @Override
    public void tickSeal(Level world, ISealEntity seal) {
        if (this.delay++ % 20 == 0) {
            IItemHandler inv = SealInventoryOps.getItemHandlerAt(world, seal.getSealPos().pos, seal.getSealPos().face);
            if (inv != null) {
                for (int a = 0; a < 9; a++) {
                    int amt = SealInventoryOps.countTotalItemsIn(
                        inv,
                        this.getFilterSlot(a),
                        new SealInventoryOps.Filter(!this.props[0].value, !this.props[1].value, this.props[2].value, this.props[3].value)
                    );
                    if (amt < this.getFilterSlotSize(a)) {
                        ItemStack fs = this.getFilterSlot(a).copy();
                        fs.setCount(Math.min(fs.getMaxStackSize(), this.getFilterSlotSize(a) - amt));
                        fs = SealInventoryOps.hasRoomFor(world, seal.getSealPos().pos, seal.getSealPos().face, fs);
                        if (!fs.isEmpty()) {
                            GolemHelper.requestProvisioning(world, seal.getSealPos().pos, seal.getSealPos().face, fs);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onTaskStarted(Level world, IGolemAPI golem, Task task) {
    }

    @Override
    public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
        return true;
    }

    @Override
    public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
        return false;
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
    public void onTaskSuspension(Level world, Task task) {
    }

    @Override
    public void onRemoval(Level world, BlockPos pos, Direction side) {
    }

    @Override
    public boolean hasStacksizeLimiters() {
        return true;
    }

    @Override
    public boolean isBlacklist() {
        return false;
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

