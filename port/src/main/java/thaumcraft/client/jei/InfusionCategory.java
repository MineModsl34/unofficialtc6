package thaumcraft.client.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.NumericTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.lib.crafting.InfusionEnchantmentRecipe;
import thaumcraft.common.lib.crafting.InfusionRunicAugmentRecipe;

final class InfusionCategory extends ThaumcraftRecipeCategory<InfusionRecipe> {
    private static final int COMPONENT_COLUMNS = 7, COMPONENT_TOP = 28, DETAILS_GAP = 10;
    InfusionCategory(IGuiHelper gui) {
        super(gui, ThaumcraftJeiPlugin.INFUSION, BlocksTC.infusionMatrix, "thaumcraft.jei.infusion");
    }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<InfusionRecipe> holder, IFocusGroup focuses) {
        var recipe = holder.value();
        DisplayView view = view(recipe, focuses);
        var input = builder.addInputSlot(24, 4).setSlotName("central").setBackground(gui.getSlotDrawable(), -1, -1);
        if (recipe instanceof InfusionDisplayRecipe display) {
            input.addItemStacks(view.inputs());
            var output = builder.addOutputSlot(120, 4).setBackground(gui.getSlotDrawable(), -1, -1).addItemStacks(view.outputs());
            builder.createFocusLink(input, output);
            Component modification = modification(display);
            if (modification != null) output.addRichTooltipCallback((slot, tooltip) -> tooltip.add(modification));
        } else {
            input.addIngredients(recipe.getRecipeInput());
            output(builder, (ItemStack) recipe.getRecipeOutput(), 120, 4);
        }
        // Repeated components remain separate slots and all components participate in lookup.
        // JEI's scroll grid handles large add-on recipes without clipping or silent truncation.
        int index = 0;
        for (var ingredient : view.components()) builder.addSlot(RecipeIngredientRole.INPUT)
            .setSlotName("component_" + index++).addIngredients(ingredient);
        // Counted text stays in the scroll box; custom ingredient identity indexes U-lookups
        // by aspect independently of quantity, including focused dynamic upgrade costs.
        for (var aspect : view.costs().getAspects()) {
            int amount = view.costs().getAmount(aspect);
            if (amount > 0) builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                .addIngredient(AspectIngredientHelper.TYPE, new JeiAspectIngredient(aspect.getTag(), amount));
        }
    }

    @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<InfusionRecipe> holder, IFocusGroup focuses) {
        builder.addRecipeArrow().setPosition(70, 4);
        var components = builder.getRecipeSlots().getSlots().stream()
            .filter(slot -> slot.getSlotName().orElse("").startsWith("component_")).toList();
        int rows = Math.min(2, (components.size() + COMPONENT_COLUMNS - 1) / COMPONENT_COLUMNS);
        if (rows > 0) builder.addScrollGridWidget(components, COMPONENT_COLUMNS, rows).setPosition(4, COMPONENT_TOP);
        // The shorter one-row grid leaves the dynamic upgrade and its exact costs visible.
        // Extra components still use the native grid's scrollbar and all remain indexed.
        var text = new java.util.ArrayList<net.minecraft.network.chat.FormattedText>();
        var notes = new java.util.ArrayList<net.minecraft.network.chat.FormattedText>();
        DisplayView view = view(holder.value(), focuses);
        if (holder.value() instanceof InfusionDisplayRecipe display) {
            if (display.source() instanceof InfusionEnchantmentRecipe enchantment) {
                text.add(Component.translatable("thaumcraft.jei.infusion_enchantment",
                    Component.translatable("enchantment.infusion." + enchantment.enchantment.name()), display.level(), enchantment.enchantment.maxLevel));
                notes.add(Component.translatable("thaumcraft.jei.enchantment_cost_note"));
                notes.add(Component.translatable("thaumcraft.jei.enchantment_warp_note"));
            } else if (display.kind() == InfusionDisplayRecipe.Kind.RUNIC) {
                text.add(Component.translatable("thaumcraft.jei.runic_upgrade", display.level() - 1, display.level()));
            } else {
                Component modification = modification(display);
                if (modification != null) text.add(modification);
                notes.add(Component.translatable("thaumcraft.jei.preserves_central"));
            }
        }
        text.add(Component.translatable("thaumcraft.jei.instability", view.instability()));
        aspects(text, view.costs());
        text.addAll(details(holder.value().getResearch())); text.addAll(notes);
        int detailsTop = rows == 0 ? COMPONENT_TOP : COMPONENT_TOP + rows * 18 + DETAILS_GAP;
        detailsBox(builder, text, detailsTop);
    }

    private record DisplayView(List<ItemStack> inputs, List<ItemStack> outputs, List<Ingredient> components,
            AspectList costs, int instability) {}

    private static DisplayView view(InfusionRecipe recipe, IFocusGroup focuses) {
        if (!(recipe instanceof InfusionDisplayRecipe display))
            return new DisplayView(List.of(), List.of(), recipe.getComponents(), recipe.getAspects(), recipe.instability);
        ItemStack focused = focuses.getItemStackFocuses(RecipeIngredientRole.INPUT)
            .map(focus -> focus.getTypedValue().getIngredient())
            .filter(stack -> InfusionDisplayRecipes.matchesFocusedInput(display, stack)).findFirst().orElse(ItemStack.EMPTY);
        var level = Minecraft.getInstance().level;
        if (!focused.isEmpty() && level != null) {
            ItemStack input = focused.copyWithCount(1);
            ItemStack result = InfusionDisplayRecipes.focusedOutput(display, input, level.registryAccess());
            if (!result.isEmpty()) {
                var components = display.source() instanceof InfusionRunicAugmentRecipe runic
                    ? runic.getComponents(input) : display.source().getComponents();
                return new DisplayView(List.of(input), List.of(result), components,
                    display.source().getAspects(null, input, List.of()), display.source().getInstability(null, input, List.of()));
            }
        }
        return new DisplayView(display.inputs(), display.outputs(), display.getComponents(), display.getAspects(), display.instability);
    }

    private static Component modification(InfusionDisplayRecipe display) {
        if (display.kind() != InfusionDisplayRecipe.Kind.TAG) return null;
        Object[] tag = (Object[])display.source().getRecipeOutput();
        Component name = null;
        ItemStack central = display.firstInput();
        if (central.is(ItemsTC.fortressHelm) && tag[0].equals("goggles")) name = Component.translatable("item.thaumcraft.goggles");
        if (central.is(ItemsTC.fortressHelm) && tag[0].equals("mask") && tag[1] instanceof NumericTag mask && mask.getAsInt() >= 0 && mask.getAsInt() <= 2)
            name = Component.translatable("item.fortress_helm.mask." + mask.getAsInt());
        if (tag[0].equals("type") && central.is(ItemsTC.charmVerdant) && tag[1] instanceof NumericTag type) {
            if (type.getAsInt() == 1) name = Component.translatable("item.verdant_charm.life.text");
            if (type.getAsInt() == 2) name = Component.translatable("item.verdant_charm.sustain.text");
        }
        return name == null ? Component.translatable("thaumcraft.jei.item_modification")
            : Component.translatable("thaumcraft.jei.modification", name);
    }
}
