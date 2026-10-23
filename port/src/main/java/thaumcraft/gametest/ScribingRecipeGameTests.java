package thaumcraft.gametest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.lib.TCTags;

/** Actual ordinary RecipeManager, not research unlocks, arcane crafting or client menu acceptance. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class ScribingRecipeGameTests {
    @GameTest(template = "empty")
    public static void allSourceShapelessRecipesMatchEveryPositionAndPermutationInTwoAndThreeSquareGrids(GameTestHelper helper) {
        CraftingRecipe craft = recipe(helper, "scribingtoolscraft2");
        CraftingRecipe phialCraft = recipe(helper, "scribingtoolscraft1");
        CraftingRecipe refill = recipe(helper, "scribingtoolsrefill");
        helper.assertTrue(new ItemStack(Items.INK_SAC).is(TCTags.Items.SCRIBING_INK), "default dyeBlack bridge contains actual ink sac");
        for (int side : List.of(2, 3)) {
            for (Item container : List.of(Items.GLASS_BOTTLE, ItemsTC.phial)) {
                for (List<Item> order : permutations(List.of(container, Items.FEATHER, Items.INK_SAC))) {
                    for (int first = 0; first < side * side; first++) {
                        for (int second = first + 1; second < side * side; second++) {
                            for (int third = second + 1; third < side * side; third++) {
                                CraftingInput input = grid(side, new int[]{first, second, third}, stacks(order));
                                expectMatch(helper, container == ItemsTC.phial ? phialCraft : craft,
                                    container == ItemsTC.phial ? "scribingtoolscraft1" : "scribingtoolscraft2", input);
                            }
                        }
                    }
                }
            }
            for (List<Item> order : permutations(List.of(ItemsTC.scribingTools, Items.INK_SAC))) {
                for (int first = 0; first < side * side; first++) {
                    for (int second = first + 1; second < side * side; second++) {
                        expectMatch(helper, refill, "scribingtoolsrefill", grid(side, new int[]{first, second}, stacks(order)));
                    }
                }
            }
        }
        helper.assertFalse(craft.canCraftInDimensions(2, 1), "three ingredients do not fit two cells");
        helper.assertFalse(phialCraft.canCraftInDimensions(2, 1), "source empty-phial alternative also needs three cells");
        helper.assertTrue(refill.canCraftInDimensions(2, 1), "source refill needs two cells, not an invented table restriction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void refillAcceptsUndamagedWornExhaustedNamedCustomDataToolsButAlwaysCreatesACleanIndependentStack(GameTestHelper helper) {
        CraftingRecipe recipe = recipe(helper, "scribingtoolsrefill");
        for (int wear : new int[]{0, 1, 99, 100}) {
            for (int side : List.of(2, 3)) {
                ItemStack tool = new ItemStack(ItemsTC.scribingTools);
                tool.setDamageValue(wear);
                tool.set(DataComponents.CUSTOM_NAME, Component.literal("Source wildcard/NBT fixture"));
                CompoundTag nbt = new CompoundTag();
                nbt.putString("fixture", "discard this custom data on refill");
                nbt.putInt("oldWear", wear);
                tool.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
                ItemStack ink = new ItemStack(Items.INK_SAC, 7);
                ink.set(DataComponents.CUSTOM_NAME, Component.literal("Named legacy black ink"));
                ItemStack before = tool.copy(), inkBefore = ink.copy();
                CraftingInput input = grid(side, new int[]{0, side * side - 1}, List.of(tool, ink));
                expectMatch(helper, recipe, "scribingtoolsrefill", input);
                ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
                helper.assertTrue(ItemStack.isSameItemSameComponents(result, new ItemStack(ItemsTC.scribingTools))
                    && result.getCount() == 1 && result.getDamageValue() == 0
                    && !result.has(DataComponents.CUSTOM_DATA) && !result.has(DataComponents.CUSTOM_NAME),
                    "damage " + wear + " creates a new ordinary tool, not an NBT-preserving repair");
                result.setDamageValue(17);
                helper.assertTrue(recipe.assemble(input, helper.getLevel().registryAccess()).getDamageValue() == 0,
                    "assemble copies clean recipe output, no shared result mutation");
                helper.assertTrue(ItemStack.isSameItemSameComponents(tool, before) && tool.getCount() == before.getCount()
                    && ItemStack.isSameItemSameComponents(ink, inkBefore) && ink.getCount() == inkBefore.getCount(),
                    "matching/assembling does not consume or mutate inputs; ordinary crafting handles consumption");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missingExtraPaperBlackDyeGlowInkAndWrongContainerInputsDoNotMatchAndDefaultInputsLeaveNoRemainder(GameTestHelper helper) {
        CraftingRecipe craft = recipe(helper, "scribingtoolscraft2"), refill = recipe(helper, "scribingtoolsrefill");
        for (int side : List.of(2, 3)) {
            for (Item wrongInk : List.of(Items.BLACK_DYE, Items.GLOW_INK_SAC, Items.PAPER, Items.BONE_MEAL)) {
                helper.assertFalse(new ItemStack(wrongInk).is(TCTags.Items.SCRIBING_INK), "default explicit tag excludes " + wrongInk);
                reject(helper, craft, grid(side, new int[]{0, 1, side * side - 1},
                    List.of(new ItemStack(Items.GLASS_BOTTLE), new ItemStack(Items.FEATHER), new ItemStack(wrongInk))), "wrong ink craft");
                reject(helper, refill, grid(side, new int[]{0, side * side - 1},
                    List.of(new ItemStack(ItemsTC.scribingTools), new ItemStack(wrongInk))), "wrong ink refill");
            }
            reject(helper, craft, grid(side, new int[]{0, 1, side * side - 1},
                stacks(List.of(Items.PAPER, Items.FEATHER, Items.INK_SAC))), "paper is not glass bottle");
            reject(helper, craft, grid(side, new int[]{0, 1, side * side - 1},
                stacks(List.of(Items.GLASS_BOTTLE, Items.PAPER, Items.INK_SAC))), "paper is not feather");
            reject(helper, craft, grid(side, new int[]{0, 1, side * side - 1},
                stacks(List.of(Items.POTION, Items.FEATHER, Items.INK_SAC))), "filled bottle is not empty glass bottle");
            reject(helper, refill, grid(side, new int[]{0, side * side - 1},
                stacks(List.of(Items.WRITABLE_BOOK, Items.INK_SAC))), "other writing object is not exact tool");
            reject(helper, craft, grid(side, new int[]{0, 1}, stacks(List.of(Items.GLASS_BOTTLE, Items.FEATHER))), "missing ink");
            reject(helper, refill, grid(side, new int[]{0}, List.of(new ItemStack(ItemsTC.scribingTools))), "missing refill ink");
            reject(helper, craft, grid(side, new int[]{0, 1, 2, 3},
                stacks(List.of(Items.GLASS_BOTTLE, Items.FEATHER, Items.INK_SAC, Items.PAPER))), "extra occupied craft slot");
            reject(helper, refill, grid(side, new int[]{0, 1, side * side - 1},
                stacks(List.of(ItemsTC.scribingTools, Items.INK_SAC, Items.PAPER))), "extra occupied refill slot");
        }
        helper.succeed();
    }

    private static CraftingRecipe recipe(GameTestHelper helper, String name) {
        var holder = helper.getLevel().getRecipeManager().byKey(id(name))
            .orElseThrow(() -> new AssertionError("missing source ordinary recipe " + name));
        helper.assertTrue(holder.value() instanceof ShapelessRecipe, name + " is standard ordinary shapeless crafting");
        CraftingRecipe recipe = (CraftingRecipe) holder.value();
        helper.assertTrue(recipe.getGroup().equals("thaumcraft:inkwell"), "source inkwell group is retained");
        return recipe;
    }

    private static void expectMatch(GameTestHelper helper, CraftingRecipe recipe, String name, CraftingInput input) {
        helper.assertTrue(recipe.matches(input, helper.getLevel()), name + " matches the actual grid");
        var selected = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
        helper.assertTrue(selected.id().equals(id(name)), "live RecipeManager selects exactly " + name);
        ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ItemsTC.scribingTools) && result.getCount() == 1 && result.getDamageValue() == 0,
            "ordinary recipe assembles one undamaged source tool");
        helper.assertTrue(recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
            "source default bottle/feather/ink/tool have no crafting container remainder");
    }

    private static void reject(GameTestHelper helper, CraftingRecipe recipe, CraftingInput input, String label) {
        helper.assertFalse(recipe.matches(input, helper.getLevel()), label);
        var selected = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(selected.isEmpty() || (selected.get().value() != recipe), "RecipeManager does not select rejected source recipe: " + label);
    }

    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, name); }

    private static CraftingInput grid(int side, int[] slots, List<ItemStack> ingredients) {
        if (slots.length != ingredients.size()) throw new IllegalArgumentException("fixture ingredient/slot count differs");
        List<ItemStack> grid = new ArrayList<>(side * side);
        for (int index = 0; index < side * side; index++) grid.add(ItemStack.EMPTY);
        for (int index = 0; index < slots.length; index++) grid.set(slots[index], ingredients.get(index));
        return CraftingInput.of(side, side, grid);
    }

    private static List<ItemStack> stacks(List<Item> items) { return items.stream().map(ItemStack::new).toList(); }

    private static List<List<Item>> permutations(List<Item> items) {
        if (items.size() == 1) return List.of(items);
        List<List<Item>> result = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            List<Item> remaining = new ArrayList<>(items);
            Item first = remaining.remove(index);
            for (List<Item> tail : permutations(remaining)) {
                List<Item> row = new ArrayList<>();
                row.add(first);
                row.addAll(tail);
                result.add(List.copyOf(row));
            }
        }
        return result;
    }
}
