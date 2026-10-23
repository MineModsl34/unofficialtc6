package thaumcraft.client.renderers.entity.projectile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import thaumcraft.client.renderers.entity.Boss8RenderTypes;
import thaumcraft.common.entities.projectile.EntityHomingShard;

/** Source animated 64-grid billboard and camera-facing six-point beam taper. */
public final class RenderHomingShard extends EntityRenderer<EntityHomingShard> {
    private static final ResourceLocation ATLAS=ResourceLocation.parse("thaumcraft:textures/misc/particles.png"),BEAM=ResourceLocation.parse("thaumcraft:textures/misc/beaml.png");
    public RenderHomingShard(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(EntityHomingShard entity){return ATLAS;}
    @Override public void render(EntityHomingShard entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
        Vec3 current=entity.getPosition(partial),camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(),start=current;
        VertexConsumer trail=buffers.getBuffer(Boss8RenderTypes.sourceBeam(BEAM));int count=entity.trail.size();
        for(int i=0;i<count;i++){Vec3 end=entity.trail.get(i),normal=start.subtract(camera).cross(end.subtract(start)).normalize().scale(.25*(count-i)/(double)count);
            beamVertex(trail,poses.last(),start.add(normal).subtract(current),0,0);beamVertex(trail,poses.last(),end.add(normal).subtract(current),1,0);
            beamVertex(trail,poses.last(),end.subtract(normal).subtract(current),1,1);beamVertex(trail,poses.last(),start.subtract(normal).subtract(current),0,1);start=end;}
        poses.pushPose();poses.mulPose(entityRenderDispatcher.cameraOrientation());float scale=.4F+.1F*entity.getStrength();poses.scale(scale,scale,scale);
        VertexConsumer sprite=buffers.getBuffer(Boss8RenderTypes.sourceBeam(ATLAS));float u=(8+entity.tickCount%8)/64F,v=4/64F;
        spriteVertex(sprite,poses.last(),-.5F,-.5F,u+1/64F,v+1/64F);spriteVertex(sprite,poses.last(),-.5F,.5F,u+1/64F,v);
        spriteVertex(sprite,poses.last(),.5F,.5F,u,v);spriteVertex(sprite,poses.last(),.5F,-.5F,u,v+1/64F);poses.popPose();super.render(entity,yaw,partial,poses,buffers,light);
    }
    private static void spriteVertex(VertexConsumer vertices,PoseStack.Pose pose,float x,float y,float u,float v){vertices.addVertex(pose,x,y,0).setColor(.9F,.075F,.9525F,1).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(240).setNormal(pose,0,1,0);}
    private static void beamVertex(VertexConsumer vertices,PoseStack.Pose pose,Vec3 position,float u,float v){vertices.addVertex(pose,(float)position.x,(float)position.y,(float)position.z).setColor(.405F,.075F,.525F,.5F).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(240).setNormal(pose,0,1,0);}
}
