package thaumcraft.datagen;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import thaumcraft.common.lib.research.ResearchRecipeDisplayCatalog.Kind;

class TCResearchRecipeDisplayProviderTest {
    private static byte[] capture() throws Exception {
        return Files.readAllBytes(Path.of("src/generated/resources/assets/thaumcraft/research_recipe_display/catalog.json"));
    }
    @Test void exactExecutedCaptureIncludesEveryKindGroupsAndRealInfusionTransform() throws Exception {
        var catalog = TCResearchRecipeDisplayProvider.validate(capture());
        assertEquals(Map.of(Kind.CRAFTING,598L,Kind.ARCANE,88L,Kind.CRUCIBLE,74L,Kind.INFUSION,51L,
            Kind.ENCHANTMENT,16L,Kind.BLUEPRINT,6L,Kind.GROUP,37L),
            catalog.all().values().stream().collect(Collectors.groupingBy(row -> row.kind(), Collectors.counting())));
        var arcing = catalog.find(ResourceLocation.parse("thaumcraft:iearcingfake"));
        assertEquals("minecraft:wooden_sword;1;0;{infench:[{lvl:1s,id:5s}]}", arcing.output());
        assertEquals(4, arcing.instability());
        assertFalse(arcing.aspects().isEmpty());
        assertTrue(catalog.all().values().stream().filter(row -> row.kind() == Kind.BLUEPRINT).anyMatch(row -> !row.blueprint().isEmpty()));
    }
    @Test void editedOrTruncatedCaptureCannotBecomeAnUnreviewedOrdinaryDatagenDefault() throws Exception {
        var bytes = capture(); bytes[0] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> TCResearchRecipeDisplayProvider.validate(bytes));
        assertThrows(IllegalArgumentException.class, () -> TCResearchRecipeDisplayProvider.validate(new byte[0]));
    }
    @Test void all870ExecutedRawRowsAnd37GroupsSurviveWithoutDefaultNamespaceCollapse() throws Exception {
        byte[] bytes = capture(); var catalog = TCResearchRecipeDisplayProvider.validate(bytes);
        var raw = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("recipes");
        assertEquals(870, raw.size()); assertEquals(870, catalog.all().size());
        java.util.Set<ResourceLocation> groupIdentities = new java.util.HashSet<>();
        int groups = 0, unqualifiedGroups = 0;
        for (var value : raw) {
            var row = value.getAsJsonObject(); String sourceId = row.get("id").getAsString(); var resolved = catalog.findSource(sourceId);
            assertNotNull(resolved, sourceId); assertEquals(sourceId, resolved.sourceId()); assertEquals(row.get("kind").getAsString(), resolved.kind().name());
            if (resolved.kind() != Kind.GROUP) continue;
            groups++; assertTrue(groupIdentities.add(resolved.id()), "group identity collapsed: " + sourceId);
            if (sourceId.indexOf(':') < 0) { unqualifiedGroups++; assertEquals("thaumcraft", resolved.id().getNamespace()); assertEquals("source_group/" + sourceId, resolved.id().getPath()); }
            var members = row.getAsJsonArray("groupMembers"); assertEquals(members.size(), resolved.sourceGroupMembers().size());
            for (int index = 0; index < members.size(); index++) {
                String member = members.get(index).getAsString(); assertEquals(member, resolved.sourceGroupMembers().get(index));
                assertSame(catalog.findSource(member), catalog.find(resolved.groupMembers().get(index)), "raw group member routing " + member);
            }
        }
        assertEquals(37, groups); assertEquals(37, groupIdentities.size()); assertEquals(27, unqualifiedGroups);
        assertEquals(Kind.CRAFTING, catalog.find(ResourceLocation.parse("minecraft:wooden_door")).kind());
        assertEquals(Kind.GROUP, catalog.findSource("wooden_door").kind());
        assertEquals(6, catalog.findSource("wooden_door").groupMembers().size());
    }
}
