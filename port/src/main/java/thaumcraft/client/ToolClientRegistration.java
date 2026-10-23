package thaumcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.client.fx.FXDispatcher;
import thaumcraft.common.entities.ToolPresentation;
import thaumcraft.common.entities.ToolRegistration;

@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ToolClientRegistration {
    private ToolClientRegistration() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) { event.registerEntityRenderer(ToolRegistration.FOLLOWING_ITEM, ItemEntityRenderer::new); }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ToolPresentation.install((item, mode) -> {
            if (!(item.level() instanceof ClientLevel world) || world != Minecraft.getInstance().level) return;
            var random = item.trailRandom(); float height = item.getBbHeight()/2F + Mth.sin(item.getAge()/10F + item.bobOffs)*.1F+.1F;
            float x=(float)item.xo+(random.nextFloat()-random.nextFloat())*.125F;
            float y=(float)item.yo+height+(random.nextFloat()-random.nextFloat())*.125F;
            float z=(float)item.zo+(random.nextFloat()-random.nextFloat())*.125F;
            if(mode==10)FXDispatcher.INSTANCE.crucibleBubble(x,y,z,.33F,.33F,1);
            else FXDispatcher.INSTANCE.drawNitorCore(x,y,z,random.nextGaussian()*.01F,random.nextGaussian()*.01F,random.nextGaussian()*.01F);
        }));
    }
}
