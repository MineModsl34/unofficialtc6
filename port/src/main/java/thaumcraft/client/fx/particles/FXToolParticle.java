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
import com.mojang.math.Axis;
import thaumcraft.client.renderers.entity.CultRenderTypes;
import thaumcraft.client.renderers.entity.ToolRenderTypes;

/** Literal source FXPlane slash and FXGeneric sounding configurations. */
public final class FXToolParticle extends Particle {
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("thaumcraft:textures/misc/particles.png");
    private final boolean scan;
    private int delay;
    private float yaw, pitch, spin;
    private FXToolParticle(ClientLevel world, double x, double y, double z, boolean scan) {
        super(world, x, y, z); this.scan = scan; lifetime = scan ? 44 : 8; setSize(.01F, .01F); xd = yd = zd = 0; alpha = 0;
    }
    public static FXToolParticle slash(ClientLevel world, double x, double y, double z, double targetX, double targetY, double targetZ) {
        FXToolParticle particle = new FXToolParticle(world, x, y, z, false); double dx = targetX-x, dy = targetY-y, dz = targetZ-z;
        particle.xd = dx/8; particle.yd = dy/8; particle.zd = dz/8; double horizontal = (float)Math.sqrt(dx*dx+dz*dz);
        if (horizontal >= 1E-7) { particle.yaw = (float)(Math.atan2(dz, dx)*180/Math.PI)-90; particle.pitch = (float)(-Math.atan2(dy, horizontal)*180/Math.PI); }
        particle.spin = (float)(world.random.nextGaussian()*20); return particle;
    }
    public static FXToolParticle scan(ClientLevel world, double x, double y, double z, int color, int delay) {
        FXToolParticle particle = new FXToolParticle(world, x, y, z, true); particle.delay = delay;
        particle.rCol = ((color>>16)&255)/255F; particle.gCol = ((color>>8)&255)/255F; particle.bCol = (color&255)/255F; return particle;
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.CUSTOM; }
    @Override public void tick() {
        if (delay > 0) { delay--; return; }
        xo=x; yo=y; zo=z;
        if (!scan) alpha = age <= lifetime/5F ? age/(lifetime/5F) : (float)(lifetime-age)/lifetime;
        if (age++ >= lifetime) remove();
        if (!scan) { x+=xd; y+=yd; z+=zd; }
    }
    @Override public void render(VertexConsumer ignored, Camera camera, float partial) {
        if (delay > 0) return;
        PoseStack poses = new PoseStack(); var eye=camera.getPosition(); poses.translate(Mth.lerp(partial,xo,x)-eye.x,Mth.lerp(partial,yo,y)-eye.y,Mth.lerp(partial,zo,z)-eye.z);
        float progress = Mth.clamp((age+partial)/lifetime,0,1); RenderType type; float size,u,v,opacity,red,green,blue; int light;
        if (scan) {
            poses.mulPose(camera.rotation()); int frame=240+age%15; u=frame%16/16F; v=frame/16/16F; size=.9F;
            float alphaFrame=age*3F/44F; int key=Math.min((int)alphaFrame,2); float[] keys={0,1,.8F,0}; opacity=keys[key]+(keys[key+1]-keys[key])*(alphaFrame-key);
            red=rCol*(1-progress); green=gCol*(1-progress); blue=bCol*(1-progress); light=getLightColor(partial);
            type=(rCol+gCol+bCol)/3F < .25F ? ToolRenderTypes.SCAN_DARK : ToolRenderTypes.SCAN_GLOW;
        } else {
            poses.mulPose(Axis.YP.rotationDegrees(-yaw+90)); poses.mulPose(Axis.ZP.rotationDegrees(pitch+90)); poses.mulPose(Axis.YP.rotationDegrees(spin));
            u=(22+Math.round(progress*8))/32F; v=10/32F; size=.5F*(.5F+progress); opacity=alpha*.5F; red=green=blue=1; light=240;
            type=CultRenderTypes.electric(TEXTURE);
        }
        float step=scan ? 1/16F : 1/32F; var buffers=Minecraft.getInstance().renderBuffers().bufferSource(); var vertices=buffers.getBuffer(type);
        vertex(vertices,poses.last(),-size,size,u+step,v+step,red,green,blue,opacity,light);
        vertex(vertices,poses.last(),size,size,u+step,v,red,green,blue,opacity,light);
        vertex(vertices,poses.last(),size,-size,u,v,red,green,blue,opacity,light);
        vertex(vertices,poses.last(),-size,-size,u,v+step,red,green,blue,opacity,light); buffers.endBatch(type);
    }
    private static void vertex(VertexConsumer vertices,PoseStack.Pose pose,float x,float y,float u,float v,float r,float g,float b,float a,int light) {
        vertices.addVertex(pose,x,y,0).setColor(r,g,b,a).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,1,0);
    }
}
