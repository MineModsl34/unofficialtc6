package thaumcraft.api.research;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.internal.CommonInternals;
import thaumcraft.common.config.ConfigResearch;

class ResearchLocationApiTest {
    private HashMap<String, ResourceLocation> previous;

    @BeforeEach void isolate() {
        previous = CommonInternals.jsonLocs;
        CommonInternals.jsonLocs = new HashMap<>();
    }
    @AfterEach void restore() { CommonInternals.jsonLocs = previous; }

    @Test void exactLocationStringRegistrationIsFirstSeenIdempotent() {
        ResourceLocation first = ResourceLocation.parse("addon:custom/research");
        ResourceLocation duplicate = ResourceLocation.parse("addon:custom/research");
        ThaumcraftApi.registerResearchLocation(first);
        ThaumcraftApi.registerResearchLocation(duplicate);
        ThaumcraftApi.registerResearchLocation(ResourceLocation.parse("addon:custom/research.json"));
        assertEquals(2, CommonInternals.jsonLocs.size());
        assertSame(first, CommonInternals.jsonLocs.get(first.toString()));
        assertThrows(NullPointerException.class, () -> ThaumcraftApi.registerResearchLocation(null));
    }

    @Test void repeatedBuiltinRegistrationPreservesExternalEntriesAndPublicMapReference() {
        var map = CommonInternals.jsonLocs;
        ResourceLocation external = ResourceLocation.parse("addon:not_research_prefix");
        ThaumcraftApi.registerResearchLocation(external);
        ConfigResearch.registerResourceLocations(ThaumcraftApi::registerResearchLocation);
        ConfigResearch.registerResourceLocations(ThaumcraftApi::registerResearchLocation);
        assertSame(map, CommonInternals.jsonLocs);
        assertEquals(9, map.size());
        assertSame(external, map.get(external.toString()));
        for (var builtin : ConfigResearch.BUILTIN_RESEARCH_LOCATIONS) assertSame(builtin, map.get(builtin.toString()));
    }

    @Test void existingStringKeyWinsEvenIfPublicMapWasExternallyReassigned() {
        ResourceLocation requested = ResourceLocation.parse("addon:source");
        ResourceLocation externallyMapped = ResourceLocation.parse("addon:replacement");
        HashMap<String, ResourceLocation> replacement = new HashMap<>();
        replacement.put(requested.toString(), externallyMapped);
        CommonInternals.jsonLocs = replacement;
        ThaumcraftApi.registerResearchLocation(requested);
        assertSame(replacement, CommonInternals.jsonLocs);
        assertSame(externallyMapped, replacement.get(requested.toString()));
        assertEquals(1, replacement.size());
    }
}
