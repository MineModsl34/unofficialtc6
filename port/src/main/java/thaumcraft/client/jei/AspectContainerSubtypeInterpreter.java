package thaumcraft.client.jei;

import java.util.LinkedHashMap;
import java.util.Map;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.items.TCDataComponents;

final class AspectContainerSubtypeInterpreter implements ISubtypeInterpreter<ItemStack> {
    @Override public Object getSubtypeData(ItemStack stack, UidContext context) {
        Map<String, Integer> amounts = new LinkedHashMap<>();
        var aspects = TCDataComponents.ASPECTS == null ? null : stack.get(TCDataComponents.ASPECTS);
        if (aspects != null) for (var aspect : aspects.getAspects())
            if (aspect != null) amounts.put(aspect.getTag(), aspects.getAmount(aspect));
        String filter = TCDataComponents.ASPECT_FILTER == null ? "" : stack.getOrDefault(TCDataComponents.ASPECT_FILTER, "");
        var subtype = AspectContainerSubtype.of(amounts, filter);
        return subtype.isEmpty() ? null : subtype;
    }
    @Override public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) { return ""; }
}
