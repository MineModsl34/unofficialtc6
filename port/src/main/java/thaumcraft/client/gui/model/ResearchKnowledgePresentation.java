package thaumcraft.client.gui.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntBiFunction;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;

/** Source positive raw balances, division/remainder and in-page/inset spacing. Read-only. */
public final class ResearchKnowledgePresentation {
    private ResearchKnowledgePresentation() {}
    public record Balance(EnumKnowledgeType type, String category, int amount, int partial, int progressPixels, int row, int column) {}
    public static List<Balance> project(List<String> categories, ToIntBiFunction<EnumKnowledgeType, String> rawKnowledge) {
        List<Balance> result = new ArrayList<>(); int row = 0;
        for (EnumKnowledgeType type : EnumKnowledgeType.values()) {
            int column = 0;
            for (String category : categories) {
                if (!type.hasFields() && category != null) continue;
                int raw = rawKnowledge.applyAsInt(type, category);
                int amount = raw / type.getProgression(), partial = raw % type.getProgression();
                if (amount <= 0 && partial <= 0) continue;
                result.add(new Balance(type, category, amount, partial, (int) ((float) partial / type.getProgression() * 16F), row, column++));
            }
            if (column > 0) row++;
        }
        return List.copyOf(result);
    }
    public static int rowCount(List<Balance> balances) { return balances.stream().mapToInt(Balance::row).max().orElse(-1) + 1; }
}
