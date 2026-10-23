package thaumcraft.client.renderers.tile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.tiles.crafting.TileCrucible;

/** Native-buffer port of the exact source fluid quad, including full-block coverage, animated
 * water UVs, fluidHeight, aspect recolor and alpha1. Source GL blend770/771 maps to the standard
 * entityTranslucent block atlas buffer; modern lighting/shader batching is not pixel parity.
 */
public final class TileCrucibleRenderer implements BlockEntityRenderer<TileCrucible> {
    private static final ResourceLocation WATER = ResourceLocation.withDefaultNamespace("block/water_still");
    public TileCrucibleRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(TileCrucible tile, float partialTick, PoseStack poses, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (tile.tank.getFluidAmount() <= 0) return;
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(WATER);
        float recolor = tile.aspects.visSize() / 500F;
        if (recolor > 0F) recolor = .5F + recolor / 2F;
        recolor = Math.min(recolor, 1F);
        float red = 1F - recolor / 3F, green = 1F - recolor, blue = 1F - recolor / 2F;
        float y = tile.getFluidHeight();
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        // Source translate(z+1), rotate90 around -X transforms its four unit-quad vertices here.
        vertex(vertices, poses.last(), 0, y, 1, sprite.getU1(), sprite.getV1(), red, green, blue, packedLight);
        vertex(vertices, poses.last(), 1, y, 1, sprite.getU0(), sprite.getV1(), red, green, blue, packedLight);
        vertex(vertices, poses.last(), 1, y, 0, sprite.getU0(), sprite.getV0(), red, green, blue, packedLight);
        vertex(vertices, poses.last(), 0, y, 0, sprite.getU1(), sprite.getV0(), red, green, blue, packedLight);
    }
    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float z, float u, float v,
        float red, float green, float blue, int light) {
        vertices.addVertex(pose, x, y, z).setColor(red, green, blue, 1F).setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
    }
}
