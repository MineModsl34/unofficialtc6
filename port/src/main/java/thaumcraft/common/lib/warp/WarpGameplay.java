package thaumcraft.common.lib.warp;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.GearHelper;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.lib.network.misc.PacketMiscEvent;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.playerdata.PacketWarpMessage;
import thaumcraft.common.lib.potions.WarpEffects;
import thaumcraft.common.lib.research.ResearchManager;

/** Server-owned source TC6 warp API and PlayerEvents cadence. Never called by a client grant. */
public final class WarpGameplay {
    private WarpGameplay() {}

    public static void addWarpToPlayer(Player player, int amount, EnumWarpType type) {
        if (amount == 0 || !(player instanceof ServerPlayer owner)) return;
        if (!owner.getServer().isSameThread()) throw new IllegalStateException("Warp mutations require the server thread");
        IPlayerWarp data = ThaumcraftCapabilities.getWarp(owner);
        amount = SourceWarpRules.adjustedAmount(data.get(type), amount);
        data.add(type, amount);
        byte messageType = switch (type) {
            case PERMANENT -> 0;
            case NORMAL -> 1;
            case TEMPORARY -> 2;
        };
        PlayerDataSync.sendWarpMessage(owner, new PacketWarpMessage(amount, messageType));
        if (amount > 0) data.setCounter(data.get(EnumWarpType.TEMPORARY)
            + data.get(EnumWarpType.PERMANENT) + data.get(EnumWarpType.NORMAL));
        if (type != EnumWarpType.TEMPORARY && ThaumcraftCapabilities.knowsResearchStrict(owner, "FIRSTSTEPS")
            && !ThaumcraftCapabilities.knowsResearchStrict(owner, "WARP")) {
            ResearchManager.completeWarpUnlock(owner);
            owner.displayClientMessage(Component.translatable("research.WARP.warn"), true);
        }
        data.sync(owner);
    }

    public static void tick(ServerPlayer player) {
        if (SourceWarpRules.shouldCheck(player.tickCount, ModConfig.CONFIG_MISC.wussMode,
                player.hasEffect(WarpEffects.WARP_WARD))) checkWarpEvent(player);
        if (player.tickCount % 20 == 0 && player.hasEffect(WarpEffects.DEATH_GAZE)) checkDeathGaze(player);
    }

    public static void checkWarpEvent(ServerPlayer player) {
        IPlayerWarp data = ThaumcraftCapabilities.getWarp(player);
        addWarpToPlayer(player, -1, EnumWarpType.TEMPORARY);
        int normal = data.get(EnumWarpType.NORMAL), permanent = data.get(EnumWarpType.PERMANENT);
        int actualWarp = permanent + normal;
        int gear = GearHelper.getWarpFromGear(player);
        var random = player.level().getRandom();
        var roll = SourceWarpRules.roll(data.get(EnumWarpType.TEMPORARY), normal, permanent, gear,
            data.getCounter(), fortressMaskZero(player.getInventory().armor.get(3)), random::nextInt);
        if (!roll.occurred()) return;
        data.setCounter(roll.nextCounter());
        PlayerDataSync.sendMiscEvent(player, new PacketMiscEvent(PacketMiscEvent.WARP_EVENT));
        int warp = roll.effectiveWarp(), amp = SourceWarpRules.amplifier(warp);
        switch (roll.event()) {
            case NONE -> { }
            case CREEPER_SOUND -> {
                if (!ModConfig.CONFIG_GRAPHICS.nostress) player.level().playSound(player, player.blockPosition(),
                    SoundEvents.CREEPER_PRIMED, SoundSource.AMBIENT, 1.0F, 0.5F);
            }
            case EXPLOSION_SOUND -> {
                if (!ModConfig.CONFIG_GRAPHICS.nostress) player.level().playSound(player,
                    player.getX() + (random.nextFloat() - random.nextFloat()) * 10.0F,
                    player.getY() + (random.nextFloat() - random.nextFloat()) * 10.0F,
                    player.getZ() + (random.nextFloat() - random.nextFloat()) * 10.0F,
                    SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 4.0F,
                    (1.0F + (random.nextFloat() - random.nextFloat()) * 0.2F) * 0.7F);
            }
            case UNEASE -> message(player, "warp.text.11");
            case VIS_EXHAUST -> { addEffect(player, WarpEffects.VIS_EXHAUST, 5000, amp, true, true, true); message(player, "warp.text.1"); }
            case THAUMARHIA -> { addEffect(player, WarpEffects.THAUMARHIA, Math.min(32000, 10 * warp), 0, true, true, true); message(player, "warp.text.15"); }
            case HUNGER -> { addEffect(player, WarpEffects.UNNATURAL_HUNGER, 5000, amp, true, true, true); message(player, "warp.text.2"); }
            case WATCHED -> message(player, "warp.text.12");
            case MIST_ONE, MIST_FEW, MIST_MANY -> {
                PlayerDataSync.sendMiscEvent(player, new PacketMiscEvent(PacketMiscEvent.MIST_EVENT));
                for (int i = 0; i < SourceWarpRules.guardians(roll.event(), warp); i++) WarpWorldDependencies.spawnGuardian(player);
                message(player, "warp.text.6");
            }
            case BLURRED -> addEffect(player, WarpEffects.BLURRED_VISION, Math.min(32000, 10 * warp), 0, true, true, false);
            case SUN_SCORNED -> { addEffect(player, WarpEffects.SUN_SCORNED, 5000, amp, true, true, true); message(player, "warp.text.5"); }
            case FATIGUE -> { addEffect(player, MobEffects.DIG_SLOWDOWN, 1200, amp, true, true, false); message(player, "warp.text.9"); }
            case INFECTIOUS_EXHAUST -> { addEffect(player, WarpEffects.INFECTIOUS_VIS_EXHAUST, 6000, amp, false, true, true); message(player, "warp.text.1"); }
            case NIGHT_VISION -> { addEffect(player, MobEffects.NIGHT_VISION, Math.min(40 * warp, 6000), 0, true, true, false); message(player, "warp.text.10"); }
            case DEATH_GAZE -> { addEffect(player, WarpEffects.DEATH_GAZE, 6000, amp, true, true, true); message(player, "warp.text.4"); }
            case FALSE_SPIDERS, REAL_SPIDERS -> {
                WarpWorldDependencies.suddenlySpiders(player, warp, roll.event() == SourceWarpRules.Event.REAL_SPIDERS);
                message(player, "warp.text.7");
            }
            case VOICES -> message(player, "warp.text.13");
            case BLINDNESS -> addEffect(player, MobEffects.BLINDNESS, Math.min(32000, 5 * warp), 0, true, true, false);
            case CLARITY -> { if (normal > 0) addWarpToPlayer(player, -1, EnumWarpType.NORMAL); message(player, "warp.text.14"); }
            case STRONG_HUNGER -> { addEffect(player, WarpEffects.UNNATURAL_HUNGER, 6000, amp, true, true, true); message(player, "warp.text.2"); }
            case PORTAL -> { if (WarpWorldDependencies.spawnPortal(player)) message(player, "warp.text.16"); }
        }
        if (actualWarp > 10 && !ThaumcraftCapabilities.knowsResearch(player, "BATHSALTS")
            && !ThaumcraftCapabilities.knowsResearch(player, "!BATHSALTS")) {
            message(player, "warp.text.8");
            ResearchManager.completeResearch(player, "!BATHSALTS");
        }
        if (actualWarp > 25 && !ThaumcraftCapabilities.knowsResearch(player, "ELDRITCHMINOR")) ResearchManager.completeResearch(player, "ELDRITCHMINOR");
        if (actualWarp > 50 && !ThaumcraftCapabilities.knowsResearch(player, "ELDRITCHMAJOR")) ResearchManager.completeResearch(player, "ELDRITCHMAJOR");
        // TC6's early temp mutation sync precedes counter change. Do not fabricate a second sync.
    }

    private static boolean fortressMaskZero(ItemStack helmet) {
        if (helmet.isEmpty()) return false;
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(helmet.getItem());
        if (!key.equals(net.minecraft.resources.ResourceLocation.parse("thaumcraft:fortress_helm"))) return false;
        var custom = helmet.get(DataComponents.CUSTOM_DATA);
        return custom != null && custom.copyTag().contains("mask") && custom.copyTag().getInt("mask") == 0;
    }

    public static void addEffect(LivingEntity player, Holder<MobEffect> effect, int duration, int amplifier,
                                 boolean ambient, boolean visible, boolean nonMilkCurable) {
        MobEffectInstance instance = new MobEffectInstance(effect, duration, amplifier, ambient, visible);
        if (nonMilkCurable) instance.getCures().clear();
        player.addEffect(instance);
    }

    public static void finishedUsingItem(LivingEntity player, ItemStack consumed) {
        if (player.level().isClientSide || !player.hasEffect(WarpEffects.UNNATURAL_HUNGER)) return;
        if (consumed.is(Items.ROTTEN_FLESH) || consumed.getItem() == ItemsTC.brain) {
            MobEffectInstance old = player.getEffect(WarpEffects.UNNATURAL_HUNGER);
            int amplifier = old.getAmplifier() - 1, duration = old.getDuration() - 600;
            player.removeEffect(WarpEffects.UNNATURAL_HUNGER);
            if (duration > 0 && amplifier >= 0) addEffect(player, WarpEffects.UNNATURAL_HUNGER, duration, amplifier, true, false, true);
            player.sendSystemMessage(Component.translatable("warp.text.hunger.2").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC));
        } else if (consumed.has(DataComponents.FOOD)) {
            player.sendSystemMessage(Component.translatable("warp.text.hunger.1").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        }
    }

    public static void checkDeathGaze(ServerPlayer player) {
        MobEffectInstance effect = player.getEffect(WarpEffects.DEATH_GAZE);
        if (effect == null) return;
        int range = Math.min(8 + effect.getAmplifier() * 3, 24);
        Vec3 eye = player.getEyePosition(), axis = player.getLookAngle().scale(range);
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(range), other -> other != player)) {
            Vec3 delta = target.position().add(0, target.getBbHeight() / 2.0F, 0).subtract(eye);
            boolean inCone = delta.dot(axis) / delta.length() / axis.length() > Math.cos(0.75F / 2.0F)
                && delta.dot(axis) / axis.length() < axis.length();
            // Old LivingEntity.canBeCollidedWith meant !isDead; modern canBeCollidedWith is
            // physical block-like collision and defaults false. Its living/picking analogue isPickable.
            if (target.isPickable() && target.isAlive() && inCone && player.hasLineOfSight(target)
                && (!(target instanceof Player) || player.getServer().isPvpAllowed()) && !target.hasEffect(MobEffects.WITHER)) {
                target.setLastHurtByMob(player);
                target.setLastHurtMob(player);
                if (target instanceof Mob mob) mob.setTarget(player);
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 80));
            }
        }
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), true);
    }
}
