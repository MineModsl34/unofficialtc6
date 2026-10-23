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
public class SealFillAdvanced extends SealFill implements ISealConfigToggles {
    ResourceLocation icon = ResourceLocation.fromNamespaceAndPath("thaumcraft", "item/seals/seal_fill_advanced");

    @Override
    public String getKey() {
        return "thaumcraft:fill_advanced";
    }

    @Override
    public int getFilterSize() {
        return 9;
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
    public ISealConfigToggles.SealToggle[] getToggles() {
        return this.props;
    }

    @Override
    public void setToggle(int indx, boolean value) {
        this.props[indx].setValue(value);
    }

    @Override
    public EnumGolemTrait[] getRequiredTags() {
        return new EnumGolemTrait[]{EnumGolemTrait.SMART};
    }
}

