package thaumcraft.gametest;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.items.GearHelper;
import thaumcraft.api.items.IVisDiscountGear;
import thaumcraft.api.items.IWarpingGear;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.items.armor.ItemCultistBoots;
import thaumcraft.common.items.armor.ItemCultistPlateArmor;
import thaumcraft.common.items.armor.ItemCultistRobeArmor;

@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class CultistArmorGameTests {
    @GameTest(template = "empty")
    public static void allSevenLiveItemsKeepSourceDurabilityDefenseRepairRarityAndDistinctInterfaces(GameTestHelper helper) {
        int[] base = {11, 16, 15}, plateDefense = {2, 6, 5}, robeDefense = {2, 5, 4};
        var plate = List.of(ItemsTC.crimsonPlateHelm, ItemsTC.crimsonPlateChest, ItemsTC.crimsonPlateLegs);
        var robe = List.of(ItemsTC.crimsonRobeHelm, ItemsTC.crimsonRobeChest, ItemsTC.crimsonRobeLegs);
        for (int index = 0; index < 3; index++) {
            helper.assertTrue(plate.get(index) instanceof ItemCultistPlateArmor && robe.get(index) instanceof ItemCultistRobeArmor, "root actual source item class registration");
            properties(helper, plate.get(index), base[index] * 18, plateDefense[index], 13);
            properties(helper, robe.get(index), base[index] * 17, robeDefense[index], 13);
            helper.assertFalse(plate.get(index) instanceof IWarpingGear || plate.get(index) instanceof IVisDiscountGear, "plate must not gain robe-only interface semantics");
            helper.assertTrue(((IVisDiscountGear) robe.get(index)).getVisDiscount(new ItemStack(robe.get(index)), null) == 1
                && ((IWarpingGear) robe.get(index)).getWarp(new ItemStack(robe.get(index)), null) == 1, "source each robe 1% discount and1 warp");
            helper.assertTrue(BuiltInRegistries.ITEM.getKey(plate.get(index)).getPath().startsWith("crimson_plate_")
                && BuiltInRegistries.ITEM.getKey(robe.get(index)).getPath().startsWith("crimson_robe_"), "actual source IDs not substitute vanilla pieces");
        }
        helper.assertTrue(ItemsTC.crimsonBoots instanceof ItemCultistBoots && ((ArmorItem) ItemsTC.crimsonBoots).getMaterial().equals(ArmorMaterials.IRON), "source boots genuinely vanilla IRON material");
        properties(helper, ItemsTC.crimsonBoots, 195, 2, 9);
        helper.assertTrue(((IWarpingGear) ItemsTC.crimsonBoots).getWarp(new ItemStack(ItemsTC.crimsonBoots), null) == 1
            && ((IVisDiscountGear) ItemsTC.crimsonBoots).getVisDiscount(new ItemStack(ItemsTC.crimsonBoots), null) == 1, "source boots1warp/1percent");
        helper.succeed();
    }
    private static void properties(GameTestHelper helper, Item item, int durability, int defense, int enchantment) {
        ItemStack stack = new ItemStack(item); ArmorItem armor = (ArmorItem) item;
        helper.assertTrue(stack.getMaxDamage() == durability && stack.getMaxStackSize() == 1 && stack.getRarity() == Rarity.UNCOMMON,
            "source duration/single/uncommon: " + BuiltInRegistries.ITEM.getKey(item));
        helper.assertTrue(armor.getDefense() == defense && armor.getToughness() == 0 && armor.getEnchantmentValue() == enchantment,
            "real material source defense/toughness/enchantability");
        helper.assertTrue(item.isValidRepairItem(stack, new ItemStack(Items.IRON_INGOT)) && !item.isValidRepairItem(stack, new ItemStack(Items.LEATHER)), "source iron repair, not generic fabric");
    }
    @GameTest(template = "empty")
    public static void actualGearQueriesCountOnlyEquippedRobeAndBootsAndDoNotInventRepairTicks(GameTestHelper helper) {
        var wearer = helper.makeMockPlayer(GameType.SURVIVAL); wearer.getInventory().clearContent();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) wearer.setItemSlot(slot, ItemStack.EMPTY);
        wearer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ItemsTC.crimsonRobeHelm)); wearer.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ItemsTC.crimsonRobeChest));
        wearer.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ItemsTC.crimsonRobeLegs)); wearer.setItemSlot(EquipmentSlot.FEET, new ItemStack(ItemsTC.crimsonBoots));
        helper.assertTrue(GearHelper.getGearVisDiscount(wearer) == .04F && GearHelper.getWarpFromGear(wearer) == 4, "actual armor query totals 4percent/4warp");
        wearer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ItemsTC.crimsonPlateHelm)); wearer.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ItemsTC.crimsonPlateChest));
        wearer.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ItemsTC.crimsonPlateLegs));
        helper.assertTrue(GearHelper.getGearVisDiscount(wearer) == .01F && GearHelper.getWarpFromGear(wearer) == 1, "plate contributes neither bonus; boots retain source1");
        ItemStack chest = wearer.getItemBySlot(EquipmentSlot.CHEST); chest.setDamageValue(7); wearer.tickCount = 40; wearer.getInventory().tick();
        helper.assertTrue(chest.getDamageValue() == 7 && !chest.has(DataComponents.TRIM), "no invented void-style self-repair or extra components"); helper.succeed();
    }
}
