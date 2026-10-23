package thaumcraft.common.blocks.devices;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import thaumcraft.api.aura.AuraHelper;

/** The original vis battery stores charge in block state, not a block entity. */
public final class BlockVisBattery extends Block {
    public static final MapCodec<BlockVisBattery> CODEC = simpleCodec(BlockVisBattery::new);
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge",0,10);
    public BlockVisBattery(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(CHARGE,0)); }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(CHARGE); }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos) { return state.getValue(CHARGE); }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block source,BlockPos from,boolean moving) { if (!level.isClientSide && level.hasNeighborSignal(pos)) level.scheduleTick(pos,this,1); }
    @Override protected void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) { tick(state,level,pos,random); }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        int charge = state.getValue(CHARGE); float vis = AuraHelper.getVis(level,pos), base = AuraHelper.getAuraBase(level,pos);
        if (level.hasNeighborSignal(pos)) { if (charge > 0) { AuraHelper.addVis(level,pos,1); level.setBlock(pos,state.setValue(CHARGE,charge - 1),3); level.scheduleTick(pos,this,5); } }
        else if (charge < 10 && vis > base * .9F && vis > 1) { AuraHelper.drainVis(level,pos,1,false); level.setBlock(pos,state.setValue(CHARGE,charge + 1),3); level.scheduleTick(pos,this,100 + random.nextInt(100)); }
        else if (charge > 0 && vis < base * .75F) { AuraHelper.addVis(level,pos,1); level.setBlock(pos,state.setValue(CHARGE,charge - 1),3); level.scheduleTick(pos,this,20 + random.nextInt(20)); }
    }
}
