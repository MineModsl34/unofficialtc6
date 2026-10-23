package thaumcraft.client.gui.model;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import thaumcraft.common.lib.research.LegacyScanIdentity;
import thaumcraft.common.lib.research.LegacyScanIdentity.Identity;
import thaumcraft.common.lib.research.catalog.ResearchCatalogParser;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemKind;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemToken;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.LegacyResourceId;

/** Pure source-data projection only: no item registry bootstrap, renderer, GUI or grant. */
class ResearchIconIdentityTest {
    @Test
    void vanillaFlatteningUsesMetadataIncludingReversedBannersAndOldDyeIngredients() {
        resolved("minecraft:stone", 3, "minecraft:diorite");
        resolved("minecraft:dirt", 2, "minecraft:podzol");
        resolved("minecraft:wool", 14, "minecraft:red_wool");
        resolved("minecraft:stained_hardened_clay", 8, "minecraft:light_gray_terracotta");
        resolved("minecraft:banner", 15, "minecraft:white_banner");
        resolved("minecraft:banner", 0, "minecraft:black_banner");
        resolved("minecraft:banner", 1, "minecraft:red_banner");
        resolved("minecraft:dye", 0, "minecraft:ink_sac");
        resolved("minecraft:dye", 4, "minecraft:lapis_lazuli");
        resolved("minecraft:dye", 15, "minecraft:bone_meal");
        resolved("minecraft:melon_block", 0, "minecraft:melon");
        resolved("minecraft:melon", 0, "minecraft:melon_slice");
        resolved("minecraft:stone_slab", 0, "minecraft:smooth_stone_slab");
        resolved("minecraft:sign", 0, "minecraft:oak_sign");
    }

    @Test
    void tcMetadataFamiliesMapOnlyTheirExactOldVariant() {
        String[] ingots = {"thaumium", "void", "brass"};
        for (int meta = 0; meta < ingots.length; meta++)
            resolved("thaumcraft:ingot", meta, "thaumcraft:ingot_" + ingots[meta]);
        resolved("thaumcraft:nugget", 10, "thaumcraft:nugget_rareearth");
        resolved("thaumcraft:cluster", 6, "thaumcraft:cluster_cinnabar");
        resolved("thaumcraft:plate", 3, "thaumcraft:plate_void");
        resolved("thaumcraft:mind", 1, "thaumcraft:mind_biothaumic");
        resolved("thaumcraft:module", 0, "thaumcraft:module_vision");
        String[] baubles = {"amulet_mundane", "ring_mundane", "girdle_mundane", "ring_apprentice",
            "amulet_fancy", "ring_fancy", "girdle_fancy"};
        for (int meta = 0; meta < baubles.length; meta++)
            resolved("thaumcraft:baubles", meta, "thaumcraft:" + baubles[meta]);
        String[] notes = {"sun", "stars_1", "stars_2", "stars_3", "stars_4", "moon_1", "moon_2",
            "moon_3", "moon_4", "moon_5", "moon_6", "moon_7", "moon_8"};
        for (int meta = 0; meta < notes.length; meta++)
            resolved("thaumcraft:celestial_notes", meta, "thaumcraft:celestial_notes_" + notes[meta]);
        resolved("thaumcraft:candle_lightblue", 0, "thaumcraft:candle_light_blue");
        resolved("thaumcraft:candle_silver", 0, "thaumcraft:candle_light_gray");
        assertNull(ResearchIconIdentity.resolve(token("thaumcraft:ingot", 3)));
        assertNull(ResearchIconIdentity.resolve(token("thaumcraft:baubles", 7)));
    }

    @Test
    void everyCurrentlyDeclaredProvenIdentityHasAnExactUniqueInverse() {
        Map<ResourceLocation, Identity> declared = LegacyScanIdentity.knownItems();
        Map<ResourceLocation, Identity> before = new LinkedHashMap<>(declared);
        for (var entry : declared.entrySet()) {
            Identity legacy = entry.getValue();
            assertTrue(legacy.sourceProven(), entry.toString());
            assertEquals(entry.getKey(), ResearchIconIdentity.resolve(token(legacy.itemId().toString(), legacy.metadata())),
                entry.toString());
        }
        assertEquals(before, declared, "Icon projection must not extend or mutate the scanning table");
    }

    @Test
    void durableItemsResolveOnlyUndamagedSourceStacksAndPickaxeIsOneExplicitProof() {
        String[] paths = {"primordial_pearl", "scribing_tools", "thaumium_sword", "void_sword",
            "thaumium_helm", "thaumium_chest", "thaumium_legs", "thaumium_boots",
            "void_helm", "void_chest", "void_legs", "void_boots"};
        for (String path : paths) {
            String id = "thaumcraft:" + path;
            resolved(id, 0, id);
            for (int damage : new int[] {1, 7, 32767, Integer.MAX_VALUE})
                assertNull(ResearchIconIdentity.resolve(token(id, damage)), id + "@" + damage);
        }
        resolved("minecraft:iron_pickaxe", 0, "minecraft:iron_pickaxe");
        assertNull(ResearchIconIdentity.resolve(token("minecraft:iron_pickaxe", 1)));
        assertNull(ResearchIconIdentity.resolve(token("minecraft:diamond_pickaxe", 0)),
            "An explicit iron-pickaxe proof must not become a same-id vanilla fallback");
    }

    @Test
    void portedCasterAndFocusIdentitiesResolveThroughTheirProvenSourceMappings() {
        // These are genuine registered source items after the caster/equipment stages.
        // Keep explicit positive coverage instead of treating the old stage-3 boundary as current.
        for (String path : new String[] {"caster_basic", "focus_1", "focus_2", "focus_3"}) {
            String item = "thaumcraft:" + path;
            resolved(item, 0, item);
            var proven = LegacyScanIdentity.knownItems().get(id(item));
            assertNotNull(proven, item);
            assertTrue(proven.sourceProven(), item);
            assertEquals(id(item), proven.itemId());
            assertEquals(0, proven.metadata());
        }
    }

    @Test
    void unportedNewAmbiguousAndAirIdentitiesStayUnresolved() {
        for (String id : new String[] {"thaumcraft:thaumonomicon_cheat",
            "thaumcraft:unported_item", "minecraft:netherite_ingot", "minecraft:deadbush",
            "minecraft:dead_bush", "minecraft:black_dye", "minecraft:blue_dye", "minecraft:white_dye",
            "minecraft:air", "addon:ingot", "addon:red_wool"})
            assertNull(ResearchIconIdentity.resolve(token(id, 0)), id);
        assertNull(ResearchIconIdentity.resolve(token("minecraft:tallgrass", 0)),
            "The two old dead-bush item identities do not have a proven unique reverse");
        assertNull(ResearchIconIdentity.resolve(token("minecraft:stone", 8)));
    }

    @Test
    void zeroCountOreSnbtAndIgnoredSegmentsCannotProduceAnIconIdentity() {
        ItemToken good = token("minecraft:stone", 0);
        assertEquals(id("minecraft:stone"), ResearchIconIdentity.resolve(good));
        assertEquals(id("minecraft:stone"), ResearchIconIdentity.resolve(copy(good, ItemKind.ITEM, 64, 0,
            null, null, List.of(), good.legacyId())));
        assertNull(ResearchIconIdentity.resolve(null));
        assertNull(ResearchIconIdentity.resolve(copy(good, ItemKind.OREDICT, 1, 0, null, null, List.of(), good.legacyId())));
        assertNull(ResearchIconIdentity.resolve(copy(good, null, 1, 0, null, null, List.of(), good.legacyId())));
        for (int count : new int[] {0, -1, Integer.MIN_VALUE})
            assertNull(ResearchIconIdentity.resolve(copy(good, ItemKind.ITEM, count, 0, null, null, List.of(), good.legacyId())));
        assertNull(ResearchIconIdentity.resolve(copy(good, ItemKind.ITEM, 1, -1, null, null, List.of(), good.legacyId())));
        assertNull(ResearchIconIdentity.resolve(copy(good, ItemKind.ITEM, 1, 0, "{}", null, List.of(), good.legacyId())));
        assertNull(ResearchIconIdentity.resolve(copy(good, ItemKind.ITEM, 1, 0, null, "{}", List.of(), good.legacyId())));
        assertNull(ResearchIconIdentity.resolve(copy(good, ItemKind.ITEM, 1, 0, "", "", List.of(), good.legacyId())));
        assertNull(ResearchIconIdentity.resolve(copy(good, ItemKind.ITEM, 1, 0, null, null, List.of("ignored"), good.legacyId())));
    }

    @Test
    void malformedOrNonmodernLegacyIdsAreRejectedEvenIfTheSyntaxFlagIsForged() {
        ItemToken good = token("minecraft:stone", 0);
        List<LegacyResourceId> malformed = List.of(
            new LegacyResourceId("minecraft:stone", "minecraft", "stone", false),
            new LegacyResourceId("minecraft:stone", "Minecraft", "stone", true),
            new LegacyResourceId("minecraft:stone", "minecraft", "stone space", true),
            new LegacyResourceId("minecraft:stone", "minecraft", "stone:extra", true),
            new LegacyResourceId("minecraft:stone", "", "stone", true),
            new LegacyResourceId("minecraft:stone", "minecraft", "", true),
            new LegacyResourceId("minecraft:stone", null, "stone", true),
            new LegacyResourceId("minecraft:stone", "minecraft", null, true));
        for (LegacyResourceId legacy : malformed)
            assertNull(ResearchIconIdentity.resolve(copy(good, ItemKind.ITEM, 1, 0, null, null, List.of(), legacy)), legacy.toString());
        assertNull(ResearchIconIdentity.resolve(copy(good, ItemKind.ITEM, 1, 0, null, null, List.of(), null)));
    }

    @Test
    void inverseBuilderSuppressesAllAmbiguousCandidatesAndNeverPromotesUnprovenOrAir() {
        Map<ResourceLocation, Identity> forward = new LinkedHashMap<>();
        ResourceLocation shared = id("old:shared");
        forward.put(id("modern:first"), new Identity(shared, 0, true));
        forward.put(id("modern:second"), new Identity(shared, 0, true));
        forward.put(id("modern:third"), new Identity(shared, 0, true));
        forward.put(id("modern:other_meta"), new Identity(shared, 1, true));
        forward.put(id("modern:unproven"), new Identity(id("old:unproven"), 0, false));
        forward.put(id("minecraft:air"), new Identity(id("old:air_candidate"), 0, true));
        forward.put(id("modern:from_air"), new Identity(id("minecraft:air"), 0, true));
        var inverse = ResearchIconIdentity.uniqueInverse(forward);
        assertFalse(inverse.containsKey(new ResearchIconIdentity.LegacyIdentity(shared, 0)));
        assertEquals(Map.of(new ResearchIconIdentity.LegacyIdentity(shared, 1), id("modern:other_meta")), inverse);
        assertThrows(UnsupportedOperationException.class, inverse::clear);
        forward.clear();
        assertEquals(1, inverse.size(), "Detached immutable inverse must not alias the mutable input");
    }

    @Test
    void actualIconParserNormalizationRemainsSourceBackedButItsIgnoredOrSnbtTokensAreDeferred() {
        for (String raw : new String[] {"minecraft:stone;2;3", "MINECRAFT:STONE;2;3", "stone;2;3", "m:stone;2;3"})
            assertEquals(id("minecraft:diorite"), ResearchIconIdentity.resolve(parsedIcon(raw)), raw);
        for (String raw : new String[] {"minecraft:stone;0;3", "minecraft:stone;2;3;ignored",
            "minecraft:stone;2;3;4", "minecraft:stone;2;3;{}", "minecraft:stone;-1;3",
            "minecraft:stone;2;3;{display:{Name:'source'}}", "minecraft:Bad ID"})
            assertNull(ResearchIconIdentity.resolve(parsedIcon(raw)), raw);
        assertEquals(id("minecraft:iron_pickaxe"), ResearchIconIdentity.resolve(parsedIcon("minecraft:iron_pickaxe")));
    }

    private static void resolved(String old, int metadata, String modern) {
        assertEquals(id(modern), ResearchIconIdentity.resolve(token(old, metadata)), old + "@" + metadata);
    }

    private static ItemToken token(String old, int metadata) {
        ResourceLocation identity = id(old);
        return new ItemToken(old, old, ItemKind.ITEM, old, 1, metadata, null, null, List.of(),
            new LegacyResourceId(old, identity.getNamespace(), identity.getPath(), true),
            "Item registry/metadata/SNBT resolution deferred");
    }

    private static ItemToken copy(ItemToken old, ItemKind kind, int count, int damage, String rawSnbt,
                                  String legacySnbt, List<String> ignored, LegacyResourceId legacy) {
        return new ItemToken(old.raw(), old.legacyText(), kind, old.name(), count, damage, rawSnbt,
            legacySnbt, ignored, legacy, old.unresolvedReason());
    }

    private static ItemToken parsedIcon(String raw) {
        JsonObject entry = new JsonObject();
        entry.addProperty("key", "ICON"); entry.addProperty("name", "source.icon");
        entry.addProperty("category", "BASICS");
        JsonArray icons = new JsonArray(); icons.add(raw); entry.add("icons", icons);
        entry.add("stages", new JsonArray());
        JsonArray entries = new JsonArray(); entries.add(entry);
        JsonObject root = new JsonObject(); root.add("entries", entries);
        return ResearchCatalogParser.parse(Map.of("fixture", root.toString()), Set.of("BASICS"))
            .entries().getFirst().icons().values().getFirst().itemCandidate();
    }

    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
}
