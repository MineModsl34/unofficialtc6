package thaumcraft.client.renderers.models.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import thaumcraft.common.entities.monster.EntityEldritchGuardian;

/** Original TC6 guardian cube geometry, UVs, hierarchy and cloth animation; no vanilla substitute. */
public final class ModelEldritchGuardian extends EntityModel<EntityEldritchGuardian> {
    private final ModelPart root;
    private final ModelPart Hood1, HoodEye, ArmL1, ArmR1, LegpanelC1, LegpanelC2, LegpanelC3, Cloak1, Cloak2, Cloak3, SidepanelL2, SidepanelL3, SidepanelL4, SidepanelR2, SidepanelR3, SidepanelR4;
    private float partialTicks, opacity = 1.0F;
    public ModelEldritchGuardian(ModelPart root) {
        this.root = root;
        this.Hood1 = root.getChild("Hood1");
        this.HoodEye = new ModelPart(java.util.List.of(), java.util.Map.of());
        this.ArmL1 = root.getChild("ArmL1");
        this.ArmR1 = root.getChild("ArmR1");
        this.LegpanelC1 = root.getChild("LegpanelC1");
        this.LegpanelC2 = root.getChild("LegpanelC1").getChild("LegpanelC2");
        this.LegpanelC3 = root.getChild("LegpanelC1").getChild("LegpanelC2").getChild("LegpanelC3");
        this.Cloak1 = root.getChild("Cloak1");
        this.Cloak2 = root.getChild("Cloak1").getChild("Cloak2");
        this.Cloak3 = root.getChild("Cloak1").getChild("Cloak2").getChild("Cloak3");
        this.SidepanelL2 = root.getChild("SidepanelL2");
        this.SidepanelL3 = root.getChild("SidepanelL2").getChild("SidepanelL3");
        this.SidepanelL4 = root.getChild("SidepanelL2").getChild("SidepanelL3").getChild("SidepanelL4");
        this.SidepanelR2 = root.getChild("SidepanelR2");
        this.SidepanelR3 = root.getChild("SidepanelR2").getChild("SidepanelR3");
        this.SidepanelR4 = root.getChild("SidepanelR2").getChild("SidepanelR3").getChild("SidepanelR4");
    }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition BeltR = root.addOrReplaceChild("BeltR", CubeListBuilder.create().texOffs(76, 44).mirror(false).addBox(-5.0F, 4.0F, -3.0F, 1, 3, 6),
            PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Mbelt = root.addOrReplaceChild("Mbelt", CubeListBuilder.create().texOffs(56, 55).mirror(false).addBox(-4.0F, 8.0F, -3.0F, 8, 4, 1),
            PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition MbeltL = root.addOrReplaceChild("MbeltL", CubeListBuilder.create().texOffs(76, 44).mirror(false).addBox(4.0F, 8.0F, -3.0F, 1, 3, 6),
            PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition MbeltR = root.addOrReplaceChild("MbeltR", CubeListBuilder.create().texOffs(76, 44).mirror(false).addBox(-5.0F, 8.0F, -3.0F, 1, 3, 6),
            PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition BeltL = root.addOrReplaceChild("BeltL", CubeListBuilder.create().texOffs(76, 44).mirror(false).addBox(4.0F, 4.0F, -3.0F, 1, 3, 6),
            PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Chestplate = root.addOrReplaceChild("Chestplate", CubeListBuilder.create().texOffs(56, 45).mirror(false).addBox(-4.0F, 1.0F, -4.0F, 8, 7, 2),
            PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Hood1 = root.addOrReplaceChild("Hood1", CubeListBuilder.create().texOffs(40, 12).mirror(false).addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8),
            PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Hood2 = Hood1.addOrReplaceChild("Hood2", CubeListBuilder.create().texOffs(36, 28).mirror(false).addBox(-3.5F, -8.7F, 2.0F, 7, 7, 3),
            PartPose.offsetAndRotation(0, 0, 0, -0.2268928F, 0.0F, 0.0F));
        PartDefinition Hood3 = Hood1.addOrReplaceChild("Hood3", CubeListBuilder.create().texOffs(22, 19).mirror(false).addBox(-3.0F, -9.0F, 2.5F, 6, 6, 3),
            PartPose.offsetAndRotation(0, 0, 0, -0.3490659F, 0.0F, 0.0F));
        PartDefinition Hood4 = Hood1.addOrReplaceChild("Hood4", CubeListBuilder.create().texOffs(40, 4).mirror(false).addBox(-2.5F, -9.7F, 3.5F, 5, 5, 3),
            PartPose.offsetAndRotation(0, 0, 0, -0.5759587F, 0.0F, 0.0F));
        PartDefinition Backplate = root.addOrReplaceChild("Backplate", CubeListBuilder.create().texOffs(36, 45).mirror(false).addBox(-4.0F, 1.0F, 2.0F, 8, 11, 2),
            PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition ShoulderplateTopR = root.addOrReplaceChild("ShoulderplateTopR", CubeListBuilder.create().texOffs(110, 37).mirror(false).addBox(-5.5F, -2.5F, -3.5F, 2, 1, 7),
            PartPose.offsetAndRotation(-5.0F, -4.0F, 0.0F, -0.3665191F, 0.3141593F, 0.4363323F));
        PartDefinition ShoulderplateR1 = root.addOrReplaceChild("ShoulderplateR1", CubeListBuilder.create().texOffs(110, 45).mirror(false).addBox(3.5F, -1.5F, -3.5F, 1, 4, 7),
            PartPose.offsetAndRotation(5.0F, -4.0F, 0.0F, -0.3665191F, -0.3141593F, -0.4363323F));
        PartDefinition ShoulderplateR2 = root.addOrReplaceChild("ShoulderplateR2", CubeListBuilder.create().texOffs(94, 45).mirror(false).addBox(-3.5F, 1.5F, -3.5F, 1, 3, 7),
            PartPose.offsetAndRotation(-5.0F, -4.0F, 0.0F, -0.3665191F, 0.3141593F, 0.4363323F));
        PartDefinition ShoulderplateR3 = root.addOrReplaceChild("ShoulderplateR3", CubeListBuilder.create().texOffs(94, 45).mirror(false).addBox(-2.5F, 3.5F, -3.5F, 1, 3, 7),
            PartPose.offsetAndRotation(-5.0F, -4.0F, 0.0F, -0.3665191F, 0.3141593F, 0.4363323F));
        PartDefinition ShoulderR = root.addOrReplaceChild("ShoulderR", CubeListBuilder.create().texOffs(56, 35).mirror(false).addBox(-3.5F, -2.5F, -2.5F, 5, 5, 5),
            PartPose.offsetAndRotation(-5.0F, -4.0F, 0.0F, -0.3665191F, 0.122173F, 0.0349066F));
        PartDefinition ArmL1 = root.addOrReplaceChild("ArmL1", CubeListBuilder.create().texOffs(72, 8).mirror(false).addBox(-1.0F, 2.5F, -1.5F, 4, 10, 5),
            PartPose.offsetAndRotation(5.0F, -4.0F, 0.0F, -0.9599311F, -0.1047198F, -0.1919862F));
        PartDefinition ArmL2 = ArmL1.addOrReplaceChild("ArmL2", CubeListBuilder.create().texOffs(76, 28).mirror(false).addBox(-1.0F, 9.5F, 3.5F, 4, 3, 3),
            PartPose.offsetAndRotation(0, 0, 0, 0, 0, 0));
        PartDefinition ArmL3 = ArmL1.addOrReplaceChild("ArmL3", CubeListBuilder.create().texOffs(76, 23).mirror(false).addBox(-1.0F, 6.5F, 3.5F, 4, 3, 2),
            PartPose.offsetAndRotation(0, 0, 0, 0, 0, 0));
        PartDefinition ArmR1 = root.addOrReplaceChild("ArmR1", CubeListBuilder.create().texOffs(72, 8).mirror(false).addBox(-3.0F, 2.5F, -1.5F, 4, 10, 5),
            PartPose.offsetAndRotation(-5.0F, -4.0F, 0.0F, -0.9599311F, 0.1047198F, 0.1919862F));
        PartDefinition ArmR2 = ArmR1.addOrReplaceChild("ArmR2", CubeListBuilder.create().texOffs(76, 28).mirror(false).addBox(-3.0F, 9.5F, 3.5F, 4, 3, 3),
            PartPose.offsetAndRotation(0, 0, 0, 0, 0, 0));
        PartDefinition ArmR3 = ArmR1.addOrReplaceChild("ArmR3", CubeListBuilder.create().texOffs(76, 23).mirror(false).addBox(-3.0F, 6.5F, 3.5F, 4, 3, 2),
            PartPose.offsetAndRotation(0, 0, 0, 0, 0, 0));
        PartDefinition ShoulderL = root.addOrReplaceChild("ShoulderL", CubeListBuilder.create().texOffs(56, 35).mirror(true).addBox(-1.5F, -2.5F, -2.5F, 5, 5, 5),
            PartPose.offsetAndRotation(5.0F, -4.0F, 0.0F, -0.3665191F, -0.122173F, -0.0349066F));
        PartDefinition ShoulderplateLtop = root.addOrReplaceChild("ShoulderplateLtop", CubeListBuilder.create().texOffs(110, 37).mirror(false).addBox(3.5F, -2.5F, -3.5F, 2, 1, 7),
            PartPose.offsetAndRotation(5.0F, -4.0F, 0.0F, -0.3665191F, -0.3141593F, -0.4363323F));
        PartDefinition ShoulderplateL1 = root.addOrReplaceChild("ShoulderplateL1", CubeListBuilder.create().texOffs(110, 45).mirror(false).addBox(-4.5F, -1.5F, -3.5F, 1, 4, 7),
            PartPose.offsetAndRotation(-5.0F, -4.0F, 0.0F, -0.3665191F, 0.3141593F, 0.4363323F));
        PartDefinition ShoulderplateL2 = root.addOrReplaceChild("ShoulderplateL2", CubeListBuilder.create().texOffs(94, 45).mirror(false).addBox(2.5F, 1.5F, -3.5F, 1, 3, 7),
            PartPose.offsetAndRotation(5.0F, -4.0F, 0.0F, -0.3665191F, -0.3141593F, -0.4363323F));
        PartDefinition ShoulderplateL3 = root.addOrReplaceChild("ShoulderplateL3", CubeListBuilder.create().texOffs(94, 45).mirror(false).addBox(1.5F, 3.5F, -3.5F, 1, 3, 7),
            PartPose.offsetAndRotation(5.0F, -4.0F, 0.0F, -0.3665191F, -0.3141593F, -0.4363323F));
        PartDefinition LegpanelR4 = root.addOrReplaceChild("LegpanelR4", CubeListBuilder.create().texOffs(0, 43).mirror(false).addBox(-3.0F, 0.5F, -3.5F, 2, 3, 1),
            PartPose.offsetAndRotation(-2.0F, 6.0F, 0.0F, -0.4363323F, 0.0F, 0.0F));
        PartDefinition LegpanelR5 = root.addOrReplaceChild("LegpanelR5", CubeListBuilder.create().texOffs(0, 47).mirror(false).addBox(-3.0F, 2.5F, -2.5F, 2, 3, 1),
            PartPose.offsetAndRotation(-2.0F, 6.0F, 0.0F, -0.4363323F, 0.0F, 0.0F));
        PartDefinition LegpanelR6 = root.addOrReplaceChild("LegpanelR6", CubeListBuilder.create().texOffs(6, 43).mirror(false).addBox(-3.0F, 4.5F, -1.5F, 2, 3, 1),
            PartPose.offsetAndRotation(-2.0F, 6.0F, 0.0F, -0.4363323F, 0.0F, 0.0F));
        PartDefinition BackpanelR1 = root.addOrReplaceChild("BackpanelR1", CubeListBuilder.create().texOffs(0, 18).mirror(false).addBox(-3.0F, 0.5F, 2.5F, 5, 3, 1),
            PartPose.offsetAndRotation(-2.0F, 6.0F, 0.0F, 0.4363323F, 0.0F, 0.0F));
        PartDefinition BackpanelR2 = root.addOrReplaceChild("BackpanelR2", CubeListBuilder.create().texOffs(0, 18).mirror(false).addBox(-3.0F, 2.5F, 1.5F, 5, 3, 1),
            PartPose.offsetAndRotation(-2.0F, 6.0F, 0.0F, 0.4363323F, 0.0F, 0.0F));
        PartDefinition BackpanelR3 = root.addOrReplaceChild("BackpanelR3", CubeListBuilder.create().texOffs(0, 18).mirror(false).addBox(-3.0F, 4.5F, 0.5F, 5, 3, 1),
            PartPose.offsetAndRotation(-2.0F, 6.0F, 0.0F, 0.4363323F, 0.0F, 0.0F));
        PartDefinition BackpanelL3 = root.addOrReplaceChild("BackpanelL3", CubeListBuilder.create().texOffs(0, 18).mirror(false).addBox(-2.0F, 4.5F, 0.5F, 5, 3, 1),
            PartPose.offsetAndRotation(2.0F, 6.0F, 0.0F, 0.4363323F, 0.0F, 0.0F));
        PartDefinition LegpanelL4 = root.addOrReplaceChild("LegpanelL4", CubeListBuilder.create().texOffs(0, 43).mirror(false).addBox(1.0F, 0.5F, -3.5F, 2, 3, 1),
            PartPose.offsetAndRotation(2.0F, 6.0F, 0.0F, -0.4363323F, 0.0F, 0.0F));
        PartDefinition LegpanelL5 = root.addOrReplaceChild("LegpanelL5", CubeListBuilder.create().texOffs(0, 47).mirror(false).addBox(1.0F, 2.5F, -2.5F, 2, 3, 1),
            PartPose.offsetAndRotation(2.0F, 6.0F, 0.0F, -0.4363323F, 0.0F, 0.0F));
        PartDefinition LegpanelL6 = root.addOrReplaceChild("LegpanelL6", CubeListBuilder.create().texOffs(6, 43).mirror(false).addBox(1.0F, 4.5F, -1.5F, 2, 3, 1),
            PartPose.offsetAndRotation(2.0F, 6.0F, 0.0F, -0.4363323F, 0.0F, 0.0F));
        PartDefinition BackpanelL1 = root.addOrReplaceChild("BackpanelL1", CubeListBuilder.create().texOffs(0, 18).mirror(false).addBox(-2.0F, 0.5F, 2.5F, 5, 3, 1),
            PartPose.offsetAndRotation(2.0F, 6.0F, 0.0F, 0.4363323F, 0.0F, 0.0F));
        PartDefinition BackpanelL2 = root.addOrReplaceChild("BackpanelL2", CubeListBuilder.create().texOffs(0, 18).mirror(false).addBox(-2.0F, 2.5F, 1.5F, 5, 3, 1),
            PartPose.offsetAndRotation(2.0F, 6.0F, 0.0F, 0.4363323F, 0.0F, 0.0F));
        PartDefinition Cloak1 = root.addOrReplaceChild("Cloak1", CubeListBuilder.create().texOffs(106, 0).mirror(false).addBox(0.0F, 0.0F, -0.5F, 10, 18, 1),
            PartPose.offsetAndRotation(-5.0F, -6.0F, 4.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Cloak2 = Cloak1.addOrReplaceChild("Cloak2", CubeListBuilder.create().texOffs(106, 19).mirror(false).addBox(0.0F, 0.0F, -0.5F, 10, 4, 1),
            PartPose.offsetAndRotation(0.0F, 18.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Cloak3 = Cloak2.addOrReplaceChild("Cloak3", CubeListBuilder.create().texOffs(106, 24).mirror(false).addBox(0.0F, 0.0F, -0.5F, 10, 4, 1),
            PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition SidepanelR1 = root.addOrReplaceChild("SidepanelR1", CubeListBuilder.create().texOffs(0, 22).mirror(false).addBox(-2.5F, 0.5F, -2.5F, 1, 4, 5),
            PartPose.offsetAndRotation(-2.0F, 6.0F, 0.0F, 0.0F, 0.0F, 0.4363323F));
        PartDefinition SidepanelL1 = root.addOrReplaceChild("SidepanelL1", CubeListBuilder.create().texOffs(0, 22).mirror(false).addBox(1.5F, 0.5F, -2.5F, 1, 4, 5),
            PartPose.offsetAndRotation(2.0F, 6.0F, 0.0F, 0.0F, 0.0F, -0.4363323F));
        PartDefinition SidepanelL2 = root.addOrReplaceChild("SidepanelL2", CubeListBuilder.create().texOffs(0, 54).mirror(false).addBox(0.0F, 0.0F, -0.5F, 1, 5, 5),
            PartPose.offsetAndRotation(4.5F, 9.5F, -2.0F, 0.0F, 0.0F, -0.122173F));
        PartDefinition SidepanelL3 = SidepanelL2.addOrReplaceChild("SidepanelL3", CubeListBuilder.create().texOffs(0, 35).mirror(false).addBox(0.0F, 0.0F, -0.5F, 1, 3, 5),
            PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, -0.296706F));
        PartDefinition SidepanelL4 = SidepanelL3.addOrReplaceChild("SidepanelL4", CubeListBuilder.create().texOffs(24, 35).mirror(false).addBox(0.0F, 0.0F, -0.5F, 1, 3, 5),
            PartPose.offsetAndRotation(0.0F, 3.0F, 0.0F, 0.0F, 0.0F, (float) (-Math.PI / 6)));
        PartDefinition SidepanelR2 = root.addOrReplaceChild("SidepanelR2", CubeListBuilder.create().texOffs(0, 54).mirror(false).addBox(0.0F, 0.0F, -0.5F, 1, 5, 5),
            PartPose.offsetAndRotation(-4.5F, 9.5F, -2.0F, 0.0F, 0.0F, 0.122173F));
        PartDefinition SidepanelR3 = SidepanelR2.addOrReplaceChild("SidepanelR3", CubeListBuilder.create().texOffs(0, 35).mirror(false).addBox(0.0F, 0.0F, -0.5F, 1, 3, 5),
            PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, 0.296706F));
        PartDefinition SidepanelR4 = SidepanelR3.addOrReplaceChild("SidepanelR4", CubeListBuilder.create().texOffs(24, 35).mirror(false).addBox(0.0F, 0.0F, -0.5F, 1, 3, 5),
            PartPose.offsetAndRotation(0.0F, 3.0F, 0.0F, 0.0F, 0.0F, (float) (Math.PI / 6)));
        PartDefinition LegpanelC1 = root.addOrReplaceChild("LegpanelC1", CubeListBuilder.create().texOffs(16, 45).mirror(false).addBox(-3.0F, 0.0F, -0.5F, 6, 8, 1),
            PartPose.offsetAndRotation(0.0F, 5.5F, -3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LegpanelC2 = LegpanelC1.addOrReplaceChild("LegpanelC2", CubeListBuilder.create().texOffs(16, 54).mirror(false).addBox(-3.0F, 0.0F, -0.5F, 6, 4, 1),
            PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LegpanelC3 = LegpanelC2.addOrReplaceChild("LegpanelC3", CubeListBuilder.create().texOffs(32, 59).mirror(false).addBox(-3.0F, 0.0F, -0.5F, 6, 4, 1),
            PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    @Override public void prepareMobModel(EntityEldritchGuardian entity, float limb, float amount, float partial) {
        partialTicks = partial;
        if (entity.inOuterLands()) opacity = 1.0F;
        else {
            var viewer = Minecraft.getInstance().getCameraEntity();
            double distance = viewer == null ? 0.0 : entity.distanceToSqr(viewer);
            float far = entity.level().getDifficulty() == Difficulty.HARD ? 576.0F : 1024.0F;
            opacity = distance < 256.0 ? 0.6F : (float) (1.0 - Math.min(far - 256.0F, distance - 256.0) / (far - 256.0F)) * 0.6F;
        }
    }
    @Override public void renderToBuffer(PoseStack poses, VertexConsumer vertices, int light, int overlay, int color) {
        root.render(poses, vertices, light, overlay, FastColor.ARGB32.color((int) (255 * opacity), color));
    }
    @Override public void setupAnim(EntityEldritchGuardian entity, float limb, float amount, float age, float yaw, float pitch) {
        Hood1.yRot = yaw / (180.0F / (float) Math.PI);
        Hood1.xRot = pitch / (180.0F / (float) Math.PI);
        HoodEye.xRot = Hood1.xRot;
        HoodEye.yRot = Hood1.yRot;
        float all = entity.armLiftL, alr = entity.armLiftR;
        ArmL1.xRot = -1.0F - all + Mth.sin((entity.tickCount + 20 + partialTicks) / 10.0F) * 0.08F;
        ArmR1.xRot = -1.0F - alr + Mth.sin((entity.tickCount + partialTicks) / 10.0F) * 0.08F;
        LegpanelC1.xRot = -0.15F + Mth.sin((entity.tickCount + partialTicks) / 8.0F) * 0.12F;
        LegpanelC2.xRot = Mth.sin((entity.tickCount + partialTicks - 5.0F) / 8.0F) * 0.13F;
        LegpanelC3.xRot = Mth.sin((entity.tickCount + partialTicks - 10.0F) / 8.0F) * 0.14F;
        Cloak1.xRot = 0.2F + Mth.sin((entity.tickCount + partialTicks) / 7.0F) * 0.08F;
        Cloak2.xRot = Mth.sin((entity.tickCount + partialTicks - 5.0F) / 7.0F) * 0.1F;
        Cloak3.xRot = Mth.sin((entity.tickCount + partialTicks - 10.0F) / 7.0F) * 0.12F;
        SidepanelL2.zRot = -0.2F + Mth.sin((entity.tickCount + 10 + partialTicks) / 8.0F) * 0.12F;
        SidepanelL3.zRot = Mth.sin((entity.tickCount + partialTicks + 5.0F) / 8.0F) * 0.13F;
        SidepanelL4.zRot = Mth.sin((entity.tickCount + partialTicks) / 8.0F) * 0.14F;
        SidepanelR2.zRot = 0.2F + Mth.sin((entity.tickCount - 5 + partialTicks) / 8.0F) * 0.12F;
        SidepanelR3.zRot = Mth.sin((entity.tickCount + partialTicks - 10.0F) / 8.0F) * 0.13F;
        SidepanelR4.zRot = Mth.sin((entity.tickCount + partialTicks - 15.0F) / 8.0F) * 0.14F;
    }

}
