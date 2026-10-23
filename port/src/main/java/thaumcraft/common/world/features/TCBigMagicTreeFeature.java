package thaumcraft.common.world.features;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** The wide oak used by TC6's magical forest, not a substitution with modern fancy oak. */
public final class TCBigMagicTreeFeature extends Feature<NoneFeatureConfiguration> {
    public TCBigMagicTreeFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        return placeTree(context.level(), context.random(), context.origin());
    }

    public static boolean placeTree(WorldGenLevel level, RandomSource random, BlockPos origin) {
        return new Generator(level, new Random(random.nextLong()), origin.immutable(), 0).generate();
    }

    /**
     * BiomeGenMagicalForest kept one BigMagicTree instance, including height after failed attempts
     * and obstacle truncation. Preserve that gameplay state while serializing the complete placement
     * instead of sharing mutable world/random/node fields between asynchronous worldgen workers.
     * Its lifetime intentionally follows the registered feature/biome singleton, not an individual world.
     */
    public static final class LegacyState {
        private int heightLimit;

        public synchronized boolean placeTree(WorldGenLevel level, RandomSource random, BlockPos origin) {
            Generator generator = new Generator(level, new Random(random.nextLong()), origin.immutable(), heightLimit);
            try {
                return generator.generate();
            } finally {
                heightLimit = generator.heightLimit;
            }
        }

        public synchronized int heightLimit() {
            return heightLimit;
        }
    }

    private record FoliageNode(BlockPos position, int branchBase) {
    }

    private static final class Generator {
        private static final int LEAF_DISTANCE = 4;
        private final WorldGenLevel level;
        private final Random random;
        private final BlockPos origin;
        private final List<FoliageNode> nodes = new ArrayList<>();
        private final Set<BlockPos> logs = new HashSet<>();
        private final Set<BlockPos> leaves = new HashSet<>();
        private int heightLimit;
        private int height;

        private Generator(WorldGenLevel level, Random random, BlockPos origin, int heightLimit) {
            this.level = level;
            this.random = random;
            this.origin = origin;
            this.heightLimit = heightLimit;
        }

        private boolean generate() {
            if (heightLimit == 0) {
                heightLimit = 11 + random.nextInt(11);
            }
            if (!TCTreeSupport.canSustainTree(level, origin.below())) {
                return false;
            }
            int obstacle = checkBlockLine(origin, origin.above(heightLimit - 1));
            if (obstacle != -1) {
                if (obstacle < 6) {
                    return false;
                }
                heightLimit = obstacle;
            }
            generateLeafNodeList();
            for (FoliageNode node : nodes) {
                for (int layer = 0; layer < LEAF_DISTANCE; layer++) {
                    crossSection(node.position().above(layer), layer == 0 || layer == LEAF_DISTANCE - 1 ? 2.0F : 3.0F);
                }
            }
            limb(origin, origin.above(height));
            for (FoliageNode node : nodes) {
                if (node.branchBase() - origin.getY() >= heightLimit * 0.2) {
                    limb(new BlockPos(origin.getX(), node.branchBase(), origin.getZ()), node.position());
                }
            }
            TCTreeSupport.updateLeafDistances(level, logs, leaves, Block.UPDATE_CLIENTS);
            return true;
        }

        private void generateLeafNodeList() {
            height = (int)(heightLimit * 0.6618);
            if (height >= heightLimit) {
                height = heightLimit - 1;
            }
            int count = Math.max(1, (int)(1.382 + Math.pow(0.9 * heightLimit / 13.0, 2.0)));
            int trunkTop = origin.getY() + height;
            int layer = heightLimit - LEAF_DISTANCE;
            nodes.add(new FoliageNode(origin.above(layer), trunkTop));
            for (; layer >= 0; layer--) {
                float radius = layerSize(layer);
                if (radius < 0.0F) {
                    continue;
                }
                for (int attempt = 0; attempt < count; attempt++) {
                    double distance = 1.25 * radius * (random.nextFloat() + 0.328);
                    // Preserve the float multiplication before promotion to double in TC6.
                    double angle = random.nextFloat() * 2.0F * Math.PI;
                    BlockPos node = BlockPos.containing(origin.getX() + distance * Math.sin(angle) + 0.5,
                        origin.getY() + layer - 1, origin.getZ() + distance * Math.cos(angle) + 0.5);
                    if (checkBlockLine(node, node.above(LEAF_DISTANCE)) != -1) {
                        continue;
                    }
                    int dx = origin.getX() - node.getX();
                    int dz = origin.getZ() - node.getZ();
                    double branchY = node.getY() - Math.sqrt(dx * dx + dz * dz) * 0.381;
                    int baseY = branchY > trunkTop ? trunkTop : (int)branchY;
                    if (checkBlockLine(new BlockPos(origin.getX(), baseY, origin.getZ()), node) == -1) {
                        nodes.add(new FoliageNode(node, baseY));
                    }
                }
            }
        }

        private float layerSize(int layer) {
            if (layer < heightLimit * 0.3F) {
                return -1.0F;
            }
            float half = heightLimit / 2.0F;
            float offset = half - layer;
            float radius = (float)Math.sqrt(half * half - offset * offset);
            if (offset == 0.0F) {
                radius = half;
            } else if (Math.abs(offset) >= half) {
                return 0.0F;
            }
            return radius * 0.5F;
        }

        private void crossSection(BlockPos center, float radius) {
            int extent = (int)(radius + 0.618);
            for (int dx = -extent; dx <= extent; dx++) {
                for (int dz = -extent; dz <= extent; dz++) {
                    if (Math.pow(Math.abs(dx) + 0.5, 2.0) + Math.pow(Math.abs(dz) + 0.5, 2.0) > radius * radius) {
                        continue;
                    }
                    BlockPos pos = center.offset(dx, 0, dz);
                    if (canAccess(pos) && TCTreeSupport.canBeReplacedByLeaves(level.getBlockState(pos))) {
                        put(pos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));
                    }
                }
            }
        }

        private void limb(BlockPos start, BlockPos end) {
            BlockPos delta = end.subtract(start);
            int distance = greatestDistance(delta);
            // TC6 divides by zero here for a degenerate branch; do not allow NaN to write at world origin.
            if (distance == 0) {
                return;
            }
            float stepX = (float)delta.getX() / distance;
            float stepY = (float)delta.getY() / distance;
            float stepZ = (float)delta.getZ() / distance;
            for (int step = 0; step <= distance; step++) {
                BlockPos pos = BlockPos.containing(start.getX() + (double)(0.5F + step * stepX),
                    start.getY() + (double)(0.5F + step * stepY), start.getZ() + (double)(0.5F + step * stepZ));
                int dx = Math.abs(pos.getX() - start.getX());
                int dz = Math.abs(pos.getZ() - start.getZ());
                Direction.Axis axis = Math.max(dx, dz) == 0 ? Direction.Axis.Y : dx >= dz ? Direction.Axis.X : Direction.Axis.Z;
                if (canAccess(pos)) {
                    // Original limb writes were unconditional after clearance, including pre-existing logs.
                    put(pos, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));
                }
            }
        }

        private int checkBlockLine(BlockPos start, BlockPos end) {
            BlockPos delta = end.subtract(start);
            int distance = greatestDistance(delta);
            if (distance == 0) {
                return -1;
            }
            float stepX = (float)delta.getX() / distance;
            float stepY = (float)delta.getY() / distance;
            float stepZ = (float)delta.getZ() / distance;
            for (int step = 0; step <= distance; step++) {
                BlockPos pos = BlockPos.containing(start.getX() + (double)(0.5F + step * stepX),
                    start.getY() + (double)(0.5F + step * stepY), start.getZ() + (double)(0.5F + step * stepZ));
                if (!canAccess(pos) || !isReplaceable(level.getBlockState(pos))) {
                    return step;
                }
            }
            return -1;
        }

        private static int greatestDistance(BlockPos delta) {
            return Math.max(Math.abs(delta.getX()), Math.max(Math.abs(delta.getY()), Math.abs(delta.getZ())));
        }

        private static boolean isReplaceable(BlockState state) {
            return state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(BlockTags.SAPLINGS) || state.is(Blocks.VINE);
        }

        private boolean canAccess(BlockPos pos) {
            return TCWorldgenBounds.canAccess(level, pos);
        }

        private void put(BlockPos pos, BlockState state) {
            if (level.setBlock(pos, state, Block.UPDATE_CLIENTS)) {
                if (state.is(Blocks.OAK_LOG)) {
                    logs.add(pos.immutable());
                    leaves.remove(pos);
                } else {
                    leaves.add(pos.immutable());
                }
            }
        }
    }
}
