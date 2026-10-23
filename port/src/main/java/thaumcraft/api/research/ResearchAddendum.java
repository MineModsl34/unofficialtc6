package thaumcraft.api.research;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Common-side addendum metadata with TC6's nullable, directly mutable arrays. */
public class ResearchAddendum {
    String text;
    ResourceLocation[] recipes;
    String[] research;

    public String getText() {
        return text;
    }

    public Component getTextLocalized() {
        return Component.translatable(getText());
    }

    public void setText(String text) {
        this.text = text;
    }

    public ResourceLocation[] getRecipes() {
        return recipes;
    }

    public void setRecipes(ResourceLocation[] recipes) {
        this.recipes = recipes;
    }

    public String[] getResearch() {
        return research;
    }

    public void setResearch(String[] research) {
        this.research = research;
    }
}
