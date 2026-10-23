package thaumcraft.api.research;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.Test;

class ScanOreDictionaryTest {
    @Test
    void allThirteenSourceNamesMapExplicitlyToItemTags() {
        Map<String, String> expected = Map.ofEntries(
            Map.entry("oreIron", "ores/iron"), Map.entry("ingotIron", "ingots/iron"),
            Map.entry("blockIron", "storage_blocks/iron"), Map.entry("plateIron", "plates/iron"),
            Map.entry("ingotBrass", "ingots/brass"), Map.entry("blockBrass", "storage_blocks/brass"),
            Map.entry("plateBrass", "plates/brass"), Map.entry("ingotThaumium", "ingots/thaumium"),
            Map.entry("blockThaumium", "storage_blocks/thaumium"), Map.entry("plateThaumium", "plates/thaumium"),
            Map.entry("ingotVoid", "ingots/void"), Map.entry("blockVoid", "storage_blocks/void"),
            Map.entry("plateVoid", "plates/void"));
        assertEquals(13, expected.size());
        expected.forEach((name, path) -> {
            var tag = ScanOreDictionary.tagForLegacyName(name);
            assertEquals(Registries.ITEM, tag.registry());
            assertEquals(ResourceLocation.fromNamespaceAndPath("c", path), tag.location());
        });
    }

    @Test
    void unknownOrCaseNormalizedLegacyNamesNeverBecomeAnImplicitFallback() {
        for (String name : List.of("oreCopper", "plateiron", "ingotThaumium ", "c:plates/iron", "")) {
            assertThrows(IllegalArgumentException.class, () -> new ScanOreDictionary("f_MATIRON", name));
        }
        assertThrows(IllegalArgumentException.class, () -> ScanOreDictionary.tagForLegacyName(null));
        assertThrows(NullPointerException.class, () -> new ScanOreDictionary("f_MATIRON", (String[]) null));
    }

    @Test
    void callerEntryArrayRemainsLiveAndResearchKeyRemainsOpaque() {
        String[] entries = {"ingotBrass"};
        String key = " !CaseSensitive@0 ";
        var scanner = new ScanOreDictionary(key, entries);
        assertSame(key, scanner.getResearchKey(null, null));
        assertEquals(List.of(ScanOreDictionary.tagForLegacyName("ingotBrass")), scanner.tagKeys());
        entries[0] = "plateVoid";
        assertEquals(List.of(ScanOreDictionary.tagForLegacyName("plateVoid")), scanner.tagKeys());
        entries[0] = "unknownAddonOre";
        assertThrows(IllegalArgumentException.class, () -> scanner.matchesTags(tag -> true));
    }

    @Test
    void eachCallReadsMembershipAgainAndStopsAtFirstOrderedMatch() {
        var scanner = new ScanOreDictionary("f_MATIRON", "oreIron", "ingotIron", "blockIron", "plateIron");
        Set<TagKey<Item>> current = new HashSet<>();
        List<TagKey<Item>> queried = new ArrayList<>();
        assertFalse(scanner.matchesTags(tag -> { queried.add(tag); return current.contains(tag); }));
        assertEquals(scanner.tagKeys(), queried);
        current.add(ScanOreDictionary.tagForLegacyName("ingotIron"));
        queried.clear();
        assertTrue(scanner.matchesTags(tag -> { queried.add(tag); return current.contains(tag); }));
        assertEquals(scanner.tagKeys().subList(0, 2), queried);
        current.clear();
        queried.clear();
        assertFalse(scanner.matchesTags(tag -> { queried.add(tag); return current.contains(tag); }));
        assertEquals(4, queried.size(), "neither previous negative nor positive result was cached");
    }

    @Test
    void explicitModernBridgeCopiesDescriptorsButStillReadsCurrentMembership() {
        TagKey<Item> addon = TagKey.create(Registries.ITEM, ResourceLocation.parse("testaddon:custom_material"));
        var input = new ArrayList<>(List.of(addon));
        var scanner = ScanOreDictionary.forTags(null, input);
        input.clear();
        assertNull(scanner.getResearchKey(null, null));
        assertEquals(List.of(addon), scanner.tagKeys());
        assertTrue(scanner.matchesTags(tag -> tag == addon));
        assertFalse(scanner.matchesTags(tag -> false));
        assertThrows(UnsupportedOperationException.class, () -> scanner.tagKeys().clear());
    }

    @Test
    void emptyDescriptorsHaveNoMatchAndDoNotReadMembership() {
        assertFalse(new ScanOreDictionary("f_EMPTY").matchesTags(tag -> { fail("empty old descriptors queried membership"); return true; }));
        assertFalse(ScanOreDictionary.forTags("f_EMPTY", List.of()).matchesTags(tag -> { fail("empty tag descriptors queried membership"); return true; }));
    }
}
