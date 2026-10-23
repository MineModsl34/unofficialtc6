package thaumcraft.client.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.common.lib.crafting.InfusionEnchantmentRecipe;
import thaumcraft.common.lib.crafting.InfusionRecipes;
import thaumcraft.common.lib.crafting.InfusionRunicAugmentRecipe;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/** Build projections from the accepted server catalog and registered ingredients, never source fake recipes. */
public final class InfusionDisplayRecipes {
    private InfusionDisplayRecipes() {}

    public static ResourceLocation sourceId(RecipeHolder<InfusionRecipe> holder) {
        return holder.value() instanceof InfusionDisplayRecipe display ? display.sourceId() : holder.id();
    }

    public static InfusionRecipe sourceRecipe(RecipeHolder<InfusionRecipe> holder) {
        return holder.value() instanceof InfusionDisplayRecipe display ? display.source() : holder.value();
    }

    /** Enchantment uses the API helper's &&/|| expression semantics; other infusion gates are literal. */
    public static boolean researchUsesExpressions(RecipeHolder<InfusionRecipe> holder) {
        return sourceRecipe(holder) instanceof InfusionEnchantmentRecipe;
    }

    public static List<RecipeHolder<InfusionRecipe>> create(List<RecipeHolder<InfusionRecipe>> recipes,
            List<ItemStack> candidates, HolderLookup.Provider lookup) {
        List<RecipeHolder<InfusionRecipe>> result = new ArrayList<>();
        // Preserve the registered ingredient's components: elemental tools already contain
        // source enchantments, and add-on equipment may depend on its data for acceptance.
        List<ItemStack> registered = candidates.stream().filter(stack -> !stack.isEmpty()).map(stack -> stack.copyWithCount(1)).toList();
        for (var holder : recipes) {
            InfusionRecipe recipe = holder.value();
            if (recipe instanceof InfusionEnchantmentRecipe enchantment) {
                if (TCDataComponents.INFUSION_ENCHANTMENTS == null) continue;
                List<ItemStack> accepted = registered.stream()
                    .filter(stack -> InfusionEnchantmentRecipe.accepts(enchantment.enchantment, stack))
                    .filter(stack -> centralMatches(recipe, stack)).toList();
                for (int level : InfusionDisplayStages.enchantmentLevels(enchantment.enchantment.maxLevel)) {
                    Map<Integer, List<ItemStack>> groups = new LinkedHashMap<>();
                    for (ItemStack stack : accepted) {
                        int current = EnumInfusionEnchantment.getInfusionEnchantmentLevel(stack, enchantment.enchantment);
                        if (current < 0 || current >= level) continue;
                        ItemStack input = enchantmentInput(stack, enchantment.enchantment, level);
                        if (!centralMatches(recipe, input)) continue;
                        List<EnumInfusionEnchantment> existing = EnumInfusionEnchantment.getInfusionEnchantments(input);
                        int others = existing.size() - (existing.contains(enchantment.enchantment) ? 1 : 0);
                        groups.computeIfAbsent(others, ignored -> new ArrayList<>()).add(input);
                    }
                    for (var group : groups.entrySet()) {
                        List<ItemStack> inputs = group.getValue();
                        List<ItemStack> outputs = inputs.stream().map(stack -> enchantmentOutput(stack, enchantment.enchantment)).toList();
                        result.add(display(holder, InfusionDisplayRecipe.Kind.ENCHANTMENT, level, group.getKey(), inputs, outputs,
                            recipe.getComponents(), recipe.getAspects(null, inputs.getFirst(), List.of()), recipe.instability));
                    }
                }
            } else if (recipe instanceof InfusionRunicAugmentRecipe runic) {
                List<ItemStack> accepted = registered.stream().filter(InfusionDisplayRecipes::acceptsRunic)
                    .filter(stack -> centralMatches(recipe, stack)).toList();
                for (int charge : InfusionDisplayStages.runicCharges()) {
                    List<ItemStack> inputs = accepted.stream().filter(stack -> {
                        int current = InfusionRunicAugmentRecipe.getRunicCharge(stack);
                        return current >= 0 && current <= charge;
                    }).map(stack -> runicInput(stack, charge)).filter(stack -> centralMatches(recipe, stack)).toList();
                    List<ItemStack> outputs = inputs.stream().map(stack -> (ItemStack)runic.getRecipeOutput(null, stack, List.of())).toList();
                    if (!inputs.isEmpty()) result.add(display(holder, InfusionDisplayRecipe.Kind.RUNIC, charge + 1, 0, inputs, outputs,
                        runic.getComponents(inputs.getFirst()), runic.getAspects(null, inputs.getFirst(), List.of()),
                        runic.getInstability(null, inputs.getFirst(), List.of())));
                }
            } else if (recipe.getRecipeOutput() instanceof Object[] tag && tag.length == 2) {
                List<ItemStack> inputs = new ArrayList<>(), outputs = new ArrayList<>();
                // Ingredient#getItems retains real component-sensitive source ingredient representatives.
                List<ItemStack> centralCandidates = recipe.getRecipeInput() == Ingredient.EMPTY ? registered
                    : java.util.Arrays.asList(recipe.getRecipeInput().getItems());
                for (ItemStack central : centralCandidates) {
                    if (central.isEmpty() || !centralMatches(recipe, central)) continue;
                    ItemStack input = central.copyWithCount(1), output = input.copy();
                    if (InfusionRecipes.applyOutputTag(output, (String)tag[0], (Tag)tag[1], lookup)) {
                        inputs.add(input); outputs.add(output);
                    }
                }
                if (!inputs.isEmpty()) result.add(display(holder, InfusionDisplayRecipe.Kind.TAG, 0, 0, inputs, outputs,
                    recipe.getComponents(), recipe.getAspects(), recipe.instability));
            } else if (recipe.getRecipeOutput() instanceof ItemStack output && !output.isEmpty()) {
                result.add(holder);
            }
        }
        return List.copyOf(result);
    }

    private static RecipeHolder<InfusionRecipe> display(RecipeHolder<InfusionRecipe> holder, InfusionDisplayRecipe.Kind kind,
            int level, int costGroup, List<ItemStack> inputs, List<ItemStack> outputs, List<Ingredient> components,
            thaumcraft.api.aspects.AspectList costs, int instability) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("thaumcraft", "jei/infusion/" + holder.id().getNamespace()
            + "/" + holder.id().getPath() + "/" + kind.name().toLowerCase(java.util.Locale.ROOT) + "/" + level + "/" + costGroup);
        return new RecipeHolder<>(id, new InfusionDisplayRecipe(holder.id(), holder.value(), kind, level, inputs, outputs, components, costs, instability));
    }

    private static boolean centralMatches(InfusionRecipe recipe, ItemStack stack) {
        return recipe.getRecipeInput() == Ingredient.EMPTY || recipe.getRecipeInput().test(stack);
    }
    private static boolean acceptsRunic(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem || stack.getItem() instanceof ICurioItem;
    }
    private static ItemStack enchantmentInput(ItemStack stack, EnumInfusionEnchantment enchantment, int nextLevel) {
        ItemStack input = stack.copyWithCount(1);
        if (nextLevel > 1) EnumInfusionEnchantment.addInfusionEnchantment(input, enchantment, nextLevel - 1);
        return input;
    }
    static ItemStack enchantmentOutput(ItemStack input, EnumInfusionEnchantment enchantment) {
        ItemStack output = input.copyWithCount(1);
        EnumInfusionEnchantment.addInfusionEnchantment(output, enchantment,
            EnumInfusionEnchantment.getInfusionEnchantmentLevel(input, enchantment) + 1);
        // Source may add warp randomly; a recipe preview must never roll the random side effect.
        return output;
    }
    private static ItemStack runicInput(ItemStack stack, int charge) {
        ItemStack input = stack.copyWithCount(1);
        if (charge > 0) {
            CustomData previous = input.get(DataComponents.CUSTOM_DATA);
            CompoundTag tag = previous == null ? new CompoundTag() : previous.copyTag();
            tag.putByte("TC.RUNIC", (byte)charge);
            input.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return input;
    }

    /** Preserve an actual focused player's stack when it is valid for this displayed upgrade. */
    static boolean matchesFocusedInput(InfusionDisplayRecipe display, ItemStack input) {
        if (input.isEmpty() || !centralMatches(display.source(), input)) return false;
        if (display.source() instanceof InfusionEnchantmentRecipe enchantment) {
            return InfusionEnchantmentRecipe.accepts(enchantment.enchantment, input)
                && EnumInfusionEnchantment.getInfusionEnchantmentLevel(input, enchantment.enchantment) == display.level() - 1;
        }
        if (display.source() instanceof InfusionRunicAugmentRecipe) {
            return acceptsRunic(input) && InfusionRunicAugmentRecipe.getRunicCharge(input) == display.level() - 1;
        }
        return display.kind() == InfusionDisplayRecipe.Kind.TAG;
    }

    static ItemStack focusedOutput(InfusionDisplayRecipe display, ItemStack input, HolderLookup.Provider lookup) {
        if (!matchesFocusedInput(display, input)) return ItemStack.EMPTY;
        if (display.source() instanceof InfusionEnchantmentRecipe enchantment) return enchantmentOutput(input, enchantment.enchantment);
        if (display.source() instanceof InfusionRunicAugmentRecipe runic)
            return (ItemStack)runic.getRecipeOutput(null, input.copyWithCount(1), List.of());
        Object[] tag = (Object[])display.source().getRecipeOutput();
        ItemStack output = input.copyWithCount(1);
        return InfusionRecipes.applyOutputTag(output, (String)tag[0], (Tag)tag[1], lookup) ? output : ItemStack.EMPTY;
    }
}
