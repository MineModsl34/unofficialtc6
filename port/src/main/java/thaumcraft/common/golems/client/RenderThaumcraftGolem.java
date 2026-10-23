package thaumcraft.common.golems.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import thaumcraft.api.golems.ISealDisplayer;
import thaumcraft.common.golems.EntityThaumcraftGolem;

/** Native living renderer keeps actual name/leash/overlay/outline and Forge living hooks around source OBJ. */
public final class RenderThaumcraftGolem extends MobRenderer<EntityThaumcraftGolem,GolemObjModel> {
    public RenderThaumcraftGolem(EntityRendererProvider.Context context) { super(context,new GolemObjModel(context.getItemInHandRenderer()),.3F); }
    @Override public ResourceLocation getTextureLocation(EntityThaumcraftGolem entity) { return entity.getProperties().getMaterial().texture; }
    @Override public void render(EntityThaumcraftGolem entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light) {
        model.context(buffers,partial,light); super.render(entity,yaw,partial,poses,buffers,light);
        var player = Minecraft.getInstance().player;
        if (player != null && player.isShiftKeyDown() && (player.getMainHandItem().getItem() instanceof ISealDisplayer || player.getOffhandItem().getItem() instanceof ISealDisplayer)
            && !canEntityBeSeen(player,entity)) {
            poses.pushPose(); float scale = entity.getScale(); poses.scale(scale,scale,scale);
            setupRotations(entity,poses,getBob(entity,partial),Mth.rotLerp(partial,entity.yBodyRotO,entity.yBodyRot),partial,scale);
            model.renderGhost(poses); poses.popPose();
        }
    }
    /** Source EntityUtils ray uses centres (not eyes), ignores liquids, and ignores non-colliders. */
    private static boolean canEntityBeSeen(Entity observer,Entity target) {
        var from = observer.position().add(0,observer.getBbHeight() / 2,0);
        var to = target.position().add(0,target.getBbHeight() / 2,0);
        return observer.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,observer)).getType() == HitResult.Type.MISS;
    }
}
