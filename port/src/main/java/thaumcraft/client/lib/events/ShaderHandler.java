package thaumcraft.client.lib.events;
import net.minecraft.resources.ResourceLocation;
/** Source ID/resource facade. WarpPostEffects remains the single owner of active PostChains. */
public final class ShaderHandler {
    public static final int SHADER_DESAT=0,SHADER_BLUR=1,SHADER_HUNGER=2,SHADER_SUNSCORNED=3;
    public static final ResourceLocation[] shader_resources={
        ResourceLocation.parse("thaumcraft:shaders/post/warp_desaturatetc.json"),ResourceLocation.parse("thaumcraft:shaders/post/warp_blurtc.json"),
        ResourceLocation.parse("thaumcraft:shaders/post/warp_hunger.json"),ResourceLocation.parse("thaumcraft:shaders/post/warp_sunscorned.json")};
    private ShaderHandler() {}
}
