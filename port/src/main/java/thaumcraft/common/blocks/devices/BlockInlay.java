package thaumcraft.common.blocks.devices;

import com.mojang.serialization.MapCodec;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.crafting.IInfusionStabiliserExt;
import thaumcraft.common.blocks.devices.BlockPedestal;
import thaumcraft.common.tiles.devices.TileStabilizer;
import thaumcraft.common.lib.fx.SourceFX;
import net.minecraft.world.phys.Vec3;

/** TC6 finite 0..15 inlay charge propagation, including FIRST adjacent source (N,E,S,W).
 * Connections are computed directly by the multipart model, avoiding legacy extended states.
 */
public class BlockInlay extends Block implements IInfusionStabiliserExt {
    public static final MapCodec<BlockInlay> CODEC = simpleCodec(BlockInlay::new);
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 15);
    public static final Direction[] HORIZONTALS = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
    private static final Direction[] PLANE_ORDER = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    public enum AttachPosition implements net.minecraft.util.StringRepresentable { NONE, SIDE, EXT; @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); } }
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<AttachPosition> NORTH = net.minecraft.world.level.block.state.properties.EnumProperty.create("north",AttachPosition.class);
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<AttachPosition> EAST = net.minecraft.world.level.block.state.properties.EnumProperty.create("east",AttachPosition.class);
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<AttachPosition> SOUTH = net.minecraft.world.level.block.state.properties.EnumProperty.create("south",AttachPosition.class);
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<AttachPosition> WEST = net.minecraft.world.level.block.state.properties.EnumProperty.create("west",AttachPosition.class);
    public BlockInlay(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(CHARGE,0).setValue(NORTH,AttachPosition.NONE).setValue(EAST,AttachPosition.NONE).setValue(SOUTH,AttachPosition.NONE).setValue(WEST,AttachPosition.NONE)); }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(CHARGE,NORTH,EAST,SOUTH,WEST); }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return connections(defaultBlockState(), context.getLevel(), context.getClickedPos()); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = box(3,0,3,13,1,13);
        if (state.getValue(NORTH) != AttachPosition.NONE) shape = Shapes.or(shape,box(3,0,0,13,1,3));
        if (state.getValue(SOUTH) != AttachPosition.NONE) shape = Shapes.or(shape,box(3,0,13,13,1,16));
        if (state.getValue(WEST) != AttachPosition.NONE) shape = Shapes.or(shape,box(0,0,3,3,1,13));
        if (state.getValue(EAST) != AttachPosition.NONE) shape = Shapes.or(shape,box(13,0,3,16,1,13));
        return shape;
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) { if (old.getBlock() != this) changed(level,pos); }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) { if (next.getBlock() != this) { super.onRemove(state,level,pos,next,moving); for (Direction side : HORIZONTALS) changed(level,pos.relative(side)); } }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        if (!canSurvive(state,level,pos)) { dropResources(state,level,pos); level.removeBlock(pos,false); } else changed(level,pos);
    }
    public static boolean connects(BlockState state) { return state.getBlock() instanceof BlockInlay || state.getBlock() instanceof BlockPedestal; }
    private static BlockState connections(BlockState state, Level level, BlockPos pos) {
        return state.setValue(NORTH,connected(level,pos.north())).setValue(EAST,connected(level,pos.east()))
            .setValue(SOUTH,connected(level,pos.south())).setValue(WEST,connected(level,pos.west()));
    }
    private static AttachPosition connected(Level level,BlockPos pos) { return connects(level.getBlockState(pos)) ? AttachPosition.SIDE : level.getBlockEntity(pos) instanceof TileStabilizer ? AttachPosition.EXT : AttachPosition.NONE; }
    public static int getSourceStrength(Level world, BlockPos pos) {
        for (Direction direction : PLANE_ORDER) if (world.getBlockEntity(pos.relative(direction)) instanceof TileStabilizer stabilizer && stabilizer.getEnergy() > 0) return stabilizer.getEnergy();
        return 0;
    }
    public static int getSourceStrengthAt(Level world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof TileStabilizer stabilizer ? stabilizer.getEnergy() : 0;
    }
    private static int getMaxStrength(Level level, BlockPos pos, int strength) {
        BlockState state = level.getBlockState(pos);
        return connects(state) ? Math.max(strength,state.getValue(CHARGE)) : strength;
    }
    public static void changed(Level level, BlockPos pos) {
        if (level.isClientSide || !level.hasChunkAt(pos)) return;
        BlockState old = level.getBlockState(pos);
        if (!connects(old)) return;
        int source = getSourceStrength(level,pos), max = getMaxStrength(level,pos,0), neighbor = 0;
        if (source > 0 && source > max - 1) max = source;
        for (Direction side : HORIZONTALS) neighbor = getMaxStrength(level,pos.relative(side),neighbor);
        if (neighbor > max) max = neighbor - 1; else if (max > 0) max--; else max = 0;
        if (source > max - 1) max = source;
        BlockState next = old.setValue(CHARGE,max);
        if (next.getBlock() instanceof BlockInlay) next = connections(next,level,pos);
        if (old == next) return;
        level.setBlock(pos,next,2);
        Set<BlockPos> notify = new LinkedHashSet<>(); notify.add(pos);
        for (Direction side : Direction.values()) notify.add(pos.relative(side));
        for (BlockPos changed : notify) level.updateNeighborsAt(changed,next.getBlock());
    }
    @Override public boolean canStabaliseInfusion(Level world, BlockPos pos) { return true; }
    @Override public float getStabilizationAmount(Level world, BlockPos pos) { return .025F; }
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,RandomSource random){
        int charge=state.getValue(CHARGE);
        if(charge>0&&random.nextInt(20-charge)==0){
            Direction face=HORIZONTALS[random.nextInt(HORIZONTALS.length)];
            if(connected(level,pos.relative(face))!=AttachPosition.NONE){
                double x=pos.getX()+.5+random.nextGaussian()*.08,y=pos.getY()+.025F,z=pos.getZ()+.5+random.nextGaussian()*.08;
                double vx=face.getStepX()/70D*(1-random.nextFloat()*.1),vz=face.getStepZ()/70D*(1-random.nextFloat()*.1);
                int red=random.nextInt(51)+150,green=random.nextInt(201);
                // Dispatcher consumes one float from animateTick's Random, not world.rand.
                int sample=Float.floatToRawIntBits(random.nextFloat());
                SourceFX.local(level,"inlay",new Vec3(x,y,z),new Vec3(vx,0,vz),red<<16|green<<8,sample);
            }
        }
    }
}
