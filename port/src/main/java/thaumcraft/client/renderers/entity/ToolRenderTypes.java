package thaumcraft.client.renderers.entity;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.client.lib.warp.WarpEntityRenderTypes;

/** Original scan layers2/3 are visible through terrain, with additive/alpha blend. */
public final class ToolRenderTypes extends RenderType {
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("thaumcraft:textures/misc/particles.png");
    private ToolRenderTypes() { super("unused_tc_tool", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true, () -> {}, () -> {}); }
    public static final RenderType SCAN_GLOW = make(true), SCAN_DARK = make(false);
    private static RenderType make(boolean glow) {
        return create("tc_source_scan_" + glow, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true,
            CompositeState.builder().setShaderState(new ShaderStateShard(WarpEntityRenderTypes::particleShader))
                .setTextureState(new TextureStateShard(TEXTURE, false, false)).setTransparencyState(glow ? ADDITIVE_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL).setDepthTestState(NO_DEPTH_TEST).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                .setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    }
}
