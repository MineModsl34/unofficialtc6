package thaumcraft.common.golems.client;

import net.minecraft.client.Minecraft;
import thaumcraft.client.fx.FXDispatcher;
import thaumcraft.common.golems.EntityThaumcraftGolem;

/** Actual source handleStatusUpdate 5–9 calls, owned by ParticleEngine rather than a model overlay. */
public final class GolemEmotes {
    private GolemEmotes() {}
    public static void status(EntityThaumcraftGolem golem,Byte status){if(!golem.level().isClientSide||golem.level()!=Minecraft.getInstance().level)return;var random=golem.getRandom();var fx=FXDispatcher.INSTANCE;double x=golem.getX(),y=golem.getY()+golem.getBbHeight()+.1,z=golem.getZ();
        switch(status){
            case 5->fx.drawGenericParticles(x,y,z,0,0,0,1,1,1,.5F,false,704+(random.nextBoolean()?0:3),3,1,6,0,2,0,1);
            case 6->fx.drawGenericParticles(x,y,z,0,.025,0,.1F,1,1,.5F,false,15,1,1,10,0,2,0,1);
            case 7->fx.drawGenericParticles(x,y,z,0,.05,0,1,1,1,.5F,false,640,10,1,10,0,2,0,1);
            case 8->fx.drawGenericParticles(x,y,z,0,.01,0,1,1,.1F,.5F,false,14,1,1,20,0,2,0,1);
            case 9->{for(int i=0;i<5;i++)fx.drawGenericParticles(x,golem.getY()+golem.getBbHeight(),z,random.nextGaussian()*.01F,random.nextFloat()*.02,random.nextGaussian()*.01F,1,1,1,.5F,false,13,1,1,20+random.nextInt(20),0,.3F+random.nextFloat()*.4F,0,1);}
            default->{}
        }
    }
}
