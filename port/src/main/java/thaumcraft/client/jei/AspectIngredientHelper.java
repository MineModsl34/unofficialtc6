package thaumcraft.client.jei;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Locale;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;

public final class AspectIngredientHelper implements IIngredientHelper<JeiAspectIngredient> {
    public static final IIngredientType<JeiAspectIngredient> TYPE = () -> JeiAspectIngredient.class;
    // JEI saves normalized ingredients for bookmarks; quantities belong to the recipe display.
    public static final Codec<JeiAspectIngredient> CODEC = Codec.STRING.xmap(JeiAspectIngredient::new, JeiAspectIngredient::tag);

    @Override public IIngredientType<JeiAspectIngredient> getIngredientType() { return TYPE; }
    @Override public String getDisplayName(JeiAspectIngredient ingredient) {
        var aspect = Aspect.getAspect(ingredient.tag());
        return aspect == null ? ingredient.tag() : aspect.getName();
    }
    @Override public String getUniqueId(JeiAspectIngredient ingredient, UidContext context) { return ingredient.lookupKey(); }
    @Override public Object getUid(JeiAspectIngredient ingredient, UidContext context) { return ingredient.lookupKey(); }
    @Override public Object getGroupingUid(JeiAspectIngredient ingredient) { return ingredient.lookupKey(); }
    @Override public ResourceLocation getResourceLocation(JeiAspectIngredient ingredient) {
        String path = ingredient.tag().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        return ResourceLocation.fromNamespaceAndPath("thaumcraft", "aspect/" + path);
    }
    @Override public String getDisplayModId(JeiAspectIngredient ingredient) { return "thaumcraft"; }
    @Override public long getAmount(JeiAspectIngredient ingredient) { return ingredient.amount(); }
    @Override public JeiAspectIngredient copyWithAmount(JeiAspectIngredient ingredient, long amount) { return ingredient.withAmount(amount); }
    @Override public JeiAspectIngredient copyIngredient(JeiAspectIngredient ingredient) { return ingredient; }
    @Override public JeiAspectIngredient normalizeIngredient(JeiAspectIngredient ingredient) { return ingredient.normalized(); }
    @Override public Iterable<Integer> getColors(JeiAspectIngredient ingredient) {
        var aspect = Aspect.getAspect(ingredient.tag());
        return aspect == null ? List.of() : List.of(aspect.getColor());
    }
    @Override public boolean isValidIngredient(JeiAspectIngredient ingredient) {
        return ingredient != null && ingredient.amount() > 0 && Aspect.getAspect(ingredient.tag()) != null;
    }
    @Override public String getErrorInfo(@Nullable JeiAspectIngredient ingredient) { return String.valueOf(ingredient); }
}
