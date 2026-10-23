package thaumcraft.client.jei;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import thaumcraft.api.aspects.Aspect;

class AspectCombinationRecipeTest {
    @Test void sourceCompoundsRetainTheirExactTwoIngredients() {
        assertEquals(new AspectCombinationRecipe("aer", "ignis", "lux"), AspectCombinationRecipe.from(Aspect.LIGHT));
        assertEquals(new AspectCombinationRecipe("potentia", "aer", "praecantatio"), AspectCombinationRecipe.from(Aspect.MAGIC));
        assertEquals(new AspectCombinationRecipe("ignis", "spiritus", "cognitio"), AspectCombinationRecipe.from(Aspect.MIND));
    }
    @Test void primalsAndMissingDefinitionsHaveNoInventedCombination() {
        assertNull(AspectCombinationRecipe.from(null));
        assertNull(AspectCombinationRecipe.from(Aspect.AIR));
        assertNull(AspectCombinationRecipe.from(Aspect.ENTROPY));
    }
    @Test void everyRegisteredCompoundProducesItsOwnResult() {
        for (var aspect : Aspect.aspects.values()) {
            var recipe = AspectCombinationRecipe.from(aspect);
            if (aspect.isPrimal()) assertNull(recipe);
            else {
                assertNotNull(recipe);
                assertEquals(aspect.getTag(), recipe.result());
                assertNotNull(Aspect.getAspect(recipe.first()));
                assertNotNull(Aspect.getAspect(recipe.second()));
            }
        }
    }
}
