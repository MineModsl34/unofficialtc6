package thaumcraft.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

/**
 * Stage 1 check in a real world: the aspect table is built on server start.
 * Expected values come from TC6 (ConfigAspects + the recipe derivation formula).
 * Run: gradlew runGameTestServer.
 */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public class AspectGameTests {
    private static AspectList tags(Item item) {
        return ThaumcraftCraftingManager.getObjectTags(new ItemStack(item));
    }

    private static void expect(GameTestHelper h, Item item, Aspect aspect, int amount) {
        AspectList al = tags(item);
        int actual = al == null ? 0 : al.getAmount(aspect);
        h.assertTrue(actual == amount, BuiltInRegistries.ITEM.getKey(item) + " " + aspect.getTag() + ": expected " + amount + ", got " + actual + " " + al);
    }

    @GameTest(template = "empty")
    public static void explicitTags(GameTestHelper h) {
        expect(h, Items.IRON_INGOT, Aspect.METAL, 15);
        expect(h, Items.DEEPSLATE_IRON_ORE, Aspect.METAL, 15); // through the c:ores/iron tag
        expect(h, Items.CHARCOAL, Aspect.FIRE, 10);
        expect(h, Items.PODZOL, Aspect.PLANT, 1);
        expect(h, Items.CUT_SANDSTONE, Aspect.ORDER, 1);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void derivedFromRecipes(GameTestHelper h) {
        // Iron block: 9 ingots * 15 * 0.75 = 101
        expect(h, Items.IRON_BLOCK, Aspect.METAL, 101);
        // Water bucket = bucket (3 iron -> 33 metal) + 20 water
        expect(h, Items.WATER_BUCKET, Aspect.WATER, 20);
        h.assertTrue(tags(Items.WATER_BUCKET).getAmount(Aspect.METAL) == tags(Items.BUCKET).getAmount(Aspect.METAL), "water bucket inherits bucket metal");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bonusTags(GameTestHelper h) {
        // Iron sword: iron tier +2 damage -> (2 + 1) * 4 = 12 aversio
        expect(h, Items.IRON_SWORD, Aspect.AVERSION, 12);
        // Diamond chestplate: armour 8 -> 32 praemunio
        expect(h, Items.DIAMOND_CHESTPLATE, Aspect.PROTECT, 32);

        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(h.getLevel().registryAccess().holderOrThrow(Enchantments.SHARPNESS), 2);
        AspectList al = ThaumcraftCraftingManager.getObjectTags(sword);
        h.assertTrue(al.getAmount(Aspect.MAGIC) > 0, "enchanted sword has praecantatio: " + al);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void potions(GameTestHelper h) {
        AspectList water = ThaumcraftCraftingManager.getObjectTags(PotionContents.createItemStack(Items.POTION, Potions.WATER));
        h.assertTrue(water.getAmount(Aspect.WATER) == 10, "water bottle: " + water);
        AspectList healing = ThaumcraftCraftingManager.getObjectTags(PotionContents.createItemStack(Items.POTION, Potions.HEALING));
        h.assertTrue(healing.getAmount(Aspect.ALCHEMY) > 0, "healing potion has alkimia: " + healing);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void entityTags(GameTestHelper h) {
        Zombie zombie = h.spawn(EntityType.ZOMBIE, 1, 1, 1);
        AspectList al = AspectHelper.getEntityAspects(zombie);
        h.assertTrue(al != null && al.getAmount(Aspect.UNDEAD) == 20, "zombie: " + al);
        zombie.discard();
        h.succeed();
    }

    /** Not a check: writes the list of items without aspects for ConfigAspects.registerModernItemAspects. */
    @GameTest(template = "empty", required = false)
    public static void reportMissing(GameTestHelper h) {
        List<String> missing = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            AspectList al = item == Items.AIR ? null : tags(item);
            if (item != Items.AIR && (al == null || al.size() == 0)) {
                missing.add(BuiltInRegistries.ITEM.getKey(item).toString());
            }
        }
        try {
            Path out = Path.of("thaumcraft-missing-aspects.txt");
            Files.write(out, missing);
            Thaumcraft.log.info("{} items without aspects written to {}", missing.size(), out.toAbsolutePath());
        } catch (Exception e) {
            Thaumcraft.log.error("could not write missing aspects", e);
        }
        h.succeed();
    }
}
