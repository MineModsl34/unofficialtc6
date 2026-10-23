package thaumcraft.client.lib.fx;

import java.util.WeakHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import thaumcraft.client.fx.FXDispatcher;
import thaumcraft.common.entities.monster.boss.EntityEldritchGolem;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.network.fx.PacketSourceFX;
import thaumcraft.common.tiles.crafting.TileGolemBuilder;

/** Literal remaining source presentation consumers. Common hooks only describe client-local events. */
public final class SourceMiscFX {
    private record Arc(int x,int y,int z,int ticks) {}
    private static final WeakHashMap<EntityEldritchGolem,Arc> ARCS=new WeakHashMap<>();
    private static java.lang.ref.WeakReference<ClientLevel> owner=new java.lang.ref.WeakReference<>(null);
    private SourceMiscFX() {}
    public static boolean render(ClientLevel world,PacketSourceFX p) {
        if(owner.get()!=world){ARCS.clear();owner=new java.lang.ref.WeakReference<>(world);}
        var fx=FXDispatcher.INSTANCE;var random=world.random;BlockPos pos=BlockPos.containing(p.x(),p.y(),p.z());
        Entity entity=world.getEntity(p.color());
        switch(p.key()) {
            case "smelter_vent" -> {
                Direction d=Direction.from3DDataValue(p.extra()).getOpposite();
                for(int n=0;n<4;n++) {
                    float x=.1F-random.nextFloat()*.2F,z=.1F-random.nextFloat()*.2F,y=.1F-random.nextFloat()*.2F;
                    float vx=.1F-random.nextFloat()*.2F,vz=.1F-random.nextFloat()*.2F,vy=.1F-random.nextFloat()*.2F;
                    fx.drawVentParticles(pos.getX()+.5F+x+d.getStepX(),pos.getY()+.5F+y,pos.getZ()+.5F+z+d.getStepZ(),d.getStepX()/4F+vx,d.getStepY()/4F+vy,d.getStepZ()/4F+vz,11184810);
                }
            }
            case "infernal_lava" -> {
                Direction d=Direction.from3DDataValue(p.extra());
                for(int n=0;n<5;n++) {
                    fx.furnaceLavaFx(pos.getX(),pos.getY(),pos.getZ(),d.getStepX(),d.getStepZ());
                    world.playLocalSound(pos.getX()+.5F,pos.getY()+.5F,pos.getZ()+.5F,SoundEvents.LAVA_POP,SoundSource.BLOCKS,.1F+random.nextFloat()*.1F,.9F+random.nextFloat()*.15F,false);
                }
            }
            case "pedestal" -> {
                if(p.extra()==11)fx.drawBamf(pos.above(),.75F,0,.5F,true,true,null);
                else if(p.extra()==12)fx.drawBamf(pos.above(),true,true,null);
                else if(p.extra()==5)fx.drawPedestalShield(pos);
            }
            case "golem_builder" -> {if(world.getBlockEntity(pos) instanceof TileGolemBuilder tile)builder(world,pos,tile);}
            case "elemental_sword" -> {
                if(entity!=null) {
                    int floor=(int)(entity.getBoundingBox().minY-2);
                    if(entity.onGround())floor=Mth.floor(entity.getBoundingBox().minY);
                    for(int n=0;n<5;n++)fx.smokeSpiral(entity.getX(),entity.getBoundingBox().minY+entity.getBbHeight()/2F,entity.getZ(),1.5F,random.nextInt(360),floor,14540253);
                    if(entity.onGround()) {
                        float angle=random.nextFloat()*360F,vx=-Mth.sin(angle/180F*(float)Math.PI)/5F,vz=Mth.cos(angle/180F*(float)Math.PI)/5F;
                        // This one ground smoke particle is genuinely vanilla in the source.
                        world.addParticle(ParticleTypes.SMOKE,entity.getX(),entity.getBoundingBox().minY+.1F,entity.getZ(),vx,0,vz);
                    }
                }
            }
            case "elemental_axe" -> {
                if(entity!=null)fx.crucibleBubble((float)entity.getX()+(random.nextFloat()-random.nextFloat())*.2F,(float)entity.getY()+entity.getBbHeight()+(random.nextFloat()-random.nextFloat())*.2F,(float)entity.getZ()+(random.nextFloat()-random.nextFloat())*.2F,.33F,.33F,1);
            }
            case "taint_land" -> {if(entity!=null)for(int n=0;n<10;n++)fx.taintLandFX(entity);}
            case "bottle_taint" -> {if(entity!=null){for(int n=0;n<100;n++)fx.taintsplosionFX(entity);fx.bottleTaintBreak(entity.getX(),entity.getY(),entity.getZ());}}
            case "causality" -> {if(entity!=null)causality(world,entity);}
            case "taintacle_arise" -> {if(entity!=null)fx.tentacleAriseFX(entity);}
            case "eldritch_golem" -> {if(entity instanceof EntityEldritchGolem golem)golem(golem);}
            case "eldritch_golem_charge" -> {if(entity instanceof EntityEldritchGolem golem)charge(golem);}
            default -> {return false;}
        }
        return true;
    }
    private static void builder(ClientLevel world,BlockPos pos,TileGolemBuilder tile) {
        var random=world.random;
        if(tile.press<90&&tile.cost>0&&tile.golem>0) {
            tile.press+=6;
            if(tile.press>=60) {
                world.playLocalSound(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,SoundEvents.LAVA_EXTINGUISH,SoundSource.BLOCKS,.66F,1+random.nextFloat()*.1F,false);
                vent(world,pos,16);
            }
        }
        if(tile.press>=90&&random.nextInt(8)==0) {
            vent(world,pos,1);
            world.playLocalSound(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,SoundEvents.LAVA_EXTINGUISH,SoundSource.BLOCKS,.1F,1+random.nextFloat()*.1F,false);
        }
        if(tile.press>0&&(tile.cost<=0||tile.golem==-1)) {if(tile.press>=90)vent(world,pos,10);tile.press-=3;}
    }
    private static void vent(ClientLevel world,BlockPos pos,int count) {
        for(int n=0;n<count;n++)FXDispatcher.INSTANCE.drawVentParticles(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,world.random.nextGaussian()*.1,0,world.random.nextGaussian()*.1,11184810);
    }
    private static void causality(ClientLevel world,Entity entity) {
        var fx=FXDispatcher.INSTANCE;var random=entity.getRandom();
        for(double i=0;i<3;i++) {
            double coefficient=i/3;
            fx.drawAlumentum((float)(entity.xo+(entity.getX()-entity.xo)*coefficient),(float)(entity.yo+(entity.getY()-entity.yo)*coefficient)+entity.getBbHeight()/2F,(float)(entity.zo+(entity.getZ()-entity.zo)*coefficient),.0125F*(random.nextFloat()-.5F),.0125F*(random.nextFloat()-.5F),.0125F*(random.nextFloat()-.5F),.8F+random.nextFloat()*.2F,.3F+random.nextFloat()*.1F,random.nextFloat()*.1F,.5F,4);
            fx.drawGenericParticles(entity.getX()+world.random.nextGaussian()*.2F,entity.getY()+world.random.nextGaussian()*.2F,entity.getZ()+world.random.nextGaussian()*.2F,0,0,0,1,1,1,.7F,false,448,8,1,8,0,.3F,0,1);
        }
    }
    private static void charge(EntityEldritchGolem entity) {
        if(ARCS.containsKey(entity))return;
        var random=entity.getRandom();float radius=2+random.nextFloat()*2;double angle=Math.toRadians(random.nextInt(360));
        int x=Mth.floor(entity.getX()+radius*Math.cos(angle)),y=Mth.floor(entity.getY()),z=Mth.floor(entity.getZ()+radius*Math.sin(angle));
        BlockPos pos=new BlockPos(x,y,z);
        // Literal source quirk: its test BlockPos is not rebuilt when the local y is decremented.
        for(int n=0;n<5&&entity.level().isEmptyBlock(pos);y--)n++;
        if(entity.level().isEmptyBlock(pos.above())&&!entity.level().isEmptyBlock(pos)) {
            ARCS.put(entity,new Arc(x,y,z,8+random.nextInt(5)));
            entity.playSound(SoundsTC.jacobs,.8F,1+(random.nextFloat()-random.nextFloat())*.05F);
        }
    }
    private static void golem(EntityEldritchGolem entity) {
        if(!entity.isHeadless())return;
        entity.setXRot(0);var random=entity.getRandom();var fx=FXDispatcher.INSTANCE;
        float c=Mth.cos(-entity.yBodyRot*(float)(Math.PI/180)-(float)Math.PI),s=Mth.sin(-entity.yBodyRot*(float)(Math.PI/180)-(float)Math.PI);
        float pitch=-Mth.cos(-entity.getXRot()*(float)(Math.PI/180));
        float vx=s*pitch,vz=c*pitch;
        if(random.nextInt(20)==0) {
            float a=(random.nextFloat()-random.nextFloat())/3F,b=(random.nextFloat()-random.nextFloat())/3F;
            fx.spark((float)(entity.getX()+vx+a),(float)entity.getY()+entity.getEyeHeight()-.75F,(float)(entity.getZ()+vz+b),3,.65F+random.nextFloat()*.1F,1,1,.8F);
        }
        fx.drawVentParticles((float)entity.getX()+vx*.4,(float)entity.getY()+entity.getEyeHeight()-1.25F,(float)entity.getZ()+vz*.4,0,.001,0,5592405,4);
        Arc arc=ARCS.get(entity);
        if(arc!=null) {
            fx.arcLightning(entity.getX(),entity.getY()+entity.getBbHeight()/2F,entity.getZ(),arc.x()+.5,arc.y()+1,arc.z()+.5,.65F+random.nextFloat()*.1F,1,1,1-arc.ticks()/10F);
            if(arc.ticks()<=1)ARCS.remove(entity);else ARCS.put(entity,new Arc(arc.x(),arc.y(),arc.z(),arc.ticks()-1));
        }
    }
}
