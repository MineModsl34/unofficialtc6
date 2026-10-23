package thaumcraft.common.lib.research.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.research.ResearchEntry.EnumResearchMeta;
import thaumcraft.common.lib.research.catalog.ResearchCatalogParser.CatalogException;
import thaumcraft.common.lib.research.catalog.ResearchCatalogParser.Limits;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ArrayField;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.Entry;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemKind;

class ResearchCatalogParserTest {
    private static final Set<String> CATEGORIES = Set.of("BASICS", "ALCHEMY", "ARTIFICE", "AUROMANCY",
        "ELDRITCH", "GOLEMANCY", "INFUSION", "basics");

    @Test
    void retainsSourceTextOrderAndOpaqueKeysWithoutRegistryPublication() {
        JsonObject first = entry("!Mixed@2", "BASICS", 1, 2);
        JsonObject second = entry("!mixed@2", "basics", 1, 2);
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("source-z", " \n" + document(first) + "\n");
        sources.put("source-a", document(second));
        ResearchCatalogSpec spec = ResearchCatalogParser.parse(sources, CATEGORIES);
        assertEquals(List.of("source-z", "source-a"), List.copyOf(spec.sources().keySet()));
        assertEquals(sources.get("source-z"), spec.sources().get("source-z"));
        assertEquals(List.of("!Mixed@2", "!mixed@2"), spec.entries().stream().map(Entry::key).toList());
        assertEquals("basics", spec.entries().get(1).category());
        sources.clear();
        assertEquals(2, spec.sources().size());
        assertThrows(UnsupportedOperationException.class, () -> spec.sources().clear());
        assertThrows(UnsupportedOperationException.class, () -> spec.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> spec.entriesByKey().clear());
    }

    @Test
    void missingAndExplicitlyEmptyArraysRemainDistinctButBothHaveLegacyNull() {
        JsonObject obj = entry("EMPTY", "BASICS", 0, 0);
        obj.add("parents", new JsonArray());
        obj.add("stages", new JsonArray());
        obj.add("addenda", new JsonArray());
        Entry entry = parse(obj).entries().getFirst();
        assertFalse(entry.siblings().present());
        assertTrue(entry.parents().present());
        assertNull(entry.siblings().legacyValues());
        assertNull(entry.parents().legacyValues());
        assertTrue(entry.stages().present());
        assertNull(entry.stages().legacyValues());
        assertNull(entry.addenda().legacyValues());
        assertThrows(UnsupportedOperationException.class, () -> entry.parents().values().add("OTHER"));
        assertThrows(IllegalArgumentException.class, () -> new ArrayField<>(false, List.of("bad")));
    }

    @Test
    void absentLocationUsesOriginalZeroAndIsStillDistinctFromExplicitLocation() {
        JsonObject obj = entry("ZERO", "BASICS", 0, 0);
        obj.remove("location");
        Entry entry = parse(obj).entries().getFirst();
        assertFalse(entry.location().present());
        assertEquals(0, entry.displayColumn());
        assertEquals(0, entry.displayRow());
    }

    @Test
    void metadataEnumsPreserveRawOrderAndDuplicates() {
        JsonObject obj = entry("META", "BASICS", 0, 0);
        obj.add("meta", strings("round", "AUTOUNLOCK", "ROUND"));
        Entry entry = parse(obj).entries().getFirst();
        assertEquals(List.of(EnumResearchMeta.ROUND, EnumResearchMeta.AUTOUNLOCK, EnumResearchMeta.ROUND),
            entry.meta().values().stream().map(ResearchCatalogSpec.MetaToken::value).toList());
        assertEquals("round", entry.meta().values().getFirst().raw());
    }

    @Test
    void stageAndAddendumRetainAllFieldsWithoutInventingCraftHashes() {
        JsonObject obj = entry("FULL", "BASICS", -4, 7);
        obj.add("parents", strings("~OPAQUE@2", "!gotThing"));
        obj.add("siblings", strings("OTHER", "!marker"));
        obj.add("reward_item", strings("minecraft:stick;2;3"));
        obj.add("reward_knowledge", strings("THEORY;BASICS;2"));
        JsonObject stage = stage(obj);
        stage.add("required_item", strings("minecraft:planks;30;1"));
        stage.add("required_craft", strings("oredict:stick"));
        stage.add("required_knowledge", strings("OBSERVATION;BASICS;+1"));
        stage.add("required_research", strings("!Custom@2;NS:Texture.png;ignored", "m_deepdown"));
        stage.add("recipes", strings("Thaumcraft:MixedRecipe"));
        stage.addProperty("warp", -5);
        JsonObject addendum = new JsonObject();
        addendum.addProperty("text", "research.FULL.addendum");
        addendum.add("required_research", strings("!Custom;unchanged"));
        addendum.add("recipes", strings("Thaumcraft:OtherRecipe"));
        JsonArray addenda = new JsonArray();
        addenda.add(addendum);
        obj.add("addenda", addenda);
        Entry entry = parse(obj).entries().getFirst();
        var parsed = entry.stages().values().getFirst();
        assertEquals(List.of("~OPAQUE@2", "!gotThing"), entry.parents().values());
        assertEquals(2, entry.rewardItems().values().getFirst().count());
        assertEquals(2, entry.rewardKnowledge().values().getFirst().amount());
        assertEquals(30, parsed.requiredItems().values().getFirst().count());
        assertEquals(ItemKind.OREDICT, parsed.requiredCraft().values().getFirst().kind());
        assertEquals(EnumKnowledgeType.OBSERVATION, parsed.requiredKnowledge().values().getFirst().type());
        assertEquals("!Custom@2", parsed.requiredResearch().values().getFirst().key());
        assertEquals("NS:Texture.png", parsed.requiredResearch().values().getFirst().icon());
        assertEquals("!Custom@2;NS:Texture.png;ignored", parsed.requiredResearch().values().getFirst().raw());
        assertNull(parsed.requiredResearch().values().get(1).icon());
        assertEquals(-5, parsed.warp());
        assertTrue(parsed.warpPresent());
        assertEquals("thaumcraft:mixedrecipe", parsed.recipes().values().getFirst().normalized());
        assertEquals("!Custom;unchanged", entry.addenda().values().getFirst().requiredResearch().values().getFirst());
    }

    @Test
    void recipeNormalizationUsesLegacyRootLocaleAndOneCharacterPrefixQuirkOnly() {
        JsonObject obj = entry("UPPER_RESEARCH", "BASICS", 0, 0);
        stage(obj).add("recipes", strings("Thaumcraft:StoneArcane", "OneUpperRecipe", "a:Upper", ":UPPER"));
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            Entry entry = parse(obj).entries().getFirst();
            assertEquals("UPPER_RESEARCH", entry.key());
            assertEquals(List.of("thaumcraft:stonearcane", "minecraft:oneupperrecipe", "minecraft:upper", "minecraft:upper"),
                entry.stages().values().getFirst().recipes().values().stream().map(ResearchCatalogSpec.LegacyResourceId::normalized).toList());
            assertEquals("Thaumcraft:StoneArcane", entry.stages().values().getFirst().recipes().values().getFirst().raw());
        } finally { Locale.setDefault(previous); }
    }

    @Test
    void quoteReplacementRetainsUntouchedSnbtAndIconDirectHelperBug() {
        String raw = "thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'aer'}]}";
        JsonObject obj = entry("NBT", "BASICS", 0, 0);
        obj.add("icons", strings(raw));
        obj.add("reward_item", strings(raw));
        stage(obj).add("required_item", strings(raw));
        Entry entry = parse(obj).entries().getFirst();
        var item = entry.stages().values().getFirst().requiredItems().values().getFirst();
        assertEquals(raw, item.raw());
        assertEquals("{Aspects:[{amount:1,key:'aer'}]}", item.rawSnbt());
        assertEquals("{Aspects:[{amount:1,key:\"aer\"}]}", item.legacySnbt());
        assertEquals(item.legacyText(), entry.rewardItems().values().getFirst().legacyText());
        assertEquals(item.rawSnbt(), entry.icons().values().getFirst().itemCandidate().legacySnbt());
        assertTrue(item.unresolvedReason().contains("deferred"));
    }

    @Test
    void semicolonInsideSnbtQuirkRetainsWholeRawSuffixAndEffectiveFirstSegment() {
        JsonObject obj = entry("SEMICOLON", "BASICS", 0, 0);
        stage(obj).add("required_item", strings("minecraft:stick;2;3;{x:'first;second'}"));
        var item = parse(obj).entries().getFirst().stages().values().getFirst().requiredItems().values().getFirst();
        assertEquals("{x:'first;second'}", item.rawSnbt());
        assertEquals("{x:\"first", item.legacySnbt());
        assertEquals(List.of("second\"}"), item.ignoredSegments());
    }

    @Test
    void legacyIgnoredNegativeAndNonNumericSegmentsDoNotDisappearFromRawDescriptor() {
        JsonObject obj = entry("ITEM", "BASICS", 0, 0);
        stage(obj).add("required_item", strings("minecraft:stick;-1;ignored;4;2;6"));
        var item = parse(obj).entries().getFirst().stages().values().getFirst().requiredItems().values().getFirst();
        assertEquals(4, item.count());
        assertEquals(2, item.damage());
        assertEquals(List.of("-1", "ignored", "6"), item.ignoredSegments());
    }

    @Test
    void itemIntegerTokensRetainOriginalUnicodeBmpDigitAcceptanceAndStrictOverflow() {
        JsonObject obj = entry("UNICODE_DIGITS", "BASICS", 0, 0);
        stage(obj).add("required_item", strings("minecraft:stick;\u0664;\uff12", "minecraft:stone;-\u0661;\u0663"));
        var items = parse(obj).entries().getFirst().stages().values().getFirst().requiredItems().values();
        assertEquals(4, items.getFirst().count());
        assertEquals(2, items.getFirst().damage());
        assertEquals("minecraft:stick;\u0664;\uff12", items.getFirst().raw());
        assertEquals(3, items.get(1).count());
        assertEquals(List.of("-\u0661"), items.get(1).ignoredSegments());
        stage(obj).add("required_item", strings("minecraft:stick;\uff12\uff11\uff14\uff17\uff14\uff18\uff13\uff16\uff14\uff18"));
        assertThrows(CatalogException.class, () -> parse(obj));
    }

    @Test
    void malformedSourceItemIdAndZeroCountAreExplicitUnresolvedTokensNotFiltered() {
        JsonObject obj = entry("BROKEN_ORIGINAL", "INFUSION", 0, 0);
        stage(obj).add("required_item", strings("minecraft:thaumcraft:enchanted_placeholder;1;0;{ench:[]}", "minecraft:stick;0"));
        ResearchCatalogSpec spec = parse(obj);
        var items = spec.entries().getFirst().stages().values().getFirst().requiredItems().values();
        assertEquals(2, items.size());
        assertFalse(items.getFirst().legacyId().modernSyntax());
        assertEquals("minecraft:thaumcraft:enchanted_placeholder", items.getFirst().name());
        assertTrue(items.getFirst().unresolvedReason().contains("not AIR"));
        assertEquals(0, items.get(1).count());
        assertEquals(1, spec.diagnostics().stream().filter(diagnostic -> diagnostic.code().equals("UNRESOLVED_ITEM_ID")).count());
    }

    @Test
    void oreGrammarAndFocusFallbackPreserveOriginalUnresolvedAlternatives() {
        JsonObject obj = entry("ICONS", "BASICS", 0, 0);
        obj.add("icons", strings("focus:thaumcraft.SCATTER", "thaumcraft:textures/Icon.png", "minecraft:stick"));
        stage(obj).add("required_craft", strings("oredict:stick:ignored", "oredict:"));
        Entry entry = parse(obj).entries().getFirst();
        assertEquals("focus:thaumcraft.SCATTER", entry.icons().values().getFirst().focusFallback());
        assertNull(entry.icons().values().getFirst().resourceFallback());
        assertNotNull(entry.icons().values().get(1).itemCandidate());
        assertNotNull(entry.icons().values().get(1).resourceFallback());
        assertNull(entry.icons().values().get(2).focusFallback());
        var ores = entry.stages().values().getFirst().requiredCraft().values();
        assertEquals("stick", ores.getFirst().name());
        assertEquals(List.of("ignored"), ores.getFirst().ignoredSegments());
        assertEquals("", ores.get(1).name());
        assertTrue(ores.get(1).unresolvedReason().contains("not discarded"));
    }

    @Test
    void unknownFieldsAreImmutableAndOriginalTypoNeverBecomesRequirement() {
        JsonObject obj = entry("TYPO", "ARTIFICE", 0, 0);
        stage(obj).add("required_knowledgeauram", strings("OBSERVATION;ARTIFICE;1"));
        JsonObject unknown = new JsonObject();
        unknown.add("nested", strings("raw"));
        obj.add("extension", unknown);
        ResearchCatalogSpec spec = parse(obj);
        var entry = spec.entries().getFirst();
        var stage = entry.stages().values().getFirst();
        assertFalse(stage.requiredKnowledge().present());
        assertEquals("[\"OBSERVATION;ARTIFICE;1\"]", stage.unknownFields().get("required_knowledgeauram"));
        assertEquals("{\"nested\":[\"raw\"]}", entry.unknownFields().get("extension"));
        unknown.addProperty("modified", true);
        assertFalse(entry.unknownFields().get("extension").contains("modified"));
        assertThrows(UnsupportedOperationException.class, () -> stage.unknownFields().clear());
        assertEquals(2, spec.diagnostics().stream().filter(diagnostic -> diagnostic.code().equals("UNKNOWN_FIELD")).count());
    }

    @Test
    void duplicateResearchKeyAnywhereFailsWholeCandidateRatherThanFirstWins() {
        CatalogException failure = assertThrows(CatalogException.class,
            () -> parse(entry("KEY", "BASICS", 0, 0), entry("KEY", "ALCHEMY", 1, 1)));
        assertTrue(failure.getMessage().contains("Duplicate opaque research key"));
    }

    @Test
    void duplicateCoordinatesWithinCategoryFailButOtherCategoriesMayReuseThem() {
        assertThrows(CatalogException.class,
            () -> parse(entry("ONE", "BASICS", 4, 5), entry("TWO", "BASICS", 4, 5)));
        assertEquals(2, parse(entry("ONE", "BASICS", 4, 5), entry("TWO", "ALCHEMY", 4, 5)).entries().size());
    }

    @Test
    void invalidEnumCategoryAndOpaqueKeyRejectWholeCandidateWithPath() {
        JsonObject obj = entry("GOOD", "BASICS", 0, 0);
        obj.add("meta", strings("MISSING"));
        assertTrue(assertThrows(CatalogException.class, () -> parse(obj)).path().endsWith(".meta[0]"));
        assertThrows(CatalogException.class, () -> parse(entry("GOOD", "missing", 0, 0)));
        assertThrows(CatalogException.class, () -> parse(entry("", "BASICS", 0, 0)));
        assertThrows(CatalogException.class, () -> parse(entry(" ", "BASICS", 0, 0)));
        assertThrows(CatalogException.class, () -> parse(entry("BAD\nKEY", "BASICS", 0, 0)));
    }

    @Test
    void badKnowledgeTokenDoesNotSilentlyRemoveRequiredKnowledge() {
        for (String bad : List.of("INVALID;BASICS;1", "THEORY;MISSING;1", "THEORY;BASICS;0", "THEORY;BASICS;-1",
            "THEORY;BASICS;2147483648", "THEORY;BASICS; 1", "THEORY;BASICS;bad", "THEORY;1")) {
            JsonObject obj = entry("KEY", "BASICS", 0, 0);
            stage(obj).add("required_knowledge", strings(bad));
            assertThrows(CatalogException.class, () -> parse(obj), bad);
        }
    }

    @Test
    void malformedKnownShapesAndNullValuesAreNotCoercedOrSkipped() {
        for (String field : List.of("key", "name", "category", "stages")) {
            JsonObject obj = entry("KEY", "BASICS", 0, 0);
            obj.remove(field);
            assertThrows(CatalogException.class, () -> parse(obj), field);
        }
        for (String badArray : List.of("null", "1", "{}", "[true]", "[1]", "[null]")) {
            String raw = "{\"entries\":[{\"key\":\"KEY\",\"name\":\"name\",\"category\":\"BASICS\",\"parents\":" + badArray
                + ",\"stages\":[{\"text\":\"text\"}]}]}";
            assertThrows(CatalogException.class, () -> ResearchCatalogParser.parse(Map.of("test", raw), CATEGORIES), badArray);
        }
        JsonObject obj = entry("KEY", "BASICS", 0, 0);
        stage(obj).remove("text");
        assertThrows(CatalogException.class, () -> parse(obj));
    }

    @Test
    void overflowFractionalIntegerAndBadLocationLengthRejectCandidate() {
        for (String number : List.of("2147483648", "-2147483649", "1.5", "1e999999")) {
            JsonObject obj = entry("KEY", "BASICS", 0, 0);
            String raw = document(obj).replace("\"text\":\"stage.text\"", "\"text\":\"stage.text\",\"warp\":" + number);
            assertThrows(CatalogException.class, () -> ResearchCatalogParser.parse(Map.of("test", raw), CATEGORIES), number);
        }
        for (int length : List.of(0, 1, 3)) {
            JsonObject obj = entry("KEY", "BASICS", 0, 0);
            JsonArray location = new JsonArray();
            for (int i = 0; i < length; i++) location.add(i);
            obj.add("location", location);
            assertThrows(CatalogException.class, () -> parse(obj));
        }
        JsonObject obj = entry("KEY", "BASICS", 0, 0);
        stage(obj).add("required_craft", strings("minecraft:stick;2147483648"));
        assertThrows(CatalogException.class, () -> parse(obj));
    }

    @Test
    void strictJsonRejectsDuplicatesCommentsUnquotedSingleQuotedAndTrailingContent() {
        for (String bad : List.of("{\"entries\":[],\"entries\":[]}", "{/*comment*/\"entries\":[]}",
            "{entries:[]}", "{'entries':[]}", "{\"entries\":[]} {}", "{\"entries\":[}", "")) {
            assertThrows(CatalogException.class, () -> ResearchCatalogParser.parse(Map.of("test", bad), CATEGORIES), bad);
        }
    }

    @Test
    void lexicalPreflightClosesGsonNonLenientKeywordControlAndEscapeHoles() {
        for (String value : List.of("TRUE", "False", "NULL", "NaN", "Infinity", "+1", "01", "1.", ".1", "1e", "-", "truefalse")) {
            String raw = "{\"entries\":[],\"extension\":" + value + "}";
            assertThrows(CatalogException.class, () -> ResearchCatalogParser.parse(Map.of("test", raw), CATEGORIES), value);
        }
        for (String invalidString : List.of("raw\nline", "raw\tline", "raw\rline", "raw\u0001line", "bad\\'escape", "bad\\\nline",
            "bad\\u12x4", "bad\\u\uff11\uff12\uff13\uff14", "bad\\x20")) {
            String raw = "{\"entries\":[],\"extension\":\"" + invalidString + "\"}";
            assertThrows(CatalogException.class, () -> ResearchCatalogParser.parse(Map.of("test", raw), CATEGORIES), invalidString);
        }
    }

    @Test
    void lexicalPreflightRetainsValidJsonEscapesNumbersWhitespaceAndExactRawSource() {
        String raw = " \r\n\t{\"entries\":[],\"extension\":[true,false,null,0,-0,1.25,-1.25e+3,1E-4,"
            + "\"escaped\\\"\\\\\\/\\b\\f\\n\\r\\t\\u0041\"]} \r\n";
        ResearchCatalogSpec spec = ResearchCatalogParser.parse(Map.of("valid.json", raw), CATEGORIES);
        assertEquals(raw, spec.sources().get("valid.json"));
        assertEquals(0, spec.entries().size());
        assertEquals(1, spec.diagnostics().size());
    }

    @Test
    void depthNodeStringSourceAndArrayBoundsAreEnforcedBeforePublishing() {
        String valid = document(entry("KEY", "BASICS", 0, 0));
        Limits tinySources = new Limits(1, 10_000, 10_000, 32, 100, 100, 1000, 1000);
        assertThrows(CatalogException.class,
            () -> ResearchCatalogParser.parse(Map.of("one", valid, "two", valid), CATEGORIES, tinySources));
        assertThrows(CatalogException.class, () -> ResearchCatalogParser.parse(Map.of("one", valid), CATEGORIES,
            new Limits(1, 1, 10_000, 32, 100, 100, 1000, 1000)));
        assertThrows(CatalogException.class, () -> ResearchCatalogParser.parse(Map.of("one", valid), CATEGORIES,
            new Limits(1, 10_000, 1, 32, 100, 100, 1000, 1000)));
        assertThrows(CatalogException.class, () -> ResearchCatalogParser.parse(Map.of("one", valid), CATEGORIES,
            new Limits(1, 10_000, 10_000, 1, 100, 100, 1000, 1000)));
        assertThrows(CatalogException.class, () -> ResearchCatalogParser.parse(Map.of("one", valid), CATEGORIES,
            new Limits(1, 10_000, 10_000, 32, 100, 100, 2, 1000)));
        assertThrows(CatalogException.class, () -> ResearchCatalogParser.parse(Map.of("one", valid), CATEGORIES,
            new Limits(1, 10_000, 10_000, 32, 100, 100, 1000, 2)));
        assertThrows(CatalogException.class, () -> ResearchCatalogParser.parse(Map.of("one", valid), CATEGORIES,
            new Limits(1, 10_000, 10_000, 32, 100, 1, 1000, 1000)));
        assertThrows(CatalogException.class, () -> ResearchCatalogParser.parse(Map.of("one",
            document(entry("ONE", "BASICS", 0, 0), entry("TWO", "BASICS", 1, 0))), CATEGORIES,
            new Limits(1, 10_000, 10_000, 32, 1, 100, 1000, 1000)));
        assertThrows(IllegalArgumentException.class, () -> new Limits(1, 1, 1, 129, 1, 1, 1, 1));
    }

    @Test
    void optionalPrivateOriginalEightFilesRetainAllOccurrencesAndKnownSourceMistakes() throws Exception {
        String configured = System.getProperty("thaumcraft.originalResearchDirectory");
        Path directory = configured == null ? Path.of("..", "reference", "tc6", "src", "assets", "thaumcraft", "research")
            : Path.of(configured);
        Assumptions.assumeTrue(Files.isDirectory(directory), "Private original fixtures are optional and never packaged");
        Map<String, String> sources = new LinkedHashMap<>();
        for (String file : List.of("basics", "auromancy", "alchemy", "artifice", "infusion", "golemancy", "eldritch", "scans")) {
            Path path = directory.resolve(file + ".json");
            assertTrue(Files.isRegularFile(path), path.toString());
            sources.put(file + ".json", Files.readString(path, StandardCharsets.UTF_8));
        }
        ResearchCatalogSpec spec = ResearchCatalogParser.parse(sources, CATEGORIES);
        assertEquals(8, spec.sources().size());
        assertEquals(148, spec.entries().size());
        assertEquals(271, spec.entries().stream().mapToInt(entry -> entry.stages().values().size()).sum());
        assertEquals(16, spec.entries().stream().mapToInt(entry -> entry.addenda().values().size()).sum());
        List<ResearchCatalogSpec.ItemToken> requirements = new ArrayList<>();
        List<ResearchCatalogSpec.LegacyResourceId> recipes = new ArrayList<>();
        for (Entry entry : spec.entries()) {
            for (var stage : entry.stages().values()) {
                requirements.addAll(stage.requiredItems().values());
                requirements.addAll(stage.requiredCraft().values());
                recipes.addAll(stage.recipes().values());
            }
            for (var addendum : entry.addenda().values()) recipes.addAll(addendum.recipes().values());
        }
        assertEquals(103, requirements.size());
        assertEquals(253, recipes.size());
        assertEquals(93, requirements.stream().map(ResearchCatalogSpec.ItemToken::raw).distinct().count());
        assertEquals(203, recipes.stream().map(ResearchCatalogSpec.LegacyResourceId::normalized).distinct().count());
        assertEquals(2, requirements.stream().filter(item -> item.name().equals("minecraft:thaumcraft:enchanted_placeholder")).count());
        assertEquals(2, requirements.stream().filter(item -> item.kind() == ItemKind.ITEM && !item.legacyId().modernSyntax()).count());
        var typoStages = spec.entries().stream().flatMap(entry -> entry.stages().values().stream())
            .filter(stage -> stage.unknownFields().containsKey("required_knowledgeauram")).toList();
        assertEquals(1, typoStages.size());
        assertFalse(typoStages.getFirst().requiredKnowledge().present());
        assertEquals(1, spec.diagnostics().stream().filter(diagnostic -> diagnostic.code().equals("UNKNOWN_FIELD")).count());
        assertEquals(sources, spec.sources());
    }

    private static ResearchCatalogSpec parse(JsonObject... entries) {
        return ResearchCatalogParser.parse(Map.of("test.json", document(entries)), CATEGORIES);
    }

    private static JsonObject entry(String key, String category, int column, int row) {
        JsonObject entry = new JsonObject();
        entry.addProperty("key", key);
        entry.addProperty("name", "research." + key + ".title");
        entry.addProperty("category", category);
        JsonArray location = new JsonArray();
        location.add(column);
        location.add(row);
        entry.add("location", location);
        JsonObject stage = new JsonObject();
        stage.addProperty("text", "stage.text");
        JsonArray stages = new JsonArray();
        stages.add(stage);
        entry.add("stages", stages);
        return entry;
    }

    private static JsonObject stage(JsonObject entry) { return entry.getAsJsonArray("stages").get(0).getAsJsonObject(); }

    private static JsonArray strings(String... values) {
        JsonArray result = new JsonArray();
        for (String value : values) result.add(value);
        return result;
    }

    private static String document(JsonObject... entries) {
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (JsonObject entry : entries) array.add(entry);
        root.add("entries", array);
        return root.toString();
    }
}
