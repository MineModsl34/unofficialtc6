package thaumcraft.client.gui.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.client.gui.model.ResearchRequirementPresentation.Kind;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ArrayField;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.Entry;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemKind;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemToken;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.KnowledgeToken;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.LegacyResourceId;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ResearchToken;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.Stage;

/** Headless row/label projection only: no renderer, item registry, costs or progression grants. */
class ResearchRequirementPresentationTest {
    @Test
    void sourceGroupOrderSpritesAndDescriptorsRemainSeparateFromLabels() {
        ResearchToken research = new ResearchToken("m_jumper;sourceicon", "m_jumper", "sourceicon");
        ItemToken item = item("thaumcraft:arcane_workbench", 2);
        KnowledgeToken knowledge = new KnowledgeToken("OBSERVATION;BASICS;3", EnumKnowledgeType.OBSERVATION, "BASICS", 3);
        Stage stage = stage(field(List.of(item)), field(List.of(item)), field(List.of(knowledge)), field(List.of(research)));
        var groups = ResearchRequirementPresentation.groups(stage);
        assertEquals(List.of(Kind.RESEARCH, Kind.OBTAIN, Kind.CRAFT, Kind.KNOWLEDGE), groups.stream().map(g -> g.kind()).toList());
        assertEquals(List.of(232, 216, 200, 184), groups.stream().map(g -> g.kind().v()).toList());
        assertEquals(List.of("tc.need.research", "tc.need.obtain", "tc.need.craft", "tc.need.know"), groups.stream().map(g -> g.kind().labelKey()).toList());
        for (var group : groups) {
            assertEquals(200, group.kind().u());
            assertEquals(24, group.slots().getFirst().shift());
        }
        assertSame(research, ((ResearchRequirementPresentation.Research) groups.getFirst().slots().getFirst().requirement()).token());
        assertSame(item, ((ResearchRequirementPresentation.Item) groups.get(1).slots().getFirst().requirement()).token());
        assertSame(knowledge, ((ResearchRequirementPresentation.Knowledge) groups.getLast().slots().getFirst().requirement()).token());
        assertEquals(2, item.count(), "Presentation must not consume or alter descriptor counts");
        assertThrows(UnsupportedOperationException.class, () -> groups.clear());
        assertThrows(UnsupportedOperationException.class, () -> groups.getFirst().slots().clear());
    }

    @Test
    void upToSixIconsUseSourceEighteenPixelStep() {
        for (int count = 1; count <= 6; count++) {
            var slots = researchSlots(count);
            for (int index = 0; index < count; index++) assertEquals(24 + index * 18, slots.get(index).shift());
        }
    }

    @Test
    void moreThanSixUsesSourceIntegerCompressionIncludingZeroStep() {
        for (int count : new int[] {7, 10, 110, 111, 250}) {
            var slots = researchSlots(count);
            assertEquals(count, slots.size());
            int sourceStep = 110 / count;
            for (int index = 0; index < count; index++) assertEquals(24 + index * sourceStep, slots.get(index).shift());
        }
    }

    @Test
    void compressedHoverSelectsOnlyLastMatchingSlotLikeSourceTipTextAssignment() {
        var slots = researchSlots(10); // Canonical ESSENTIASMELTER has ten obtain requirements.
        var group = new ResearchRequirementPresentation.Group(Kind.OBTAIN, slots);
        assertNull(group.hoveredSlot(23));
        assertSame(slots.get(0), group.hoveredSlot(24));
        assertSame(slots.get(1), group.hoveredSlot(35)); // Both first and second 16px icons overlap.
        assertSame(slots.get(1), group.hoveredSlot(40));
        assertSame(slots.get(9), group.hoveredSlot(123));
        assertNull(group.hoveredSlot(139));
        var zeroStep = researchSlots(111);
        assertSame(zeroStep.getLast(), new ResearchRequirementPresentation.Group(Kind.RESEARCH, zeroStep).hoveredSlot(24));
    }

    @Test
    void absentEmptyAndNullStageDoNotInventRequirementRows() {
        assertEquals(List.of(), ResearchRequirementPresentation.groups(null));
        assertEquals(List.of(), ResearchRequirementPresentation.groups(stage(ArrayField.absent(), ArrayField.absent(), ArrayField.absent(), ArrayField.absent())));
        assertEquals(List.of(), ResearchRequirementPresentation.groups(stage(field(List.of()), field(List.of()), field(List.of()), field(List.of()))));
    }

    @Test
    void sourceEntryNamePrecedesMarkerAndUnlocalizedKeysNeverBecomeVisibleIds() {
        ResearchToken token = new ResearchToken("m_jumper;opaque/icon.png", "m_jumper", "opaque/icon.png");
        Entry entry = entry("m_jumper", "research.source.name");
        Set<String> translated = Set.of("research.source.name", "research.m_jumper.text");
        assertEquals("research.source.name", ResearchRequirementPresentation.researchLabel(token, Map.of(entry.key(), entry), translated::contains));
        assertEquals("research.m_jumper.text", ResearchRequirementPresentation.researchLabel(token, Map.of(), translated::contains));
        assertEquals(ResearchRequirementPresentation.UNAVAILABLE, ResearchRequirementPresentation.researchLabel(token, Map.of(), ignored -> false));
        assertEquals(ResearchRequirementPresentation.UNAVAILABLE, ResearchRequirementPresentation.researchLabel(null, Map.of(), ignored -> true));
        Entry rawName = entry("m_jumper", "thaumcraft:arcane_workbench");
        assertEquals(ResearchRequirementPresentation.UNAVAILABLE, ResearchRequirementPresentation.researchLabel(token, Map.of(rawName.key(), rawName), ignored -> false));
    }

    private static List<ResearchRequirementPresentation.Slot> researchSlots(int count) {
        List<ResearchToken> tokens = new ArrayList<>();
        for (int i = 0; i < count; i++) tokens.add(new ResearchToken("RAW" + i, "RAW" + i, null));
        return ResearchRequirementPresentation.groups(stage(ArrayField.absent(), ArrayField.absent(), ArrayField.absent(), field(tokens))).getFirst().slots();
    }
    private static ItemToken item(String id, int count) {
        String[] parts = id.split(":", 2);
        return new ItemToken(id, id, ItemKind.ITEM, id, count, 0, null, null, List.of(),
            new LegacyResourceId(id, parts[0], parts[1], true), "Unresolved source descriptor");
    }
    private static <T> ArrayField<T> field(List<T> values) { return new ArrayField<>(true, values); }
    private static Stage stage(ArrayField<ItemToken> items, ArrayField<ItemToken> craft,
                               ArrayField<KnowledgeToken> knowledge, ArrayField<ResearchToken> research) {
        return new Stage("source.text", ArrayField.absent(), items, craft, knowledge, research, false, 0, Map.of());
    }
    private static Entry entry(String key, String name) {
        return new Entry("source", "$.entries[0]", key, name, "BASICS", ArrayField.absent(),
            ArrayField.absent(), ArrayField.absent(), ArrayField.absent(), ArrayField.absent(),
            ArrayField.absent(), ArrayField.absent(), ArrayField.absent(), ArrayField.absent(), Map.of());
    }
}
