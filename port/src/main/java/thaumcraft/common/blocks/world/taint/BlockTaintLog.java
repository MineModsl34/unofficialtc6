package thaumcraft.common.blocks.world.taint;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.lib.SoundsTC;
public final class BlockTaintLog extends RotatedPillarBlock {
    public static final com.mojang.serialization.MapCodec<BlockTaintLog> CODEC=simpleCodec(BlockTaintLog::new);
    public BlockTaintLog(){this(Properties.of());}
    // Codec construction must retain the same hardness, resistance, sound and growth ticks as registration.
    public BlockTaintLog(Properties properties){super(properties.mapColor(MapColor.COLOR_PURPLE).strength(3,60).sound(SoundsTC.GORE).randomTicks());}
    @Override public com.mojang.serialization.MapCodec<BlockTaintLog> codec(){return CODEC;}
    @Override protected void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){if(!TaintHelper.isNearTaintSeed(level,pos))level.setBlock(pos,BlocksTC.fluxGoo.defaultBlockState(),3);else TaintHelper.spreadFibres(level,pos);}
    @Override public int getFlammability(BlockState state,BlockGetter level,BlockPos pos,Direction side){return 4;}@Override public int getFireSpreadSpeed(BlockState state,BlockGetter level,BlockPos pos,Direction side){return 4;}
    // BETA26 inherits Block.getItemDropped through BlockTC: unlike taint crust/feature this log drops itself normally.
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params){return List.of(new ItemStack(this));}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState replacement,boolean moving){super.onRemove(state,level,pos,replacement,moving);if(state.is(replacement.getBlock())||level.isClientSide||!level.isAreaLoaded(pos,5))return;for(BlockPos other:BlockPos.betweenClosed(pos.offset(-4,-4,-4),pos.offset(4,4,4)))if(level.getBlockState(other).getBlock() instanceof LeavesBlock)level.scheduleTick(other,level.getBlockState(other).getBlock(),1);}
}
