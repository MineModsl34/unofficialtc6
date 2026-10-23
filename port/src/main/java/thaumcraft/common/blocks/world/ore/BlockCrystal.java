package thaumcraft.common.blocks.world.ore;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.common.lib.TCTags;
import thaumcraft.common.world.aura.AuraHandler;

/**
 * Vis crystal. In TC6 the attachment faces were unlisted properties for CrystalModel; in 1.21 they are plain
 * BooleanProperty, and the model is assembled by a multipart blockstate from OBJ shards (see datagen).
 * The support (Material.ROCK) is replaced by the tag {@link TCTags.Blocks#CRYSTAL_SUPPORT}.
 */
public class BlockCrystal extends Block {
    public static final MapCodec<BlockCrystal> CODEC = simpleCodec(p -> new BlockCrystal(Aspect.AIR, p));
    public static final IntegerProperty SIZE = IntegerProperty.create("size", 0, 3);
    public static final IntegerProperty GENERATION = IntegerProperty.create("gen", 1, 4);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final Map<Direction, BooleanProperty> FACES = new EnumMap<>(Map.of(
        Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH,
        Direction.WEST, WEST, Direction.UP, UP, Direction.DOWN, DOWN));

    private static final VoxelShape FULL = Shapes.block();
    private static final Map<Direction, VoxelShape> HALF = new EnumMap<>(Map.of(
        Direction.UP, box(0, 8, 0, 16, 16, 16),
        Direction.DOWN, box(0, 0, 0, 16, 8, 16),
        Direction.EAST, box(8, 0, 0, 16, 16, 16),
        Direction.WEST, box(0, 0, 0, 8, 16, 16),
        Direction.SOUTH, box(0, 0, 8, 16, 16, 16),
        Direction.NORTH, box(0, 0, 0, 16, 16, 8)));

    public final Aspect aspect;

    public BlockCrystal(Aspect aspect, BlockBehaviour.Properties properties) {
        super(properties);
        this.aspect = aspect;
        BlockState def = this.stateDefinition.any().setValue(SIZE, 0).setValue(GENERATION, 1);
        for (BooleanProperty p : FACES.values()) {
            def = def.setValue(p, false);
        }
        this.registerDefaultState(def);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIZE, GENERATION, NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    public int getGrowth(BlockState state) {
        return state.getValue(SIZE);
    }

    public int getGeneration(BlockState state) {
        return state.getValue(GENERATION);
    }

    /** Analogue of TC6's drawAt: the neighbour is "stone" with a full face towards the crystal. */
    public static boolean isSupport(BlockGetter level, BlockPos crystalPos, Direction side) {
        BlockPos p = crystalPos.relative(side);
        BlockState s = level.getBlockState(p);
        return s.is(TCTags.Blocks.CRYSTAL_SUPPORT) && s.isFaceSturdy(level, p, side.getOpposite());
    }

    /** Analogue of BlockUtils.isBlockTouching(world, pos, Material.ROCK, true). */
    public static boolean isTouchingSupport(BlockGetter level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (isSupport(level, pos, d)) {
                return true;
            }
        }
        return false;
    }

    /** Sets the attachment faces from the neighbours. Also needed by worldgen (stage 3). */
    public static BlockState withConnections(BlockState state, BlockGetter level, BlockPos pos) {
        for (Map.Entry<Direction, BooleanProperty> e : FACES.entrySet()) {
            state = state.setValue(e.getValue(), isSupport(level, pos, e.getKey()));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(this.defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return isTouchingSupport(level, pos);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // In TC6 neighborChanged dropped the crystal as an item; returning AIR from updateShape does the same (Block.updateOrDestroy)
        if (!isTouchingSupport(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state.setValue(FACES.get(direction), isSupport(level, pos, direction));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction only = null;
        for (Map.Entry<Direction, BooleanProperty> e : FACES.entrySet()) {
            if (state.getValue(e.getValue())) {
                if (only != null) {
                    return FULL;
                }
                only = e.getKey();
            }
        }
        return only == null ? FULL : HALF.get(only);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int generation = getGeneration(state);
        if (random.nextInt(3 + generation) != 0) {
            return;
        }
        int growth = getGrowth(state);
        boolean flux = this.aspect == Aspect.FLUX;
        float available = flux ? AuraHelper.getFlux(level, pos) : AuraHelper.getVis(level, pos);
        int threshold = 10;
        if (available <= threshold) {
            if (growth > 0) {
                level.setBlock(pos, state.setValue(SIZE, growth - 1), Block.UPDATE_ALL);
                releaseAura(level, pos, threshold, flux);
            } else {
                // TC6 keeps the last crystal: it disappears only next to a neighbour of the same kind.
                for (Direction direction : Direction.values()) {
                    if (level.getBlockState(pos.relative(direction)).is(this)) {
                        level.removeBlock(pos, false);
                        releaseAura(level, pos, threshold, flux);
                        break;
                    }
                }
            }
        } else if (available > AuraHandler.getAuraBase(level, pos) + threshold) {
            if (growth < 3 && growth < getGrowthLimit(pos, generation)) {
                if (drainAura(level, pos, threshold, flux) > 0.0F) {
                    level.setBlock(pos, state.setValue(SIZE, growth + 1), Block.UPDATE_ALL);
                }
            } else if (generation < 4) {
                // In game random == level.random; pass it explicitly for reproducible tests too.
                BlockPos target = spreadCrystal(level, pos, random);
                if (target != null && drainAura(level, pos, threshold, flux) > 0.0F) {
                    if (random.nextInt(6) == 0) {
                        generation--;
                    }
                    BlockState child = defaultBlockState().setValue(GENERATION, generation + 1);
                    level.setBlock(target, withConnections(child, level, target), Block.UPDATE_ALL);
                }
            }
        }
    }

    /**
     * TC6's exclusive growth boundary; keep the 1.12 toLong (x:26, y:12, z:26) and the signed %.
     * The 1.21 asLong has a different y/z order, but for % 3 both packings are equivalent (the shifts are even).
     */
    public static int getGrowthLimit(BlockPos pos, int generation) {
        long legacyPosition = ((long) pos.getX() & 0x3FFFFFFL) << 38
            | ((long) pos.getY() & 0xFFFL) << 26
            | ((long) pos.getZ() & 0x3FFFFFFL);
        return 5 - generation + (int) (legacyPosition % 3L);
    }

    private static float drainAura(Level level, BlockPos pos, float amount, boolean flux) {
        return flux ? AuraHelper.drainFlux(level, pos, amount, false) : AuraHelper.drainVis(level, pos, amount, false);
    }

    private static void releaseAura(Level level, BlockPos pos, float amount, boolean flux) {
        if (flux) {
            AuraHelper.polluteAura(level, pos, amount, false);
        } else {
            AuraHelper.addVis(level, pos, amount);
        }
    }

    /** TC6: one attempt in a 3x3x3 cube, chance 1/16, only a replaceable dry block with stone support. */
    public static BlockPos spreadCrystal(Level level, BlockPos pos) {
        return spreadCrystal(level, pos, level.random);
    }

    private static BlockPos spreadCrystal(Level level, BlockPos pos, RandomSource random) {
        BlockPos target = pos.offset(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
        if (target.equals(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(target);
        return state.getFluidState().isEmpty() && (state.isAir() || state.canBeReplaced())
            && random.nextInt(16) == 0 && isTouchingSupport(level, target) ? target : null;
    }
}
