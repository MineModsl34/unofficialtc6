package thaumcraft.common.golems;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.golems.ISealDisplayer;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.seals.SealPos;
import thaumcraft.common.golems.seals.SealHandler;
import thaumcraft.common.lib.SoundsTC;

public final class ItemGolemBell extends Item implements ISealDisplayer {
    public ItemGolemBell() { super(new Properties().stacksTo(1)); }
    public static ISealEntity getSeal(Player player) { return SealHandler.getSealRayTrace(player.level(),player,5); }
    private boolean operate(ServerPlayer player,ISealEntity seal) {
        var pos = seal.getSealPos(); if (!player.level().mayInteract(player,pos.pos)) return false;
        if (player.isShiftKeyDown()) {
            // SealHandler validates ownership/locking through the supplied player, not untrusted client identity.
            if (!SealHandler.canPlayerEdit(player,seal)) return false;
            SealHandler.removeSealEntity(player.level(),pos,false);
            player.level().playSound(null,pos.pos,SoundsTC.zap,SoundSource.BLOCKS,.5F,1); return true;
        }
        return SealHandler.openMenu(player,pos);
    }
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context) {
        var player = context.getPlayer(); if (player == null) return InteractionResult.PASS; player.swing(context.getHand());
        if (player instanceof ServerPlayer server) {
            var seal = SealHandler.getSealEntity(player.level().dimension(),new SealPos(context.getClickedPos(),context.getClickedFace()));
            if (seal != null) return operate(server,seal) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            if (player.isShiftKeyDown() && ThaumcraftCapabilities.knowsResearch(player,"GOLEMLOGISTICS")) return SealHandler.openLogisticsMenu(server,context.getClickedPos()) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        player.swing(hand);
        if (level.isClientSide) player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP,.6F,1 + level.random.nextFloat() * .1F);
        else if (player instanceof ServerPlayer server) {
            var seal = getSeal(player); if (seal != null) { operate(server,seal); return InteractionResultHolder.fail(player.getItemInHand(hand)); }
            if (player.isShiftKeyDown() && ThaumcraftCapabilities.knowsResearch(player,"GOLEMLOGISTICS")) { SealHandler.openLogisticsMenu(server,player.blockPosition()); return InteractionResultHolder.fail(player.getItemInHand(hand)); }
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
}
