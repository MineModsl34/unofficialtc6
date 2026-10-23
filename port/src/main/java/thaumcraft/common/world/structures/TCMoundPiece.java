package thaumcraft.common.world.structures;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.world.features.TCWorldgenBounds;

/**
 * Verified, derived NORMAL geometry with no rotation (west entrance), clipped to each FEATURES chunk.
 * Validation and random choices are shared, synchronized and persisted: a later chunk never rechecks
 * terrain modified by the first one, and a repeated postProcess never resets chest/spawner loot.
 */
public class TCMoundPiece extends TemplateStructurePiece {
    public static final ResourceLocation TEMPLATE = ResourceLocation.parse("thaumcraft:mound");
    public static final StructurePieceType.StructureTemplateType TYPE = TCMoundPiece::new;
    private static final long KIND_SEED_SALT = 0x5443364d4f554e44L;
    private final long worldSeed;
    private final ChunkPos startChunk;
    private final StairOverlay stairOverlay;
    private final Set<Long> completedChunks = new HashSet<>();
    private int validation; // 0 pending, 1 rejected, 2 accepted.
    private Contents contents;

    public TCMoundPiece(StructureTemplateManager manager, BlockPos legacyOrigin, long worldSeed, ChunkPos startChunk) {
        super(TYPE, 0, manager, TEMPLATE, TEMPLATE.toString(), settings(), legacyOrigin.below());
        this.worldSeed = worldSeed;
        this.startChunk = startChunk;
        requireTemplate();
        stairOverlay = readStairOverlay(template.save(new CompoundTag()));
    }

    public TCMoundPiece(StructureTemplateManager manager, CompoundTag tag) {
        super(TYPE, tag, manager, location -> settings());
        worldSeed = tag.getLong("TCWorldSeed");
        startChunk = new ChunkPos(tag.getInt("TCStartX"), tag.getInt("TCStartZ"));
        validation = tag.getInt("TCValidation");
        if (validation < 0 || validation > 2) {
            throw new IllegalArgumentException("Invalid saved mound validation state");
        }
        if (validation == 2) {
            contents = new Contents(tag.getInt("TCRarityA"), tag.getBoolean("TCCrateA"), tag.getInt("TCRarityB"),
                tag.getBoolean("TCCrateB"), tag.getBoolean("TCTrapped"), tag.getLong("TCChestSeed"));
        }
        for (long chunk : tag.getLongArray("TCCompletedChunks")) {
            completedChunks.add(chunk);
        }
        requireTemplate();
        stairOverlay = readStairOverlay(template.save(new CompoundTag()));
    }

    private static StructurePlaceSettings settings() {
        return new StructurePlaceSettings().setRotation(Rotation.NONE).setMirror(Mirror.NONE)
            .setIgnoreEntities(true).setKnownShape(true).setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
    }

    private void requireTemplate() {
        if (!template.getSize().equals(new Vec3i(19, 17, 19))) {
            throw new IllegalStateException("Verified thaumcraft:mound template must be 19x17x19, not " + template.getSize());
        }
    }

    public BlockPos legacyOrigin() {
        return templatePosition.above();
    }

    /** Audit-only snapshots; no generation, validation or placement is triggered by reading them. */
    public synchronized int validationState() {
        return validation;
    }

    public synchronized Set<Long> completedChunks() {
        return Set.copyOf(completedChunks);
    }

    @Override
    protected synchronized void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putLong("TCWorldSeed", worldSeed);
        tag.putInt("TCStartX", startChunk.x);
        tag.putInt("TCStartZ", startChunk.z);
        tag.putInt("TCValidation", validation);
        tag.putLongArray("TCCompletedChunks", completedChunks.stream().mapToLong(Long::longValue).sorted().toArray());
        if (contents != null) {
            tag.putInt("TCRarityA", contents.rarityA());
            tag.putBoolean("TCCrateA", contents.crateA());
            tag.putInt("TCRarityB", contents.rarityB());
            tag.putBoolean("TCCrateB", contents.crateB());
            tag.putBoolean("TCTrapped", contents.trapped());
            tag.putLong("TCChestSeed", contents.chestSeed());
        }
    }

    @Override
    public synchronized void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                                         RandomSource engineRandom, BoundingBox box, ChunkPos chunk, BlockPos pivot) {
        if (completedChunks.contains(chunk.toLong())) {
            return;
        }
        if (!allowsPlacement(level, generator)) {
            if (validation == 0 && completedChunks.isEmpty()) {
                validation = 1;
                markOwnerUnsaved(level);
                invalidateRejectedStart(level);
            }
            // A temporary config change cannot invalidate an already accepted partially built mound.
            return;
        }
        if (validation == 1) {
            invalidateRejectedStart(level);
            return;
        }
        if (validation == 0) {
            // All five guards and the whole template footprint are checked before the first write,
            // including when a neighboring chunk is the first one to reach FEATURES.
            if (!footprintAccessible(level) || !validLocation(level, legacyOrigin())) {
                validation = 1;
                markOwnerUnsaved(level);
                invalidateRejectedStart(level);
                return;
            }
            WorldgenRandom sourceRandom = sourceRandom(worldSeed, startChunk);
            sourceRandom.nextInt(9);
            sourceRandom.nextInt(9);
            sourceRandom.nextInt(100); // Reproduce the accepted start's generator stream without extra draws.
            WorldgenRandom kindRandom = sourceRandom(worldSeed ^ KIND_SEED_SALT, startChunk);
            contents = rollContents(sourceRandom, kindRandom);
            validation = 2;
            markOwnerUnsaved(level);
        }
        if (validation != 2) {
            return;
        }
        placeSettings.setBoundingBox(box);
        // Geometry/palette machinery has its own stream. In TC6 hardcoded geometry consumed no RNG.
        // The derived template deliberately has no chest NBT (vanilla otherwise adds nextLong).
        RandomSource geometryRandom = RandomSource.create(worldSeed ^ chunk.toLong() ^ 0x47454f4d45545259L);
        if (!template.placeInWorld(level, templatePosition, pivot, placeSettings, geometryRandom, 2)) {
            return;
        }
        applySourceStairShapes(level, box);
        placeLootBlock(level, box, legacyOrigin().offset(9, 1, 7), contents.rarityA(), contents.crateA());
        placeLootBlock(level, box, legacyOrigin().offset(9, 1, 11), contents.rarityB(), contents.crateB());
        BlockPos chest = legacyOrigin().offset(10, 1, 9);
        if (box.isInside(chest)) {
            level.setBlock(chest, (contents.trapped() ? Blocks.TRAPPED_CHEST : Blocks.CHEST).defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.WEST), 2);
            if (level.getBlockEntity(chest) instanceof ChestBlockEntity entity) {
                entity.setLootTable(BuiltInLootTables.SIMPLE_DUNGEON, contents.chestSeed());
                entity.setChanged();
            }
        }
        if (contents.trapped()) {
            BlockPos tnt = legacyOrigin().offset(10, -1, 9);
            if (box.isInside(tnt)) {
                level.setBlock(tnt, Blocks.TNT.defaultBlockState(), 2);
            }
        }
        completedChunks.add(chunk.toLong());
        markOwnerUnsaved(level);
    }

    private void markOwnerUnsaved(WorldGenLevel level) {
        // A neighboring FEATURES chunk can mutate this piece after its start chunk was saved.
        // Chunk block writes dirty only the written neighbor, not the StructureStart's owner.
        // STRUCTURE_STARTS is already in the bounded region dependency; do not load a FULL chunk.
        level.getChunk(startChunk.x, startChunk.z, ChunkStatus.STRUCTURE_STARTS).setUnsaved(true);
    }

    private static StairOverlay readStairOverlay(CompoundTag saved) {
        ListTag palette = saved.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> states = new ArrayList<>(palette.size());
        for (int index = 0; index < palette.size(); index++) {
            states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(index)));
        }
        Set<BlockPos> literal = new HashSet<>();
        Map<BlockPos, BlockState> stairs = new HashMap<>();
        for (Tag raw : saved.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            ListTag coordinates = entry.getList("pos", Tag.TAG_INT);
            BlockPos relative = new BlockPos(coordinates.getInt(0), coordinates.getInt(1), coordinates.getInt(2));
            literal.add(relative);
            BlockState state = states.get(entry.getInt("state"));
            if (state.getBlock() instanceof StairBlock) stairs.put(relative, state);
        }
        return new StairOverlay(Set.copyOf(literal), Map.copyOf(stairs));
    }

    private void applySourceStairShapes(WorldGenLevel level, BoundingBox box) {
        Function<BlockPos, BlockState> neighbors = pos -> {
            BlockPos relative = pos.subtract(templatePosition);
            BlockState ownStairs = stairOverlay.stairs().get(relative);
            if (ownStairs != null) return ownStairs;
            // Literal AIR/non-stairs must win even when that neighboring template chunk is not placed.
            // The old algorithm only asks whether these cells are stairs, so AIR is a sufficient sentinel.
            if (stairOverlay.literal().contains(relative) || !TCWorldgenBounds.canAccess(level, pos)) return Blocks.AIR.defaultBlockState();
            return level.getBlockState(pos); // Untouched background remains original-world-dependent.
        };
        for (var entry : stairOverlay.stairs().entrySet()) {
            BlockPos pos = templatePosition.offset(entry.getKey());
            if (!box.isInside(pos)) continue;
            StairsShape shape = sourceStairShape(entry.getValue(), pos, neighbors);
            BlockState actual = level.getBlockState(pos);
            if (actual.getBlock() instanceof StairBlock && actual.getValue(StairBlock.SHAPE) != shape) {
                level.setBlock(pos, actual.setValue(StairBlock.SHAPE, shape), 2);
            }
        }
    }

    /** Exact 1.12 BlockStairs.getActualState predicate, equivalent to modern getStairsShape. */
    private static StairsShape sourceStairShape(BlockState state, BlockPos pos, Function<BlockPos, BlockState> neighbors) {
        Direction facing = state.getValue(StairBlock.FACING);
        BlockState front = neighbors.apply(pos.relative(facing));
        if (front.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == front.getValue(StairBlock.HALF)) {
            Direction other = front.getValue(StairBlock.FACING);
            if (other.getAxis() != facing.getAxis() && differentStairs(state, neighbors.apply(pos.relative(other.getOpposite())))) {
                return other == facing.getCounterClockWise() ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
            }
        }
        BlockState back = neighbors.apply(pos.relative(facing.getOpposite()));
        if (back.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == back.getValue(StairBlock.HALF)) {
            Direction other = back.getValue(StairBlock.FACING);
            if (other.getAxis() != facing.getAxis() && differentStairs(state, neighbors.apply(pos.relative(other)))) {
                return other == facing.getCounterClockWise() ? StairsShape.INNER_LEFT : StairsShape.INNER_RIGHT;
            }
        }
        return StairsShape.STRAIGHT;
    }

    private static boolean differentStairs(BlockState state, BlockState other) {
        return !(other.getBlock() instanceof StairBlock) || other.getValue(StairBlock.FACING) != state.getValue(StairBlock.FACING)
            || other.getValue(StairBlock.HALF) != state.getValue(StairBlock.HALF);
    }

    private record StairOverlay(Set<BlockPos> literal, Map<BlockPos, BlockState> stairs) {}

    private void invalidateRejectedStart(WorldGenLevel level) {
        ChunkAccess capturedOwner = level.getChunk(startChunk.x, startChunk.z, ChunkStatus.STRUCTURE_STARTS);
        var serverLevel = level.getLevel();
        // Generation workers must never block/join the server. A loaded FULL owner may have replaced
        // the captured ProtoChunk before this callback executes, so prefer a non-loading current view.
        serverLevel.getServer().execute(() -> {
            ChunkAccess currentOwner = serverLevel.getChunkSource().getChunkNow(startChunk.x, startChunk.z);
            if (currentOwner == null) currentOwner = capturedOwner;
            if (currentOwner instanceof ImposterProtoChunk imposter) currentOwner = imposter.getWrapped();
            for (var entry : currentOwner.getAllStarts().entrySet()) {
                if (!(entry.getKey() instanceof TCMoundStructure)) continue;
                StructureStart current = currentOwner.getStartForStructure(entry.getKey());
                if (current != null && current.getPieces().stream().anyMatch(piece -> piece == this)) {
                    // Replace only this already-registered start. Never add phantom keys or invalidate
                    // another mound, an unrelated structure, or a regenerated replacement start.
                    currentOwner.setStartForStructure(entry.getKey(), StructureStart.INVALID_START);
                    currentOwner.setUnsaved(true);
                    serverLevel.onStructureStartsAvailable(currentOwner);
                }
            }
        });
    }

    private boolean footprintAccessible(WorldGenLevel level) {
        // The engine's FEATURES piece box begins at minY+1. Reject rather than silently clip the
        // template's y=-1 floor/TNT layer at minY (or a position below the world).
        if (templatePosition.getY() <= level.getMinBuildHeight()) return false;
        for (int x : new int[]{0, 18}) {
            for (int z : new int[]{0, 18}) {
                if (!TCWorldgenBounds.canAccess(level, templatePosition.offset(x, 0, z))
                    || !TCWorldgenBounds.canAccess(level, templatePosition.offset(x, 16, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean allowsPlacement(WorldGenLevel level, ChunkGenerator generator) {
        return ModConfig.CONFIG_WORLD.generateStructure && !(generator instanceof FlatLevelSource)
            && level.getLevel().dimension().equals(Level.OVERWORLD)
            && ModConfig.CONFIG_WORLD.worldgenPolicy.allowsStructures(level.getLevel().dimension().location());
    }

    /** Source checks center, NW, NE, SE, SW in precisely this short-circuit order. */
    public static boolean validLocation(WorldGenLevel level, BlockPos origin) {
        return validSpawnPoint(level, origin.offset(9, 9, 9))
            && validSpawnPoint(level, origin.offset(0, 9, 0))
            && validSpawnPoint(level, origin.offset(18, 9, 0))
            && validSpawnPoint(level, origin.offset(18, 9, 18))
            && validSpawnPoint(level, origin.offset(0, 9, 18));
    }

    public static boolean validSpawnPoint(WorldGenLevel level, BlockPos pos) {
        if (!TCWorldgenBounds.canAccess(level, pos.below()) || !TCWorldgenBounds.canAccess(level, pos.above(3))) {
            return false;
        }
        return validPoint(pos, level::getBlockState);
    }

    /** Start-phase raw column preflight; final FEATURES validation uses the same predicates on real terrain. */
    public static boolean validRawLocation(BlockPos origin, Function<BlockPos, BlockState> terrain) {
        return validPoint(origin.offset(9, 9, 9), terrain)
            && validPoint(origin.offset(0, 9, 0), terrain)
            && validPoint(origin.offset(18, 9, 0), terrain)
            && validPoint(origin.offset(18, 9, 18), terrain)
            && validPoint(origin.offset(0, 9, 18), terrain);
    }

    private static boolean validPoint(BlockPos pos, Function<BlockPos, BlockState> terrain) {
        int distanceToAir = 0;
        while (!terrain.apply(pos.above(distanceToAir)).isAir() && distanceToAir < 3) {
            distanceToAir++;
        }
        if (distanceToAir > 2) {
            return false;
        }
        BlockPos ground = pos.above(distanceToAir - 1);
        BlockState state = terrain.apply(ground);
        BlockState below = terrain.apply(ground.below());
        if (!terrain.apply(ground.above()).isAir()) {
            return false;
        }
        return validGround(state) || (state.is(Blocks.SNOW) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN)) && validGround(below);
    }

    private static boolean validGround(BlockState state) {
        // Legacy STONE and DIRT were blocks with metadata variants, now separate registry IDs.
        // Do not expand to modern stone/dirt tags: those would also admit deepslate and new soils.
        return state.is(Blocks.STONE) || state.is(Blocks.GRANITE) || state.is(Blocks.POLISHED_GRANITE)
            || state.is(Blocks.DIORITE) || state.is(Blocks.POLISHED_DIORITE)
            || state.is(Blocks.ANDESITE) || state.is(Blocks.POLISHED_ANDESITE)
            || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL);
    }

    private static WorldgenRandom sourceRandom(long seed, ChunkPos chunk) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0));
        random.setLargeFeatureSeed(seed, chunk.x, chunk.z);
        return random;
    }

    /** Two independent streams, as source rand versus world.rand; modern seeds replace global inter-feature interference. */
    public static Contents rollContents(RandomSource generatorRandom, RandomSource worldRandom) {
        int rarityA = rarity(generatorRandom.nextFloat());
        boolean crateA = worldRandom.nextFloat() < 0.3F;
        int rarityB = rarity(generatorRandom.nextFloat());
        boolean crateB = worldRandom.nextFloat() < 0.3F;
        boolean trapped = generatorRandom.nextInt(3) == 0;
        long chestSeed = generatorRandom.nextLong();
        return new Contents(rarityA, crateA, rarityB, crateB, trapped, chestSeed);
    }

    public static int rarity(float roll) {
        return roll < 0.1F ? 2 : roll < 0.33F ? 1 : 0;
    }

    private static void placeLootBlock(WorldGenLevel level, BoundingBox box, BlockPos pos, int rarity, boolean crate) {
        if (!box.isInside(pos)) {
            return;
        }
        Block block = switch (rarity) {
            case 0 -> crate ? BlocksTC.lootCrateCommon : BlocksTC.lootUrnCommon;
            case 1 -> crate ? BlocksTC.lootCrateUncommon : BlocksTC.lootUrnUncommon;
            case 2 -> crate ? BlocksTC.lootCrateRare : BlocksTC.lootUrnRare;
            default -> throw new IllegalArgumentException("Invalid saved mound loot rarity: " + rarity);
        };
        level.setBlock(pos, block.defaultBlockState(), 2);
    }

    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
        // Derived coordinate-exact source geometry has no data markers or structure-block placeholders.
    }

    public record Contents(int rarityA, boolean crateA, int rarityB, boolean crateB, boolean trapped, long chestSeed) {
        public Contents {
            if (rarityA < 0 || rarityA > 2 || rarityB < 0 || rarityB > 2) {
                throw new IllegalArgumentException("Mound rarity must be 0..2");
            }
        }
    }
}
