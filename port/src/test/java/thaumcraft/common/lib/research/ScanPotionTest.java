package thaumcraft.common.lib.research;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import org.junit.jupiter.api.Test;

class ScanPotionTest {
    private static ResourceKey<MobEffect> key(String id) {
        return ResourceKey.create(Registries.MOB_EFFECT, ResourceLocation.parse(id));
    }

    @Test
    void addonBridgeUsesExactGetNameAndAddsOnlySourceExclamation() {
        var scanner = new ScanPotion(key("addon:effect"), "effect.Custom_Name@2");
        assertEquals("!effect.Custom_Name@2", scanner.getResearchKey(null, null));
        assertFalse(scanner.sourceProvenName());
        assertEquals("!", new ScanPotion(key("addon:effect"), "").getResearchKey(null, null));
        assertFalse(scanner.checkThing(null, null));
    }

    @Test
    void differentContextHolderWithSameKeyMatchesWithoutCapturedWorldHolderOrValue() {
        var effect = key("minecraft:speed");
        var scanner = new ScanPotion(effect, "effect.moveSpeed");
        var firstContext = Holder.Reference.createStandAlone(new HolderOwner<MobEffect>() {}, effect);
        var secondContext = Holder.Reference.createStandAlone(new HolderOwner<MobEffect>() {}, effect);
        assertNotSame(firstContext, secondContext);
        assertTrue(scanner.matchesEffect(firstContext));
        assertTrue(scanner.matchesEffect(secondContext));
        assertFalse(scanner.matchesEffect(Holder.Reference.createStandAlone(new HolderOwner<MobEffect>() {}, key("minecraft:poison"))));
        assertFalse(Arrays.stream(ScanPotion.class.getDeclaredFields()).anyMatch(field -> Holder.class.isAssignableFrom(field.getType())));
    }

    @Test
    void unnamedDirectHolderCannotInventARegistryKey() {
        assertThrows(IllegalArgumentException.class, () -> new ScanPotion(Holder.direct(null)));
        assertThrows(NullPointerException.class, () -> new ScanPotion((Holder<MobEffect>) null));
        assertThrows(NullPointerException.class, () -> new ScanPotion(key("addon:effect"), (String) null));
    }
}
