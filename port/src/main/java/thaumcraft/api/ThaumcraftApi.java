package thaumcraft.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.AspectEventProxy;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.internal.CommonInternals;
import thaumcraft.api.internal.DummyInternalMethodHandler;
import thaumcraft.api.internal.IInternalMethodHandler;

public class ThaumcraftApi {
    public static java.util.HashMap<ResourceLocation, Object> getCraftingRecipesFake() { return CommonInternals.craftingRecipeCatalogFake; }
    public static void addFakeCraftingRecipe(ResourceLocation registry, Object recipe) { getCraftingRecipesFake().put(registry, recipe); }
    public static void addWarpToItem(ItemStack stack, int amount) {
        CommonInternals.warpMap.put(java.util.Arrays.asList(stack.getItem(),
            thaumcraft.common.lib.research.LegacyScanIdentity.rawDamage(stack)), amount);
    }
    public static int getWarp(ItemStack stack) {
        return stack == null ? 0 : CommonInternals.warpMap.getOrDefault(java.util.Arrays.asList(stack.getItem(),
            thaumcraft.common.lib.research.LegacyScanIdentity.rawDamage(stack)), 0);
    }
    public static void addLootBagItem(ItemStack item, int weight, int... bagTypes) {
        if (bagTypes == null || bagTypes.length == 0) bagTypes = new int[]{0};
        for (int rarity : bagTypes) if (rarity >= 0 && rarity <= 2) {
            thaumcraft.api.internal.WeightedRandomLoot.pool(thaumcraft.common.lib.utils.LegacyLootRules.Rarity.values()[rarity])
                .add(new thaumcraft.api.internal.WeightedRandomLoot(item, weight));
        }
    }
    /** Optional explicit marker for an addon's modern enchantment, consumed on registry reload/sync. */
    public static void registerEnchantmentScanName(net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key, String legacyName) {
        thaumcraft.common.config.ConfigResearch.enchantmentScanNames.put(
            java.util.Objects.requireNonNull(key), java.util.Objects.requireNonNull(legacyName));
    }
    /** Source crop registration; copies prevent harvesting from mutating an addon definition. */
    public static void registerSeed(net.minecraft.world.level.block.Block block, ItemStack seed) {
        CommonInternals.seedList.put(java.util.Objects.requireNonNull(block).getDescriptionId(),
            java.util.Objects.requireNonNull(seed).copy());
    }
    /** Retains the original nullable missing-seed contract. Returned stacks are isolated from consumption. */
    public static ItemStack getSeed(net.minecraft.world.level.block.Block block) {
        ItemStack seed = CommonInternals.seedList.get(java.util.Objects.requireNonNull(block).getDescriptionId());
        return seed == null ? null : seed.copy();
    }
    /** Original public mutable addon descriptor. Chance/RNG execution belongs to the infernal furnace.
     * Native flattened wildcard definitions use a bounded modern Alternatives descriptor in 'in';
     * modern consumers must use SourceSmeltingBonusCatalog.matches, not cast every non-stack to String.
     * Legacy matching is bounded to this source's ore names. Other old OreDictionary categories
     * require an explicit modern namespaced tag string; registration itself retains any source String.
     */
    public static class SmeltBonus {
        public Object in;
        public ItemStack out;
        public float chance;
        public SmeltBonus(Object in, ItemStack out, float chance) { this.in = in; this.out = out; this.chance = chance; }
    }
    public static void addSmeltingBonus(Object in, ItemStack out, float chance) {
        if (in instanceof ItemStack || in instanceof String) CommonInternals.smeltingBonus.add(new SmeltBonus(in,out,chance));
    }
    public static void addSmeltingBonus(Object in, ItemStack out) { addSmeltingBonus(in,out,0.33F); }
    /** Original source multiblock catalogue descriptor, separate from its executable dust trigger. */
    public static class BluePrint implements thaumcraft.api.crafting.IThaumcraftRecipe {
        private final thaumcraft.api.crafting.Part[][][] parts;
        private final String research;
        private final ItemStack displayStack;
        private final ItemStack[] ingredientList;
        private String group;
        public BluePrint(String research,thaumcraft.api.crafting.Part[][][] parts,ItemStack... ingredients) { this(research,null,parts,ingredients); }
        public BluePrint(String research,ItemStack display,thaumcraft.api.crafting.Part[][][] parts,ItemStack... ingredients) { this.research = research; this.displayStack = display; this.parts = parts; this.ingredientList = ingredients; }
        public thaumcraft.api.crafting.Part[][][] getParts() { return parts; }
        @Override public String getResearch() { return research; }
        public ItemStack[] getIngredientList() { return ingredientList; }
        public ItemStack getDisplayStack() { return displayStack; }
        @Override public String getGroup() { return group; }
        public BluePrint setGroup(ResourceLocation value) { group = value.toString(); return this; }
    }
    public static void addMultiblockRecipeToCatalog(ResourceLocation id,BluePrint recipe) { getCraftingRecipes().put(java.util.Objects.requireNonNull(id),java.util.Objects.requireNonNull(recipe)); }
    public static IInternalMethodHandler internalMethods = new DummyInternalMethodHandler();
    public static java.util.HashMap<ResourceLocation, thaumcraft.api.crafting.IThaumcraftRecipe> getCraftingRecipes() {
        return CommonInternals.craftingRecipeCatalog;
    }
    public static void addCrucibleRecipe(ResourceLocation id, thaumcraft.api.crafting.CrucibleRecipe recipe) {
        thaumcraft.common.lib.crafting.CrucibleRecipes.register(id, recipe);
    }
    public static void addArcaneCraftingRecipe(ResourceLocation id, thaumcraft.api.crafting.IArcaneRecipe recipe) {
        thaumcraft.common.lib.crafting.ArcaneWorkbenchRecipes.register(id,recipe);
    }
    public static void addInfusionCraftingRecipe(ResourceLocation id, thaumcraft.api.crafting.InfusionRecipe recipe) {
        thaumcraft.common.lib.crafting.InfusionRecipes.register(id,recipe);
    }
    public static thaumcraft.api.crafting.InfusionRecipe getInfusionRecipe(ItemStack stack) {
        return thaumcraft.common.lib.crafting.InfusionRecipes.findByOutput(stack);
    }
    public static thaumcraft.api.crafting.CrucibleRecipe getCrucibleRecipe(ItemStack stack) {
        return thaumcraft.common.lib.crafting.CrucibleRecipes.findByOutput(stack);
    }
    /** Modern process-local hash, NOT migration of the old NBT-derived recipe hashes. */
    public static thaumcraft.api.crafting.CrucibleRecipe getCrucibleRecipeFromHash(int hash) {
        return thaumcraft.common.lib.crafting.CrucibleRecipes.findByModernHash(hash);
    }

    /**
     * Source-faithful TC6 registration: exact location-string key, first registration wins.
     * Resources now come from server datapack data, not client assets. Register on the mod's
     * owning setup thread; this deliberately retains the original public mutable map API.
     */
    public static void registerResearchLocation(ResourceLocation loc) {
        if (!CommonInternals.jsonLocs.containsKey(loc.toString())) {
            CommonInternals.jsonLocs.put(loc.toString(), loc);
        }
    }

    /**
     * In TC6 the method looked up a string key in a Map with Integer keys and always returned false.
     * Here it is fixed: an exact match of the item with its components, or the item without them.
     */
    public static boolean exists(ItemStack item) {
        return CommonInternals.objectTags.containsKey(CommonInternals.generateUniqueItemstackId(item))
            || CommonInternals.objectTags.containsKey(CommonInternals.generateUniqueItemstackIdStripped(item));
    }

    @Deprecated
    public static void registerObjectTag(ItemStack item, AspectList aspects) {
        new AspectEventProxy().registerObjectTag(item, aspects);
    }

    /** Old metadata arrays resolve to existing flattened variants; no unavailable variant is invented. */
    @Deprecated
    public static void registerObjectTag(ItemStack item, int[] metadata, AspectList aspects) {
        if (metadata == null) { registerObjectTag(item, aspects); return; }
        var identity = thaumcraft.common.lib.research.LegacyScanIdentity.identify(item);
        for (int meta : metadata) for (var candidate : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (candidate == net.minecraft.world.item.Items.AIR) continue;
            ItemStack variant = new ItemStack(candidate);
            var nativeIdentity = thaumcraft.common.lib.research.LegacyScanIdentity.identify(variant);
            if (!nativeIdentity.itemId().equals(identity.itemId())) continue;
            if (variant.isDamageableItem()) {
                if (meta != 32767) variant.setDamageValue(meta);
            } else if (meta != 32767 && nativeIdentity.metadata() != meta) continue;
            variant.applyComponents(item.getComponentsPatch().forget(type -> type == net.minecraft.core.component.DataComponents.DAMAGE));
            registerObjectTag(variant, aspects);
        }
    }

    @Deprecated
    public static void registerObjectTag(String itemTag, AspectList aspects) {
        new AspectEventProxy().registerObjectTag(itemTag, aspects);
    }

    @Deprecated
    public static void registerComplexObjectTag(ItemStack item, AspectList aspects) {
        new AspectEventProxy().registerComplexObjectTag(item, aspects);
    }

    @Deprecated
    public static void registerComplexObjectTag(String itemTag, AspectList aspects) {
        new AspectEventProxy().registerComplexObjectTag(itemTag, aspects);
    }

    /** entityName: an id from the EntityType registry, for example "minecraft:zombie". */
    @Deprecated
    public static void registerEntityTag(String entityName, AspectList aspects, ThaumcraftApi.EntityTagsNBT... nbt) {
        CommonInternals.scanEntities.add(new ThaumcraftApi.EntityTags(entityName, aspects, nbt));
    }

    public static class EntityTags {
        public String entityName;
        public ThaumcraftApi.EntityTagsNBT[] nbts;
        public AspectList aspects;

        public EntityTags(String entityName, AspectList aspects, ThaumcraftApi.EntityTagsNBT... nbts) {
            this.entityName = entityName;
            this.nbts = nbts;
            this.aspects = aspects;
        }
    }

    public static class EntityTagsNBT {
        public String name;
        public Object value;

        public EntityTagsNBT(String name, Object value) {
            this.name = name;
            this.value = value;
        }
    }
}
