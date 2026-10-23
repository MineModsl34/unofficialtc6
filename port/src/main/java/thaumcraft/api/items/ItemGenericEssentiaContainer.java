package thaumcraft.api.items;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;

/** Aspects are stored in the {@link TCDataComponents#ASPECTS} component instead of NBT "Aspects". */
public class ItemGenericEssentiaContainer extends Item implements IEssentiaContainerItem {
    protected int base = 1;

    public ItemGenericEssentiaContainer(Item.Properties properties, int base) {
        super(properties.stacksTo(64));
        this.base = base;
    }

    /** Analogue of getSubItems: one stack per aspect. */
    public List<ItemStack> getTabVariants() {
        List<ItemStack> out = new ArrayList<>();
        for (Aspect tag : Aspect.aspects.values()) {
            ItemStack i = new ItemStack(this);
            this.setAspects(i, new AspectList().add(tag, this.base));
            out.add(i);
        }
        return out;
    }

    @Override
    public AspectList getAspects(ItemStack itemstack) {
        AspectList aspects = itemstack.get(TCDataComponents.ASPECTS);
        return aspects != null && aspects.size() > 0 ? aspects.copy() : null;
    }

    @Override
    public void setAspects(ItemStack itemstack, AspectList aspects) {
        itemstack.set(TCDataComponents.ASPECTS, aspects.copy());
    }

    @Override
    public boolean ignoreContainedAspects() {
        return false;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {
        if (!world.isClientSide && !stack.has(TCDataComponents.ASPECTS)) {
            Aspect[] displayAspects = Aspect.aspects.values().toArray(new Aspect[0]);
            this.setAspects(stack, new AspectList().add(displayAspects[world.random.nextInt(displayAspects.length)], this.base));
        }

        super.inventoryTick(stack, world, entity, slot, selected);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level world, Player player) {
        if (!world.isClientSide && !stack.has(TCDataComponents.ASPECTS)) {
            Aspect[] displayAspects = Aspect.aspects.values().toArray(new Aspect[0]);
            this.setAspects(stack, new AspectList().add(displayAspects[world.random.nextInt(displayAspects.length)], this.base));
        }
    }
}
