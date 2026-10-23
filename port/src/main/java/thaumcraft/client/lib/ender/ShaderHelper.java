package thaumcraft.client.lib.ender;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import thaumcraft.common.config.ModConfig;

/** Resource-reloaded native programs; no legacy ARB program IDs or global glUseProgram state. */
@EventBusSubscriber(modid="thaumcraft",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ShaderHelper {
    private static ShaderInstance endShader, sketchShader, mirrorShader;
    private ShaderHelper() {}
    /** Called by the existing boss program registrar; all five source ender consumers share it. */
    public static void installEnder(ShaderInstance shader) { endShader=shader; }
    public static boolean useShaders() { return !ModConfig.CONFIG_GRAPHICS.disableShaders; }
    public static ShaderInstance enderShader() {
        Minecraft mc=Minecraft.getInstance(); var camera=mc.getCameraEntity(); var player=mc.player;
        if(endShader!=null && camera!=null) {
            endShader.safeGetUniform("ShaderEnabled").set(useShaders()?1:0);
            endShader.safeGetUniform("time").set(camera.tickCount);
            // Source ticks come from renderViewEntity; yaw/pitch specifically come from the player.
            if(player!=null) {endShader.safeGetUniform("yaw").set((float)Math.toRadians(player.getYRot()));endShader.safeGetUniform("pitch").set(-(float)Math.toRadians(player.getXRot()));}
        }
        return endShader;
    }
    public static ShaderInstance mirrorShader() { return mirrorShader; }
    /** Source compiled this program but never called it; no invented gameplay activation. */
    public static ShaderInstance sketchShader() { return sketchShader; }
    @SubscribeEvent public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.parse("thaumcraft:source_mirror"),DefaultVertexFormat.NEW_ENTITY),shader->mirrorShader=shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.parse("thaumcraft:source_sketch"),DefaultVertexFormat.POSITION),shader->sketchShader=shader);
    }
}
