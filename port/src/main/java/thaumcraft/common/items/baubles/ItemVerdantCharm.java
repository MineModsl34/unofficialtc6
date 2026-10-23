package thaumcraft.common.items.baubles;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import thaumcraft.api.items.*;
import thaumcraft.common.items.armor.WearableTags;
import thaumcraft.common.lib.potions.WarpEffects;
import top.theillusivec4.curios.api.SlotContext;
/** One original item with three NBT types, not three unrelated recipe identities. */
public final class ItemVerdantCharm extends SourceCurioItem implements IRechargable {
    public ItemVerdantCharm() { super("charm"); }
    public List<ItemStack> getTabVariants() { var out=new java.util.ArrayList<ItemStack>(); for(int type=0;type<3;type++) {var stack=new ItemStack(this); if(type>0){var tag=new CompoundTag();tag.putByte("type",(byte)type);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}out.add(stack);}return List.copyOf(out); }
    @Override public int getMaxCharge(ItemStack stack,LivingEntity wearer) { return 200; }
    @Override public EnumChargeDisplay showInHud(ItemStack stack,LivingEntity wearer) { return EnumChargeDisplay.NORMAL; }
    @Override public void curioTick(SlotContext context,ItemStack stack) {
        if(context.cosmetic() || !(context.entity() instanceof Player player) || player.level().isClientSide || player.tickCount%20!=0) return;
        if(player.hasEffect(MobEffects.WITHER)&&RechargeHelper.consumeCharge(stack,player,20)){player.removeEffect(MobEffects.WITHER);return;}
        if(player.hasEffect(MobEffects.POISON)&&RechargeHelper.consumeCharge(stack,player,10)){player.removeEffect(MobEffects.POISON);return;}
        if(WarpEffects.FLUX_TAINT!=null&&player.hasEffect(WarpEffects.FLUX_TAINT)&&RechargeHelper.consumeCharge(stack,player,5)){player.removeEffect(WarpEffects.FLUX_TAINT);return;}
        int type=WearableTags.verdantType(stack);
        if(type==1&&player.getHealth()<player.getMaxHealth()&&RechargeHelper.consumeCharge(stack,player,5)){player.heal(1);return;}
        if(type==2){ if(player.getAirSupply()<100&&RechargeHelper.consumeCharge(stack,player,1)){player.setAirSupply(300);return;} if(player.canEat(false)&&RechargeHelper.consumeCharge(stack,player,1))player.getFoodData().eat(1,.3F); }
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){int type=WearableTags.verdantType(stack);if(type==1||type==2)tooltip.add(Component.translatable("item.verdant_charm."+(type==1?"life":"sustain")+".text").withStyle(ChatFormatting.GOLD));super.appendHoverText(stack,context,tooltip,flag);}
}
