package thaumcraft.common.lib.crafting;

import java.util.LinkedHashMap;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import thaumcraft.api.internal.CommonInternals;
import thaumcraft.api.items.ItemsTC;

/** The 13 ConfigRecipes source display-only objects, populated after native recipes/tags load.
 * They are never inserted into RecipeManager. Identity-owned refresh preserves addon overrides.
 */
public final class SourceFakeRecipeCatalog {
    private static final OwnedRecipeCatalog<ResourceLocation,Object> OWNED = new OwnedRecipeCatalog<>();
    private static MinecraftServer server;
    private SourceFakeRecipeCatalog() {}
    public static void setServer(MinecraftServer current) {
        if (current != null && !current.isSameThread()) throw new IllegalStateException("Fake recipe API refresh requires the server owner thread");
        if (current == null || current != server) OWNED.clear(CommonInternals.craftingRecipeCatalogFake);
        server = current;
        if (current == null) return;
        LinkedHashMap<ResourceLocation,Object> defaults = new LinkedHashMap<>();
        for (int charge = 0; charge < 3; charge++) {
            ItemStack input = new ItemStack(ItemsTC.ringMundane);
            if (charge > 0) { CompoundTag tag = new CompoundTag(); tag.putByte("TC.RUNIC",(byte)charge); input.set(DataComponents.CUSTOM_DATA,CustomData.of(tag)); }
            defaults.put(id("runicarmorfake" + charge),new InfusionRunicAugmentRecipe(input));
        }
        String[] names = {"burrowing","collector","destructive","refining","sounding","arcing","essence","lamplight"};
        net.minecraft.world.item.Item[] inputs = {Items.WOODEN_PICKAXE,Items.STONE_AXE,Items.STONE_PICKAXE,Items.IRON_PICKAXE,
            Items.GOLDEN_PICKAXE,Items.WOODEN_SWORD,Items.STONE_SWORD,Items.GOLDEN_PICKAXE};
        for (int index = 0; index < names.length; index++) {
            String name = "ie" + names[index];
            Object recipe = CommonInternals.getCatalogRecipe(id(name));
            if (recipe instanceof InfusionEnchantmentRecipe enchantment)
                defaults.put(id(name + "fake"),new InfusionEnchantmentRecipe(enchantment,new ItemStack(inputs[index])));
        }
        Ingredient meat = Ingredient.of(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
            ResourceLocation.parse("c:nuggets/meat")));
        defaults.put(id("triplemeattreatfake"),shapeless(new ItemStack(ItemsTC.tripleMeatTreat),meat,meat,meat,Ingredient.of(Items.SUGAR)));
        Ingredient crystal = Ingredient.of(ItemsTC.crystalEssence);
        defaults.put(id("salismundusfake"),shapeless(new ItemStack(ItemsTC.salisMundus),Ingredient.of(Items.FLINT),Ingredient.of(Items.BOWL),
            Ingredient.of(Items.REDSTONE),crystal,crystal,crystal));
        OWNED.rebuild(CommonInternals.craftingRecipeCatalogFake,defaults);
    }
    private static ShapelessRecipe shapeless(ItemStack result, Ingredient... inputs) {
        return new ShapelessRecipe("minecraft:",CraftingBookCategory.MISC,result,NonNullList.of(Ingredient.EMPTY,inputs));
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("thaumcraft",path); }
}
