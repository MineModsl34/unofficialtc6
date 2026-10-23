package thaumcraft.client.gui.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemToken;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.KnowledgeToken;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.Stage;

/** Observational source GuiResearchPage.checkRequisites. Checked icons mean current inventory
 * or RECEIVED knowledge satisfies a requirement, never paid or completed optimistically.
 * Craft references must be exact legacy hashes supplied by the common source resolver.
 */
public record ResearchRequirementStatus(List<Boolean> research, List<Boolean> obtain,
                                        List<Boolean> craft, List<Boolean> knowledge, boolean all) {
    public ResearchRequirementStatus {
        research = List.copyOf(research); obtain = List.copyOf(obtain);
        craft = List.copyOf(craft); knowledge = List.copyOf(knowledge);
    }
    public static ResearchRequirementStatus empty() { return new ResearchRequirementStatus(List.of(), List.of(), List.of(), List.of(), false); }
    public static ResearchRequirementStatus evaluate(Stage stage, Predicate<String> knowsStrict,
             Predicate<String> knowsMarker, Predicate<ItemToken> carries,
             Function<ItemToken, Integer> craftReference, ToIntFunction<KnowledgeToken> knownAmount) {
        if (stage == null) return empty();
        List<Boolean> research = new ArrayList<>(), obtain = new ArrayList<>(), craft = new ArrayList<>(), knowledge = new ArrayList<>();
        if (stage.requiredResearch() != null) for (var token : stage.requiredResearch().values()) research.add(knowsStrict.test(token.key()));
        if (stage.requiredItems() != null) for (var token : stage.requiredItems().values()) obtain.add(carries.test(token));
        if (stage.requiredCraft() != null) for (var token : stage.requiredCraft().values()) {
            Integer reference = craftReference.apply(token);
            craft.add(reference != null && knowsMarker.test("[#]" + reference));
        }
        if (stage.requiredKnowledge() != null) for (var token : stage.requiredKnowledge().values())
            knowledge.add(token.type() != null && knownAmount.applyAsInt(token) >= token.amount());
        boolean all = research.stream().allMatch(Boolean::booleanValue) && obtain.stream().allMatch(Boolean::booleanValue)
            && craft.stream().allMatch(Boolean::booleanValue) && knowledge.stream().allMatch(Boolean::booleanValue);
        return new ResearchRequirementStatus(research, obtain, craft, knowledge, all);
    }
    public boolean satisfied(ResearchRequirementPresentation.Kind kind, int index) {
        List<Boolean> values = switch (kind) { case RESEARCH -> research; case OBTAIN -> obtain; case CRAFT -> craft; case KNOWLEDGE -> knowledge; };
        return index >= 0 && index < values.size() && values.get(index);
    }
}
