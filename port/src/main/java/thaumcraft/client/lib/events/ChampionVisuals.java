package thaumcraft.client.lib.events;

import net.minecraft.world.entity.LivingEntity;
import thaumcraft.client.fx.FXDispatcher;

/** All fourteen TC6 ChampionMod.showFX branches, presentation only. Called once per entity tick. */
public final class ChampionVisuals {
    private ChampionVisuals() {}
    public static void showFX(LivingEntity boss,int id) {
        var random=boss.level().random;var fx=FXDispatcher.INSTANCE;
        if(id<0||id>13)return;
        if(id==12){if(random.nextBoolean())fx.slimeJumpFX(boss,0);return;}
        switch(id){
            case 2->{if(random.nextInt(4)!=0)return;}
            case 3->{if(random.nextFloat()>.3F)return;}
            case 11->{if(random.nextFloat()>.2F)return;}
            case 8,13->{}
            default->{if(random.nextBoolean())return;}
        }
        // Source RNG order is width, depth, height, then branch-specific frame/color/age/size.
        float w=random.nextFloat()*boss.getBbWidth(),d=random.nextFloat()*boss.getBbWidth(),h=random.nextFloat()*boss.getBbHeight();
        if(id==0)h/=3F;
        var box=boss.getBoundingBox();double x=box.minX+w,y=box.minY+h,z=box.minZ+d;
        switch(id){
            case 0->fx.spark(x,y,z,.2F,.3F-random.nextFloat()*.1F,0,.8F+random.nextFloat()*.2F,1);
            case 1->{int frame=704+random.nextInt(4)*3;fx.drawGenericParticles(x,y,z,0,0,0,.5F+random.nextFloat()*.2F,.1F+random.nextFloat()*.2F,.1F+random.nextFloat()*.2F,.7F,false,frame,3,1,3,0,1.2F+random.nextFloat()*.3F,0,0);}
            case 2->fx.drawGenericParticles(x,y,z,0,0,0,.9F,.9F,.9F+random.nextFloat()*.1F,.7F,false,448,9,1,5+random.nextInt(4),0,.6F+random.nextFloat()*.2F,0,0);
            case 3->{int frame=704+random.nextInt(4)*3;fx.drawGenericParticles(x,y,z,0,0,0,.8F+random.nextFloat()*.2F,.8F+random.nextFloat()*.2F,.8F+random.nextFloat()*.2F,.7F,false,frame,3,1,4+random.nextInt(3),0,1+random.nextFloat()*.3F,0,0);}
            case 4->fx.drawGenericParticles(x,y,z,0,-.02,0,random.nextFloat()*.2F,random.nextFloat()*.2F,random.nextFloat()*.2F,.8F,false,640,10,1,8+random.nextInt(4),0,.6F+random.nextFloat()*.4F,0,0);
            case 5->fx.drawGenericParticles(x,y,z,0,0,0,.5F+random.nextFloat()*.1F,.5F+random.nextFloat()*.1F,.5F+random.nextFloat()*.1F,.6F,true,69,4,1,4+random.nextInt(4),0,.8F+random.nextFloat()*.3F,0,0);
            case 6->fx.drawGenericParticles(x,y,z,0,0,0,.8F+random.nextFloat()*.2F,0,.9F+random.nextFloat()*.1F,.7F,true,264,8,1,10+random.nextInt(4),0,.6F+random.nextFloat()*.4F,0,0);
            case 7->fx.drawGenericParticles(x,y,z,0,.03,0,.1F+random.nextFloat()*.1F,.8F+random.nextFloat()*.2F,.1F+random.nextFloat()*.1F,.9F,true,69,4,1,4+random.nextInt(4),0,.5F+random.nextFloat()*.2F,0,0);
            case 8->fx.drawGenericParticles(x,y,z,0,.03,0,.9F+random.nextFloat()*.1F,1,1,.7F,false,640,10,1,8+random.nextInt(4),0,.7F+random.nextFloat()*.2F,0,0);
            case 9->fx.drawGenericParticles(x,y,z,0,-.02,0,.2F,.6F+random.nextFloat()*.1F,.2F+random.nextFloat()*.1F,.5F,false,1,4,2,5+random.nextInt(4),0,.9F+random.nextFloat()*.3F,0,0);
            case 10->fx.drawGenericParticles(x,y,z,0,.02,0,.2F,.6F+random.nextFloat()*.1F,.2F+random.nextFloat()*.1F,.7F,false,579,4,1,8+random.nextInt(4),0,.5F+random.nextFloat()*.2F,.5F,0);
            case 11->fx.drawGenericParticles(x,y,z,0,0,0,.9F+random.nextFloat()*.1F,0,0,.9F,false,579,4,1,4+random.nextInt(4),0,.5F+random.nextFloat()*.2F,0,0);
            case 13->fx.drawGenericParticles(x,y,z,0,-.01,0,.1F+random.nextFloat()*.2F,0,.1F+random.nextFloat()*.1F,.25F,false,1,5,1,6+random.nextInt(6),0,2+random.nextFloat(),.5F,1);
            default->{}
        }
    }
}
