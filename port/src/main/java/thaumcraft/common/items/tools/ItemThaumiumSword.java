package thaumcraft.common.items.tools;

import net.minecraft.world.item.SwordItem;
import thaumcraft.common.items.TCGearMaterials;

public class ItemThaumiumSword extends SwordItem {
    public ItemThaumiumSword() {
        super(TCGearMaterials.TOOLMAT_THAUMIUM, new Properties()
            .attributes(SwordItem.createAttributes(TCGearMaterials.TOOLMAT_THAUMIUM, 3, -2.4F)));
    }
}
