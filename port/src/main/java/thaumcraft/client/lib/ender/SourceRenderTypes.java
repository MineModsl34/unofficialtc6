package thaumcraft.client.lib.ender;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.client.lib.warp.WarpEntityRenderTypes;
/** Explicit source blend/depth states replace fixed-function GL without leaking between batches. */
public final class SourceRenderTypes extends RenderType {
    public static final ResourceLocation STARS=ResourceLocation.withDefaultNamespace("textures/entity/end_portal.png");
    private static final TransparencyStateShard ADD=new TransparencyStateShard("tc_source_src_alpha_one",()->{RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE);},()->{RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
    private static final TransparencyStateShard ONE_ONE=new TransparencyStateShard("tc_source_one_one",()->{RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ONE);},()->{RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
    private static final Map<String,RenderType> TYPES=new HashMap<>();
    private SourceRenderTypes(){super("unused_tc_source",DefaultVertexFormat.POSITION,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
    public static RenderType ender(boolean additive,boolean reveal,boolean depthWrite){String key="end"+additive+reveal+depthWrite;return TYPES.computeIfAbsent(key,k->create(k,DefaultVertexFormat.POSITION_TEX,VertexFormat.Mode.QUADS,1536,false,false,CompositeState.builder().setShaderState(new ShaderStateShard(ShaderHelper::enderShader)).setTextureState(new TextureStateShard(STARS,false,false)).setTransparencyState(additive?ADD:TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setDepthTestState(reveal?NO_DEPTH_TEST:LEQUAL_DEPTH_TEST).setWriteMaskState(depthWrite?COLOR_DEPTH_WRITE:COLOR_WRITE).createCompositeState(false)));}
    public static RenderType additive(ResourceLocation texture,boolean depthWrite){String key="add"+texture+depthWrite;return TYPES.computeIfAbsent(key,k->create(k,DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,1536,false,true,CompositeState.builder().setShaderState(new ShaderStateShard(WarpEntityRenderTypes::particleShader)).setTextureState(new TextureStateShard(texture,false,false)).setTransparencyState(ADD).setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).setWriteMaskState(depthWrite?COLOR_DEPTH_WRITE:COLOR_WRITE).createCompositeState(false)));}
    public static RenderType rays(){return TYPES.computeIfAbsent("rays",k->create("tc_source_rays",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.TRIANGLES,1536,false,false,CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(ADD).setCullState(CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false)));}
    public static RenderType mirror(boolean first){return TYPES.computeIfAbsent("mirror"+first,k->create(k,DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,1536,false,false,CompositeState.builder().setShaderState(new ShaderStateShard(ShaderHelper::mirrorShader)).setTextureState(new TextureStateShard(ResourceLocation.parse("thaumcraft:textures/misc/"+(first?"tunnel":"particlefield")+".png"),false,false)).setTransparencyState(first?TRANSLUCENT_TRANSPARENCY:ONE_ONE).setCullState(NO_CULL).createCompositeState(false)));}
}
