package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.crafting.RecipeMagicDust;
import thaumcraft.common.lib.crafting.ResearchAcquisitionRecipes;

/** Actual ordinary RecipeManager/serializer and CraftingMenu takes, not GUI/TCP/book acquisition. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class MagicDustRecipeGameTests {
    @GameTest(template = "empty")
    public static void realSerializerAndSourceRecipeRoundTripThroughJsonAndRegistryFriendlyWire(GameTestHelper helper) {
        RecipeMagicDust recipe = recipe(helper);
        helper.assertTrue(recipe.getSerializer() == ResearchAcquisitionRecipes.MAGIC_DUST
            && BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()).equals(ResearchAcquisitionRecipes.MAGIC_DUST_ID),
            "actual MOD registration uses the separate magic_dust serializer");
        var encoded = Recipe.CODEC.encodeStart(JsonOps.INSTANCE, recipe).getOrThrow();
        helper.assertTrue(encoded.getAsJsonObject().get("type").getAsString().equals("thaumcraft:magic_dust"),
            "recipe JSON identifies the live registered serializer, not an ordinary shapeless substitute");
        Recipe<?> json = Recipe.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        helper.assertTrue(json instanceof RecipeMagicDust && ((RecipeMagicDust) json).category() == recipe.category(),
            "registered recipe codec retains its modern book category");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            Recipe.STREAM_CODEC.encode(buffer, recipe);
            Recipe<?> wire = Recipe.STREAM_CODEC.decode(buffer);
            helper.assertTrue(wire instanceof RecipeMagicDust && ((RecipeMagicDust) wire).category() == recipe.category()
                && buffer.readableBytes() == 0, "actual registry serializer wire roundtrip consumes the complete frame");
            expect(helper, (RecipeMagicDust) wire, grid(3, 3, new int[]{0, 1, 2, 3, 4, 5}, validIngredients(1)));
        } finally { buffer.release(); }
        helper.assertTrue(recipe.isSpecial(), "modern CustomRecipe handles data-dependent crystal ingredients");
        ItemStack result = recipe.getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ItemsTC.salisMundus) && result.getCount() == 1, "source public preview returns one dust, not CustomRecipe EMPTY");
        result.set(DataComponents.CUSTOM_NAME, Component.literal("Do not retain this output mutation"));
        helper.assertFalse(recipe.getResultItem(helper.getLevel().registryAccess()).has(DataComponents.CUSTOM_NAME), "public preview is an independent stack");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allSixIngredientOrdersMatchRealRecipeManagerInCompactAndSparseCraftingGrids(GameTestHelper helper) {
        RecipeMagicDust recipe = recipe(helper);
        List<List<ItemStack>> orders = permutations(validIngredients(7));
        helper.assertTrue(orders.size() == 720, "six distinct ingredient stacks have 720 permutations");
        for (List<ItemStack> order : orders) {
            expect(helper, recipe, grid(3, 2, new int[]{0, 1, 2, 3, 4, 5}, order));
            expect(helper, recipe, grid(3, 3, new int[]{0, 2, 3, 5, 6, 8}, order));
        }
        expect(helper, recipe, grid(2, 3, new int[]{0, 1, 2, 3, 4, 5}, validIngredients(3)));
        helper.assertTrue(recipe.canCraftInDimensions(3, 2) && recipe.canCraftInDimensions(2, 3)
            && recipe.canCraftInDimensions(3, 3), "source canFit requires at least six cells, not a fabricated nine-cell minimum");
        helper.assertFalse(recipe.canCraftInDimensions(2, 2) || recipe.canCraftInDimensions(1, 5), "six occupied ingredients do not fit smaller grids");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyFirstAspectTagsMustDifferAndCompoundAspectsAndContainedAmountsAreNotRestricted(GameTestHelper helper) {
        RecipeMagicDust recipe = recipe(helper);
        List<ItemStack> ingredients = ordinaryIngredients();
        ingredients.add(crystal(new AspectList().add(Aspect.MAGIC, 0).add(Aspect.AIR, 99), 6));
        ingredients.add(crystal(new AspectList().add(Aspect.DEATH, -7).add(Aspect.AIR, 99), 6));
        ingredients.add(crystal(new AspectList().add(Aspect.MAN, 37).add(Aspect.AIR, 99), 6));
        CraftingInput input = grid(3, 3, new int[]{0, 1, 2, 3, 4, 5}, ingredients);
        List<ItemStack> before = copy(input.items());
        expect(helper, recipe, input);
        helper.assertTrue(ItemStack.listMatches(before, input.items()), "matches/assemble preserve every input count/component");
        List<ItemStack> duplicateFirst = ordinaryIngredients();
        duplicateFirst.add(crystal(new AspectList().add(Aspect.MAGIC, 1).add(Aspect.AIR, 1), 1));
        duplicateFirst.add(crystal(new AspectList().add(Aspect.MAGIC, 1).add(Aspect.EARTH, 1), 1));
        duplicateFirst.add(crystal(new AspectList().add(Aspect.MAN, 1), 1));
        reject(helper, recipe, grid(3, 3, new int[]{0, 1, 2, 3, 4, 5}, duplicateFirst), "same FIRST tag rejects even if later tags differ");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missingDuplicateExtraAndWrongOccupiedCellsNeverSelectTheRecipe(GameTestHelper helper) {
        RecipeMagicDust recipe = recipe(helper);
        for (int index = 0; index < 6; index++) {
            List<ItemStack> missing = validIngredients(1);
            missing.set(index, ItemStack.EMPTY);
            reject(helper, recipe, grid(3, 3, new int[]{0, 1, 2, 3, 4, 5}, missing), "missing ingredient " + index);
            List<ItemStack> wrong = validIngredients(1);
            wrong.set(index, new ItemStack(Items.PAPER));
            reject(helper, recipe, grid(3, 3, new int[]{0, 1, 2, 3, 4, 5}, wrong), "wrong ingredient " + index);
        }
        for (Item extra : List.of(Items.BOWL, Items.FLINT, Items.REDSTONE, Items.PAPER)) {
            List<ItemStack> ingredients = validIngredients(1);
            ingredients.add(new ItemStack(extra));
            reject(helper, recipe, grid(3, 3, new int[]{0, 1, 2, 3, 4, 5, 8}, ingredients), "extra occupied " + extra);
        }
        List<ItemStack> fourthCrystal = validIngredients(1);
        fourthCrystal.add(crystal(new AspectList().add(Aspect.WATER, 1), 1));
        reject(helper, recipe, grid(3, 3, new int[]{0, 1, 2, 3, 4, 5, 8}, fourthCrystal), "fourth distinct crystal is still an extra cell");
        List<ItemStack> duplicateCrystal = validIngredients(1);
        duplicateCrystal.set(5, duplicateCrystal.get(3).copy());
        reject(helper, recipe, grid(3, 3, new int[]{0, 1, 2, 3, 4, 5}, duplicateCrystal), "three cells with duplicate aspect tags");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void malformedAspectComponentsRejectInsteadOfTheOldNullOrIndexCrash(GameTestHelper helper) {
        RecipeMagicDust recipe = recipe(helper);
        List<ItemStack> malformed = new ArrayList<>();
        malformed.add(new ItemStack(ItemsTC.crystalEssence));
        ItemStack empty = new ItemStack(ItemsTC.crystalEssence);
        empty.set(TCDataComponents.ASPECTS, new AspectList());
        malformed.add(empty);
        ItemStack nullFirst = new ItemStack(ItemsTC.crystalEssence);
        nullFirst.set(TCDataComponents.ASPECTS, new AspectList().add(null, 1).add(Aspect.AIR, 1));
        malformed.add(nullFirst);
        ItemStack nullMap = new ItemStack(ItemsTC.crystalEssence);
        AspectList invalidMap = new AspectList();
        invalidMap.aspects = null;
        nullMap.set(TCDataComponents.ASPECTS, invalidMap);
        malformed.add(nullMap);
        for (ItemStack invalid : malformed) {
            List<ItemStack> ingredients = validIngredients(1);
            ingredients.set(3, invalid);
            reject(helper, recipe, grid(3, 3, new int[]{0, 1, 2, 3, 4, 5}, ingredients), "explicit malformed-aspect safety adaptation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void remainderCopiesPreserveBowlFlintComponentsAndRetainOtherPlatformContainerDefaults(GameTestHelper helper) {
        RecipeMagicDust recipe = recipe(helper);
        List<ItemStack> ingredients = validIngredients(7);
        named(ingredients.get(0), "Whole source bowl data");
        named(ingredients.get(1), "Whole source flint data");
        CraftingInput input = grid(3, 3, new int[]{0, 1, 2, 3, 4, 5}, ingredients);
        List<ItemStack> before = copy(input.items());
        var remainders = recipe.getRemainingItems(input);
        for (int index = 0; index < 2; index++) {
            helper.assertTrue(remainders.get(index).getCount() == 1
                && ItemStack.isSameItemSameComponents(remainders.get(index), input.getItem(index))
                && remainders.get(index) != input.getItem(index), "full component-preserving independent refund " + index);
            remainders.get(index).set(DataComponents.CUSTOM_NAME, Component.literal("Refund mutation must not affect input"));
        }
        helper.assertTrue(remainders.subList(2, remainders.size()).stream().allMatch(ItemStack::isEmpty), "redstone/crystal defaults have no remainder");
        helper.assertTrue(ItemStack.listMatches(before, input.items()), "creating/modifying remainders never edits inputs");
        CraftingInput containerFixture = grid(3, 1, new int[]{0, 1, 2},
            List.of(ingredients.get(0), ingredients.get(1), new ItemStack(Items.MILK_BUCKET)));
        var defaults = recipe.getRemainingItems(containerFixture);
        helper.assertTrue(defaults.get(2).is(Items.BUCKET) && defaults.get(2).getCount() == 1,
            "getRemainingItems preserves the platform container fallback even for a nonmatching diagnostic input");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realCraftingMenuResultTakesConsumeOnePerCellAndFullyRefundNamedBowlFlint(GameTestHelper helper) throws Exception {
        ServerPlayer owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TC_MagicDust"));
        var knowledgeBefore = ThaumcraftCapabilities.getKnowledge(owner).serializeNBT().copy();
        int experienceBefore = owner.totalExperience;
        List<ItemStack> ingredients = validIngredients(2);
        ingredients.get(0).setCount(5);
        ingredients.get(1).setCount(7);
        named(ingredients.get(0), "Bowl survives both actual takes");
        named(ingredients.get(1), "Flint survives both actual takes");
        List<ItemStack> before = copy(ingredients);
        BlockPos table = new BlockPos(1, 1, 1);
        helper.setBlock(table, Blocks.CRAFTING_TABLE);
        CraftingMenu menu = new CraftingMenu(37, owner.getInventory(),
            ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(table)));
        try (var probe = new CraftProbe(owner);
             var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> helper.fail("ordinary dust crafting must not grant/sync research"))) {
            for (int index = 0; index < ingredients.size(); index++) menu.getSlot(index + 1).set(ingredients.get(index));
            helper.assertTrue(menu.getSlot(0).container instanceof RecipeCraftingHolder, "real ResultContainer tracks the actual recipe");
            var used = (RecipeCraftingHolder) menu.getSlot(0).container;
            helper.assertTrue(used.getRecipeUsed() != null && used.getRecipeUsed().id().equals(recipeId()), "actual CraftingMenu selected salismundus");
            helper.assertTrue(probe.results.isEmpty(), "preview is not a crafting event");
            for (int take = 1; take <= 2; take++) {
                ItemStack output = menu.getSlot(0).safeTake(1, Integer.MAX_VALUE, owner);
                helper.assertTrue(output.is(ItemsTC.salisMundus) && output.getCount() == 1, "real ResultSlot.take assembles exactly one dust");
                helper.assertTrue(probe.results.size() == take && probe.results.get(take - 1).is(ItemsTC.salisMundus)
                    && probe.results.get(take - 1).getCount() == 1, "actual take publishes exactly one production ItemCraftedEvent");
                for (int index = 0; index < 6; index++) {
                    ItemStack remaining = menu.getSlot(index + 1).getItem();
                    int expectedCount = index < 2 ? before.get(index).getCount() : 2 - take;
                    helper.assertTrue(remaining.getCount() == expectedCount, "source consume/refund count at index " + index + ", take " + take);
                    if (expectedCount > 0) helper.assertTrue(ItemStack.isSameItemSameComponents(remaining, before.get(index)),
                        "full surviving components at index " + index + ", take " + take);
                }
            }
            helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "after both consumable sets, bowl/flint alone do not craft a third result");
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(owner).serializeNBT().equals(knowledgeBefore)
                && owner.totalExperience == experienceBefore && !PlayerDataSync.isKnowledgeDirty(owner),
                "ordinary recipe/take does not manufacture acquisition flags, XP or research grants");
        } finally {
            menu.clearCraftingContent();
            PlayerDataSync.forget(owner);
        }
        helper.succeed();
    }

    private static RecipeMagicDust recipe(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager().byKey(recipeId())
            .orElseThrow(() -> new AssertionError("missing original ordinary salismundus recipe"));
        helper.assertTrue(holder.value() instanceof RecipeMagicDust, "actual source recipe uses data-sensitive RecipeMagicDust");
        return (RecipeMagicDust) holder.value();
    }

    private static ResourceLocation recipeId() { return ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "salismundus"); }

    private static void expect(GameTestHelper helper, RecipeMagicDust recipe, CraftingInput input) {
        helper.assertTrue(recipe.matches(input, helper.getLevel()), "source dust ingredients match");
        var selected = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
        helper.assertTrue(selected.id().equals(recipeId()) && selected.value() instanceof RecipeMagicDust
            && selected.value().getSerializer() == recipe.getSerializer(), "real manager selects the source recipe (decoded recipes are independent instances)");
        ItemStack assembled = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(assembled.is(ItemsTC.salisMundus) && assembled.getCount() == 1
            && ItemStack.isSameItemSameComponents(assembled, new ItemStack(ItemsTC.salisMundus)), "assemble returns one independent clean dust stack");
    }

    private static void reject(GameTestHelper helper, RecipeMagicDust recipe, CraftingInput input, String label) {
        helper.assertFalse(recipe.matches(input, helper.getLevel()), label);
        var selected = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(selected.isEmpty() || selected.get().value() != recipe, "real manager does not select invalid dust input: " + label);
    }

    private static List<ItemStack> ordinaryIngredients() {
        return new ArrayList<>(List.of(new ItemStack(Items.BOWL), new ItemStack(Items.FLINT), new ItemStack(Items.REDSTONE)));
    }

    private static List<ItemStack> validIngredients(int count) {
        List<ItemStack> ingredients = ordinaryIngredients();
        ingredients.forEach(stack -> stack.setCount(count));
        for (Aspect aspect : List.of(Aspect.AIR, Aspect.EARTH, Aspect.FIRE)) {
            ingredients.add(crystal(new AspectList().add(aspect, 1), count));
        }
        return ingredients;
    }

    private static ItemStack crystal(AspectList aspects, int count) {
        ItemStack stack = new ItemStack(ItemsTC.crystalEssence, count);
        stack.set(TCDataComponents.ASPECTS, aspects);
        return stack;
    }

    private static void named(ItemStack stack, String label) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(label));
        CompoundTag tag = new CompoundTag();
        tag.putString("fullRefundFixture", label);
        tag.putInt("arbitraryLegacyValue", 123);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static List<ItemStack> copy(List<ItemStack> items) { return items.stream().map(ItemStack::copy).toList(); }

    private static CraftingInput grid(int width, int height, int[] slots, List<ItemStack> ingredients) {
        List<ItemStack> items = new ArrayList<>();
        for (int index = 0; index < width * height; index++) items.add(ItemStack.EMPTY);
        for (int index = 0; index < slots.length; index++) items.set(slots[index], ingredients.get(index));
        return CraftingInput.of(width, height, items);
    }

    private static List<List<ItemStack>> permutations(List<ItemStack> items) {
        if (items.size() == 1) return List.of(items);
        List<List<ItemStack>> result = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            List<ItemStack> tail = new ArrayList<>(items);
            ItemStack first = tail.remove(index);
            for (List<ItemStack> order : permutations(tail)) {
                List<ItemStack> row = new ArrayList<>();
                row.add(first);
                row.addAll(order);
                result.add(List.copyOf(row));
            }
        }
        return result;
    }

    public static final class CraftProbe implements AutoCloseable {
        private final ServerPlayer owner;
        final List<ItemStack> results = new ArrayList<>();
        CraftProbe(ServerPlayer owner) { this.owner = owner; NeoForge.EVENT_BUS.register(this); }
        @SubscribeEvent public void crafted(PlayerEvent.ItemCraftedEvent event) {
            if (event.getEntity() == owner) results.add(event.getCrafting().copy());
        }
        @Override public void close() { NeoForge.EVENT_BUS.unregister(this); }
    }
}
