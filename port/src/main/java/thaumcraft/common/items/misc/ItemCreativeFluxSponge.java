package thaumcraft.common.items.misc;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.common.entities.EntityFluxRift;
import thaumcraft.common.lib.SoundsTC;

/** BETA26 creative administration item; source label is not a creative-mode access restriction. */
public final class ItemCreativeFluxSponge extends Item {
    public ItemCreativeFluxSponge() { super(new Properties().stacksTo(1).rarity(Rarity.EPIC)); }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> tooltip, TooltipFlag flags) {
        tooltip.add(Component.translatable("thaumcraft.flux_sponge.tooltip.drain").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("thaumcraft.flux_sponge.tooltip.area").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("thaumcraft.flux_sponge.tooltip.rifts").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("thaumcraft.flux_sponge.tooltip.sneak").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("thaumcraft.flux_sponge.tooltip.creative").withStyle(ChatFormatting.DARK_PURPLE));
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            player.swing(hand);
            level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundsTC.craftstart,
                SoundSource.PLAYERS, .15F, 1.0F, false);
        } else {
            int drained = 0;
            BlockPos origin = player.blockPosition();
            for (int x = -CreativeFluxSpongeRules.CHUNK_RADIUS; x <= CreativeFluxSpongeRules.CHUNK_RADIUS; x++) {
                for (int z = -CreativeFluxSpongeRules.CHUNK_RADIUS; z <= CreativeFluxSpongeRules.CHUNK_RADIUS; z++) {
                    drained = CreativeFluxSpongeRules.accumulate(drained, AuraHelper.drainFlux(level,
                        origin.offset(x * 16, 0, z * 16), CreativeFluxSpongeRules.DRAIN_PER_CHUNK, false));
                }
            }
            player.displayClientMessage(Component.translatable("thaumcraft.flux_sponge.drained", drained)
                .withStyle(ChatFormatting.GREEN), false);
            if (player.isShiftKeyDown()) {
                // Source EntityUtils queries a point-centred cube, not distance-to-centre sphere.
                var rifts = level.getEntitiesOfClass(EntityFluxRift.class,
                    new AABB(player.position(), player.position()).inflate(CreativeFluxSpongeRules.RIFT_RANGE));
                for (EntityFluxRift rift : rifts) rift.discard();
                player.displayClientMessage(Component.translatable("thaumcraft.flux_sponge.removed", rifts.size())
                    .withStyle(ChatFormatting.DARK_AQUA), false);
            }
        }
        // Source ItemTCBase does not override Item.use, so it returns PASS after performing the action.
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
}
