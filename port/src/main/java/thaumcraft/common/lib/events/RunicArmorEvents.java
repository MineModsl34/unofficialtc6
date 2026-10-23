package thaumcraft.common.lib.events;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.lib.crafting.InfusionRunicAugmentRecipe;
import thaumcraft.common.world.aura.AuraHandler;
import top.theillusivec4.curios.api.CuriosApi;

/** Source PlayerEvents.handleRunicArmor: 20 tick gear scan, millisecond wait/recharge and vis payment.
 * 1.21 clamps absorption to MAX_ABSORPTION: the transient modifier is an engine adapter, not
 * additional free absorption. UUID/logout cleanup prevents original entity-id reuse leakage.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class RunicArmorEvents {
    private static final ResourceLocation MODIFIER = ResourceLocation.parse("thaumcraft:runic_shielding");
    private static final Map<UUID,Integer> maxima = new HashMap<>(), lastCharge = new HashMap<>();
    private static final Map<UUID,Long> nextCycle = new HashMap<>();
    private RunicArmorEvents() {}
    public static boolean rendersShield(Player player){return (int)player.getAbsorptionAmount()>0&&maxima.containsKey(player.getUUID());}
    @SubscribeEvent public static void shield(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre event){
        if(event.getEntity() instanceof Player player&&!player.level().isClientSide&&rendersShield(player))thaumcraft.common.lib.fx.SourceFX.shield(player,event.getSource());
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        Player player = event.getEntity(); if (player.level().isClientSide) return;
        UUID id = player.getUUID();
        if (player.tickCount % 20 == 0) {
            int maximum = 0;
            for (ItemStack armor : player.getInventory().armor) maximum += InfusionRunicAugmentRecipe.getRunicCharge(armor);
            var curios = CuriosApi.getCuriosInventory(player);
            if (curios.isPresent()) { var equipped = curios.get().getEquippedCurios(); for (int slot = 0; slot < equipped.getSlots(); slot++) maximum += InfusionRunicAugmentRecipe.getRunicCharge(equipped.getStackInSlot(slot)); }
            int previous = maxima.getOrDefault(id,0);
            if (previous > maximum) player.setAbsorptionAmount(player.getAbsorptionAmount() - (previous - maximum));
            var attribute = player.getAttribute(Attributes.MAX_ABSORPTION);
            if (attribute != null) {
                var modifier = attribute.getModifier(MODIFIER);
                if (modifier != null && modifier.amount() != Math.max(0,maximum)) attribute.removeModifier(MODIFIER);
                if (maximum > 0 && !attribute.hasModifier(MODIFIER)) attribute.addTransientModifier(new AttributeModifier(MODIFIER,maximum,AttributeModifier.Operation.ADD_VALUE));
            }
            if (maximum > 0) maxima.put(id,maximum); else maxima.remove(id);
        }
        Integer maximum = maxima.get(id); if (maximum == null) return;
        long now = System.currentTimeMillis(); int charge = (int)player.getAbsorptionAmount();
        if (charge == 0 && lastCharge.getOrDefault(id,0) > 0) { nextCycle.put(id,now + ModConfig.CONFIG_MISC.shieldWait); lastCharge.put(id,0); }
        if (charge < maximum && nextCycle.getOrDefault(id,0L) < now && !AuraHandler.shouldPreserveAura(player.level(),player,player.blockPosition())
            && AuraHelper.getVis(player.level(),player.blockPosition()) >= ModConfig.CONFIG_MISC.shieldCost) {
            AuraHelper.drainVis(player.level(),player.blockPosition(),ModConfig.CONFIG_MISC.shieldCost,false);
            nextCycle.put(id,now + ModConfig.CONFIG_MISC.shieldRecharge); player.setAbsorptionAmount(charge + 1); lastCharge.put(id,charge + 1);
        }
    }
    private static void clear(Player player) { UUID id = player.getUUID(); maxima.remove(id); lastCharge.remove(id); nextCycle.remove(id); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) { clear(event.getEntity()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { maxima.clear(); lastCharge.clear(); nextCycle.clear(); }
}
