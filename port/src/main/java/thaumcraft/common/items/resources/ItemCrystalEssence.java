package thaumcraft.common.items.resources;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.ItemGenericEssentiaContainer;

public class ItemCrystalEssence extends ItemGenericEssentiaContainer {
    public ItemCrystalEssence() {
        super(new Item.Properties(), 1);
    }

    /** "%s Vis Crystal" with the aspect name (getItemStackDisplayName in TC6). */
    @Override
    public Component getName(ItemStack stack) {
        AspectList aspects = this.getAspects(stack);
        return aspects != null && !aspects.aspects.isEmpty()
            ? Component.translatable(this.getDescriptionId(stack), aspects.getAspects()[0].getName())
            : super.getName(stack);
    }
}
