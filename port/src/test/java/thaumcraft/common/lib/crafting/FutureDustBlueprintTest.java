package thaumcraft.common.lib.crafting;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import thaumcraft.api.crafting.Part;

/** Pure Part geometry/binding evidence; no future engine blocks/BE/runtime created. */
class FutureDustBlueprintTest {
    @Test void infernalFurnaceHasExactlyTwelveOfEachSolidOneBarsRemovalOneOppositeLavaTarget() {
        var parts = FutureDustTriggers.infernalBlueprint("NB", "NBproxy", "OB", "OBproxy", "bars", "lava", "furnace");
        assertEquals(3, parts.length); assertEquals(3, parts[0].length); assertEquals(3, parts[0][0].length);
        assertEquals(26, cells(parts).count()); assertEquals(12, cells(parts).filter(part -> "NB".equals(part.getSource())).count());
        assertEquals(12, cells(parts).filter(part -> "OB".equals(part.getSource())).count());
        assertNull(parts[0][1][1]); assertEquals("lava", parts[1][1][1].getSource()); assertEquals("furnace", parts[1][1][1].getTarget());
        assertTrue(parts[1][1][1].isOpp()); assertFalse(parts[1][1][1].getApplyPlayerFacing());
        assertEquals("bars", parts[1][2][1].getSource()); assertEquals("AIR", parts[1][2][1].getTarget());
        cells(parts).forEach(part -> assertEquals(50, part.getPriority()));
    }
    @Test void thaumatoriumRequiresTwoExactMetalStatesAndLeavesTheBottomCrucibleIntact() {
        var parts = FutureDustTriggers.thaumatoriumBlueprint("metal.defaultState", "top", "bottom", "crucible.block");
        assertEquals(3, parts.length); assertEquals(3, cells(parts).count());
        assertEquals("metal.defaultState", parts[0][0][0].getSource()); assertEquals("top", parts[0][0][0].getTarget());
        assertEquals("bottom", parts[1][0][0].getTarget()); assertEquals("crucible.block", parts[2][0][0].getSource());
        assertTrue(parts[0][0][0].getApplyPlayerFacing()); assertTrue(parts[1][0][0].getApplyPlayerFacing());
        assertNull(parts[2][0][0].getTarget()); cells(parts).forEach(part -> assertEquals(50, part.getPriority()));
    }
    @Test void golemPressHasFiveExactCellsAndAnUpwardPistonNotAFacingFreeWildcard() {
        var parts = FutureDustTriggers.golemBlueprint("bars", "barsProxy", "cauldronFamily", "cauldronProxy", "piston.up.exact", "builder", "anvilFamily", "anvilProxy", "table", "tableProxy");
        assertEquals(2, parts.length); assertEquals(2, parts[0].length); assertEquals(2, parts[0][0].length);
        assertEquals(5, cells(parts).count()); assertEquals("bars", parts[0][1][0].getSource());
        assertEquals("cauldronFamily", parts[1][0][0].getSource()); assertEquals("anvilFamily", parts[1][0][1].getSource());
        assertEquals("piston.up.exact", parts[1][1][0].getSource()); assertEquals("builder", parts[1][1][0].getTarget());
        assertEquals("table", parts[1][1][1].getSource()); assertEquals("tableProxy", parts[1][1][1].getTarget());
        cells(parts).forEach(part -> { assertEquals(50, part.getPriority()); assertFalse(part.isOpp() || part.getApplyPlayerFacing()); });
    }
    @Test void definitionsRetainSourceIdsResearchMaterialsDisplayAndNamedDependencies() {
        var definitions = FutureDustTriggers.definitions(); assertEquals(3, definitions.size());
        assertEquals(List.of("thaumcraft:infernalfurnace", "thaumcraft:thaumatorium", "thaumcraft:golempress"), definitions.stream().map(FutureDustTriggers.Definition::id).toList());
        assertEquals(List.of("INFERNALFURNACE", "THAUMATORIUM", "MINDCLOCKWORK"), definitions.stream().map(FutureDustTriggers.Definition::research).toList());
        assertFalse(definitions.get(0).explicitDisplay()); assertFalse(definitions.get(1).explicitDisplay()); assertTrue(definitions.get(2).explicitDisplay());
        assertEquals(26, definitions.get(0).materials().stream().mapToInt(FutureDustTriggers.Material::count).sum());
        assertEquals(new FutureDustTriggers.Material("minecraft:lava_bucket", 1), definitions.get(0).materials().getLast());
        assertTrue(definitions.get(0).requiredBindings().contains("placeholderNetherbrick=thaumcraft:placeholder_brick"));
        assertEquals(List.of(new FutureDustTriggers.Material("thaumcraft:metal_alchemical", 2), new FutureDustTriggers.Material("thaumcraft:crucible", 1)), definitions.get(1).materials());
        assertEquals(5, definitions.get(2).materials().size());
    }
    @Test void anyMissingOrRejectedBindingSkipsTheWholeDefinitionRatherThanCreatingEmptyParts() {
        List<String> dependencies = List.of("actualMachineBE", "actualProxyBlock", "actualProxyItem");
        assertTrue(FutureDustTriggers.allBindingsReady(dependencies, dependencies::contains));
        for (String absent : dependencies) assertFalse(FutureDustTriggers.allBindingsReady(dependencies, binding -> !binding.equals(absent)));
        for (var definition : FutureDustTriggers.definitions()) assertFalse(FutureDustTriggers.allBindingsReady(definition.requiredBindings(), ignored -> false));
    }
    private static Stream<Part> cells(Part[][][] parts) {
        return java.util.Arrays.stream(parts).flatMap(java.util.Arrays::stream).flatMap(java.util.Arrays::stream).filter(java.util.Objects::nonNull);
    }
}
