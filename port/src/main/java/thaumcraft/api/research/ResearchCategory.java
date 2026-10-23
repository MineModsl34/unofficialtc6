package thaumcraft.api.research;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

/** Mutable category metadata retaining the original public API fields. */
public class ResearchCategory {
    public int minDisplayColumn;
    public int minDisplayRow;
    public int maxDisplayColumn;
    public int maxDisplayRow;
    public ResourceLocation icon;
    public ResourceLocation background;
    public ResourceLocation background2;
    public String researchKey;
    public String key;
    public AspectList formula;
    public Map<String, ResearchEntry> research = new HashMap<>();

    public ResearchCategory(String key, String researchKey, AspectList formula,
                            ResourceLocation icon, ResourceLocation background) {
        this(key, researchKey, formula, icon, background, null);
    }

    public ResearchCategory(String key, String researchKey, AspectList formula, ResourceLocation icon,
                            ResourceLocation background, ResourceLocation background2) {
        this.key = key;
        this.researchKey = researchKey;
        this.formula = formula;
        this.icon = icon;
        this.background = background;
        this.background2 = background2;
    }

    public int applyFormula(AspectList aspects) {
        return applyFormula(aspects, 1.0);
    }

    public int applyFormula(AspectList aspects, double modifier) {
        if (formula == null) {
            return 0;
        }
        double total = 0.0;
        for (Aspect aspect : formula.getAspects()) {
            total += modifier * modifier * aspects.getAmount(aspect) * (formula.getAmount(aspect) / 10.0);
        }
        if (total > 0.0) {
            total = Math.sqrt(total);
        }
        return Mth.ceil(total);
    }
}
