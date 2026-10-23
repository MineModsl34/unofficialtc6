package thaumcraft.client.renderers.models.gear;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;

/** TC6 ModelCustomArmor pose formulas translated to native ModelPart fields. The normal
 * wearer/cultist path keeps source attack, riding, crouch, elytra, item/block/bow and idle motion.
 * Source skeleton/zombie special arms and armor-stand authored poses are retained; their base
 * skeleton/zombie locomotion is the platform HumanoidModel bridge, not an obsolete entity API.
 */
public class ModelCustomArmor extends HumanoidModel<LivingEntity> {
    public ModelCustomArmor(ModelPart root) { super(root); }
    @Override public void setupAnim(LivingEntity entity, float swing, float amount, float age, float yaw, float pitch) {
        if (entity instanceof ArmorStand stand) { stand(stand); return; }
        if (entity instanceof Zombie || entity instanceof AbstractSkeleton) {
            super.setupAnim(entity, swing, amount, age, yaw, pitch);
            float a = Mth.sin(attackTime * (float) Math.PI);
            float b = Mth.sin((1F - (1F - attackTime) * (1F - attackTime)) * (float) Math.PI);
            rightArm.zRot = leftArm.zRot = 0;
            rightArm.yRot = -(0.1F - a * .6F); leftArm.yRot = .1F - a * .6F;
            float f = (float) -Math.PI / (entity instanceof Zombie zombie && zombie.isAggressive() ? 1.5F : 2.25F);
            rightArm.xRot = leftArm.xRot = f + a * 1.2F - b * .4F; idle(age); return;
        }
        boolean flying = entity.getFallFlyingTicks() > 4;
        head.yRot = yaw * (float) (Math.PI / 180.0); head.xRot = flying ? (float) (-Math.PI / 4) : pitch * (float) (Math.PI / 180.0);
        head.zRot = 0; body.yRot = 0; body.zRot = 0;
        rightArm.z = leftArm.z = 0; rightArm.x = -5; leftArm.x = 5;
        float movement = 1F;
        if (flying) { movement = (float) entity.getDeltaMovement().lengthSqr(); movement /= .2F; movement = movement * movement * movement; }
        if (movement < 1F) movement = 1F;
        rightArm.xRot = Mth.cos(swing * .6662F + (float) Math.PI) * 2F * amount * .5F / movement;
        leftArm.xRot = Mth.cos(swing * .6662F) * 2F * amount * .5F / movement;
        rightArm.zRot = leftArm.zRot = 0;
        rightLeg.xRot = Mth.cos(swing * .6662F) * 1.4F * amount / movement;
        leftLeg.xRot = Mth.cos(swing * .6662F + (float) Math.PI) * 1.4F * amount / movement;
        rightLeg.yRot = leftLeg.yRot = rightLeg.zRot = leftLeg.zRot = 0;
        if (riding) {
            rightArm.xRot += (float) (-Math.PI / 5); leftArm.xRot += (float) (-Math.PI / 5);
            rightLeg.xRot = leftLeg.xRot = -1.4137167F; rightLeg.yRot = (float) (Math.PI / 10); leftLeg.yRot = (float) (-Math.PI / 10);
            rightLeg.zRot = .07853982F; leftLeg.zRot = -.07853982F;
        }
        rightArm.yRot = rightArm.zRot = 0;
        poseArm(leftArm, leftArmPose, true); poseArm(rightArm, rightArmPose, false);
        if (attackTime > 0) {
            HumanoidArm side = entity.swingingArm == InteractionHand.OFF_HAND ? entity.getMainArm().getOpposite() : entity.getMainArm();
            ModelPart arm = side == HumanoidArm.LEFT ? leftArm : rightArm;
            body.yRot = Mth.sin(Mth.sqrt(attackTime) * (float) (Math.PI * 2)) * .2F; if (side == HumanoidArm.LEFT) body.yRot *= -1F;
            rightArm.z = Mth.sin(body.yRot) * 5F; rightArm.x = -Mth.cos(body.yRot) * 5F;
            leftArm.z = -Mth.sin(body.yRot) * 5F; leftArm.x = Mth.cos(body.yRot) * 5F;
            rightArm.yRot += body.yRot; leftArm.yRot += body.yRot; leftArm.xRot += body.yRot;
            float progress = 1F - attackTime; progress *= progress; progress *= progress; progress = 1F - progress;
            float a = Mth.sin(progress * (float) Math.PI), b = Mth.sin(attackTime * (float) Math.PI) * -(head.xRot - .7F) * .75F;
            arm.xRot = (float) (arm.xRot - (a * 1.2 + b)); arm.yRot += body.yRot * 2F; arm.zRot += Mth.sin(attackTime * (float) Math.PI) * -.4F;
        }
        if (crouching) {
            body.xRot = .5F; rightArm.xRot += .4F; leftArm.xRot += .4F;
            rightLeg.z = leftLeg.z = 4; rightLeg.y = leftLeg.y = 13; head.y = body.y = 4.5F; rightArm.y = leftArm.y = 5;
        } else {
            body.xRot = 0; rightLeg.z = leftLeg.z = .1F; rightLeg.y = leftLeg.y = 12; head.y = body.y = 0; rightArm.y = leftArm.y = 2;
        }
        idle(age);
        if (rightArmPose == ArmPose.BOW_AND_ARROW) {
            rightArm.yRot = -.1F + head.yRot; leftArm.yRot = .1F + head.yRot + .4F;
            rightArm.xRot = leftArm.xRot = (float) (-Math.PI / 2) + head.xRot;
        } else if (leftArmPose == ArmPose.BOW_AND_ARROW) {
            rightArm.yRot = -.1F + head.yRot - .4F; leftArm.yRot = .1F + head.yRot;
            rightArm.xRot = leftArm.xRot = (float) (-Math.PI / 2) + head.xRot;
        }
        hat.copyFrom(head);
    }
    private static void poseArm(ModelPart arm, ArmPose pose, boolean left) {
        switch (pose) {
            case EMPTY -> arm.yRot = 0;
            case BLOCK -> { arm.xRot = arm.xRot * .5F - .9424779F; arm.yRot = (float) (Math.PI / 6) * (left ? 1 : -1); }
            case ITEM -> { arm.xRot = arm.xRot * .5F - (float) (Math.PI / 10); arm.yRot = 0; }
            default -> {} // Only source EMPTY/ITEM/BLOCK/BOW are assigned by our finite helper.
        }
    }
    private void idle(float age) {
        rightArm.zRot += Mth.cos(age * .09F) * .05F + .05F; leftArm.zRot -= Mth.cos(age * .09F) * .05F + .05F;
        rightArm.xRot += Mth.sin(age * .067F) * .05F; leftArm.xRot -= Mth.sin(age * .067F) * .05F;
    }
    private static void rotation(ModelPart part, net.minecraft.core.Rotations pose) {
        float degrees = (float) (Math.PI / 180.0); part.xRot = degrees * pose.getX(); part.yRot = degrees * pose.getY(); part.zRot = degrees * pose.getZ();
    }
    private void stand(ArmorStand entity) {
        rotation(head, entity.getHeadPose()); head.setPos(0, 1, 0); rotation(body, entity.getBodyPose());
        rotation(leftArm, entity.getLeftArmPose()); rotation(rightArm, entity.getRightArmPose());
        rotation(leftLeg, entity.getLeftLegPose()); leftLeg.setPos(1.9F, 11, 0);
        rotation(rightLeg, entity.getRightLegPose()); rightLeg.setPos(-1.9F, 11, 0); hat.copyFrom(head);
    }
}
