package thaumcraft.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.lib.crafting.InfusionRunicAugmentRecipe;

/** One client pass over every loaded public recipe display, without executing a craft. */
public final class SourceRecipeClientAudit {
    private SourceRecipeClientAudit() {}
    public static JsonObject run(Minecraft minecraft) {
        if (!minecraft.isSameThread() || minecraft.player == null) throw new IllegalStateException("Client recipe audit requires the live owner thread");
        int real = 0, fake = 0, choices = 0;
        for (var entry : Map.copyOf(ThaumcraftApi.getCraftingRecipes()).entrySet()) {
            verify(entry.getKey(), entry.getValue(), minecraft); real++;
        }
        for (var entry : Map.copyOf(ThaumcraftApi.getCraftingRecipesFake()).entrySet()) {
            var view = verify(entry.getKey(), entry.getValue(), minecraft); fake++;
            if (entry.getKey().equals(ResourceLocation.parse("thaumcraft:salismundusfake"))) {
                choices = view.ingredients().get(3).size();
                if (choices != thaumcraft.api.aspects.Aspect.aspects.size()) throw new IllegalStateException("Book crystal choices do not include every aspect: " + choices);
            }
            if (entry.getValue() instanceof InfusionRunicAugmentRecipe) {
                int inputCharge = InfusionRunicAugmentRecipe.getRunicCharge(view.ingredients().getFirst().getFirst());
                if (InfusionRunicAugmentRecipe.getRunicCharge(view.output()) != inputCharge + 1) throw new IllegalStateException("Book runic display has the wrong output charge");
            }
        }
        if (real == 0 || fake != 13 || choices == 0) throw new IllegalStateException("Incomplete public source recipe catalogs: " + real + "/" + fake);
        var snapshot = thaumcraft.client.lib.ClientResearchPresentation.view();
        if (snapshot == null) throw new IllegalStateException("Missing received research view");
        int bookKeys = 0, bookVariants = 0;
        java.util.Set<ResourceLocation> handles = new java.util.LinkedHashSet<>();
        for (var entry : snapshot.spec().entries()) {
            for (var stage : entry.stages().values()) for (var raw : stage.recipes().values()) {
                ResourceLocation key = ResearchRecipeViewer.sourceKey(raw); if (key != null) handles.add(key);
            }
            for (var addendum : entry.addenda().values()) for (var raw : addendum.recipes().values()) {
                ResourceLocation key = ResearchRecipeViewer.sourceKey(raw); if (key != null) handles.add(key);
            }
        }
        JsonArray missing = new JsonArray(), conditional = new JsonArray(), missingModels = new JsonArray();
        for (var key : handles) {
            var variants = ResearchRecipeViewer.recipes(snapshot, key);
            if (variants.isEmpty()) {
                if (unavailableMetal(key)) conditional.add(key.toString()); else missing.add(key.toString());
                continue;
            }
            bookKeys++; bookVariants += variants.size();
        }
        int itemModels = 0, blockStates = 0;
        var missingModel = minecraft.getModelManager().getMissingModel();
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            if (!key.getNamespace().equals("thaumcraft")) continue;
            if (minecraft.getItemRenderer().getModel(new ItemStack(item), minecraft.level, minecraft.player, 0) == missingModel)
                missingModels.add("item " + key);
            itemModels++;
        }
        for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            if (!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("thaumcraft")) continue;
            for (var state : block.getStateDefinition().getPossibleStates()) {
                if (state.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) continue;
                if (minecraft.getBlockRenderer().getBlockModel(state) == missingModel) missingModels.add("block " + state);
                blockStates++;
            }
        }
        JsonObject report = new JsonObject(); report.addProperty("passed", missing.isEmpty() && missingModels.isEmpty());
        report.addProperty("realRecipesProjected", real); report.addProperty("fakeRecipesProjected", fake);
        report.addProperty("crystalAspectChoices", choices); report.addProperty("scope", "live client projection; JEI screenshots recorded separately");
        report.addProperty("bookRecipeKeysResolved", bookKeys); report.addProperty("bookRecipeVariantsResolved", bookVariants);
        report.add("missingBookRecipes", missing); report.add("unavailableConditionalRecipes", conditional);
        report.addProperty("itemModelsChecked", itemModels); report.addProperty("blockStateModelsChecked", blockStates);
        report.add("missingModels", missingModels);
        return report;
    }
    private static boolean unavailableMetal(ResourceLocation key) {
        if (!key.getNamespace().equals("thaumcraft") || !key.getPath().startsWith("metal_purification_")) return false;
        String metal = key.getPath().substring("metal_purification_".length());
        if (!java.util.List.of("copper", "tin", "silver", "lead").contains(metal)) return false;
        var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.parse("c:ores/" + metal));
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getTag(tag).map(values -> values.size() == 0).orElse(true);
    }
    private static ResearchRecipeViewer.Recipe verify(ResourceLocation id, Object value, Minecraft minecraft) {
        if (!ResearchApiRecipeDisplay.supported(value)) throw new IllegalStateException("Unsupported source API display " + id);
        var view = ResearchApiRecipeDisplay.project(id, value, minecraft.player.registryAccess());
        for (var slot : view.ingredients()) for (ItemStack stack : slot) {
            if (stack.isEmpty()) throw new IllegalStateException("Empty choice in book ingredient " + id);
            if (stack.is(ItemsTC.crystalEssence) && !stack.has(thaumcraft.api.items.TCDataComponents.ASPECTS)) throw new IllegalStateException("Aspectless book crystal " + id);
        }
        return view;
    }
}
