package thaumcraft.common.golems.client;

import java.util.HashMap;
import java.util.Map;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Source sneaking seal-displayer silhouette: translucent, no depth-test and no depth writes. */
public final class GolemRenderTypes extends RenderType {
    private static final Map<ResourceLocation,RenderType> GHOSTS = new HashMap<>();
    private GolemRenderTypes() { super("unused_tc_golem",DefaultVertexFormat.POSITION,VertexFormat.Mode.QUADS,256,false,true,() -> {},() -> {}); }
    public static RenderType ghost(ResourceLocation texture) { return GHOSTS.computeIfAbsent(texture,key -> create("tc_golem_occluded",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,1536,false,true,
        CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER).setTextureState(new TextureStateShard(key,false,false))
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
            .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(false))); }
}
