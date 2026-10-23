package thaumcraft.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.client.renderers.entity.construct.RenderCultistPortalLesser;
import thaumcraft.client.renderers.entity.mob.RenderCultist;
import thaumcraft.client.renderers.entity.projectile.RenderElectricOrb;
import thaumcraft.common.entities.CultEntities;
import thaumcraft.common.entities.CultistPresentationHooks;
import thaumcraft.client.fx.particles.FXCultistParticle;
import net.minecraft.world.level.Level;

@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CultClientRegistration {
    private CultClientRegistration() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) { event.enqueueWork(() -> CultistPresentationHooks.install(new CultistPresentationHooks.Presentation() {
        @Override public void spawn(Level level, double x, double y, double z, double dx, double dy, double dz) {
            if (level instanceof ClientLevel world) Minecraft.getInstance().particleEngine.add(new FXCultistParticle(world, x, y, z, dx, dy, dz, true, 1));
        }
        @Override public void burst(Level level, double x, double y, double z, float size) {
            if (level instanceof ClientLevel world) Minecraft.getInstance().particleEngine.add(new FXCultistParticle(world, x, y, z, 0, 0, 0, false, size));
        }
    })); }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CultEntities.PORTAL, RenderCultistPortalLesser::new);
        event.registerEntityRenderer(CultEntities.KNIGHT, RenderCultist::new);
        event.registerEntityRenderer(CultEntities.CLERIC, RenderCultist::new);
        event.registerEntityRenderer(CultEntities.GOLEM_ORB, RenderElectricOrb::new);
    }
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(RenderCultist.LAYER, () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0), 64, 32));
    }
}
