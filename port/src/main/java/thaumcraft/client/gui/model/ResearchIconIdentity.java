package thaumcraft.client.gui.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.lib.research.LegacyScanIdentity;
import thaumcraft.common.lib.research.LegacyScanIdentity.Identity;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemKind;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemToken;

/**
 * Read-only, finite inverse of the proven legacy ITEM identities used by scanning.
 * This returns a possible rendering identity, NOT an ItemStack, a registry-existence
 * guarantee, a block-state inverse, a recipe resolver or a reward/requirement grant.
 * The renderer must independently check the actual item registry without an AIR fallback.
 * Component/NBT-dependent icons and damaged durable items remain unresolved here.
 */
public final class ResearchIconIdentity {
    private static final ResourceLocation AIR = ResourceLocation.withDefaultNamespace("air");
    private static final Map<LegacyIdentity, ResourceLocation> INVERSE = declaredInverse();

    private ResearchIconIdentity() {}

    /** Returns null rather than guessing an item, metadata variant or component state. */
    public static ResourceLocation resolve(ItemToken token) {
        if (token == null || token.kind() != ItemKind.ITEM || token.count() <= 0
            || token.damage() < 0 || token.rawSnbt() != null || token.legacySnbt() != null
            || !token.ignoredSegments().isEmpty() || token.legacyId() == null
            || !token.legacyId().modernSyntax()) return null;
        var legacy = token.legacyId();
        if (legacy.namespace() == null || legacy.namespace().isEmpty()
            || legacy.path() == null || legacy.path().isEmpty()) return null;
        ResourceLocation id = ResourceLocation.tryParse(legacy.normalized());
        if (id == null) return null;
        return INVERSE.get(new LegacyIdentity(id, token.damage()));
    }

    private static Map<LegacyIdentity, ResourceLocation> declaredInverse() {
        Map<ResourceLocation, Identity> items = new LinkedHashMap<>(LegacyScanIdentity.knownItems());
        // BASICS ORE icon: assets/thaumcraft/research/basics.json:81. Exact same ITEM id:
        // MC1.12 Item.registerItems:1530 and MC1.21 Items.IRON_PICKAXE:996-997.
        // This one explicit undamaged tool is not a broad vanilla same-id fallback.
        ResourceLocation pickaxe = ResourceLocation.withDefaultNamespace("iron_pickaxe");
        items.putIfAbsent(pickaxe, new Identity(pickaxe, 0, true));
        return uniqueInverse(items);
    }

    record LegacyIdentity(ResourceLocation itemId, int metadata) {}

    /** Pure test seam: collisions stay absent even if a third candidate follows them. */
    static Map<LegacyIdentity, ResourceLocation> uniqueInverse(Map<ResourceLocation, Identity> items) {
        Map<LegacyIdentity, ResourceLocation> result = new LinkedHashMap<>();
        Set<LegacyIdentity> ambiguous = new HashSet<>();
        for (var entry : items.entrySet()) {
            ResourceLocation modern = entry.getKey();
            Identity legacy = entry.getValue();
            if (modern == null || legacy == null || !legacy.sourceProven()
                || legacy.itemId() == null || legacy.metadata() < 0
                || AIR.equals(modern) || AIR.equals(legacy.itemId())) continue;
            LegacyIdentity key = new LegacyIdentity(legacy.itemId(), legacy.metadata());
            if (ambiguous.contains(key)) continue;
            ResourceLocation previous = result.putIfAbsent(key, modern);
            if (previous != null && !previous.equals(modern)) {
                result.remove(key);
                ambiguous.add(key);
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
