package thaumcraft.common.lib.crafting;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import org.junit.jupiter.api.Test;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

class CrucibleAspectCostsTest {
    @Test void matchingRequiresEveryCostButAcceptsExcessAndUnrelatedAspects() {
        AspectList costs = new AspectList().add(Aspect.ENERGY, 10).add(Aspect.FIRE, 10).add(Aspect.LIGHT, 10);
        AspectList available = new AspectList().add(Aspect.ENERGY, 11).add(Aspect.FIRE, 10).add(Aspect.LIGHT, 10).add(Aspect.WATER, 9);
        assertTrue(CrucibleAspectCosts.matches(costs, available));
        assertFalse(CrucibleAspectCosts.matches(costs, null));
        for (Aspect aspect : costs.getAspects()) {
            AspectList missing = available.copy(); missing.aspects.put(aspect, 9);
            assertFalse(CrucibleAspectCosts.matches(costs, missing));
        }
        assertEquals(40, available.visSize());
    }

    @Test void subtractionIsFreshAndRetainsSourceUnrelatedZeroEntries() {
        AspectList costs = new AspectList().add(Aspect.ENERGY, 10).add(Aspect.FIRE, 10).add(Aspect.LIGHT, 10);
        AspectList available = new AspectList().add(Aspect.ENERGY, 11).add(Aspect.FIRE, 10).add(Aspect.LIGHT, 10).add(Aspect.WATER, 9);
        available.aspects.put(Aspect.EARTH, 0);
        AspectList remaining = CrucibleAspectCosts.subtract(costs, available);
        assertEquals(1, remaining.getAmount(Aspect.ENERGY)); assertEquals(9, remaining.getAmount(Aspect.WATER));
        assertFalse(remaining.aspects.containsKey(Aspect.FIRE)); assertFalse(remaining.aspects.containsKey(Aspect.LIGHT));
        assertTrue(remaining.aspects.containsKey(Aspect.EARTH));
        assertEquals(40, available.visSize()); assertEquals(30, costs.visSize());
        assertNotSame(available, remaining);
    }

    @Test void choosesGreatestSumNotNumberOfAspectTypesAndLeavesTiesInTraversalOrder() {
        record Candidate(String id, int cost, boolean allowed) {}
        Candidate first = new Candidate("first", 15, true), second = new Candidate("second", 15, true);
        Candidate locked = new Candidate("locked", 100, false), zero = new Candidate("zero", 0, true);
        assertSame(first, CrucibleAspectCosts.greatestMatching(List.of(zero, first, locked, second), Candidate::allowed, Candidate::cost));
        assertSame(second, CrucibleAspectCosts.greatestMatching(List.of(second, first), Candidate::allowed, Candidate::cost));
        assertNull(CrucibleAspectCosts.greatestMatching(List.of(zero, locked), Candidate::allowed, Candidate::cost));
        assertNull(CrucibleAspectCosts.greatestMatching(List.<Candidate>of(), Candidate::allowed, Candidate::cost));
    }

    @Test void hedgeConversionSubtractsOnlyInputAspectsAndDoesNotChargeUnrelatedInputSurplus() {
        AspectList clay = new AspectList().add(Aspect.WATER, 5).add(Aspect.EARTH, 5);
        AspectList dirt = new AspectList().add(Aspect.EARTH, 5);
        assertEquals(new AspectList().add(Aspect.WATER, 5), CrucibleAspectCosts.subtract(dirt, clay));
        AspectList string = new AspectList().add(Aspect.BEAST, 5).add(Aspect.CRAFT, 1);
        AspectList wheat = new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 5);
        assertEquals(string, CrucibleAspectCosts.subtract(wheat, string));
        AspectList web = new AspectList().add(Aspect.TRAP, 5).add(Aspect.BEAST, 1);
        assertEquals(new AspectList().add(Aspect.TRAP, 5), CrucibleAspectCosts.subtract(string, web));
        assertEquals(10, clay.visSize()); assertEquals(5, dirt.visSize());
        assertEquals(6, string.visSize()); assertEquals(10, wheat.visSize()); assertEquals(6, web.visSize());
    }

    @Test void validatesCostsAndBoundedCodecWithoutBootingMinecraft() {
        assertTrue(CrucibleAspectCosts.valid(new AspectList()));
        assertTrue(CrucibleAspectCosts.valid(new AspectList().add(Aspect.FIRE, 32767)));
        assertFalse(CrucibleAspectCosts.valid(null));
        for (int bad : new int[]{-1, 0, 32768, Integer.MAX_VALUE})
            assertFalse(CrucibleAspectCosts.valid(new AspectList().add(Aspect.FIRE, bad)));
        AspectList nullAspect = new AspectList(); nullAspect.aspects.put(null, 1);
        assertFalse(CrucibleAspectCosts.valid(nullAspect));
        for (String invalid : new String[]{"{\"ignis\":0}", "{\"ignis\":-1}", "{\"ignis\":32768}", "{\"missing_aspect\":1}"})
            assertTrue(CrucibleAspectCosts.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(invalid)).error().isPresent());
        AspectList value = new AspectList().add(Aspect.FIRE, 10).add(Aspect.ENERGY, 10);
        var encoded = CrucibleAspectCosts.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        assertEquals(value, CrucibleAspectCosts.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }
}
