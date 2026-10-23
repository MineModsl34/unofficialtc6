package thaumcraft.common.lib.research;

import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import thaumcraft.api.research.IScanThing;
import thaumcraft.api.research.ScanningManager;

/** Source ScanPotion: living active effects OR the scan helper's item potion contents.
 * Stores a stable effect key, not a holder/value from an old registry context. Does not apply,
 * consume or award effects/complex research. A thrown potion or arrow ENTITY is not its item;
 * a splash/lingering/tipped-arrow ITEM and an ItemEntity are handled by the original item branch. */
public final class ScanPotion implements IScanThing {
    private final ResourceKey<MobEffect> effectKey;
    private final LegacyPotionScanNames.Name name;

    public ScanPotion(Holder<MobEffect> effect) {
        this(registeredKey(effect), LegacyPotionScanNames.resolve(registeredKey(effect).location(), effect.value().getDescriptionId()));
    }

    /** Explicit addon bridge for an old Potion.getName() value. Opaque names are not normalized;
     * this caller-supplied name is not independently asserted to be an original vanilla fixture. */
    public ScanPotion(ResourceKey<MobEffect> effectKey, String legacyGetName) {
        this(effectKey, new LegacyPotionScanNames.Name(legacyGetName, false));
    }

    private ScanPotion(ResourceKey<MobEffect> effectKey, LegacyPotionScanNames.Name name) {
        this.effectKey = Objects.requireNonNull(effectKey, "effect key");
        if (!effectKey.isFor(Registries.MOB_EFFECT)) throw new IllegalArgumentException("ScanPotion requires a mob-effect registry key");
        this.name = Objects.requireNonNull(name, "effect name");
    }

    private static ResourceKey<MobEffect> registeredKey(Holder<MobEffect> effect) {
        return Objects.requireNonNull(effect, "effect holder").unwrapKey()
            .orElseThrow(() -> new IllegalArgumentException("ScanPotion requires a registered effect holder"));
    }

    public ResourceKey<MobEffect> effectKey() { return effectKey; }
    public boolean sourceProvenName() { return name.sourceProven(); }

    @Override
    public boolean checkThing(Player player, Object object) {
        if (object == null) return false;
        if (object instanceof LivingEntity living) {
            // No fallback to a living entity's held/equipped potion: source used an exclusive branch.
            return matchesEffects(living.getActiveEffects());
        }
        ItemStack stack = ScanningManager.getItemFromParms(player, object);
        return stack != null && !stack.isEmpty()
            && matchesEffects(stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getAllEffects());
    }

    private boolean matchesEffects(Iterable<MobEffectInstance> effects) {
        for (MobEffectInstance effect : effects) if (matchesEffect(effect.getEffect())) return true;
        return false;
    }

    // Pure key check also exercises distinct registry-context holders without game bootstrap.
    boolean matchesEffect(Holder<MobEffect> effect) { return effect.is(effectKey); }

    @Override
    public String getResearchKey(Player player, Object object) { return name.researchKey(); }
}
