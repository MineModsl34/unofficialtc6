package thaumcraft.common.lib.events;
import java.util.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.*;
import thaumcraft.Thaumcraft;
import thaumcraft.common.items.armor.*;

/** Source Forge armor absorption at the native ARMOR stage. Native enchantment/effect stages remain intact. */
@EventBusSubscriber(modid=Thaumcraft.MODID)
public final class WearableArmorEvents {
    private static final EquipmentSlot[] SLOTS={EquipmentSlot.FEET,EquipmentSlot.LEGS,EquipmentSlot.CHEST,EquipmentSlot.HEAD};
    private WearableArmorEvents(){}
    private static boolean special(ItemStack stack){return stack.getItem() instanceof ItemFortressArmor||stack.getItem() instanceof ItemVoidRobeArmor;}
    private static boolean wears(LivingEntity entity){for(var stack:entity.getArmorSlots())if(special(stack))return true;return false;}
    @SubscribeEvent public static void durability(ArmorHurtEvent event){if(wears(event.getEntity())&&!event.getDamageSource().is(DamageTypeTags.BYPASSES_ARMOR))for(var slot:SLOTS)event.setNewDamage(slot,0);}
    @SubscribeEvent(priority=EventPriority.LOW) public static void armor(LivingIncomingDamageEvent event){
        if(event.isCanceled()||event.getEntity().level().isClientSide||event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)||!wears(event.getEntity()))return;
        event.addReductionModifier(DamageContainer.Reduction.ARMOR,(container,nativeReduction)->{
            LivingEntity wearer=event.getEntity(); var pieces=new ArrayList<SourceSpecialArmorRules.Piece>();var stacks=new ItemStack[4];int fortress=0,masks=0;
            if(wearer instanceof Player)for(int i=1;i<4;i++){var stack=wearer.getItemBySlot(SLOTS[i]);if(stack.getItem() instanceof ItemFortressArmor){fortress++;if(WearableTags.has(stack,"mask"))masks++;}}
            float armor=wearer.getArmorValue(),toughness=(float)wearer.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            boolean magic=event.getSource().is(DamageTypes.MAGIC)||event.getSource().is(DamageTypes.INDIRECT_MAGIC)||event.getSource().is(DamageTypes.THORNS);
            for(int i=0;i<4;i++){var stack=stacks[i]=wearer.getItemBySlot(SLOTS[i]);if(!(stack.getItem() instanceof ArmorItem item))continue;int priority=0;double ratio=0;int maximum=Integer.MAX_VALUE;
                if(special(stack)){ratio=item.getDefense()/25D;maximum=stack.getMaxDamage()+1-stack.getDamageValue();if(magic){priority=1;ratio=item.getDefense()/35D;}else if(item instanceof ItemFortressArmor&&(event.getSource().is(DamageTypeTags.IS_FIRE)||event.getSource().is(DamageTypeTags.IS_EXPLOSION))){priority=1;ratio=item.getDefense()/20D;}if(item instanceof ItemFortressArmor&&fortress>0){armor+=1+masks;toughness++;}}
                pieces.add(new SourceSpecialArmorRules.Piece(i,priority,ratio,maximum));
            }
            float input=container.getNewDamage();var result=SourceSpecialArmorRules.reduce(input,armor,toughness,pieces);
            // Separate source calls preserve Unbreaking rolls, break timing and both passes.
            for(var payment:result.payments()){int i=payment.slot();if(!stacks[i].isEmpty())stacks[i].hurtAndBreak(payment.amount(),wearer,SLOTS[i]);}
            return input-result.damage();
        });
    }
}
