package thaumcraft.client.fx.particles;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import thaumcraft.client.renderers.entity.CultRenderTypes;

/** Source FXDispatcher.crucibleBubble literal blue Collector mode10 configuration. */
public final class FXFollowingBubble extends Particle {
    private static final ResourceLocation TEXTURE=ResourceLocation.parse("thaumcraft:textures/misc/particles.png");
    private final float scale;
    public FXFollowingBubble(ClientLevel world,double x,double y,double z) {
        super(world,x,y,z); xd=yd=zd=0; setSize(.1F,.1F); lifetime=15+world.random.nextInt(10); scale=world.random.nextFloat()*.3F+.3F;
        rCol=gCol=.33F; bCol=1; alpha=1;
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.CUSTOM; }
    @Override public void tick() {
        xo=x;yo=y;zo=z;if(age++>=lifetime)remove();yd-=.04*-.001F;move(xd,yd,zd);
        xd=xd*.9800000190734863+level.random.nextGaussian()*.002F;
        yd=yd*.9800000190734863+level.random.nextGaussian()*.002F;
        zd=zd*.9800000190734863+level.random.nextGaussian()*.002F;
        if(onGround){xd*=.7F;zd*=.7F;}
    }
    @Override public void render(VertexConsumer ignored,Camera camera,float partial) {
        int frame=age>lifetime-3?(lifetime-age<=0?65:66):64;
        float u=frame%64/64F,v=frame/64/64F,size=.1F*scale,color=1-Mth.clamp((age+partial)/lifetime,0,1);
        PoseStack poses=new PoseStack();var eye=camera.getPosition();poses.translate(Mth.lerp(partial,xo,x)-eye.x,Mth.lerp(partial,yo,y)-eye.y,Mth.lerp(partial,zo,z)-eye.z);poses.mulPose(camera.rotation());
        var type=CultRenderTypes.electric(TEXTURE);var buffers=Minecraft.getInstance().renderBuffers().bufferSource();var vertices=buffers.getBuffer(type);
        vertex(vertices,poses.last(),-size,-size,u+1/64F,v+1/64F,color,partial);vertex(vertices,poses.last(),-size,size,u+1/64F,v,color,partial);
        vertex(vertices,poses.last(),size,size,u,v,color,partial);vertex(vertices,poses.last(),size,-size,u,v+1/64F,color,partial);buffers.endBatch(type);
    }
    private void vertex(VertexConsumer vertices,PoseStack.Pose pose,float x,float y,float u,float v,float color,float partial) {
        vertices.addVertex(pose,x,y,0).setColor(rCol*color,gCol*color,bCol*color,alpha).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(getLightColor(partial)).setNormal(pose,0,1,0);
    }
}
