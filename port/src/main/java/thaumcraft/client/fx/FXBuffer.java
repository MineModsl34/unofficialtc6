package thaumcraft.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;
import thaumcraft.client.lib.ender.SourceRenderTypes;

/** Source vertex stream adapter. QUADS and TRIANGLE_STRIP become native quads without losing source winding. */
public final class FXBuffer {
    public static final int PARTICLE_POSITION_TEX_COLOR_LMAP=0,POSITION_TEX_LMAP_COLOR=0,POSITION_TEX_COLOR=0;
    private static final FXBuffer INSTANCE=new FXBuffer();
    private record Vertex(float x,float y,float z,float u,float v,float r,float g,float b,float a,int light) {}
    private final List<Vertex> vertices=new ArrayList<>();
    private int mode=7,light=240;
    private double x,y,z,u,v;
    private float r=1,g=1,b=1,a=1;
    private FXBuffer() {}
    public static FXBuffer current() { return INSTANCE; }
    public FXBuffer getBuffer() { return this; }
    public void begin(int mode,int format) { draw(); this.mode=mode; }
    public FXBuffer pos(double x,double y,double z) { this.x=x;this.y=y;this.z=z;return this; }
    public FXBuffer tex(double u,double v) { this.u=u;this.v=v;return this; }
    public FXBuffer color(float r,float g,float b,float a) { this.r=r;this.g=g;this.b=b;this.a=a;return this; }
    public FXBuffer lightmap(int sky,int block) { light=sky<<16|block;return this; }
    public void endVertex() {
        Vector3f p=FxPoseState.poses.last().pose().transformPosition((float)x,(float)y,(float)z,new Vector3f());
        vertices.add(new Vertex(p.x,p.y,p.z,(float)u,(float)v,r,g,b,a,light));
    }
    public void draw() {
        if(vertices.isEmpty())return;
        var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType type=FxPoseState.ender ? SourceRenderTypes.ender(FxPoseState.additive,false,FxPoseState.depthWrite)
            : FxRenderTypes.type(FxPoseState.texture,FxPoseState.additive,FxPoseState.depthTest,FxPoseState.depthWrite,FxPoseState.gui);
        VertexConsumer output=buffers.getBuffer(type);
        if(mode==5) {
            for(int i=2;i<vertices.size();i++) {
                emit(output,vertices.get((i&1)==0?i-2:i-1));emit(output,vertices.get((i&1)==0?i-1:i-2));
                emit(output,vertices.get(i));emit(output,vertices.get(i));
            }
        } else {
            for(int i=0;i+3<vertices.size();i+=4)for(int j=0;j<4;j++)emit(output,vertices.get(i+j));
        }
        vertices.clear();buffers.endBatch(type);
    }
    private static void emit(VertexConsumer output,Vertex p) {
        if(FxPoseState.ender)output.addVertex(p.x,p.y,p.z).setUv(p.u,p.v);
        else output.addVertex(p.x,p.y,p.z).setColor(p.r,p.g,p.b,p.a).setUv(p.u,p.v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(p.light).setNormal(0,1,0);
    }
}
