package thaumcraft.common.entities.monster.mods;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Common gameplay API. Client FX/layers live in a client-only subscriber, not this interface. */
@FunctionalInterface
public interface IChampionModifierEffect {
    float performEffect(LivingEntity mob, LivingEntity target, DamageSource source, float amount);
}
