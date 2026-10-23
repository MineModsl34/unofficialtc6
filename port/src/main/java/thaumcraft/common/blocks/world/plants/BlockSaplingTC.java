package thaumcraft.common.blocks.world.plants;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.event.EventHooks;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.world.features.TCTreeSupport;

/**
 * Stages/random tick from SaplingBlock; tree choice without the extra RNG draws of the modern TreeGrower.
 * The greatwood needs a 2x2 square. NeoForge's event for cancelling/replacing the configured feature is kept.
 */
public class BlockSaplingTC extends SaplingBlock {
    private static final VoxelShape TC_SHAPE = Block.box(1.6, 0.0, 1.6, 14.4, 12.8, 14.4);
    public static final MapCodec<BlockSaplingTC> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(TreeGrower.CODEC.fieldOf("tree").forGetter(b -> b.treeGrower), propertiesCodec()).apply(i, BlockSaplingTC::new));

    public static final ResourceKey<ConfiguredFeature<?, ?>> GREATWOOD = key("greatwood");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SILVERWOOD = key("silverwood");
    public static final TreeGrower GREATWOOD_GROWER =
        new TreeGrower(Thaumcraft.MODID + ":greatwood", Optional.of(GREATWOOD), Optional.empty(), Optional.empty());
    public static final TreeGrower SILVERWOOD_GROWER =
        new TreeGrower(Thaumcraft.MODID + ":silverwood", Optional.empty(), Optional.of(SILVERWOOD), Optional.empty());

    public BlockSaplingTC(TreeGrower grower, Properties properties) {
        super(grower, properties);
    }

    private static ResourceKey<ConfiguredFeature<?, ?>> key(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, name));
    }

    @Override
    public MapCodec<? extends SaplingBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return TC_SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState soil, BlockGetter level, BlockPos pos) {
        return TCTreeSupport.isLegacySoil(soil);
    }

    @Override
    public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.cycle(STAGE), 4);
            return;
        }
        boolean greatwood = state.is(BlocksTC.saplingGreatwood);
        Holder<ConfiguredFeature<?, ?>> holder = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
            .getHolder(greatwood ? GREATWOOD : SILVERWOOD).orElse(null);
        var event = EventHooks.fireBlockGrowFeature(level, random, pos, holder);
        if (event.isCanceled() || event.getFeature() == null) {
            return;
        }
        BlockPos origin = pos;
        if (greatwood) {
            boolean found = false;
            search:
            for (int x = 0; x >= -1; x--) {
                for (int z = 0; z >= -1; z--) {
                    BlockPos corner = pos.offset(x, 0, z);
                    if (sameSapling(level, corner, state) && sameSapling(level, corner.east(), state)
                        && sameSapling(level, corner.south(), state) && sameSapling(level, corner.east().south(), state)) {
                        origin = corner;
                        found = true;
                        break search;
                    }
                }
            }
            if (!found) {
                return;
            }
        }
        clearOrRestore(level, origin, Blocks.AIR.defaultBlockState(), greatwood);
        if (!event.getFeature().value().place(level, level.getChunkSource().getGenerator(), random, origin)) {
            // Like TC6, a failed 2x2 restores the triggering sapling's stage to all four positions.
            clearOrRestore(level, origin, state, greatwood);
        }
    }

    private static boolean sameSapling(ServerLevel level, BlockPos pos, BlockState state) {
        return level.getBlockState(pos).is(state.getBlock());
    }

    private static void clearOrRestore(ServerLevel level, BlockPos origin, BlockState state, boolean square) {
        level.setBlock(origin, state, 4);
        if (square) {
            level.setBlock(origin.east(), state, 4);
            level.setBlock(origin.south(), state, 4);
            level.setBlock(origin.east().south(), state, 4);
        }
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return level.random.nextFloat() < 0.25F;
    }
}
