package thaumcraft.gametest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import thaumcraft.Thaumcraft;
import thaumcraft.common.lib.utils.LegacyLootEnchantments;
import thaumcraft.common.lib.utils.LegacyLootEnchantments.Definition;
import thaumcraft.common.lib.utils.LegacyLootEnchantments.Selection;
import thaumcraft.common.lib.utils.LegacyLootRules;
import thaumcraft.common.lib.utils.LegacyLootRules.EnchantmentRequest;
import thaumcraft.common.lib.utils.LegacyLootRules.Rarity;
import thaumcraft.common.lib.utils.LegacyLootRules.Reward;
import thaumcraft.common.lib.utils.TCLegacyLoot;

/**
 * Optional comparison against an executed, unmodified original JAR. No tests or
 * fixture data are loaded in normal runs; source-derived unit expectations remain
 * separate. The private original report and modern result are never mod resources.
 */
@GameTestHolder(Thaumcraft.MODID)
public final class OriginalLootComparisonGameTests {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CASES_PER_TICK = 128;
    private static final int MAX_RECORDED_FAILURES = 256;
    private static final long MAX_BASELINE_BYTES = 32L * 1024 * 1024;

    public OriginalLootComparisonGameTests() {}

    @GameTestGenerator
    public static Collection<TestFunction> optionalOriginalLootComparison() {
        String fixture = System.getProperty("thaumcraft.originalLootFixtures");
        if (fixture == null || fixture.isBlank()) {
            return List.of();
        }
        return List.of(new TestFunction("thaumcraft_original_loot_reference", "thaumcraft.original_loot_runtime_comparison",
            "thaumcraft:empty", 1200, 0L, true, helper -> compare(helper, Path.of(fixture))));
    }

    private static void compare(GameTestHelper helper, Path path) {
        try {
            Path baseline = path.toAbsolutePath().normalize();
            helper.assertTrue(Files.isRegularFile(baseline) && Files.size(baseline) <= MAX_BASELINE_BYTES,
                "Original loot fixture must be a bounded regular file");
            JsonObject original = JsonParser.parseString(Files.readString(baseline, StandardCharsets.UTF_8)).getAsJsonObject();
            helper.assertTrue(original.get("schemaVersion").getAsInt() == 1 && original.get("status").getAsString().equals("completed"),
                "Original loot audit must have completed with schemaVersion1");
            JsonObject source = original.getAsJsonObject("source");
            helper.assertTrue(source.get("mcVersion").getAsString().equals("1.12.2")
                && source.get("tcVersion").getAsString().equals("6.1.BETA26"), "Exact requested original build");
            JsonArray cases = original.getAsJsonArray("cases");
            JsonArray enchantFixtures = original.getAsJsonArray("enchantFixtures");
            helper.assertTrue(cases.size() >= 6144 && cases.size() <= 32768 && enchantFixtures.size() == 1260,
                "Expected complete fixed/scattered-seed loot and all35gear+BOOK enchantment fixtures");
            int totalRolls = 0;
            int streamCases = 0;
            for (JsonElement element : cases) {
                JsonObject row = element.getAsJsonObject();
                int rarity = row.get("rarity").getAsInt();
                int count = row.get("rollCount").getAsInt();
                helper.assertTrue(rarity >= 0 && rarity <= 2 && count >= 1 && count <= 4096
                    && row.getAsJsonArray("rewards").size() == count, "Valid complete original reward sequence");
                totalRolls += count;
                if (count > 1) streamCases++;
            }
            helper.assertTrue(totalRolls <= 1_000_000 && totalRolls == original.get("lootRollCount").getAsInt(), "Original roll count metadata");
            helper.assertTrue(streamCases >= 24 && totalRolls >= 9216, "Original fixture includes post-N-roll stream state, not only single rolls");
            if (original.has("lootCaseCount")) {
                helper.assertTrue(cases.size() == original.get("lootCaseCount").getAsInt(), "Original case count metadata");
            }
            TCLegacyLoot loot = TCLegacyLoot.create(helper.getLevel().registryAccess());
            Path output = baseline.resolveSibling("modern-loot-comparison-" + UUID.randomUUID() + ".json");
            Results results = new Results(baseline, output);
            results.report.addProperty("lootCaseCount", cases.size());
            results.report.addProperty("lootRollCount", totalRolls);
            results.report.addProperty("streamCaseCount", streamCases);
            results.report.addProperty("enchantFixtureCount", enchantFixtures.size());
            compareCatalog(original, loot, results);

            long tick = 1;
            for (int start = 0; start < cases.size(); start += CASES_PER_TICK) {
                int from = start;
                int until = Math.min(start + CASES_PER_TICK, cases.size());
                helper.runAtTickTime(tick++, () -> {
                    for (int index = from; index < until; index++) {
                        compareLootCase(cases.get(index).getAsJsonObject(), loot, results);
                    }
                });
            }
            for (int start = 0; start < enchantFixtures.size(); start += CASES_PER_TICK) {
                int from = start;
                int until = Math.min(start + CASES_PER_TICK, enchantFixtures.size());
                helper.runAtTickTime(tick++, () -> {
                    for (int index = from; index < until; index++) {
                        compareEnchantFixture(enchantFixtures.get(index).getAsJsonObject(), loot, results);
                    }
                });
            }
            helper.runAtTickTime(tick, () -> finish(helper, results));
        } catch (Exception failure) {
            helper.fail("Unable to initialize executed-original loot comparison: " + failure);
        }
    }

    private static void compareCatalog(JsonObject original, TCLegacyLoot loot, Results results) {
        results.check("potionRegistryOrder", original.getAsJsonArray("potionRegistryOrder"), JSON.toJsonTree(LegacyLootRules.VANILLA_POTION_TYPES));
        JsonObject originalPools = original.getAsJsonObject("pools");
        String[] fields = {"lootBagCommon", "lootBagUncommon", "lootBagRare"};
        for (Rarity rarity : Rarity.values()) {
            JsonArray expected = new JsonArray();
            for (JsonElement element : originalPools.getAsJsonArray(fields[rarity.index()])) {
                JsonObject old = element.getAsJsonObject();
                JsonObject row = new JsonObject();
                row.addProperty("weight", old.get("weight").getAsInt());
                row.add("stack", expectedStack(old.getAsJsonObject("stack")));
                expected.add(row);
            }
            JsonArray actual = new JsonArray();
            for (LegacyLootRules.Entry entry : LegacyLootRules.pool(rarity)) {
                JsonObject row = new JsonObject();
                row.addProperty("weight", entry.weight());
                row.add("stack", actualStack(loot.materialize(entry.reward())));
                actual.add(row);
            }
            results.check(fields[rarity.index()], expected, actual);
        }
        JsonArray expectedCatalog = new JsonArray();
        for (JsonElement element : original.getAsJsonArray("enchantmentRegistry")) {
            JsonObject raw = element.getAsJsonObject();
            JsonObject row = new JsonObject();
            for (String field : List.of("id", "weight", "minLevel", "maxLevel", "treasure", "allowedOnBooks", "costs", "compatibility")) {
                row.add(field, raw.get(field).deepCopy());
            }
            expectedCatalog.add(row);
        }
        JsonArray actualCatalog = new JsonArray();
        for (Definition definition : LegacyLootEnchantments.DEFINITIONS) {
            JsonObject row = new JsonObject();
            row.addProperty("id", "minecraft:" + definition.id());
            row.addProperty("weight", definition.weight());
            row.addProperty("minLevel", 1);
            row.addProperty("maxLevel", definition.maxLevel());
            row.addProperty("treasure", definition.treasure());
            row.addProperty("allowedOnBooks", true);
            JsonArray costs = new JsonArray();
            for (int level = 1; level <= definition.maxLevel(); level++) {
                JsonObject cost = new JsonObject();
                cost.addProperty("level", level);
                cost.addProperty("min", definition.minCost(level));
                cost.addProperty("max", definition.maxCost(level));
                costs.add(cost);
            }
            row.add("costs", costs);
            JsonObject compatibility = new JsonObject();
            for (Definition other : LegacyLootEnchantments.DEFINITIONS) {
                compatibility.addProperty("minecraft:" + other.id(), LegacyLootEnchantments.compatible(definition, other));
            }
            row.add("compatibility", compatibility);
            actualCatalog.add(row);
        }
        // Original numeric IDs are intentionally not a modern registry mapping.
        // Array order, keys, every cost/weight and compatibility pair are exact.
        results.check("enchantmentRegistry", expectedCatalog, actualCatalog);
    }

    private static void compareLootCase(JsonObject original, TCLegacyLoot loot, Results results) {
        String name = original.get("name").getAsString();
        try {
            Random random = new Random(Long.parseLong(original.get("seed").getAsString()));
            Rarity rarity = Rarity.values()[original.get("rarity").getAsInt()];
            JsonArray expected = new JsonArray();
            for (JsonElement element : original.getAsJsonArray("rewards")) {
                expected.add(expectedStack(element.getAsJsonObject()));
            }
            JsonArray actual = new JsonArray();
            for (int roll = 0; roll < original.get("rollCount").getAsInt(); roll++) {
                ItemStack stack = loot.generate(rarity, random);
                actual.add(actualStack(stack));
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (id.equals("minecraft:book")) results.ordinaryBooks++;
                if (id.endsWith("_axe") || id.endsWith("_sword") || id.endsWith("_helm") || id.endsWith("_helmet")
                    || id.endsWith("_chest") || id.endsWith("_chestplate") || id.endsWith("_legs")
                    || id.endsWith("_leggings") || id.endsWith("_boots")) results.gearRolls++;
                if (stack.has(DataComponents.POTION_CONTENTS)) {
                    results.potionRewards++;
                    if (stack.get(DataComponents.POTION_CONTENTS).potion().isEmpty()) results.emptyPotionRewards++;
                }
            }
            long actualNextLong = random.nextLong();
            long expectedNextLong = Long.parseLong(original.get("nextLong").getAsString());
            boolean rewardsMatch = expected.equals(actual);
            boolean randomMatches = actualNextLong == expectedNextLong;
            JsonObject row = new JsonObject();
            row.addProperty("name", name);
            row.addProperty("rarity", rarity.index());
            row.addProperty("seed", original.get("seed").getAsString());
            row.addProperty("rollCount", original.get("rollCount").getAsInt());
            row.addProperty("rewardsMatch", rewardsMatch);
            row.addProperty("randomMatches", randomMatches);
            row.addProperty("expectedNextLong", Long.toString(expectedNextLong));
            row.addProperty("actualNextLong", Long.toString(actualNextLong));
            results.lootCases.add(row);
            results.lootCasesCompared++;
            if (!rewardsMatch || !randomMatches) {
                results.difference("loot:" + name, expected, actual, row);
            }
        } catch (Exception failure) {
            results.exception("loot:" + name, failure);
        }
    }

    private static void compareEnchantFixture(JsonObject original, TCLegacyLoot loot, Results results) {
        String name = original.get("item").getAsString() + "/power" + original.get("power").getAsInt()
            + "/seed" + original.get("seed").getAsString();
        try {
            Reward reward = new Reward(original.get("item").getAsString(), 1, 0, null);
            ItemStack stack = loot.materialize(reward);
            int enchantability = stack.getItem().getEnchantmentValue(stack);
            Random random = new Random(Long.parseLong(original.get("seed").getAsString()));
            List<Selection> selected = LegacyLootEnchantments.select(random,
                new EnchantmentRequest(reward, original.get("power").getAsInt(), enchantability, false));
            JsonArray actual = new JsonArray();
            for (Selection selection : selected) {
                actual.add(enchantment(selection.enchantment().modernId(), selection.level()));
            }
            // Selection order is NOT sorted: it affects successive compatibility filtering/RNG.
            JsonArray expected = expectedEnchantments(original.getAsJsonArray("selected"), false);
            long actualNextLong = random.nextLong();
            long expectedNextLong = Long.parseLong(original.get("nextLong").getAsString());
            boolean selectedMatch = expected.equals(actual);
            boolean randomMatches = actualNextLong == expectedNextLong;
            boolean gearMatches = enchantability == original.get("enchantability").getAsInt()
                && stack.getMaxDamage() == original.get("maxDamage").getAsInt();
            JsonObject row = new JsonObject();
            row.addProperty("name", name);
            row.addProperty("selectedMatch", selectedMatch);
            row.addProperty("randomMatches", randomMatches);
            row.addProperty("gearMatches", gearMatches);
            row.addProperty("expectedNextLong", Long.toString(expectedNextLong));
            row.addProperty("actualNextLong", Long.toString(actualNextLong));
            results.enchantCases.add(row);
            results.enchantCasesCompared++;
            if (!selectedMatch || !randomMatches || !gearMatches) {
                results.difference("enchant:" + name, expected, actual, row);
            }
        } catch (Exception failure) {
            results.exception("enchant:" + name, failure);
        }
    }

    private static JsonObject expectedStack(JsonObject original) {
        String oldId = original.get("itemId").getAsString();
        int damage = original.get("damage").getAsInt();
        String itemId = oldId;
        // Independent old-variant normalization, not the production resolver: a
        // bug in its metadata mapping must not normalize both sides identically.
        if (oldId.equals("thaumcraft:baubles")) {
            itemId = switch (damage) {
                case 0 -> "thaumcraft:amulet_mundane";
                case 1 -> "thaumcraft:ring_mundane";
                case 2 -> "thaumcraft:girdle_mundane";
                case 3 -> "thaumcraft:ring_apprentice";
                case 4 -> "thaumcraft:amulet_fancy";
                case 5 -> "thaumcraft:ring_fancy";
                case 6 -> "thaumcraft:girdle_fancy";
                default -> throw new IllegalArgumentException("Unknown original bauble metadata " + damage);
            };
            damage = 0;
        } else if (oldId.equals("minecraft:golden_apple")) {
            if (damage == 1) itemId = "minecraft:enchanted_golden_apple";
            else if (damage != 0) throw new IllegalArgumentException("Unknown original golden apple variant");
            damage = 0;
        } else if (oldId.equals("thaumcraft:amulet_vis") && damage != 0) {
            throw new IllegalArgumentException("Unexpected crafted amulet in loot fixture");
        }
        JsonObject result = new JsonObject();
        result.addProperty("itemId", itemId);
        result.addProperty("count", original.get("count").getAsInt());
        result.addProperty("damage", damage);
        result.addProperty("maxDamage", original.get("maxDamage").getAsInt());
        if (original.has("potionType")) result.addProperty("potionType", original.get("potionType").getAsString());
        result.add("enchantments", expectedEnchantments(original.getAsJsonArray("enchantments"), true));
        return result;
    }

    private static JsonObject actualStack(ItemStack stack) {
        JsonObject result = new JsonObject();
        result.addProperty("itemId", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        result.addProperty("count", stack.getCount());
        result.addProperty("damage", stack.getDamageValue());
        result.addProperty("maxDamage", stack.getMaxDamage());
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null) {
            if (potion.customColor().isPresent() || !potion.customEffects().isEmpty()) {
                throw new IllegalStateException("Loot acquired unexpected custom potion components");
            }
            result.addProperty("potionType", potion.potion().map(holder -> holder.unwrapKey().orElseThrow().location().toString())
                .orElse("minecraft:empty"));
        }
        List<JsonObject> entries = new ArrayList<>();
        for (var entry : stack.getTagEnchantments().entrySet()) {
            entries.add(enchantment(entry.getKey().unwrapKey().orElseThrow().location().toString(), entry.getIntValue()));
        }
        entries.sort(Comparator.comparing(entry -> entry.get("id").getAsString()));
        JsonArray enchantments = new JsonArray();
        entries.forEach(enchantments::add);
        result.add("enchantments", enchantments);
        return result;
    }

    private static JsonArray expectedEnchantments(JsonArray original, boolean sort) {
        List<JsonObject> rows = new ArrayList<>();
        for (JsonElement element : original) {
            JsonObject row = element.getAsJsonObject();
            String id = row.get("id").getAsString();
            rows.add(enchantment(id.equals("minecraft:sweeping") ? "minecraft:sweeping_edge" : id, row.get("level").getAsInt()));
        }
        if (sort) rows.sort(Comparator.comparing(row -> row.get("id").getAsString()));
        JsonArray result = new JsonArray();
        rows.forEach(result::add);
        return result;
    }

    private static JsonObject enchantment(String id, int level) {
        JsonObject result = new JsonObject();
        result.addProperty("id", id);
        result.addProperty("level", level);
        return result;
    }

    private static void finish(GameTestHelper helper, Results results) {
        try {
            if (results.lootCasesCompared != results.report.get("lootCaseCount").getAsInt()
                || results.enchantCasesCompared != results.report.get("enchantFixtureCount").getAsInt()) {
                results.exception("completeFixtureCoverage", new IllegalStateException("Not every original fixture was compared"));
            }
            if (results.gearRolls <= 0 || results.ordinaryBooks <= 0 || results.emptyPotionRewards <= 0) {
                results.exception("rewardBranchCoverage", new IllegalStateException("Fixture did not cover gear, ordinary BOOK and empty potion branches"));
            }
            results.report.addProperty("finishedUtc", Instant.now().toString());
            results.report.addProperty("status", results.failureCount == 0 ? "completed" : "failed");
            results.report.addProperty("failureCount", results.failureCount);
            results.report.addProperty("lootCasesCompared", results.lootCasesCompared);
            results.report.addProperty("enchantCasesCompared", results.enchantCasesCompared);
            results.report.addProperty("gearRolls", results.gearRolls);
            results.report.addProperty("ordinaryBooks", results.ordinaryBooks);
            results.report.addProperty("potionRewards", results.potionRewards);
            results.report.addProperty("emptyPotionRewards", results.emptyPotionRewards);
            results.report.add("lootCases", results.lootCases);
            results.report.add("enchantCases", results.enchantCases);
            results.report.add("differences", results.differences);
            Files.writeString(results.output, JSON.toJson(results.report), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            helper.assertTrue(results.failureCount == 0, "Executed-original loot differences: " + results.failureCount + "; private report " + results.output);
            helper.succeed();
        } catch (Exception failure) {
            helper.fail("Executed-original loot comparison failed: " + failure + "; private report " + results.output);
        }
    }

    private static final class Results {
        final Path output;
        final JsonObject report = new JsonObject();
        final JsonArray differences = new JsonArray();
        final JsonArray lootCases = new JsonArray();
        final JsonArray enchantCases = new JsonArray();
        int failureCount;
        int lootCasesCompared;
        int enchantCasesCompared;
        int gearRolls;
        int ordinaryBooks;
        int potionRewards;
        int emptyPotionRewards;

        Results(Path baseline, Path output) {
            this.output = output;
            report.addProperty("baseline", baseline.toString());
            report.addProperty("output", output.toString());
            report.addProperty("startedUtc", Instant.now().toString());
            report.addProperty("scope", "Actual modern reward materialization versus actual original JAR; complete ordered pools/catalog, canonical items/components/enchants and post-sequence java.util.Random state");
            report.addProperty("normalization", "Only old bauble/apple metadata to separate item keys, sweeping to sweeping_edge, empty potion to PotionContents.EMPTY; no replacement rewards or altered probabilities");
            report.addProperty("maximumRecordedDifferences", MAX_RECORDED_FAILURES);
        }

        void check(String name, JsonElement expected, JsonElement actual) {
            if (!expected.equals(actual)) difference(name, expected, actual, null);
        }

        void difference(String name, JsonElement expected, JsonElement actual, JsonObject context) {
            failureCount++;
            if (differences.size() < MAX_RECORDED_FAILURES) {
                JsonObject row = new JsonObject();
                row.addProperty("fixture", name);
                row.add("expected", expected.deepCopy());
                row.add("actual", actual.deepCopy());
                if (context != null) row.add("context", context.deepCopy());
                differences.add(row);
            }
        }

        void exception(String name, Exception failure) {
            failureCount++;
            if (differences.size() < MAX_RECORDED_FAILURES) {
                JsonObject row = new JsonObject();
                row.addProperty("fixture", name);
                row.addProperty("error", failure.toString());
                differences.add(row);
            }
        }
    }
}
