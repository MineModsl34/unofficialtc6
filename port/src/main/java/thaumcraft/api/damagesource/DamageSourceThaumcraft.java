package thaumcraft.api.damagesource;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

/** Dynamic-registry counterparts of source damage; dissolve is bypass-armor, NOT magic. */
public final class DamageSourceThaumcraft {
    public static final ResourceKey<DamageType> TAINT = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse("thaumcraft:taint"));
    public static final ResourceKey<DamageType> DISSOLVE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse("thaumcraft:dissolve"));
    public static final ResourceKey<DamageType> SWARM = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse("thaumcraft:swarm"));
    public static final ResourceKey<DamageType> TENTACLE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse("thaumcraft:tentacle"));
    private DamageSourceThaumcraft() {}
    public static DamageSource taint(Level level) { return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TAINT)); }
    public static DamageSource dissolve(Level level) { return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DISSOLVE)); }
    public static DamageSource swarm(Level level) { return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SWARM)); }
    public static DamageSource tentacle(Level level) { return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TENTACLE)); }
    public static DamageSource causeSwarmDamage(net.minecraft.world.entity.LivingEntity attacker) {
        return new DamageSource(attacker.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SWARM), attacker);
    }
    public static DamageSource causeTentacleDamage(net.minecraft.world.entity.LivingEntity attacker) {
        return new DamageSource(attacker.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TENTACLE), attacker);
    }
}
