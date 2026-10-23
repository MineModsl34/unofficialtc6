package thaumcraft.common.items;

import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.items.ItemsTC;

/** Exact TC6 material values; 1.21 armor durability belongs to each item, not the material registry. */
public final class TCGearMaterials {
    public static Holder<ArmorMaterial> THAUMIUM;
    public static Holder<ArmorMaterial> VOID;
    public static Holder<ArmorMaterial> CULTIST_PLATE;
    public static Holder<ArmorMaterial> CULTIST_ROBE;
    public static final Tier TOOLMAT_THAUMIUM = new GearTier(500, 7.0F, 2.5F, 22, false);
    public static final Tier TOOLMAT_VOID = new GearTier(150, 8.0F, 3.0F, 10, true);

    private TCGearMaterials() {
    }

    public static void register(RegisterEvent.RegisterHelper<ArmorMaterial> registry) {
        ResourceLocation thaumium = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "thaumium");
        ResourceLocation voidMetal = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "void");
        registry.register(thaumium, new ArmorMaterial(
            defense(2, 5, 6, 2), 25, SoundEvents.ARMOR_EQUIP_IRON,
            () -> Ingredient.of(ItemsTC.ingotThaumium), List.of(new ArmorMaterial.Layer(thaumium)), 1.0F, 0.0F));
        registry.register(voidMetal, new ArmorMaterial(
            defense(3, 6, 8, 3), 10, SoundEvents.ARMOR_EQUIP_CHAIN,
            () -> Ingredient.of(ItemsTC.ingotVoid), List.of(new ArmorMaterial.Layer(voidMetal)), 1.0F, 0.0F));
        THAUMIUM = BuiltInRegistries.ARMOR_MATERIAL.getHolderOrThrow(ResourceKey.create(Registries.ARMOR_MATERIAL, thaumium));
        VOID = BuiltInRegistries.ARMOR_MATERIAL.getHolderOrThrow(ResourceKey.create(Registries.ARMOR_MATERIAL, voidMetal));
        ResourceLocation cultPlate = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "cultist_plate");
        ResourceLocation cultRobe = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "cultist_robe");
        registry.register(cultPlate, new ArmorMaterial(defense(2, 5, 6, 2), 13, SoundEvents.ARMOR_EQUIP_IRON,
            () -> Ingredient.of(net.minecraft.world.item.Items.IRON_INGOT), List.of(new ArmorMaterial.Layer(cultPlate)), 0, 0));
        registry.register(cultRobe, new ArmorMaterial(defense(2, 4, 5, 2), 13, SoundEvents.ARMOR_EQUIP_CHAIN,
            () -> Ingredient.of(net.minecraft.world.item.Items.IRON_INGOT), List.of(new ArmorMaterial.Layer(cultRobe)), 0, 0));
        CULTIST_PLATE = BuiltInRegistries.ARMOR_MATERIAL.getHolderOrThrow(ResourceKey.create(Registries.ARMOR_MATERIAL, cultPlate));
        CULTIST_ROBE = BuiltInRegistries.ARMOR_MATERIAL.getHolderOrThrow(ResourceKey.create(Registries.ARMOR_MATERIAL, cultRobe));
    }

    private static Map<ArmorItem.Type, Integer> defense(int boots, int legs, int chest, int helm) {
        return Map.of(ArmorItem.Type.BOOTS, boots, ArmorItem.Type.LEGGINGS, legs,
            ArmorItem.Type.CHESTPLATE, chest, ArmorItem.Type.HELMET, helm);
    }

    private record GearTier(int getUses, float getSpeed, float getAttackDamageBonus,
                            int getEnchantmentValue, boolean voidMetal) implements Tier {
        @Override
        public TagKey<Block> getIncorrectBlocksForDrops() {
            // Removed harvest-level ints: TC6 levels 3/4 map to modern diamond/netherite suitability tags.
            return voidMetal ? BlockTags.INCORRECT_FOR_NETHERITE_TOOL : BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(voidMetal ? ItemsTC.ingotVoid : ItemsTC.ingotThaumium);
        }
    }
}
