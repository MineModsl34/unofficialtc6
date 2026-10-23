package thaumcraft.api.research;

import java.util.LinkedHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.aspects.AspectList;

/** Common-side research metadata registry. Research keys are opaque, case-sensitive strings. */
public class ResearchCategories {
    // Public and reassignable, as in the TC6 API. No built-in catalog is installed by this class.
    // Volatile publishes whole reference swaps to an integrated client; nested API mutations
    // remain source-compatible and still require the owning thread.
    public static volatile LinkedHashMap<String, ResearchCategory> researchCategories = new LinkedHashMap<>();

    public static ResearchCategory getResearchCategory(String key) {
        return researchCategories.get(key);
    }

    /** Translation is deferred to the recipient instead of using client-side I18n. */
    public static Component getCategoryName(String key) {
        return Component.translatable("tc.research_category." + key);
    }

    public static ResearchEntry getResearch(String key) {
        for (ResearchCategory category : researchCategories.values()) {
            for (ResearchEntry entry : category.research.values()) {
                if (entry.key.equals(key)) {
                    return entry;
                }
            }
        }
        return null;
    }

    public static ResearchCategory registerCategory(String key, String researchKey, AspectList formula,
                                                    ResourceLocation icon, ResourceLocation background) {
        return registerCategory(key, researchKey, formula, icon, background, null);
    }

    public static ResearchCategory registerCategory(String key, String researchKey, AspectList formula,
                                                    ResourceLocation icon, ResourceLocation background,
                                                    ResourceLocation background2) {
        if (getResearchCategory(key) != null) {
            return null;
        }
        ResearchCategory category = new ResearchCategory(key, researchKey, formula, icon, background, background2);
        researchCategories.put(key, category);
        return category;
    }

    /**
     * Common insertion adapter for TC6 ResearchManager's category-registration rules.
     * Missing categories, duplicate keys and occupied display coordinates reject the new entry.
     * Bounds deliberately retain their initial zero, and mutable entries are not cloned.
     * This does not load JSON, grant research or validate stage requirements.
     */
    public static boolean registerResearch(ResearchEntry entry) {
        ResearchCategory category = getResearchCategory(entry.getCategory());
        if (category == null || category.research.containsKey(entry.getKey())) {
            return false;
        }
        for (ResearchEntry present : category.research.values()) {
            if (present.getDisplayColumn() == entry.getDisplayColumn()
                && present.getDisplayRow() == entry.getDisplayRow()) {
                return false;
            }
        }
        category.research.put(entry.getKey(), entry);
        if (entry.getDisplayColumn() < category.minDisplayColumn) {
            category.minDisplayColumn = entry.getDisplayColumn();
        }
        if (entry.getDisplayColumn() > category.maxDisplayColumn) {
            category.maxDisplayColumn = entry.getDisplayColumn();
        }
        if (entry.getDisplayRow() < category.minDisplayRow) {
            category.minDisplayRow = entry.getDisplayRow();
        }
        if (entry.getDisplayRow() > category.maxDisplayRow) {
            category.maxDisplayRow = entry.getDisplayRow();
        }
        return true;
    }
}
