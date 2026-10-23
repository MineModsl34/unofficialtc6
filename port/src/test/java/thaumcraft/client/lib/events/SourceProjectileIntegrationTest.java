package thaumcraft.client.lib.events;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Lightweight actual producer/consumer closure; no client bootstrap or visual acceptance. */
class SourceProjectileIntegrationTest {
    @Test void cloudHasBothSourceBranchesAndSeparateRandomOwners() throws Exception {
        String cloud=source("common/entities/projectile/EntityFocusCloud.java");assertTrue(cloud.contains("effects[random.nextInt(effects.length)]"));assertTrue(cloud.contains("var worldRandom=level().random"));assertTrue(cloud.contains("\"focus_cloud_base\""));assertTrue(cloud.contains("/2.0*.85"));assertTrue(cloud.contains("effect.renderParticleFX(level()"));
        String receiver=source("client/lib/fx/SourceProjectileFX.java");assertTrue(receiver.contains("case \"focus_cloud_base\"->fx.drawFocusCloudParticle"));assertTrue(receiver.contains("case \"focus_projectile_mote\"->fx.drawFireMote"));
        String registration=source("client/CasterClientRegistration.java");assertTrue(registration.contains("RenderNoProjectile::new"));
    }
    @Test void sevenImpactProducersAndCombinedTouchDoNotInventBurstEngineSender() throws Exception {
        String effect=source("common/items/casters/foci/FocusEffectRuntime.java");assertTrue(effect.contains("impact(effect,level,hit)"));assertTrue(effect.contains("Kind.FOCUS_IMPACT"));assertTrue(effect.contains("Vec3.atCenterOf(block.getBlockPos())"));assertTrue(effect.contains("Collectors.joining(\"%\")"));assertFalse(effect.contains("trail(effect,level,pos,Vec3.ZERO)"));assertFalse(effect.contains("Kind.FOCUS_BURST"));
        String touch=source("common/items/casters/foci/FocusMediumTouch.java");assertTrue(touch.contains("trail(getPackage().getFocusEffects()"));assertFalse(source("api/casters/FocusEngine.java").contains("FOCUS_BURST"));
    }
    @Test void allFiveGolemEventsUseActualParticleDispatcherNotRenderTimeBillboards() throws Exception {
        String emotes=source("common/golems/client/GolemEmotes.java");for(int id=5;id<=9;id++)assertTrue(emotes.contains("case "+id+"->"));assertTrue(emotes.contains("fx.drawGenericParticles"));assertFalse(emotes.contains("WeakHashMap"));assertFalse(emotes.contains("VertexConsumer"));assertFalse(source("common/golems/client/RenderThaumcraftGolem.java").contains("GolemEmotes.render"));
        assertTrue(source("common/golems/client/Golem9BuilderClient.java").contains("CLIENT_STATUS = GolemEmotes::status"));assertTrue(source("common/golems/EntityThaumcraftGolem.java").contains("CLIENT_STATUS.accept(this, event)"));
    }
    private static String source(String path) throws Exception {return Files.readString(Path.of("src/main/java/thaumcraft/"+path));}
}
