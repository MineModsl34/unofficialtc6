package thaumcraft.common.lib.potions;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.common.lib.warp.WarpWorldDependencies;
import thaumcraft.api.damagesource.DamageSourceThaumcraft;
import thaumcraft.api.entities.ITaintedMob;
import net.minecraft.tags.EntityTypeTags;

/** Eight source warp potion definitions, with source per-duration tick cadence and colors. */
public final class WarpEffects {
    public static Holder<MobEffect> VIS_EXHAUST, INFECTIOUS_VIS_EXHAUST, UNNATURAL_HUNGER,
        WARP_WARD, DEATH_GAZE, BLURRED_VISION, SUN_SCORNED, THAUMARHIA, FLUX_TAINT;
    private WarpEffects() {}

    public static void register(RegisterEvent.RegisterHelper<MobEffect> registry) {
        VIS_EXHAUST = register(registry, "vis_exhaust", "potion.vis_exhaust", 6702199, Kind.PASSIVE);
        INFECTIOUS_VIS_EXHAUST = register(registry, "infectious_vis_exhaust", "potion.infvisexhaust", 6706551, Kind.INFECTIOUS);
        UNNATURAL_HUNGER = register(registry, "unnatural_hunger", "potion.unhunger", 4482611, Kind.HUNGER);
        WARP_WARD = register(registry, "warp_ward", "potion.warpward", 14742263, Kind.WARD);
        DEATH_GAZE = register(registry, "death_gaze", "potion.deathgaze", 6702131, Kind.PASSIVE);
        BLURRED_VISION = register(registry, "blurred_vision", "potion.blurred", 8421504, Kind.PASSIVE);
        SUN_SCORNED = register(registry, "sun_scorned", "potion.sunscorned", 16308330, Kind.SUN);
        THAUMARHIA = register(registry, "thaumarhia", "potion.thaumarhia", 6702199, Kind.THAUMARHIA);
        FLUX_TAINT = register(registry,"flux_taint","potion.flux_taint",6697847,Kind.TAINT);
    }

    private static Holder<MobEffect> register(RegisterEvent.RegisterHelper<MobEffect> registry, String key,
                                              String name, int color, Kind kind) {
        MobEffect effect = new SourceEffect(name, color, kind);
        registry.register(ResourceLocation.fromNamespaceAndPath("thaumcraft", key), effect);
        return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
    }

    private enum Kind { PASSIVE, WARD, INFECTIOUS, HUNGER, SUN, THAUMARHIA, TAINT }

    private static final class SourceEffect extends MobEffect {
        private final String name;
        private final Kind kind;
        SourceEffect(String name, int color, Kind kind) {
            super(kind == Kind.WARD ? MobEffectCategory.BENEFICIAL : MobEffectCategory.HARMFUL, color);
            this.name = name;
            this.kind = kind;
        }
        @Override public String getDescriptionId() { return name; }
        @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return switch (kind) {
                case HUNGER -> true;
                case INFECTIOUS, SUN -> duration % 40 == 0;
                case THAUMARHIA -> duration % 20 == 0;
                case TAINT -> (40 >> amplifier) <= 0 || duration % (40 >> amplifier) == 0;
                default -> false;
            };
        }
        @Override public boolean applyEffectTick(LivingEntity target, int amplifier) {
            if (target.level().isClientSide) return true;
            switch (kind) {
                case HUNGER -> {
                    if (target instanceof Player player) player.causeFoodExhaustion(0.025F * (amplifier + 1));
                }
                case INFECTIOUS -> {
                    for (LivingEntity nearby : target.level().getEntitiesOfClass(LivingEntity.class,
                            target.getBoundingBox().inflate(4.0))) {
                        if (!nearby.hasEffect(INFECTIOUS_VIS_EXHAUST)) {
                            nearby.addEffect(new MobEffectInstance(amplifier > 0 ? INFECTIOUS_VIS_EXHAUST : VIS_EXHAUST,
                                6000, amplifier > 0 ? amplifier - 1 : 0, false, true));
                        }
                    }
                }
                case SUN -> {
                    float brightness = target.getLightLevelDependentMagicValue();
                    if (brightness > 0.5F && target.level().getRandom().nextFloat() * 30.0F < (brightness - 0.4F) * 2.0F
                        && target.level().canSeeSky(target.blockPosition())) target.igniteForSeconds(4.0F);
                    else if (brightness < 0.25F && target.level().getRandom().nextFloat() > brightness * 2.0F) target.heal(1.0F);
                }
                case THAUMARHIA -> {
                    if (target.level().getRandom().nextInt(15) == 0 && target.level().isEmptyBlock(target.blockPosition())) {
                        WarpWorldDependencies.placeFluxGoo(target);
                    }
                }
                case TAINT -> {
                    if(target instanceof ITaintedMob) target.heal(1);
                    else if(!target.getType().is(EntityTypeTags.UNDEAD)) target.hurt(DamageSourceThaumcraft.taint(target.level()),1);
                }
                default -> { }
            }
            return true;
        }
    }
}
