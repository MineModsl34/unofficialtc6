package thaumcraft.client.jei;

import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ItemLike;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.client.lib.ClientResearchPresentation;

abstract class ThaumcraftRecipeCategory<R extends Recipe<?>> implements IRecipeCategory<RecipeHolder<R>> {
    protected final IGuiHelper gui;
    private final RecipeType<RecipeHolder<R>> type;
    private final Component title;
    private final IDrawable icon;

    protected ThaumcraftRecipeCategory(IGuiHelper gui, RecipeType<RecipeHolder<R>> type, ItemLike station, String key) {
        this.gui = gui;
        this.type = type;
        title = Component.translatable(key);
        icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(station));
    }

    @Override public RecipeType<RecipeHolder<R>> getRecipeType() { return type; }
    @Override public Component getTitle() { return title; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 160; }
    // At the normal automatic GUI scale a 1280x720 window has only 240 GUI pixels.
    // Leave room for JEI's tabs/navigation/border above and below this card.
    @Override public int getHeight() { return 128; }
    @Override public net.minecraft.resources.ResourceLocation getRegistryName(RecipeHolder<R> holder) {
        return JeiRecipeIdentity.logicalId(holder);
    }
    @Override public com.mojang.serialization.Codec<RecipeHolder<R>> getCodec(
        mezz.jei.api.helpers.ICodecHelper helper, mezz.jei.api.recipe.IRecipeManager manager) {
        return JeiRecipeIdentity.codec(type);
    }

    protected void output(IRecipeLayoutBuilder builder, ItemStack result, int x, int y) {
        builder.addOutputSlot(x, y).setBackground(gui.getSlotDrawable(), -1, -1).addItemStack(result.copy());
    }

    protected List<FormattedText> details(String research) {
        List<FormattedText> text = new ArrayList<>();
        if (research == null || research.isEmpty()) return text;
        // Names come from this connection's accepted catalog; no raw protocol keys in the GUI.
        var snapshot = ClientResearchPresentation.view();
        String clean = research.startsWith("~") ? research.substring(1) : research;
        int qualifier = clean.indexOf('@');
        if (qualifier >= 0) clean = clean.substring(0, qualifier);
        var entry = snapshot == null ? null : snapshot.spec().entriesByKey().get(clean);
        Component name = entry == null ? Component.literal("?") : Component.translatable(entry.name());
        text.add(Component.translatable("thaumcraft.jei.research", name));
        if (qualifier >= 0) {
            String stage = research.substring(research.indexOf('@') + 1);
            if (stage.matches("[0-9]{1,5}")) text.add(Component.translatable("thaumcraft.jei.stage", stage));
        }
        return text;
    }

    protected void aspects(List<FormattedText> text, AspectList costs) {
        if (costs == null || costs.size() == 0) return;
        text.add(Component.translatable("thaumcraft.jei.essentia"));
        for (var aspect : costs.getAspects())
            text.add(Component.literal(aspect.getName() + ": " + costs.getAmount(aspect)));
    }

    protected void detailsBox(IRecipeExtrasBuilder builder, List<FormattedText> text, int y) {
        if (!text.isEmpty()) builder.addScrollBoxWidget(156, getHeight() - y - 2, 2, y).setContents(text);
    }
}
