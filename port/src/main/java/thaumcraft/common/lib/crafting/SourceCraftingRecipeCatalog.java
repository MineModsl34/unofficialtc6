package thaumcraft.common.lib.crafting;

import java.util.LinkedHashMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import thaumcraft.api.crafting.IThaumcraftRecipe;
import thaumcraft.api.internal.CommonInternals;

/** Makes real native alchemy/infusion recipes visible through the original add-on API.
 * Arcane and ordinary recipes remain in RecipeManager, exactly as source GameData recipes did.
 * Never mirrors the read-only research-book display catalog or source fake display recipes.
 */
public final class SourceCraftingRecipeCatalog {
    private static final OwnedRecipeCatalog<ResourceLocation, IThaumcraftRecipe> MIRROR = new OwnedRecipeCatalog<>();
    private static MinecraftServer server;

    private SourceCraftingRecipeCatalog() {}

    /** Call after native recipe loading/reloading and before ConfigAspects/tag generation.
     * On shutdown pass null. Only identity-owned entries are refreshed or removed;
     * an add-on's map put/replace wins over the same native recipe id across /reload.
     */
    public static void setServer(@Nullable MinecraftServer current) {
        if (current != null && !current.isSameThread())
            throw new IllegalStateException("Recipe API mirror requires the server owner thread");
        if (current == null || current != server) MIRROR.clear(CommonInternals.craftingRecipeCatalog);
        server = current;
        if (current == null) { SourceFakeRecipeCatalog.setServer(null); return; }
        LinkedHashMap<ResourceLocation, IThaumcraftRecipe> nativeRecipes = new LinkedHashMap<>();
        for (var holder : current.getRecipeManager().getAllRecipesFor(CrucibleRecipes.TYPE))
            nativeRecipes.put(holder.id(), holder.value());
        for (var holder : current.getRecipeManager().getAllRecipesFor(InfusionRecipes.TYPE))
            nativeRecipes.put(holder.id(), holder.value());
        MIRROR.rebuild(CommonInternals.craftingRecipeCatalog, nativeRecipes);
        SourceFakeRecipeCatalog.setServer(current);
    }
}
