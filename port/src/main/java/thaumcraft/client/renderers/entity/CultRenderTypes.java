package thaumcraft.client.renderers.entity;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.client.lib.warp.WarpEntityRenderTypes;

/** Source additive, unculled electric orb with no depth writes, scoped to a modern render type. */
public final class CultRenderTypes extends RenderType {
    private static final java.util.Map<ResourceLocation, RenderType> ELECTRIC = new HashMap<>();
    private CultRenderTypes() { super("unused_tc_cult", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true, () -> {}, () -> {}); }
    public static RenderType electric(ResourceLocation texture) {
        return ELECTRIC.computeIfAbsent(texture, key -> create("tc_electric_orb", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
            CompositeState.builder().setShaderState(new ShaderStateShard(WarpEntityRenderTypes::particleShader)).setTextureState(new TextureStateShard(key, false, false))
                .setTransparencyState(ADDITIVE_TRANSPARENCY).setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).setWriteMaskState(COLOR_WRITE).createCompositeState(false)));
    }
}
