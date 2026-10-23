package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.crafting.InfusionInput;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.IngredientNBTTC;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.crafting.CrucibleRecipes;
import thaumcraft.common.lib.crafting.InfusionEnchantmentRecipe;
import thaumcraft.common.lib.crafting.InfusionRecipes;
import thaumcraft.common.lib.crafting.InfusionRunicAugmentRecipe;
import thaumcraft.common.lib.crafting.JeiRecipeSnapshotCodec;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;

/** Actual registered serializers, components, tags and owner gates; no JEI/client dependency.
 * Research grants are explicit fixtures, not a claim of normal survival research acquisition.
 */
@GameTestHolder("thaumcraft_jei")
@PrefixGameTestTemplate(false)
public final class JeiRecipeSnapshotGameTests {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, path); }

    private static RecipeHolder<InfusionRecipe> source(GameTestHelper helper, String path) {
        var holder = helper.getLevel().getRecipeManager().byKey(id(path)).orElseThrow(() -> new AssertionError("Missing actual source recipe " + path));
        helper.assertTrue(holder.value() instanceof InfusionRecipe, "real source infusion serializer: " + path);
        return new RecipeHolder<>(holder.id(), (InfusionRecipe)holder.value());
    }

    private static RecipeHolder<?> roundTrip(GameTestHelper helper, RecipeHolder<?> source) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            JeiRecipeSnapshotCodec.encode(buffer, source);
            RecipeHolder<?> decoded = JeiRecipeSnapshotCodec.decode(buffer);
            helper.assertTrue(!buffer.isReadable() && decoded.id().equals(source.id()), "exact source holder identity and no trailing bytes");
            helper.assertTrue(decoded.value().getClass() == source.value().getClass(), "exact supported native recipe class");
            helper.assertTrue(decoded.value().getSerializer() == source.value().getSerializer(), "registered serializer is retained");
            return decoded;
        } finally { buffer.release(); }
    }

    private static ServerPlayer owner(GameTestHelper helper, String name, String research) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        var knowledge = ThaumcraftCapabilities.getKnowledge(player);
        if (research != null && !research.isEmpty()) knowledge.addResearch(research);
        return player;
    }

    private static List<ItemStack> components(GameTestHelper helper, List<Ingredient> ingredients) {
        List<ItemStack> result = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            helper.assertTrue(ingredient.getItems().length > 0, "actual registered ingredient/tag has a material");
            ItemStack stack = ingredient.getItems()[0].copyWithCount(1);
            helper.assertTrue(ingredient.test(stack), "source component representative satisfies its native predicate");
            result.add(stack);
        }
        return result;
    }

    private static void equalCosts(GameTestHelper helper, AspectList expected, AspectList actual, String label) {
        helper.assertTrue(expected.aspects.equals(actual.aspects), label + " exact counted source costs");
    }

    @GameTest(templateNamespace = "thaumcraft_jei", template = "scan_test")
    public static void fixedOutputSnapshotRetainsRegisteredComponentsCostsAndNativeAssembly(GameTestHelper helper) {
        var holder = source(helper, "mindbiothaumic");
        InfusionRecipe recipe = (InfusionRecipe)roundTrip(helper, holder).value();
        var player = owner(helper, "TC_JEI_Fixed", recipe.getResearch());
        try {
            ItemStack central = recipe.getRecipeInput().getItems()[0].copy();
            List<ItemStack> materials = components(helper, recipe.getComponents());
            helper.assertTrue(recipe.matches(materials, central, helper.getLevel(), player), "native paid matrix recipe still matches known owner");
            equalCosts(helper, holder.value().getAspects(), recipe.getAspects(), "fixed infusion");
            ItemStack assembled = recipe.assemble(new InfusionInput(central, materials, player), helper.getLevel().registryAccess());
            helper.assertTrue(ItemStack.isSameItemSameComponents(assembled, (ItemStack)holder.value().getRecipeOutput()), "actual fixed output assembles after snapshot round trip");
            ThaumcraftCapabilities.getKnowledge(player).removeResearch(recipe.getResearch());
            helper.assertFalse(recipe.matches(materials, central, helper.getLevel(), player), "metadata round trip grants no research");
        } finally { PlayerDataSync.forget(player); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "thaumcraft_jei", template = "scan_test")
    public static void sixTagOutputSnapshotsPreserveCentralDataAndApplyOnlyTheirActualSourceModification(GameTestHelper helper) {
        for (String path : List.of("helmgoggles", "maskangryghost", "maskgrinningdevil", "masksippingfiend", "verdantheartlife", "verdantheartsustain")) {
            var holder = source(helper, path);
            InfusionRecipe recipe = (InfusionRecipe)roundTrip(helper, holder).value();
            helper.assertTrue(recipe.getRecipeOutput() instanceof Object[], "source tag modification stays dynamic, without a fake fixed output");
            var player = owner(helper, "TC_JEI_Tag", recipe.getResearch());
            try {
                ItemStack central = recipe.getRecipeInput().getItems()[0].copy();
                central.set(DataComponents.CUSTOM_NAME, Component.literal("Preserved source central name"));
                List<ItemStack> materials = components(helper, recipe.getComponents());
                helper.assertTrue(recipe.matches(materials, central, helper.getLevel(), player), "literal known source gate and real pedestal components: " + path);
                ItemStack actual = recipe.assemble(new InfusionInput(central, materials, player), helper.getLevel().registryAccess());
                ItemStack expected = central.copy(); Object[] tag = (Object[])holder.value().getRecipeOutput();
                helper.assertTrue(InfusionRecipes.applyOutputTag(expected, (String)tag[0], (net.minecraft.nbt.Tag)tag[1], helper.getLevel().registryAccess()), "genuine source tag applies");
                helper.assertTrue(ItemStack.isSameItemSameComponents(actual, expected), "source result preserves all central components: " + path);
                helper.assertTrue(central.get(DataComponents.CUSTOM_NAME).getString().equals("Preserved source central name"), "assembly did not mutate input");
                equalCosts(helper, holder.value().getAspects(), recipe.getAspects(), path);
            } finally { PlayerDataSync.forget(player); }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "thaumcraft_jei", template = "scan_test")
    public static void eightEnchantmentDescriptorsAndConstrainedMutableOverlayKeepNativeCostsAndExpressionGate(GameTestHelper helper) {
        int levels = 0;
        for (String name : List.of("burrowing", "collector", "destructive", "refining", "sounding", "arcing", "essence", "lamplight")) {
            var holder = source(helper, "ie" + name);
            InfusionEnchantmentRecipe recipe = (InfusionEnchantmentRecipe)roundTrip(helper, holder).value();
            helper.assertTrue(recipe.enchantment == ((InfusionEnchantmentRecipe)holder.value()).enchantment, "source enchantment ordinal identity");
            levels += recipe.enchantment.maxLevel;
            equalCosts(helper, holder.value().getAspects(), recipe.getAspects(), name);
        }
        helper.assertTrue(levels == 21, "eight source recipes expose exactly21 legitimate upgrade levels");
        InfusionEnchantmentRecipe overlay = (InfusionEnchantmentRecipe)roundTrip(helper, source(helper, "iearcing")).value();
        ItemStack central = new ItemStack(Items.IRON_SWORD);
        central.set(DataComponents.CUSTOM_NAME, Component.literal("Source constrained sword"));
        EnumInfusionEnchantment.addInfusionEnchantment(central, EnumInfusionEnchantment.COLLECTOR, 1);
        EnumInfusionEnchantment.addInfusionEnchantment(central, EnumInfusionEnchantment.ARCING, 1);
        overlay.restoreJeiSnapshotBase("FIRSTSTEPS@2&&INFUSIONENCHANTMENT", 11, overlay.getAspects(),
            new IngredientNBTTC(central).toVanilla(), overlay.getComponents(), "thaumcraft:jei_source_overlay");
        InfusionEnchantmentRecipe restored = (InfusionEnchantmentRecipe)roundTrip(helper, new RecipeHolder<>(id("jei_test_enchantment_overlay"), overlay)).value();
        helper.assertTrue(restored.instability == 11 && restored.getGroup().equals("thaumcraft:jei_source_overlay"), "mutable base instability and group are not reset by compact serializer");
        var player = owner(helper, "TC_JEI_Enchant", "FIRSTSTEPS");
        try {
            var knowledge = ThaumcraftCapabilities.getKnowledge(player);
            knowledge.setResearchStage("FIRSTSTEPS", 2); knowledge.addResearch("INFUSIONENCHANTMENT");
            List<ItemStack> materials = components(helper, restored.getComponents());
            helper.assertTrue(restored.matches(materials, central, helper.getLevel(), player), "enchantment uses source nonstrict helper expression gate");
            helper.assertFalse(restored.matches(materials, new ItemStack(Items.IRON_SWORD), helper.getLevel(), player), "component-sensitive source central restriction survives serialization");
            AspectList costs = restored.getAspects(player, central, materials);
            helper.assertTrue(costs.getAmount(Aspect.ENERGY) == 93 && costs.getAmount(Aspect.AIR) == 139, "level2 plus one other enchantment costs40/60 ×2.33 with TC6 float truncation");
            ItemStack result = (ItemStack)restored.getRecipeOutput(player, central, materials);
            helper.assertTrue(EnumInfusionEnchantment.getInfusionEnchantmentLevel(result, EnumInfusionEnchantment.ARCING) == 2
                && EnumInfusionEnchantment.getInfusionEnchantmentLevel(result, EnumInfusionEnchantment.COLLECTOR) == 1
                && result.get(DataComponents.CUSTOM_NAME).equals(central.get(DataComponents.CUSTOM_NAME)), "native output raises only source enchantment and retains existing properties");
            knowledge.setResearchStage("FIRSTSTEPS", 1);
            helper.assertFalse(restored.matches(materials, central, helper.getLevel(), player), "source stage qualifier still rejects stage1 after round trip");
        } finally { PlayerDataSync.forget(player); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "thaumcraft_jei", template = "scan_test")
    public static void runicOverlayRetainsConstrainedCentralAndLiteralGateWhileCostsRemainInputDependent(GameTestHelper helper) {
        InfusionRunicAugmentRecipe overlay = (InfusionRunicAugmentRecipe)roundTrip(helper, source(helper, "runicarmor")).value();
        ItemStack central = new ItemStack(Items.IRON_CHESTPLATE);
        var tag = new net.minecraft.nbt.CompoundTag(); tag.putByte("TC.RUNIC", (byte)2);
        central.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        central.set(DataComponents.CUSTOM_NAME, Component.literal("Source constrained armour"));
        overlay.restoreJeiSnapshotBase("FIRSTSTEPS@2", 14, new AspectList().add(Aspect.AIR, 7),
            new IngredientNBTTC(central).toVanilla(), overlay.getComponents(central), "thaumcraft:jei_runic_overlay");
        InfusionRunicAugmentRecipe restored = (InfusionRunicAugmentRecipe)roundTrip(helper, new RecipeHolder<>(id("jei_test_runic_overlay"), overlay)).value();
        helper.assertTrue(restored.getResearch().equals("FIRSTSTEPS@2") && restored.instability == 14
            && restored.getGroup().equals("thaumcraft:jei_runic_overlay") && restored.getAspects().getAmount(Aspect.AIR) == 7
            && restored.getComponents().size() == 4, "compact runic serializer preserves all validated source base overlay fields");
        var player = owner(helper, "TC_JEI_Runic", "FIRSTSTEPS");
        try {
            var knowledge = ThaumcraftCapabilities.getKnowledge(player); knowledge.setResearchStage("FIRSTSTEPS", 2);
            List<ItemStack> materials = components(helper, restored.getComponents(central));
            helper.assertTrue(materials.size() == 4 && restored.matches(materials, central, helper.getLevel(), player), "charge2 consumes salis mundus and three real amber ingredients");
            helper.assertFalse(restored.matches(materials, new ItemStack(Items.IRON_CHESTPLATE), helper.getLevel(), player), "named/charged central restriction retained");
            AspectList costs = restored.getAspects(player, central, materials);
            helper.assertTrue(costs.size() == 3 && costs.getAmount(Aspect.PROTECT) == 100 && costs.getAmount(Aspect.CRYSTAL) == 50
                && costs.getAmount(Aspect.ENERGY) == 50 && restored.getInstability(player, central, materials) == 6, "actual runic charge drives source costs and instability");
            ItemStack result = (ItemStack)restored.getRecipeOutput(player, central, materials);
            helper.assertTrue(InfusionRunicAugmentRecipe.getRunicCharge(result) == 3
                && result.get(DataComponents.CUSTOM_NAME).equals(central.get(DataComponents.CUSTOM_NAME))
                && InfusionRunicAugmentRecipe.getRunicCharge(central) == 2, "native runic output raises exactly one charge without mutating central");
            knowledge.setResearchStage("FIRSTSTEPS", 1);
            helper.assertFalse(restored.matches(materials, central, helper.getLevel(), player), "source literal @2 gate rejects lower stage");
        } finally { PlayerDataSync.forget(player); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "thaumcraft_jei", template = "scan_test")
    public static void crossKindSequenceUsesRealSerializersAndUnambiguousSupplementFraming(GameTestHelper helper) {
        List<RecipeHolder<?>> holders = List.of(helper.getLevel().getRecipeManager().getAllRecipesFor(CrucibleRecipes.TYPE).getFirst(),
            source(helper, "mindbiothaumic"), source(helper, "helmgoggles"), source(helper, "iearcing"), source(helper, "runicarmor"));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            for (var holder : holders) JeiRecipeSnapshotCodec.encode(buffer, holder);
            for (var expected : holders) {
                var actual = JeiRecipeSnapshotCodec.decode(buffer);
                helper.assertTrue(actual.id().equals(expected.id()) && actual.value().getClass() == expected.value().getClass(), "sequential crucible/fixed/tag/enchantment/runic framing retains each source identity");
            }
            helper.assertTrue(!buffer.isReadable(), "all supplemental bytes consumed exactly");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "thaumcraft_jei", template = "scan_test")
    public static void missingInfusionSupplementIsRejectedInsteadOfResettingSourceDefaults(GameTestHelper helper) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            RecipeHolder.STREAM_CODEC.encode(buffer, source(helper, "iearcing")); buffer.writeBoolean(false);
            boolean rejected = false;
            try { JeiRecipeSnapshotCodec.decode(buffer); } catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "missing dynamic base metadata cannot widen source recipe constraints");
        } finally { buffer.release(); }
        helper.succeed();
    }
}
