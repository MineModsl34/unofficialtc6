package thaumcraft.client.renderers.models.gear;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/** Exact source visibility and hand pose decisions, prepared every frame (never retained
 * across wearer/slot changes). Source CHEST/FEET choose outer; HEAD/LEGS choose inner mesh.
 */
public final class CustomArmorHelper {
    private CustomArmorHelper() {}
    public static boolean inner(EquipmentSlot slot) { return slot != EquipmentSlot.CHEST && slot != EquipmentSlot.FEET; }
    public static void prepare(ModelCultistArmor model, LivingEntity wearer, EquipmentSlot slot, float partialTick) {
        model.head.visible = model.hat.visible = slot == EquipmentSlot.HEAD;
        model.body.visible = slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS;
        model.rightArm.visible = model.leftArm.visible = slot == EquipmentSlot.CHEST;
        model.rightLeg.visible = model.leftLeg.visible = slot == EquipmentSlot.LEGS;
        model.crouching = wearer.isShiftKeyDown(); model.riding = wearer.isPassenger(); model.young = wearer.isBaby(); model.attackTime = wearer.getAttackAnim(partialTick);
        var main = pose(wearer.getMainHandItem(), wearer.isUsingItem(), true); var off = pose(wearer.getOffhandItem(), wearer.isUsingItem(), false);
        model.rightArmPose = wearer.getMainArm() == HumanoidArm.RIGHT ? main : off;
        model.leftArmPose = wearer.getMainArm() == HumanoidArm.RIGHT ? off : main;
    }
    private static HumanoidModel.ArmPose pose(ItemStack stack, boolean using, boolean main) {
        if (stack.isEmpty()) return HumanoidModel.ArmPose.EMPTY;
        if (using && stack.getUseAnimation() == UseAnim.BLOCK) return HumanoidModel.ArmPose.BLOCK;
        if (main && using && stack.getUseAnimation() == UseAnim.BOW) return HumanoidModel.ArmPose.BOW_AND_ARROW;
        return HumanoidModel.ArmPose.ITEM;
    }
}
