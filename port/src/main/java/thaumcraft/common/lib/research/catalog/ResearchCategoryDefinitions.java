package thaumcraft.common.lib.research.catalog;

import java.util.LinkedHashMap;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.research.ResearchCategory;

/** Detached TC6 ConfigResearch.java:134-240 definitions; this never registers a global category. */
public final class ResearchCategoryDefinitions {
    private ResearchCategoryDefinitions() {}

    /** Every call creates seven independent categories, formulas and empty research maps. */
    public static LinkedHashMap<String, ResearchCategory> newBuiltinCategories() {
        LinkedHashMap<String, ResearchCategory> result = new LinkedHashMap<>();
        add(result, "BASICS", null, new AspectList().add(Aspect.PLANT, 5).add(Aspect.ORDER, 5)
            .add(Aspect.ENTROPY, 5).add(Aspect.AIR, 5).add(Aspect.FIRE, 5).add(Aspect.EARTH, 3).add(Aspect.WATER, 5),
            "textures/items/thaumonomicon_cheat.png", 1);
        add(result, "AUROMANCY", "UNLOCKAUROMANCY", new AspectList().add(Aspect.AURA, 20).add(Aspect.MAGIC, 20)
            .add(Aspect.FLUX, 15).add(Aspect.CRYSTAL, 5).add(Aspect.COLD, 5).add(Aspect.AIR, 5),
            "textures/research/cat_auromancy.png", 2);
        add(result, "ALCHEMY", "UNLOCKALCHEMY", new AspectList().add(Aspect.ALCHEMY, 30).add(Aspect.FLUX, 10)
            .add(Aspect.MAGIC, 10).add(Aspect.LIFE, 5).add(Aspect.AVERSION, 5).add(Aspect.DESIRE, 5).add(Aspect.WATER, 5),
            "textures/research/cat_alchemy.png", 3);
        add(result, "ARTIFICE", "UNLOCKARTIFICE", new AspectList().add(Aspect.MECHANISM, 10).add(Aspect.CRAFT, 10)
            .add(Aspect.METAL, 10).add(Aspect.TOOL, 10).add(Aspect.ENERGY, 10).add(Aspect.LIGHT, 5)
            .add(Aspect.FLIGHT, 5).add(Aspect.TRAP, 5).add(Aspect.FIRE, 5), "textures/research/cat_artifice.png", 4);
        add(result, "INFUSION", "UNLOCKINFUSION", new AspectList().add(Aspect.MAGIC, 30).add(Aspect.PROTECT, 10)
            .add(Aspect.TOOL, 10).add(Aspect.FLUX, 5).add(Aspect.CRAFT, 5).add(Aspect.SOUL, 5).add(Aspect.EARTH, 3),
            "textures/research/cat_infusion.png", 7);
        add(result, "GOLEMANCY", "UNLOCKGOLEMANCY", new AspectList().add(Aspect.MAN, 20).add(Aspect.MOTION, 10)
            .add(Aspect.MIND, 10).add(Aspect.MECHANISM, 10).add(Aspect.EXCHANGE, 5).add(Aspect.SENSES, 5)
            .add(Aspect.BEAST, 5).add(Aspect.ORDER, 5), "textures/research/cat_golemancy.png", 5);
        add(result, "ELDRITCH", "UNLOCKELDRITCH", new AspectList().add(Aspect.ELDRITCH, 20).add(Aspect.DARKNESS, 10)
            .add(Aspect.MAGIC, 5).add(Aspect.MIND, 5).add(Aspect.VOID, 5).add(Aspect.DEATH, 5)
            .add(Aspect.UNDEAD, 5).add(Aspect.ENTROPY, 5), "textures/research/cat_eldritch.png", 6);
        return result;
    }

    /** Copies definition fields only. Bounds and indexes deliberately begin at zero/empty. */
    static ResearchCategory detachedDefinition(ResearchCategory source) {
        return new ResearchCategory(source.key, source.researchKey,
            source.formula == null ? null : source.formula.copy(), source.icon, source.background, source.background2);
    }

    private static void add(LinkedHashMap<String, ResearchCategory> target, String key, String unlock,
                            AspectList formula, String icon, int background) {
        target.put(key, new ResearchCategory(key, unlock, formula, tc(icon),
            tc("textures/gui/gui_research_back_" + background + ".jpg"), tc("textures/gui/gui_research_back_over.png")));
    }

    private static ResourceLocation tc(String path) {
        return ResourceLocation.fromNamespaceAndPath("thaumcraft", path);
    }
}
