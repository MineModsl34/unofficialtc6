package thaumcraft.common.golems.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.parts.PartModel;
import thaumcraft.client.renderers.models.entity.ConstructObjMesh;
import thaumcraft.common.golems.EntityThaumcraftGolem;

/** Full source OBJ group hierarchy, material overrides, colored flag and animated attachment poses. */
public final class GolemObjModel extends EntityModel<EntityThaumcraftGolem> {
    private static final ResourceLocation BASE = ResourceLocation.parse("thaumcraft:models/obj/golem_base.obj");
    private final ItemInHandRenderer items;
    private EntityThaumcraftGolem entity;
    private MultiBufferSource buffers;
    private float partial,limb,amount,age,headYaw,headPitch;
    private int light,overlay,color;
    private boolean ghost;
    public GolemObjModel(ItemInHandRenderer items) { super(RenderType::entityTranslucent); this.items = items; }
    public void context(MultiBufferSource buffers,float partial,int light) { this.buffers = buffers; this.partial = partial; this.light = light; overlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY; }
    @Override public void setupAnim(EntityThaumcraftGolem entity,float limb,float amount,float age,float headYaw,float headPitch) { this.entity = entity; this.limb = limb; this.amount = amount; this.age = age; this.headYaw = headYaw; this.headPitch = headPitch; }
    @Override public void renderToBuffer(PoseStack poses,VertexConsumer ignored,int light,int overlay,int color) {
        this.light = light; this.overlay = overlay; this.color = color;
        // LivingEntityRenderer's vanilla cube coordinate frame is removed; OBJ source units are metres, y-up.
        poses.pushPose(); poses.translate(0,1.501,0); poses.scale(-1,-1,1); renderSource(poses); poses.popPose();
    }
    private void renderSource(PoseStack poses) {
        var props = entity.getProperties(); var texture = props.getMaterial().texture;
        boolean holding = !entity.getMainHandItem().isEmpty(),floating = props.hasTrait(EnumGolemTrait.WHEELED) || props.hasTrait(EnumGolemTrait.FLYER);
        double dx = entity.getX() - entity.xo,dz = entity.getZ() - entity.zo,speed = dx * dx + dz * dz;
        float rx = (float)Math.toDegrees(Mth.sin(age * .067F) * .03F),rz = (float)Math.toDegrees(Mth.cos(age * .09F) * .05F + .05F);
        float rightX,leftX,rightY = 0,leftY = 0,rightZ,leftZ,bodyY = 0;
        if (holding) { rightX = leftX = 90 - rz / 2; rightZ = -2; leftZ = 2; }
        else { rightX = floating ? rx * 2 : (float)Math.toDegrees(Mth.cos(limb * .6662F + (float)Math.PI) * amount) + rx;
            leftX = floating ? -rx * 2 : (float)Math.toDegrees(Mth.cos(limb * .6662F) * amount) - rx; rightZ = rz + 2; leftZ = -(rz + 2); }
        if (attackTime > 0) { float wiggle = -Mth.sin(Mth.sqrt(attackTime) * (float)Math.PI * 2) * .2F; bodyY = (float)Math.toDegrees(wiggle); rightZ = -(float)Math.toDegrees(Mth.sin(wiggle) * 3); rightX = (float)Math.toDegrees(-Mth.cos(wiggle) * 5); rightY += bodyY; }
        poses.mulPose(Axis.YP.rotationDegrees(bodyY)); float lean = floating ? 75 : 25;
        poses.mulPose(Axis.XN.rotationDegrees((float)(speed * lean))); poses.mulPose(Axis.ZN.rotationDegrees((float)(speed * lean * .06 * (entity.getYRot() - entity.yRotO))));
        poses.pushPose(); poses.translate(0,.5,0); group(BASE,"chest",texture,poses,color); group(BASE,"waist",texture,poses,color);
        if (entity.getGolemColor() > 0) group(BASE,"flag",texture,poses,(color & 0xff000000) | DyeColor.byId(entity.getGolemColor() - 1).getTextureDiffuseColor());
        attachments(PartModel.EnumAttachPoint.BODY,PartModel.EnumLimbSide.MIDDLE,texture,poses); poses.popPose();
        poses.pushPose(); poses.translate(0,.75,-.03125); poses.mulPose(Axis.YN.rotationDegrees(headYaw)); poses.mulPose(Axis.XN.rotationDegrees(headPitch));
        attachments(PartModel.EnumAttachPoint.HEAD,PartModel.EnumLimbSide.MIDDLE,texture,poses); group(BASE,"head",texture,poses,color); poses.popPose();
        arm(true,rightX,rightY,rightZ,texture,poses); arm(false,leftX,leftY,leftZ,texture,poses);
        for (boolean right : new boolean[]{true,false}) { poses.pushPose(); poses.translate(right ? .09375 : -.09375,.375,0); poses.mulPose(Axis.XP.rotationDegrees((float)Math.toDegrees(Mth.cos(limb * .6662F + (right ? 0 : (float)Math.PI)) * amount)));
            attachments(PartModel.EnumAttachPoint.LEGS,right ? PartModel.EnumLimbSide.RIGHT : PartModel.EnumLimbSide.LEFT,texture,poses); poses.popPose(); }
        poses.pushPose(); poses.translate(0,.625,0); poses.mulPose(Axis.XP.rotationDegrees(90 - rz * .5F));
        var stack = entity.getMainHandItem(); if (!ghost && !stack.isEmpty()) { poses.pushPose(); poses.mulPose(Axis.XN.rotationDegrees(90)); poses.scale(.375F,.375F,.375F); poses.translate(0,.25,-1.5);
            if (!(stack.getItem() instanceof BlockItem)) poses.translate(0,-.6,0); items.renderItem(entity,stack,ItemDisplayContext.HEAD,false,poses,buffers,light); poses.popPose(); }
        poses.popPose(); entity.redrawParts = false;
    }
    private void arm(boolean right,float x,float y,float z,ResourceLocation texture,PoseStack poses) {
        var side = right ? PartModel.EnumLimbSide.RIGHT : PartModel.EnumLimbSide.LEFT; var part = entity.getProperties().getArms().model;
        if (part != null) { var rotations = GolemPartAnimations.arm(part,entity,partial,side,x,y,z); x = rotations[0]; y = rotations[1]; z = rotations[2]; }
        poses.pushPose(); poses.translate(right ? .20625 : -.20625,.6875,0); poses.mulPose(Axis.XP.rotationDegrees(x)); poses.mulPose(Axis.YP.rotationDegrees(y + (right ? 0 : 180))); poses.mulPose((right ? Axis.ZP : Axis.ZN).rotationDegrees(z));
        group(BASE,"arm",texture,poses,color); attachments(PartModel.EnumAttachPoint.ARMS,side,texture,poses); poses.popPose();
    }
    private void attachments(PartModel.EnumAttachPoint point,PartModel.EnumLimbSide side,ResourceLocation material,PoseStack poses) {
        var p = entity.getProperties(); PartModel[] parts = {p.getHead().model,p.getArms().model,p.getLegs().model,p.getAddon().model};
        for (var part : parts) if (part != null && part.getAttachPoint() == point) for (String name : GolemObjMeshes.get(part.getObjModel()).groups()) {
            poses.pushPose(); GolemPartAnimations.before(name,part,entity,partial,side,poses);
            var texture = part.useMaterialTextureForObjectPart(name) || part.getTexture() == null ? material : part.getTexture();
            group(part.getObjModel(),name,texture,poses,color); if (!ghost) GolemPartAnimations.after(name,part,entity,partial,side,poses,buffers,items,light); poses.popPose();
        }
    }
    public void renderGhost(PoseStack poses) { int previous = color; ghost = true; color = 0x40404040; try { renderSource(poses); } finally { ghost = false; color = previous; } }
    private void group(ResourceLocation model,String group,ResourceLocation texture,PoseStack poses,int color) { draw(GolemObjMeshes.get(model),group,poses,buffers.getBuffer(ghost ? GolemRenderTypes.ghost(texture) : RenderType.entityTranslucent(texture)),light,overlay,color,false); }
    public static void draw(ConstructObjMesh mesh,String group,PoseStack poses,VertexConsumer out,int light,int overlay,int color,boolean invertV) {
        for (var face : mesh.faces(group)) { for (var vertex : face.vertices()) vertex(out,poses.last(),vertex,face,light,overlay,color,invertV); if (face.vertices().size() == 3) vertex(out,poses.last(),face.vertices().get(2),face,light,overlay,color,invertV); }
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,ConstructObjMesh.Vertex vertex,ConstructObjMesh.Face face,int light,int overlay,int color,boolean invertV) {
        out.addVertex(pose,vertex.x(),vertex.y(),vertex.z()).setColor(color).setUv(vertex.u(),invertV ? 1 - vertex.v() : vertex.v()).setOverlay(overlay).setLight(light).setNormal(pose,face.nx(),face.ny(),face.nz());
    }
}
