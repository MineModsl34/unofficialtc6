package thaumcraft.api.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.AspectList;

// Public source registries use native item/component identities.
public class CommonInternals {
    /** Ordered source addon list, not a map: two oreGold/oreIron entries intentionally coexist. */
    public static ArrayList<ThaumcraftApi.SmeltBonus> smeltingBonus = new ArrayList<>();
    /** Original public crop-to-seed addon registry; modern block description IDs replace translation keys. */
    public static HashMap<String, ItemStack> seedList = new HashMap<>();
    /** TC6 public mutable registration map; runtime loading snapshots it before resource I/O. */
    public static HashMap<String, ResourceLocation> jsonLocs = new HashMap<>();
    /** Source public addon catalog, independent from the read-only book presentation catalog. */
    public static HashMap<ResourceLocation, thaumcraft.api.crafting.IThaumcraftRecipe> craftingRecipeCatalog = new HashMap<>();
    public static HashMap<ResourceLocation, Object> craftingRecipeCatalogFake = new HashMap<>();
    public static HashMap<java.util.List<Object>, Integer> warpMap = new HashMap<>();
    public static ArrayList<ThaumcraftApi.EntityTags> scanEntities = new ArrayList<>();
    public static ConcurrentHashMap<Integer, AspectList> objectTags = new ConcurrentHashMap<>();

    public static thaumcraft.api.crafting.IThaumcraftRecipe getCatalogRecipe(ResourceLocation key) {
        return craftingRecipeCatalog.get(key);
    }

    public static Object getCatalogRecipeFake(ResourceLocation key) {
        return craftingRecipeCatalogFake.get(key);
    }
    /**
     * Not in TC6: the stack each objectTags key was registered under. The keys are JVM-dependent hashes,
     * so syncing with the client needs the stack itself (the client recomputes the key on its side).
     */
    public static ConcurrentHashMap<Integer, ItemStack> objectTagStacks = new ConcurrentHashMap<>();
    /**
     * Not in TC6: objectTags keys obtained by generation from recipes (rather than explicit registration).
     * Allows recomputing them after ConfigAspects, once the table is full (see PORTING.md).
     */
    public static java.util.Set<Integer> generatedTags = ConcurrentHashMap.newKeySet();

    /**
     * In 1.12.2, the hash of the stack's NBT serialisation (item + meta + NBT).
     * In 1.21 meta and NBT are replaced by Data Components, so hash the item and its components.
     */
    public static int generateUniqueItemstackId(ItemStack stack) {
        return ItemStack.hashItemAndComponents(stack.copyWithCount(1));
    }

    /** The same, but without user components: the analogue of a stack without NBT in 1.12.2. */
    public static int generateUniqueItemstackIdStripped(ItemStack stack) {
        return ItemStack.hashItemAndComponents(new ItemStack(stack.getItem()));
    }
}
