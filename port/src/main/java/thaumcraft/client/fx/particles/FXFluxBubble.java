package thaumcraft.client.fx.particles;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import thaumcraft.client.lib.warp.WarpEntityRenderTypes;

/** Narrow literal FXGeneric configuration used by source BlockFluxGoo (frames64/66/65). */
public final class FXFluxBubble extends Particle {
    private static final RenderType TYPE=WarpEntityRenderTypes.particle(ResourceLocation.parse("thaumcraft:textures/misc/particles.png"));
    private final float scale;
    public FXFluxBubble(ClientLevel world,double x,double y,double z) {
        super(world,x,y,z);xd=yd=zd=0;setSize(.1F,.1F);lifetime=2+world.random.nextInt(3);scale=world.random.nextFloat()*.3F+.2F;
        rCol=1;gCol=0;bCol=.5F;alpha=.25F;
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.CUSTOM; }
    @Override public void tick() {
        xo=x;yo=y;zo=z;if(age++>=lifetime)remove();yd-=.04*-.01F;move(xd,yd,zd);
        xd=xd*.9800000190734863+level.random.nextGaussian()*.001F;
        yd=yd*.9800000190734863+level.random.nextGaussian()*.001F;
        zd=zd*.9800000190734863+level.random.nextGaussian()*.001F;
        if(onGround){xd*=.7F;zd*=.7F;}
    }
    @Override public void render(VertexConsumer ignored,Camera camera,float partial) {
        int frame=age>lifetime-2?(lifetime-age<=0?65:66):64;
        float u=frame%64/64F,v=frame/64/64F,size=.1F*scale;
        PoseStack poses=new PoseStack();var eye=camera.getPosition();poses.translate(Mth.lerp(partial,xo,x)-eye.x,Mth.lerp(partial,yo,y)-eye.y,Mth.lerp(partial,zo,z)-eye.z);poses.mulPose(camera.rotation());
        var buffers=Minecraft.getInstance().renderBuffers().bufferSource();var vertices=buffers.getBuffer(TYPE);
        vertex(vertices,poses.last(),-size,-size,u+1/64F,v+1/64F,partial);vertex(vertices,poses.last(),-size,size,u+1/64F,v,partial);
        vertex(vertices,poses.last(),size,size,u,v,partial);vertex(vertices,poses.last(),size,-size,u,v+1/64F,partial);buffers.endBatch(TYPE);
    }
    private void vertex(VertexConsumer vertices,PoseStack.Pose pose,float x,float y,float u,float v,float partial) {
        vertices.addVertex(pose,x,y,0).setColor(rCol,gCol,bCol,alpha).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(getLightColor(partial)).setNormal(pose,0,1,0);
    }
}
