package thaumcraft.common.golems.client;

import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.parts.PartModel;
import thaumcraft.common.golems.parts.GolemLegWheels;

/** Client-only adapters for all source physical part callbacks. Descriptor catalog is server-safe. */
public final class GolemPartAnimations {
    public interface Adapter {
        default void before(String group,PartModel part,IGolemAPI golem,float partial,PartModel.EnumLimbSide side,PoseStack poses) {}
        default void after(String group,PartModel part,IGolemAPI golem,float partial,PartModel.EnumLimbSide side,PoseStack poses,MultiBufferSource buffers,ItemInHandRenderer items,int light) {}
        default float[] arm(PartModel part,IGolemAPI golem,float partial,PartModel.EnumLimbSide side,float x,float y,float z) { return new float[]{x,y,z}; }
    }
    private static final Map<ResourceLocation,Adapter> ADDONS = new HashMap<>();
    private static final Map<LivingEntity,float[]> BREAKERS = new WeakHashMap<>();
    private GolemPartAnimations() {}
    public static void register(ResourceLocation sourceObj,Adapter adapter) { ADDONS.put(Objects.requireNonNull(sourceObj),Objects.requireNonNull(adapter)); }
    public static void clear() { BREAKERS.clear(); }
    public static float[] arm(PartModel part,IGolemAPI golem,float partial,PartModel.EnumLimbSide side,float x,float y,float z) {
        var addon = ADDONS.get(part.getObjModel()); if (addon != null) return addon.arm(part,golem,partial,side,x,y,z);
        if (part.animation() == PartModel.Animation.DARTS && golem.isInCombat()) { x = 90 - golem.getGolemEntity().xRotO + x / 10; y /= 10; z /= 10; }
        return new float[]{part.preRenderArmRotationX(golem,partial,side,x),part.preRenderArmRotationY(golem,partial,side,y),part.preRenderArmRotationZ(golem,partial,side,z)};
    }
    public static void before(String group,PartModel part,IGolemAPI golem,float partial,PartModel.EnumLimbSide side,PoseStack poses) {
        part.preRenderObjectPart(group,golem,partial,side);
        var addon = ADDONS.get(part.getObjModel()); if (addon != null) { addon.before(group,part,golem,partial,side,poses); return; }
        var entity = golem.getGolemEntity();
        if (part.animation() == PartModel.Animation.CLAWS && group.startsWith("claw")) {
            float angle = entity.getAttackAnim(partial) * 4.1F; poses.translate(0,-.2,0); poses.mulPose((group.endsWith("1") ? Axis.XP : Axis.XN).rotationDegrees(angle * angle));
        } else if (part.animation() == PartModel.Animation.BREAKERS && group.equals("grinder")) {
            var last = BREAKERS.computeIfAbsent(entity,key -> new float[2]); float speed = Math.max(last[0],entity.getAttackAnim(partial) * 20); last[1] += speed; last[0] = speed * .99F;
            poses.translate(0,-.34,0); poses.mulPose((side == PartModel.EnumLimbSide.LEFT ? Axis.XN : Axis.XP).rotationDegrees((entity.tickCount + partial) / 2 + last[1] + (side == PartModel.EnumLimbSide.LEFT ? 22 : 0)));
        } else if (part.animation() == PartModel.Animation.WHEEL && group.equals("wheel")) { poses.translate(0,-.375,0); poses.mulPose(Axis.XN.rotationDegrees(GolemLegWheels.ani.getOrDefault(entity,0F))); }
    }
    public static void after(String group,PartModel part,IGolemAPI golem,float partial,PartModel.EnumLimbSide side,PoseStack poses,MultiBufferSource buffers,ItemInHandRenderer renderer,int light) {
        part.postRenderObjectPart(group,golem,partial,side);
        var addon = ADDONS.get(part.getObjModel()); if (addon != null) { addon.after(group,part,golem,partial,side,poses,buffers,renderer,light); return; }
        if (part.animation() == PartModel.Animation.HAULER && golem.getCarrying().size() > 1) {
            var stack = golem.getCarrying().get(1); if (stack.isEmpty()) return;
            poses.pushPose(); poses.scale(.375F,.375F,.375F); poses.translate(0,.33,.825); if (!(stack.getItem() instanceof BlockItem)) poses.translate(0,0,-.25);
            renderer.renderItem(golem.getGolemEntity(),stack,ItemDisplayContext.HEAD,false,poses,buffers,light); poses.popPose();
        }
    }
}
