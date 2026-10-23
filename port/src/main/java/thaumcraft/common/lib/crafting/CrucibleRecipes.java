package thaumcraft.common.lib.crafting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.common.crafting.IngredientType;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.IngredientNBTTC;
import thaumcraft.api.internal.CommonInternals;

/** Native datapack recipes plus the original server-owned add-on recipe catalog. */
@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class CrucibleRecipes {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "crucible");
    public static final RecipeType<CrucibleRecipe> TYPE = new RecipeType<>() {
        @Override public String toString() { return ID.toString(); }
    };
    public static final RecipeSerializer<CrucibleRecipe> SERIALIZER = new CrucibleRecipe.Serializer();
    public static final IngredientType<IngredientNBTTC> NBT_INGREDIENT_TYPE = new IngredientType<>(IngredientNBTTC.CODEC);
    private static MinecraftServer server;
    private static final LinkedHashMap<ResourceLocation, CrucibleRecipe> GENERATED_HEDGE = new LinkedHashMap<>();

    private CrucibleRecipes() {}

    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.RECIPE_TYPE, helper -> helper.register(ID, TYPE));
        event.register(Registries.RECIPE_SERIALIZER, helper -> helper.register(ID, SERIALIZER));
        event.register(NeoForgeRegistries.Keys.INGREDIENT_TYPES, helper -> helper.register(
            ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "nbt"), NBT_INGREDIENT_TYPE));
    }

    /** Same lifecycle as the aspect manager; never retains a stopped world's RecipeManager. */
    public static void setServer(@Nullable MinecraftServer current) {
        if (current == null || server != current) {
            GENERATED_HEDGE.forEach((id, recipe) -> CommonInternals.craftingRecipeCatalog.remove(id, recipe));
            GENERATED_HEDGE.clear();
        }
        server = current;
    }

    /** Source put/replace semantics. An add-on cannot mutate a live server catalog off-thread. */
    public static void register(ResourceLocation id, CrucibleRecipe recipe) {
        if (server != null && !server.isSameThread()) throw new IllegalStateException("Crucible registration must run on the server owner thread");
        CommonInternals.craftingRecipeCatalog.put(Objects.requireNonNull(id), Objects.requireNonNull(recipe));
    }

    /** ConfigRecipes.postAspects: call AFTER the real ConfigAspects.postInit, before tag warm-up.
     * These eight source API recipes derive their costs from the current actual object-tag table.
     * They are not baked from the read-only research-book oracle, and no missing tag becomes free.
     * Native RecipeManager still owns the other 46 JSON recipes; all() merges these real API entries.
     */
    public static void postAspects() {
        if (server == null || !server.isSameThread()) throw new IllegalStateException("Hedge recipes require the live server owner thread");
        LinkedHashMap<ResourceLocation, CrucibleRecipe> rebuilt = new LinkedHashMap<>();
        hedgeCopy(rebuilt, "hedge_gunpowder", new ItemStack(Items.GUNPOWDER, 2), new ItemStack(Items.GUNPOWDER));
        hedgeCopy(rebuilt, "hedge_slime", new ItemStack(Items.SLIME_BALL, 2), new ItemStack(Items.SLIME_BALL));
        AspectList glowstone = ThaumcraftCraftingManager.getObjectTags(new ItemStack(Items.GLOWSTONE_DUST));
        if (glowstone != null) addHedge(rebuilt, "hedge_glowstone", "HEDGEALCHEMY@2", new ItemStack(Items.GLOWSTONE_DUST, 2), "dustGlowstone", glowstone);
        // Source Items.DYE metadata 0 is INK_SAC, not flattened BLACK_DYE.
        hedgeCopy(rebuilt, "hedge_dye", new ItemStack(Items.INK_SAC, 2), new ItemStack(Items.INK_SAC));
        hedgeConversion(rebuilt, "hedge_clay", new ItemStack(Items.CLAY_BALL), new ItemStack(Items.DIRT));
        hedgeConversion(rebuilt, "hedge_string", new ItemStack(Items.STRING), new ItemStack(Items.WHEAT));
        hedgeConversion(rebuilt, "hedge_web", new ItemStack(Items.COBWEB), new ItemStack(Items.STRING));
        addHedge(rebuilt, "hedge_lava", "HEDGEALCHEMY@3", new ItemStack(Items.LAVA_BUCKET), new ItemStack(Items.BUCKET),
            new AspectList().add(Aspect.FIRE, 15).add(Aspect.EARTH, 5));

        // /reload updates only our own previous values. Preserve explicit add-on replacements.
        GENERATED_HEDGE.forEach((id, recipe) -> {
            if (!rebuilt.containsKey(id)) CommonInternals.craftingRecipeCatalog.remove(id, recipe);
        });
        LinkedHashMap<ResourceLocation, CrucibleRecipe> accepted = new LinkedHashMap<>();
        rebuilt.forEach((id, recipe) -> {
            Object existing = CommonInternals.craftingRecipeCatalog.get(id);
            if (existing == null || existing == GENERATED_HEDGE.get(id)) {
                CommonInternals.craftingRecipeCatalog.put(id, recipe);
                accepted.put(id, recipe);
            }
        });
        GENERATED_HEDGE.clear();
        GENERATED_HEDGE.putAll(accepted);
    }

    private static void hedgeCopy(LinkedHashMap<ResourceLocation, CrucibleRecipe> rebuilt, String id, ItemStack output, ItemStack input) {
        AspectList costs = ThaumcraftCraftingManager.getObjectTags(input);
        if (costs != null) addHedge(rebuilt, id, "HEDGEALCHEMY@2", output, input, costs);
    }
    private static void hedgeConversion(LinkedHashMap<ResourceLocation, CrucibleRecipe> rebuilt, String id, ItemStack output, ItemStack input) {
        AspectList outputTags = ThaumcraftCraftingManager.getObjectTags(output);
        AspectList inputTags = ThaumcraftCraftingManager.getObjectTags(input);
        if (outputTags != null && inputTags != null)
            addHedge(rebuilt, id, "HEDGEALCHEMY@3", output, input, CrucibleAspectCosts.subtract(inputTags, outputTags));
    }
    private static void addHedge(LinkedHashMap<ResourceLocation, CrucibleRecipe> rebuilt, String id, String research, ItemStack output, Object input, AspectList costs) {
        if (CrucibleAspectCosts.valid(costs)) rebuilt.put(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, id),
            new CrucibleRecipe(research, output, input, costs));
    }

    /** No cache: /reload and add-on replacement are visible at the next actual craft attempt. */
    public static List<RecipeHolder<CrucibleRecipe>> all(@Nullable Level level) {
        LinkedHashMap<ResourceLocation, RecipeHolder<CrucibleRecipe>> recipes = new LinkedHashMap<>();
        if (level != null) for (RecipeHolder<CrucibleRecipe> recipe : level.getRecipeManager().getAllRecipesFor(TYPE))
            recipes.put(recipe.id(), recipe);
        // TC6's public map is a HashMap. Do not pretend its equal-cost tie order was insertion order.
        CommonInternals.craftingRecipeCatalog.forEach((id, recipe) -> {
            if (recipe instanceof CrucibleRecipe crucible) recipes.put(id, new RecipeHolder<>(id, crucible));
            else recipes.remove(id); // Source API replacement can change the recipe's kind too.
        });
        return List.copyOf(recipes.values());
    }

    /** Source findMatchingCrucibleRecipe: strict completed/stage-qualified research, one catalyst,
     * greatest TOTAL aspect cost, strict greater-than ties and zero-cost recipe never selected.
     */
    @Nullable public static CrucibleRecipe findMatching(@Nullable Player owner, AspectList contents, ItemStack lastDrop) {
        if (owner == null || owner.level().isClientSide) return null;
        RecipeHolder<CrucibleRecipe> result = selectMatching(all(owner.level()),
            key -> ThaumcraftCapabilities.knowsResearchStrict(owner, key), contents, lastDrop);
        return result == null ? null : result.value();
    }

    /** Pure selection seam shared with the live owner path; it neither consumes nor awards anything. */
    @Nullable public static RecipeHolder<CrucibleRecipe> selectMatching(Iterable<RecipeHolder<CrucibleRecipe>> recipes,
        Predicate<String> strictResearch, AspectList contents, ItemStack catalyst) {
        if (contents == null || catalyst == null || catalyst.isEmpty()) return null;
        ItemStack one = catalyst.copyWithCount(1);
        return CrucibleAspectCosts.greatestMatching(recipes,
            holder -> strictResearch.test(holder.value().getResearch()) && holder.value().matches(contents, one),
            holder -> holder.value().getAspects().visSize());
    }

    /** Source output lookup ignores output NBT/components; old metadata is flattened into item ids. */
    @Nullable public static CrucibleRecipe findByOutput(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Level level = server != null && server.isSameThread() ? server.overworld() : null;
        for (RecipeHolder<CrucibleRecipe> holder : all(level)) {
            ItemStack output = holder.value().getRecipeOutput();
            if (output.is(stack.getItem()) && output.getDamageValue() == stack.getDamageValue()) return holder.value();
        }
        return null;
    }

    /** MODERN process-local hash only, explicitly not a legacy NBT hash migration route. */
    @Nullable public static CrucibleRecipe findByModernHash(int hash) {
        Level level = server != null && server.isSameThread() ? server.overworld() : null;
        for (RecipeHolder<CrucibleRecipe> holder : all(level)) if (holder.value().hash == hash) return holder.value();
        return null;
    }
}
