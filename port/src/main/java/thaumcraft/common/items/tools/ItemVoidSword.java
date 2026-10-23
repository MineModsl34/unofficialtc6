package thaumcraft.common.items.tools;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import thaumcraft.api.items.IWarpingGear;
import thaumcraft.common.items.TCGearMaterials;

public class ItemVoidSword extends SwordItem implements IWarpingGear {
    public ItemVoidSword() {
        super(TCGearMaterials.TOOLMAT_VOID, new Properties()
            .attributes(SwordItem.createAttributes(TCGearMaterials.TOOLMAT_VOID, 3, -2.4F)));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        // Unlike void armor, the original sword repaired on both logical sides.
        if (stack.isDamaged() && entity instanceof LivingEntity && entity.tickCount % 20 == 0) {
            stack.setDamageValue(stack.getDamageValue() - 1);
        }
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!target.level().isClientSide && (!(target instanceof Player) || !(attacker instanceof Player)
            || target.getServer() != null && target.getServer().isPvpAllowed())) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60));
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public int getWarp(ItemStack stack, Player player) {
        return 1;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("enchantment.special.sapless").withStyle(ChatFormatting.GOLD));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
