package thaumcraft.client.lib.warp;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

/** Source low-alpha1/255 test and per-renderer depth writes, without mutating Minecraft global shaders. */
public final class WarpEntityRenderTypes extends RenderType {
    private record Key(ResourceLocation texture, boolean depth, boolean lit) {}
    private static final Map<Key, RenderType> TYPES = new HashMap<>();
    private static ShaderInstance entityShader, particleShader;
    private WarpEntityRenderTypes() { super("unused_tc_warp", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true, () -> {}, () -> {}); }
    public static void entityShader(ShaderInstance shader) { entityShader = shader; }
    public static void particleShader(ShaderInstance shader) { particleShader = shader; }
    public static ShaderInstance particleShader() { return particleShader; }
    public static RenderType body(ResourceLocation texture, boolean writesDepth) { return type(new Key(texture, writesDepth, true)); }
    public static RenderType particle(ResourceLocation texture) { return type(new Key(texture, false, false)); }
    private static RenderType type(Key key) {
        return TYPES.computeIfAbsent(key, value -> create("tc_warp_source_alpha", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
            1536, false, true, CompositeState.builder()
                .setShaderState(new ShaderStateShard(() -> value.lit() ? entityShader : particleShader))
                .setTextureState(new TextureStateShard(value.texture(), false, false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                .setWriteMaskState(value.depth() ? COLOR_DEPTH_WRITE : COLOR_WRITE).createCompositeState(false)));
    }
}
