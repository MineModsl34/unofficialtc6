package thaumcraft.client.renderers.tile;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import thaumcraft.common.tiles.essentia.TileAlembic;

public final class TileAlembicRenderer implements BlockEntityRenderer<TileAlembic> {
    public TileAlembicRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(TileAlembic tile,float partialTick,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        if (tile.aspectFilter != null) AlchemyRenderSupport.label(poses,buffers,tile.aspectFilter,tile.facing,.5F,.376F,.44F,.32F,0,light);
        // Actual side-pipe nozzle rendering joins the source tube devices in phase6.
    }
}
