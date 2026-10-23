package thaumcraft.client.renderers.models.gear;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import thaumcraft.common.items.armor.SourceCultistArmorGeometry;
import thaumcraft.common.items.armor.SourceCultistArmorGeometry.Kind;

/** All three original TC6 meshes/UVs/attachments, rendered through native ModelPart vertices.
 * No inflated vanilla body cubes remain. Source head1.01 adult scale and exact child transforms
 * are explicit; source cloak/tabard motion uses the old native float trig lookup.
 */
public class ModelCultistArmor extends ModelCustomArmor {
    private final Kind kind;
    public ModelCultistArmor(ModelPart root, Kind kind) { super(root); this.kind = kind; }
    public static LayerDefinition createLayer(Kind kind, boolean inner) {
        return createLayer(SourceCultistArmorGeometry.load(kind), inner);
    }
    public static LayerDefinition createLayer(SourceCultistArmorGeometry geometry, boolean inner) {
        MeshDefinition mesh = new MeshDefinition(); PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO); root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5, 2, 0)); root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5, 2, 0));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12, 0)); root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12, 0));
        for (var part : geometry.parts()) if (part.included(inner)) {
            String parent = switch (part.parent()) {
                case "bipedHead" -> "head"; case "bipedHeadwear" -> "hat"; case "bipedBody" -> "body";
                case "bipedRightArm" -> "right_arm"; case "bipedLeftArm" -> "left_arm"; case "bipedRightLeg" -> "right_leg"; case "bipedLeftLeg" -> "left_leg";
                default -> throw new IllegalArgumentException("Source parent");
            };
            var o = part.box().origin(); var size = part.box().size(); var pivot = part.offset(); var rotation = part.rotation();
            root.getChild(parent).addOrReplaceChild(part.name(), CubeListBuilder.create().texOffs(part.u(), part.v()).mirror(part.mirror())
                .addBox(o.x(), o.y(), o.z(), size.x(), size.y(), size.z()), PartPose.offsetAndRotation(pivot.x(), pivot.y(), pivot.z(), rotation.x(), rotation.y(), rotation.z()));
        }
        return LayerDefinition.create(mesh, 128, 64);
    }
    @Override public void setupAnim(LivingEntity entity, float swing, float amount, float age, float yaw, float pitch) {
        super.setupAnim(entity, swing, amount, age, yaw, pitch);
        float a = Mth.cos(swing * .6662F) * 1.4F * amount, b = Mth.cos(swing * .6662F + (float) Math.PI) * 1.4F * amount, c = Math.min(a, b);
        if (kind == Kind.ROBE) {
            angle("FrontclothR1", c - .1047198F); angle("FrontclothL1", c - .1047198F);
            angle("FrontclothR2", c - .3316126F); angle("FrontclothL2", c - .3316126F);
            angle("ClothBackR1", -c + .1047198F); angle("ClothBackL1", -c + .1047198F);
            for (String name : new String[]{"ClothBackR2", "ClothBackL2", "ClothBackR3", "ClothBackL3"}) angle(name, -c + .2268928F);
        } else {
            if (kind == Kind.KNIGHT) { angle("Frontcloth1", c - .1047198F); angle("Frontcloth2", c - .3316126F); }
            else { angle("LegClothR", a - .1047198F); angle("LegClothL", b - .1047198F); }
            angle("Cloak1", -c / 2F + .1396263F); angle("Cloak2", -c / 2F + .3069452F); angle("Cloak3", -c / 2F + .4465716F);
        }
    }
    private void angle(String name, float rotation) { if (body.hasChild(name)) body.getChild(name).xRot = rotation; }
    @Override public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay, int color) {
        pose.pushPose();
        if (young) { pose.scale(.75F, .75F, .75F); pose.translate(0, 1, 0); } else pose.scale(1.01F, 1.01F, 1.01F);
        head.render(pose, vertices, light, overlay, color); pose.popPose();
        pose.pushPose(); if (young) { pose.scale(.5F, .5F, .5F); pose.translate(0, 1.5F, 0); }
        body.render(pose, vertices, light, overlay, color); rightArm.render(pose, vertices, light, overlay, color); leftArm.render(pose, vertices, light, overlay, color);
        rightLeg.render(pose, vertices, light, overlay, color); leftLeg.render(pose, vertices, light, overlay, color); hat.render(pose, vertices, light, overlay, color);
        pose.popPose();
    }
}
