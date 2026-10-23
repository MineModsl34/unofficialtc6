package thaumcraft.client.fx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import thaumcraft.client.fx.other.FXShieldRunes;

/** FX-only native resources; no server class references or compatibility GL registrations. */
@EventBusSubscriber(modid="thaumcraft",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class FXClientRegistration {
    private FXClientRegistration() {}
    @SubscribeEvent public static void shaders(RegisterShadersEvent event) throws java.io.IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.parse("thaumcraft:fx_gui"),DefaultVertexFormat.NEW_ENTITY),FxRenderTypes::guiShader);
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> Minecraft.getInstance().execute(FXShieldRunes::reload));
    }
}
