package thaumcraft.common.lib.research.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import thaumcraft.api.research.ResearchAddendum;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.lib.research.catalog.PreparedResearchMetadata.DependencyKind;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ArrayField;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemToken;

/** Optional external private fixture; never loads/publishes a runtime catalog or materializes items. */
class OriginalResearchCatalogComparisonTest {
    private static final String PROPERTY = "thaumcraft.originalResearchCatalogFixtures";
    private static final String ORIGINAL_SHA256 = "9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f";
    private static final Gson JSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    private static final String[] ENTRY_METADATA = {"registryKey", "key", "category", "name", "parents", "parentsStripped",
        "parentsClean", "siblings", "meta", "displayColumn", "displayRow", "rewardKnow"};
    private static final String[] STAGE_METADATA = {"text", "recipes", "know", "research", "researchIcon", "warp"};
    private static final String[] ADDENDUM_METADATA = {"text", "recipes", "research"};
    private static final String[] CATEGORY_METADATA = {"registryKey", "key", "researchKey", "icon", "background", "background2",
        "minDisplayColumn", "minDisplayRow", "maxDisplayColumn", "maxDisplayRow", "formulaInOriginalOrder", "entryCount"};

    @Test
    void detachedMetadataMatchesExecutedOriginalWhileRegistryDependenciesRemainExplicit() throws Exception {
        String setting = System.getProperty(PROPERTY);
        Assumptions.assumeTrue(setting != null && !setting.isBlank(), "Private original catalog fixture is opt-in");
        Path fixture = Path.of(setting).toAbsolutePath().normalize().toRealPath();
        assertTrue(isPrivateReference(fixture), "Fixture/output must remain under an ignored reference/validation directory");
        assertTrue(Files.size(fixture) <= 8 * 1024 * 1024, "Bounded external fixture read");
        byte[] bytes = Files.readAllBytes(fixture);
        JsonObject original = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        validateOriginal(original);
        Comparison comparison = new Comparison(fixture, bytes);
        try {
            comparison.run(original);
        } catch (Exception | AssertionError failure) {
            comparison.report.addProperty("status", "failed");
            comparison.report.addProperty("failureClass", failure.getClass().getName());
            comparison.report.addProperty("failureMessageDiagnostic", failure.getMessage());
            comparison.write();
            throw failure;
        }
        comparison.report.addProperty("status", comparison.mismatches.isEmpty() ? "completed_metadata_only" : "metadata_mismatches");
        comparison.write();
        assertTrue(comparison.mismatches.isEmpty(), () -> "Original metadata mismatches: " + comparison.mismatches
            + "; private report " + comparison.output);
    }

    private static void validateOriginal(JsonObject original) {
        assertEquals(1, original.get("schemaVersion").getAsInt());
        assertEquals("completed", original.get("status").getAsString());
        assertTrue(original.get("runtimeCatalogUnchangedDuringCapture").getAsBoolean());
        JsonObject source = original.getAsJsonObject("source");
        assertEquals("1.12.2", source.get("mcVersion").getAsString());
        assertEquals("6.1.BETA26", source.get("tcVersion").getAsString());
        for (String hash : List.of("originalJarSha256Before", "installedJarSha256Before", "originalJarSha256After", "installedJarSha256After")) {
            assertEquals(ORIGINAL_SHA256, source.get(hash).getAsString(), hash);
        }
        JsonObject catalog = original.getAsJsonObject("catalog");
        assertEquals(7, catalog.get("categoryCount").getAsInt());
        assertEquals(148, catalog.get("entryCount").getAsInt());
        assertEquals(271, catalog.get("stageCount").getAsInt());
        assertEquals(16, catalog.get("addendumCount").getAsInt());
        assertEquals(8, original.get("sourceFileCount").getAsInt());
    }

    private static boolean isPrivateReference(Path fixture) {
        for (Path directory = fixture.getParent(); directory != null; directory = directory.getParent()) {
            Path parent = directory.getParent();
            if (directory.getFileName() != null && parent != null && parent.getFileName() != null
                && directory.getFileName().toString().equals("validation") && parent.getFileName().toString().equals("reference")) return true;
        }
        return false;
    }

    private static final class Comparison {
        private final Path output;
        private final JsonObject report = new JsonObject();
        private final JsonArray mismatches = new JsonArray();
        private final JsonArray deferred = new JsonArray();
        private final JsonArray dispositions = new JsonArray();
        private final Map<DependencyKind, Integer> dependencyCounts = new LinkedHashMap<>();
        private int categories, entries, stages, addenda, matchedCategories, matchedEntries, matchedStages, matchedAddenda;
        private int retainedRequirementTokens, retainedIconTokens, originalDroppedTokens;
        private final Map<String, Integer> originalIconKinds = new LinkedHashMap<>();

        Comparison(Path fixture, byte[] originalBytes) throws Exception {
            output = fixture.resolveSibling("modern-research-metadata-" + UUID.randomUUID() + ".json");
            report.addProperty("schemaVersion", 1);
            report.addProperty("startedUtc", Instant.now().toString());
            report.addProperty("scope", "Production detached parse/prepare metadata only; no publication, runtime hooks, registry materialization, progression, reload execution or client rendering");
            report.addProperty("originalFixturePathDiagnostic", fixture.toString());
            report.addProperty("originalFixtureSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(originalBytes)));
            report.addProperty("registryMaterializationCompared", false);
            report.addProperty("craftHashesCompared", false);
            report.addProperty("recipeIdentifierNormalizationCompared", true);
            report.addProperty("recipeRegistryResolutionCompared", false);
            report.addProperty("iconResolutionCompared", false);
            report.add("mismatches", mismatches);
            report.add("deferredDependencies", deferred);
            report.add("sourceBugDispositions", dispositions);
        }

        void run(JsonObject original) {
            Map<String, String> sources = new LinkedHashMap<>();
            Map<String, JsonObject> sourceEntries = new LinkedHashMap<>();
            JsonArray originalUnknowns = new JsonArray();
            for (JsonElement value : original.getAsJsonArray("sourceFilesInOriginalIterationOrder")) {
                JsonObject file = value.getAsJsonObject();
                String label = file.get("location").getAsString();
                assertNull(sources.put(label, file.get("documentPrivate").toString()), "Distinct original registration labels");
                for (JsonElement raw : file.getAsJsonObject("documentPrivate").getAsJsonArray("entries")) {
                    JsonObject entry = raw.getAsJsonObject();
                    assertNull(sourceEntries.put(entry.get("key").getAsString(), entry), "Distinct original research definitions");
                }
                for (JsonElement raw : file.getAsJsonArray("unknownFieldDiagnostics")) {
                    JsonObject unknown = raw.getAsJsonObject();
                    JsonObject position = new JsonObject();
                    position.addProperty("source", label);
                    position.addProperty("path", unknown.get("path").getAsString());
                    originalUnknowns.add(position);
                    JsonObject disposition = new JsonObject();
                    disposition.addProperty("kind", "UNKNOWN_ORIGINAL_FIELD_NOT_ACTIVATED");
                    disposition.addProperty("source", label);
                    disposition.addProperty("path", unknown.get("path").getAsString());
                    disposition.add("valuePrivate", unknown.get("valuePrivate"));
                    disposition.addProperty("meaning", "Original ignored this field; modern parser must retain diagnostic without repairing a requirement");
                    dispositions.add(disposition);
                }
            }
            var globalBefore = ResearchCategories.researchCategories;
            Map<String, ResearchCategory> detachedOrigin = new LinkedHashMap<>();
            ResearchCatalogSpec spec = ResearchCatalogParser.parse(sources, ResearchCategoryDefinitions.newBuiltinCategories().keySet());
            ResearchCatalogPublication.Candidate candidate = ResearchCatalogPublication.prepareWhole(spec, detachedOrigin, Set.of());
            assertSame(globalBefore, ResearchCategories.researchCategories, "Detached prepare never publishes a global catalog");
            assertTrue(detachedOrigin.isEmpty(), "Detached source map is unchanged");
            assertTrue(candidate.hasUnresolved(), "This baseline cannot establish registry-dependent gameplay acceptance");
            compare("source order", scalar(sources.keySet().toArray()), scalar(spec.sources().keySet().toArray()));
            JsonArray modernUnknowns = new JsonArray();
            for (var diagnostic : spec.diagnostics()) if (diagnostic.code().equals("UNKNOWN_FIELD")) {
                JsonObject position = new JsonObject();
                position.addProperty("source", diagnostic.source());
                position.addProperty("path", diagnostic.path());
                modernUnknowns.add(position);
            }
            compare("ignored original field positions", originalUnknowns, modernUnknowns);
            JsonObject catalog = original.getAsJsonObject("catalog");
            JsonArray originalCategories = catalog.getAsJsonArray("categoriesInRuntimeOrder");
            List<String> originalOrder = new ArrayList<>();
            for (JsonElement category : originalCategories) originalOrder.add(category.getAsJsonObject().get("registryKey").getAsString());
            compare("category order", scalar(originalOrder.toArray()), scalar(candidate.categories().keySet().toArray()));
            for (JsonElement value : originalCategories) {
                JsonObject expected = value.getAsJsonObject();
                String key = expected.get("registryKey").getAsString();
                ResearchCategory category = candidate.categories().get(key);
                assertNotNull(category, "Prepared original category " + key);
                categories++;
                if (compare("category " + key, projection(expected, CATEGORY_METADATA), categoryMetadata(key, category))) matchedCategories++;
                List<String> originalKeys = new ArrayList<>();
                for (JsonElement item : expected.getAsJsonArray("entriesSortedByOpaqueKey")) originalKeys.add(item.getAsJsonObject().get("registryKey").getAsString());
                compare("category research keys " + key, scalar(originalKeys.toArray()), scalar(category.research.keySet().stream().sorted().toArray()));
                for (JsonElement item : expected.getAsJsonArray("entriesSortedByOpaqueKey")) {
                    JsonObject originalEntry = item.getAsJsonObject();
                    String entryKey = originalEntry.get("registryKey").getAsString();
                    PreparedResearchMetadata.Entry modernEntry = candidate.entries().get(entryKey);
                    assertNotNull(modernEntry, "Prepared original research " + entryKey);
                    entries++;
                    if (compare("entry " + entryKey, projection(originalEntry, ENTRY_METADATA), entryMetadata(entryKey, modernEntry))) matchedEntries++;
                    retainIcons(sourceEntries.get(entryKey), originalEntry, modernEntry);
                    compareEntryArrays(originalEntry, modernEntry, sourceEntries.get(entryKey));
                    for (var dependency : modernEntry.deferredDependencies()) {
                        dependencyCounts.merge(dependency.kind(), 1, Integer::sum);
                        JsonObject diagnostic = new JsonObject();
                        diagnostic.addProperty("research", entryKey);
                        diagnostic.addProperty("kind", dependency.kind().name());
                        diagnostic.addProperty("path", dependency.path());
                        diagnostic.addProperty("reason", dependency.reason());
                        diagnostic.addProperty("materializationCompared", false);
                        deferred.add(diagnostic);
                    }
                }
            }
            assertSame(globalBefore, ResearchCategories.researchCategories);
            compare("category count", catalog.get("categoryCount"), JSON.toJsonTree(categories));
            compare("entry count", catalog.get("entryCount"), JSON.toJsonTree(entries));
            compare("stage count", catalog.get("stageCount"), JSON.toJsonTree(stages));
            compare("addendum count", catalog.get("addendumCount"), JSON.toJsonTree(addenda));
            compare("parsed definitions count", original.get("sourceEntryCount"), JSON.toJsonTree(spec.entries().size()));
            report.addProperty("comparedCategoryCount", categories);
            report.addProperty("matchedCategoryCount", matchedCategories);
            report.addProperty("comparedEntryCount", entries);
            report.addProperty("matchedEntryCount", matchedEntries);
            report.addProperty("comparedStageCount", stages);
            report.addProperty("matchedStageCount", matchedStages);
            report.addProperty("comparedAddendumCount", addenda);
            report.addProperty("matchedAddendumCount", matchedAddenda);
            report.addProperty("retainedRawRequirementTokenCount", retainedRequirementTokens);
            report.addProperty("retainedRawIconTokenCount", retainedIconTokens);
            report.add("originalIconKindCountsNotCompared", JSON.toJsonTree(originalIconKinds));
            report.addProperty("originalDiscardedRequirementTokenCount", originalDroppedTokens);
            report.addProperty("deferredDependencyCount", deferred.size());
            JsonObject counts = new JsonObject();
            dependencyCounts.forEach((kind, count) -> counts.addProperty(kind.name(), count));
            report.add("deferredDependencyCounts", counts);
            report.addProperty("unknownFieldDiagnosticCount", originalUnknowns.size());
            report.addProperty("mismatchCount", mismatches.size());
        }

        private void retainIcons(JsonObject source, JsonObject original, PreparedResearchMetadata.Entry modern) {
            assertNotNull(source);
            JsonArray raw = source.has("icons") ? source.getAsJsonArray("icons") : new JsonArray();
            JsonArray retained = new JsonArray();
            modern.rawSpec().icons().values().forEach(token -> retained.add(token.raw()));
            compare(modern.getKey() + " raw icon descriptor retention", raw, retained);
            retainedIconTokens += retained.size();
            if (retained.isEmpty()) assertNull(modern.getIcons());
            else {
                assertNotNull(modern.getIcons(), "Unresolved icons retain positional descriptors");
                assertEquals(retained.size(), modern.getIcons().length);
            }
            JsonElement originalIcons = original.get("icons");
            if (!originalIcons.isJsonNull()) for (JsonElement icon : originalIcons.getAsJsonArray()) {
                if (!icon.isJsonNull()) originalIconKinds.merge(icon.getAsJsonObject().get("kind").getAsString(), 1, Integer::sum);
            }
        }

        private void compareEntryArrays(JsonObject original, PreparedResearchMetadata.Entry modern, JsonObject source) {
            String key = modern.getKey();
            assertNotNull(source, "Raw private definition " + key);
            JsonElement expectedStages = original.get("stages");
            ResearchStage[] actualStages = modern.getStages();
            compare(key + " stages null/length", length(expectedStages), nullableLength(actualStages));
            if (actualStages != null) stages += actualStages.length;
            if (!expectedStages.isJsonNull() && actualStages != null) for (int i = 0; i < Math.min(expectedStages.getAsJsonArray().size(), actualStages.length); i++) {
                JsonObject expected = expectedStages.getAsJsonArray().get(i).getAsJsonObject();
                PreparedResearchMetadata.Stage actual = (PreparedResearchMetadata.Stage) actualStages[i];
                if (compare(key + " stage " + i, projection(expected, STAGE_METADATA), stageMetadata(actual))) matchedStages++;
                assertNull(actual.getCraftReference(), "Original craft hashes must remain explicitly unresolved");
                JsonObject raw = source.getAsJsonArray("stages").get(i).getAsJsonObject();
                retainRequirements(key, i, "required_item", "obtain", raw, expected, actual.rawSpec().requiredItems(), actual.getObtain());
                retainRequirements(key, i, "required_craft", "craft", raw, expected, actual.rawSpec().requiredCraft(), actual.getCraft());
            }
            JsonElement expectedAddenda = original.get("addenda");
            ResearchAddendum[] actualAddenda = modern.getAddenda();
            compare(key + " addenda null/length", length(expectedAddenda), nullableLength(actualAddenda));
            if (actualAddenda != null) addenda += actualAddenda.length;
            if (!expectedAddenda.isJsonNull() && actualAddenda != null) for (int i = 0; i < Math.min(expectedAddenda.getAsJsonArray().size(), actualAddenda.length); i++) {
                if (compare(key + " addendum " + i, projection(expectedAddenda.getAsJsonArray().get(i).getAsJsonObject(), ADDENDUM_METADATA),
                    addendumMetadata(actualAddenda[i]))) matchedAddenda++;
            }
            // Even if a future original fixture has rewards, metadata preparation must not pretend they resolved.
            assertNull(modern.getRewardItem(), "Deferred rewards are not materialized ItemStacks");
            assertEquals(modern.rawSpec().rewardItems().values(), modern.deferredRewards());
        }

        private void retainRequirements(String key, int index, String rawField, String resolvedField, JsonObject sourceStage,
                                        JsonObject originalStage, ArrayField<ItemToken> field, Object[] preparedTokens) {
            JsonArray raw = sourceStage.has(rawField) ? sourceStage.getAsJsonArray(rawField) : new JsonArray();
            assertEquals(sourceStage.has(rawField), field.present(), "Source field presence must remain distinct");
            JsonArray retained = new JsonArray();
            for (ItemToken token : field.values()) retained.add(token.raw());
            compare(key + " stage " + index + " raw descriptor retention " + rawField, raw, retained);
            retainedRequirementTokens += retained.size();
            if (field.values().isEmpty()) assertNull(preparedTokens);
            else {
                assertNotNull(preparedTokens, "Nonempty unresolved requirements cannot disappear");
                assertEquals(field.values().size(), preparedTokens.length);
                for (int i = 0; i < preparedTokens.length; i++) assertEquals(field.values().get(i), preparedTokens[i]);
            }
            JsonElement resolved = originalStage.get(resolvedField);
            int originalCount = resolved.isJsonNull() ? 0 : resolved.getAsJsonArray().size();
            if (raw.size() > originalCount) {
                originalDroppedTokens += raw.size() - originalCount;
                JsonObject disposition = new JsonObject();
                disposition.addProperty("kind", "ORIGINAL_DISCARDED_REQUIREMENTS_MODERN_RETAINS_BLOCKED");
                disposition.addProperty("research", key);
                disposition.addProperty("stageIndex", index);
                disposition.addProperty("field", rawField);
                disposition.addProperty("sourceTokenCount", raw.size());
                disposition.addProperty("originalResolvedSelectorCount", originalCount);
                disposition.addProperty("retainedUnresolvedTokenCount", retained.size());
                disposition.add("sourceTokensPrivate", raw);
                disposition.addProperty("meaning", "Deliberate fail-closed adaptation, not exact original item/requirement materialization parity");
                dispositions.add(disposition);
            }
        }

        private boolean compare(String location, JsonElement expected, JsonElement actual) {
            if (expected.equals(actual)) return true;
            JsonObject mismatch = new JsonObject();
            mismatch.addProperty("location", location);
            mismatch.add("expectedPrivate", expected);
            mismatch.add("actualPrivate", actual);
            mismatches.add(mismatch);
            return false;
        }

        private void write() throws Exception {
            report.addProperty("finishedUtc", Instant.now().toString());
            Files.writeString(output, JSON.toJson(report), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            System.out.println("Original research metadata replay: " + report.get("status") + ", " + categories + " categories / "
                + entries + " entries / " + stages + " stages / " + addenda + " addenda, " + mismatches.size()
                + " metadata mismatches; registry materialization NOT compared. Private report: " + output);
        }
    }

    private static JsonObject categoryMetadata(String registryKey, ResearchCategory category) {
        JsonObject result = new JsonObject();
        result.addProperty("registryKey", registryKey);
        result.addProperty("key", category.key);
        result.add("researchKey", JSON.toJsonTree(category.researchKey));
        result.add("icon", resource(category.icon));
        result.add("background", resource(category.background));
        result.add("background2", resource(category.background2));
        result.addProperty("minDisplayColumn", category.minDisplayColumn);
        result.addProperty("minDisplayRow", category.minDisplayRow);
        result.addProperty("maxDisplayColumn", category.maxDisplayColumn);
        result.addProperty("maxDisplayRow", category.maxDisplayRow);
        if (category.formula == null) result.add("formulaInOriginalOrder", JsonNull.INSTANCE);
        else {
            JsonArray formula = new JsonArray();
            for (var aspect : category.formula.getAspects()) {
                JsonObject value = new JsonObject();
                value.add("aspect", aspect == null ? JsonNull.INSTANCE : JSON.toJsonTree(aspect.getTag()));
                value.addProperty("amount", category.formula.getAmount(aspect));
                formula.add(value);
            }
            result.add("formulaInOriginalOrder", formula);
        }
        result.addProperty("entryCount", category.research.size());
        return result;
    }

    private static JsonObject entryMetadata(String registryKey, ResearchEntry entry) {
        JsonObject result = new JsonObject();
        result.addProperty("registryKey", registryKey);
        result.addProperty("key", entry.getKey());
        result.addProperty("category", entry.getCategory());
        result.addProperty("name", entry.getName());
        result.add("parents", scalar(entry.getParents()));
        result.add("parentsStripped", scalar(entry.getParentsStripped()));
        result.add("parentsClean", scalar(entry.getParentsClean()));
        result.add("siblings", scalar(entry.getSiblings()));
        result.add("meta", scalar(entry.getMeta()));
        result.addProperty("displayColumn", entry.getDisplayColumn());
        result.addProperty("displayRow", entry.getDisplayRow());
        result.add("rewardKnow", knowledge(entry.getRewardKnow()));
        return result;
    }

    private static JsonObject stageMetadata(ResearchStage stage) {
        JsonObject result = new JsonObject();
        result.addProperty("text", stage.getText());
        result.add("recipes", scalar(stage.getRecipes()));
        result.add("know", knowledge(stage.getKnow()));
        result.add("research", scalar(stage.getResearch()));
        result.add("researchIcon", scalar(stage.getResearchIcon()));
        result.addProperty("warp", stage.getWarp());
        return result;
    }

    private static JsonObject addendumMetadata(ResearchAddendum addendum) {
        JsonObject result = new JsonObject();
        result.addProperty("text", addendum.getText());
        result.add("recipes", scalar(addendum.getRecipes()));
        result.add("research", scalar(addendum.getResearch()));
        return result;
    }

    private static JsonElement knowledge(ResearchStage.Knowledge[] values) {
        if (values == null) return JsonNull.INSTANCE;
        JsonArray result = new JsonArray();
        for (var knowledge : values) {
            if (knowledge == null) { result.add(JsonNull.INSTANCE); continue; }
            JsonObject value = new JsonObject();
            value.add("type", knowledge.type == null ? JsonNull.INSTANCE : JSON.toJsonTree(knowledge.type.name()));
            value.add("category", knowledge.category == null ? JsonNull.INSTANCE : JSON.toJsonTree(knowledge.category.key));
            value.addProperty("amount", knowledge.amount);
            result.add(value);
        }
        return result;
    }

    private static JsonElement scalar(Object values) {
        if (values == null) return JsonNull.INSTANCE;
        JsonArray result = new JsonArray();
        for (int i = 0; i < Array.getLength(values); i++) {
            Object value = Array.get(values, i);
            if (value == null) result.add(JsonNull.INSTANCE);
            else if (value instanceof Enum<?> enumeration) result.add(enumeration.name());
            else if (value instanceof ResourceLocation || value instanceof String) result.add(value.toString());
            else if (value instanceof Number number) result.add(number);
            else throw new IllegalArgumentException("Unsupported metadata scalar " + value.getClass().getName());
        }
        return result;
    }

    private static JsonElement resource(ResourceLocation value) {
        return value == null ? JsonNull.INSTANCE : JSON.toJsonTree(value.toString());
    }

    private static JsonObject projection(JsonObject value, String[] fields) {
        JsonObject result = new JsonObject();
        for (String field : fields) {
            if (!value.has(field)) throw new IllegalArgumentException("Incomplete original metadata field " + field);
            result.add(field, value.get(field));
        }
        return result;
    }

    private static JsonElement length(JsonElement array) {
        return array.isJsonNull() ? JsonNull.INSTANCE : JSON.toJsonTree(array.getAsJsonArray().size());
    }

    private static JsonElement nullableLength(Object array) {
        return array == null ? JsonNull.INSTANCE : JSON.toJsonTree(Array.getLength(array));
    }
}
