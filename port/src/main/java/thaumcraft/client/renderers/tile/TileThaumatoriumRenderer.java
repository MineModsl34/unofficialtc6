package thaumcraft.client.renderers.tile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import thaumcraft.common.tiles.crafting.TileThaumatorium;

/** Source one-second rotating output preview; native item renderer avoids fake client entities. */
public final class TileThaumatoriumRenderer implements BlockEntityRenderer<TileThaumatorium> {
    public TileThaumatoriumRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(TileThaumatorium tile,float partialTick,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        if (tile.getLevel() == null || tile.selections().isEmpty()) return;
        var stack = tile.renderOutput((int)(tile.getLevel().getGameTime() / 20 % tile.selections().size()));
        if (stack.isEmpty()) return;
        var face = tile.facing();
        poses.pushPose(); poses.translate(.5 + face.getStepX() / 1.99,1.125,.5 + face.getStepZ() / 1.99);
        poses.mulPose(Axis.YP.rotationDegrees(switch(face) { case NORTH -> 180; case WEST -> 270; case EAST -> 90; default -> 0; }));
        poses.scale(.75F,.75F,.75F);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack.copyWithCount(1),ItemDisplayContext.GROUND,light,overlay,poses,buffers,tile.getLevel(),0);
        poses.popPose();
    }
    @Override public int getViewDistance() { return 64; }
}
