package thaumcraft.gametest;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.world.WorldgenPolicy;
import thaumcraft.common.world.structures.TCMoundPiece;
import thaumcraft.common.world.structures.TCMoundStructure;

/** Default tests use the real bundled template and block entities, but never write outside empty.nbt. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public class MoundGameTests {
    @GameTest(template = "empty")
    public static void moundFailedRawSlopeNeverCreatesARegisteredStructureStart(GameTestHelper h) {
        boolean oldGenerate = ModConfig.CONFIG_WORLD.generateStructure;
        try {
            ModConfig.CONFIG_WORLD.generateStructure = true;
            var registries = h.getLevel().registryAccess();
            var biome = registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
            var generator = new NoiseBasedChunkGenerator(new FixedBiomeSource(biome), registries.registryOrThrow(Registries.NOISE_SETTINGS)
                .getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD)) {
                @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState state) { return 80; }
                @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState state) {
                    BlockState[] column = new BlockState[384];
                    int ground = x == 26 && z == 8 ? 85 : 79; // third source point exceeds permitted air distance.
                    for (int index = 0; index < column.length; index++) {
                        column[index] = index - 64 <= ground ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    }
                    return new NoiseColumn(-64, column);
                }
            };
            var random = new WorldgenRandom(new LegacyRandomSource(0)) {
                @Override public int nextInt(int bound) {
                    if (bound == 9) { return 4; }
                    if (bound == 100) { return 0; }
                    throw new AssertionError("unexpected source draw before raw guard: " + bound);
                }
            };
            var context = new Structure.GenerationContext(registries, generator, generator.getBiomeSource(),
                h.getLevel().getChunkSource().randomState(), h.getLevel().getStructureManager(), random, 17,
                new ChunkPos(0, 0), h.getLevel(), candidate -> true);
            Structure structure = registries.registryOrThrow(Registries.STRUCTURE).getOrThrow(
                net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, net.minecraft.resources.ResourceLocation.parse("thaumcraft:mound")));
            h.assertTrue(structure instanceof TCMoundStructure && structure.findValidGenerationPoint(context).isEmpty(),
                "registered modern structure returns no stub for failed raw five-point slope");
        } finally {
            ModConfig.CONFIG_WORLD.generateStructure = oldGenerate;
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void moundCandidateRetainsOffsetsChanceAndElseIfPortalDraw(GameTestHelper h) {
        Rolls selected = new Rolls(new int[]{9, 9, 100}, new int[]{0, 8, 0});
        var origin = TCMoundStructure.candidate(selected, new ChunkPos(2, -3), (x, z) -> {
            h.assertTrue(x == 36 && z == -36, "inclusive +/-4 offsets from chunk center");
            return 80;
        }, 320).orElseThrow();
        h.assertTrue(origin.equals(new BlockPos(36, 71, -36)), "precipitation height minus nine, no random rotation");
        selected.exhausted(h);
        Rolls portal = new Rolls(new int[]{9, 9, 100, 500}, new int[]{4, 4, 1, 0});
        h.assertTrue(TCMoundStructure.candidate(portal, new ChunkPos(0, 0), (x, z) -> 80, 320).isEmpty(),
            "portal branch remains future entity stage, never becomes a mound");
        portal.exhausted(h);
        Rolls tooHigh = new Rolls(new int[]{9, 9}, new int[]{4, 4});
        h.assertTrue(TCMoundStructure.candidate(tooHigh, new ChunkPos(0, 0), (x, z) -> 329, 320).isEmpty(), "height gate precedes 1/100 draw");
        tooHigh.exhausted(h);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void moundContentsRetainTwoStreamsExactThresholdsTrapAndLootSeed(GameTestHelper h) {
        h.assertTrue(TCMoundPiece.rarity(0.0999F) == 2 && TCMoundPiece.rarity(0.1F) == 1
            && TCMoundPiece.rarity(0.3299F) == 1 && TCMoundPiece.rarity(0.33F) == 0, "original strict rarity boundaries");
        List<String> order = new ArrayList<>();
        var generator = new LegacyRandomSource(0) {
            int floats;
            @Override public float nextFloat() { order.add("generator.float"); return floats++ == 0 ? 0.1F : 0.33F; }
            @Override public int nextInt(int bound) { h.assertTrue(bound == 3, "trap bound three"); order.add("generator.int3"); return 0; }
            @Override public long nextLong() { order.add("generator.long"); return 1234567L; }
        };
        var world = new LegacyRandomSource(0) {
            int floats;
            @Override public float nextFloat() { order.add("world.float"); return floats++ == 0 ? 0.3F : 0.2999F; }
            @Override public int nextInt(int bound) { throw new AssertionError("crate kind stream must not draw ints"); }
        };
        var contents = TCMoundPiece.rollContents(generator, world);
        h.assertTrue(contents.equals(new TCMoundPiece.Contents(1, false, 0, true, true, 1234567L)), "source decisions and independent crate threshold");
        h.assertTrue(order.equals(List.of("generator.float", "world.float", "generator.float", "world.float", "generator.int3", "generator.long")), "exact interleaved source draw order");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void moundGroundGuardsRetainFlattenedLegacyVariantsAndShortCircuit(GameTestHelper h) {
        Memory memory = new Memory(h, 79);
        BlockPos pos = new BlockPos(12, 80, 12);
        for (Block ground : List.of(Blocks.STONE, Blocks.GRANITE, Blocks.POLISHED_GRANITE, Blocks.DIORITE,
            Blocks.POLISHED_DIORITE, Blocks.ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.GRASS_BLOCK,
            Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL)) {
            memory.states.put(pos.below(), ground.defaultBlockState());
            h.assertTrue(TCMoundPiece.validSpawnPoint(memory.level, pos), "legacy variant allowed: " + ground);
        }
        for (Block ground : List.of(Blocks.DEEPSLATE, Blocks.SAND, Blocks.COBBLESTONE, Blocks.MUD)) {
            memory.states.put(pos.below(), ground.defaultBlockState());
            h.assertFalse(TCMoundPiece.validSpawnPoint(memory.level, pos), "not a source valid block: " + ground);
        }
        for (Block cover : List.of(Blocks.SNOW, Blocks.SHORT_GRASS, Blocks.FERN)) {
            memory.states.put(pos.below(), cover.defaultBlockState());
            memory.states.put(pos.below(2), Blocks.DIRT.defaultBlockState());
            h.assertTrue(TCMoundPiece.validSpawnPoint(memory.level, pos), "source snow/tallgrass cover accepted");
        }
        memory.states.put(pos.below(), Blocks.DEAD_BUSH.defaultBlockState());
        h.assertFalse(TCMoundPiece.validSpawnPoint(memory.level, pos), "flattened tallgrass-meta0/dead_bush ambiguity is conservative, not broadened");
        memory.states.clear();
        BlockPos origin = new BlockPos(12, 71, 12);
        h.assertTrue(TCMoundPiece.validLocation(memory.level, origin), "all five flat guard points valid");
        memory.states.put(origin.offset(18, 8, 0), Blocks.SAND.defaultBlockState());
        memory.reads.clear();
        h.assertFalse(TCMoundPiece.validLocation(memory.level, origin), "third guard rejects the whole mound");
        h.assertFalse(memory.reads.contains(origin.offset(18, 9, 18)), "fourth guard not sampled after third rejection");
        h.assertTrue(memory.writes == 0, "guard evaluation never modifies terrain");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void moundChunkPlacementReloadAndIdempotenceUseRealTemplateAndBlockEntities(GameTestHelper h) {
        boolean oldGenerate = ModConfig.CONFIG_WORLD.generateStructure;
        WorldgenPolicy oldPolicy = ModConfig.CONFIG_WORLD.worldgenPolicy;
        try {
            ModConfig.CONFIG_WORLD.generateStructure = true;
            ModConfig.CONFIG_WORLD.worldgenPolicy = WorldgenPolicy.EMPTY;
            Memory memory = new Memory(h, 79);
            BlockPos origin = new BlockPos(12, 71, 12);
            var piece = new TCMoundPiece(h.getLevel().getStructureManager(), origin, 17L, new ChunkPos(0, 0));
            var generator = generator(h);
            var context = StructurePieceSerializationContext.fromLevel(h.getLevel());
            process(piece, memory, h, generator, new ChunkPos(0, 0));
            h.assertTrue(memory.writes > 0, "first chunk writes actual bundled geometry");
            h.assertTrue(memory.owner.isUnsaved(), "validation, loot choices and completion dirty the actual owner ProtoChunk");
            CompoundTag saved = piece.createTag(context);
            h.assertTrue(saved.getInt("TCValidation") == 2 && saved.getLongArray("TCCompletedChunks").length == 1, "shared decision and completed chunk persisted");
            int oldWrites = memory.writes;
            memory.owner.setUnsaved(false);
            process(piece, memory, h, generator, new ChunkPos(0, 0));
            h.assertTrue(memory.writes == oldWrites, "same chunk never replaces chest/spawner data");
            h.assertFalse(memory.owner.isUnsaved(), "completed chunk retry does not dirty owner again");
            piece = new TCMoundPiece(h.getLevel().getStructureManager(), saved);
            for (ChunkPos next : List.of(new ChunkPos(1, 0), new ChunkPos(0, 1), new ChunkPos(1, 1))) {
                memory.owner.setUnsaved(false);
                process(piece, memory, h, generator, next);
                h.assertTrue(memory.owner.isUnsaved(), "neighbor completion dirties start owner, not only written chunk: " + next);
            }
            CompoundTag complete = piece.createTag(context);
            h.assertTrue(complete.getLongArray("TCCompletedChunks").length == 4, "all four clipped pieces survive reload without revalidating modified terrain");
            h.assertTrue(memory.states.values().stream().filter(state -> state.is(Blocks.IRON_BARS)).count() == 2,
                "bundled source geometry contains exactly two iron-bar cells");
            for (BlockPos pos : List.of(origin.offset(3, 9, 9), origin.offset(3, 10, 9))) {
                BlockState state = memory.state(pos);
                h.assertTrue(state.is(Blocks.IRON_BARS) && state.getValue(BlockStateProperties.NORTH)
                    && state.getValue(BlockStateProperties.SOUTH) && !state.getValue(BlockStateProperties.EAST)
                    && !state.getValue(BlockStateProperties.WEST), "original actual-state bars connect N/S, not E/W: " + pos);
            }
            var stairs = memory.states.values().stream().filter(state -> state.is(Blocks.COBBLESTONE_STAIRS)).toList();
            h.assertTrue(stairs.size() == 59 && stairs.stream().allMatch(state -> state.getValue(BlockStateProperties.STAIRS_SHAPE) == StairsShape.STRAIGHT),
                "all 59 stairs match the executed original actual-state reference on its plain baseline");
            BlockPos chestPos = origin.offset(10, 1, 9);
            var chest = (ChestBlockEntity) memory.entities.get(chestPos);
            h.assertTrue(chest != null && BuiltInLootTables.SIMPLE_DUNGEON.equals(chest.getLootTable())
                && chest.getLootTableSeed() == complete.getLong("TCChestSeed"), "actual chest receives source table and saved selected seed");
            h.assertTrue(memory.state(chestPos).is(complete.getBoolean("TCTrapped") ? Blocks.TRAPPED_CHEST : Blocks.CHEST), "saved trap choice changes actual chest block");
            if (complete.getBoolean("TCTrapped")) {
                h.assertTrue(memory.state(origin.offset(10, -1, 9)).is(Blocks.TNT), "trap TNT at original y=-1");
            }
            h.assertTrue(memory.entities.get(origin.offset(4, 5, 4)) instanceof SpawnerBlockEntity
                && memory.entities.get(origin.offset(4, 5, 14)) instanceof SpawnerBlockEntity, "both original spawner NBT entries create actual block entities");
            var skeleton = ((SpawnerBlockEntity) memory.entities.get(origin.offset(4, 5, 4))).getSpawner().save(new CompoundTag());
            var zombie = ((SpawnerBlockEntity) memory.entities.get(origin.offset(4, 5, 14))).getSpawner().save(new CompoundTag());
            h.assertTrue(skeleton.getCompound("SpawnData").getCompound("entity").getString("id").equals("minecraft:skeleton")
                && zombie.getCompound("SpawnData").getCompound("entity").getString("id").equals("minecraft:zombie"), "derived template retains exact skeleton/zombie spawn data");
            h.assertTrue(memory.state(origin.offset(9, 1, 7)).is(loot(complete.getInt("TCRarityA"), complete.getBoolean("TCCrateA")))
                && memory.state(origin.offset(9, 1, 11)).is(loot(complete.getInt("TCRarityB"), complete.getBoolean("TCCrateB"))), "both source loot positions retain saved choices");
            // Simulate an already-opened chest without asking a virtual fixture to roll server loot.
            chest.setLootTable(null);
            chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
            oldWrites = memory.writes;
            var reloaded = new TCMoundPiece(h.getLevel().getStructureManager(), complete);
            memory.owner.setUnsaved(false);
            process(reloaded, memory, h, generator, new ChunkPos(1, 1));
            h.assertTrue(memory.writes == oldWrites && chest.getItem(0).is(net.minecraft.world.item.Items.DIAMOND), "repeated saved postProcess never refreshes/opened chest contents");
            h.assertFalse(memory.owner.isUnsaved(), "already completed saved piece does not dirty owner");
        } finally {
            ModConfig.CONFIG_WORLD.generateStructure = oldGenerate;
            ModConfig.CONFIG_WORLD.worldgenPolicy = oldPolicy;
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void moundSourceDryBlocksReplaceCaveWaterWithoutTemplateWaterlogging(GameTestHelper h) {
        boolean oldGenerate = ModConfig.CONFIG_WORLD.generateStructure;
        WorldgenPolicy oldPolicy = ModConfig.CONFIG_WORLD.worldgenPolicy;
        try {
            ModConfig.CONFIG_WORLD.generateStructure = true;
            ModConfig.CONFIG_WORLD.worldgenPolicy = WorldgenPolicy.EMPTY;
            Memory memory = new Memory(h, 79);
            BlockPos origin = new BlockPos(12, 71, 12);
            BlockPos stairs = origin.offset(6, 4, 7);
            BlockPos ladder = origin.offset(7, 1, 9);
            BlockPos chest = origin.offset(10, 1, 9);
            for (BlockPos pos : List.of(stairs, ladder, chest)) memory.states.put(pos, Blocks.WATER.defaultBlockState());
            var piece = new TCMoundPiece(h.getLevel().getStructureManager(), origin, 17L, new ChunkPos(0, 0));
            process(piece, memory, h, generator(h), new ChunkPos(1, 1));
            h.assertTrue(memory.state(stairs).is(Blocks.COBBLESTONE_STAIRS) && memory.state(ladder).is(Blocks.LADDER)
                && (memory.state(chest).is(Blocks.CHEST) || memory.state(chest).is(Blocks.TRAPPED_CHEST)),
                "original stairs/ladder/chest coordinates replace existing water");
            for (BlockPos pos : List.of(stairs, ladder, chest)) {
                BlockState state = memory.state(pos);
                h.assertFalse(state.getValue(BlockStateProperties.WATERLOGGED), "source has no waterlogged blocks: " + pos);
                h.assertTrue(state.getFluidState().isEmpty(), "replacement is dry at " + pos);
            }
            h.assertTrue(memory.fluidReads == 0, "template never runs modern waterlogging preservation passes");
            h.assertTrue(memory.owner.isUnsaved(), "neighbor-first validation and completion dirty start owner");
        } finally {
            ModConfig.CONFIG_WORLD.generateStructure = oldGenerate;
            ModConfig.CONFIG_WORLD.worldgenPolicy = oldPolicy;
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void moundStairShapesUseUntouchedBackgroundButNeverPendingChunkTerrain(GameTestHelper h) {
        boolean oldGenerate = ModConfig.CONFIG_WORLD.generateStructure;
        WorldgenPolicy oldPolicy = ModConfig.CONFIG_WORLD.worldgenPolicy;
        try {
            ModConfig.CONFIG_WORLD.generateStructure = true;
            ModConfig.CONFIG_WORLD.worldgenPolicy = WorldgenPolicy.EMPTY;
            BlockPos origin = new BlockPos(9, 71, 12);
            Map<BlockPos, BlockState> expected = null;
            for (List<ChunkPos> order : List.of(
                List.of(new ChunkPos(0, 1), new ChunkPos(0, 0), new ChunkPos(1, 0), new ChunkPos(1, 1)),
                List.of(new ChunkPos(1, 1), new ChunkPos(1, 0), new ChunkPos(0, 0), new ChunkPos(0, 1)))) {
                Memory memory = new Memory(h, 79);
                BlockState external = Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM);
                memory.states.put(origin.offset(-1, 9, 8), external);
                memory.states.put(origin.offset(-1, 9, 10), external);
                // This cell is outside the sparse literal mask, but neither adjacent roof stair's
                // conditional original shape algorithm ever reads it (front/back are non-stairs).
                memory.states.put(origin.offset(10, 11, 7), external.setValue(StairBlock.HALF, Half.TOP));
                // Literal AIR across x=15/16 seam, deliberately still background stairs until its
                // owning chunk is placed: virtual template lookup must prevent a false inner corner.
                memory.states.put(origin.offset(7, 4, 7), external);
                var piece = new TCMoundPiece(h.getLevel().getStructureManager(), origin, 17, new ChunkPos(0, 0));
                for (ChunkPos chunk : order) {
                    process(piece, memory, h, generator(h), chunk);
                    if (chunk.equals(new ChunkPos(0, 1))) {
                        h.assertTrue(memory.state(origin.offset(6, 4, 7)).getValue(StairBlock.SHAPE) == StairsShape.STRAIGHT,
                            "pending neighbor literal AIR wins over current background stairs across the seam");
                    }
                }
                for (BlockPos pos : List.of(origin.offset(0, 9, 8), origin.offset(0, 9, 10))) {
                    h.assertTrue(memory.state(pos).getValue(StairBlock.SHAPE) == StairsShape.INNER_LEFT,
                        "source east-bottom stairs see untouched west north-bottom neighbor: " + pos);
                }
                for (BlockPos pos : List.of(origin.offset(10, 11, 6), origin.offset(11, 11, 7))) {
                    h.assertTrue(memory.state(pos).getValue(StairBlock.SHAPE) == StairsShape.STRAIGHT,
                        "unused lateral roof background cannot create a source corner");
                }
                h.assertTrue(memory.state(origin.offset(-1, 9, 8)).equals(external)
                    && memory.state(origin.offset(-1, 9, 10)).equals(external)
                    && memory.state(origin.offset(10, 11, 7)).equals(external.setValue(StairBlock.HALF, Half.TOP)),
                    "shape adapter never writes outside the literal template mask");
                if (expected == null) expected = Map.copyOf(memory.states);
                else h.assertTrue(expected.equals(memory.states), "clipped generation order cannot change final geometry or corner shapes");
            }
        } finally {
            ModConfig.CONFIG_WORLD.generateStructure = oldGenerate;
            ModConfig.CONFIG_WORLD.worldgenPolicy = oldPolicy;
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void moundRejectedRegisteredStartIsInvalidatedWithoutTouchingOtherStructures(GameTestHelper h) {
        boolean oldGenerate = ModConfig.CONFIG_WORLD.generateStructure;
        WorldgenPolicy oldPolicy = ModConfig.CONFIG_WORLD.worldgenPolicy;
        try {
            ModConfig.CONFIG_WORLD.generateStructure = true;
            ModConfig.CONFIG_WORLD.worldgenPolicy = WorldgenPolicy.EMPTY;
            // An unrequested, far-away coordinate: getChunkNow must remain null, not load a world chunk.
            // The production bounded owner is represented by a real ProtoChunk in this fixture.
            ChunkPos ownerPos = new ChunkPos(1_000_000, 1_000_000);
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(ownerPos.x, ownerPos.z) == null, "fixture owner is not a loaded FULL chunk");
            Memory memory = new Memory(h, 79, ownerPos);
            BlockPos origin = new BlockPos(12, 71, 12);
            memory.states.put(origin.offset(0, 8, 18), Blocks.SAND.defaultBlockState());
            var piece = new TCMoundPiece(h.getLevel().getStructureManager(), origin, 17, ownerPos);
            var registry = h.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
            Structure mound = registry.get(net.minecraft.resources.ResourceLocation.parse("thaumcraft:mound"));
            Structure unrelated = registry.get(net.minecraft.resources.ResourceLocation.parse("minecraft:desert_pyramid"));
            h.assertTrue(mound instanceof TCMoundStructure && unrelated != null, "actual registered mound and unrelated vanilla structure");
            var start = new StructureStart(mound, ownerPos, 0, new PiecesContainer(List.of(piece)));
            var otherStart = new StructureStart(unrelated, ownerPos, 0, new PiecesContainer(List.of(
                new TCMoundPiece(h.getLevel().getStructureManager(), origin, 99, ownerPos))));
            memory.owner.setStartForStructure(mound, start);
            memory.owner.setStartForStructure(unrelated, otherStart);
            memory.owner.setUnsaved(false);
            process(piece, memory, h, generator(h), new ChunkPos(1, 0));
            h.runAfterDelay(2, () -> {
                h.assertTrue(memory.owner.getStartForStructure(mound) == StructureStart.INVALID_START,
                    "final rejection replaces only its registered StructureStart with INVALID");
                h.assertTrue(memory.owner.getStartForStructure(unrelated) == otherStart, "unrelated structure start survives");
                h.assertTrue(memory.owner.isUnsaved() && memory.writes == 0, "INVALID owner is persisted without any geometry write");
                CompoundTag savedInvalid = memory.owner.getStartForStructure(mound).createTag(
                    StructurePieceSerializationContext.fromLevel(h.getLevel()), ownerPos);
                h.assertTrue(savedInvalid.getString("id").equals(StructureStart.INVALID_START_ID), "saved start stays INVALID, not a locate phantom");
                h.assertTrue(h.getLevel().getChunkSource().getChunkNow(ownerPos.x, ownerPos.z) == null, "invalidation never loads a FULL owner");
                h.succeed();
            });
        } finally {
            ModConfig.CONFIG_WORLD.generateStructure = oldGenerate;
            ModConfig.CONFIG_WORLD.worldgenPolicy = oldPolicy;
        }
    }

    @GameTest(template = "empty")
    public static void moundMinimumBuildHeightFloorRejectsBeforeTerrainReadsOrPlacement(GameTestHelper h) {
        boolean oldGenerate = ModConfig.CONFIG_WORLD.generateStructure;
        WorldgenPolicy oldPolicy = ModConfig.CONFIG_WORLD.worldgenPolicy;
        try {
            ModConfig.CONFIG_WORLD.generateStructure = true;
            ModConfig.CONFIG_WORLD.worldgenPolicy = WorldgenPolicy.EMPTY;
            Memory memory = new Memory(h, -55);
            var piece = new TCMoundPiece(h.getLevel().getStructureManager(), new BlockPos(12, -63, 12), 17, new ChunkPos(0, 0));
            process(piece, memory, h, generator(h), new ChunkPos(0, 0));
            h.assertTrue(piece.validationState() == 1 && memory.writes == 0 && memory.reads.isEmpty(),
                "template floor at minY cannot be silently clipped by the engine minY+1 box");
            h.assertTrue(memory.owner.isUnsaved(), "minimum-height whole-piece rejection is persisted");
        } finally {
            ModConfig.CONFIG_WORLD.generateStructure = oldGenerate;
            ModConfig.CONFIG_WORLD.worldgenPolicy = oldPolicy;
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void moundConfigBlacklistAndFifthGuardRejectBeforeAnyWrite(GameTestHelper h) {
        boolean oldGenerate = ModConfig.CONFIG_WORLD.generateStructure;
        WorldgenPolicy oldPolicy = ModConfig.CONFIG_WORLD.worldgenPolicy;
        try {
            var generator = generator(h);
            BlockPos origin = new BlockPos(12, 71, 12);
            for (int mode : new int[]{0, 1, 2, 7}) {
                ModConfig.CONFIG_WORLD.generateStructure = true;
                ModConfig.CONFIG_WORLD.worldgenPolicy = WorldgenPolicy.EMPTY.withDimension(net.minecraft.world.level.Level.OVERWORLD.location(), mode);
                Memory memory = new Memory(h, 79);
                var piece = new TCMoundPiece(h.getLevel().getStructureManager(), origin, 17, new ChunkPos(0, 0));
                process(piece, memory, h, generator, new ChunkPos(0, 0));
                h.assertTrue(memory.writes == 0 && memory.reads.isEmpty(), "dimension mode " + mode + " rejects before terrain reads/writes");
                h.assertTrue(piece.validationState() == 1 && memory.owner.isUnsaved(), "pending policy-blocked start becomes persisted rejection");
            }
            ModConfig.CONFIG_WORLD.worldgenPolicy = WorldgenPolicy.EMPTY;
            ModConfig.CONFIG_WORLD.generateStructure = false;
            Memory disabled = new Memory(h, 79);
            process(new TCMoundPiece(h.getLevel().getStructureManager(), origin, 17, new ChunkPos(0, 0)), disabled, h, generator, new ChunkPos(0, 0));
            h.assertTrue(disabled.writes == 0 && disabled.reads.isEmpty(), "disabled config rejects before terrain reads");
            h.assertTrue(disabled.owner.isUnsaved(), "pending disabled start is terminally rejected without terrain/RNG");
            ModConfig.CONFIG_WORLD.generateStructure = true;
            Memory invalid = new Memory(h, 79);
            invalid.states.put(origin.offset(0, 8, 18), Blocks.SAND.defaultBlockState());
            var rejected = new TCMoundPiece(h.getLevel().getStructureManager(), origin, 17, new ChunkPos(0, 0));
            process(rejected, invalid, h, generator, new ChunkPos(1, 0));
            h.assertTrue(invalid.writes == 0, "fifth guard failure cannot leave a partially written first chunk");
            h.assertTrue(invalid.owner.isUnsaved(), "rejection discovered by neighbor dirties actual start owner");
            h.assertTrue(rejected.createTag(StructurePieceSerializationContext.fromLevel(h.getLevel())).getInt("TCValidation") == 1,
                "whole-piece rejection is persisted, not rechecked against partial geometry");
            invalid.owner.setUnsaved(false);
            process(rejected, invalid, h, generator, new ChunkPos(1, 1));
            h.assertFalse(invalid.owner.isUnsaved(), "cached rejection is not an additional state mutation");
            Memory partial = new Memory(h, 79);
            var accepted = new TCMoundPiece(h.getLevel().getStructureManager(), origin, 17, new ChunkPos(0, 0));
            process(accepted, partial, h, generator, new ChunkPos(0, 0));
            int acceptedWrites = partial.writes;
            partial.owner.setUnsaved(false);
            ModConfig.CONFIG_WORLD.generateStructure = false;
            process(accepted, partial, h, generator, new ChunkPos(1, 0));
            h.assertTrue(accepted.validationState() == 2 && accepted.completedChunks().size() == 1
                && partial.writes == acceptedWrites && !partial.owner.isUnsaved(),
                "temporary config toggle pauses accepted partial mound, never invalidates it");
        } finally {
            ModConfig.CONFIG_WORLD.generateStructure = oldGenerate;
            ModConfig.CONFIG_WORLD.worldgenPolicy = oldPolicy;
        }
        h.succeed();
    }

    private static ChunkGenerator generator(GameTestHelper h) {
        return new NoiseBasedChunkGenerator(new FixedBiomeSource(h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS)),
            h.getLevel().registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD));
    }

    private static void process(TCMoundPiece piece, Memory memory, GameTestHelper h, ChunkGenerator generator, ChunkPos chunk) {
        var random = new LegacyRandomSource(0) {
            @Override public int nextInt(int bound) { throw new AssertionError("engine stream must not choose mound loot"); }
            @Override public float nextFloat() { throw new AssertionError("engine stream must not choose mound loot"); }
            @Override public long nextLong() { throw new AssertionError("engine stream must not seed mound chest"); }
        };
        piece.postProcess(memory.level, h.getLevel().structureManager(), generator, random,
            new BoundingBox(chunk.getMinBlockX(), -63, chunk.getMinBlockZ(), chunk.getMaxBlockX(), 319, chunk.getMaxBlockZ()), chunk, piece.legacyOrigin());
    }

    private static Block loot(int rarity, boolean crate) {
        return switch (rarity) {
            case 0 -> crate ? BlocksTC.lootCrateCommon : BlocksTC.lootUrnCommon;
            case 1 -> crate ? BlocksTC.lootCrateUncommon : BlocksTC.lootUrnUncommon;
            default -> crate ? BlocksTC.lootCrateRare : BlocksTC.lootUrnRare;
        };
    }

    private static final class Memory {
        final Map<BlockPos, BlockState> states = new HashMap<>();
        final Map<BlockPos, BlockEntity> entities = new HashMap<>();
        final List<BlockPos> reads = new ArrayList<>();
        final WorldGenLevel level;
        final ProtoChunk owner;
        final int ground;
        int writes;
        int fluidReads;

        Memory(GameTestHelper h, int ground) {
            this(h, ground, new ChunkPos(0, 0));
        }

        Memory(GameTestHelper h, int ground, ChunkPos ownerPos) {
            this.ground = ground;
            owner = new ProtoChunk(ownerPos, UpgradeData.EMPTY, LevelHeightAccessor.create(-64, 384),
                h.getLevel().registryAccess().registryOrThrow(Registries.BIOME), null);
            owner.setUnsaved(false);
            level = (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLevel" -> h.getLevel();
                    case "getChunk" -> {
                        h.assertTrue((Integer) args[0] == ownerPos.x && (Integer) args[1] == ownerPos.z && args[2] == ChunkStatus.STRUCTURE_STARTS,
                            "mound persistence requests only existing owner at STRUCTURE_STARTS, never FULL");
                        yield owner;
                    }
                    case "registryAccess" -> h.getLevel().registryAccess();
                    case "getMinBuildHeight" -> -64;
                    case "getMaxBuildHeight" -> 320;
                    case "getHeight" -> 384;
                    case "isOutsideBuildHeight" -> {
                        int y = args[0] instanceof BlockPos pos ? pos.getY() : (Integer) args[0];
                        yield y < -64 || y >= 320;
                    }
                    case "ensureCanWrite" -> true;
                    case "isEmptyBlock" -> {
                        BlockPos pos = ((BlockPos) args[0]).immutable(); reads.add(pos); yield state(pos).isAir();
                    }
                    case "getBlockState" -> {
                        BlockPos pos = ((BlockPos) args[0]).immutable(); reads.add(pos); yield state(pos);
                    }
                    case "getFluidState" -> {
                        fluidReads++;
                        yield state((BlockPos) args[0]).getFluidState();
                    }
                    case "getBlockEntity" -> entities.get((BlockPos) args[0]);
                    case "setBlock" -> {
                        BlockPos pos = ((BlockPos) args[0]).immutable();
                        BlockState state = (BlockState) args[1];
                        BlockState old = state(pos);
                        states.put(pos, state); writes++;
                        if (old.getBlock() != state.getBlock()) {
                            entities.remove(pos);
                        }
                        if (state.getBlock() instanceof EntityBlock entityBlock && !entities.containsKey(pos)) {
                            BlockEntity entity = entityBlock.newBlockEntity(pos, state);
                            if (entity != null) { entities.put(pos, entity); }
                        }
                        yield true;
                    }
                    default -> throw new AssertionError("Mound fixture tried unexpected world access: " + method);
                });
        }

        BlockState state(BlockPos pos) {
            return states.getOrDefault(pos, pos.getY() <= ground ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
    }

    private static final class Rolls extends LegacyRandomSource {
        final int[] bounds;
        final int[] values;
        int cursor;

        Rolls(int[] bounds, int[] values) { super(0); this.bounds = bounds; this.values = values; }
        @Override public int nextInt(int bound) {
            if (cursor >= bounds.length || bounds[cursor] != bound) { throw new AssertionError("unexpected candidate bound: " + bound); }
            return values[cursor++];
        }
        void exhausted(GameTestHelper h) { h.assertTrue(cursor == bounds.length, "all original candidate draws consumed, no extra draws"); }
    }
}
