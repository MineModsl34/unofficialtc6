package thaumcraft.client.renderers.tile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import thaumcraft.common.tiles.crafting.TilePedestal;

public final class TilePedestalRenderer implements BlockEntityRenderer<TilePedestal> {
    public TilePedestalRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(TilePedestal tile,float partialTick,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        var stack = tile.getSyncedStackInSlot(0); if (stack.isEmpty()) return;
        float ticks = Minecraft.getInstance().getCameraEntity() == null ? 0 : Minecraft.getInstance().getCameraEntity().tickCount + partialTick;
        poses.pushPose(); poses.translate(.5,.75,.5); poses.scale(1.25F,1.25F,1.25F); poses.mulPose(Axis.YP.rotationDegrees(ticks % 360));
        // Native item render path (the source's zero-age EntityItem imposed an extra .25 y offset).
        poses.translate(0,.25,0);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack.copyWithCount(1),ItemDisplayContext.GROUND,light,overlay,poses,buffers,tile.getLevel(),0);
        poses.popPose();
    }
}
