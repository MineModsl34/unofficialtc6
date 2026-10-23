package thaumcraft.common.items.casters.foci;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.casters.*;
import thaumcraft.common.items.casters.*;
/** Native effect bodies retaining the BETA26 formulas and return-value quirks. */
public final class FocusEffectRuntime {
    private FocusEffectRuntime(){}
    public static boolean execute(FocusEffect effect,HitResult hit,Trajectory trajectory,float power,int num){var pack=effect.getPackage();if(pack==null||!(pack.world instanceof ServerLevel level)||hit==null)return false;var caster=pack.getCaster();if(caster==null)return false;var pos=hit.getLocation();impact(effect,level,hit);Entity entity=hit instanceof EntityHitResult e?e.getEntity():null;int rank=effect.getSettingValue("power"),duration=effect.getSettingValue("duration");String key=effect.getKey();
        if(entity!=null){float damage=effect.getDamageForDisplay(power);switch(key){
            case "thaumcraft.AIR"->{level.playSound(null,pos.x,pos.y,pos.z,SoundEvents.ENDER_DRAGON_FLAP,SoundSource.PLAYERS,.5F,.66F);entity.hurt(level.damageSources().thrown(entity,caster),damage);if(entity instanceof LivingEntity living){if(trajectory==null)living.knockback(damage*.25,-net.minecraft.util.Mth.sin(entity.getYRot()*(float)Math.PI/180),net.minecraft.util.Mth.cos(entity.getYRot()*(float)Math.PI/180));else living.knockback(damage*.25,-trajectory.direction.x,-trajectory.direction.z);}return true;}
            case "thaumcraft.FIRE"->{if(entity.fireImmune())return false;entity.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.FIREBALL),entity,caster),damage);entity.igniteForSeconds(Math.round((1+duration*duration)*power));return true;}
            case "thaumcraft.FROST"->{entity.hurt(level.damageSources().thrown(entity,caster),damage);if(entity instanceof LivingEntity living)living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,20*duration,(int)(1+rank*power/3)));}
            case "thaumcraft.EARTH"->{entity.hurt(level.damageSources().thrown(entity,caster),damage);return true;}
            case "thaumcraft.FLUX"->entity.hurt(level.damageSources().indirectMagic(entity,caster),damage);
            case "thaumcraft.HEAL"->{if(entity instanceof LivingEntity living){if(living.getType().is(EntityTypeTags.UNDEAD))living.hurt(level.damageSources().indirectMagic(caster,caster),rank*power*1.5F);else living.heal(rank*power);}}
            case "thaumcraft.CURSE"->{entity.hurt(level.damageSources().indirectMagic(entity,caster),damage);if(entity instanceof LivingEntity living){int potency=Math.max(0,(int)(rank*power/2));living.addEffect(new MobEffectInstance(MobEffects.POISON,20*duration,potency));float chance=.85F;var effects=java.util.List.of(MobEffects.MOVEMENT_SLOWDOWN,MobEffects.WEAKNESS,MobEffects.DIG_SLOWDOWN,MobEffects.HUNGER,MobEffects.UNLUCK);for(int i=0;i<effects.size();i++)if(level.random.nextFloat()<chance){living.addEffect(new MobEffectInstance(effects.get(i),20*duration*(i==2?2:i>=3?3:1),potency));if(i<4)chance-=.15F;}}}
        }return key.equals("thaumcraft.BREAK");}
        if(!(hit instanceof BlockHitResult block))return key.equals("thaumcraft.BREAK");BlockPos at=block.getBlockPos();switch(key){
            case "thaumcraft.AIR"->level.playSound(null,pos.x,pos.y,pos.z,SoundEvents.ENDER_DRAGON_FLAP,SoundSource.PLAYERS,.5F,.66F);
            case "thaumcraft.FIRE"->{var fire=at.relative(block.getDirection());if(duration>0&&level.isEmptyBlock(fire)&&level.random.nextFloat()<power){level.playSound(null,fire,SoundEvents.FLINTANDSTEEL_USE,SoundSource.BLOCKS,1,level.random.nextFloat()*.4F+.8F);level.setBlock(fire,Blocks.FIRE.defaultBlockState(),11);return true;}}
            case "thaumcraft.FROST"->{float radius=Math.min(16,2*rank*power);for(var p:area(at,radius)){if(Vec3.atCenterOf(p).distanceToSqr(pos)>radius*radius||!level.hasChunkAt(p))continue;var state=level.getBlockState(p);if(state.is(Blocks.WATER)&&state.getFluidState().isSource()&&level.isUnobstructed(Blocks.FROSTED_ICE.defaultBlockState(),p,net.minecraft.world.phys.shapes.CollisionContext.empty())){level.setBlockAndUpdate(p,Blocks.FROSTED_ICE.defaultBlockState());level.scheduleTick(p.immutable(),Blocks.FROSTED_ICE,60+level.random.nextInt(61));}}}
            case "thaumcraft.EARTH"->{if(caster instanceof Player player&&level.getBlockState(at).getDestroySpeed(level,at)<=effect.getDamageForDisplay(power)/25)FocusBlockActions.breaker(level,at,player,false,false,0,1,0,1,num,.1F);}
            case "thaumcraft.BREAK"->{if(caster instanceof Player player){float strength=rank*power,dur=(float)Math.sqrt(level.getBlockState(at).getDestroySpeed(level,at)*100);boolean silk=effect.getSettingValue("silk")>0;int fortune=effect.getSettingValue("fortune");FocusBlockActions.breaker(level,at,player,true,silk,fortune,strength,dur,dur,strength<=0?0:(int)(dur/strength/3*num),.25F+(silk?.25F:0)+fortune*.1F);}return true;}
            case "thaumcraft.CURSE"->{float radius=(float)Math.min(8,1.5*rank*power);for(var p:area(at,radius))if(level.hasChunkAt(p)&&Vec3.atCenterOf(p).distanceToSqr(pos)<=radius*radius&&level.isEmptyBlock(p.above())&&level.getBlockState(p).isCollisionShapeFullBlock(level,p))level.setBlockAndUpdate(p.above(),BlocksTC.effectSap.defaultBlockState());}
            case "thaumcraft.EXCHANGE"->{var stack=caster.getMainHandItem().getItem() instanceof ItemCaster?caster.getMainHandItem():caster.getOffhandItem();if(!(stack.getItem() instanceof ItemCaster item))return false;if(caster instanceof Player player){var picked=item.getPickedBlock(stack);if(picked!=null&&!picked.isEmpty()){boolean silk=effect.getSettingValue("silk")>0;int fortune=effect.getSettingValue("fortune");FocusBlockActions.swapper(level,at,player,picked,silk,fortune,.25F+(silk?.25F:0)+fortune*.1F);}}return true;}
            case "thaumcraft.RIFT"->{if(level.dimension().location().equals(net.minecraft.resources.ResourceLocation.parse("thaumcraft:outer_lands"))){level.playSound(null,at,thaumcraft.common.lib.SoundsTC.wandfail,SoundSource.PLAYERS,1,1);return false;}int distance=0;var p=at;for(;distance<effect.getSettingValue("depth")*power;distance++){var state=level.getBlockState(p);if(FocusBlockActions.holeBlacklisted(state)||state.is(Blocks.BEDROCK)||state.is(BlocksTC.hole)||state.isAir()||state.getDestroySpeed(level,p)==-1)break;p=p.relative(block.getDirection().getOpposite());}FocusEffectRift.createHole(level,at,block.getDirection(),(byte)Math.round(distance+1),20*duration);return true;}
        }return false;
    }
    /** BlockPos.add(double, double, double) floored each coordinate, including the negative edge. */
    private static Iterable<BlockPos> area(BlockPos center,float radius){return BlockPos.betweenClosed(
        BlockPos.containing(center.getX()-(double)radius,center.getY()-(double)radius,center.getZ()-(double)radius),
        BlockPos.containing(center.getX()+(double)radius,center.getY()+(double)radius,center.getZ()+(double)radius));}
    public static void particle(FocusEffect effect,Level level,double x,double y,double z,double vx,double vy,double vz){if(level.isClientSide)thaumcraft.common.lib.fx.SourceFX.focus(level,effect.getKey(),new Vec3(x,y,z),new Vec3(vx,vy,vz),thaumcraft.common.lib.network.fx.PacketSourceFX.Kind.FOCUS_PARTICLE);}
    public static void trail(FocusEffect effect,Level level,Vec3 pos,Vec3 velocity){trail(new FocusEffect[]{effect},level,pos,velocity);}
    /** PacketFXFocusEffect is one combined source message, not ten particles per effect. */
    public static void trail(FocusEffect[] effects,Level level,Vec3 pos,Vec3 velocity){if(effects.length==0)return;String parts=java.util.Arrays.stream(effects).map(FocusEffect::getKey).collect(java.util.stream.Collectors.joining("%"));thaumcraft.common.lib.fx.SourceFX.emit(level,thaumcraft.common.lib.fx.SourceFX.event(thaumcraft.common.lib.network.fx.PacketSourceFX.Kind.FOCUS_TRAIL,floats(pos),floats(velocity),0,0,0,-1,parts),64);}
    private static void impact(FocusEffect effect,Level level,HitResult hit){String key=effect.getKey();Vec3 pos=hit.getLocation();if(key.equals("thaumcraft.CURSE")){thaumcraft.common.lib.fx.SourceFX.bamf(level,pos,6946821,true,true,null);return;}if(key.equals("thaumcraft.BREAK")){if(!(hit instanceof BlockHitResult block))return;pos=Vec3.atCenterOf(block.getBlockPos());}else if(!java.util.Set.of("thaumcraft.AIR","thaumcraft.FIRE","thaumcraft.FROST","thaumcraft.EARTH","thaumcraft.FLUX","thaumcraft.HEAL").contains(key))return;
        thaumcraft.common.lib.fx.SourceFX.emit(level,thaumcraft.common.lib.fx.SourceFX.event(thaumcraft.common.lib.network.fx.PacketSourceFX.Kind.FOCUS_IMPACT,floats(pos),Vec3.ZERO,0,0,0,-1,key),64,hit.getLocation());
    }
    private static Vec3 floats(Vec3 value){return new Vec3((float)value.x,(float)value.y,(float)value.z);}
    public static void onCast(FocusEffect effect,Entity caster){var level=caster.level();var key=effect.getKey();SoundEvent sound=switch(key){case "thaumcraft.AIR"->thaumcraft.common.lib.SoundsTC.wind;case "thaumcraft.FIRE"->SoundEvents.FIRECHARGE_USE;case "thaumcraft.FROST"->SoundEvents.ZOMBIE_VILLAGER_CURE;case "thaumcraft.EARTH"->SoundEvents.DRAGON_FIREBALL_EXPLODE;case "thaumcraft.FLUX","thaumcraft.HEAL"->SoundEvents.CHORUS_FLOWER_GROW;case "thaumcraft.CURSE"->SoundEvents.ELDER_GUARDIAN_CURSE;case "thaumcraft.BREAK"->SoundEvents.END_GATEWAY_SPAWN;default->SoundEvents.ENCHANTMENT_TABLE_USE;};float volume=switch(key){case "thaumcraft.AIR"->.125F;case "thaumcraft.FIRE"->1;case "thaumcraft.EARTH"->.25F;case "thaumcraft.FLUX","thaumcraft.HEAL"->2;case "thaumcraft.CURSE"->.15F;case "thaumcraft.BREAK"->.1F;default->.2F;};float pitch=switch(key){case "thaumcraft.AIR"->2;case "thaumcraft.FLUX","thaumcraft.HEAL"->2+(float)level.random.nextGaussian()*.1F;case "thaumcraft.CURSE"->1+level.random.nextFloat()/2;case "thaumcraft.BREAK","thaumcraft.EXCHANGE"->2+(float)level.random.nextGaussian()*.05F;case "thaumcraft.RIFT"->.7F;default->1+(float)level.random.nextGaussian()*.05F;};level.playSound(null,caster.blockPosition().above(),sound,SoundSource.PLAYERS,volume,pitch);}
}
