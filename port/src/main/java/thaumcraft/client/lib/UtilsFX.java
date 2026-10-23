package thaumcraft.client.lib;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.config.ModConfig;

/** Explicit pose/texture native equivalents of UtilsFX; no hidden GL matrix or bound texture. */
public final class UtilsFX {
    public static float sysPartialTicks;
    private static final Map<String, RenderType> TYPES = new HashMap<>();
    private static final RenderStateShard.TransparencyStateShard SOURCE_ADDITIVE = new RenderStateShard.TransparencyStateShard(
        "tc_utils_src_alpha_one", () -> {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        }, () -> {
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        });
    private static final DecimalFormat AMOUNT = new DecimalFormat("#######.#");
    private UtilsFX() {}
    public static RenderType type(ResourceLocation texture, boolean additive, boolean through) {
        return TYPES.computeIfAbsent(texture + "|" + additive + "|" + through, key -> RenderType.create("tc_utils_fx",
            com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
            1536, false, true, RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(additive ? SOURCE_ADDITIVE : RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setCullState(RenderStateShard.NO_CULL).setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY).setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setDepthTestState(through ? RenderStateShard.NO_DEPTH_TEST : RenderStateShard.LEQUAL_DEPTH_TEST)
                .createCompositeState(false)));
    }
    public static void renderQuadCentered(PoseStack poses, VertexConsumer out, float size,
            float r, float g, float b, float alpha, int light, float u0, float v0, float u1, float v1) {
        float h = size / 2;
        // TC6 TexturedQuadTC uses this rotated UV order, not the ordinary atlas quad.
        vertex(poses, out, -h,h,0,r,g,b,alpha,u1,v1,light);
        vertex(poses, out, h,h,0,r,g,b,alpha,u1,v0,light);
        vertex(poses, out, h,-h,0,r,g,b,alpha,u0,v0,light);
        vertex(poses, out, -h,-h,0,r,g,b,alpha,u0,v1,light);
    }
    public static void vertex(PoseStack poses, VertexConsumer out,float x,float y,float z,float r,float g,float b,float a,float u,float v,int light) {
        out.addVertex(poses.last(),x,y,z).setColor(r,g,b,a).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(poses.last(),0,0,1);
    }
    public static void drawTexturedQuad(GuiGraphics graphics, ResourceLocation texture, float x,float y,float u,float v,float width,float height) {
        if(width<=0||height<=0)return;
        // Retain fractional scrolling meter UVs/heights; native integer blit would snap them.
        graphics.pose().pushPose(); graphics.pose().translate(x,y,0);graphics.pose().scale(width,height,1);
        var matrix=graphics.pose().last().pose();com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0,texture);com.mojang.blaze3d.systems.RenderSystem.setShader(GameRenderer::getPositionTexShader);
        var buffer=com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix,0,1,0).setUv(u/256,(v+height)/256);buffer.addVertex(matrix,1,1,0).setUv((u+width)/256,(v+height)/256);buffer.addVertex(matrix,1,0,0).setUv((u+width)/256,v/256);buffer.addVertex(matrix,0,0,0).setUv(u/256,v/256);
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.buildOrThrow());
        graphics.pose().popPose();
    }
    public static void renderItemInGUI(GuiGraphics graphics,int x,int y,int z,ItemStack stack) {
        graphics.pose().pushPose(); graphics.pose().translate(0,0,z); graphics.renderItem(stack,x,y);
        // CustomRenderItem's recipe descriptor count <= 0 star cannot be represented by EMPTY
        // native ItemStack. Existing recipe viewer renders this descriptor separately.
        graphics.renderItemDecorations(Minecraft.getInstance().font,stack,x,y); graphics.pose().popPose();
    }
    public static void drawTag(GuiGraphics graphics,int x,int y,Aspect aspect,float amount) {
        int c=aspect.getColor(); graphics.setColor((c>>16&255)/255F,(c>>8&255)/255F,(c&255)/255F,1);
        graphics.blit(aspect.getImage(),x,y,0,0,16,16,16,16); graphics.setColor(1,1,1,1);
        if(amount<=0)return;
        var font=Minecraft.getInstance().font; String text=AMOUNT.format(amount); float scale=ModConfig.CONFIG_GRAPHICS.largeTagText?1:.5F;
        graphics.pose().pushPose(); graphics.pose().translate(x,y,0);graphics.pose().scale(scale,scale,1);
        int tx=(int)(16/scale)-font.width(text),ty=(int)(16/scale)-font.lineHeight;
        graphics.drawString(font,text,tx-1,ty,0,false);graphics.drawString(font,text,tx+1,ty,0,false);
        graphics.drawString(font,text,tx,ty-1,0,false);graphics.drawString(font,text,tx,ty+1,0,false);
        graphics.drawString(font,text,tx,ty,-1,false);graphics.pose().popPose();
    }
    public static void orientFace(PoseStack poses,net.minecraft.core.Direction face) {
        switch(face){case UP->poses.mulPose(Axis.XP.rotationDegrees(-90));case DOWN->poses.mulPose(Axis.XP.rotationDegrees(90));case NORTH->poses.mulPose(Axis.YP.rotationDegrees(180));case EAST->poses.mulPose(Axis.YP.rotationDegrees(90));case WEST->poses.mulPose(Axis.YP.rotationDegrees(-90));default->{}}
    }
    public static void clear() { TYPES.clear();sysPartialTicks=0; }
}
