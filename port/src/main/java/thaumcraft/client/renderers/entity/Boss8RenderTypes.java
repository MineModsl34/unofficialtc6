package thaumcraft.client.renderers.entity;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.Map;
import thaumcraft.client.lib.warp.WarpEntityRenderTypes;

/** Native shader lifecycle for the literal TC ender shader and its two source blend passes. */
public final class Boss8RenderTypes extends RenderType {
    private static final Map<ResourceLocation,RenderType> SOURCE_BEAMS=new HashMap<>();
    private static final ResourceLocation STARS=ResourceLocation.parse("minecraft:textures/entity/end_portal.png");
    private Boss8RenderTypes(){super("unused_tc_boss",DefaultVertexFormat.POSITION,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
    public static void install(ShaderInstance shader){thaumcraft.client.lib.ender.ShaderHelper.installEnder(shader);}
    private static ShaderInstance shader(){
        return thaumcraft.client.lib.ender.ShaderHelper.enderShader();
    }
    private static RenderType type(boolean outer){return create("tc_rift_blast_"+(outer?"outer":"inner"),DefaultVertexFormat.POSITION_TEX,VertexFormat.Mode.QUADS,1536,false,true,
        CompositeState.builder().setShaderState(new ShaderStateShard(Boss8RenderTypes::shader)).setTextureState(new TextureStateShard(STARS,false,false))
            .setTransparencyState(outer?LIGHTNING_TRANSPARENCY:TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setWriteMaskState(outer?COLOR_WRITE:COLOR_DEPTH_WRITE).createCompositeState(false));}
    /** Source glBlendFunc(770,1), not native ADDITIVE's (1,1); scoped to these new spell beams. */
    public static RenderType sourceBeam(ResourceLocation texture){return SOURCE_BEAMS.computeIfAbsent(texture,key->create("tc_boss_source_beam",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,
        CompositeState.builder().setShaderState(new ShaderStateShard(WarpEntityRenderTypes::particleShader)).setTextureState(new TextureStateShard(key,false,false))
            .setTransparencyState(LIGHTNING_TRANSPARENCY).setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).setWriteMaskState(COLOR_WRITE).createCompositeState(false)));}
    public static final RenderType RIFT_OUTER=type(true),RIFT_INNER=type(false);
}
