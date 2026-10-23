package thaumcraft.common.lib.events;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.items.*;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.common.items.armor.*;
import thaumcraft.common.items.baubles.*;
import top.theillusivec4.curios.api.CuriosApi;

@EventBusSubscriber(modid=Thaumcraft.MODID)
public final class WearableEvents {
    private static final ResourceLocation STEP=ResourceLocation.parse("thaumcraft:traveller_step");
    private WearableEvents(){}
    public static int equipped(Player player,Class<? extends Item> type){var curios=CuriosApi.getCuriosInventory(player);if(curios.isPresent()){var inventory=curios.get().getEquippedCurios();for(int i=0;i<inventory.getSlots();i++)if(type.isInstance(inventory.getStackInSlot(i).getItem()))return i;}return -1;}
    public static boolean hasGoggles(LivingEntity player){if(!(player instanceof Player))return false;var held=player.getMainHandItem();if(held.getItem() instanceof IGoggles goggles&&goggles.showIngamePopups(held,player))return true;for(var stack:player.getArmorSlots())if(stack.getItem() instanceof IGoggles goggles&&goggles.showIngamePopups(stack,player))return true;var curios=CuriosApi.getCuriosInventory(player);if(curios.isPresent()){var inv=curios.get().getEquippedCurios();for(int i=0;i<inv.getSlots();i++){var stack=inv.getStackInSlot(i);if(stack.getItem() instanceof IGoggles goggles&&goggles.showIngamePopups(stack,player))return true;}}return false;}
    public static int fortressMask(Player player){var stack=player.getItemBySlot(EquipmentSlot.HEAD);return stack.getItem() instanceof ItemFortressArmor&&WearableTags.has(stack,"mask")?WearableTags.integer(stack,"mask"):-1;}
    /** Source ForgeHooks adds special-armor display points on top of vanilla's base. */
    public static int displayedArmor(Player player){int fortress=0,masks=0,voidDefense=0;for(var stack:player.getArmorSlots()){if(stack.getItem() instanceof ItemFortressArmor){fortress++;if(WearableTags.has(stack,"mask"))masks++;}else if(stack.getItem() instanceof ItemVoidRobeArmor robe)voidDefense+=robe.getDefense();}return WearableRules.armorDisplay(player.getArmorValue(),fortress,masks,voidDefense);}
    @SubscribeEvent public static void tick(PlayerTickEvent.Pre event){Player player=event.getEntity();var boots=player.getItemBySlot(EquipmentSlot.FEET);var step=player.getAttribute(Attributes.STEP_HEIGHT);
        if(!(boots.getItem() instanceof ItemBootsTraveller)){if(step!=null)step.removeModifier(STEP);return;}
        if(player.isShiftKeyDown()&&step!=null)step.removeModifier(STEP);
        boolean charge=RechargeHelper.getCharge(boots)>0;
        if(!player.level().isClientSide&&player.tickCount%20==0){int energy=WearableTags.integer(boots,"energy");boolean consume=energy<=0&&RechargeHelper.consumeCharge(boots,player,1);WearableTags.integer(boots,"energy",WearableRules.energy(energy,consume));}
        if(charge&&!player.getAbilities().flying&&player.zza>0){if(player.level().isClientSide&&!player.isShiftKeyDown()&&step!=null){step.removeModifier(STEP);step.addTransientModifier(new AttributeModifier(STEP,1-step.getValue(),AttributeModifier.Operation.ADD_VALUE));}
            if(player.onGround())player.moveRelative(player.isInWater()?.0125F:.05F,new Vec3(0,0,1));else if(player.isInWater())player.moveRelative(.025F,new Vec3(0,0,1));else player.moveRelative(.05F-(player.isSprinting()?.025999999F:.02F),new Vec3(player.xxa,0,player.zza));
        }
    }
    @SubscribeEvent public static void jump(LivingEvent.LivingJumpEvent event){if(event.getEntity() instanceof Player player){var boots=player.getItemBySlot(EquipmentSlot.FEET);if(boots.getItem() instanceof ItemBootsTraveller&&RechargeHelper.getCharge(boots)>0)player.setDeltaMovement(player.getDeltaMovement().add(0,.275F,0));}}
    @SubscribeEvent public static void damage(LivingIncomingDamageEvent event){if(event.getEntity().level().isClientSide)return;
        if(event.getEntity() instanceof Player player&&event.getSource().is(DamageTypes.FALL)){float reduced=WearableRules.fallDamage(event.getAmount(),player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof ItemBootsTraveller,equipped(player,ItemCloudRing.class)>=0);if(reduced<=0){event.setAmount(0);event.setCanceled(true);return;}event.setAmount(reduced);}
        if(event.getSource().getEntity() instanceof Player attacker&&fortressMask(attacker)==2&&attacker.getRandom().nextFloat()<event.getAmount()/12F)attacker.heal(1);
        if(event.getEntity() instanceof Player wearer&&fortressMask(wearer)==1&&event.getSource().getEntity() instanceof LivingEntity attacker&&wearer.getRandom().nextFloat()<event.getAmount()/10F)attacker.addEffect(new MobEffectInstance(MobEffects.WITHER,80));
    }
    @SubscribeEvent public static void experience(PlayerXpEvent.PickupXp event){if(event.isCanceled()||!(event.getEntity() instanceof ServerPlayer player)||equipped(player,ItemCuriosityBand.class)<0||event.getOrb().getValue()<=1)return;
        int removed=event.getOrb().getValue()/2;event.getOrb().value-=removed;float random=player.getRandom().nextFloat();int award=WearableRules.curiosityAward(removed*2,random);if(award==0||ResearchCategories.researchCategories.isEmpty())return;
        var categories=ResearchCategories.researchCategories.values().toArray(new thaumcraft.api.research.ResearchCategory[0]);var category=categories[player.getRandom().nextInt(categories.length)];ThaumcraftApi.internalMethods.addKnowledge(player,award==2?IPlayerKnowledge.EnumKnowledgeType.THEORY:IPlayerKnowledge.EnumKnowledgeType.OBSERVATION,category,1);
    }
    @SubscribeEvent public static void death(LivingDeathEvent event){if(event.isCanceled()||!(event.getEntity() instanceof ServerPlayer player))return;int index=equipped(player,ItemCharmUndying.class);if(index<0)return;var curios=CuriosApi.getCuriosInventory(player);if(curios.isEmpty())return;var inv=curios.get().getEquippedCurios();var charm=inv.getStackInSlot(index).copy();if(inv.extractItem(index,1,false).isEmpty())return;
        player.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));CriteriaTriggers.USED_TOTEM.trigger(player,charm);player.setHealth(1);player.removeAllEffects();player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,900,1));player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,100,1));player.level().broadcastEntityEvent(player,(byte)35);event.setCanceled(true);
    }
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event){var stack=event.getItemStack();var player=event.getEntity();int warp=GearHelper.getFinalWarp(stack,player),discount=GearHelper.getFinalDiscount(stack,player);
        if(warp>0)event.getToolTip().add(net.minecraft.network.chat.Component.translatable("item.warping").append(" "+warp).withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
        if(discount>0)event.getToolTip().add(net.minecraft.network.chat.Component.translatable("tc.visdiscount").append(": "+discount+"%").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
        if(stack.getItem() instanceof IRechargable){int charge=RechargeHelper.getCharge(stack);if(charge>=0)event.getToolTip().add(net.minecraft.network.chat.Component.translatable("tc.charge").append(" "+charge).withStyle(net.minecraft.ChatFormatting.YELLOW));}
    }
}
