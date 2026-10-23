package thaumcraft.common.items.consumables;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.common.entities.projectile.EntityBottleTaint;

public final class ItemBottleTaint extends Item {
    public ItemBottleTaint(){super(new Properties().stacksTo(8));}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        var stack=player.getItemInHand(hand);if(!player.getAbilities().instabuild)stack.shrink(1);
        player.playSound(SoundEvents.EGG_THROW,.5F,.4F/(level.random.nextFloat()*.4F+.8F));
        if(!level.isClientSide){var bottle=new EntityBottleTaint(level,player);bottle.shootFromRotation(player,player.getXRot(),player.getYRot(),-5,.66F,1);level.addFreshEntity(bottle);}
        return InteractionResultHolder.success(stack);
    }
}
