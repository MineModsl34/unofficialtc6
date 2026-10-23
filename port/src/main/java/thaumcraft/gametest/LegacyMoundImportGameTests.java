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
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.common.world.validation.LegacyMoundImporter;

/** Synthetic default safety tests and an opt-in private comparison to executed original exports. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class LegacyMoundImportGameTests {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    public LegacyMoundImportGameTests() {
    }

    @GameTest(template = "empty")
    public static void rejectsUnknownUnusedPaletteEntry(GameTestHelper helper) throws Exception {
        CompoundTag tag = synthetic("[{Name:'minecraft:air'},{Name:'thaumcraft:definitely_unregistered_audit_block'}]",
            "[{pos:[0,0,0],state:0}]");
        rejects(helper, () -> LegacyMoundImporter.validateModern(tag), "unused unknown block");
        rejects(helper, () -> LegacyMoundImporter.importLegacy(tag), "legacy unknown block before DataFixer");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rejectsUnknownAndNonStringProperties(GameTestHelper helper) throws Exception {
        CompoundTag unknown = synthetic("[{Name:'minecraft:stone',Properties:{typo:'true'}}]", "[{pos:[0,0,0],state:0}]");
        rejects(helper, () -> LegacyMoundImporter.validateModern(unknown), "unknown property");
        CompoundTag typed = synthetic("[{Name:'minecraft:oak_log',Properties:{axis:1}}]", "[{pos:[0,0,0],state:0}]");
        rejects(helper, () -> LegacyMoundImporter.validateModern(typed), "non-string property");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rejectsInvalidModernAndLegacyPropertyValues(GameTestHelper helper) throws Exception {
        CompoundTag modern = synthetic("[{Name:'minecraft:oak_log',Properties:{axis:'diagonal'}}]", "[{pos:[0,0,0],state:0}]");
        rejects(helper, () -> LegacyMoundImporter.validateModern(modern), "invalid modern value");
        CompoundTag legacy = synthetic("[{Name:'minecraft:stonebrick',Properties:{variant:'typo'}}]", "[{pos:[0,0,0],state:0}]");
        rejects(helper, () -> LegacyMoundImporter.importLegacy(legacy), "invalid legacy value before flattening");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rejectsInvalidIndicesAndDuplicateCoordinates(GameTestHelper helper) throws Exception {
        CompoundTag index = synthetic("[{Name:'minecraft:air'}]", "[{pos:[0,0,0],state:1}]");
        rejects(helper, () -> LegacyMoundImporter.validateModern(index), "out-of-range index");
        CompoundTag duplicate = synthetic("[{Name:'minecraft:air'}]", "[{pos:[0,0,0],state:0},{pos:[0,0,0],state:0}]");
        rejects(helper, () -> LegacyMoundImporter.validateModern(duplicate), "duplicate coordinate");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vanillaDataFixerFlattensLegacyMoundStates(GameTestHelper helper) throws Exception {
        CompoundTag original = synthetic("[{Name:'minecraft:grass',Properties:{snowy:'false'}},"
            + "{Name:'minecraft:tallgrass',Properties:{type:'tall_grass'}},"
            + "{Name:'minecraft:stone_stairs',Properties:{facing:'north',half:'top',shape:'straight'}},"
            + "{Name:'minecraft:stonebrick',Properties:{variant:'chiseled_stonebrick'}},"
            + "{Name:'minecraft:air'}]", "[{pos:[0,0,0],state:0},{pos:[1,0,0],state:1},"
            + "{pos:[2,0,0],state:2},{pos:[3,0,0],state:3},{pos:[4,0,0],state:4}]");
        String untouched = original.toString();
        var result = LegacyMoundImporter.importLegacy(original);
        helper.assertTrue(original.toString().equals(untouched), "Importer must not mutate source NBT");
        helper.assertTrue(result.blockCounts().equals(Map.of("minecraft:grass_block", 1, "minecraft:short_grass", 1,
            "minecraft:cobblestone_stairs", 1, "minecraft:chiseled_stone_bricks", 1, "minecraft:air", 1)),
            "Vanilla STRUCTURE fixer must flatten the old names and retain explicit AIR");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void retainsExplicitActualBarConnectionsWhichVanillaFlatteningDrops(GameTestHelper helper) throws Exception {
        CompoundTag original = synthetic("[{Name:'minecraft:iron_bars',Properties:{north:'true',east:'false',south:'true',west:'false'}},"
            + "{Name:'minecraft:iron_bars',Properties:{north:'false',east:'false',south:'false',west:'true'}}]",
            "[{pos:[3,10,9],state:0},{pos:[3,11,9],state:1}]");
        String untouched = original.toString();
        CompoundTag vanilla = net.minecraft.util.datafix.DataFixTypes.STRUCTURE.updateToCurrentVersion(
            net.minecraft.util.datafix.DataFixers.getDataFixer(), original.copy(), 1343);
        helper.assertTrue(vanilla.getList("palette", Tag.TAG_COMPOUND).getCompound(0)
            .getCompound("Properties").getString("north").equals("false"), "Vanilla drops original actual-state connections");
        var imported = LegacyMoundImporter.importLegacy(original);
        ListTag palette = imported.fixedTag().getList("palette", Tag.TAG_COMPOUND);
        helper.assertTrue(palette.getCompound(0).getCompound("Properties").getString("north").equals("true")
            && palette.getCompound(0).getCompound("Properties").getString("south").equals("true")
            && palette.getCompound(1).getCompound("Properties").getString("west").equals("true"),
            "Each validated original connection combination survives independently");
        helper.assertTrue(original.toString().equals(untouched), "Connection repair must not mutate original data");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void normalizesLegacyTrappedChestEntityIdentity(GameTestHelper helper) throws Exception {
        CompoundTag original = synthetic("[{Name:'minecraft:trapped_chest',Properties:{facing:'west'}}]",
            "[{pos:[0,0,0],state:0,nbt:{id:'minecraft:chest',LootTable:'minecraft:chests/simple_dungeon',LootTableSeed:123L}}]");
        var result = LegacyMoundImporter.importLegacy(original);
        CompoundTag nbt = result.fixedTag().getList("blocks", Tag.TAG_COMPOUND).getCompound(0).getCompound("nbt");
        helper.assertTrue(nbt.getString("id").equals("minecraft:trapped_chest"), "Trapped block must retain its modern BE identity");
        helper.assertTrue(nbt.getLong("LootTableSeed") == 123L, "Fixing BE identity must not change loot seed");
        helper.succeed();
    }

    @GameTestGenerator
    public static Collection<TestFunction> optionalOriginalMoundImport() {
        String baseline = System.getProperty("thaumcraft.originalMoundImport");
        if (baseline == null || baseline.isBlank()) {
            return List.of();
        }
        return List.of(new TestFunction("thaumcraft_original_mound", "thaumcraft.original_mound_import_comparison",
            "thaumcraft:empty", 200, 0L, true, helper -> compareExternal(helper, Path.of(baseline))));
    }

    private static void compareExternal(GameTestHelper helper, Path source) {
        JsonObject report = new JsonObject();
        JsonArray variants = new JsonArray();
        List<String> failures = new ArrayList<>();
        Path baseline = source.toAbsolutePath().normalize();
        Path output = baseline.resolveSibling("modern-mound-import-report.json");
        try {
            helper.assertFalse(Files.exists(output), "Refuses to overwrite an existing private import report");
            JsonObject original = JsonParser.parseString(Files.readString(baseline, StandardCharsets.UTF_8)).getAsJsonObject();
            helper.assertTrue(original.get("status").getAsString().equals("completed"), "Original export must be completed");
            boolean actualStates = original.has("actualStateFixtureEnabled") && original.get("actualStateFixtureEnabled").getAsBoolean();
            helper.assertTrue(original.get("minecraft").getAsString().equals("1.12.2")
                && original.get("thaumcraft").getAsString().equals("6.1.BETA26"), "Wrong original reference version");
            Path maskPath = Path.of(original.get("maskPath").getAsString());
            helper.assertTrue(hash(maskPath).equalsIgnoreCase(original.get("maskSha256").getAsString()), "Private mask hash mismatch");
            JsonObject mask = JsonParser.parseString(Files.readString(maskPath, StandardCharsets.UTF_8)).getAsJsonObject();
            helper.assertTrue(mask.get("SourceSha256").getAsString().equalsIgnoreCase(original.get("maskSourceSha256").getAsString()),
                "Mask source hash mismatch");
            report.addProperty("baseline", baseline.toString());
            report.addProperty("startedUtc", Instant.now().toString());
            report.addProperty("scope", "Private original template -> vanilla DataFixer -> fail-closed palette validation -> actual template placement; not natural generation, loot algorithm or visual acceptance");
            report.addProperty("placementAdaptation", "Modern StructureTemplate randomizes container LootTableSeed; fixed original block-entity NBT is restored after placement for this fixture comparison only");
            report.addProperty("maskSha256", original.get("maskSha256").getAsString());
            report.addProperty("sourceSha256", original.get("maskSourceSha256").getAsString());
            report.addProperty("actualStateReference", actualStates);
            JsonArray cases = original.getAsJsonArray("variants");
            helper.assertTrue(cases.size() == 2, "Expected normal and trapped original exports");
            for (int index = 0; index < cases.size(); index++) {
                JsonObject expected = cases.get(index).getAsJsonObject();
                JsonObject result = new JsonObject();
                result.addProperty("name", expected.get("name").getAsString());
                try {
                    compareVariant(helper, baseline.getParent(), expected, mask, index, result, actualStates);
                    result.addProperty("matches", true);
                } catch (Exception failure) {
                    result.addProperty("matches", false);
                    result.addProperty("error", failure.toString());
                    failures.add(expected.get("name").getAsString() + ": " + failure);
                }
                variants.add(result);
            }
            report.addProperty("finishedUtc", Instant.now().toString());
            report.addProperty("status", failures.isEmpty() ? "completed" : "failed");
            report.addProperty("matchedVariants", cases.size() - failures.size());
            report.add("variants", variants);
            report.add("failures", JSON.toJsonTree(failures));
            Files.writeString(output, JSON.toJson(report), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            helper.assertTrue(failures.isEmpty(), "Private mound import comparison: " + failures);
            helper.succeed();
        } catch (Exception failure) {
            helper.fail("Unable to compare private original mound imports: " + failure);
        }
    }

    private static void compareVariant(GameTestHelper helper, Path directory, JsonObject expected, JsonObject mask,
        int index, JsonObject result, boolean actualStates) throws Exception {
        String name = expected.get("name").getAsString();
        helper.assertTrue(name.equals("mound_normal") || name.equals("mound_trapped"), "Unexpected external fixture name");
        Path input = directory.resolve(name + (actualStates ? "-actualstate" : "") + ".nbt");
        String sourceHash = expected.get(actualStates ? "actualStateTemplateSha256" : "templateSha256").getAsString();
        helper.assertTrue(hash(input).equalsIgnoreCase(sourceHash), "Original NBT hash mismatch");
        if (actualStates) {
            helper.assertTrue(expected.get("actualStateFixtureVersion").getAsInt() == 1
                && expected.get("actualStateDifferences").getAsInt() == 2, "Expected executed two-bar actual-state correction");
        }
        var imported = LegacyMoundImporter.read(input);
        CompoundTag fixed = imported.fixedTag();
        ListTag entries = fixed.getList("blocks", Tag.TAG_COMPOUND);
        ListTag palette = fixed.getList("palette", Tag.TAG_COMPOUND);
        boolean trapped = expected.get("trapped").getAsBoolean();
        Map<BlockPos, CompoundTag> byPosition = new HashMap<>();
        for (Tag raw : entries) {
            CompoundTag entry = (CompoundTag) raw;
            byPosition.put(position(entry), entry);
        }
        Set<BlockPos> maskPositions = new HashSet<>();
        int explicitAir = 0;
        for (var raw : mask.getAsJsonArray("Coordinates")) {
            JsonObject coordinate = raw.getAsJsonObject();
            if (coordinate.get("conditionalTnt").getAsBoolean() && !trapped) {
                continue;
            }
            BlockPos local = new BlockPos(coordinate.get("x").getAsInt(), coordinate.get("y").getAsInt() + 1,
                coordinate.get("z").getAsInt());
            maskPositions.add(local);
            helper.assertTrue(byPosition.containsKey(local), "Missing original mask cell " + local);
            if (coordinate.get("explicitAir").getAsBoolean()) {
                helper.assertTrue(palette.getCompound(byPosition.get(local).getInt("state")).getString("Name").equals("minecraft:air"),
                    "Explicit AIR cell changed at " + local);
                explicitAir++;
            }
        }
        helper.assertTrue(maskPositions.equals(byPosition.keySet()), "Imported coordinate mask differs from original source mask");
        helper.assertTrue(entries.size() == (trapped ? 2447 : 2446) && explicitAir == 774
            && imported.blockCounts().getOrDefault("minecraft:air", 0) == 774, "Original count/AIR contract changed");
        helper.assertTrue(imported.blockCounts().equals(flattenedCounts(expected.getAsJsonObject("blocks"))), "Block counts changed beyond vanilla flattening");

        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(4096 + index * 64, 64, 4096);
        for (int x = 0; x < 19; x++) {
            for (int y = 0; y < 17; y++) {
                for (int z = 0; z < 19; z++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        helper.assertTrue(imported.template().placeInWorld(level, origin, origin,
            new StructurePlaceSettings().setKnownShape(true).setIgnoreEntities(true), new LegacyRandomSource(0), Block.UPDATE_CLIENTS),
            "Actual modern template placement failed");
        JsonObject blockEntities = new JsonObject();
        int restored = 0;
        for (var entry : byPosition.entrySet()) {
            BlockPos local = entry.getKey();
            CompoundTag block = entry.getValue();
            BlockPos actual = origin.offset(local);
            BlockState expectedState = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(block.getInt("state")));
            helper.assertTrue(level.getBlockState(actual).equals(expectedState), "Placed block state differs at " + local);
            if (block.contains("nbt", Tag.TAG_COMPOUND)) {
                BlockEntity entity = level.getBlockEntity(actual);
                helper.assertTrue(entity != null, "Missing placed block entity at " + local);
                entity.loadWithComponents(block.getCompound("nbt").copy(), level.registryAccess());
                entity.setChanged();
                CompoundTag saved = entity.saveWithId(level.registryAccess());
                blockEntities.addProperty(key(local), saved.toString());
                verifyEntity(helper, local, entity, block.getCompound("nbt"), saved, expected);
                restored++;
            }
        }
        int untouched = 0;
        for (int x = 0; x < 19; x++) {
            for (int y = 0; y < 17; y++) {
                for (int z = 0; z < 19; z++) {
                    BlockPos local = new BlockPos(x, y, z);
                    if (!byPosition.containsKey(local)) {
                        helper.assertTrue(level.getBlockState(origin.offset(local)).is(Blocks.DIAMOND_BLOCK), "Untouched background was captured/overwritten");
                        untouched++;
                    }
                }
            }
        }
        helper.assertTrue(restored == 3, "Expected one chest and two spawners");
        Path fixedOutput = directory.resolve(name + "-modern.nbt");
        try (var stream = Files.newOutputStream(fixedOutput, StandardOpenOption.CREATE_NEW)) {
            NbtIo.writeCompressed(fixed.copy(), stream);
        }
        result.addProperty("sourceTemplateSha256", sourceHash);
        result.addProperty("actualStateReference", actualStates);
        result.addProperty("modernTemplateSha256", hash(fixedOutput));
        result.addProperty("dataVersion", fixed.getInt("DataVersion"));
        result.addProperty("templateBlocks", entries.size());
        result.addProperty("explicitAir", explicitAir);
        result.addProperty("untouchedBackground", untouched);
        result.addProperty("normalizedTrappedChestIds", imported.normalizedTrappedChestIds());
        result.addProperty("restoredBlockEntities", restored);
        result.add("blocks", JSON.toJsonTree(imported.blockCounts()));
        result.add("blockEntities", blockEntities);
    }

    private static void verifyEntity(GameTestHelper helper, BlockPos local, BlockEntity entity, CompoundTag fixed,
        CompoundTag saved, JsonObject expected) throws Exception {
        CompoundTag old = TagParser.parseTag(expected.getAsJsonObject("blockEntities").get(key(local)).getAsString());
        if (entity instanceof ChestBlockEntity chest) {
            helper.assertTrue(chest.getLootTable() != null && chest.getLootTable().location().toString().equals(old.getString("LootTable")),
                "Chest loot table changed");
            helper.assertTrue(fixed.getLong("LootTableSeed") == old.getLong("LootTableSeed")
                && chest.getLootTableSeed() == old.getLong("LootTableSeed"), "Original loot seed changed during fixing/restoration");
            helper.assertTrue(saved.getString("id").equals(expected.get("trapped").getAsBoolean() ? "minecraft:trapped_chest" : "minecraft:chest"),
                "Placed chest BE identity is incompatible with its block");
        } else if (entity instanceof SpawnerBlockEntity) {
            String wanted = old.getCompound("SpawnData").getString("id");
            helper.assertTrue(fixed.getCompound("SpawnData").getCompound("entity").getString("id").equals(wanted)
                && saved.getCompound("SpawnData").getCompound("entity").getString("id").equals(wanted), "Spawner entity changed");
            for (String field : List.of("MaxNearbyEntities", "RequiredPlayerRange", "SpawnCount", "MaxSpawnDelay", "Delay", "SpawnRange", "MinSpawnDelay")) {
                helper.assertTrue(saved.getInt(field) == old.getInt(field), "Spawner setting changed: " + field);
            }
            ListTag potentials = saved.getList("SpawnPotentials", Tag.TAG_COMPOUND);
            helper.assertTrue(potentials.size() == 1 && potentials.getCompound(0).getInt("weight") == 1
                && potentials.getCompound(0).getCompound("data").getCompound("entity").getString("id").equals(wanted),
                "Spawner weighted entity choice changed");
        } else {
            helper.fail("Unexpected original mound block entity at " + local);
        }
    }

    private static Map<String, Integer> flattenedCounts(JsonObject oldCounts) {
        Map<String, Integer> result = new HashMap<>();
        oldCounts.entrySet().forEach(entry -> {
            String id = switch (entry.getKey()) {
                case "minecraft:grass" -> "minecraft:grass_block";
                case "minecraft:tallgrass" -> "minecraft:short_grass";
                case "minecraft:stone_stairs" -> "minecraft:cobblestone_stairs";
                case "minecraft:mob_spawner" -> "minecraft:spawner";
                case "minecraft:stonebrick" -> "minecraft:chiseled_stone_bricks";
                default -> entry.getKey();
            };
            result.merge(id, entry.getValue().getAsInt(), Integer::sum);
        });
        return result;
    }

    private static CompoundTag synthetic(String palette, String blocks) throws Exception {
        return TagParser.parseTag("{DataVersion:1343,size:[19,17,19],palette:" + palette + ",blocks:" + blocks + ",entities:[]}");
    }

    private static void rejects(GameTestHelper helper, Runnable operation, String description) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            helper.assertTrue(expected.getMessage().startsWith("Rejected legacy mound:"), "Expected fail-closed importer rejection");
            return;
        }
        helper.fail("Unsafe template accepted: " + description);
    }

    private static BlockPos position(CompoundTag entry) {
        ListTag pos = entry.getList("pos", Tag.TAG_INT);
        return new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
    }

    private static String key(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
