package thaumcraft.client.renderers.tile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.aspects.Aspect;

/** Native world-space label quads; original label/tag sizes and aspect tint, no GUI font overlay. */
final class AlchemyRenderSupport {
    private static final ResourceLocation LABEL = ResourceLocation.parse("thaumcraft:textures/models/label.png");
    private AlchemyRenderSupport() {}
    static void label(PoseStack poses,MultiBufferSource buffers,Aspect aspect,int facing,float height,float distance,float labelSize,float tagSize,float crooked,int light) {
        poses.pushPose(); poses.translate(.5,height,.5);
        poses.mulPose(Axis.YP.rotationDegrees(switch(Direction.from3DDataValue(facing)) { case SOUTH -> 180; case WEST -> 270; case EAST -> 90; default -> 0; }));
        poses.translate(0,0,-distance); poses.mulPose(Axis.ZP.rotationDegrees(crooked));
        quad(poses,buffers.getBuffer(RenderType.entityTranslucent(LABEL)),labelSize,0xffffffff,light,0,0,1,1);
        poses.translate(0,0,-.001);
        quad(poses,buffers.getBuffer(RenderType.entityTranslucent(aspect.getImage())),tagSize,0xff000000 | aspect.getColor(),light,0,0,1,1);
        poses.popPose();
    }
    static void quad(PoseStack poses,VertexConsumer vertices,float scale,int color,int light,float u0,float v0,float u1,float v1) {
        float half = scale / 2;
        vertex(vertices,poses.last(),-half,half,0,u0,v0,color,light,0,0,-1);
        vertex(vertices,poses.last(),half,half,0,u1,v0,color,light,0,0,-1);
        vertex(vertices,poses.last(),half,-half,0,u1,v1,color,light,0,0,-1);
        vertex(vertices,poses.last(),-half,-half,0,u0,v1,color,light,0,0,-1);
    }
    static void vertex(VertexConsumer vertices,PoseStack.Pose pose,float x,float y,float z,float u,float v,int color,int light,float nx,float ny,float nz) {
        vertices.addVertex(pose,x,y,z).setColor(color).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,nx,ny,nz);
    }
}
