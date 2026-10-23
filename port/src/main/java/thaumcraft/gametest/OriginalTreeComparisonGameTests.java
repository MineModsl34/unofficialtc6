package thaumcraft.gametest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import thaumcraft.Thaumcraft;
import thaumcraft.common.world.features.TCGreatwoodTreeFeature;
import thaumcraft.common.world.features.TCSilverwoodTreeFeature;

/**
 * Optional exact fixture comparison to an executed original JAR, not a source-derived golden file.
 * No test is registered unless a private baseline path is explicitly supplied. The raw original
 * maps remain external/ignored and are never bundled into the mod or its default tests.
 */
@GameTestHolder(Thaumcraft.MODID)
public final class OriginalTreeComparisonGameTests {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int RADIUS = 20;

    public OriginalTreeComparisonGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> optionalReferenceComparison() {
        String baseline = System.getProperty("thaumcraft.originalTreeComparison");
        if (baseline == null || baseline.isBlank()) {
            return List.of();
        }
        return List.of(new TestFunction("thaumcraft_original_reference", "thaumcraft.original_tree_runtime_comparison",
            "thaumcraft:empty", 160, 0L, true, helper -> compare(helper, Path.of(baseline))));
    }

    private static void compare(GameTestHelper helper, Path source) {
        try {
            Path baseline = source.toAbsolutePath().normalize();
            Path output = baseline.resolveSibling("modern-tree-report.json");
            helper.assertFalse(Files.exists(output), "Reference comparison refuses to overwrite an existing modern report");
            JsonObject original = JsonParser.parseString(Files.readString(baseline, StandardCharsets.UTF_8)).getAsJsonObject();
            helper.assertTrue(original.get("status").getAsString().equals("completed"), "Original runtime report must have completed");
            helper.assertTrue(original.get("minecraft").getAsString().equals("1.12.2")
                && original.get("thaumcraft").getAsString().equals("6.1.BETA26"), "Reference version must be the requested TC6 build");
            JsonArray cases = original.getAsJsonArray("cases");
            helper.assertTrue(cases.size() == 22, "Expected 22 original generator fixtures");
            int oldRandomTicks = helper.getLevel().getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, helper.getLevel().getServer());
            JsonObject report = new JsonObject();
            report.addProperty("baseline", baseline.toString());
            report.addProperty("startedUtc", Instant.now().toString());
            report.addProperty("scope", "Actual port generators versus actual original JAR on identical controlled terrain; canonical geometry/RNG/loot seed, not visual or natural distribution acceptance");
            JsonArray results = new JsonArray();
            List<String> failures = new ArrayList<>();
            for (int index = 0; index < cases.size(); index++) {
                int caseIndex = index;
                helper.runAtTickTime(1L + index * 2L, () -> {
                    JsonObject expected = cases.get(caseIndex).getAsJsonObject();
                    try {
                        JsonObject result = runCase(helper.getLevel(), caseIndex, expected);
                        results.add(result);
                        if (!result.get("matches").getAsBoolean()) {
                            failures.add(expected.get("kind").getAsString() + " seed " + expected.get("seed").getAsLong());
                        }
                    } catch (Exception failure) {
                        JsonObject result = new JsonObject();
                        result.addProperty("kind", expected.get("kind").getAsString());
                        result.addProperty("seed", expected.get("seed").getAsLong());
                        result.addProperty("matches", false);
                        result.addProperty("error", failure.toString());
                        results.add(result);
                        failures.add(failure.toString());
                    }
                });
            }
            helper.runAtTickTime(2L + cases.size() * 2L, () -> {
                helper.getLevel().getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(oldRandomTicks, helper.getLevel().getServer());
                report.addProperty("finishedUtc", Instant.now().toString());
                report.addProperty("caseCount", results.size());
                report.addProperty("matchedCases", results.size() - failures.size());
                report.addProperty("status", failures.isEmpty() ? "completed" : "failed");
                report.add("cases", results);
                report.add("failures", JSON.toJsonTree(failures));
                try {
                    Files.writeString(output, JSON.toJson(report), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                } catch (Exception failure) {
                    helper.fail("Unable to save private modern comparison report: " + failure);
                    return;
                }
                helper.assertTrue(failures.isEmpty() && results.size() == cases.size(), "Original runtime comparison differences: " + failures);
                helper.succeed();
            });
        } catch (Exception failure) {
            helper.fail("Unable to initialize original runtime comparison: " + failure);
        }
    }

    private static JsonObject runCase(ServerLevel level, int index, JsonObject original) throws Exception {
        String kind = original.get("kind").getAsString();
        long seed = original.get("seed").getAsLong();
        boolean natural = !original.get("notify").getAsBoolean();
        boolean spiders = original.get("spiders").getAsBoolean();
        Block soil = BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse(original.get("soil").getAsString()));
        BlockPos origin = new BlockPos(1024 + index * 64, 64, 1024);
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                level.setBlock(origin.offset(x, -2, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(origin.offset(x, -1, z), soil.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = 0; y <= 56; y++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        LegacyRandomSource random = new LegacyRandomSource(seed);
        boolean success;
        if (kind.startsWith("greatwood")) {
            success = TCGreatwoodTreeFeature.placeTree(level, random, origin, natural, spiders);
        } else {
            success = TCSilverwoodTreeFeature.placeTree(level, random, origin,
                original.get("minHeight").getAsInt(), original.get("randomHeight").getAsInt(), natural);
        }
        long nextRandomLong = random.nextLong();
        List<String> placements = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int y = -2; y <= 56; y++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    BlockState state = level.getBlockState(origin.offset(x, y, z));
                    if (state.isAir() || (y == -2 && state.is(Blocks.STONE)) || (y == -1 && state.is(soil))) {
                        continue;
                    }
                    String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    if (!isFixtureBlock(id)) {
                        continue;
                    }
                    placements.add(x + "," + y + "," + z + "=" + canonical(state, id));
                    counts.merge(id, 1, Integer::sum);
                }
            }
        }
        Collections.sort(placements);
        List<String> expected = new ArrayList<>();
        int excludedOriginalTerrain = 0;
        for (var placement : original.getAsJsonArray("placements")) {
            String line = placement.getAsString();
            String block = line.substring(line.indexOf('=') + 1).split("\\[", 2)[0];
            if (isFixtureBlock(block)) {
                expected.add(line);
            } else {
                excludedOriginalTerrain++;
            }
        }
        List<String> missing = new ArrayList<>(expected);
        missing.removeAll(placements);
        List<String> extra = new ArrayList<>(placements);
        extra.removeAll(expected);
        boolean lootMatches = true;
        Long expectedLoot = null;
        Long actualLoot = null;
        if (spiders) {
            expectedLoot = TagParser.parseTag(original.getAsJsonObject("blockEntities").get("0,-2,0").getAsString()).getLong("LootTableSeed");
            actualLoot = ((ChestBlockEntity) level.getBlockEntity(origin.below(2))).getLootTableSeed();
            lootMatches = expectedLoot.equals(actualLoot);
        }
        boolean matches = success == original.get("success").getAsBoolean()
            && nextRandomLong == original.get("nextRandomLong").getAsLong()
            && missing.isEmpty() && extra.isEmpty() && lootMatches;
        JsonObject result = new JsonObject();
        result.addProperty("kind", kind);
        result.addProperty("seed", seed);
        result.addProperty("success", success);
        result.addProperty("nextRandomLong", nextRandomLong);
        result.addProperty("placementCount", placements.size());
        result.addProperty("expectedFixturePlacementCount", expected.size());
        result.addProperty("excludedOriginalTerrainPlacements", excludedOriginalTerrain);
        result.addProperty("placementSha256", hex(MessageDigest.getInstance("SHA-256")
            .digest(String.join("\n", placements).getBytes(StandardCharsets.UTF_8))));
        result.addProperty("matches", matches);
        result.addProperty("lootSeedMatches", lootMatches);
        if (expectedLoot != null) {
            result.addProperty("expectedLootSeed", expectedLoot);
            result.addProperty("actualLootSeed", actualLoot);
        }
        result.add("blocks", JSON.toJsonTree(counts));
        result.add("placements", JSON.toJsonTree(placements));
        result.add("missingFromPort", JSON.toJsonTree(missing));
        result.add("extraInPort", JSON.toJsonTree(extra));
        return result;
    }

    private static String canonical(BlockState state, String id) {
        if (id.equals("thaumcraft:log_greatwood") || id.equals("thaumcraft:log_silverwood")) {
            return id + "[axis=" + state.getValue(RotatedPillarBlock.AXIS).toString().toLowerCase(Locale.ROOT) + "]";
        }
        if (id.equals("thaumcraft:leaves_greatwood") || id.equals("thaumcraft:leaves_silverwood")) {
            return id + "[persistent=" + state.getValue(LeavesBlock.PERSISTENT) + "]";
        }
        return id;
    }

    private static boolean isFixtureBlock(String id) {
        // Forge 1.12 can populate a neighbor after a pad was prepared, leaving TC ores in its stone floor.
        // These are not tree placements. Filter both maps symmetrically, never tree/flower/nest blocks.
        return id.equals("thaumcraft:log_greatwood") || id.equals("thaumcraft:log_silverwood")
            || id.equals("thaumcraft:leaves_greatwood") || id.equals("thaumcraft:leaves_silverwood")
            || id.equals("thaumcraft:shimmerleaf") || id.equals("minecraft:dirt")
            || id.equals("minecraft:cobweb") || id.equals("minecraft:spawner") || id.equals("minecraft:chest");
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder();
        for (byte element : bytes) value.append(String.format(Locale.ROOT, "%02x", element & 255));
        return value.toString();
    }
}
