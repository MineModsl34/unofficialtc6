package thaumcraft.common.world.features;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import thaumcraft.api.blocks.BlocksTC;

/** TC6's two-crown, two-by-two greatwood, with one isolated Java Random per placement. */
public final class TCGreatwoodTreeFeature extends Feature<TCGreatwoodTreeFeature.Configuration> {
    public record Configuration(boolean natural, boolean spiders) implements FeatureConfiguration {
        public static final Codec<Configuration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("natural").forGetter(Configuration::natural),
            Codec.BOOL.fieldOf("spiders").forGetter(Configuration::spiders)
        ).apply(instance, Configuration::new));
    }

    public TCGreatwoodTreeFeature() {
        super(Configuration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Configuration> context) {
        return placeTree(context.level(), context.random(), context.origin(),
            context.config().natural(), context.config().spiders());
    }

    /** Shared by biome decoration and two-by-two sapling growth; natural trees omit neighbor notifications. */
    public static boolean placeTree(WorldGenLevel level, RandomSource random, BlockPos origin,
                                    boolean natural, boolean spiders) {
        return new Generator(level, random, origin, natural, spiders).generate();
    }

    private static final class Generator {
        private static final byte[] OTHER_COORD_PAIRS = {2, 0, 0, 1, 2, 1};
        private static final double HEIGHT_ATTENUATION = 0.618;
        private static final double BRANCH_SLOPE = 0.38;
        private static final double LEAF_DENSITY = 0.9;
        private static final int HEIGHT_LIMIT_LIMIT = 11;
        private static final int LEAF_DISTANCE_LIMIT = 4;
        private static final int TRUNK_SIZE = 2;

        private final WorldGenLevel level;
        private final RandomSource sharedRandom;
        private final Random random;
        private final BlockPos origin;
        private final boolean spiders;
        private final int flags;
        private final int[] basePos;
        private final Set<BlockPos> logs = new HashSet<>();
        private final Set<BlockPos> leaves = new HashSet<>();
        private int heightLimit;
        private int height;
        private double scaleWidth = 1.2;
        private int[][] leafNodes;

        private Generator(WorldGenLevel level, RandomSource sharedRandom, BlockPos origin,
                          boolean natural, boolean spiders) {
            this.level = level;
            this.sharedRandom = sharedRandom;
            this.origin = origin.immutable();
            this.spiders = spiders;
            this.flags = natural ? Block.UPDATE_CLIENTS : Block.UPDATE_ALL;
            this.basePos = new int[]{origin.getX(), origin.getY(), origin.getZ()};
            // TC6 seeded java.util.Random, not the world's RNG, before rolling the height.
            this.random = new Random(sharedRandom.nextLong());
        }

        private boolean generate() {
            heightLimit = HEIGHT_LIMIT_LIMIT + random.nextInt(HEIGHT_LIMIT_LIMIT);
            for (int x = 0; x < TRUNK_SIZE; x++) {
                for (int z = 0; z < TRUNK_SIZE; z++) {
                    if (!validTreeLocation(x, z)) {
                        return false;
                    }
                }
            }
            // The second crown starts at the first trunk's top. Do not silently clip it at a modern world boundary.
            int firstTrunkHeight = (int)(heightLimit * HEIGHT_ATTENUATION);
            if (!canAccess(origin.above(firstTrunkHeight + heightLimit - 1))
                || (spiders && !canAccess(origin.below(2)))) {
                return false;
            }

            setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            generateLeafNodeList();
            generateLeaves();
            generateLeafNodeBases();
            generateTrunk();
            scaleWidth = 1.66;
            basePos[1] = origin.getY() + height;
            generateLeafNodeList();
            generateLeaves();
            generateLeafNodeBases();
            generateTrunk();
            if (spiders) {
                generateSpiderNest();
            }
            // 1.21 leaves store distance instead of 1.12's deferred CHECK_DECAY bit.
            TCTreeSupport.updateLeafDistances(level, logs, leaves, flags);
            return true;
        }

        private void generateLeafNodeList() {
            height = (int)(heightLimit * HEIGHT_ATTENUATION);
            if (height >= heightLimit) {
                height = heightLimit - 1;
            }
            int nodesPerLayer = (int)(1.382 + Math.pow(LEAF_DENSITY * heightLimit / 13.0, 2.0));
            if (nodesPerLayer < 1) {
                nodesPerLayer = 1;
            }
            int[][] candidates = new int[nodesPerLayer * heightLimit][4];
            int nodeY = basePos[1] + heightLimit - LEAF_DISTANCE_LIMIT;
            int count = 1;
            int trunkTop = basePos[1] + height;
            int relativeY = nodeY - basePos[1];
            candidates[0] = new int[]{basePos[0], nodeY, basePos[2], trunkTop};
            nodeY--;
            while (relativeY >= 0) {
                float radius = layerSize(relativeY);
                if (radius >= 0.0F) {
                    for (int attempt = 0; attempt < nodesPerLayer; attempt++) {
                        double distance = scaleWidth * radius * (random.nextFloat() + 0.328);
                        double angle = random.nextFloat() * 2.0 * Math.PI;
                        int nodeX = Mth.floor(distance * Math.sin(angle) + basePos[0] + 0.5);
                        int nodeZ = Mth.floor(distance * Math.cos(angle) + basePos[2] + 0.5);
                        int[] node = {nodeX, nodeY, nodeZ};
                        int[] nodeTop = {nodeX, nodeY + LEAF_DISTANCE_LIMIT, nodeZ};
                        if (checkBlockLine(node, nodeTop) == -1) {
                            int[] branchBase = {basePos[0], basePos[1], basePos[2]};
                            double horizontalDistance = Math.sqrt(
                                Math.pow(Math.abs(basePos[0] - nodeX), 2.0)
                                + Math.pow(Math.abs(basePos[2] - nodeZ), 2.0));
                            double drop = horizontalDistance * BRANCH_SLOPE;
                            branchBase[1] = nodeY - drop > trunkTop ? trunkTop : (int)(nodeY - drop);
                            if (checkBlockLine(branchBase, node) == -1) {
                                candidates[count++] = new int[]{nodeX, nodeY, nodeZ, branchBase[1]};
                            }
                        }
                    }
                }
                nodeY--;
                relativeY--;
            }
            leafNodes = new int[count][4];
            System.arraycopy(candidates, 0, leafNodes, 0, count);
        }

        private float layerSize(int layer) {
            if (layer < heightLimit * 0.3) {
                return -1.618F;
            }
            float halfHeight = heightLimit / 2.0F;
            float offset = heightLimit / 2.0F - layer;
            float radius;
            if (offset == 0.0F) {
                radius = halfHeight;
            } else if (Math.abs(offset) >= halfHeight) {
                radius = 0.0F;
            } else {
                radius = (float)Math.sqrt(Math.pow(Math.abs(halfHeight), 2.0) - Math.pow(Math.abs(offset), 2.0));
            }
            return radius * 0.5F;
        }

        private void generateLeaves() {
            for (int[] node : leafNodes) {
                for (int layer = 0; layer < LEAF_DISTANCE_LIMIT; layer++) {
                    float radius = layer != 0 && layer != LEAF_DISTANCE_LIMIT - 1 ? 3.0F : 2.0F;
                    generateTreeLayer(node[0], node[1] + layer, node[2], radius);
                }
            }
        }

        private void generateTreeLayer(int x, int y, int z, float radius) {
            int extent = (int)(radius + 0.618);
            // Original axis pair for a Y layer is X, then Z.
            for (int dx = -extent; dx <= extent; dx++) {
                for (int dz = -extent; dz <= extent; dz++) {
                    double distanceSquared = Math.pow(Math.abs(dx) + 0.5, 2.0) + Math.pow(Math.abs(dz) + 0.5, 2.0);
                    if (distanceSquared <= radius * radius) {
                        BlockPos pos = new BlockPos(x + dx, y, z + dz);
                        if (!canAccess(pos)) {
                            continue;
                        }
                        BlockState state = level.getBlockState(pos);
                        if ((state.isAir() || state.is(BlocksTC.leafGreatwood))
                            && TCTreeSupport.canBeReplacedByLeaves(state)) {
                            setBlock(pos, BlocksTC.leafGreatwood.defaultBlockState(), flags);
                        }
                    }
                }
            }
        }

        private void generateTrunk() {
            int[] bottom = {basePos[0], basePos[1], basePos[2]};
            int[] top = {basePos[0], basePos[1] + height, basePos[2]};
            placeBlockLine(bottom, top);
            bottom[0]++;
            top[0]++;
            placeBlockLine(bottom, top);
            bottom[2]++;
            top[2]++;
            placeBlockLine(bottom, top);
            bottom[0]--;
            top[0]--;
            placeBlockLine(bottom, top);
        }

        private void generateLeafNodeBases() {
            int[] base = {basePos[0], basePos[1], basePos[2]};
            for (int[] node : leafNodes) {
                base[1] = node[3];
                if (base[1] - basePos[1] >= heightLimit * 0.2) {
                    placeBlockLine(base, new int[]{node[0], node[1], node[2]});
                }
            }
        }

        private void placeBlockLine(int[] start, int[] end) {
            int[] delta = {end[0] - start[0], end[1] - start[1], end[2] - start[2]};
            int mainAxis = dominantAxis(delta);
            if (delta[mainAxis] == 0) {
                return;
            }
            int otherA = OTHER_COORD_PAIRS[mainAxis];
            int otherB = OTHER_COORD_PAIRS[mainAxis + 3];
            int step = delta[mainAxis] > 0 ? 1 : -1;
            double slopeA = (double)delta[otherA] / delta[mainAxis];
            double slopeB = (double)delta[otherB] / delta[mainAxis];
            int[] point = new int[3];
            for (int i = 0, endStep = delta[mainAxis] + step; i != endStep; i += step) {
                point[mainAxis] = Mth.floor(start[mainAxis] + i + 0.5);
                point[otherA] = Mth.floor(start[otherA] + i * slopeA + 0.5);
                point[otherB] = Mth.floor(start[otherB] + i * slopeB + 0.5);
                int dx = Math.abs(point[0] - start[0]);
                int dz = Math.abs(point[2] - start[2]);
                int horizontal = Math.max(dx, dz);
                Direction.Axis axis = Direction.Axis.Y;
                if (horizontal > 0) {
                    axis = dx == horizontal ? Direction.Axis.X : Direction.Axis.Z;
                }
                BlockPos pos = new BlockPos(point[0], point[1], point[2]);
                if (canAccess(pos) && isLogReplaceable(level.getBlockState(pos))) {
                    setBlock(pos, BlocksTC.logGreatwood.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis), flags);
                }
            }
        }

        private int checkBlockLine(int[] start, int[] end) {
            int[] delta = {end[0] - start[0], end[1] - start[1], end[2] - start[2]};
            int mainAxis = dominantAxis(delta);
            if (delta[mainAxis] == 0) {
                return -1;
            }
            int otherA = OTHER_COORD_PAIRS[mainAxis];
            int otherB = OTHER_COORD_PAIRS[mainAxis + 3];
            int step = delta[mainAxis] > 0 ? 1 : -1;
            double slopeA = (double)delta[otherA] / delta[mainAxis];
            double slopeB = (double)delta[otherB] / delta[mainAxis];
            int[] point = new int[3];
            for (int i = 0, endStep = delta[mainAxis] + step; i != endStep; i += step) {
                point[mainAxis] = start[mainAxis] + i;
                // No +0.5 here: checking and placing deliberately use different rounding in TC6.
                point[otherA] = Mth.floor(start[otherA] + i * slopeA);
                point[otherB] = Mth.floor(start[otherB] + i * slopeB);
                BlockPos pos = new BlockPos(point[0], point[1], point[2]);
                if (!canAccess(pos)) {
                    return Math.abs(i);
                }
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && !state.is(BlocksTC.leafGreatwood)) {
                    return Math.abs(i);
                }
            }
            return -1;
        }

        private static int dominantAxis(int[] delta) {
            int axis = 0;
            for (int candidate = 0; candidate < 3; candidate++) {
                if (Math.abs(delta[candidate]) > Math.abs(delta[axis])) {
                    axis = candidate;
                }
            }
            return axis;
        }

        private static boolean isLogReplaceable(BlockState state) {
            // Greatwood used WorldGenAbstractTree.isReplaceable, not silverwood's broad replaceable-material test.
            return state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(BlockTags.SAPLINGS) || state.is(Blocks.VINE);
        }

        private boolean validTreeLocation(int x, int z) {
            BlockPos soil = new BlockPos(basePos[0] + x, basePos[1] - 1, basePos[2] + z);
            if (!canAccess(soil) || !TCTreeSupport.canSustainTree(level, soil)) {
                return false;
            }
            int[] bottom = {basePos[0] + x, basePos[1], basePos[2] + z};
            int[] top = {basePos[0] + x, basePos[1] + heightLimit - 1, basePos[2] + z};
            int obstacleDistance = checkBlockLine(bottom, top);
            if (obstacleDistance == -1) {
                return true;
            }
            if (obstacleDistance < 6) {
                return false;
            }
            heightLimit = obstacleDistance;
            return true;
        }

        private void generateSpiderNest() {
            BlockPos spawnerPos = origin.below();
            setBlock(spawnerPos, Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_ALL);
            if (!(level.getBlockEntity(spawnerPos) instanceof SpawnerBlockEntity spawner)) {
                return;
            }
            spawner.setEntityId(EntityType.CAVE_SPIDER, sharedRandom);
            for (int attempt = 0; attempt < 50; attempt++) {
                BlockPos pos = new BlockPos(origin.getX() - 7 + sharedRandom.nextInt(14),
                    origin.getY() + sharedRandom.nextInt(10), origin.getZ() - 7 + sharedRandom.nextInt(14));
                if (canAccess(pos) && level.isEmptyBlock(pos) && touchesTree(pos)) {
                    setBlock(pos, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            BlockPos chestPos = origin.below(2);
            setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
                chest.setLootTable(BuiltInLootTables.SIMPLE_DUNGEON, random.nextLong());
            }
        }

        private boolean touchesTree(BlockPos pos) {
            for (Direction face : Direction.values()) {
                BlockPos neighbor = pos.relative(face);
                if (canAccess(neighbor)) {
                    BlockState state = level.getBlockState(neighbor);
                    if (state.is(BlocksTC.leafGreatwood) || state.is(BlocksTC.logGreatwood)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean canAccess(BlockPos pos) {
            return TCWorldgenBounds.canAccess(level, pos);
        }

        private void setBlock(BlockPos pos, BlockState state, int blockFlags) {
            if (canAccess(pos) && level.setBlock(pos, state, blockFlags)) {
                if (state.is(BlocksTC.logGreatwood)) {
                    logs.add(pos.immutable());
                } else if (state.is(BlocksTC.leafGreatwood)) {
                    leaves.add(pos.immutable());
                }
            }
        }
    }
}
