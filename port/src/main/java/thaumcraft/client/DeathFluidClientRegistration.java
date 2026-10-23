package thaumcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.client.fx.particles.FXSlimyBubble;
import thaumcraft.common.blocks.misc.DeathFluidPresentationHooks;
import thaumcraft.common.blocks.misc.DeathFluidRegistries;

@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DeathFluidClientRegistration {
    @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(DeathFluidRegistries.SOURCE, RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(DeathFluidRegistries.FLOWING, RenderType.translucent());
            DeathFluidPresentationHooks.install((level, pos, metadata, random) -> {
                if (random.nextInt(20) != 0 || !(level instanceof ClientLevel clientLevel)) return;
                float height = random.nextFloat() * .075F;
                FXSlimyBubble bubble = new FXSlimyBubble(clientLevel, pos.getX() + random.nextFloat(), pos.getY() + .1F + .225F * (4 - metadata), pos.getZ() + random.nextFloat(), .075F + height);
                bubble.sourceColor(.3F - random.nextFloat() * .1F, 0, .4F + random.nextFloat() * .1F, .8F);
                Minecraft.getInstance().particleEngine.add(bubble);
            });
        });
    }
    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override public ResourceLocation getStillTexture() { return ResourceLocation.parse("thaumcraft:block/animatedglow"); }
            @Override public ResourceLocation getFlowingTexture() { return ResourceLocation.parse("thaumcraft:block/animatedglow"); }
            @Override public int getTintColor() { return -263978855; }
        }, DeathFluidRegistries.TYPE);
    }
}
