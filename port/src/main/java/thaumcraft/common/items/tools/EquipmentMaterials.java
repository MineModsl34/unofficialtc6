package thaumcraft.common.items.tools;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import thaumcraft.api.items.ItemsTC;

/** Independent source special materials; never mutates the existing sword/armor materials. */
public final class EquipmentMaterials {
    public static final Tier ELEMENTAL = new SourceTier(1500,9,3,18,false);
    public static final Tier PRIMAL = new SourceTier(500,8,4,20,true);
    public static final Tier CRIMSON = new SourceTier(200,8,3.5F,20,true);
    private EquipmentMaterials() {}
    private record SourceTier(int getUses,float getSpeed,float getAttackDamageBonus,int getEnchantmentValue,boolean voidMetal) implements Tier {
        @Override public TagKey<Block> getIncorrectBlocksForDrops(){return voidMetal?BlockTags.INCORRECT_FOR_NETHERITE_TOOL:BlockTags.INCORRECT_FOR_DIAMOND_TOOL;}
        @Override public Ingredient getRepairIngredient(){return Ingredient.of(voidMetal?ItemsTC.ingotVoid:ItemsTC.ingotThaumium);}
    }
}
