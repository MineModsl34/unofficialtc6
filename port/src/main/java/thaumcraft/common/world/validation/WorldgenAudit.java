package thaumcraft.common.world.validation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.blocks.world.BlockLootTC;
import thaumcraft.common.blocks.world.ore.BlockCrystal;
import thaumcraft.common.lib.TCAttachments;
import thaumcraft.common.world.aura.AuraChunk;
import thaumcraft.common.world.structures.TCMoundPiece;
import thaumcraft.common.world.structures.TCMoundStructure;

/**
 * Opt-in, bounded, real noise-world audit. This is dormant in every ordinary game/server.
 * It never invokes a configured feature manually, accepts the EULA, or opens a player's save.
 * The launch directory must have been freshly prepared by tools/prepare_worldgen_audit.ps1.
 * Counts and connected-log bounds are evidence, not visual/original-version acceptance.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class WorldgenAudit {
    public static final String MARKER = "TC_PORT_ISOLATED_WORLDGEN_AUDIT_V1";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static Run active;

    private WorldgenAudit() {
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        String reportPath = System.getProperty("thaumcraft.worldgenAudit");
        if (reportPath == null || reportPath.isBlank()) {
            return;
        }
        try {
            active = new Run(event.getServer(), Path.of(reportPath));
        } catch (Exception failure) {
            Thaumcraft.log.error("Worldgen audit refused/failed to initialize", failure);
            event.getServer().halt(false);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (active == null || event.getServer() != active.server) {
            return;
        }
        try {
            active.step();
        } catch (Exception failure) {
            active.finish("failed", failure.toString());
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        if (active != null && active.server == event.getServer()) {
            active.finish("interrupted", "Server stopped before the bounded audit completed");
        }
    }

    private static final class Run {
        private static final int MAX_CHUNKS = 2048;
        private final MinecraftServer server;
        private final ServerLevel level;
        private final Path reportPath;
        private final Path outputPath;
        private final boolean reload;
        private final int radius;
        private final long deadlineNanos;
        private final JsonObject report = new JsonObject();
        private final JsonArray regions = new JsonArray();
        private final JsonArray auditedChunks = new JsonArray();
        private final JsonArray auraProbes = new JsonArray();
        private final JsonArray moundReloadProbes = new JsonArray();
        private final ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
        private final Set<Long> planned = new LinkedHashSet<>();
        private final Set<Long> scanned = new HashSet<>();
        private final Set<Long> forcedProbes = new LinkedHashSet<>();
        private final Set<Long> forcedMoundStarts = new LinkedHashSet<>();
        private final Set<Long> forcedMoundFootprints = new LinkedHashSet<>();
        private final Map<Long, ChunkPos> moundStarts = new LinkedHashMap<>();
        private final Map<String, Long> counts = new LinkedHashMap<>();
        private final Map<String, Long> properties = new LinkedHashMap<>();
        private final Map<String, List<String>> samples = new LinkedHashMap<>();
        private final Map<String, Long> biomes = new LinkedHashMap<>();
        private final Map<BlockPos, String> logs = new HashMap<>();
        private long auraGenerated;
        private long auraAbsent;
        private long auraProbeMatches;
        private long auraProbeMismatches;
        private long moundProbeMatches;
        private long moundProbeMismatches;
        private long verifiedMounds;
        private long phantomMoundStarts;
        private long moundVerificationFailures;
        private long omittedByChunkBound;
        private boolean finished;

        private Run(MinecraftServer server, Path requestedReport) throws Exception {
            this.server = server;
            this.level = server.overworld();
            if (!server.isDedicatedServer()
                || !(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator)) {
                throw new IllegalStateException("Audit requires a dedicated server with a real noise-generated overworld");
            }
            Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path launchDirectory = world.getParent();
            if (!world.getFileName().toString().equals("worldgen-audit") || launchDirectory == null
                || !Files.readString(launchDirectory.resolve(".thaumcraft-worldgen-audit"), StandardCharsets.UTF_8).strip().equals(MARKER)) {
                throw new IllegalStateException("Missing isolation marker or unexpected world folder; no world audited");
            }
            this.reportPath = requestedReport.toAbsolutePath().normalize();
            if (!reportPath.getParent().equals(launchDirectory)) {
                throw new IllegalArgumentException("Report must be immediately inside the isolated launch directory");
            }
            String phase = System.getProperty("thaumcraft.worldgenAudit.phase", "generate");
            if (!phase.equals("generate") && !phase.equals("reload")) {
                throw new IllegalArgumentException("Audit phase must be generate or reload");
            }
            this.reload = phase.equals("reload");
            this.outputPath = reload ? reportPath.resolveSibling("reload-report.json") : reportPath;
            if (Files.exists(outputPath)) {
                throw new IllegalStateException("Refusing to replace an existing audit report: " + outputPath);
            }
            this.radius = boundedProperty("thaumcraft.worldgenAudit.radius", 4, 1, 10);
            this.deadlineNanos = System.nanoTime() + boundedProperty("thaumcraft.worldgenAudit.timeoutSeconds", 600, 60, 1800) * 1_000_000_000L;
            report.addProperty("phase", phase);
            report.addProperty("startedUtc", Instant.now().toString());
            report.addProperty("world", world.toString());
            report.addProperty("seed", level.getSeed());
            report.addProperty("generator", level.getChunkSource().getGenerator().getClass().getName());
            report.addProperty("scope", "Bounded natural feature/structure generation, actual mound placement evidence and saved aura/full-piece NBT reload; not visual inspection or a full comparison to running TC6");
            if (reload) {
                loadPrevious();
            } else {
                BlockPos spawn = level.getSharedSpawnPos();
                String centers = System.getProperty("thaumcraft.worldgenAudit.centers");
                if (centers == null) {
                    addRegion("spawn", spawn);
                    locateRegion("forest", spawn, holder -> holder.is(Tags.Biomes.IS_FOREST));
                    locateRegion("plains", spawn, holder -> holder.is(ResourceLocation.parse("minecraft:plains")));
                    locateRegion("hot_sandy", spawn, holder -> holder.is(Tags.Biomes.IS_SANDY) && holder.value().getBaseTemperature() > 1.0F);
                    locateRegion("magical_forest", spawn, holder -> holder.is(id("magical_forest")));
                    locateRegion("eerie", spawn, holder -> holder.is(id("eerie")));
                } else {
                    String[] coordinates = centers.split(",", -1);
                    if (coordinates.length < 1 || coordinates.length > 6) {
                        throw new IllegalArgumentException("Explicit audit centers require 1..6 comma-separated x:z block coordinates");
                    }
                    for (int index = 0; index < coordinates.length; index++) {
                        String[] axes = coordinates[index].strip().split(":", -1);
                        if (axes.length != 2) {
                            throw new IllegalArgumentException("Audit center must be x:z block coordinates");
                        }
                        int x = Integer.parseInt(axes[0].strip()), z = Integer.parseInt(axes[1].strip());
                        if (Math.abs((long) x) > 29_999_984L || Math.abs((long) z) > 29_999_984L) {
                            throw new IllegalArgumentException("Audit center outside the buildable world bound");
                        }
                        addRegion("explicit_center_" + index, new BlockPos(x, spawn.getY(), z));
                    }
                    report.addProperty("explicitCenters", centers);
                }
            }
            report.add("regions", regions);
            report.addProperty("requestedChunks", queue.size());
            Thaumcraft.log.info("Worldgen audit {}: {} chunks, report {}", phase, queue.size(), outputPath);
        }

        private void loadPrevious() throws Exception {
            JsonObject previous = JsonParser.parseString(Files.readString(reportPath, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!previous.get("phase").getAsString().equals("generate")
                || !previous.get("status").getAsString().equals("completed")
                || previous.get("seed").getAsLong() != level.getSeed()
                || !Path.of(previous.get("world").getAsString()).toAbsolutePath().normalize()
                    .equals(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize())) {
                throw new IllegalStateException("Reload requires a completed generation report with the identical seed");
            }
            JsonArray savedQueue = previous.getAsJsonArray("auditedChunks");
            if (savedQueue.isEmpty() || savedQueue.size() > MAX_CHUNKS || savedQueue.size() != previous.get("scannedChunks").getAsInt()) {
                throw new IllegalArgumentException("Saved audit queue exceeds bounds or its recorded count");
            }
            for (var entry : savedQueue) {
                JsonObject coordinate = entry.getAsJsonObject();
                ChunkPos chunk = new ChunkPos(coordinate.get("x").getAsInt(), coordinate.get("z").getAsInt());
                if (planned.contains(chunk.toLong()) || !inWorldChunkBounds(chunk)) {
                    throw new IllegalArgumentException("Duplicate or out-of-world saved audit chunk");
                }
                addChunk(chunk);
            }
            // ServerStarted is before the first normal tick: compare persisted values before aura can evolve.
            for (var entry : previous.getAsJsonArray("savedAuraProbes")) {
                JsonObject expected = entry.getAsJsonObject();
                requireSavedFullChunk(new ChunkPos(expected.get("x").getAsInt(), expected.get("z").getAsInt()));
                LevelChunk chunk = level.getChunk(expected.get("x").getAsInt(), expected.get("z").getAsInt());
                AuraChunk aura = chunk.getData(TCAttachments.AURA);
                boolean matches = aura.isGenerated() == expected.get("generated").getAsBoolean()
                    && aura.getBase() == expected.get("base").getAsShort()
                    && Float.floatToIntBits(aura.getVis()) == Float.floatToIntBits(expected.get("vis").getAsFloat())
                    && Float.floatToIntBits(aura.getFlux()) == Float.floatToIntBits(expected.get("flux").getAsFloat());
                if (matches) {
                    auraProbeMatches++;
                } else {
                    auraProbeMismatches++;
                }
                JsonObject comparison = auraJson(chunk);
                comparison.addProperty("matchesSaved", matches);
                auraProbes.add(comparison);
            }
            if (previous.has("naturalMoundEvidence")) {
                for (var entry : previous.getAsJsonArray("naturalMoundEvidence")) {
                    JsonObject expected = entry.getAsJsonObject();
                    ChunkPos coordinate = new ChunkPos(expected.get("startX").getAsInt(), expected.get("startZ").getAsInt());
                    CompoundTag savedChunk = requireSavedFullChunk(coordinate);
                    CompoundTag wanted = TagParser.parseTag(expected.get("serializedPieceNbt").getAsString());
                    var children = savedChunk.getCompound("structures").getCompound("starts")
                        .getCompound("thaumcraft:mound").getList("Children", Tag.TAG_COMPOUND);
                    boolean matches = false;
                    JsonArray actualPieces = new JsonArray();
                    for (Tag child : children) {
                        CompoundTag actual = (CompoundTag) child;
                        actualPieces.add(actual.toString());
                        matches |= wanted.equals(actual); // Compound equality is independent of SNBT/map key order.
                    }
                    JsonObject comparison = new JsonObject();
                    comparison.addProperty("startX", coordinate.x);
                    comparison.addProperty("startZ", coordinate.z);
                    comparison.addProperty("matchesSavedPiece", matches);
                    comparison.addProperty("expectedPieceNbt", wanted.toString());
                    comparison.add("actualSavedPiecesNbt", actualPieces);
                    comparison.addProperty("readMode", "Direct saved chunk NBT read before first tick; no structure placement or chunk generation");
                    moundReloadProbes.add(comparison);
                    if (matches) {
                        moundProbeMatches++;
                    } else {
                        moundProbeMismatches++;
                    }
                }
            }
        }

        private CompoundTag requireSavedFullChunk(ChunkPos coordinate) {
            if (!planned.contains(coordinate.toLong())) {
                throw new IllegalArgumentException("Reload probe lies outside the saved bounded audit queue");
            }
            CompoundTag saved = level.getChunkSource().chunkMap.read(coordinate).orTimeout(10, TimeUnit.SECONDS).join()
                .orElseThrow(() -> new IllegalStateException("Saved audit chunk missing: " + coordinate));
            if (!saved.getString("Status").equals("minecraft:full") || saved.getInt("xPos") != coordinate.x || saved.getInt("zPos") != coordinate.z) {
                throw new IllegalStateException("Refusing to regenerate a missing/incomplete audit chunk: " + coordinate);
            }
            return saved;
        }

        private void locateRegion(String name, BlockPos origin, Predicate<Holder<Biome>> predicate) {
            var result = level.findClosestBiome3d(predicate, origin, 6000, 64, 256);
            if (result == null) {
                JsonObject absent = new JsonObject();
                absent.addProperty("name", name);
                absent.addProperty("located", false);
                regions.add(absent);
            } else {
                addRegion(name, result.getFirst());
            }
        }

        private void addRegion(String name, BlockPos center) {
            JsonObject region = new JsonObject();
            region.addProperty("name", name);
            region.addProperty("located", true);
            region.addProperty("center", center.toShortString());
            region.addProperty("chunkRadius", radius);
            regions.add(region);
            ChunkPos chunk = new ChunkPos(center);
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    addChunk(new ChunkPos(chunk.x + dx, chunk.z + dz));
                }
            }
        }

        private void addChunk(ChunkPos position) {
            if (!inWorldChunkBounds(position)) {
                throw new IllegalArgumentException("Audit queue would leave the buildable world bound");
            }
            if (planned.contains(position.toLong())) {
                return;
            }
            if (planned.size() < MAX_CHUNKS && planned.add(position.toLong())) {
                queue.add(position);
            } else {
                omittedByChunkBound++;
            }
        }

        private static boolean inWorldChunkBounds(ChunkPos position) {
            return position.x >= -1_875_000 && position.x < 1_875_000
                && position.z >= -1_875_000 && position.z < 1_875_000;
        }

        private void step() {
            if (finished) {
                return;
            }
            if (System.nanoTime() >= deadlineNanos) {
                finish("timed_out", "Audit reached its configured time bound; partial evidence retained");
                return;
            }
            ChunkPos coordinate = queue.poll();
            if (coordinate == null) {
                boolean matches = auraProbeMismatches == 0 && moundProbeMismatches == 0;
                finish(matches ? "completed" : "failed", matches ? null : "Saved aura or mound piece probe mismatch");
                return;
            }
            if (reload) {
                // FULL saved chunks are mandatory: a missing entry must not be silently regenerated.
                requireSavedFullChunk(coordinate);
            }
            LevelChunk chunk = level.getChunk(coordinate.x, coordinate.z);
            // Only eight probes remain forced; retaining thousands of chunks would consume excessive memory.
            if (!reload && forcedProbes.size() < 8) {
                level.setChunkForced(coordinate.x, coordinate.z, true);
                forcedProbes.add(coordinate.toLong());
            }
            scan(chunk);
            scanned.add(coordinate.toLong());
            JsonObject recorded = new JsonObject();
            recorded.addProperty("x", coordinate.x);
            recorded.addProperty("z", coordinate.z);
            auditedChunks.add(recorded);
            if (scanned.size() % 25 == 0) {
                Thaumcraft.log.info("Worldgen audit: {}/{} chunks scanned; TC block counts {}", scanned.size(), planned.size(), counts);
            }
        }

        private void scan(LevelChunk chunk) {
            observeMoundStarts(chunk);
            ChunkPos cp = chunk.getPos();
            int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
            Holder<Biome> biome = chunk.getNoiseBiome((cp.x << 2) + 2, surface >> 2, (cp.z << 2) + 2);
            increment(biomes, biome.unwrapKey().map(key -> key.location().toString()).orElse("unbound"));
            AuraChunk aura = chunk.getData(TCAttachments.AURA);
            if (aura.isGenerated()) {
                auraGenerated++;
            } else {
                auraAbsent++;
            }
            LevelChunkSection[] sections = chunk.getSections();
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                if (section.hasOnlyAir() || !section.maybeHas(WorldgenAudit::isTCBlock)) {
                    continue;
                }
                int bottom = chunk.getMinBuildHeight() + sectionIndex * 16;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (!isTCBlock(state)) {
                                continue;
                            }
                            String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                            BlockPos pos = new BlockPos(cp.getMinBlockX() + x, bottom + y, cp.getMinBlockZ() + z);
                            increment(counts, id);
                            List<String> positions = samples.computeIfAbsent(id, ignored -> new ArrayList<>());
                            if (positions.size() < 6) {
                                positions.add(pos.toShortString() + " " + state);
                            }
                            if (state.getBlock() instanceof BlockCrystal) {
                                increment(properties, id + "/size/" + state.getValue(BlockCrystal.SIZE));
                                increment(properties, id + "/generation/" + state.getValue(BlockCrystal.GENERATION));
                                long attachments = BlockCrystal.FACES.values().stream().filter(state::getValue).count();
                                increment(properties, id + "/attachment_faces/" + attachments);
                            }
                            if (state.getBlock() instanceof LeavesBlock) {
                                increment(properties, id + "/persistent/" + state.getValue(LeavesBlock.PERSISTENT));
                                increment(properties, id + "/distance/" + state.getValue(LeavesBlock.DISTANCE));
                            }
                            if (id.equals("thaumcraft:log_greatwood") || id.equals("thaumcraft:log_silverwood")) {
                                logs.put(pos, id);
                                increment(properties, id + "/axis/" + state.getValue(RotatedPillarBlock.AXIS));
                            }
                        }
                    }
                }
            }
        }

        private void observeMoundStarts(LevelChunk chunk) {
            for (var entry : chunk.getAllStarts().entrySet()) {
                StructureStart start = entry.getValue();
                if (!(entry.getKey() instanceof TCMoundStructure) || !start.isValid()) {
                    continue;
                }
                ChunkPos startChunk = start.getChunkPos();
                if (moundStarts.putIfAbsent(startChunk.toLong(), startChunk) != null) {
                    continue;
                }
                // A bounded start + footprint ticket set keeps actual evidence loaded until saving.
                // No feature is forced manually: normal FULL generation owns FEATURES/postProcess.
                if (forcedMoundStarts.size() < 64) {
                    if (!planned.contains(startChunk.toLong())) {
                        throw new IllegalStateException("Mound start lies outside the bounded audit queue");
                    }
                    level.setChunkForced(startChunk.x, startChunk.z, true);
                    forcedMoundStarts.add(startChunk.toLong());
                }
                for (var piece : start.getPieces()) {
                    if (piece instanceof TCMoundPiece) {
                        Set<Long> footprint = footprint(piece.getBoundingBox());
                        for (long coordinate : footprint) {
                            if (!reload) {
                                addChunk(new ChunkPos(coordinate));
                            }
                        }
                        if (!forcedMoundStarts.contains(startChunk.toLong()) || !planned.containsAll(footprint)) {
                            continue; // A chunk/start cap cannot authorize a force outside the queue.
                        }
                        long additional = footprint.stream().filter(coordinate -> !forcedMoundFootprints.contains(coordinate)).count();
                        if (forcedMoundFootprints.size() + additional > 256) {
                            throw new IllegalStateException("Mound evidence footprint exceeds the bounded 256-chunk ticket cap");
                        }
                        for (long coordinate : footprint) {
                            ChunkPos pos = new ChunkPos(coordinate);
                            if (reload) {
                                requireSavedFullChunk(pos);
                            }
                            if (forcedMoundFootprints.add(coordinate)) {
                                level.setChunkForced(pos.x, pos.z, true);
                            }
                        }
                    }
                }
            }
        }

        private JsonArray moundEvidence() {
            JsonArray result = new JsonArray();
            var context = StructurePieceSerializationContext.fromLevel(level);
            for (ChunkPos startChunk : moundStarts.values()) {
                // Every start chunk was scanned already; reloading it is not a new generation request.
                LevelChunk startData = level.getChunkSource().getChunkNow(startChunk.x, startChunk.z);
                if (startData == null) {
                    throw new IllegalStateException("Audit start chunk is not loaded; evidence inspection refuses to generate/load it");
                }
                for (var entry : startData.getAllStarts().entrySet()) {
                    if (!(entry.getKey() instanceof TCMoundStructure) || !entry.getValue().isValid()) {
                        continue;
                    }
                    StructureStart start = entry.getValue();
                    for (var piece : start.getPieces()) {
                        if (!(piece instanceof TCMoundPiece mound)) {
                            continue;
                        }
                        JsonObject evidence = new JsonObject();
                        CompoundTag saved = mound.createTag(context);
                        BoundingBox box = mound.getBoundingBox();
                        BlockPos origin = mound.legacyOrigin();
                        Set<Long> needed = footprint(box);
                        Set<Long> completed = new HashSet<>();
                        for (long coordinate : saved.getLongArray("TCCompletedChunks")) {
                            completed.add(coordinate);
                        }
                        boolean fullyObserved = scanned.containsAll(needed);
                        boolean generationComplete = completed.containsAll(needed);
                        int validation = saved.getInt("TCValidation");
                        evidence.addProperty("structure", "thaumcraft:mound");
                        evidence.addProperty("startX", startChunk.x);
                        evidence.addProperty("startZ", startChunk.z);
                        evidence.addProperty("engineStartValid", start.isValid());
                        evidence.addProperty("legacyOrigin", origin.toShortString());
                        evidence.addProperty("bounds", box.toString());
                        evidence.addProperty("validation", validation);
                        evidence.addProperty("validationMeaning", switch (validation) {
                            case 0 -> "pending";
                            case 1 -> "rejected";
                            case 2 -> "accepted";
                            default -> "invalid";
                        });
                        evidence.add("requiredChunks", chunkCoordinates(needed));
                        evidence.add("completedChunks", chunkCoordinates(completed));
                        evidence.addProperty("fullyObserved", fullyObserved);
                        evidence.addProperty("generationComplete", generationComplete);
                        evidence.addProperty("serializedPieceNbt", saved.toString());
                        Map<String, Long> actualCounts = new LinkedHashMap<>();
                        for (int x = box.minX(); x <= box.maxX(); x++) {
                            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                                if (!scanned.contains(ChunkPos.asLong(x >> 4, z >> 4))) {
                                    continue;
                                }
                                for (int y = box.minY(); y <= box.maxY(); y++) {
                                    BlockState state = moundState(new BlockPos(x, y, z));
                                    increment(actualCounts, BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                                }
                            }
                        }
                        evidence.add("actualBlocksInObservedBounds", JSON.toJsonTree(actualCounts));
                        List<String> failures = new ArrayList<>();
                        boolean anySignature = actualCounts.getOrDefault("minecraft:chest", 0L)
                            + actualCounts.getOrDefault("minecraft:trapped_chest", 0L)
                            + actualCounts.getOrDefault("minecraft:spawner", 0L) > 0
                            || actualCounts.keySet().stream().anyMatch(id -> id.startsWith("thaumcraft:loot_"));
                        // A rejected/pending valid start is not made real by an unrelated vanilla dungeon
                        // or another structure inside its bounds. All footprint chunks are FULL at this point.
                        boolean phantom = fullyObserved && (validation != 2 || !generationComplete || !anySignature);
                        evidence.addProperty("phantomOrIncompleteValidStart", phantom);
                        if (phantom) {
                            phantomMoundStarts++;
                            failures.add("Engine-valid start has no verified complete actual placement: validation="
                                + validation + ", completed footprint=" + completed.size() + "/" + needed.size());
                        }
                        if (fullyObserved && validation == 2 && generationComplete) {
                            if (box.getXSpan() != 19 || box.getYSpan() != 17 || box.getZSpan() != 19) {
                                failures.add("Unexpected template bounds");
                            }
                            verifyMoundChest(origin.offset(10, 1, 9), saved, evidence, failures);
                            verifyMoundSpawner(origin.offset(4, 5, 4), "minecraft:skeleton", evidence, failures);
                            verifyMoundSpawner(origin.offset(4, 5, 14), "minecraft:zombie", evidence, failures);
                            verifyMoundLoot(origin.offset(9, 1, 7), saved.getInt("TCRarityA"), saved.getBoolean("TCCrateA"), "lootA", evidence, failures);
                            verifyMoundLoot(origin.offset(9, 1, 11), saved.getInt("TCRarityB"), saved.getBoolean("TCCrateB"), "lootB", evidence, failures);
                            verifyMoundShapes(origin, evidence, failures);
                            boolean actualTnt = moundState(origin.offset(10, -1, 9)).is(Blocks.TNT);
                            evidence.addProperty("actualTntAtTrapCoordinate", actualTnt);
                            if (actualTnt != saved.getBoolean("TCTrapped")) {
                                failures.add("TNT placement differs from saved trap choice");
                            }
                            // Extra natural blocks/entities in untouched background do not belong to the
                            // literal source mask; the five exact source coordinates are checked above.
                            if (failures.isEmpty()) {
                                verifiedMounds++;
                            } else {
                                moundVerificationFailures++;
                            }
                        }
                        evidence.addProperty("verifiedActualCompletePlacement", fullyObserved && validation == 2
                            && generationComplete && failures.isEmpty() && !phantom);
                        evidence.add("failures", JSON.toJsonTree(failures));
                        result.add(evidence);
                    }
                }
            }
            return result;
        }

        private void verifyMoundChest(BlockPos pos, CompoundTag saved, JsonObject evidence, List<String> failures) {
            boolean trapped = saved.getBoolean("TCTrapped");
            BlockState actual = moundState(pos);
            evidence.addProperty("actualChestBlock", BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString());
            if (!actual.is(trapped ? Blocks.TRAPPED_CHEST : Blocks.CHEST)) {
                failures.add("Chest block differs from saved trap choice");
            }
            if (moundEntity(pos) instanceof ChestBlockEntity chest) {
                evidence.addProperty("actualChestNbt", chest.saveWithId(level.registryAccess()).toString());
                if (!BuiltInLootTables.SIMPLE_DUNGEON.equals(chest.getLootTable()) || chest.getLootTableSeed() != saved.getLong("TCChestSeed")) {
                    failures.add("Actual chest loot table/seed differs from saved contents");
                }
            } else {
                failures.add("No actual chest block entity");
            }
        }

        private void verifyMoundSpawner(BlockPos pos, String entityId, JsonObject evidence, List<String> failures) {
            if (!moundState(pos).is(Blocks.SPAWNER) || !(moundEntity(pos) instanceof SpawnerBlockEntity spawner)) {
                failures.add("No actual " + entityId + " spawner at " + pos.toShortString());
                return;
            }
            CompoundTag nbt = spawner.saveWithId(level.registryAccess());
            evidence.addProperty(entityId.substring(entityId.indexOf(':') + 1) + "SpawnerNbt", nbt.toString());
            if (!nbt.getCompound("SpawnData").getCompound("entity").getString("id").equals(entityId)) {
                failures.add("Spawner entity differs from " + entityId);
            }
            Map<String, Integer> settings = Map.of("MaxNearbyEntities", 6, "RequiredPlayerRange", 16,
                "SpawnCount", 4, "SpawnRange", 4, "MinSpawnDelay", 200, "MaxSpawnDelay", 800);
            settings.forEach((key, value) -> {
                if (nbt.getInt(key) != value) {
                    failures.add("Spawner setting differs: " + entityId + "/" + key);
                }
            });
        }

        private void verifyMoundLoot(BlockPos pos, int rarity, boolean crate, String name, JsonObject evidence, List<String> failures) {
            BlockState state = moundState(pos);
            evidence.addProperty(name, BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            if (!(state.getBlock() instanceof BlockLootTC container) || container.rarity().index() != rarity || container.isCrate() != crate) {
                failures.add("Actual loot container differs from saved choice: " + name);
            }
        }

        private void verifyMoundShapes(BlockPos origin, JsonObject evidence, List<String> failures) {
            int verifiedBars = 0;
            for (int y : new int[]{9, 10}) {
                BlockState bar = moundState(origin.offset(3, y, 9));
                if (bar.is(Blocks.IRON_BARS) && bar.getValue(BlockStateProperties.NORTH) && bar.getValue(BlockStateProperties.SOUTH)
                    && !bar.getValue(BlockStateProperties.EAST) && !bar.getValue(BlockStateProperties.WEST)) {
                    verifiedBars++;
                } else {
                    failures.add("Iron bars differ from executed original getActualState at y=" + y);
                }
            }
            var template = server.getStructureManager().getOrCreate(TCMoundPiece.TEMPLATE);
            var stairs = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.COBBLESTONE_STAIRS);
            Set<BlockPos> literalMask = new HashSet<>();
            for (Tag entry : template.save(new CompoundTag()).getList("blocks", Tag.TAG_COMPOUND)) {
                var pos = ((CompoundTag) entry).getList("pos", Tag.TAG_INT);
                literalMask.add(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)));
            }
            int straight = 0;
            JsonArray externalShapes = new JsonArray();
            for (var entry : stairs) {
                BlockState state = moundState(origin.below().offset(entry.pos()));
                boolean sameOrientation = state.is(Blocks.COBBLESTONE_STAIRS)
                    && state.getValue(StairBlock.FACING) == entry.state().getValue(StairBlock.FACING)
                    && state.getValue(StairBlock.HALF) == entry.state().getValue(StairBlock.HALF);
                if (sameOrientation && state.getValue(StairBlock.SHAPE) == StairsShape.STRAIGHT) {
                    straight++;
                } else if (sameOrientation) {
                    // The controlled original fixture has 59 straight stairs, but a natural entrance
                    // may legally meet an external stair. Such shape cases are not fixture mismatches.
                    boolean externalStair = false, unknownExternalContext = false;
                    for (Direction direction : Direction.Plane.HORIZONTAL) {
                        BlockPos local = entry.pos().relative(direction);
                        if (literalMask.contains(local)) {
                            continue;
                        }
                        BlockPos world = origin.below().offset(local);
                        ChunkPos coordinate = new ChunkPos(world);
                        LevelChunk observed = scanned.contains(coordinate.toLong())
                            ? level.getChunkSource().getChunkNow(coordinate.x, coordinate.z) : null;
                        if (observed == null) {
                            unknownExternalContext = true; // Do not load/generate an extra context chunk.
                        } else if (StairBlock.isStairs(observed.getBlockState(world))) {
                            externalStair = true;
                        }
                    }
                    if (externalStair || unknownExternalContext) {
                        JsonObject contextual = new JsonObject();
                        contextual.addProperty("templatePosition", entry.pos().toShortString());
                        contextual.addProperty("actualShape", state.getValue(StairBlock.SHAPE).getSerializedName());
                        contextual.addProperty("externalStairObserved", externalStair);
                        contextual.addProperty("externalContextUnobserved", unknownExternalContext);
                        externalShapes.add(contextual);
                    } else {
                        failures.add("Stair shape differs from original fixture without an external stair context at " + entry.pos());
                    }
                } else {
                    failures.add("Stair block/orientation differs from executed original actual-state fixture at " + entry.pos());
                }
            }
            if (stairs.size() != 59) {
                failures.add("Canonical mound asset must contain 59 source stair coordinates");
            }
            evidence.addProperty("verifiedConnectedIronBars", verifiedBars);
            evidence.addProperty("verifiedStraightStairs", straight);
            evidence.add("stairShapesWithExternalContext", externalShapes);
        }

        private LevelChunk observedMoundChunk(BlockPos pos) {
            ChunkPos coordinate = new ChunkPos(pos);
            if (!scanned.contains(coordinate.toLong())) {
                throw new IllegalStateException("Mound evidence position outside scanned queue");
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(coordinate.x, coordinate.z);
            if (chunk == null) {
                throw new IllegalStateException("Mound evidence inspection refuses to generate/load an unloaded footprint chunk");
            }
            return chunk;
        }

        private BlockState moundState(BlockPos pos) {
            return observedMoundChunk(pos).getBlockState(pos);
        }

        private BlockEntity moundEntity(BlockPos pos) {
            return observedMoundChunk(pos).getBlockEntity(pos);
        }

        private static Set<Long> footprint(BoundingBox box) {
            Set<Long> result = new LinkedHashSet<>();
            for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
                for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                    result.add(ChunkPos.asLong(x, z));
                }
            }
            return result;
        }

        private static JsonArray chunkCoordinates(Set<Long> chunks) {
            JsonArray result = new JsonArray();
            chunks.stream().sorted().forEach(coordinate -> {
                ChunkPos pos = new ChunkPos(coordinate);
                JsonObject json = new JsonObject();
                json.addProperty("x", pos.x);
                json.addProperty("z", pos.z);
                result.add(json);
            });
            return result;
        }

        private JsonArray logComponents() {
            JsonArray components = new JsonArray();
            Set<BlockPos> remaining = new HashSet<>(logs.keySet());
            while (!remaining.isEmpty()) {
                BlockPos start = remaining.iterator().next();
                String tree = logs.get(start);
                ArrayDeque<BlockPos> pending = new ArrayDeque<>();
                remaining.remove(start);
                pending.add(start);
                int[] min = {start.getX(), start.getY(), start.getZ()};
                int[] max = min.clone();
                int size = 0;
                boolean possiblyClipped = false;
                while (!pending.isEmpty()) {
                    BlockPos pos = pending.remove();
                    size++;
                    min[0] = Math.min(min[0], pos.getX());
                    min[1] = Math.min(min[1], pos.getY());
                    min[2] = Math.min(min[2], pos.getZ());
                    max[0] = Math.max(max[0], pos.getX());
                    max[1] = Math.max(max[1], pos.getY());
                    max[2] = Math.max(max[2], pos.getZ());
                    for (Direction direction : Direction.values()) {
                        BlockPos next = pos.relative(direction);
                        if (!scanned.contains(new ChunkPos(next).toLong())) {
                            possiblyClipped = true;
                        }
                        if (tree.equals(logs.get(next)) && remaining.remove(next)) {
                            pending.add(next);
                        }
                    }
                }
                JsonObject component = new JsonObject();
                component.addProperty("block", tree);
                component.addProperty("logCount", size);
                component.addProperty("min", new BlockPos(min[0], min[1], min[2]).toShortString());
                component.addProperty("max", new BlockPos(max[0], max[1], max[2]).toShortString());
                component.addProperty("height", max[1] - min[1] + 1);
                component.addProperty("possiblyClippedByAuditBoundary", possiblyClipped);
                components.add(component);
            }
            return components;
        }

        private void finish(String status, String detail) {
            if (finished) {
                return;
            }
            finished = true;
            active = null;
            try {
                JsonArray naturalMounds;
                try {
                    naturalMounds = moundEvidence();
                } catch (Exception failure) {
                    // Keep the ore/tree/aura evidence even if a malformed start cannot be inspected.
                    naturalMounds = new JsonArray();
                    moundVerificationFailures++;
                    report.addProperty("moundEvidenceError", failure.toString());
                }
                if (status.equals("completed") && (moundVerificationFailures > 0 || phantomMoundStarts > 0)) {
                    status = "failed";
                    detail = "Natural mound start had missing or inconsistent actual placement evidence";
                }
                if (!reload) {
                    for (long coordinate : forcedProbes) {
                        ChunkPos pos = new ChunkPos(coordinate);
                        auraProbes.add(auraJson(level.getChunk(pos.x, pos.z)));
                        level.setChunkForced(pos.x, pos.z, false);
                    }
                }
                for (long coordinate : forcedMoundStarts) {
                    ChunkPos pos = new ChunkPos(coordinate);
                    level.setChunkForced(pos.x, pos.z, false);
                }
                for (long coordinate : forcedMoundFootprints) {
                    ChunkPos pos = new ChunkPos(coordinate);
                    level.setChunkForced(pos.x, pos.z, false);
                }
                boolean saved = server.saveEverything(true, true, true);
                report.addProperty("status", status);
                report.addProperty("finishedUtc", Instant.now().toString());
                report.addProperty("saveFlushed", saved);
                if (detail != null) {
                    report.addProperty("detail", detail);
                }
                report.addProperty("scannedChunks", scanned.size());
                report.addProperty("remainingChunks", queue.size());
                report.addProperty("totalPlannedChunks", planned.size());
                report.addProperty("omittedByChunkBound", omittedByChunkBound);
                report.addProperty("generatedAuraChunks", auraGenerated);
                report.addProperty("absentAuraChunks", auraAbsent);
                report.addProperty("auraProbeMatches", auraProbeMatches);
                report.addProperty("auraProbeMismatches", auraProbeMismatches);
                report.addProperty("moundProbeMatches", moundProbeMatches);
                report.addProperty("moundProbeMismatches", moundProbeMismatches);
                report.addProperty("heldMoundStartChunks", forcedMoundStarts.size());
                report.addProperty("heldMoundFootprintChunks", forcedMoundFootprints.size());
                report.add("savedMoundReloadProbes", moundReloadProbes);
                report.add("auditedChunks", auditedChunks);
                report.add("savedAuraProbes", auraProbes);
                report.add("blocks", JSON.toJsonTree(counts));
                report.add("blockProperties", JSON.toJsonTree(properties));
                report.add("samples", JSON.toJsonTree(samples));
                report.add("surfaceBiomes", JSON.toJsonTree(biomes));
                report.add("connectedLogComponents", logComponents());
                report.add("naturalMoundEvidence", naturalMounds);
                report.addProperty("verifiedNaturallyPlacedMounds", verifiedMounds);
                report.addProperty("phantomMoundStarts", phantomMoundStarts);
                report.addProperty("moundVerificationFailures", moundVerificationFailures);
                report.addProperty("moundNote", "Only accepted, fully scanned and fully completed pieces with actual expected blocks/entities count as placed; a registered valid start alone is not evidence. Bounds counts include untouched natural terrain, unlike the private literal-mask fixture.");
                report.addProperty("moundShapeNote", "Two source iron bars must connect north/south. Original controlled fixtures contain 59 straight stairs; natural non-straight stairs are separately reported, not rejected solely for shape when an external stair or unobserved external context can affect them.");
                report.addProperty("shapeNote", "Connected log groups can merge touching trees or omit disconnected branches; bounds are measurements, not a shape verdict");
                Files.writeString(outputPath, JSON.toJson(report), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                Thaumcraft.log.info("Worldgen audit {}: {} chunks, report {}", status, scanned.size(), outputPath);
            } catch (Exception failure) {
                Thaumcraft.log.error("Unable to finish/save worldgen audit", failure);
            } finally {
                server.halt(false);
            }
        }
    }

    private static boolean isTCBlock(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(Thaumcraft.MODID);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, path);
    }

    private static int boundedProperty(String name, int fallback, int minimum, int maximum) {
        int value = Integer.parseInt(System.getProperty(name, Integer.toString(fallback)));
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static void increment(Map<String, Long> counts, String key) {
        counts.merge(key, 1L, Long::sum);
    }

    private static JsonObject auraJson(LevelChunk chunk) {
        AuraChunk aura = chunk.getData(TCAttachments.AURA);
        JsonObject probe = new JsonObject();
        probe.addProperty("x", chunk.getPos().x);
        probe.addProperty("z", chunk.getPos().z);
        probe.addProperty("generated", aura.isGenerated());
        probe.addProperty("base", aura.getBase());
        probe.addProperty("vis", aura.getVis());
        probe.addProperty("flux", aura.getFlux());
        return probe;
    }
}
