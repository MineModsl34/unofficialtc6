package thaumcraft.common.golems.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import thaumcraft.client.renderers.tile.TileGolemBuilderRenderer;

/** Blueprint/item view uses the actual18th source OBJ without a fake level or surrogate BE. */
public final class GolemBuilderItemRenderer extends BlockEntityWithoutLevelRenderer {
    public GolemBuilderItemRenderer() { super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels()); }
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext display,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        poses.pushPose(); poses.translate(.2,0,.2); poses.scale(.6F,.6F,.6F); TileGolemBuilderRenderer.renderPress(poses,buffers,Direction.NORTH,0,light,overlay); poses.popPose();
    }
}
