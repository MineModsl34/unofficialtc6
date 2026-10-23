package thaumcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.client.renderers.entity.mob.RenderMindSpider;
import thaumcraft.client.renderers.entity.mob.RenderEldritchGuardian;
import thaumcraft.client.renderers.entity.mob.RenderThaumicSlime;
import thaumcraft.client.renderers.entity.projectile.RenderEldritchOrb;
import thaumcraft.client.renderers.models.entity.ModelEldritchGuardian;
import thaumcraft.client.lib.warp.WarpPostEffects;
import thaumcraft.client.fx.other.FXSonic;
import thaumcraft.client.lib.warp.WarpEntityRenderTypes;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.minecraft.client.renderer.ShaderInstance;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import thaumcraft.common.entities.WarpEntities;
import thaumcraft.common.lib.potions.WarpEffects;
import thaumcraft.common.blocks.world.taint.WarpTaintRegistries;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.client.lib.warp.WarpTaintPresentation;
import thaumcraft.common.lib.warp.WarpTaintPresentationHooks;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;

/** Source potion atlas coordinates; no missing modern effect-icon sprites. */
@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WarpClientRegistration {
    private static final ResourceLocation POTIONS = ResourceLocation.parse("thaumcraft:textures/misc/potions.png");
    private WarpClientRegistration() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) { event.enqueueWork(() -> {
        WarpTaintPresentationHooks.install(new WarpTaintPresentation());
        ItemBlockRenderTypes.setRenderLayer(WarpTaintRegistries.FLUX,RenderType.translucent());
        ItemBlockRenderTypes.setRenderLayer(BlocksTC.taintFibre,RenderType.cutout());
    }); }
    @SubscribeEvent public static void shaders(RegisterShadersEvent event) throws java.io.IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.parse("thaumcraft:warp_entity"), DefaultVertexFormat.NEW_ENTITY), WarpEntityRenderTypes::entityShader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.parse("thaumcraft:warp_particle"), DefaultVertexFormat.NEW_ENTITY), WarpEntityRenderTypes::particleShader);
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> Minecraft.getInstance().execute(() -> {
            WarpPostEffects.clear(); FXSonic.reload();
        }));
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WarpEntities.MIND_SPIDER, RenderMindSpider::new);
        event.registerEntityRenderer(WarpEntities.ELDRITCH_GUARDIAN, RenderEldritchGuardian::new);
        event.registerEntityRenderer(WarpEntities.ELDRITCH_ORB, RenderEldritchOrb::new);
        event.registerEntityRenderer(WarpEntities.THAUMIC_SLIME,RenderThaumicSlime::new);
    }
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(RenderEldritchGuardian.LAYER, ModelEldritchGuardian::createBodyLayer);
    }
    @SubscribeEvent public static void effects(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override public ResourceLocation getStillTexture() { return ResourceLocation.parse("thaumcraft:block/flux_goo"); }
            @Override public ResourceLocation getFlowingTexture() { return ResourceLocation.parse("thaumcraft:block/flux_goo"); }
        },WarpTaintRegistries.FLUX_TYPE);
        event.registerMobEffect(new Icon(3,1),WarpEffects.FLUX_TAINT);
        event.registerMobEffect(new Icon(5, 1), WarpEffects.VIS_EXHAUST);
        event.registerMobEffect(new Icon(6, 1), WarpEffects.INFECTIOUS_VIS_EXHAUST);
        event.registerMobEffect(new Icon(7, 1), WarpEffects.UNNATURAL_HUNGER);
        event.registerMobEffect(new Icon(3, 2), WarpEffects.WARP_WARD);
        event.registerMobEffect(new Icon(4, 2), WarpEffects.DEATH_GAZE);
        event.registerMobEffect(new Icon(5, 2), WarpEffects.BLURRED_VISION);
        event.registerMobEffect(new Icon(6, 2), WarpEffects.SUN_SCORNED);
        event.registerMobEffect(new Icon(7, 2), WarpEffects.THAUMARHIA);
    }
    private record Icon(int column, int row) implements IClientMobEffectExtensions {
        private void draw(GuiGraphics graphics, int x, int y, float alpha) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1, 1, 1, alpha);
            graphics.blit(POTIONS, x, y, column * 18, row * 18, 18, 18, 256, 256);
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
        @Override public boolean renderInventoryIcon(MobEffectInstance instance, EffectRenderingInventoryScreen<?> screen,
                GuiGraphics graphics, int x, int y, int depth) { draw(graphics, x, y + 7, 1); return true; }
        @Override public boolean renderGuiIcon(MobEffectInstance instance, Gui gui, GuiGraphics graphics,
                int x, int y, float depth, float alpha) { draw(graphics, x + 3, y + 3, alpha); return true; }
    }
}
