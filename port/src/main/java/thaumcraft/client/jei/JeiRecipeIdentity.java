package thaumcraft.client.jei;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Stable bookmark/source IDs with distinct JEI equality identities for replaced recipe bodies. */
public final class JeiRecipeIdentity {
    private record Identity(ResourceLocation logical, ResourceLocation source) {}
    private static final IdentityHashMap<RecipeHolder<?>, Identity> IDENTITIES = new IdentityHashMap<>();
    private static final Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> CURRENT = new HashMap<>();
    private static long generation;
    private JeiRecipeIdentity() {}
    static long nextGeneration() { return ++generation; }
    static <R extends Recipe<?>> void begin(RecipeType<RecipeHolder<R>> type) { CURRENT.put(type, new HashMap<>()); }
    static <R extends Recipe<?>> RecipeHolder<R> attach(RecipeType<RecipeHolder<R>> type, RecipeHolder<R> original,
        ResourceLocation source, long generation) {
        var logical = original.id();
        var holder = new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("thaumcraft",
            "jei/" + generation + "/" + logical.getNamespace() + "/" + logical.getPath()), original.value());
        IDENTITIES.put(holder, new Identity(logical, source)); CURRENT.get(type).put(logical, holder); return holder;
    }
    public static ResourceLocation sourceId(RecipeHolder<?> holder) {
        var identity = IDENTITIES.get(holder); return identity == null ? holder.id() : identity.source;
    }
    static ResourceLocation logicalId(RecipeHolder<?> holder) {
        var identity = IDENTITIES.get(holder); return identity == null ? holder.id() : identity.logical;
    }
    static <R extends Recipe<?>> boolean isCurrent(RecipeType<RecipeHolder<R>> type, RecipeHolder<R> holder) {
        var active = CURRENT.get(type); return active != null && active.get(logicalId(holder)) == holder;
    }
    static <R extends Recipe<?>> Codec<RecipeHolder<R>> codec(RecipeType<RecipeHolder<R>> type) {
        return ResourceLocation.CODEC.comapFlatMap(id -> {
            var recipes = CURRENT.get(type); var holder = recipes == null ? null : recipes.get(id);
            if (holder == null) return DataResult.error(() -> "Recipe is absent from this connection: " + id);
            @SuppressWarnings("unchecked") RecipeHolder<R> current = (RecipeHolder<R>) holder;
            return DataResult.success(current);
        }, JeiRecipeIdentity::logicalId);
    }
    static void clear() { IDENTITIES.clear(); CURRENT.clear(); }
}
