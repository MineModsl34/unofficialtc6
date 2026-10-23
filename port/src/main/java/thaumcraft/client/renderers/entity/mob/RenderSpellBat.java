package thaumcraft.client.renderers.entity.mob;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import thaumcraft.common.entities.monster.EntitySpellBat;
/** Original ModelFireBat boxes/UVs/non-hanging animation, .35 scale and .5-alpha focus colour. */
public final class RenderSpellBat extends EntityRenderer<EntitySpellBat>{
    private static final ResourceLocation TEXTURE=ResourceLocation.parse("thaumcraft:textures/entity/spellbat.png");private final ModelPart head,body,right,left,outerRight,outerLeft;
    public RenderSpellBat(EntityRendererProvider.Context context){super(context);shadowRadius=.25F;var mesh=new MeshDefinition();var root=mesh.getRoot();var h=root.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-3,-3,-3,6,6,6),PartPose.ZERO);h.addOrReplaceChild("ear1",CubeListBuilder.create().texOffs(24,0).addBox(-4,-6,-2,3,4,1),PartPose.ZERO);h.addOrReplaceChild("ear2",CubeListBuilder.create().texOffs(24,0).mirror().addBox(1,-6,-2,3,4,1),PartPose.ZERO);var b=root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,16).addBox(-3,4,-3,6,12,6).texOffs(0,34).addBox(-5,16,0,10,6,1),PartPose.ZERO);var r=b.addOrReplaceChild("right",CubeListBuilder.create().texOffs(42,0).addBox(-12,1,1.5F,10,16,1),PartPose.ZERO);r.addOrReplaceChild("outer",CubeListBuilder.create().texOffs(24,16).addBox(-8,1,0,8,12,1),PartPose.offset(-12,1,1.5F));var l=b.addOrReplaceChild("left",CubeListBuilder.create().texOffs(42,0).mirror().addBox(2,1,1.5F,10,16,1),PartPose.ZERO);l.addOrReplaceChild("outer",CubeListBuilder.create().texOffs(24,16).mirror().addBox(0,1,0,8,12,1),PartPose.offset(12,1,1.5F));var model=LayerDefinition.create(mesh,64,64).bakeRoot();head=model.getChild("head");body=model.getChild("body");right=body.getChild("right");left=body.getChild("left");outerRight=right.getChild("outer");outerLeft=left.getChild("outer");}
    @Override public ResourceLocation getTextureLocation(EntitySpellBat bat){return TEXTURE;}
    @Override public void render(EntitySpellBat bat,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){float age=bat.tickCount+partial;head.xRot=Mth.lerp(partial,bat.xRotO,bat.getXRot())*(float)Math.PI/180;head.yRot=Mth.rotLerp(partial,bat.yHeadRotO,bat.yHeadRot)*(float)Math.PI/180;body.xRot=(float)Math.PI/4+Mth.cos(age*.1F)*.15F;right.yRot=Mth.cos(age*1.3F)*(float)Math.PI*.25F;left.yRot=-right.yRot;outerRight.yRot=right.yRot*.5F;outerLeft.yRot=-right.yRot*.5F;poses.pushPose();poses.translate(0,-.1,0);poses.mulPose(Axis.YP.rotationDegrees(180-yaw));poses.scale(-.35F,-.35F,.35F);poses.translate(0,-1.501,0);var out=buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));int color=0x80000000|bat.color&0xffffff;head.render(poses,out,light,OverlayTexture.NO_OVERLAY,color);body.render(poses,out,light,OverlayTexture.NO_OVERLAY,color);poses.popPose();super.render(bat,yaw,partial,poses,buffers,light);}
}
