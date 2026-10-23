package thaumcraft.common.lib.research;

import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;

/**
 * Finite TC6 crafting-reference bridge for the two current THEORYRESEARCH requirements.
 * Source ResearchManager.createItemStackHash normalizes count to one and hashes MC1.12's
 * ItemStack.toString: count + "x" + item.getTranslationKey() + "@" + damage. That string
 * does NOT contain NBT. Modern ItemStack descriptions, registry IDs and recipe IDs are
 * deliberately not used as substitute hashes. This is not a general legacy item/NBT resolver.
 */
public final class LegacyResearchCrafting {
    public static final Descriptor SCRIBING_TOOLS =
        new Descriptor("thaumcraft:scribing_tools", 0, "item.scribing_tools");
    public static final Descriptor RESEARCH_TABLE =
        new Descriptor("thaumcraft:research_table", 0, "tile.research_table");
    public static final List<Descriptor> REFERENCES = List.of(SCRIBING_TOOLS, RESEARCH_TABLE);

    private LegacyResearchCrafting() {}

    /** Pure compatibility metadata; only REFERENCES participates in production matching. */
    public record Descriptor(String legacyItemId, int legacyDamage, String legacyTranslationKey) {
        public String canonicalString() { return "1x" + legacyTranslationKey + "@" + legacyDamage; }
        public int hash() { return canonicalString().hashCode(); }
        public String marker() { return "[#]" + hash(); }
        public boolean matchesDamage(int damage) { return damage == legacyDamage; }
    }

    /** Exact, finite legacy requirement lookup; no wildcard or modern registry-name guessing. */
    public static Optional<Descriptor> reference(String legacyItemId, int damage) {
        return REFERENCES.stream().filter(d -> d.legacyItemId().equals(legacyItemId) && d.matchesDamage(damage)).findFirst();
    }

    /** Runtime lookup: public TC fields may still be null during registration. Never cache them. */
    public static Optional<Descriptor> reference(ItemStack crafted) {
        if (crafted == null || crafted.isEmpty()) return Optional.empty();
        // NeoForge getDamageValue clamps to [0,maxDamage], including every non-durable
        // table value to zero. Old crafting references distinguish the raw metadata instead.
        int damage = LegacyScanIdentity.rawDamage(crafted);
        if (ItemsTC.scribingTools != null && crafted.is(ItemsTC.scribingTools)
            && SCRIBING_TOOLS.matchesDamage(damage)) return Optional.of(SCRIBING_TOOLS);
        Item table = BlocksTC.researchTable == null ? null : BlocksTC.researchTable.asItem();
        if (table != null && table != Items.AIR && crafted.is(table)
            && RESEARCH_TABLE.matchesDamage(damage)) return Optional.of(RESEARCH_TABLE);
        return Optional.empty();
    }

    /** No unlock prerequisite: the source records crafting even before research becomes visible. */
    public static boolean recordCrafting(ServerPlayer owner, ItemStack crafted) {
        if (owner == null) return false;
        Integer hash = ResearchItemResolver.craftReference(crafted);
        boolean recorded = false;
        // As in source, only declared craft requirements produce trusted receipt markers.
        for (var category : thaumcraft.api.research.ResearchCategories.researchCategories.values()) {
            for (var entry : category.research.values()) {
                if (entry.getStages() == null) continue;
                for (var stage : entry.getStages()) {
                    if (stage.getCraft() != null) for (Object requirement : stage.getCraft()) {
                        Integer wanted = requirement instanceof thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemToken token
                            ? ResearchItemResolver.craftReference(token)
                            : requirement instanceof ItemStack stack ? ResearchItemResolver.craftReference(stack)
                            : requirement instanceof String ore ? ResearchItemResolver.craftReference(ResearchItemResolver.parse("oredict:" + ore)) : null;
                        boolean matches = wanted != null && wanted.equals(hash);
                        if (requirement instanceof thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemToken token
                            && token.kind() == thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemKind.OREDICT)
                            matches = wanted != null && ResearchItemResolver.matches(token, crafted);
                        if (requirement instanceof String ore)
                            matches = wanted != null && ResearchItemResolver.matches(ResearchItemResolver.parse("oredict:" + ore), crafted);
                        if (matches) recorded |= ResearchManager.completeResearch(owner, "[#]" + wanted);
                    }
                    if (!(entry instanceof thaumcraft.common.lib.research.catalog.PreparedResearchMetadata.Entry)
                        && hash != null && stage.getCraftReference() != null) {
                        for (int wanted : stage.getCraftReference()) if (hash == wanted)
                            recorded |= ResearchManager.completeResearch(owner, "[#]" + wanted);
                    }
                }
            }
        }
        return recorded;
    }
}
