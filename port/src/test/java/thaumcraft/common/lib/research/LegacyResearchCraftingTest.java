package thaumcraft.common.lib.research;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyResearchCraftingTest {
    @Test
    void canonicalSourceStringsAndSignedJavaHashesAreFixedNotModernDescriptions() {
        var tools = LegacyResearchCrafting.SCRIBING_TOOLS;
        var table = LegacyResearchCrafting.RESEARCH_TABLE;
        assertEquals("1xitem.scribing_tools@0", tools.canonicalString());
        assertEquals(528411015, tools.hash());
        assertEquals("[#]528411015", tools.marker());
        assertEquals("1xtile.research_table@0", table.canonicalString());
        assertEquals(1543993555, table.hash());
        assertEquals("[#]1543993555", table.marker());
    }

    @Test
    void referenceIndexIsExactlyTheTwoSelectedOriginalItemIdentities() {
        assertEquals(List.of("thaumcraft:scribing_tools", "thaumcraft:research_table"),
            LegacyResearchCrafting.REFERENCES.stream().map(LegacyResearchCrafting.Descriptor::legacyItemId).toList());
        for (var descriptor : LegacyResearchCrafting.REFERENCES) {
            assertSame(descriptor, LegacyResearchCrafting.reference(descriptor.legacyItemId(), 0).orElseThrow());
            assertEquals(0, descriptor.legacyDamage());
        }
        assertThrows(UnsupportedOperationException.class, () -> LegacyResearchCrafting.REFERENCES.clear());
    }

    @Test
    void damagedWildcardAndUnmappedIdentifiersDoNotBecomeReceipts() {
        for (var descriptor : LegacyResearchCrafting.REFERENCES) {
            for (int damage : List.of(-1, 1, 99, 100, 32767, Integer.MAX_VALUE)) {
                assertFalse(descriptor.matchesDamage(damage));
                assertTrue(LegacyResearchCrafting.reference(descriptor.legacyItemId(), damage).isEmpty());
            }
        }
        for (String id : List.of("scribing_tools", "thaumcraft:scribing_tools@0", "thaumcraft:thaumometer",
            "addon:scribing_tools", "minecraft:crafting_table", "thaumcraft:table_wood")) {
            assertTrue(LegacyResearchCrafting.reference(id, 0).isEmpty());
        }
        assertTrue(LegacyResearchCrafting.reference(null, 0).isEmpty());
    }

    @Test
    void nullOwnerCannotRequestResearchMutation() {
        assertFalse(LegacyResearchCrafting.recordCrafting(null, null));
    }
}
