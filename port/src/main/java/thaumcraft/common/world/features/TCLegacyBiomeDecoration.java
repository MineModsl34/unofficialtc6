package thaumcraft.common.world.features;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/** Vegetation loops inherited from the 1.12 BiomeDecorator, without duplicating modern ores/springs. */
public final class TCLegacyBiomeDecoration {
    // TC6 used the single-octave GRASS_COLOR_NOISE initialized with java Random(2345).
    private static final SimplexNoise FLOWER_NOISE = new SimplexNoise(new LegacyRandomSource(2345L));
    private static final Block[] FLOWERS = {
        Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID, Blocks.ALLIUM, Blocks.AZURE_BLUET,
        Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP, Blocks.OXEYE_DAISY
    };

    private TCLegacyBiomeDecoration() {
    }

    public static BlockPos surface(WorldGenLevel level, BlockPos pos) {
        // WG heightmaps stop updating after CARVERS. Decoration needs the live, foliage-aware map:
        // subsequent flowers and mushrooms in TC6 sampled the height after earlier trees were placed.
        return new BlockPos(pos.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()), pos.getZ());
    }

    public static boolean canAccess(WorldGenLevel level, BlockPos pos) {
        return TCWorldgenBounds.canAccess(level, pos);
    }

    /** Legacy patch sizes and random-call order; modern block survival contracts supply plant support. */
    public static boolean decoratePlants(WorldGenLevel level, RandomSource random, BlockPos chunk,
                                         int flowers, int grass, int lilies, int mushrooms, boolean magical) {
        boolean placed = false;
        for (int attempt = 0; attempt < flowers; attempt++) {
            BlockPos column = chunk.offset(random.nextInt(16) + 8, 0, random.nextInt(16) + 8);
            int bound = surface(level, column).getY() + 32;
            if (bound > 0) {
                BlockPos pos = new BlockPos(column.getX(), random.nextInt(bound), column.getZ());
                Block flower = magical ? forestFlower(pos) : random.nextInt(3) > 0 ? Blocks.DANDELION : Blocks.POPPY;
                placed |= plantPatch(level, random, pos, flower.defaultBlockState(), 64, false);
            }
        }
        for (int attempt = 0; attempt < grass; attempt++) {
            BlockPos column = chunk.offset(random.nextInt(16) + 8, 0, random.nextInt(16) + 8);
            int bound = surface(level, column).getY() * 2;
            if (bound > 0) {
                BlockPos pos = new BlockPos(column.getX(), random.nextInt(bound), column.getZ());
                Block plant = magical && random.nextInt(4) == 0 ? Blocks.FERN : Blocks.SHORT_GRASS;
                placed |= plantPatch(level, random, pos, plant.defaultBlockState(), 128, true);
            }
        }
        for (int attempt = 0; attempt < lilies; attempt++) {
            BlockPos column = chunk.offset(random.nextInt(16) + 8, 0, random.nextInt(16) + 8);
            int bound = surface(level, column).getY() * 2;
            if (bound > 0) {
                BlockPos pos = new BlockPos(column.getX(), random.nextInt(bound), column.getZ());
                while (pos.getY() > 0 && canAccess(level, pos.below()) && level.isEmptyBlock(pos.below())) {
                    pos = pos.below();
                }
                placed |= lilyPatch(level, random, pos);
            }
        }
        for (int attempt = 0; attempt < mushrooms; attempt++) {
            if (random.nextInt(4) == 0) {
                BlockPos column = chunk.offset(random.nextInt(16) + 8, 0, random.nextInt(16) + 8);
                placed |= plantPatch(level, random, surface(level, column), Blocks.BROWN_MUSHROOM.defaultBlockState(), 64, false);
            }
            if (random.nextInt(8) == 0) {
                placed |= mushroomAtRandomHeight(level, random, chunk, Blocks.RED_MUSHROOM);
            }
        }
        // Baseline mushroom attempts exist even when mushroomsPerChunk is zero (eerie).
        if (random.nextInt(4) == 0) {
            placed |= mushroomAtRandomHeight(level, random, chunk, Blocks.BROWN_MUSHROOM);
        }
        if (random.nextInt(8) == 0) {
            placed |= mushroomAtRandomHeight(level, random, chunk, Blocks.RED_MUSHROOM);
        }
        // BiomeDecorator always made these ten reed attempts in addition to reedsPerChunk (zero here).
        for (int attempt = 0; attempt < 10; attempt++) {
            BlockPos column = chunk.offset(random.nextInt(16) + 8, 0, random.nextInt(16) + 8);
            int bound = surface(level, column).getY() * 2;
            if (bound > 0) {
                placed |= reedPatch(level, random, new BlockPos(column.getX(), random.nextInt(bound), column.getZ()));
            }
        }
        if (random.nextInt(32) == 0) {
            BlockPos column = chunk.offset(random.nextInt(16) + 8, 0, random.nextInt(16) + 8);
            int bound = surface(level, column).getY() * 2;
            if (bound > 0) {
                placed |= pumpkinPatch(level, random, new BlockPos(column.getX(), random.nextInt(bound), column.getZ()));
            }
        }
        return placed;
    }

    public static Block forestFlower(BlockPos pos) {
        double value = Mth.clamp((1.0 + FLOWER_NOISE.getValue(pos.getX() / 48.0, pos.getZ() / 48.0)) / 2.0, 0.0, 0.9999);
        return FLOWERS[(int)(value * FLOWERS.length)];
    }

    public static boolean plantPatch(WorldGenLevel level, RandomSource random, BlockPos origin,
                                     BlockState state, int attempts, boolean descend) {
        BlockPos center = origin;
        if (descend) {
            while (center.getY() > 0 && canAccess(level, center)) {
                BlockState existing = level.getBlockState(center);
                if (!existing.isAir() && !existing.is(BlockTags.LEAVES)) {
                    break;
                }
                center = center.below();
            }
        }
        boolean placed = false;
        for (int attempt = 0; attempt < attempts; attempt++) {
            BlockPos pos = triangularOffset(center, random);
            if (canAccess(level, pos) && canAccess(level, pos.below()) && level.isEmptyBlock(pos)
                && state.canSurvive(level, pos)) {
                placed |= level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            }
        }
        return placed;
    }

    private static boolean mushroomAtRandomHeight(WorldGenLevel level, RandomSource random, BlockPos chunk, Block mushroom) {
        BlockPos column = chunk.offset(random.nextInt(16) + 8, 0, random.nextInt(16) + 8);
        int bound = surface(level, column).getY() * 2;
        return bound > 0 && plantPatch(level, random, new BlockPos(column.getX(), random.nextInt(bound), column.getZ()),
            mushroom.defaultBlockState(), 64, false);
    }

    private static BlockPos triangularOffset(BlockPos origin, RandomSource random) {
        return origin.offset(random.nextInt(8) - random.nextInt(8), random.nextInt(4) - random.nextInt(4),
            random.nextInt(8) - random.nextInt(8));
    }

    private static boolean lilyPatch(WorldGenLevel level, RandomSource random, BlockPos origin) {
        boolean placed = false;
        for (int attempt = 0; attempt < 10; attempt++) {
            BlockPos pos = triangularOffset(origin, random);
            if (!canAccess(level, pos) || !canAccess(level, pos.below()) || !level.isEmptyBlock(pos)) {
                continue;
            }
            BlockState below = level.getBlockState(pos.below());
            // Forge's canPlaceBlockAt -> canSustainPlant -> canSustainBush allowed stationary WATER
            // (without a level check) and Material.ICE, not the distinct Material.PACKED_ICE.
            // Modern water merges the old flowing/stationary block distinction.
            if (below.is(Blocks.WATER) || below.is(Blocks.ICE) || below.is(Blocks.FROSTED_ICE)) {
                placed |= level.setBlock(pos, Blocks.LILY_PAD.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        return placed;
    }

    private static boolean reedPatch(WorldGenLevel level, RandomSource random, BlockPos origin) {
        boolean placed = false;
        for (int attempt = 0; attempt < 20; attempt++) {
            BlockPos pos = origin.offset(random.nextInt(4) - random.nextInt(4), 0, random.nextInt(4) - random.nextInt(4));
            if (!canAccess(level, pos) || !canAccess(level, pos.below()) || !level.isEmptyBlock(pos)) {
                continue;
            }
            BlockPos soilPos = pos.below();
            boolean water = false;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos adjacent = soilPos.relative(direction);
                if (canAccess(level, adjacent) && level.getBlockState(adjacent).is(Blocks.WATER)) {
                    water = true;
                    break;
                }
            }
            if (!water) {
                continue;
            }
            int height = 2 + random.nextInt(random.nextInt(3) + 1);
            BlockState soil = level.getBlockState(soilPos);
            if (!(isDirt(soil) || soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.SAND) || soil.is(Blocks.RED_SAND)
                || soil.is(Blocks.SUGAR_CANE))) {
                continue;
            }
            for (int layer = 0; layer < height; layer++) {
                BlockPos target = pos.above(layer);
                // Original tested support at the base each time, not occupancy at each upper block.
                if (canAccess(level, target)) {
                    placed |= level.setBlock(target, Blocks.SUGAR_CANE.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        return placed;
    }

    private static boolean pumpkinPatch(WorldGenLevel level, RandomSource random, BlockPos origin) {
        boolean placed = false;
        for (int attempt = 0; attempt < 64; attempt++) {
            BlockPos pos = triangularOffset(origin, random);
            if (canAccess(level, pos) && canAccess(level, pos.below()) && level.isEmptyBlock(pos)
                && level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)) {
                // 1.12's naturally generated pumpkin already had a face and horizontal orientation.
                BlockState pumpkin = Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,
                    Direction.from2DDataValue(random.nextInt(4)));
                placed |= level.setBlock(pos, pumpkin, Block.UPDATE_CLIENTS);
            }
        }
        return placed;
    }

    public static boolean mossyBlob(WorldGenLevel level, RandomSource random, BlockPos origin) {
        BlockPos center = origin;
        while (center.getY() > 3) {
            if (!canAccess(level, center.below())) {
                return false;
            }
            BlockState below = level.getBlockState(center.below());
            if (below.is(Blocks.GRASS_BLOCK) || isDirt(below) || isStone(below)) {
                break;
            }
            center = center.below();
        }
        if (center.getY() <= 3) {
            return false;
        }
        boolean placed = false;
        for (int part = 0; part < 3; part++) {
            int dx = random.nextInt(2);
            int dy = random.nextInt(2);
            int dz = random.nextInt(2);
            float radius = (dx + dy + dz) * 0.333F + 0.5F;
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-dx, -dy, -dz), center.offset(dx, dy, dz))) {
                if (pos.distSqr(center) <= radius * radius && canAccess(level, pos)) {
                    placed |= level.setBlock(pos, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Block.UPDATE_INVISIBLE);
                }
            }
            center = center.offset(-1 + random.nextInt(2), -random.nextInt(2), -1 + random.nextInt(2));
        }
        return placed;
    }

    /** Original WorldGenBigMushroom shape, with metadata cap faces converted to modern booleans. */
    public static boolean giantMushroom(WorldGenLevel level, RandomSource random, BlockPos origin) {
        boolean brown = random.nextBoolean();
        int height = random.nextInt(3) + 4;
        if (random.nextInt(12) == 0) {
            height *= 2;
        }
        if (origin.getY() < level.getMinBuildHeight() + 1 || origin.getY() + height + 1 >= level.getMaxBuildHeight()) {
            return false;
        }
        for (int y = origin.getY(); y <= origin.getY() + height + 1; y++) {
            int radius = y <= origin.getY() + 3 ? 0 : 3;
            for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
                for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!canAccess(level, pos) || !TCTreeSupport.canBeReplacedByLeaves(level.getBlockState(pos))) {
                        return false;
                    }
                }
            }
        }
        if (!canAccess(level, origin.below())) {
            return false;
        }
        BlockState soil = level.getBlockState(origin.below());
        if (!isDirt(soil) && !soil.is(Blocks.GRASS_BLOCK) && !soil.is(Blocks.MYCELIUM)) {
            return false;
        }
        int top = origin.getY() + height;
        int bottom = brown ? top : top - 3;
        Block cap = brown ? Blocks.BROWN_MUSHROOM_BLOCK : Blocks.RED_MUSHROOM_BLOCK;
        for (int y = bottom; y <= top; y++) {
            int radius = brown ? 3 : y < top ? 2 : 1;
            int minimumX = origin.getX() - radius;
            int maximumX = origin.getX() + radius;
            int minimumZ = origin.getZ() - radius;
            int maximumZ = origin.getZ() + radius;
            for (int x = minimumX; x <= maximumX; x++) {
                for (int z = minimumZ; z <= maximumZ; z++) {
                    int metadata = 5;
                    if (x == minimumX) {
                        metadata--;
                    } else if (x == maximumX) {
                        metadata++;
                    }
                    if (z == minimumZ) {
                        metadata -= 3;
                    } else if (z == maximumZ) {
                        metadata += 3;
                    }
                    if (brown || y < top) {
                        if ((x == minimumX || x == maximumX) && (z == minimumZ || z == maximumZ)) {
                            continue;
                        }
                        if ((x == origin.getX() - (radius - 1) && z == minimumZ)
                            || (x == minimumX && z == origin.getZ() - (radius - 1))) {
                            metadata = 1;
                        }
                        if ((x == origin.getX() + (radius - 1) && z == minimumZ)
                            || (x == maximumX && z == origin.getZ() - (radius - 1))) {
                            metadata = 3;
                        }
                        if ((x == origin.getX() - (radius - 1) && z == maximumZ)
                            || (x == minimumX && z == origin.getZ() + (radius - 1))) {
                            metadata = 7;
                        }
                        if ((x == origin.getX() + (radius - 1) && z == maximumZ)
                            || (x == maximumX && z == origin.getZ() + (radius - 1))) {
                            metadata = 9;
                        }
                    }
                    if (metadata == 5 && y < top) {
                        metadata = 0;
                    }
                    // The original's first disjunct compared the origin Y with top-1 and is false here.
                    if (metadata != 0) {
                        putMushroom(level, new BlockPos(x, y, z), capState(cap, metadata));
                    }
                }
            }
        }
        BlockState stem = Blocks.MUSHROOM_STEM.defaultBlockState().setValue(HugeMushroomBlock.UP, false)
            .setValue(HugeMushroomBlock.DOWN, false);
        for (int layer = 0; layer < height; layer++) {
            putMushroom(level, origin.above(layer), stem);
        }
        return true;
    }

    private static BlockState capState(Block cap, int metadata) {
        return cap.defaultBlockState().setValue(HugeMushroomBlock.UP, true).setValue(HugeMushroomBlock.DOWN, false)
            .setValue(HugeMushroomBlock.NORTH, metadata >= 1 && metadata <= 3)
            .setValue(HugeMushroomBlock.SOUTH, metadata >= 7 && metadata <= 9)
            .setValue(HugeMushroomBlock.WEST, metadata == 1 || metadata == 4 || metadata == 7)
            .setValue(HugeMushroomBlock.EAST, metadata == 3 || metadata == 6 || metadata == 9);
    }

    private static void putMushroom(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (canAccess(level, pos) && TCTreeSupport.canBeReplacedByLeaves(level.getBlockState(pos))) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    private static boolean isDirt(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL);
    }

    private static boolean isStone(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.GRANITE) || state.is(Blocks.DIORITE) || state.is(Blocks.ANDESITE);
    }
}
