package thaumcraft.client.lib.warp;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.Holder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.slf4j.Logger;
import thaumcraft.Thaumcraft;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.lib.potions.WarpEffects;

/** Four simultaneous source warp shaders, independent of vanilla spectator/GameRenderer effects. */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class WarpPostEffects {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation[] RESOURCES = {
        ResourceLocation.parse("thaumcraft:shaders/post/warp_desaturatetc.json"),
        ResourceLocation.parse("thaumcraft:shaders/post/warp_blurtc.json"),
        ResourceLocation.parse("thaumcraft:shaders/post/warp_hunger.json"),
        ResourceLocation.parse("thaumcraft:shaders/post/warp_sunscorned.json")
    };
    private static final ResourceLocation VIGNETTE = ResourceLocation.parse("thaumcraft:textures/misc/vignette.png");
    private static final Map<Integer, PostChain> CHAINS = new LinkedHashMap<>();
    private static final boolean[] FAILED = new boolean[4];
    private static int width, height;
    private static float previousVignette;
    private static float vignetteTarget;
    private static int vignetteTicks;
    private WarpPostEffects() {}
    /** Source ShaderHandler compatibility without a second vignette clock/overlay. */
    public static int warpVignette() { return vignetteTicks; }
    public static void setVignetteTicks(int ticks) { vignetteTicks = Math.max(0,ticks); }

    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.isPaused()) return;
        if (minecraft.player.hasEffect(WarpEffects.DEATH_GAZE)) vignetteTicks = 10;
        if (vignetteTicks > 0) { vignetteTicks--; vignetteTarget = 1.0F; }
        else vignetteTarget = 0.0F;
        update(minecraft, 0, WarpEffects.DEATH_GAZE);
        update(minecraft, 1, WarpEffects.BLURRED_VISION);
        update(minecraft, 2, WarpEffects.UNNATURAL_HUNGER);
        update(minecraft, 3, WarpEffects.SUN_SCORNED);
    }

    private static void update(Minecraft minecraft, int key, Holder<MobEffect> effect) {
        if (!minecraft.player.hasEffect(effect)) {
            PostChain old = CHAINS.remove(key);
            if (old != null) old.close();
            FAILED[key] = false;
            return;
        }
        if (CHAINS.containsKey(key) || FAILED[key]) return;
        try {
            PostChain chain = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(),
                minecraft.getMainRenderTarget(), RESOURCES[key]);
            chain.resize(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
            CHAINS.put(key, chain);
        } catch (IOException | RuntimeException error) {
            FAILED[key] = true; // One log per actual effect activation, not per tick/frame.
            LOGGER.warn("Could not initialize TC6 warp shader {}", RESOURCES[key], error);
        }
    }

    @SubscribeEvent public static void shaders(RenderGuiEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ModConfig.CONFIG_GRAPHICS.disableShaders || minecraft.player == null || CHAINS.isEmpty()) return;
        int currentWidth = minecraft.getWindow().getWidth(), currentHeight = minecraft.getWindow().getHeight();
        if (width != currentWidth || height != currentHeight) {
            width = currentWidth;
            height = currentHeight;
            for (PostChain chain : CHAINS.values()) chain.resize(width, height);
        }
        // Source renders all active groups before ALL HUD. GuiGraphics is batched now: flush
        // outstanding HUD vertices before changing targets, then restore the main framebuffer.
        event.getGuiGraphics().flush();
        try {
            var iterator = CHAINS.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                try {
                    entry.getValue().process(event.getPartialTick().getGameTimeDeltaPartialTick(false));
                } catch (RuntimeException error) {
                    FAILED[entry.getKey()] = true;
                    LOGGER.warn("Could not render TC6 warp shader {}", RESOURCES[entry.getKey()], error);
                    entry.getValue().close();
                    iterator.remove();
                }
            }
        } finally {
            minecraft.getMainRenderTarget().bindWrite(true);
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }

    @SubscribeEvent public static void vignette(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        previousVignette = (float) (previousVignette + (vignetteTarget - previousVignette) * 0.01);
        if (previousVignette <= 0.0F) return;
        float intensity = previousVignette * (1.0F + (float) Math.sin(minecraft.player.tickCount / 2.0F) * 0.1F);
        var graphics = event.getGuiGraphics();
        graphics.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShaderColor(intensity, intensity, intensity, 1.0F);
        try {
            graphics.blit(VIGNETTE, 0, 0, 0, 0, graphics.guiWidth(), graphics.guiHeight(),
                graphics.guiWidth(), graphics.guiHeight());
            graphics.flush();
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.defaultBlendFunc();
        }
    }

    /** Runs on the render executor from resource reload and logout; no cross-world active chains. */
    public static void clear() {
        for (PostChain chain : CHAINS.values()) chain.close();
        CHAINS.clear();
        java.util.Arrays.fill(FAILED, false);
        width = height = 0;
        previousVignette = 0;
        vignetteTarget = 0;
        vignetteTicks = 0;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
}
