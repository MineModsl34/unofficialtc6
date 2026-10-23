package thaumcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.client.fx.particles.FXAlumentumParticle;
import thaumcraft.common.entities.AlumentumPresentation;
import thaumcraft.common.entities.AlumentumRegistration;
import thaumcraft.common.entities.projectile.EntityAlumentum;

@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AlumentumClientRegistration {
    private AlumentumClientRegistration() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> AlumentumPresentation.install(projectile -> {
            if (!(projectile.level() instanceof ClientLevel world)) return;
            var particles = Minecraft.getInstance().particleEngine; var random = projectile.trailRandom();
            for (int index = 0; index < 3; index++) {
                double coefficient = index / 3D;
                particles.add(new FXAlumentumParticle(world,
                    (float)(projectile.xo + (projectile.getX() - projectile.xo) * coefficient),
                    (float)(projectile.yo + (projectile.getY() - projectile.yo) * coefficient) + projectile.getBbHeight() / 2F,
                    (float)(projectile.zo + (projectile.getZ() - projectile.zo) * coefficient),
                    .0125F * (random.nextFloat() - .5F), .0125F * (random.nextFloat() - .5F), .0125F * (random.nextFloat() - .5F),
                    random.nextFloat() * .2F, random.nextFloat() * .1F, random.nextFloat() * .1F, true));
                particles.add(new FXAlumentumParticle(world,
                    projectile.getX() + world.random.nextGaussian() * .2F, projectile.getY() + world.random.nextGaussian() * .2F,
                    projectile.getZ() + world.random.nextGaussian() * .2F, 0, 0, 0, 1, 1, 1, false));
            }
        }));
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AlumentumRegistration.TYPE, InvisibleAlumentum::new);
    }
    /** Original RenderNoProjectile renders no model, only the particle trail. */
    private static final class InvisibleAlumentum extends EntityRenderer<EntityAlumentum> {
        InvisibleAlumentum(EntityRendererProvider.Context context) { super(context); shadowRadius = .1F; }
        @Override public ResourceLocation getTextureLocation(EntityAlumentum projectile) { return ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png"); }
    }
}
