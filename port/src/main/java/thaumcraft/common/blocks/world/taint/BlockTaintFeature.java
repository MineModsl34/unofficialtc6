package thaumcraft.common.blocks.world.taint;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.*;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.entities.monster.tainted.EntityTaintCrawler;
public final class BlockTaintFeature extends Block {
    public static final com.mojang.serialization.MapCodec<BlockTaintFeature> CODEC=simpleCodec(BlockTaintFeature::new);
    public static final DirectionProperty FACING=BlockStateProperties.FACING;
    private static final VoxelShape[] SHAPES={Shapes.box(.125,.625,.125,.875,1,.875),Shapes.box(.125,0,.125,.875,.375,.875),Shapes.box(.125,.125,.625,.875,.875,1),Shapes.box(.125,.125,0,.875,.875,.375),Shapes.box(.625,.125,.125,1,.875,.875),Shapes.box(0,.125,.125,.375,.875,.875)};
    public BlockTaintFeature(){this(Properties.of());}
    // BlockTC's source resistance 2 becomes native 1.2; source light .625 becomes integer light 9.
    // Reapply these defaults after Properties.CODEC as well, otherwise decoding disables growth/light.
    public BlockTaintFeature(Properties properties){super(properties.mapColor(MapColor.COLOR_PURPLE).strength(.1F,1.2F).lightLevel(state->9).noOcclusion().randomTicks());registerDefaultState(stateDefinition.any().setValue(FACING,Direction.UP));}
    @Override public com.mojang.serialization.MapCodec<BlockTaintFeature> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}@Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getClickedFace());}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPES[state.getValue(FACING).ordinal()];}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block changed,BlockPos other,boolean moving){Direction side=state.getValue(FACING);BlockPos support=pos.relative(side.getOpposite());if(!level.isClientSide&&!level.getBlockState(support).isFaceSturdy(level,support,side))level.removeBlock(pos,false);}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState replacement,boolean moving){if(!level.isClientSide&&!state.is(replacement.getBlock())){if(level.random.nextFloat()<.333F){EntityTaintCrawler crawler=new EntityTaintCrawler(level);crawler.moveTo(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,level.random.nextInt(360),0);level.addFreshEntity(crawler);}else AuraHelper.polluteAura(level,pos,1,true);}super.onRemove(state,level,pos,replacement,moving);}
    @Override protected void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){if(!TaintHelper.isNearTaintSeed(level,pos)&&random.nextInt(10)==0){level.setBlock(pos,BlocksTC.fluxGoo.defaultBlockState(),3);return;}TaintHelper.spreadFibres(level,pos);BlockState below=level.getBlockState(pos.below());if(below.is(BlocksTC.taintLog)&&below.getValue(BlockTaintLog.AXIS)==Direction.Axis.Y&&random.nextInt(100)==0)level.setBlock(pos,BlocksTC.taintGeyser.defaultBlockState(),3);}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params){ItemStack tool=params.getOptionalParameter(LootContextParams.TOOL);return tool!=null&&EnchantmentHelper.getItemEnchantmentLevel(params.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH),tool)>0?List.of(new ItemStack(this)):List.of();}
}
