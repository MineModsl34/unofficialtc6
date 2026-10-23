package thaumcraft.client.renderers.entity.projectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import thaumcraft.common.entities.projectile.EntityFocusMine;
/** BETA26 ModelGrappler geometry, source mine texture, rotations and eight-tick arming pulse. */
public final class RenderFocusMine extends EntityRenderer<EntityFocusMine>{
    private static final ResourceLocation TEXTURE=ResourceLocation.parse("thaumcraft:textures/entity/mine.png");private final ModelPart model;
    public RenderFocusMine(EntityRendererProvider.Context context){super(context);var mesh=new MeshDefinition();var root=mesh.getRoot();root.addOrReplaceChild("core",CubeListBuilder.create().texOffs(0,0).addBox(-1.5F,-1.5F,-1.5F,3,3,3),PartPose.ZERO);root.addOrReplaceChild("p1",CubeListBuilder.create().texOffs(0,10).addBox(-.5F,-.5F,-2.5F,1,1,5),PartPose.ZERO);root.addOrReplaceChild("p2",CubeListBuilder.create().texOffs(0,10).addBox(-.5F,-.5F,-2.5F,1,1,5),PartPose.rotation(0,(float)Math.PI/2,0));root.addOrReplaceChild("p3",CubeListBuilder.create().texOffs(0,10).addBox(-.5F,-.5F,-2.5F,1,1,5),PartPose.rotation((float)Math.PI/2,(float)Math.PI/2,0));model=LayerDefinition.create(mesh,64,32).bakeRoot();}
    @Override public ResourceLocation getTextureLocation(EntityFocusMine mine){return TEXTURE;}
    @Override public void render(EntityFocusMine mine,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){poses.pushPose();poses.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partial,mine.yRotO,mine.getYRot())-90));poses.mulPose(Axis.ZP.rotationDegrees(Mth.rotLerp(partial,mine.xRotO,mine.getXRot())));float f=(mine.counter+partial)%8/8;int green=(int)((1-f)*255);model.render(poses,buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)),15728880,OverlayTexture.NO_OVERLAY,0xffff0000|green<<8|green);poses.popPose();}
}
