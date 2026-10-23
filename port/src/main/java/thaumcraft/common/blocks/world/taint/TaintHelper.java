package thaumcraft.common.blocks.world.taint;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.entities.monster.tainted.EntityTaintSeed;
import thaumcraft.common.lib.utils.EntityUtils;
import thaumcraft.common.world.aura.AuraHandler;
/** BETA26 seed cache and real conversion/spread branches. Cache keys are world instances to avoid stale server retention. */
public final class TaintHelper {
    private static final Map<Level,List<BlockPos>> SEEDS=Collections.synchronizedMap(new WeakHashMap<>());
    private TaintHelper(){}
    public static void addTaintSeed(Level level,BlockPos pos){SEEDS.computeIfAbsent(level,key->new ArrayList<>()).add(pos.immutable());}
    public static void removeTaintSeed(Level level,BlockPos pos){List<BlockPos> seeds=SEEDS.get(level);if(seeds!=null)seeds.remove(pos);}
    public static boolean isNearTaintSeed(Level level,BlockPos pos){List<BlockPos> seeds=SEEDS.get(level);double area=ModConfig.CONFIG_WORLD.taintSpreadArea;area*=area;if(seeds!=null)for(BlockPos seed:List.copyOf(seeds))if(seed.distSqr(pos)<=area){if(EntityUtils.getEntitiesInRange(level,seed,null,EntityTaintSeed.class,1).isEmpty()){removeTaintSeed(level,seed);return false;}return true;}return false;}
    public static boolean isAtTaintSeedEdge(Level level,BlockPos pos){List<BlockPos> seeds=SEEDS.get(level);double area=ModConfig.CONFIG_WORLD.taintSpreadArea;area*=area;if(seeds!=null)for(BlockPos seed:seeds){double distance=seed.distSqr(pos);if(distance<area&&distance>area*.64)return true;}return false;}
    /** Source MATERIAL_TAINT includes finite goo, not just the six solid/fibre block families. */
    public static boolean isTaint(BlockState state){Block block=state.getBlock();return block==BlocksTC.fluxGoo||block==BlocksTC.taintFibre||block==BlocksTC.taintCrust||block==BlocksTC.taintSoil||block==BlocksTC.taintRock||block==BlocksTC.taintLog||block==BlocksTC.taintFeature||block==BlocksTC.taintGeyser;}
    public static boolean onTaint(BlockGetter level,BlockPos pos){return isTaint(level.getBlockState(pos))||isTaint(level.getBlockState(pos.below()));}
    public static boolean adjacentSolid(BlockGetter level,BlockPos pos){for(Direction side:Direction.values()){BlockPos other=pos.relative(side);if(level.getBlockState(other).isFaceSturdy(level,other,side.getOpposite()))return true;}return false;}
    public static boolean replaceablePlant(BlockState state){return state.isAir()||state.canBeReplaced()||state.is(BlocksTC.fluxGoo)&&state.getValue(BlockFluxGoo.LEVEL)<4||state.getBlock() instanceof BushBlock||state.getBlock() instanceof CropBlock;}
    /** Goo has a native FluidState, but source MaterialTaint deliberately was not MaterialLiquid. */
    public static boolean sourceLiquid(BlockState state){return !state.getFluidState().isEmpty()&&!isTaint(state);}
    public static boolean isHemmedByTaint(BlockGetter level,BlockPos pos){int count=0;for(Direction side:Direction.values()){BlockPos other=pos.relative(side);BlockState state=level.getBlockState(other);if(isTaint(state))count++;else if(state.isAir()||!sourceLiquid(state)&&!state.isFaceSturdy(level,other,side.getOpposite()))count--;}return count>0;}
    public static void spreadFibres(Level level,BlockPos pos){spreadFibres(level,pos,false);}
    public static void spreadFibres(Level level,BlockPos pos,boolean ignore){if(level.isClientSide||!ignore&&ModConfig.CONFIG_MISC.wussMode)return;float saturation=.001F+AuraHandler.getFluxSaturation(level,pos)*2;if(!ignore&&level.random.nextFloat()>ModConfig.CONFIG_WORLD.taintSpreadRate/100*saturation||!isNearTaintSeed(level,pos))return;BlockPos target=pos.offset(level.random.nextInt(3)-1,level.random.nextInt(3)-1,level.random.nextInt(3)-1);if(target.equals(pos)||!level.hasChunkAt(target))return;BlockState state=level.getBlockState(target);float hardness=state.getDestroySpeed(level,target);if(hardness<0||hardness>10)return;
        if(!state.is(BlockTags.LEAVES)&&!sourceLiquid(state)&&replaceablePlant(state)&&adjacentSolid(level,target)&&!BlockTaintFibre.isOnlyAdjacentToTaint(level,target)){setSpreadBlock(level,target,BlocksTC.taintFibre.defaultBlockState(),true);return;}
        if(state.is(BlockTags.LEAVES)){Direction touching=null;for(Direction side:Direction.values())if(level.getBlockState(target.relative(side)).is(BlocksTC.taintLog)){touching=side;break;}if(level.random.nextFloat()<.6F&&touching!=null)level.setBlock(target,BlocksTC.taintFeature.defaultBlockState().setValue(BlockTaintFeature.FACING,touching.getOpposite()),3);else setSpreadBlock(level,target,BlocksTC.taintFibre.defaultBlockState(),true);return;}
        if(isHemmedByTaint(level,target)&&hardness<5){if(state.is(BlockTags.LOGS)&&!isTaint(state)){var axis=state.hasProperty(BlockStateProperties.AXIS)?state.getValue(BlockStateProperties.AXIS):Direction.Axis.Y;level.setBlock(target,BlocksTC.taintLog.defaultBlockState().setValue(BlockTaintLog.AXIS,axis),3);return;}Block replacement=legacyMaterialReplacement(state);if(replacement!=null){setSpreadBlock(level,target,replacement.defaultBlockState(),true);return;}}
        if((state.is(BlocksTC.taintSoil)||state.is(BlocksTC.taintRock))&&level.isEmptyBlock(target.above())&&AuraHelper.getFlux(level,target)>=5&&level.random.nextFloat()<ModConfig.CONFIG_WORLD.taintSpreadRate/100*.33&&isAtTaintSeedEdge(level,target)){EntityTaintSeed seed=new EntityTaintSeed(level);seed.moveTo(target.getX()+.5,target.getY()+1,target.getZ()+.5,level.random.nextInt(360),0);if(seed.checkSpawnRules(level,MobSpawnType.EVENT)){AuraHelper.drainFlux(level,target,5,false);level.addFreshEntity(seed);}}}
    private static void setSpreadBlock(Level level,BlockPos pos,BlockState state,boolean drain){if(level.setBlock(pos,state,3)){level.blockEvent(pos,state.getBlock(),1,0);if(drain)AuraHelper.drainFlux(level,pos,.01F,false);}}
    /** 1.12 Material has no native equivalent: explicit block families and native material sounds, not arbitrary shared tags. */
    public static Block legacyMaterialReplacement(BlockState state){Block block=state.getBlock();if(isTaint(state))return null;if(block instanceof HugeMushroomBlock||block instanceof PumpkinBlock||block==Blocks.MELON||block instanceof CactusBlock||block instanceof CoralBlock||block instanceof SpongeBlock||block instanceof WetSpongeBlock||woodMaterial(state))return BlocksTC.taintCrust;if(state.is(BlockTags.DIRT)||block==Blocks.SAND||block==Blocks.RED_SAND||block==Blocks.GRAVEL||block instanceof FarmBlock||block instanceof DirtPathBlock||block==Blocks.CLAY)return BlocksTC.taintSoil;if(state.getSoundType()==SoundType.STONE||state.getSoundType()==SoundType.DEEPSLATE)return BlocksTC.taintRock;return null;}
    /** Reviewed 1.21 wood-family counterparts; the source cannot inspect the removed Material.WOOD API. */
    private static boolean woodMaterial(BlockState state){SoundType sound=state.getSoundType();return sound==SoundType.WOOD||sound==SoundType.BAMBOO_WOOD||sound==SoundType.CHERRY_WOOD||sound==SoundType.NETHER_WOOD||state.is(BlockTags.PLANKS)||state.is(BlockTags.WOODEN_STAIRS)||state.is(BlockTags.WOODEN_SLABS)||state.is(BlockTags.WOODEN_FENCES)||state.is(BlockTags.WOODEN_DOORS)||state.is(BlockTags.WOODEN_TRAPDOORS)||state.is(BlockTags.WOODEN_BUTTONS)||state.is(BlockTags.WOODEN_PRESSURE_PLATES);}
}
