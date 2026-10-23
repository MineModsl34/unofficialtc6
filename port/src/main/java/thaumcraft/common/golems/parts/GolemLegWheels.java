package thaumcraft.common.golems.parts;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.RenderShape;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.parts.GolemLeg;

public final class GolemLegWheels implements GolemLeg.ILegFunction {
    /** Entity identity avoids source ID reuse/world-switch leaks. */
    public static final Map<LivingEntity,Float> ani = Collections.synchronizedMap(new WeakHashMap<>());
    @Override public void onUpdateTick(IGolemAPI golem) {
        var world = golem.getGolemWorld(); if (!world.isClientSide) return;
        var entity = golem.getGolemEntity(); double dx = entity.getX() - entity.xo,dy = entity.getY() - entity.yo,dz = entity.getZ() - entity.zo;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float direction = (float)(Math.atan2(dz,dx) * 180 / Math.PI) - 90;
        float rotation = (float)(ani.getOrDefault(entity,0F) + distance / 1.571 * (360 - (entity.getYRot() - direction)));
        if (rotation > 360) rotation -= 360; ani.put(entity,rotation);
        if (entity.onGround() && !entity.isInWater() && distance > .25) {
            var pos = BlockPos.containing(entity.getX(),entity.getY() - .2F,entity.getZ()); var state = world.getBlockState(pos);
            if (state.getRenderShape() != RenderShape.INVISIBLE) world.addParticle(new BlockParticleOption(ParticleTypes.BLOCK,state),
                entity.getX() + (world.random.nextFloat() - .5) * entity.getBbWidth(),entity.getBoundingBox().minY + .1,
                entity.getZ() + (world.random.nextFloat() - .5) * entity.getBbWidth(),-entity.getDeltaMovement().x * 4,1.5,-entity.getDeltaMovement().z * 4);
        }
    }
}
