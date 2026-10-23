package thaumcraft.common.blocks.world.taint;
import java.util.*;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.*;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.entities.monster.tainted.EntityTaintSwarm;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.potions.WarpEffects;
public final class BlockTaint extends Block {
    public enum Kind{CRUST,SOIL,ROCK,GEYSER}private final Kind kind;
    public static final MapCodec<BlockTaint> CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(Codec.STRING.xmap(name->Kind.valueOf(name.toUpperCase(Locale.ROOT)),value->value.name().toLowerCase(Locale.ROOT)).fieldOf("kind").forGetter((BlockTaint block)->block.kind),propertiesCodec()).apply(instance,BlockTaint::new));
    public BlockTaint(Kind kind){this(kind,Properties.of());}
    // Native Properties.CODEC decodes Properties.of(): reapply the source's canonical behavior here too.
    public BlockTaint(Kind kind,Properties properties){super(properties.mapColor(MapColor.COLOR_PURPLE).strength(10,60).sound(SoundsTC.GORE).randomTicks());this.kind=kind;}
    @Override public MapCodec<BlockTaint> codec(){return CODEC;}
    public void die(Level level,BlockPos pos){level.setBlock(pos,(kind==Kind.ROCK?BlocksTC.stonePorous:kind==Kind.SOIL?Blocks.DIRT:BlocksTC.fluxGoo).defaultBlockState(),3);}
    @Override protected void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){if(!TaintHelper.isNearTaintSeed(level,pos)&&random.nextInt(10)==0){die(level,pos);return;}if(kind==Kind.ROCK)TaintHelper.spreadFibres(level,pos);if(kind==Kind.CRUST){if(tryToFall(level,pos,pos))return;if(level.isEmptyBlock(pos.above())){Direction side=Direction.Plane.HORIZONTAL.getRandomDirection(random);boolean fall=true;for(int i=1;i<4;i++)if(!level.isEmptyBlock(pos.relative(side).below(i))||!level.getBlockState(pos.below(i)).is(this)){fall=false;break;}if(fall)tryToFall(level,pos,pos.relative(side));}}else if(kind==Kind.GEYSER){if(random.nextFloat()<.2F&&level.getNearestPlayer(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,32,false)!=null&&thaumcraft.common.lib.utils.EntityUtils.getEntitiesInRange(level,pos,null,EntityTaintSwarm.class,32).isEmpty()){EntityTaintSwarm swarm=new EntityTaintSwarm(level);swarm.moveTo(pos.getX()+.5,pos.getY()+1.25,pos.getZ()+.5,random.nextInt(360),0);level.addFreshEntity(swarm);}else if(AuraHelper.getFlux(level,pos)<2)AuraHelper.polluteAura(level,pos,.25F,true);}}
    public static boolean canFallBelow(Level level,BlockPos pos){for(BlockPos other:BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,1,1)))if(level.getBlockState(other).is(BlockTags.LOGS))return false;BlockState state=level.getBlockState(pos);if(state.is(BlocksTC.fluxGoo)&&state.hasProperty(BlockFluxGoo.LEVEL)&&state.getValue(BlockFluxGoo.LEVEL)>=4)return false;return state.isAir()||state.is(Blocks.FIRE)||state.is(BlocksTC.taintFibre)||state.canBeReplaced()||state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)||state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);}
    private boolean tryToFall(ServerLevel level,BlockPos source,BlockPos target){if(!BlockTaintFibre.isOnlyAdjacentToTaint(level,source)||!canFallBelow(level,target.below())||target.getY()<level.getMinBuildHeight())return false;if(level.isAreaLoaded(target,32)){level.addFreshEntity(new thaumcraft.common.entities.EntityFallingTaint(level,target.getX()+.5,target.getY()+.5,target.getZ()+.5,level.getBlockState(source),source));return true;}level.removeBlock(source,false);BlockPos end=target;while(end.getY()>level.getMinBuildHeight()&&canFallBelow(level,end.below()))end=end.below();if(end.getY()>level.getMinBuildHeight())level.setBlock(end,BlocksTC.taintCrust.defaultBlockState(),3);return false;}
    @Override public void stepOn(Level level,BlockPos pos,BlockState state,Entity entity){if(!level.isClientSide&&entity instanceof LivingEntity living&&!living.getType().is(EntityTypeTags.UNDEAD)&&level.random.nextInt(250)==0)living.addEffect(new MobEffectInstance(WarpEffects.FLUX_TAINT,200,0,false,true));super.stepOn(level,pos,state,entity);}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params){ItemStack tool=params.getOptionalParameter(LootContextParams.TOOL);var registry=params.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);if(tool!=null&&EnchantmentHelper.getItemEnchantmentLevel(registry.getOrThrow(Enchantments.SILK_TOUCH),tool)>0)return List.of(new ItemStack(this));int fortune=tool==null?0:EnchantmentHelper.getItemEnchantmentLevel(registry.getOrThrow(Enchantments.FORTUNE),tool);return kind==Kind.ROCK&&params.getLevel().random.nextInt(15)+fortune>13?List.of(ThaumcraftApiHelper.makeCrystal(Aspect.FLUX)):List.of();}
    @Override protected boolean triggerEvent(BlockState state,Level level,BlockPos pos,int id,int parameter){if(id!=1)return super.triggerEvent(state,level,pos,id,parameter);if(level.isClientSide)level.playLocalSound(pos,net.minecraft.sounds.SoundEvents.CHORUS_FLOWER_DEATH,net.minecraft.sounds.SoundSource.BLOCKS,.1F,.9F+level.random.nextFloat()*.2F,false);return true;}
}
