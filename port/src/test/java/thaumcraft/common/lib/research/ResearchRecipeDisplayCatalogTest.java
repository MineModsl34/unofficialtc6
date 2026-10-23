package thaumcraft.common.lib.research;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
class ResearchRecipeDisplayCatalogTest {
    @Test void danglingBookHandlesResolveOnlyExistingDisplaysAndKeepExplicitLeafPriority() {
        var catalog = ResearchRecipeDisplayCatalog.parse("""
            {"recipes":[{"id":"thaumcraft:nitorgroup","kind":"GROUP","groupMembers":["thaumcraft:nitordyered"]},
              {"id":"thaumcraft:nitordyered","kind":"CRAFTING"},
              {"id":"thaumcraft:jarlabels","kind":"GROUP","groupMembers":["thaumcraft:label_aer"]},
              {"id":"thaumcraft:label_aer","kind":"CRAFTING"},
              {"id":"thaumcraft:stonearcane","kind":"CRAFTING"},
              {"id":"thaumcraft:brickarcane","kind":"CRAFTING"},
              {"id":"thaumcraft:arcane_brick","kind":"CRAFTING","output":"test:override"}]}
            """);
        assertEquals(ResourceLocation.parse("thaumcraft:nitorgroup"), catalog.bookKey("thaumcraft:nitorcolor"));
        assertEquals(ResourceLocation.parse("thaumcraft:jarlabels"), catalog.bookKey("thaumcraft:jarlabelessence"));
        assertEquals(ResourceLocation.parse("thaumcraft:stonearcane"), catalog.bookKey("thaumcraft:arcane_stone"));
        assertEquals(ResourceLocation.parse("thaumcraft:arcane_brick"), catalog.bookKey("thaumcraft:arcane_brick"));
        var missing = ResourceLocation.parse("thaumcraft:nitorcolor");
        assertEquals(missing, ResearchRecipeDisplayCatalog.empty().bookKey(missing.toString()));
        assertNull(ResearchRecipeDisplayCatalog.empty().find(missing), "an alias never creates a synthetic display");
    }
    @Test void rawVanillaGroupDoesNotOverwriteNativeLeafAndSourceBookKeepsLeafPriority() {
        var catalog = ResearchRecipeDisplayCatalog.parse("""
            {"recipes":[{"id":"minecraft:wooden_door","kind":"CRAFTING","output":"minecraft:wooden_door;3;0"},
              {"id":"wooden_door","kind":"GROUP","groupMembers":["minecraft:wooden_door","minecraft:spruce_door"]},
              {"id":"minecraft:spruce_door","kind":"CRAFTING"}]}
            """);
        var nativeKey = ResourceLocation.parse("minecraft:wooden_door");
        var groupKey = ResourceLocation.parse("thaumcraft:source_group/wooden_door");
        assertEquals(3, catalog.all().size());
        assertEquals(ResearchRecipeDisplayCatalog.Kind.CRAFTING, catalog.find(nativeKey).kind());
        assertEquals("minecraft:wooden_door;3;0", catalog.find(nativeKey).output());
        assertSame(catalog.find(groupKey), catalog.findSource("wooden_door"));
        assertEquals(ResearchRecipeDisplayCatalog.Kind.GROUP, catalog.findSource("wooden_door").kind());
        assertSame(catalog.find(nativeKey), catalog.findSource("minecraft:wooden_door"));
        assertEquals(nativeKey, catalog.bookKey("wooden_door"), "TC6 catalog/fake/vanilla leaf precedes raw group fallback");
        assertEquals(nativeKey, catalog.bookKey("minecraft:wooden_door"));
        assertEquals(groupKey, catalog.bookKey("thaumcraft:source_group/wooden_door"));
        assertEquals(java.util.List.of("minecraft:wooden_door", "minecraft:spruce_door"), catalog.find(groupKey).sourceGroupMembers());
        assertEquals(nativeKey, catalog.find(groupKey).groupMembers().getFirst());
        assertThrows(UnsupportedOperationException.class, () -> catalog.find(groupKey).sourceGroupMembers().clear());
    }
    @Test void rawForwardNestedGroupMembersRetainExactKeysAndUseDistinctDisplayIdentity() {
        var catalog = ResearchRecipeDisplayCatalog.parse("""
            {"recipes":[{"id":"thaumcraft:parent","kind":"GROUP","groupMembers":["wooden_door","minecraft:wooden_door"]},
              {"id":"wooden_door","kind":"GROUP","groupMembers":["minecraft:wooden_door"]},
              {"id":"minecraft:wooden_door","kind":"CRAFTING"}]}
            """);
        var parent = catalog.findSource("thaumcraft:parent");
        assertEquals(java.util.List.of("wooden_door", "minecraft:wooden_door"), parent.sourceGroupMembers());
        assertEquals(java.util.List.of(ResourceLocation.parse("thaumcraft:source_group/wooden_door"), ResourceLocation.parse("minecraft:wooden_door")), parent.groupMembers());
        assertSame(catalog.findSource("wooden_door"), catalog.find(parent.groupMembers().getFirst()));
        assertEquals("wooden_door", catalog.findSource("wooden_door").sourceId());
    }
    @Test void retainsRawTokensGeometryCostsGroupsAndBlueprintWithoutMaterializingItems() {
        var catalog = ResearchRecipeDisplayCatalog.parse("""
            {"recipes":[{"id":"thaumcraft:wand","kind":"ARCANE","research":"FIRSTSTEPS@2","output":"thaumcraft:future;1;3",
              "width":2,"height":1,"ingredients":[["oredict:ingotGold"],[]],"aspects":{"aer":2},"vis":10},
              {"id":"thaumcraft:group","kind":"GROUP","groupMembers":["thaumcraft:wand"]},
              {"id":"thaumcraft:construct","kind":"BLUEPRINT","blueprint":[{"x":1,"y":2,"z":3,"item":"minecraft:stone"}]}]}
            """);
        var wand = catalog.find(ResourceLocation.parse("thaumcraft:wand"));
        assertEquals(2, wand.width()); assertEquals(1, wand.height()); assertEquals(10, wand.vis());
        assertEquals("FIRSTSTEPS@2", wand.research()); assertEquals("thaumcraft:future;1;3", wand.output());
        assertEquals(2, wand.aspects().get("aer")); assertEquals("oredict:ingotGold", wand.ingredients().getFirst().getFirst());
        assertEquals(1, catalog.find(ResourceLocation.parse("thaumcraft:group")).groupMembers().size());
        assertEquals(2, catalog.find(ResourceLocation.parse("thaumcraft:construct")).blueprint().getFirst().y());
        assertThrows(UnsupportedOperationException.class, () -> catalog.all().clear());
        assertThrows(UnsupportedOperationException.class, () -> wand.ingredients().getFirst().clear());
    }
    @Test void boundedDisplayParserRejectsMalformedIdsDuplicatesAndUnsafeGeometry() {
        for (String json : new String[]{"{}", "{\"recipes\":[{\"id\":\"Bad Uppercase\"}]}",
            "{\"recipes\":[{\"id\":\"test:r\",\"width\":99}]}",
            "{\"recipes\":[{\"id\":\"test:r\",\"aspects\":{\"aer\":-1}}]}",
            "{\"recipes\":[{\"id\":\"test:r\"},{\"id\":\"test:r\"}]}",
            "{\"recipes\":[{\"id\":\"test:r\",\"blueprint\":[{\"x\":999}]}]}"})
            assertThrows(RuntimeException.class, () -> ResearchRecipeDisplayCatalog.parse(json));
        assertThrows(IllegalArgumentException.class, () -> ResearchRecipeDisplayCatalog.parse(" ".repeat(ResearchRecipeDisplayCatalog.MAX_BYTES + 1)));
    }
}
