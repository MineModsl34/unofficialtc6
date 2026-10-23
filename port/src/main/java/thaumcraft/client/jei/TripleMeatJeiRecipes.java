package thaumcraft.client.jei;

import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import thaumcraft.Thaumcraft;
import thaumcraft.common.lib.crafting.RecipeTripleMeatTreat;

/** Exact client display projections for the source callback's three distinct meat chunks.
 * Each projection has one choice per slot, so vanilla JEI transfer cannot pick the same
 * chunk three times. Only the accepted native callback crafts on the server.
 */
public final class TripleMeatJeiRecipes {
    private static final List<String> CHUNKS = List.of("chunk_beef", "chunk_chicken", "chunk_pork",
        "chunk_fish", "chunk_rabbit", "chunk_mutton");
    private static List<RecipeHolder<CraftingRecipe>> sources = List.of(), recipes = List.of();
    private static Object connection;
    private static long generation;

    private TripleMeatJeiRecipes() {}

    /** Pure source metadata combinations, deterministic and without registry initialization. */
    record Triple(int first, int second, int third) {}
    static List<Triple> triples() {
        var result = new ArrayList<Triple>();
        for (int first = 0; first < 6; first++) for (int second = first + 1; second < 6; second++)
            for (int third = second + 1; third < 6; third++) result.add(new Triple(first, second, third));
        return List.copyOf(result);
    }

    public static void register(IRecipeRegistration registration) {
        rebuild(nativeSources()); registration.addRecipes(RecipeTypes.CRAFTING, recipes);
    }

    public static void tick(IJeiRuntime runtime) {
        var next = nativeSources(); var minecraft = Minecraft.getInstance(); var listener = minecraft.getConnection();
        Object nextConnection = listener == null ? null : listener.getConnection();
        if (connection == nextConnection && sameSources(next)) return;
        runtime.getRecipeManager().hideRecipes(RecipeTypes.CRAFTING, recipes);
        rebuild(next); runtime.getRecipeManager().addRecipes(RecipeTypes.CRAFTING, recipes);
    }

    public static void clear() { connection = null; sources = List.of(); recipes = List.of(); }
    static long revision() { return generation; }
    static List<RecipeHolder<CraftingRecipe>> current() { return recipes; }
    static boolean isCurrent(RecipeHolder<CraftingRecipe> holder) {
        return recipes.stream().anyMatch(current -> current == holder);
    }
    static boolean isProjection(RecipeHolder<CraftingRecipe> holder) {
        return holder.id().getNamespace().equals(Thaumcraft.MODID) && holder.id().getPath().startsWith("jei/triple_meat/");
    }
    static List<RecipeHolder<CraftingRecipe>> nativeSources() {
        var level = Minecraft.getInstance().level;
        return level == null ? List.of() : level.getRecipeManager()
            .getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING).stream()
            .filter(holder -> holder.value() instanceof RecipeTripleMeatTreat).toList();
    }

    private static boolean sameSources(List<RecipeHolder<CraftingRecipe>> next) {
        if (next.size() != sources.size()) return false;
        for (int index = 0; index < next.size(); index++)
            if (!next.get(index).id().equals(sources.get(index).id()) || next.get(index).value() != sources.get(index).value()) return false;
        return true;
    }

    private static void rebuild(List<RecipeHolder<CraftingRecipe>> next) {
        var minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) throw new IllegalStateException("Triple meat JEI projections require client MAIN");
        var listener = minecraft.getConnection(); connection = listener == null ? null : listener.getConnection();
        sources = next; var level = minecraft.level; var result = new ArrayList<RecipeHolder<CraftingRecipe>>();
        long revision = ++generation;
        if (level != null) for (var holder : sources) for (var triple : triples()) {
            var stacks = new ArrayList<ItemStack>(); stacks.add(new ItemStack(Items.SUGAR));
            for (int index : List.of(triple.first(), triple.second(), triple.third())) {
                var key = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, CHUNKS.get(index));
                stacks.add(BuiltInRegistries.ITEM.getOptional(key).map(ItemStack::new).orElse(ItemStack.EMPTY));
            }
            if (stacks.stream().anyMatch(ItemStack::isEmpty)) continue;
            var input = CraftingInput.of(2, 2, stacks);
            if (!holder.value().matches(input, level)) continue;
            var output = holder.value().assemble(input, level.registryAccess());
            if (output.isEmpty()) continue;
            var ingredients = NonNullList.<Ingredient>create();
            for (var stack : stacks) ingredients.add(Ingredient.of(stack.getItem()));
            var recipe = new ShapelessRecipe(holder.value().getGroup(), holder.value().category(), output.copy(), ingredients);
            var id = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "jei/triple_meat/" + holder.id().getNamespace()
                + "/" + holder.id().getPath() + "/" + revision + "/" + triple.first() + "_" + triple.second() + "_" + triple.third());
            result.add(new RecipeHolder<>(id, recipe));
        }
        recipes = List.copyOf(result);
    }
}
