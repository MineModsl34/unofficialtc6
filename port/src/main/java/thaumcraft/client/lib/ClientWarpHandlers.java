package thaumcraft.client.lib;

import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumcraft.Thaumcraft;
import thaumcraft.client.lib.warp.WarpPresentationState;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.network.misc.PacketMiscEvent;
import thaumcraft.common.lib.network.playerdata.PacketWarpMessage;
import thaumcraft.common.lib.network.fx.PacketFXSonic;
import thaumcraft.client.fx.other.FXSonic;

/** Client-only TC6 warp sound/text/fog. Payloads never mutate warp or research. */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ClientWarpHandlers {
    private static final WarpPresentationState FOG = new WarpPresentationState();
    private ClientWarpHandlers() {}

    private static boolean current(Minecraft minecraft, IPayloadContext context) {
        if (!minecraft.isSameThread()) throw new IllegalStateException("Warp feedback requires client MAIN");
        return minecraft.player != null && minecraft.level != null && minecraft.getConnection() != null
            && minecraft.getConnection().getConnection() == context.connection();
    }
    public static void message(PacketWarpMessage packet, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!current(minecraft, context)) return;
        var feedback = WarpPresentationState.feedback(packet.amount(), packet.warpType());
        if (feedback.whispers()) minecraft.player.playSound(SoundsTC.whispers, 0.5F, 1.0F);
        if (feedback.translation() != null) minecraft.player.displayClientMessage(Component.translatable(feedback.translation()), true);
    }
    public static void misc(PacketMiscEvent packet, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!current(minecraft, context)) return;
        switch (packet.eventType()) {
            case PacketMiscEvent.WARP_EVENT -> {
                if (!ModConfig.CONFIG_GRAPHICS.nostress) minecraft.level.playLocalSound(minecraft.player.getX(),
                    minecraft.player.getY(), minecraft.player.getZ(), SoundsTC.heartbeat, SoundSource.AMBIENT, 1.0F, 1.0F, false);
            }
            case PacketMiscEvent.MIST_EVENT -> FOG.mist(false);
            case PacketMiscEvent.MIST_EVENT_SHORT -> FOG.mist(true);
            default -> throw new IllegalArgumentException("Unknown warp presentation event");
        }
    }
    public static void sonic(PacketFXSonic packet, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!current(minecraft, context)) return;
        var entity = minecraft.level.getEntity(packet.sourceEntity());
        if (entity != null) minecraft.particleEngine.add(new FXSonic(minecraft.level, entity, 10));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.level != null && !minecraft.isPaused()) FOG.tick();
    }
    @SubscribeEvent public static void fog(ViewportEvent.RenderFog event) {
        if (!FOG.fogActive() || FOG.density() <= 0) return;
        // Modern fog has linear shader planes, not old fixed-function GL_EXP density. Keep the
        // original duration/density envelope; place the 95% extinction plane at -ln(.05)/density.
        float distance = (float) (-Math.log(0.05) / FOG.density());
        event.setNearPlaneDistance(0.0F);
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), distance));
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { FOG.clear(); }
}
