package thaumcraft.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.client.lib.warp.WarpEntityRenderTypes;

/** TC6's additive/alpha and depth/no-depth layer states, scoped by native RenderType. */
public final class FxRenderTypes extends RenderType {
    private record Key(ResourceLocation texture, boolean additive, boolean depthTest, boolean depthWrite, boolean gui) {}
    private static ShaderInstance guiShader;
    static void guiShader(ShaderInstance shader) { guiShader=shader; }
    private static final Map<Key, RenderType> TYPES = new HashMap<>();
    private static final TransparencyStateShard SOURCE_ADDITIVE = new TransparencyStateShard("tc_fx_additive", () -> {
        RenderSystem.enableBlend(); RenderSystem.blendFunc(770,1);
    }, () -> { RenderSystem.disableBlend(); RenderSystem.defaultBlendFunc(); });
    private FxRenderTypes() { super("unused_tc_fx",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,1536,false,true,()->{},()->{}); }
    public static RenderType type(ResourceLocation texture, boolean additive, boolean depthTest, boolean depthWrite) {
        return type(texture,additive,depthTest,depthWrite,false);
    }
    public static RenderType type(ResourceLocation texture, boolean additive, boolean depthTest, boolean depthWrite, boolean gui) {
        return TYPES.computeIfAbsent(new Key(texture,additive,depthTest,depthWrite,gui), k -> create("tc_source_fx",DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,1536,false,true,CompositeState.builder()
            .setShaderState(new ShaderStateShard(k.gui()?()->guiShader:WarpEntityRenderTypes::particleShader))
            .setTextureState(new TextureStateShard(k.texture(),false,false))
            .setTransparencyState(k.additive()?SOURCE_ADDITIVE:TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(k.depthTest()?LEQUAL_DEPTH_TEST:NO_DEPTH_TEST)
            .setCullState(NO_CULL).setLightmapState(k.gui()?NO_LIGHTMAP:LIGHTMAP).setOverlayState(k.gui()?NO_OVERLAY:OVERLAY)
            .setWriteMaskState(k.depthWrite()?COLOR_DEPTH_WRITE:COLOR_WRITE).createCompositeState(false)));
    }
}
