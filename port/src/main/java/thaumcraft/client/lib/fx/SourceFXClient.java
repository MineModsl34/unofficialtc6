package thaumcraft.client.lib.fx;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.client.fx.FXDispatcher;
import thaumcraft.client.fx.ParticleEngine;
import thaumcraft.client.fx.other.FXShieldRunes;
import thaumcraft.client.lib.ClientThaumometerHooks;
import thaumcraft.common.lib.fx.SourceFX;
import thaumcraft.common.lib.network.fx.PacketSourceFX;
import thaumcraft.common.tiles.crafting.TileInfusionMatrix;
import thaumcraft.common.tiles.crafting.TilePedestal;

/** Actual source FX consumers and world-owned coalescing/delayed queues. No client gameplay authority. */
@EventBusSubscriber(modid=Thaumcraft.MODID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class SourceFXClient {
    private static final LinkedHashMap<String,PacketSourceFX> essentia=new LinkedHashMap<>();
    private static final ArrayList<Dig> digging=new ArrayList<>();
    private record Dig(PacketSourceFX packet,Entity bore,net.minecraft.world.level.block.state.BlockState state,int remaining) {}
    private static ClientLevel owner;
    private static int count;
    private SourceFXClient() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->SourceFX.install(SourceFXClient::receive,ClientThaumometerHooks::drawScanRunes));}
    private static ClientLevel world(){
        ClientLevel world=Minecraft.getInstance().level;
        if(world!=owner){owner=world;essentia.clear();digging.clear();count=0;}
        return world;
    }
    public static void receive(PacketSourceFX packet){
        ClientLevel world=world();if(world==null)return;
        var fx=FXDispatcher.INSTANCE;BlockPos pos=BlockPos.containing(packet.x(),packet.y(),packet.z());
        switch(packet.kind()){
            case POLLUTION -> {for(int n=0;n<Math.min(40,packet.extra());n++)fx.drawPollutionParticles(pos);}
            case BAMF -> {
                Direction face=packet.face()<0?null:Direction.from3DDataValue(packet.face());
                if(packet.color()==-9999)fx.drawBamf(packet.x(),packet.y(),packet.z(),(packet.flags()&1)!=0,(packet.flags()&2)!=0,face);
                else fx.drawBamf(packet.x(),packet.y(),packet.z(),packet.color(),(packet.flags()&1)!=0,(packet.flags()&2)!=0,face);
            }
            case MIST -> fx.drawBlockMistParticles(pos,packet.color());
            case ARC -> {
                float red=packet.flags()==1?Float.intBitsToFloat(packet.color()):(packet.color()>>16&255)/255F;
                float green=packet.flags()==1?0:(packet.color()>>8&255)/255F;
                float blue=packet.flags()==1?Float.intBitsToFloat(packet.extra()):(packet.color()&255)/255F;
                if(Float.isFinite(red)&&Float.isFinite(blue))fx.arcLightning(packet.tx(),packet.ty(),packet.tz(),packet.x(),packet.y(),packet.z(),red,green,blue,.5F);
            }
            case ZAP -> fx.arcBolt(packet.x(),packet.y(),packet.z(),packet.tx(),packet.ty(),packet.tz(),(packet.color()>>16&255)/255F,(packet.color()>>8&255)/255F,(packet.color()&255)/255F,Math.min(8,Math.max(.001F,Float.intBitsToFloat(packet.extra()))));
            case ESSENTIA -> {
                String key=pos+":"+BlockPos.containing(packet.tx(),packet.ty(),packet.tz())+":"+packet.color();
                if(essentia.size()<2048||essentia.containsKey(key))essentia.put(key,packet);
            }
            case INFUSION -> {
                if(world.getBlockEntity(pos) instanceof TileInfusionMatrix matrix){
                    BlockPos source=BlockPos.containing(packet.tx(),packet.ty(),packet.tz());String key=source+":"+packet.color();
                    if(matrix.sourceFX.size()<3168||matrix.sourceFX.containsKey(key))matrix.sourceFX.put(key,matrix.new SourceFX(source,world.getBlockEntity(source) instanceof TilePedestal?60:15,packet.color()));
                }
            }
            case FOCUS_PARTICLE -> SourceFocusFX.render(world,packet.key(),packet.x(),packet.y(),packet.z(),packet.tx(),packet.ty(),packet.tz());
            case FOCUS_TRAIL,FOCUS_IMPACT,FOCUS_BURST -> {
                String[] keys=packet.key().split("%",21);if(keys.length>20)return;
                int number=Math.max(1,(packet.kind()==PacketSourceFX.Kind.FOCUS_TRAIL?10:packet.kind()==PacketSourceFX.Kind.FOCUS_IMPACT?15:20)/keys.length);
                double noise=packet.kind()==PacketSourceFX.Kind.FOCUS_TRAIL?.05:packet.kind()==PacketSourceFX.Kind.FOCUS_IMPACT?.15:.4;
                for(String key:keys)if(thaumcraft.api.casters.FocusEngine.getElement(key) instanceof thaumcraft.api.casters.FocusEffect)for(int n=0;n<number;n++)SourceFocusFX.render(world,key,packet.x(),packet.y(),packet.z(),packet.tx()+world.random.nextGaussian()*noise,packet.ty()+world.random.nextGaussian()*noise,packet.tz()+world.random.nextGaussian()*noise);
            }
            case BORE -> {var bore=world.getEntity(packet.color());var state=world.getBlockState(pos);int delay=Math.max(0,Math.min(200,packet.extra()));if(bore!=null&&!state.isAir()&&delay>0&&digging.size()<256)digging.add(new Dig(packet,bore,state,delay));}
            case SHIELD -> shield(world,packet.color(),packet.extra());
            case LOCAL -> local(world,packet);
        }
    }
    private static void shield(ClientLevel world,int source,int target){
        Entity entity=world.getEntity(source);if(entity==null)return;
        if(target>=0){Entity other=world.getEntity(target);float yaw=0,pitch=90;
            if(other!=null){double dx=entity.getX()-other.getX(),dz=entity.getZ()-other.getZ();double dy=(entity.getBoundingBox().minY+entity.getBoundingBox().maxY-other.getBoundingBox().minY-other.getBoundingBox().maxY)/2;
                yaw=(float)(Math.atan2(dz,dx)*180/Math.PI)-90;pitch=(float)(-Math.atan2(dy,Mth.sqrt((float)(dx*dx+dz*dz)))*180/Math.PI);}
            Minecraft.getInstance().particleEngine.add(new FXShieldRunes(world,entity.getX(),entity.getY(),entity.getZ(),entity,8,yaw,pitch));
        }else{
            if(target==-1||target==-3)Minecraft.getInstance().particleEngine.add(new FXShieldRunes(world,entity.getX(),entity.getY(),entity.getZ(),entity,8,0,90));
            if(target==-1||target==-2)Minecraft.getInstance().particleEngine.add(new FXShieldRunes(world,entity.getX(),entity.getY(),entity.getZ(),entity,8,0,270));
        }
    }
    @EventBusSubscriber(modid=Thaumcraft.MODID,value=Dist.CLIENT)
    public static final class Ticks {
        @SubscribeEvent public static void tick(ClientTickEvent.Pre event){
            ClientLevel world=world();if(world==null||Minecraft.getInstance().isPaused())return;count++;
            for(var packet:essentia.values()){
                BlockPos target=BlockPos.containing(packet.x(),packet.y(),packet.z());
                if(world.getBlockEntity(target) instanceof TileInfusionMatrix)target=target.below();
                FXDispatcher.INSTANCE.essentiaTrailFx(BlockPos.containing(packet.tx(),packet.ty(),packet.tz()),target,count,packet.color(),.1F,packet.extra());
            }essentia.clear();
            for(int i=digging.size()-1;i>=0;i--){var dig=digging.get(i);var p=dig.packet();BlockPos pos=BlockPos.containing(p.x(),p.y(),p.z());
                FXDispatcher.INSTANCE.boreDigFx(pos.getX(),pos.getY(),pos.getZ(),dig.bore(),dig.state(),0,p.extra());
                if(dig.remaining()<=1)digging.remove(i);else digging.set(i,new Dig(p,dig.bore(),dig.state(),dig.remaining()-1));}
        }
    }
    private static void local(ClientLevel world,PacketSourceFX p){
        var fx=FXDispatcher.INSTANCE;var pos=BlockPos.containing(p.x(),p.y(),p.z());
        switch(p.key()){
            case "matrix" -> {if(world.getBlockEntity(pos) instanceof TileInfusionMatrix matrix)matrix(world,matrix);}
            case "shimmerleaf" -> fx.drawWispyMotes(p.x(),p.y(),p.z(),p.tx(),p.ty(),p.tz(),10,.3F+world.random.nextFloat()*.3F,.7F+world.random.nextFloat()*.3F,.7F+world.random.nextFloat()*.3F,0);
            case "vishroom" -> fx.drawWispyMotes(p.x(),p.y(),p.z(),0,0,0,10,.5F,.3F,.8F,.001F);
            case "grass" -> fx.drawWispyMotesOnBlock(pos,400,-.01F);
            case "nitor_flame" -> fx.drawNitorFlames(p.x(),p.y(),p.z(),p.tx(),p.ty(),p.tz(),p.color(),0);
            case "nitor_core" -> fx.drawNitorCore(p.x(),p.y(),p.z(),0,0,0);
            case "soap" -> fx.crucibleBubble((float)p.x(),(float)p.y(),(float)p.z(),1,.8F,.9F);
            case "soap_finish" -> fx.crucibleBubble((float)p.x(),(float)p.y(),(float)p.z(),1,.7F,.9F);
            case "bath" -> {
                var bubble=new thaumcraft.client.fx.particles.FXGeneric(world,p.x(),p.y(),p.z(),0,0,0);
                bubble.setMaxAge(10+world.random.nextInt(10));bubble.setScale(world.random.nextFloat()*.3F+.3F);bubble.setRBGColorF(1,1,1);
                bubble.setRandomMovementScale(.001F,.001F,.001F);bubble.setGravity(-.01F);bubble.setAlphaF(.25F);bubble.setParticle(64);bubble.setFinalFrames(65,66);ParticleEngine.addEffect(world,bubble);
            }
            case "wispy" -> fx.drawWispyMotes(p.x(),p.y(),p.z(),p.tx(),p.ty(),p.tz(),p.extra(),(p.color()>>16&255)/255F,(p.color()>>8&255)/255F,(p.color()&255)/255F,-.01F);
            case "levitator" -> fx.drawLevitatorParticles(p.x(),p.y(),p.z(),p.tx(),p.ty(),p.tz());
            case "golem_fly" -> fx.drawGolemFlyParticles(p.x(),p.y(),p.z(),p.tx(),p.ty(),p.tz());
            case "pollution" -> fx.drawPollutionParticles(pos);
            case "mist" -> fx.drawBlockMistParticlesFlat(pos,p.color());
            case "bubble" -> fx.crucibleBubble((float)p.x(),(float)p.y(),(float)p.z(),(p.color()>>16&255)/255F,(p.color()>>8&255)/255F,(p.color()&255)/255F);
            default -> {if(!SourceBlockFX.render(world,p))SourceDeviceFX.render(world,p);}
        }
    }
    private static void matrix(ClientLevel world,TileInfusionMatrix matrix){
        var fx=FXDispatcher.INSTANCE;BlockPos pos=matrix.getBlockPos();var random=world.random;
        if(matrix.crafting)fx.blockRunes(pos.getX(),pos.getY()-2,pos.getZ(),.5F+random.nextFloat()*.2F,.1F,.7F+random.nextFloat()*.3F,25,-.03F);
        for(String key:matrix.sourceFX.keySet().toArray(String[]::new)){
            var effect=matrix.sourceFX.get(key);if(effect.ticks<=0){matrix.sourceFX.remove(key);continue;}
            if(effect.loc.equals(pos)){
                Entity entity=world.getEntity(effect.color);
                if(entity!=null)for(int n=0;n<4;n++)fx.drawInfusionParticles4(entity.getX()+(random.nextFloat()-random.nextFloat())*entity.getBbWidth(),entity.getBoundingBox().minY+random.nextFloat()*entity.getBbHeight(),entity.getZ()+(random.nextFloat()-random.nextFloat())*entity.getBbWidth(),pos.getX(),pos.getY(),pos.getZ());
            }else if(world.getBlockEntity(effect.loc) instanceof TilePedestal pedestal){
                var stack=pedestal.getStackInSlot(0);
                if(!stack.isEmpty()){
                    if(random.nextInt(3)==0)fx.drawInfusionParticles3(effect.loc.getX()+random.nextFloat(),effect.loc.getY()+random.nextFloat()+1F,effect.loc.getZ()+random.nextFloat(),pos.getX(),pos.getY(),pos.getZ());
                    else for(int n=0;n<4;n++){
                        if(stack.getItem() instanceof BlockItem item)fx.drawInfusionParticles2(effect.loc.getX()+random.nextFloat(),effect.loc.getY()+random.nextFloat()+1F,effect.loc.getZ()+random.nextFloat(),pos,item.getBlock().defaultBlockState(),0);
                        else fx.drawInfusionParticles1(effect.loc.getX()+.4F+random.nextFloat()*.2F,effect.loc.getY()+1.23F+random.nextFloat()*.2F,effect.loc.getZ()+.4F+random.nextFloat()*.2F,pos,stack);
                    }
                }
            }else effect.ticks=0;
            effect.ticks--;
        }
        if(matrix.crafting&&matrix.stability<0&&random.nextInt(250)<=Math.abs(matrix.stability))fx.spark(pos.getX()+random.nextFloat(),pos.getY()+random.nextFloat(),pos.getZ()+random.nextFloat(),3+random.nextFloat()*2,.7F+random.nextFloat()*.1F,.1F,.65F+random.nextFloat()*.1F,.8F);
        var problems=matrix.problemPositions();
        if(matrix.active&&!problems.isEmpty()&&random.nextInt(25)==0){var bad=problems.get(random.nextInt(problems.size()));fx.spark(bad.getX()+random.nextFloat(),bad.getY()+random.nextFloat(),bad.getZ()+random.nextFloat(),2+random.nextFloat(),.7F+random.nextFloat()*.1F,.1F,.65F+random.nextFloat()*.1F,.8F);}
    }
}
