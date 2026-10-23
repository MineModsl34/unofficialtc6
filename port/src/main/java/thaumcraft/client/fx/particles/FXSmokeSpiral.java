package thaumcraft.client.fx.particles;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import thaumcraft.client.lib.warp.WarpEntityRenderTypes;

/** Literal source 720-degree smoke orbit, bottom clamp, five atlas frames and 20..29 tick life. */
public final class FXSmokeSpiral extends Particle implements thaumcraft.client.fx.SourceFxLayer {
    private final float radius,scale;private final int start,minY;
    public FXSmokeSpiral(ClientLevel level,double x,double y,double z,float radius,int start,int minY){this(level,x,y,z,radius,start,minY,0xFFFFFF);}
    @Override public int getFXLayer(){return 1;}
    public void setRBGColorF(float r,float g,float b){setColor(r,g,b);}
    public FXSmokeSpiral(ClientLevel level,double x,double y,double z,float radius,int start,int minY,int color){super(level,x,y,z);this.radius=radius;this.start=start;this.minY=minY;scale=(random.nextFloat()*.5F+.5F)*2;lifetime=20+level.random.nextInt(10);setSize(.01F,.01F);rCol=(color>>16&255)/255F;gCol=(color>>8&255)/255F;bCol=(color&255)/255F;}
    @Override public ParticleRenderType getRenderType(){return ParticleRenderType.CUSTOM;}
    @Override public void tick(){alpha=(lifetime-age)/(float)lifetime;if(age++>=lifetime)remove();}
    @Override public void render(VertexConsumer ignored,Camera camera,float partial){
        float progress=(age+partial)/lifetime,a=(start+720*progress)*Mth.DEG_TO_RAD,b=(90-180*progress)*Mth.DEG_TO_RAD;
        float ox=-Mth.sin(a)*Mth.cos(b)*radius,oz=Mth.cos(a)*Mth.cos(b)*radius,oy=-Mth.sin(b)*radius;
        int frame=(int)(1+age/(float)lifetime*4);float u=frame%16/64F,v=frame/16/64F,size=.15F*scale;
        var eye=camera.getPosition();PoseStack poses=new PoseStack();poses.translate(x+ox-eye.x,Math.max(y+oy,minY+.1F)-eye.y,z+oz-eye.z);poses.mulPose(camera.rotation());
        var type=WarpEntityRenderTypes.particle(ResourceLocation.parse("thaumcraft:textures/misc/particles.png"));var buffers=Minecraft.getInstance().renderBuffers().bufferSource();var vertices=buffers.getBuffer(type);
        vertex(vertices,poses.last(),-size,-size,u+1/64F,v+1/64F,partial);vertex(vertices,poses.last(),-size,size,u+1/64F,v,partial);vertex(vertices,poses.last(),size,size,u,v,partial);vertex(vertices,poses.last(),size,-size,u,v+1/64F,partial);buffers.endBatch(type);
    }
    private void vertex(VertexConsumer vertices,PoseStack.Pose pose,float x,float y,float u,float v,float partial){vertices.addVertex(pose,x,y,0).setColor(rCol,gCol,bCol,.66F*alpha).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(getLightColor(partial)).setNormal(pose,0,1,0);}
}
