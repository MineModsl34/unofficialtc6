package thaumcraft.gametest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.lib.TCAttachments;
import thaumcraft.common.lib.capabilities.PlayerKnowledge;
import thaumcraft.common.lib.capabilities.PlayerWarp;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;
import thaumcraft.common.lib.network.playerdata.PacketSyncWarp;

/**
 * Explicitly opt-in replay of the private, executed-original player-storage report.
 * Neither the report nor the original runtime research catalog is installed as a mod resource.
 * FakePlayers remain unspawned; packet construction/codec roundtrips do not send any network data.
 */
@GameTestHolder(Thaumcraft.MODID)
public final class OriginalPlayerDataComparisonGameTests {
    private static final Gson JSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    private static final String ORIGINAL_HASH = "9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f";
    private static final long MAX_BASELINE_BYTES = 8L * 1024 * 1024;
    private static final int EXPECTED_CASES = 79;
    private static final Set<String> KINDS = Set.of("knowledge", "warp", "wrapper", "knowledge_packet", "warp_packet");
    private static final Set<String> OPERATIONS = Set.of("deserialize", "serialize", "roundtrip", "copy", "snapshot",
        "snapshotNbt", "captureResearchView", "capturedResearchView", "mutateResearchView", "knowsResearch",
        "knowsResearchStrict", "constructPacket", "packetNbt", "clear", "getResearchList", "getCounter",
        "setCounter", "set", "add", "reduce", "get", "setResearchStage", "setResearchFlag", "clearResearchFlag",
        "hasResearchFlag", "addKnowledge", "getKnowledge", "getKnowledgeRaw", "addResearch", "removeResearch",
        "isResearchKnown", "isResearchComplete", "getResearchStatus", "getResearchStage");

    public OriginalPlayerDataComparisonGameTests() {}

    @GameTestGenerator
    public static Collection<TestFunction> optionalOriginalPlayerDataComparison() {
        String fixture = System.getProperty("thaumcraft.originalPlayerDataFixtures");
        if (fixture == null || fixture.isBlank()) {
            return List.of();
        }
        return List.of(new TestFunction("thaumcraft_original_player_reference",
            "thaumcraft.original_player_data_runtime_comparison", "thaumcraft:empty", 1200, 0L, true,
            helper -> compare(helper, Path.of(fixture))));
    }

    private static void compare(GameTestHelper helper, Path fixture) {
        Path baseline = fixture.toAbsolutePath().normalize();
        Results results = new Results(baseline);
        try {
            require(helper.getLevel().getServer().isSameThread(), "Scoped replay must run synchronously on the server owning thread");
            require(Files.isRegularFile(baseline) && Files.size(baseline) <= MAX_BASELINE_BYTES,
                "Original player fixture must be a bounded regular file");
            byte[] bytes = Files.readAllBytes(baseline);
            String baselineHash = hash(bytes);
            results.report.addProperty("baselineSha256", baselineHash);
            JsonObject original = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            validateBaseline(original, results);
            var originalCatalog = ResearchCategories.researchCategories;
            var originalMetadata = ResearchCatalogTestScope.snapshot();
            results.report.addProperty("productionCatalogCategoriesBeforeReplay", originalMetadata.categoryCount());
            results.report.addProperty("productionCatalogEntriesBeforeReplay", originalMetadata.entryCount());
            JsonArray cases = original.getAsJsonArray("cases");
            JsonArray synthetic = original.getAsJsonArray("syntheticCatalog");
            for (int index = 0; index < cases.size(); index++) {
                JsonObject row = cases.get(index).getAsJsonObject();
                String name = row.get("name").getAsString();
                results.casesAttempted++;
                try (var scope = ResearchCatalogTestScope.install(prepareCaseCatalog(row, synthetic), originalMetadata)) {
                    results.check(name + "/syntheticCatalogHasNoProductionMutableAliases", JSON.toJsonTree(true), JSON.toJsonTree(scope.detached()));
                    replayCase(helper, index, row, results);
                } catch (Exception | LinkageError failure) {
                    // Infrastructure failures are report failures, never expected native API outcomes.
                    results.infrastructure("case:" + name, failure);
                } finally {
                    ResearchCategories.researchCategories = originalCatalog;
                    results.check(name + "/productionCatalogIdentityAndMetadataRestored", JSON.toJsonTree(true), JSON.toJsonTree(originalMetadata.restored()));
                }
                // Completion is recorded by replayCase; scope/metadata failures remain explicit report failures.
            }
            results.check("productionCatalogRestoredAfterReplay", JSON.toJsonTree(true),
                JSON.toJsonTree(originalMetadata.restored()));
            results.check("allCasesAttempted", JSON.toJsonTree(EXPECTED_CASES), JSON.toJsonTree(results.casesAttempted));
            results.check("allCasesCompleted", JSON.toJsonTree(EXPECTED_CASES), JSON.toJsonTree(results.casesCompleted));
            results.check("allOriginalNativeExceptionsCompared", original.get("recordedNativeExceptionCount"),
                JSON.toJsonTree(results.originalNativeExceptions));
            results.check("modernNativeExceptionCount", original.get("recordedNativeExceptionCount"),
                JSON.toJsonTree(results.modernNativeExceptions));
            results.check("allOperationsCompared", results.report.get("originalOperationCount"), JSON.toJsonTree(results.operationsCompared));
            results.check("allQueriesCompared", results.report.get("originalQueryCount"), JSON.toJsonTree(results.queriesCompared));
            results.check("allPacketConstructorsExecuted", results.report.get("originalPacketConstructorCount"), JSON.toJsonTree(results.packetsConstructed));
            results.check("allPacketNbtQueriesUsedCodec", results.report.get("originalPacketNbtQueryCount"), JSON.toJsonTree(results.packetCodecRoundtrips));
            results.check("baselineUnchanged", JSON.toJsonTree(baselineHash), JSON.toJsonTree(hash(Files.readAllBytes(baseline))));
        } catch (Exception | LinkageError failure) {
            results.infrastructure("replayInitializationOrFinalCoverage", failure);
        }
        results.finish(helper);
    }

    private static void validateBaseline(JsonObject original, Results results) throws ReplayInfrastructureException {
        require(original.get("schemaVersion").getAsInt() == 1 && original.get("status").getAsString().equals("completed"),
            "Expected a completed schemaVersion1 original player audit");
        JsonObject source = original.getAsJsonObject("source");
        require(source.get("mcVersion").getAsString().equals("1.12.2") && source.get("tcVersion").getAsString().equals("6.1.BETA26"),
            "Expected the exact reviewed original version");
        for (String key : List.of("originalJarSha256Before", "originalJarSha256After", "installedJarSha256After")) {
            require(source.get(key).getAsString().equals(ORIGINAL_HASH), "Unreviewed original hash: " + key);
        }
        JsonArray cases = original.getAsJsonArray("cases");
        require(cases.size() == EXPECTED_CASES && original.get("caseCount").getAsInt() == EXPECTED_CASES,
            "Replay requires every one of the 79 reviewed cases");
        Set<String> names = new HashSet<>();
        int originalExceptions = 0;
        int operationCount = 0, queryCount = 0, packetConstructors = 0, packetNbtQueries = 0;
        for (JsonElement element : cases) {
            JsonObject row = element.getAsJsonObject();
            require(names.add(row.get("name").getAsString()), "Duplicate fixture name");
            require(KINDS.contains(row.get("kind").getAsString()), "Unreviewed fixture kind");
            require(row.has("initialNbt") && row.has("finalCanonical") && row.has("finalNbt"), "Incomplete case state");
            JsonArray operations = row.getAsJsonArray("operations");
            JsonArray outcomes = row.getAsJsonArray("outcomes");
            JsonArray queries = row.getAsJsonArray("queries");
            require(operations.size() <= 512 && queries.size() <= 512 && operations.size() == outcomes.size(), "Incomplete operation outcomes");
            operationCount += operations.size();
            queryCount += queries.size();
            if (!row.get("initialNbt").isJsonNull()) {
                require(row.has("initialOutcome"), "Missing initial-load outcome");
                validateOutcome(row.getAsJsonObject("initialOutcome"));
                if (row.getAsJsonObject("initialOutcome").has("exceptionClass")) originalExceptions++;
            }
            for (int index = 0; index < operations.size(); index++) {
                validateOperation(operations.get(index).getAsJsonObject());
                String op = operations.get(index).getAsJsonObject().get("op").getAsString();
                if (op.equals("constructPacket")) packetConstructors++;
                if (op.equals("packetNbt")) packetNbtQueries++;
                JsonObject outcome = outcomes.get(index).getAsJsonObject();
                require(outcome.get("index").getAsInt() == index, "Non-parallel operation index");
                validateOutcome(outcome);
                if (outcome.has("exceptionClass")) originalExceptions++;
            }
            for (JsonElement query : queries) {
                JsonObject outcome = query.getAsJsonObject();
                validateOperation(outcome.getAsJsonObject("operation"));
                String op = outcome.getAsJsonObject("operation").get("op").getAsString();
                if (op.equals("constructPacket")) packetConstructors++;
                if (op.equals("packetNbt")) packetNbtQueries++;
                validateOutcome(outcome);
                if (outcome.has("exceptionClass")) originalExceptions++;
            }
        }
        require(originalExceptions == original.get("recordedNativeExceptionCount").getAsInt(), "Original native-exception count is inconsistent");
        results.report.addProperty("originalCaseCount", cases.size());
        results.report.addProperty("originalRecordedNativeExceptionCount", originalExceptions);
        results.report.addProperty("originalOperationCount", operationCount);
        results.report.addProperty("originalQueryCount", queryCount);
        results.report.addProperty("originalPacketConstructorCount", packetConstructors);
        results.report.addProperty("originalPacketNbtQueryCount", packetNbtQueries);
        validateOriginalCatalog(original.getAsJsonObject("catalogMetadata"), results);
        require(original.getAsJsonArray("syntheticCatalog").size() == 8, "Expected seven regular and one opt-in auto-unlock descriptors");
    }

    private static void validateOriginalCatalog(JsonObject metadata, Results results) throws ReplayInfrastructureException {
        JsonArray categories = metadata.getAsJsonArray("categoriesInRuntimeOrder");
        JsonArray categoryOrder = new JsonArray();
        Set<String> categoryKeys = new HashSet<>();
        int entries = 0, stages = 0, auto = 0;
        for (JsonElement element : categories) {
            JsonObject category = element.getAsJsonObject();
            String key = category.get("key").getAsString();
            require(categoryKeys.add(key), "Duplicate original category key");
            categoryOrder.add(key);
            for (JsonElement research : category.getAsJsonArray("entries")) {
                JsonObject entry = research.getAsJsonObject();
                int count = entry.get("stageCount").getAsInt();
                require(count >= 0 && (!entry.get("stagesNull").getAsBoolean() || count == 0), "Invalid original stage metadata");
                stages += count;
                entries++;
                for (JsonElement meta : entry.getAsJsonArray("meta")) {
                    if (meta.getAsString().equals("AUTOUNLOCK")) auto++;
                }
            }
        }
        results.check("originalCatalogEntryCount", metadata.get("entryCount"), JSON.toJsonTree(entries));
        results.check("originalCatalogStageCount", metadata.get("stageCount"), JSON.toJsonTree(stages));
        results.check("originalCatalogAutoUnlockCount", metadata.get("autoUnlockEntries"), JSON.toJsonTree(auto));
        results.check("noRealCatalogAutoUnlockRequiredForThisReplay", JSON.toJsonTree(0), JSON.toJsonTree(auto));
        require(auto == 0, "Nonzero real-catalog AUTOUNLOCK needs a separately reviewed replay scope");
        results.report.add("originalCategoryOrder", categoryOrder);
        results.report.addProperty("originalCatalogEntryCount", entries);
        results.report.addProperty("originalCatalogStageCount", stages);
        results.report.addProperty("originalCatalogAutoUnlockEntries", auto);
        results.report.addProperty("originalCatalogInstalled", false);
    }

    private static void validateOperation(JsonObject operation) throws ReplayInfrastructureException {
        String op = operation.get("op").getAsString();
        require(OPERATIONS.contains(op), "Unreviewed replay operation: " + op);
        if (Set.of("setCounter", "set", "add", "reduce", "addKnowledge").contains(op)) {
            require(operation.has("amount") && operation.get("amount").isJsonPrimitive()
                && operation.get("amount").getAsJsonPrimitive().isNumber(), "Missing numeric amount for " + op);
        }
        if (op.equals("setResearchStage")) require(operation.has("stage"), "Missing stage");
        if (op.equals("deserialize")) require(operation.has("nbt"), "Missing explicit nullable NBT input");
        if (op.equals("knowsResearch") || op.equals("knowsResearchStrict")) require(operation.has("keys")
            && operation.get("keys").isJsonArray(), "Missing wrapper argument array");
    }

    private static void validateOutcome(JsonObject outcome) throws ReplayInfrastructureException {
        require(outcome.has("result") != outcome.has("exceptionClass"), "An outcome must be a value or native exception, never both/neither");
    }

    private static LinkedHashMap<String, ResearchCategory> prepareCaseCatalog(JsonObject fixture, JsonArray descriptors)
        throws ReplayInfrastructureException {
        LinkedHashMap<String, ResearchCategory> catalog = new LinkedHashMap<>();
        List<JsonObject> operations = new ArrayList<>();
        for (JsonElement element : fixture.getAsJsonArray("operations")) operations.add(element.getAsJsonObject());
        for (JsonElement element : fixture.getAsJsonArray("queries")) operations.add(element.getAsJsonObject().getAsJsonObject("operation"));
        boolean needsResearch = fixture.has("syntheticAutoUnlock") && fixture.get("syntheticAutoUnlock").getAsBoolean();
        for (JsonObject operation : operations) {
            String op = operation.get("op").getAsString();
            if (op.contains("Research") || op.contains("Flag")) needsResearch = true;
            String category = nullableString(operation, "category");
            if (category != null) {
                // Category-only arithmetic adapter, not any original catalog entries/formulas/progression.
                require(Set.of("BASICS", "MiXeD", "TC_PORT_AUDIT").contains(category), "Unreviewed category-only adapter: " + category);
                catalog.computeIfAbsent(category, key -> new ResearchCategory(key, null, null, null, null));
            }
        }
        if (needsResearch) {
            boolean enableAuto = fixture.has("syntheticAutoUnlock") && fixture.get("syntheticAutoUnlock").getAsBoolean();
            for (JsonElement raw : descriptors) {
                JsonObject descriptor = raw.getAsJsonObject();
                if (descriptor.has("onlyWhenCaseEnablesSyntheticAutoUnlock") && !enableAuto) continue;
                String categoryKey = descriptor.get("category").getAsString();
                require(categoryKey.equals("TC_PORT_AUDIT"), "Unreviewed synthetic research category");
                ResearchCategory category = catalog.computeIfAbsent(categoryKey, key -> new ResearchCategory(key, null, null, null, null));
                ResearchEntry entry = new ResearchEntry();
                entry.setKey(descriptor.get("key").getAsString());
                entry.setCategory(categoryKey);
                int count = descriptor.get("stageCount").getAsInt();
                require(count >= 0 && count <= 32, "Unbounded synthetic stage count");
                if (!descriptor.get("stagesNull").getAsBoolean()) {
                    ResearchStage[] stages = new ResearchStage[count];
                    for (int index = 0; index < count; index++) stages[index] = new ResearchStage();
                    entry.setStages(stages);
                }
                JsonArray metas = descriptor.getAsJsonArray("meta");
                ResearchEntry.EnumResearchMeta[] values = new ResearchEntry.EnumResearchMeta[metas.size()];
                for (int index = 0; index < metas.size(); index++) values[index] = ResearchEntry.EnumResearchMeta.valueOf(metas.get(index).getAsString());
                entry.setMeta(values);
                require(category.research.put(entry.getKey(), entry) == null, "Duplicate synthetic research descriptor");
            }
        }
        return catalog;
    }

    private static void replayCase(GameTestHelper helper, int index, JsonObject fixture, Results results) throws Exception {
        String name = fixture.get("name").getAsString();
        JsonObject actual = new JsonObject();
        actual.addProperty("name", name);
        actual.addProperty("kind", fixture.get("kind").getAsString());
        actual.addProperty("scopedCategoryCount", ResearchCategories.researchCategories.size());
        Session session = new Session(helper, index, fixture.get("kind").getAsString(), results);
        if (!fixture.get("initialNbt").isJsonNull()) {
            JsonObject load = new JsonObject();
            load.addProperty("op", "deserialize");
            load.add("nbt", fixture.get("initialNbt").deepCopy());
            JsonObject outcome = outcome(session, load);
            actual.add("initialOutcome", outcome);
            results.compareOutcome(name + "/initialLoad", fixture.getAsJsonObject("initialOutcome"), outcome, session, load);
        } else {
            actual.addProperty("initialMode", "fresh production factory or attached player data: no deserialize call");
        }
        JsonArray outcomes = new JsonArray();
        JsonArray operations = fixture.getAsJsonArray("operations");
        for (int operationIndex = 0; operationIndex < operations.size(); operationIndex++) {
            JsonObject operation = operations.get(operationIndex).getAsJsonObject();
            JsonObject outcome = outcome(session, operation);
            results.compareOutcome(name + "/operation:" + operationIndex, fixture.getAsJsonArray("outcomes").get(operationIndex).getAsJsonObject(),
                outcome, session, operation);
            outcome.addProperty("index", operationIndex);
            outcome.add("operation", operation.deepCopy());
            outcomes.add(outcome);
            results.operationsCompared++;
        }
        actual.add("outcomes", outcomes);
        JsonArray queries = new JsonArray();
        int queryIndex = 0;
        for (JsonElement raw : fixture.getAsJsonArray("queries")) {
            JsonObject expected = raw.getAsJsonObject();
            JsonObject operation = expected.getAsJsonObject("operation");
            JsonObject outcome = outcome(session, operation);
            results.compareOutcome(name + "/query:" + queryIndex++, expected, outcome, session, operation);
            outcome.add("operation", operation.deepCopy());
            queries.add(outcome);
            results.queriesCompared++;
        }
        actual.add("queries", queries);
        CompoundTag finalNbt = session.serialize(false);
        JsonObject finalCanonical = canonical(finalNbt, session.warp);
        actual.addProperty("finalNbt", finalNbt.toString());
        actual.add("finalCanonical", finalCanonical);
        results.check(name + "/finalCanonical", fixture.get("finalCanonical"), finalCanonical);
        // Check both baseline representations rather than trusting finalCanonical independently.
        results.check(name + "/originalFinalCanonicalConsistent", fixture.get("finalCanonical"),
            canonical(TagParser.parseTag(fixture.get("finalNbt").getAsString()), session.warp));
        results.cases.add(actual);
        results.casesCompleted++;
    }

    private static JsonObject outcome(Session session, JsonObject operation) throws Exception {
        JsonObject result = new JsonObject();
        try {
            Object value = session.execute(operation);
            if (value instanceof CompoundTag tag) {
                JsonObject nbt = new JsonObject();
                nbt.addProperty("snbt", tag.toString());
                nbt.add("canonical", canonical(tag, session.warp));
                result.add("result", nbt);
            } else if (value instanceof Enum<?> enumeration) {
                result.addProperty("result", enumeration.name());
            } else if (value instanceof Set<?> set) {
                List<String> values = new ArrayList<>();
                for (Object item : set) values.add((String) item);
                Collections.sort(values);
                result.add("result", JSON.toJsonTree(values));
            } else {
                result.add("result", JSON.toJsonTree(value));
            }
        } catch (RuntimeException nativeFailure) {
            // No reflection here: these are actual production API/collection exceptions.
            result.addProperty("exceptionClass", nativeFailure.getClass().getName());
            result.addProperty("exceptionMessage", nativeFailure.getMessage());
            session.results.modernNativeExceptions++;
        }
        return result;
    }

    private static final class Session {
        final boolean warp;
        final Object source;
        final ServerPlayer player;
        final GameTestHelper helper;
        final Results results;
        Object copy;
        Set<String> view;
        CompoundTag snapshot;
        CustomPacketPayload packet;
        CompoundTag legacyPacketSnapshot;
        JsonObject initialPacketCanonical;

        Session(GameTestHelper helper, int index, String kind, Results results) throws ReplayInfrastructureException {
            this.helper = helper;
            this.results = results;
            warp = kind.startsWith("warp");
            if (kind.equals("wrapper") || kind.endsWith("packet")) {
                // Construct directly: not factory-cached, spawned, logged in, sent a packet or saved.
                player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TCaudit" + index));
                require(!helper.getLevel().getServer().getPlayerList().getPlayers().contains(player), "Audit player unexpectedly connected");
                source = warp ? ThaumcraftCapabilities.getWarp(player) : ThaumcraftCapabilities.getKnowledge(player);
                require(warp ? source == player.getData(TCAttachments.WARP) : source == player.getData(TCAttachments.KNOWLEDGE),
                    "Actual registered attachment required");
                results.attachedFakePlayers++;
            } else {
                player = null;
                source = newModel(warp);
            }
        }

        CompoundTag serialize(boolean fromCopy) throws ReplayInfrastructureException {
            Object receiver = fromCopy ? copy : source;
            require(receiver != null, "Copied model does not exist");
            return warp ? ((IPlayerWarp) receiver).serializeNBT() : ((IPlayerKnowledge) receiver).serializeNBT();
        }

        void deserialize(Object receiver, CompoundTag tag) {
            if (warp) ((IPlayerWarp) receiver).deserializeNBT(tag);
            else ((IPlayerKnowledge) receiver).deserializeNBT(tag);
        }

        Object execute(JsonObject operation) throws Exception {
            String op = operation.get("op").getAsString();
            boolean fromCopy = "copy".equals(nullableString(operation, "target"));
            Object receiver = fromCopy ? copy : source;
            require(receiver != null, "Copied model does not exist");
            String key = nullableString(operation, "key");
            switch (op) {
                case "deserialize" -> {
                    String nbt = nullableString(operation, "nbt");
                    deserialize(receiver, nbt == null ? null : TagParser.parseTag(nbt));
                    return null;
                }
                case "serialize" -> { return serialize(fromCopy); }
                case "roundtrip" -> { deserialize(receiver, serialize(fromCopy)); return null; }
                case "copy" -> {
                    copy = newModel(warp);
                    deserialize(copy, serialize(false));
                    return serialize(true);
                }
                case "snapshot" -> { snapshot = serialize(fromCopy); return null; }
                case "snapshotNbt" -> { return snapshot; }
                case "captureResearchView" -> { view = ((IPlayerKnowledge) receiver).getResearchList(); return view; }
                case "capturedResearchView" -> { return view; }
                case "mutateResearchView" -> { return view.add(key); }
                case "knowsResearch", "knowsResearchStrict" -> {
                    require(player != null, "Wrapper case needs actual attached FakePlayer");
                    JsonArray keys = operation.getAsJsonArray("keys");
                    String[] values = new String[keys.size()];
                    for (int index = 0; index < values.length; index++) values[index] = keys.get(index).isJsonNull() ? null : keys.get(index).getAsString();
                    return op.equals("knowsResearch") ? ThaumcraftCapabilities.knowsResearch(player, values)
                        : ThaumcraftCapabilities.knowsResearchStrict(player, values);
                }
                case "constructPacket" -> {
                    require(player != null, "Packet case needs actual attached FakePlayer");
                    legacyPacketSnapshot = serialize(false); // Source alias evidence, before any later mutator.
                    packet = warp ? PacketSyncWarp.fromPlayer(player) : PacketSyncKnowledge.fromPlayer(player);
                    initialPacketCanonical = canonical(warp ? ((PacketSyncWarp) packet).data() : ((PacketSyncKnowledge) packet).data(), warp);
                    results.packetsConstructed++;
                    return null;
                }
                case "packetNbt" -> {
                    require(packet != null, "Packet constructor must precede codec replay");
                    var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
                    try {
                        CompoundTag data;
                        if (warp) {
                            PacketSyncWarp.STREAM_CODEC.encode(buffer, (PacketSyncWarp) packet);
                            data = PacketSyncWarp.STREAM_CODEC.decode(buffer).data();
                        } else {
                            PacketSyncKnowledge.STREAM_CODEC.encode(buffer, (PacketSyncKnowledge) packet);
                            data = PacketSyncKnowledge.STREAM_CODEC.decode(buffer).data();
                        }
                        require(buffer.readableBytes() == 0, "Packet codec did not consume exactly its bytes");
                        results.packetCodecRoundtrips++;
                        return data;
                    } finally {
                        buffer.release();
                    }
                }
                case "clear" -> { if (warp) ((IPlayerWarp) receiver).clear(); else ((IPlayerKnowledge) receiver).clear(); return null; }
                case "getCounter" -> { return ((IPlayerWarp) receiver).getCounter(); }
                case "setCounter" -> { ((IPlayerWarp) receiver).setCounter(operation.get("amount").getAsInt()); return null; }
                case "get" -> { return ((IPlayerWarp) receiver).get(enumOrNull(EnumWarpType.class, nullableString(operation, "type"))); }
                case "set", "add", "reduce" -> {
                    IPlayerWarp data = (IPlayerWarp) receiver;
                    EnumWarpType type = enumOrNull(EnumWarpType.class, nullableString(operation, "type"));
                    int amount = operation.get("amount").getAsInt();
                    if (op.equals("set")) { data.set(type, amount); return null; }
                    return op.equals("add") ? data.add(type, amount) : data.reduce(type, amount);
                }
                case "getResearchList" -> { return ((IPlayerKnowledge) receiver).getResearchList(); }
                case "setResearchStage" -> { return ((IPlayerKnowledge) receiver).setResearchStage(key, operation.get("stage").getAsInt()); }
                case "setResearchFlag", "clearResearchFlag", "hasResearchFlag" -> {
                    IPlayerKnowledge data = (IPlayerKnowledge) receiver;
                    EnumResearchFlag flag = enumOrNull(EnumResearchFlag.class, nullableString(operation, "flag"));
                    return switch (op) {
                        case "setResearchFlag" -> data.setResearchFlag(key, flag);
                        case "clearResearchFlag" -> data.clearResearchFlag(key, flag);
                        default -> data.hasResearchFlag(key, flag);
                    };
                }
                case "addKnowledge", "getKnowledge", "getKnowledgeRaw" -> {
                    IPlayerKnowledge data = (IPlayerKnowledge) receiver;
                    EnumKnowledgeType type = enumOrNull(EnumKnowledgeType.class, nullableString(operation, "type"));
                    String categoryKey = nullableString(operation, "category");
                    ResearchCategory category = categoryKey == null ? null : ResearchCategories.getResearchCategory(categoryKey);
                    require(categoryKey == null || category != null, "Missing reviewed category-only arithmetic adapter");
                    return switch (op) {
                        case "addKnowledge" -> data.addKnowledge(type, category, operation.get("amount").getAsInt());
                        case "getKnowledge" -> data.getKnowledge(type, category);
                        default -> data.getKnowledgeRaw(type, category);
                    };
                }
                case "addResearch" -> { return ((IPlayerKnowledge) receiver).addResearch(key); }
                case "removeResearch" -> { return ((IPlayerKnowledge) receiver).removeResearch(key); }
                case "isResearchKnown" -> { return ((IPlayerKnowledge) receiver).isResearchKnown(key); }
                case "isResearchComplete" -> { return ((IPlayerKnowledge) receiver).isResearchComplete(key); }
                case "getResearchStatus" -> { return ((IPlayerKnowledge) receiver).getResearchStatus(key); }
                case "getResearchStage" -> { return ((IPlayerKnowledge) receiver).getResearchStage(key); }
                default -> throw new ReplayInfrastructureException("Unreviewed audit operation: " + op);
            }
        }

        boolean isProvenPacketSafetyDeviation(JsonObject expected, JsonObject actual, JsonObject operation) {
            if (!warp || !operation.get("op").getAsString().equals("packetNbt") || initialPacketCanonical == null
                || legacyPacketSnapshot == null || !expected.has("result") || !actual.has("result")) return false;
            JsonObject legacyNow = canonical(legacyPacketSnapshot, true);
            // Only an actual post-constructor mutation of the aliased array can justify this adaptation.
            return !legacyNow.get("warp").equals(initialPacketCanonical.get("warp"))
                && expected.getAsJsonObject("result").get("canonical").equals(legacyNow)
                && actual.getAsJsonObject("result").get("canonical").equals(initialPacketCanonical);
        }
    }

    private static Object newModel(boolean warp) { return warp ? new PlayerWarp() : new PlayerKnowledge(); }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String name) {
        return name == null ? null : Enum.valueOf(type, name);
    }

    private static String nullableString(JsonObject object, String key) {
        return !object.has(key) || object.get(key).isJsonNull() ? null : object.get(key).getAsString();
    }

    private static JsonObject canonical(CompoundTag tag, boolean warp) {
        JsonObject result = new JsonObject();
        if (warp) {
            result.add("warp", JSON.toJsonTree(tag.getIntArray("warp")));
            result.addProperty("counter", tag.getInt("counter"));
            return result;
        }
        for (String name : List.of("research", "knowledge")) {
            ListTag list = tag.getList(name, Tag.TAG_COMPOUND);
            List<JsonObject> rows = new ArrayList<>();
            for (int index = 0; index < list.size(); index++) {
                CompoundTag entry = list.getCompound(index);
                JsonObject row = new JsonObject();
                row.addProperty("key", entry.getString("key"));
                if (name.equals("knowledge")) row.addProperty("amount", entry.getInt("amount"));
                if (entry.contains("stage", Tag.TAG_INT)) row.addProperty("stage", entry.getInt("stage"));
                if (entry.contains("flags", Tag.TAG_STRING)) {
                    List<String> flags = new ArrayList<>();
                    for (String flag : entry.getString("flags").split(",")) if (!flag.isEmpty()) flags.add(flag);
                    Collections.sort(flags);
                    row.add("flags", JSON.toJsonTree(flags));
                }
                rows.add(row);
            }
            rows.sort(Comparator.comparing(row -> row.get("key").getAsString()));
            JsonArray ordered = new JsonArray();
            for (JsonObject row : rows) ordered.add(row);
            result.add(name, ordered);
        }
        return result;
    }

    private static JsonObject normalizedOutcome(JsonObject raw) throws ReplayInfrastructureException {
        validateOutcome(raw);
        JsonObject result = new JsonObject();
        if (raw.has("exceptionClass")) {
            result.add("exceptionClass", raw.get("exceptionClass").deepCopy());
        } else {
            JsonElement value = raw.get("result");
            if (value.isJsonObject() && value.getAsJsonObject().has("canonical")) {
                JsonObject nbt = new JsonObject();
                nbt.add("canonical", value.getAsJsonObject().get("canonical").deepCopy());
                result.add("result", nbt);
            } else {
                result.add("result", value.deepCopy());
            }
        }
        return result;
    }

    private static String hash(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void require(boolean condition, String message) throws ReplayInfrastructureException {
        if (!condition) throw new ReplayInfrastructureException(message);
    }

    private static final class ReplayInfrastructureException extends Exception {
        ReplayInfrastructureException(String message) { super(message); }
    }

    private static final class Results {
        final Path output;
        final JsonObject report = new JsonObject();
        final JsonArray checks = new JsonArray();
        final JsonArray cases = new JsonArray();
        int failureCount, exactMatchCount, safetyAdaptationCount, infrastructureFailureCount;
        int casesAttempted, casesCompleted, operationsCompared, queriesCompared;
        int originalNativeExceptions, modernNativeExceptions, attachedFakePlayers, packetsConstructed, packetCodecRoundtrips;

        Results(Path baseline) {
            output = baseline.resolveSibling("modern-player-data-comparison-" + UUID.randomUUID() + ".json");
            report.addProperty("schemaVersion", 1);
            report.addProperty("baseline", baseline.toString());
            report.addProperty("output", output.toString());
            report.addProperty("startedUtc", Instant.now().toString());
            report.addProperty("scope", "Actual production storage/API replay and registered attachments on unspawned FakePlayers, plus payload construction and modern STREAM_CODEC encode/decode. No real network send, client connection, login/clone lifecycle, notification UI or ResearchManager progression claim.");
            report.addProperty("normalization", "Canonical research/knowledge key ordering and flag ordering; exception CLASS exact, messages and raw SNBT ordering diagnostic only. Field absence is not collapsed into an empty field. Opaque input strings and duplicate order unchanged.");
            report.addProperty("catalogScope", "Real original catalog is validated as metadata only (AUTOUNLOCK must be zero), never installed. Any existing modern/API catalog is preserved by exact map/category/entry/array identities and public metadata contents. Every case uses freshly allocated nonaliasing synthetic descriptors/category-key arithmetic adapters, with synchronous finally restoration; no real entries are merged into fixture context.");
            report.addProperty("allowedSafetyAdaptation", "Only an actually changed post-constructor aliased warp array may differ in packetNbt, if old output equals the live raw snapshot and modern output equals the constructor-time detached snapshot. Raw storage snapshot must match exactly.");
        }

        void compareOutcome(String name, JsonObject expected, JsonObject actual, Session session, JsonObject operation)
            throws ReplayInfrastructureException {
            if (expected.has("exceptionClass")) originalNativeExceptions++;
            JsonObject normalizedExpected = normalizedOutcome(expected);
            JsonObject normalizedActual = normalizedOutcome(actual);
            if (!normalizedExpected.equals(normalizedActual) && session.isProvenPacketSafetyDeviation(expected, actual, operation)) {
                JsonObject row = comparisonRow(name, normalizedExpected, normalizedActual, false);
                row.addProperty("acceptedSafetyAdaptation", "detached packet warp array after proven post-constructor mutation");
                checks.add(row);
                safetyAdaptationCount++;
            } else {
                check(name, normalizedExpected, normalizedActual);
            }
        }

        void check(String name, JsonElement expected, JsonElement actual) {
            boolean matches = expected.equals(actual);
            checks.add(comparisonRow(name, expected, actual, matches));
            if (matches) exactMatchCount++;
            else failureCount++;
        }

        private JsonObject comparisonRow(String name, JsonElement expected, JsonElement actual, boolean matches) {
            JsonObject row = new JsonObject();
            row.addProperty("check", name);
            row.addProperty("exactMatch", matches);
            if (!matches) {
                row.add("expected", expected.deepCopy());
                row.add("actual", actual.deepCopy());
            }
            return row;
        }

        void infrastructure(String name, Throwable failure) {
            failureCount++;
            infrastructureFailureCount++;
            JsonObject row = new JsonObject();
            row.addProperty("check", name);
            row.addProperty("exactMatch", false);
            row.addProperty("infrastructureFailure", true);
            row.addProperty("exceptionClass", failure.getClass().getName());
            row.addProperty("error", failure.toString());
            checks.add(row);
        }

        void finish(GameTestHelper helper) {
            report.addProperty("finishedUtc", Instant.now().toString());
            report.addProperty("status", failureCount == 0 ? "completed" : "failed");
            report.addProperty("failureCount", failureCount);
            report.addProperty("infrastructureFailureCount", infrastructureFailureCount);
            report.addProperty("checkCount", checks.size());
            report.addProperty("exactMatchCount", exactMatchCount);
            report.addProperty("acceptedSafetyAdaptationCount", safetyAdaptationCount);
            report.addProperty("casesAttempted", casesAttempted);
            report.addProperty("casesCompleted", casesCompleted);
            report.addProperty("operationsCompared", operationsCompared);
            report.addProperty("queriesCompared", queriesCompared);
            report.addProperty("originalNativeExceptionsCompared", originalNativeExceptions);
            report.addProperty("modernNativeExceptionsRecorded", modernNativeExceptions);
            report.addProperty("attachedUnspawnedFakePlayers", attachedFakePlayers);
            report.addProperty("packetsConstructed", packetsConstructed);
            report.addProperty("packetCodecRoundtrips", packetCodecRoundtrips);
            report.add("checks", checks);
            report.add("cases", cases);
            try {
                Files.writeString(output, JSON.toJson(report), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                helper.assertTrue(failureCount == 0, "Executed-original player-data differences: " + failureCount + "; private report " + output);
                helper.succeed();
            } catch (Exception failure) {
                helper.fail("Executed-original player-data comparison failed: " + failure + "; private report " + output);
            }
        }
    }
}
