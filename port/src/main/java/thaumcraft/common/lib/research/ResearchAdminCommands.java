package thaumcraft.common.lib.research;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchEntry;

/** Source admin recursion with explicit cycle/depth budgets; called only by permission2 commands.
 * No catalog mutation, player NBT reload or use by the normal browser requirement service. */
public final class ResearchAdminCommands {
    private ResearchAdminCommands() {}
    public static boolean giveRecursiveResearch(ServerPlayer owner, String key) {
        give(owner, key, new HashSet<>(), 0);
        return ThaumcraftCapabilities.getKnowledge(owner).isResearchComplete(stripStage(key));
    }
    private static void give(ServerPlayer owner, String raw, Set<String> visited, int depth) {
        if (raw == null || raw.length() > 256 || depth >= 128 || visited.size() >= 4096) return;
        String key = stripStage(raw);
        if (!visited.add(key)) return;
        var data = ThaumcraftCapabilities.getKnowledge(owner); var entry = ResearchCategories.getResearch(key);
        if (data.isResearchComplete(key)) return;
        if (entry != null && entry.getParents() != null) for (String parent : entry.getParentsStripped()) give(owner, parent, visited, depth + 1);
        if (entry != null && entry.getStages() != null) for (var stage : entry.getStages())
            if (stage != null && stage.getResearch() != null) for (String prerequisite : stage.getResearch())
                ResearchManager.completeResearchAsOperator(owner, stripStage(prerequisite), true);
        ResearchManager.completeResearchAsOperator(owner, key, true);
        if (!data.isResearchComplete(key)) return;
        for (var category : ResearchCategories.researchCategories.values()) for (var dependent : category.research.values())
            if (dependent.getStages() != null) for (var stage : dependent.getStages())
                if (stage != null && stage.getResearch() != null) for (String prerequisite : stage.getResearch())
                    if (key.equals(prerequisite)) { data.setResearchFlag(dependent.getKey(), EnumResearchFlag.PAGE); break; }
        if (entry != null && entry.getSiblings() != null) for (String sibling : entry.getSiblings()) give(owner, sibling, visited, depth + 1);
    }
    public static void revokeRecursiveResearch(ServerPlayer owner, String key) { revoke(owner, stripStage(key), new HashSet<>(), 0); }
    private static void revoke(ServerPlayer owner, String key, Set<String> visited, int depth) {
        if (key == null || depth >= 128 || visited.size() >= 4096 || !visited.add(key)) return;
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        // Source deliberately ignores known/incomplete entries, including incomplete children.
        if (!data.isResearchComplete(key)) return;
        for (var category : ResearchCategories.researchCategories.values()) for (var child : category.research.values())
            if (child.getParents() != null && data.isResearchComplete(child.getKey())) for (String parent : child.getParentsStripped())
                if (parent.equals(key)) revoke(owner, child.getKey(), visited, depth + 1);
        data.removeResearch(key);
    }
    private static String stripStage(String key) {
        if (key == null) return null;
        int separator = key.indexOf('@'); return separator < 0 ? key : key.substring(0, separator);
    }
}
