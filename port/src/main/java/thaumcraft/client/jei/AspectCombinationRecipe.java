package thaumcraft.client.jei;

import java.util.Objects;
import thaumcraft.api.aspects.Aspect;

/** Actual registered compound relationship; never a manufactured gameplay crafting recipe. */
public record AspectCombinationRecipe(String first, String second, String result) {
    public AspectCombinationRecipe {
        Objects.requireNonNull(first);
        Objects.requireNonNull(second);
        Objects.requireNonNull(result);
    }

    public static AspectCombinationRecipe from(Aspect aspect) {
        if (aspect == null || aspect.isPrimal()) return null;
        var components = aspect.getComponents();
        if (components[0] == null || components[1] == null) return null;
        return new AspectCombinationRecipe(components[0].getTag(), components[1].getTag(), aspect.getTag());
    }
}
