package thaumcraft.common.golems.parts;

import net.minecraft.core.particles.ParticleTypes;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.parts.GolemLeg;

/** Source timing, motion and expanding atlas56 flight sprite. */
public final class GolemLegLevitator implements GolemLeg.ILegFunction {
    @Override public void onUpdateTick(IGolemAPI golem) {
        var world = golem.getGolemWorld(); var entity = golem.getGolemEntity();
        if(world.isClientSide&&(!entity.onGround()||entity.tickCount%5==0))thaumcraft.common.lib.fx.SourceFX.local(world,"golem_fly",
            entity.position().add(0,.1,0),new net.minecraft.world.phys.Vec3(world.random.nextGaussian()/100,-.1,world.random.nextGaussian()/100),0,0);
    }
}
