package thaumcraft.common.items.consumables;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.potions.WarpEffects;
import thaumcraft.common.lib.warp.WarpGameplay;

/** Actual source soap: release after 96 ticks, remove normal/temporary warp, never permanent. */
public final class ItemSanitySoap extends Item {
    public ItemSanitySoap() { super(new Properties()); }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return SanitySoapRules.USE_DURATION;
    }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BLOCK; }
    @Override public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        // In 1.12 EnumAction.BLOCK alone enabled frontal blocking after five ticks, even for soap.
        // Neo1.21 checks SHIELD_BLOCK instead. No durability or other shield actions are added.
        return ability == ItemAbilities.SHIELD_BLOCK;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override public void onUseTick(Level level, LivingEntity owner, ItemStack stack, int remaining) {
        // 1.12 stopActiveHand dispatched onPlayerStoppedUsing; its native equivalent is releaseUsingItem,
        // not stopUsingItem (which would skip the release callback and make soap do nothing).
        if (SanitySoapRules.completes(remaining)) owner.releaseUsingItem();
        if (level.isClientSide) {
            if (level.random.nextFloat() < .2F) {
                level.playLocalSound(owner.getX(), owner.getY(), owner.getZ(),
                    SoundEvents.CHORUS_FLOWER_DEATH, SoundSource.PLAYERS, .1F,
                    1.5F + level.random.nextFloat() * .2F, false);
            }
            bubbles(level, owner, 10, 1.0F);
        }
    }

    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity owner, int remaining) {
        if (!SanitySoapRules.completes(remaining) || !(owner instanceof Player player)) return;
        // Source consumes even in creative and even when both mutable warp values are zero.
        // Native inventory authority is server-side; the source client shrink was only prediction.
        if (!level.isClientSide) {
            stack.shrink(1);
            IPlayerWarp warp = ThaumcraftCapabilities.getWarp(player);
            int amount = SanitySoapRules.normalReduction(player.hasEffect(WarpEffects.WARP_WARD),
                level.getBlockState(player.blockPosition()).getBlock() == BlocksTC.purifyingFluid);
            if (warp.get(EnumWarpType.NORMAL) > 0) WarpGameplay.addWarpToPlayer(player, -amount, EnumWarpType.NORMAL);
            if (warp.get(EnumWarpType.TEMPORARY) > 0) {
                WarpGameplay.addWarpToPlayer(player, -warp.get(EnumWarpType.TEMPORARY), EnumWarpType.TEMPORARY);
            }
        } else {
            level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundsTC.craftstart,
                SoundSource.PLAYERS, .25F, 1.0F, false);
            bubbles(level, player, 40, 1.5F);
        }
    }

    private static void bubbles(Level level, LivingEntity owner, int count, float spread) {
        for (int index = 0; index < count; index++) {
            thaumcraft.common.lib.fx.SourceFX.local(level,count==40?"soap_finish":"soap",new net.minecraft.world.phys.Vec3(
                (float)owner.getX()-.5F+level.random.nextFloat()*spread,(float)owner.getBoundingBox().minY+level.random.nextFloat()*owner.getBbHeight(),
                (float)owner.getZ()-.5F+level.random.nextFloat()*spread),net.minecraft.world.phys.Vec3.ZERO,0,0);
        }
    }
}
