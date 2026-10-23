package thaumcraft.common.lib.events;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;

/** TC6 EntityEvents.entityHurt's personal fire/projectile discoveries. Pre is emitted in
 * actuallyHurt after cooldown acceptance but before absorption/health changes. Its armor
 * and potion reductions do not alter this source hook, which never tests damage amount.
 * This is deliberately not a post-health-loss listener or a research-completion grant. */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class ResearchDamageEvents {
    private ResearchDamageEvents() {}

    @SubscribeEvent
    public static void acceptedDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer owner) || owner.level().isClientSide) return;
        if (event.getSource().is(DamageTypeTags.IS_FIRE)
            && ThaumcraftCapabilities.knowsResearchStrict(owner, "BASEAUROMANCY@2")) {
            discover(owner, "f_onfire", "got.onfire");
        }

        Entity immediate = event.getSource().getDirectEntity();
        if (immediate == null || !ThaumcraftCapabilities.knowsResearchStrict(owner, "FOCUSPROJECTILE@2")) return;
        // The legacy EntityArrow/EntityFireball hierarchies were split in modern vanilla.
        // Match only their finite descendants, as ConfigResearch's entity scans do;
        // AbstractArrow would additionally admit tridents, AbstractHurtingProjectile
        // would additionally admit the much newer wind-charge family.
        if (immediate instanceof Arrow || immediate instanceof SpectralArrow) {
            discover(owner, "f_arrow", "got.projectile");
        }
        if (immediate instanceof Fireball || immediate instanceof DragonFireball || immediate instanceof WitherSkull) {
            discover(owner, "f_fireball", "got.projectile");
        }
        if (immediate instanceof LlamaSpit) {
            discover(owner, "f_spit", "got.projectile");
        }
    }

    private static void discover(ServerPlayer owner, String marker, String message) {
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        if (knowledge.isResearchKnown(marker)) return;
        knowledge.addResearch(marker);
        // Source sends this owner's new marker immediately, before the action-bar message.
        // Do not award observations, flags, stages, or queue an unrelated global sync.
        knowledge.sync(owner);
        owner.displayClientMessage(Component.translatable(message).withStyle(ChatFormatting.DARK_PURPLE), true);
    }
}
