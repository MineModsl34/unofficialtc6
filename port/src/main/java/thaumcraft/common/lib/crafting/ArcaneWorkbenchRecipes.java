package thaumcraft.common.lib.crafting;

import net.minecraft.core.registries.Registries;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;
import thaumcraft.api.crafting.IArcaneWorkbench;

@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ArcaneWorkbenchRecipes {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "arcane_shaped");
    public static final ResourceLocation SHAPELESS_ID = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "arcane_shapeless");
    // Preserve existing type ID while both serializers use one source-style matching pool.
    public static final RecipeType<IArcaneRecipe> TYPE = new RecipeType<>() {
        @Override public String toString() { return ID.toString(); }
    };
    public static final RecipeSerializer<RecipeArcaneWorkbench> SERIALIZER = new RecipeArcaneWorkbench.Serializer();
    public static final RecipeSerializer<ShapelessArcaneRecipe> SHAPELESS_SERIALIZER = new ShapelessArcaneRecipe.Serializer();
    public static final RecipeSerializer<ShapedArcaneVoidJar> VOID_JAR_SERIALIZER = new ShapedArcaneVoidJar.Serializer();
    private static final LinkedHashMap<ResourceLocation, IArcaneRecipe> ADDONS = new LinkedHashMap<>();
    private static MinecraftServer server;
    private ArcaneWorkbenchRecipes() {}

    /** Source GameData.register_impl recipes belong in the native recipe pool, not the
     * crucible/infusion catalog. Re-inject after /reload before normal native recipe sync.
     */
    public static void setServer(@Nullable MinecraftServer current) {
        server = current;
        if (current != null) mergeIntoNative(false);
    }
    public static void register(ResourceLocation id, IArcaneRecipe recipe) {
        Objects.requireNonNull(id); Objects.requireNonNull(recipe);
        if (server != null && !server.isSameThread()) throw new IllegalStateException("Arcane registration requires the server owner thread");
        ArcaneWorkbenchCosts.validateRecipeCosts(recipe.getGroup(), recipe.research(), recipe.vis(), recipe.crystals());
        if (recipe.getType() != TYPE) throw new IllegalArgumentException("An arcane recipe must use the native arcane type");
        ADDONS.put(id, recipe);
        if (server != null) mergeIntoNative(true);
    }
    private static void mergeIntoNative(boolean synchronize) {
        if (!server.isSameThread()) throw new IllegalStateException("Arcane merge requires the server owner thread");
        if (ADDONS.isEmpty()) return;
        LinkedHashMap<ResourceLocation, RecipeHolder<?>> merged = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) merged.put(holder.id(), holder);
        ADDONS.forEach((id, recipe) -> merged.put(id, new RecipeHolder<>(id, recipe)));
        server.getRecipeManager().replaceRecipes(merged.values());
        if (synchronize) server.getPlayerList().broadcastAll(new ClientboundUpdateRecipesPacket(server.getRecipeManager().getRecipes()));
    }
    public static List<RecipeHolder<IArcaneRecipe>> all(@Nullable net.minecraft.world.level.Level level) {
        return level == null ? List.of() : level.getRecipeManager().getAllRecipesFor(TYPE);
    }
    /** Source matching is independent of research and aura: those are checked when paying. */
    @Nullable public static RecipeHolder<IArcaneRecipe> findMatching(Container table, Player player) {
        if (!(table instanceof IArcaneWorkbench) || table.getContainerSize() < 15 || player == null) return null;
        List<ItemStack> inputs = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) inputs.add(table.getItem(slot));
        CraftingInput input = CraftingInput.of(3, 3, inputs);
        for (var holder : all(player.level())) if (holder.value().matches(input, player.level())
            && ArcaneWorkbenchCosts.hasCrystals(table, holder.value())) return holder;
        return null;
    }
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.RECIPE_TYPE, helper -> helper.register(ID, TYPE));
        event.register(Registries.RECIPE_SERIALIZER, helper -> {
            helper.register(ID, SERIALIZER);
            helper.register(SHAPELESS_ID, SHAPELESS_SERIALIZER);
            helper.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "arcane_void_jar"), VOID_JAR_SERIALIZER);
        });
    }
}
