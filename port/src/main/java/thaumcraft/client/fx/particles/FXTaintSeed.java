package thaumcraft.client.fx.particles;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import thaumcraft.client.lib.warp.WarpEntityRenderTypes;
import thaumcraft.client.renderers.entity.CultRenderTypes;

/** Only the literal drawTaintParticles and drawLightningFlash configurations used by seeds. */
public final class FXTaintSeed extends Particle {
    private final boolean flash;private final int frame;private final float scale,rotationSpeed;private float rotation,previousRotation;
    public FXTaintSeed(ClientLevel level,double x,double y,double z,double dx,double dy,double dz,boolean flash,float scale){
        super(level,x,y,z);setSize(.1F,.1F);xd=dx;yd=dy;zd=dz;this.flash=flash;this.scale=scale;
        if(flash){lifetime=5+level.random.nextInt(5);rCol=.7F;gCol=.1F;bCol=.9F;frame=108+level.random.nextInt(4);rotationSpeed=0;}
        else{lifetime=80+level.random.nextInt(20);rCol=.4F+level.random.nextFloat()*.2F;gCol=.1F+level.random.nextFloat()*.3F;bCol=.5F+level.random.nextFloat()*.2F;frame=57+level.random.nextInt(3);rotationSpeed=(level.random.nextBoolean()?-1:1)*Mth.DEG_TO_RAD;}
        rotation=level.random.nextFloat()*(float)Math.PI*2;previousRotation=rotation;
    }
    @Override public ParticleRenderType getRenderType(){return ParticleRenderType.CUSTOM;}
    @Override public void tick(){xo=x;yo=y;zo=z;if(age++>=lifetime)remove();previousRotation=rotation;rotation+=(float)Math.PI*rotationSpeed*2;yd-=flash?0:.04*.2F;move(xd,yd,zd);double slowdown=flash?.98F:.975F;xd*=slowdown;yd*=slowdown;zd*=slowdown;xd+=level.random.nextGaussian()*0;yd+=level.random.nextGaussian()*0;zd+=level.random.nextGaussian()*0;if(onGround){xd*=.7F;zd*=.7F;}}
    @Override public void render(VertexConsumer ignored,Camera camera,float partial){
        float progress=Mth.clamp((age+partial)/lifetime,0,1),tickProgress=Math.min(age/(float)lifetime,1),size=.1F*scale*(flash?1:1-.75F*tickProgress),opacity=(flash?.5F:.75F)*(1-tickProgress);
        float u=frame%16/16F,v=frame/16/16F;var eye=camera.getPosition();PoseStack poses=new PoseStack();poses.translate(Mth.lerp(partial,xo,x)-eye.x,Mth.lerp(partial,yo,y)-eye.y,Mth.lerp(partial,zo,z)-eye.z);poses.mulPose(camera.rotation());poses.mulPose(Axis.ZP.rotation(rotation+(rotation-previousRotation)*partial));
        ResourceLocation texture=ResourceLocation.parse("thaumcraft:textures/misc/particles.png");var type=flash?CultRenderTypes.electric(texture):WarpEntityRenderTypes.particle(texture);var buffers=Minecraft.getInstance().renderBuffers().bufferSource();var vertices=buffers.getBuffer(type);
        vertex(vertices,poses.last(),-size,-size,u+1/16F,v+1/16F,opacity,progress,partial);vertex(vertices,poses.last(),-size,size,u+1/16F,v,opacity,progress,partial);vertex(vertices,poses.last(),size,size,u,v,opacity,progress,partial);vertex(vertices,poses.last(),size,-size,u,v+1/16F,opacity,progress,partial);buffers.endBatch(type);
    }
    private void vertex(VertexConsumer vertices,PoseStack.Pose pose,float x,float y,float u,float v,float opacity,float progress,float partial){vertices.addVertex(pose,x,y,0).setColor(rCol*(1-progress),gCol*(1-progress),bCol*(1-progress),opacity).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(getLightColor(partial)).setNormal(pose,0,1,0);}
}
