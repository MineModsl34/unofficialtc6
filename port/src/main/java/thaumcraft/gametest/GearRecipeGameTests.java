package thaumcraft.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

/** Real recipe manager coverage of the narrow loot-gear slice, from TC6 ConfigRecipes/ConfigAspects. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public class GearRecipeGameTests {
    @GameTest(template = "empty")
    public static void sixteenSourceNormalRecipesMatchAndAssembleRealCraftingInputs(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        List<RecipeCase> cases = sourceCases();
        h.assertTrue(cases.size() == 16, "ten gear and six ordinary bauble recipes, no loot-only inventions");
        for (RecipeCase source : cases) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, source.id());
            var holder = manager.byKey(id).orElseThrow(() -> new AssertionError("missing source recipe " + id));
            h.assertTrue(holder.value() instanceof CraftingRecipe, id + " is an ordinary crafting recipe");
            CraftingRecipe recipe = (CraftingRecipe) holder.value();
            for (boolean mirror : List.of(false, true)) {
                // Offset narrow swords and the two-row boots/helm: a real crafting table accepts both.
                CraftingInput input = CraftingInput.of(3, 3, grid(source, mirror,
                    mirror ? 3 - source.width() : 0, mirror ? 3 - source.pattern().length : 0));
                h.assertTrue(recipe.matches(input, h.getLevel()), id + " source pattern matches (mirror=" + mirror + ")");
                var selected = manager.getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
                h.assertTrue(selected.id().equals(id), id + " selected by the live RecipeManager");
                ItemStack output = recipe.assemble(input, h.getLevel().registryAccess());
                h.assertTrue(output.is(source.result()) && output.getCount() == 1 && output.getDamageValue() == 0,
                    id + " assembles exactly one undamaged source result");
                h.assertTrue(recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty), id + " has no container remainder");
            }
            List<ItemStack> missing = grid(source, false, 0, 0);
            int occupied = firstCell(missing, false);
            missing.set(occupied, ItemStack.EMPTY);
            h.assertFalse(recipe.matches(CraftingInput.of(3, 3, missing), h.getLevel()), id + " rejects a missing ingredient");
            List<ItemStack> wrong = grid(source, false, 0, 0);
            wrong.set(occupied, new ItemStack(Items.STONE));
            h.assertFalse(recipe.matches(CraftingInput.of(3, 3, wrong), h.getLevel()), id + " rejects a wrong material");
            List<ItemStack> extra = grid(source, false, 0, 0);
            extra.set(firstCell(extra, true), new ItemStack(Items.STONE));
            h.assertFalse(recipe.matches(CraftingInput.of(3, 3, extra), h.getLevel()), id + " rejects an occupied source gap");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void foundAmuletApprenticeRingAndPearlKeepOnlyTheirExplicitSourceAspects(GameTestHelper h) {
        expectAspects(h, ItemsTC.amuletVis, new AspectList().add(Aspect.AURA, 20).add(Aspect.METAL, 5).add(Aspect.MAGIC, 5));
        expectAspects(h, ItemsTC.ringApprentice, new AspectList().add(Aspect.AURA, 5).add(Aspect.METAL, 5).add(Aspect.MAGIC, 20));
        AspectList pearl = new AspectList().add(Aspect.AIR, 10).add(Aspect.EARTH, 10).add(Aspect.FIRE, 10)
            .add(Aspect.WATER, 10).add(Aspect.ORDER, 10).add(Aspect.ENTROPY, 10);
        for (int damage = 0; damage <= 8; damage++) {
            ItemStack stack = new ItemStack(ItemsTC.primordialPearl);
            stack.setDamageValue(damage);
            expectAspects(h, stack, pearl, "pearl legacy metadata " + damage);
        }
        for (var holder : h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack result = holder.value().getResultItem(h.getLevel().registryAccess());
            h.assertTrue(!result.is(ItemsTC.amuletVis) && !result.is(ItemsTC.ringApprentice) && !result.is(ItemsTC.primordialPearl),
                "no invented ordinary loot-only recipe: " + holder.id());
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void gearAndSixOrdinaryBaublesDeriveAvailableRecipeAspectsWithSourceBonuses(GameTestHelper h) {
        for (RecipeCase source : sourceCases()) {
            AspectList sum = new AspectList();
            for (ItemStack ingredient : grid(source, false, 0, 0)) {
                if (!ingredient.isEmpty()) {
                    AspectList available = ThaumcraftCraftingManager.getObjectTags(ingredient);
                    if (available != null) sum.add(available);
                }
            }
            AspectList expected = new AspectList();
            for (Aspect aspect : sum.getAspects()) {
                int amount = (int) (sum.getAmount(aspect) * 0.75F); // source TC6, count-one result and no remainder.
                if (amount > 0) expected.add(aspect, amount);
            }
            // Original bonuses merge maxima, not addition. Material aspect sources awaiting stage 5
            // are deliberately not fabricated here; sticks and every bauble ingredient are available.
            if (source.bonus() != null) expected.merge(source.bonus(), source.bonusAmount());
            expected = AspectHelper.cullTags(expected, 7);
            h.assertTrue(expected.size() > 0, source.id() + " has available derived or source gear-bonus aspects");
            expectAspects(h, source.result(), expected);
        }
        // Independent exact checks avoid merely comparing the derivation routine with itself.
        expectAspects(h, ItemsTC.amuletMundane, new AspectList().add(Aspect.BEAST, 11).add(Aspect.CRAFT, 2)
            .add(Aspect.METAL, 7).add(Aspect.TOOL, 3));
        expectAspects(h, ItemsTC.girdleMundane, new AspectList().add(Aspect.BEAST, 11).add(Aspect.PROTECT, 11)
            .add(Aspect.METAL, 7).add(Aspect.TOOL, 3));
        expectAspects(h, ItemsTC.ringMundane, new AspectList().add(Aspect.METAL, 6));
        expectAspects(h, ItemsTC.amuletFancy, new AspectList().add(Aspect.BEAST, 11).add(Aspect.CRAFT, 2)
            .add(Aspect.CRYSTAL, 11).add(Aspect.DESIRE, 18).add(Aspect.METAL, 7));
        expectAspects(h, ItemsTC.girdleFancy, new AspectList().add(Aspect.BEAST, 11).add(Aspect.PROTECT, 11)
            .add(Aspect.CRYSTAL, 11).add(Aspect.DESIRE, 18).add(Aspect.METAL, 7));
        expectAspects(h, ItemsTC.ringFancy, new AspectList().add(Aspect.METAL, 5).add(Aspect.CRYSTAL, 11).add(Aspect.DESIRE, 16));
        h.succeed();
    }

    private static void expectAspects(GameTestHelper h, Item item, AspectList expected) {
        expectAspects(h, new ItemStack(item), expected, item.toString());
    }

    private static void expectAspects(GameTestHelper h, ItemStack stack, AspectList expected, String label) {
        AspectList actual = ThaumcraftCraftingManager.getObjectTags(stack);
        h.assertTrue(actual != null && actual.size() == expected.size(), label + " exact aspect count: expected "
            + expected.aspects + ", got " + (actual == null ? "null" : actual.aspects));
        for (Aspect aspect : expected.getAspects()) {
            h.assertTrue(actual.getAmount(aspect) == expected.getAmount(aspect), label + " " + aspect.getTag()
                + ": expected " + expected.getAmount(aspect) + ", got " + actual.getAmount(aspect));
        }
    }

    private static int firstCell(List<ItemStack> grid, boolean empty) {
        for (int index = 0; index < grid.size(); index++) if (grid.get(index).isEmpty() == empty) return index;
        throw new AssertionError("source recipe has no " + (empty ? "empty" : "occupied") + " cell");
    }

    private static List<ItemStack> grid(RecipeCase source, boolean mirror, int xOffset, int yOffset) {
        List<ItemStack> grid = new ArrayList<>(9);
        for (int index = 0; index < 9; index++) grid.add(ItemStack.EMPTY);
        for (int y = 0; y < source.pattern().length; y++) {
            String row = source.pattern()[y];
            for (int x = 0; x < row.length(); x++) {
                char symbol = row.charAt(mirror ? row.length() - x - 1 : x);
                if (symbol != ' ') grid.set((y + yOffset) * 3 + x + xOffset, new ItemStack(source.keys().get(symbol)));
            }
        }
        return grid;
    }

    private static List<RecipeCase> sourceCases() {
        List<RecipeCase> cases = new ArrayList<>();
        gear(cases, "thaumium", ItemsTC.ingotThaumium, ItemsTC.thaumiumHelm, ItemsTC.thaumiumChest,
            ItemsTC.thaumiumLegs, ItemsTC.thaumiumBoots, ItemsTC.thaumiumSword, new int[]{8, 24, 20, 8, 12});
        gear(cases, "void", ItemsTC.ingotVoid, ItemsTC.voidHelm, ItemsTC.voidChest,
            ItemsTC.voidLegs, ItemsTC.voidBoots, ItemsTC.voidSword, new int[]{12, 32, 24, 12, 16});
        cases.add(new RecipeCase("babuleamulet", ItemsTC.amuletMundane, new String[]{" S ", "S S", " I "},
            Map.of('S', Items.STRING, 'I', ItemsTC.ingotBrass), null, 0));
        cases.add(new RecipeCase("babulering", ItemsTC.ringMundane, new String[]{"NNN", "N N", "NNN"},
            Map.of('N', ItemsTC.nuggetBrass), null, 0));
        cases.add(new RecipeCase("babulegirdle", ItemsTC.girdleMundane, new String[]{" L ", "L L", " I "},
            Map.of('L', Items.LEATHER, 'I', ItemsTC.ingotBrass), null, 0));
        cases.add(new RecipeCase("babuleamuletfancy", ItemsTC.amuletFancy, new String[]{" S ", "SGS", " I "},
            Map.of('S', Items.STRING, 'G', Items.DIAMOND, 'I', Items.GOLD_INGOT), null, 0));
        cases.add(new RecipeCase("babuleringfancy", ItemsTC.ringFancy, new String[]{"NGN", "N N", "NNN"},
            Map.of('N', Items.GOLD_NUGGET, 'G', Items.DIAMOND), null, 0));
        cases.add(new RecipeCase("babulegirdlefancy", ItemsTC.girdleFancy, new String[]{" L ", "LGL", " I "},
            Map.of('L', Items.LEATHER, 'G', Items.DIAMOND, 'I', Items.GOLD_INGOT), null, 0));
        return cases;
    }

    private static void gear(List<RecipeCase> cases, String material, Item ingot, Item helm, Item chest,
                             Item legs, Item boots, Item sword, int[] bonuses) {
        Item[] results = {helm, chest, legs, boots, sword};
        String[] suffixes = {"helm", "chest", "legs", "boots", "sword"};
        String[][] patterns = {{"III", "I I"}, {"I I", "III", "III"}, {"III", "I I", "I I"}, {"I I", "I I"}, {"I", "I", "S"}};
        for (int index = 0; index < results.length; index++) {
            cases.add(new RecipeCase(material + suffixes[index], results[index], patterns[index],
                index == 4 ? Map.of('I', ingot, 'S', Items.STICK) : Map.of('I', ingot),
                index == 4 ? Aspect.AVERSION : Aspect.PROTECT, bonuses[index]));
        }
    }

    private record RecipeCase(String id, Item result, String[] pattern, Map<Character, Item> keys, Aspect bonus, int bonusAmount) {
        int width() { return pattern[0].length(); }
    }
}
