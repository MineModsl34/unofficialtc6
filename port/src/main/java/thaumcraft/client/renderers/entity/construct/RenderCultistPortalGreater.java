package thaumcraft.client.renderers.entity.construct;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.client.lib.warp.WarpEntityRenderTypes;
import thaumcraft.common.entities.monster.boss.EntityCultistPortalGreater;
public final class RenderCultistPortalGreater extends EntityRenderer<EntityCultistPortalGreater>{
    private static final ResourceLocation TEXTURE=ResourceLocation.parse("thaumcraft:textures/misc/cultist_portal.png");public RenderCultistPortalGreater(EntityRendererProvider.Context context){super(context);shadowRadius=.1F;shadowStrength=.5F;}
    @Override public ResourceLocation getTextureLocation(EntityCultistPortalGreater entity){return TEXTURE;}
    @Override public void render(EntityCultistPortalGreater entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){float height=1.5F;int opening=(int)Math.min(50,entity.tickCount+partial);if(entity.hurtTime>0){double sine=Math.sin(entity.hurtTime*72*Math.PI/180);height-=sine/4;opening=(int)(opening+6*sine);}if(entity.pulse>0){double sine=Math.sin(entity.pulse*36*Math.PI/180);height+=sine/4;opening=(int)(opening+12*sine);}float scale=opening/50F*1.3F,missing=(1-entity.getHealth()/entity.getMaxHealth())/3;float bob=(float)Math.sin(entity.tickCount/(5-12*missing))*missing+missing,bob2=(float)Math.sin(entity.tickCount/(6-15*missing))*missing+missing,alpha=1-bob;height-=bob/4;scale-=bob2/3;poses.pushPose();poses.translate(0,entity.getBbHeight()/2,0);poses.mulPose(entityRenderDispatcher.cameraOrientation());var vertex=buffers.getBuffer(WarpEntityRenderTypes.particle(TEXTURE));int frame=15-(int)(System.nanoTime()/50000000%16);float u=frame/16F,right=u+1/16F;add(vertex,poses.last(),-scale,-height,right,0,alpha);add(vertex,poses.last(),scale,-height,u,0,alpha);add(vertex,poses.last(),scale,height,u,1,alpha);add(vertex,poses.last(),-scale,height,right,1,alpha);poses.popPose();super.render(entity,yaw,partial,poses,buffers,light);}
    private static void add(VertexConsumer consumer,PoseStack.Pose pose,float x,float y,float u,float v,float alpha){consumer.addVertex(pose,x,y,0).setColor(1,1,1,alpha).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(220).setNormal(pose,0,0,-1);}
}
