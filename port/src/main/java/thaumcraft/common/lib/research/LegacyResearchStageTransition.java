package thaumcraft.common.lib.research;

import java.util.Arrays;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.lib.research.catalog.PreparedResearchMetadata;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec;

/** Pure TC6 stage arithmetic. Requirements and side effects are NOT paid/executed here. */
public final class LegacyResearchStageTransition {
    private LegacyResearchStageTransition() {}

    /** previousStage is the stored value AFTER the source conditional addResearch call. */
    public record Transition(int previousStage, int nextStage, boolean writesStage, boolean completed, int warp) {}

    public static Transition calculate(ResearchEntry entry, int storedStage) {
        if (entry == null) return new Transition(storedStage, storedStage, false, false, 0);
        String malformed = shapeReason(entry);
        if (malformed != null) throw new IllegalArgumentException(malformed);
        ResearchStage[] stages = entry.getStages();
        if (stages == null) return new Transition(storedStage, storedStage, false, true, 0);
        int current = storedStage;
        ResearchStage previous = null;
        if (current > 0) {
            current = Math.min(current, stages.length);
            previous = stages[current - 1];
        }
        if (stages.length == 1 && current == 0 && empty(entry, 0)) {
            current++;
        } else if (stages.length > 1 && stages.length <= current + 1 && current < stages.length && empty(entry, current)) {
            current++;
        }
        int next = Math.min(stages.length + 1, current + 1);
        boolean completed = current >= stages.length;
        int warp = previous == null ? 0 : previous.getWarp();
        // Source reassigns currentStage only on completion. Otherwise it adds the SAME
        // previous-stage warp twice; preserve Java int overflow instead of "fixing" it.
        ResearchStage last = completed ? stages[Math.min(current, stages.length) - 1] : previous;
        if (last != null) warp += last.getWarp();
        return new Transition(storedStage, next, true, completed, warp);
    }

    public static String shapeReason(ResearchEntry entry) {
        if (entry == null) return null;
        ResearchStage[] stages = entry.getStages();
        if (entry instanceof PreparedResearchMetadata.Entry prepared) {
            int rawLength = prepared.rawSpec().stages().values().size();
            if ((stages == null ? 0 : stages.length) != rawLength) return "Raw stage structure differs from mutable API stages";
            if (!prepared.rawSpec().parents().values().isEmpty()
                && !Arrays.equals(entry.getParents(), prepared.rawSpec().parents().values().toArray(String[]::new))) {
                return "Raw parent requirements differ from mutable API parents";
            }
            if (!prepared.rawSpec().siblings().values().isEmpty()
                && !Arrays.equals(entry.getSiblings(), prepared.rawSpec().siblings().values().toArray(String[]::new))) {
                return "Raw sibling transitions differ from mutable API siblings";
            }
        }
        if (stages != null) {
            if (stages.length == 0) return "Empty non-null API stage array is not a valid source transition";
            for (ResearchStage stage : stages) if (stage == null) return "Null API stage element is not a valid source transition";
        }
        return null;
    }

    /** Empty NON-null arrays are deliberately not treated as source null. */
    public static boolean empty(ResearchEntry entry, int index) {
        ResearchStage stage = entry.getStages()[index];
        return stage.getCraft() == null && stage.getObtain() == null && stage.getKnow() == null && stage.getResearch() == null
            && !rawRequirements(entry, index);
    }

    public static boolean hasRequirements(ResearchEntry entry, int index) {
        return !empty(entry, index);
    }

    private static boolean rawRequirements(ResearchEntry entry, int index) {
        ResearchCatalogSpec.Stage raw = rawStage(entry, index);
        return raw != null && (!raw.requiredItems().values().isEmpty() || !raw.requiredCraft().values().isEmpty()
            || !raw.requiredKnowledge().values().isEmpty() || !raw.requiredResearch().values().isEmpty());
    }

    static ResearchCatalogSpec.Stage rawStage(ResearchEntry entry, int index) {
        return entry instanceof PreparedResearchMetadata.Entry prepared ? prepared.rawSpec().stages().values().get(index) : null;
    }

    /** Only what THIS transition executes is gated. Future items/warp/rewards don't block a start. */
    public static String executionReason(ResearchEntry entry, Transition transition, boolean sync, boolean requirementsChecked) {
        return executionReason(entry, transition, sync, requirementsChecked, null);
    }
    public static String executionReason(ResearchEntry entry, Transition transition, boolean sync, boolean requirementsChecked,
                                         net.minecraft.core.HolderLookup.Provider registries) {
        String malformed = shapeReason(entry);
        if (malformed != null) return malformed;
        if (entry == null) return null;
        ResearchStage[] stages = entry.getStages();
        if (stages != null) {
            if (transition.previousStage() > 0) {
                int index = Math.min(transition.previousStage(), stages.length) - 1;
                if (!requirementsChecked && hasRequirements(entry, index)) return "Active stage requirements are not checked";
                if (!rawWarpMatches(entry, index)) return "Executed raw stage warp differs from mutable API warp";
            }
            if (transition.completed() && !rawWarpMatches(entry, stages.length - 1)) {
                return "Executed raw completion warp differs from mutable API warp";
            }
        }
        if (transition.completed() && sync) {
            return ResearchCompletionRewards.reason(entry, registries);
        }
        return null;
    }

    private static boolean rawWarpMatches(ResearchEntry entry, int index) {
        var raw = rawStage(entry, index);
        return raw == null || raw.warp() == entry.getStages()[index].getWarp();
    }
}
