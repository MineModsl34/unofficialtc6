package thaumcraft.gametest;

import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import thaumcraft.Thaumcraft;
import thaumcraft.api.items.*;
import thaumcraft.common.items.baubles.ItemVerdantCharm;
import thaumcraft.common.entities.projectile.EntityGrapple;
import top.theillusivec4.curios.api.*;

/** Real Curios capability/hook and charged item action, including refusal without payment. */
@GameTestHolder(Thaumcraft.MODID)
public final class ChargedGearAcceptanceGameTests {
    @GameTestGenerator public static Collection<TestFunction> chargedActions(){var tests=new ArrayList<TestFunction>();for(int i=0;i<3;i++){int type=i;tests.add(new TestFunction("bulk_charged_gear","thaumcraft.bulk_verdant_"+i,"thaumcraft:scan_test",100,0,true,h->verdant(h,type)));}tests.add(new TestFunction("bulk_charged_gear","thaumcraft.bulk_grapple_paid","thaumcraft:scan_test",100,0,true,ChargedGearAcceptanceGameTests::grapple));return tests;}
    private static void verdant(GameTestHelper h,int type){var p=BulkGameplayFixtures.player(h);var charm=(ItemVerdantCharm)ItemsTC.charmVerdant;var stack=charm.getTabVariants().get(type);RechargeHelper.rechargeItemBlindly(stack,p,100);var inventory=CuriosApi.getCuriosInventory(p).orElseThrow();inventory.setEquippedCurio("charm",0,stack);var context=new SlotContext("charm",p,0,false,true);p.tickCount=20;
        if(type==0){p.addEffect(new MobEffectInstance(MobEffects.WITHER,200));p.addEffect(new MobEffectInstance(MobEffects.POISON,200));charm.curioTick(context,stack);h.assertTrue(!p.hasEffect(MobEffects.WITHER)&&p.hasEffect(MobEffects.POISON)&&RechargeHelper.getCharge(stack)==80,"real curio hook pays wither first without free poison removal");p.tickCount=40;charm.curioTick(context,stack);h.assertTrue(!p.hasEffect(MobEffects.POISON)&&RechargeHelper.getCharge(stack)==70,"next actual curio pulse pays poison removal");}
        else if(type==1){p.setHealth(10);charm.curioTick(new SlotContext("charm",p,0,true,true),stack);h.assertTrue(p.getHealth()==10&&RechargeHelper.getCharge(stack)==100,"cosmetic curio cannot heal");charm.curioTick(context,stack);h.assertTrue(p.getHealth()==11&&RechargeHelper.getCharge(stack)==95,"life variant pays five real charge for one health");}
        else{p.setAirSupply(80);p.getFoodData().setFoodLevel(12);charm.curioTick(context,stack);h.assertTrue(p.getAirSupply()==300&&p.getFoodData().getFoodLevel()==12&&RechargeHelper.getCharge(stack)==99,"sustain prioritizes paid breath");p.tickCount=40;charm.curioTick(context,stack);h.assertTrue(p.getFoodData().getFoodLevel()==13&&RechargeHelper.getCharge(stack)==98,"next sustain pulse pays hunger restoration");}
        RechargeHelper.consumeCharge(stack,p,RechargeHelper.getCharge(stack));p.setHealth(5);p.tickCount=60;charm.curioTick(context,stack);h.assertTrue(p.getHealth()==5&&RechargeHelper.getCharge(stack)==0,"empty native charge cannot heal");h.succeed();
    }
    private static void grapple(GameTestHelper h){var p=BulkGameplayFixtures.connectedPlayer(h);try{var gun=new ItemStack(ItemsTC.grappleGun);p.setItemInHand(InteractionHand.MAIN_HAND,gun);gun.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND);h.assertTrue(h.getLevel().getEntitiesOfClass(EntityGrapple.class,h.getBounds()).isEmpty(),"uncharged real gun creates no hook");RechargeHelper.rechargeItemBlindly(gun,p,2);gun.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND);var hooks=h.getLevel().getEntitiesOfClass(EntityGrapple.class,h.getBounds());h.assertTrue(hooks.size()==1&&RechargeHelper.getCharge(gun)==1&&thaumcraft.common.items.tools.ItemGrappleGun.loaded(gun),"real item action spawns one native hook and pays one charge");for(var hook:hooks)hook.discard();}finally{BulkGameplayFixtures.removeConnected(p);}h.succeed();}
}
