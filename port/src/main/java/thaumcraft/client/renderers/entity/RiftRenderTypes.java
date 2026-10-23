package thaumcraft.client.renderers.entity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.client.lib.ender.SourceRenderTypes;

/** All four source rift passes use TC's 16-layer ender shader, never vanilla end-portal. */
public final class RiftRenderTypes {
    public static final ResourceLocation STARS=SourceRenderTypes.STARS;
    private RiftRenderTypes(){}
    public static RenderType halo(boolean reveal){return SourceRenderTypes.ender(true,reveal,false);}
    public static RenderType portal(){return SourceRenderTypes.ender(false,false,true);}
    public static RenderType siphon(boolean upper){return SourceRenderTypes.ender(upper,false,false);}
}
