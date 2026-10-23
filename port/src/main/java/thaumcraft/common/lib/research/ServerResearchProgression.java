package thaumcraft.common.lib.research;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchEvent;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.lib.research.catalog.PreparedResearchMetadata;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemKind;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemToken;

/** Server-owned requirement evaluator, NOT a C2S endpoint or browser authorization policy.
 * Unlike TC6 PacketSyncProgressToServer's partial payments/client checks flag, all of OUR
 * costs wait until the requested Research event and every Knowledge event have accepted.
 * Listener-owned changes are retained, but invalidate this attempt. Sibling cancellation
 * occurs after the paid parent commit, as in the source, and does not refund that parent.
 * Obtain items and exact legacy craft bindings use source identities/components/tags.
 * Missing later-phase content and malformed descriptors remain fail-closed; ResearchManager
 * evaluates rewards/warp only for the transition that would actually execute them.
 */
public final class ServerResearchProgression {
    private static final ThreadLocal<Set<ServerPlayer>> ACTIVE = ThreadLocal.withInitial(
        () -> Collections.newSetFromMap(new IdentityHashMap<>()));

    private ServerResearchProgression() {}

    public enum Status {
        APPLIED, INVALID_KEY, STALE_STAGE, COMPLETE, MISSING_PARENTS,
        MISSING_RESEARCH, MISSING_CRAFT, MISSING_ITEM, INSUFFICIENT_KNOWLEDGE, UNSUPPORTED,
        CANCELLED_OR_CHANGED, CHANGED_DURING_EVENTS, CANCELLED_KNOWLEDGE, REENTRANT
    }

    public record Result(Status status, String detail) {
        public boolean applied() { return status == Status.APPLIED; }
    }

    /** expectedStage is exact storage: -1 unknown, 0 known/unstarted, 1+ exposed stage.
     * Neither an opaque scan/craft marker nor a qualified KEY@N can be granted here.
     * Safe starts do not pay for the FUTURE first stage; a known stage0 resume is explicit.
     */
    public static Result tryProgress(ServerPlayer owner, String key, int expectedStage) {
        if (owner == null || owner.getServer() == null || !owner.getServer().isSameThread()) {
            throw new IllegalStateException("Research requirements must run on the owning server thread");
        }
        if (key == null || key.isEmpty() || key.startsWith("!") || key.startsWith("[#]")
            || key.indexOf('@') >= 0 || key.contains("&&") || key.contains("||")) return result(Status.INVALID_KEY, "Exact catalog key required");
        ResearchEntry entry = ResearchCategories.getResearch(key);
        if (entry == null || !key.equals(entry.getKey())) return result(Status.INVALID_KEY, "Unknown catalog key");
        Set<ServerPlayer> active = ACTIVE.get();
        if (!active.add(owner)) return result(Status.REENTRANT, "An owner transaction is already active");
        try {
            IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(owner);
            if (knowledge.getResearchStage(key) != expectedStage) return result(Status.STALE_STAGE, "Stored stage changed");
            if (knowledge.isResearchComplete(key)) return result(Status.COMPLETE, "Already complete");
            if (!ResearchManager.doesPlayerHaveRequisites(owner, key)) return result(Status.MISSING_PARENTS, "Required parent research is not complete");
            String unsupported = ResearchManager.checkedTransitionReason(owner, key, true, expectedStage);
            if (unsupported != null) return result(Status.UNSUPPORTED, unsupported);
            Plan plan;
            try { plan = requirements(owner, entry, expectedStage); }
            catch (Rejected rejected) { return result(rejected.status, rejected.getMessage()); }
            var descriptor = ResearchManager.captureDescriptorSnapshot();
            OwnerBaseline baseline = OwnerBaseline.capture(owner);
            Result[] rejection = {null};
            boolean progressed = ResearchManager.progressChecked(owner, key, true, expectedStage, () -> {
                if (!descriptor.unchanged() || !baseline.unchanged(owner)) {
                    rejection[0] = result(Status.CHANGED_DURING_EVENTS, "Requested Research listener changed catalog or owner data");
                    return false;
                }
                for (Cost cost : plan.events()) {
                    if (NeoForge.EVENT_BUS.post(new ResearchEvent.Knowledge(owner, cost.pool().type(),
                        cost.pool().category(), -cost.amount())).isCanceled()) {
                        rejection[0] = result(Status.CANCELLED_KNOWLEDGE, "Knowledge payment cancelled");
                        return false;
                    }
                    if (!descriptor.unchanged() || !baseline.unchanged(owner)) {
                        rejection[0] = result(Status.CHANGED_DURING_EVENTS, "Knowledge listener changed catalog or owner data");
                        return false;
                    }
                }
                // Rebuild from the current authoritative stage, not a client supplied boolean.
                try {
                    if (knowledge.getResearchStage(key) != expectedStage
                        || !ResearchManager.doesPlayerHaveRequisites(owner, key)
                        || ResearchCategories.getResearch(key) != entry
                        || ResearchManager.checkedTransitionReason(owner, key, true, expectedStage) != null
                        || !plan.equals(requirements(owner, entry, expectedStage))) {
                        rejection[0] = result(Status.CHANGED_DURING_EVENTS, "Requirements changed during events");
                        return false;
                    }
                } catch (Rejected rejected) {
                    rejection[0] = result(Status.CHANGED_DURING_EVENTS, rejected.getMessage());
                    return false;
                }
                // The attached production PlayerKnowledge is final: prechecked positive aggregate
                // debits cannot overflow or underflow. Do not call ResearchManager.addKnowledge:
                // that would post the cancellable payment event twice and allow partial costs.
                for (Cost cost : plan.debits()) {
                    if (!knowledge.addKnowledge(cost.pool().type(), cost.pool().category(), -cost.amount())) {
                        throw new IllegalStateException("Prevalidated knowledge debit was rejected");
                    }
                }
                for (int slot = 0; slot < plan.inventoryDebits().size(); slot++) {
                    int debit = plan.inventoryDebits().get(slot);
                    if (debit > 0) owner.getInventory().items.get(slot).shrink(debit);
                }
                owner.getInventory().setChanged();
                return true; // No more callbacks/events before the requested stage commit.
            });
            return progressed ? result(Status.APPLIED, "Server requirements accepted")
                : rejection[0] != null ? rejection[0] : result(Status.CANCELLED_OR_CHANGED, "Research event cancelled or transition changed");
        } finally {
            active.remove(owner);
            if (active.isEmpty()) ACTIVE.remove();
        }
    }

    private static Result result(Status status, String detail) { return new Result(status, detail); }
    private record Pool(EnumKnowledgeType type, ResearchCategory category) {
        // Canonical category identities have already been checked. Addon equals/hashCode
        // overrides must not merge distinct registered knowledge balances or hide raw pools.
        @Override public boolean equals(Object other) {
            return other instanceof Pool pool && type == pool.type && category == pool.category;
        }
        @Override public int hashCode() {
            return 31 * type.hashCode() + System.identityHashCode(category);
        }
    }
    private record Cost(Pool pool, int amount) {}
    private record Plan(List<Cost> events, List<Cost> debits, List<Integer> inventoryDebits) {
        private Plan { events = List.copyOf(events); debits = List.copyOf(debits); inventoryDebits = List.copyOf(inventoryDebits); }
    }

    private static Plan requirements(ServerPlayer owner, ResearchEntry entry, int storedStage) {
        if (storedStage <= 0 || entry.getStages() == null) return new Plan(List.of(), List.of(), List.of());
        ResearchStage[] stages = entry.getStages();
        if (storedStage > stages.length || stages[storedStage - 1] == null) reject(Status.UNSUPPORTED, "Invalid active stage");
        ResearchStage stage = stages[storedStage - 1];
        if (entry instanceof PreparedResearchMetadata.Entry prepared) {
            // Entry, not Stage subtype, owns the immutable source. Replacing an API stage
            // BEFORE this call must not launder the original catalog costs into a free stage.
            if (stages.length != prepared.rawSpec().stages().values().size()) reject(Status.UNSUPPORTED, "Prepared/API stage count disagrees");
            var raw = prepared.rawSpec().stages().values().get(storedStage - 1);
            if (raw.warp() != stage.getWarp()) reject(Status.UNSUPPORTED, "Raw/API stage warp differs");
            // Mutable API replacement must not erase the immutable catalog's requirements.
            if (!sameTokens(stage.getObtain(), raw.requiredItems().values())
                || !sameTokens(stage.getCraft(), raw.requiredCraft().values())
                || !sameResearch(stage.getResearch(), raw.requiredResearch().values().stream().map(token -> token.key()).toList())
                || !sameKnowledge(stage.getKnow(), raw.requiredKnowledge().values())) {
                reject(Status.UNSUPPORTED, "Prepared/API requirement descriptors disagree");
            }
        }
        IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        int[] inventoryDebits = new int[owner.getInventory().items.size()];
        if (stage.getObtain() != null) for (Object value : stage.getObtain()) {
            ItemToken token = value instanceof ItemToken item ? item : value instanceof String ore
                ? ResearchItemResolver.parse("oredict:" + ore) : null;
            ItemStack wanted = value instanceof ItemStack item ? item : ResearchItemResolver.resolve(token, owner.registryAccess());
            if (wanted == null || wanted.isEmpty()) reject(Status.UNSUPPORTED, "Required source item is absent or malformed");
            int remaining = wanted.getCount();
            for (int slot = 0; slot < inventoryDebits.length && remaining > 0; slot++) {
                ItemStack actual = owner.getInventory().items.get(slot);
                boolean matches = token == null
                    ? ResearchItemResolver.matchesNativeRequirement(wanted, actual)
                    : ResearchItemResolver.matches(token, actual, owner.registryAccess());
                if (!matches) continue;
                int take = Math.min(remaining, actual.getCount() - inventoryDebits[slot]);
                if (take > 0) { inventoryDebits[slot] += take; remaining -= take; }
            }
            if (remaining > 0) reject(Status.MISSING_ITEM, "Insufficient aggregate main-inventory items");
        }
        if (stage.getCraft() != null) {
            for (Object value : stage.getCraft()) {
                Integer reference = value instanceof ItemToken token ? ResearchItemResolver.craftReference(token)
                    : value instanceof ItemStack stack ? ResearchItemResolver.craftReference(stack)
                    : value instanceof String ore ? ResearchItemResolver.craftReference(ResearchItemResolver.parse("oredict:" + ore)) : null;
                if (reference == null) reject(Status.UNSUPPORTED, "Required source craft identity is absent or malformed");
                if (!knowledge.isResearchKnown("[#]" + reference)) reject(Status.MISSING_CRAFT, "Missing real source crafting receipt");
            }
        }
        if (stage.getCraftReference() != null) {
            if (entry instanceof PreparedResearchMetadata.Entry) reject(Status.UNSUPPORTED, "Prepared craft references must derive from immutable source tokens");
            for (int reference : stage.getCraftReference()) if (!knowledge.isResearchKnown("[#]" + reference))
                reject(Status.MISSING_CRAFT, "Missing native addon crafting receipt");
        }
        if (stage.getResearch() != null) {
            for (String required : stage.getResearch()) {
                if (required == null) reject(Status.UNSUPPORTED, "Null required research");
                if (!ThaumcraftCapabilities.knowsResearchStrict(owner, required)) reject(Status.MISSING_RESEARCH, "Required research is incomplete");
            }
        }
        Map<Pool, Integer> amounts = new LinkedHashMap<>();
        List<Cost> orderedEvents = new ArrayList<>();
        if (stage.getKnow() != null) {
            for (ResearchStage.Knowledge required : stage.getKnow()) {
                if (required == null || required.type == null || required.amount <= 0) reject(Status.UNSUPPORTED, "Invalid knowledge descriptor");
                ResearchCategory category = required.type.hasFields() ? required.category : null;
                if (required.type.hasFields() && (category == null || category.key == null
                    || ResearchCategories.getResearchCategory(category.key) != category)) reject(Status.UNSUPPORTED, "Knowledge category is not canonically registered");
                Pool pool = new Pool(required.type, category);
                try {
                    int raw = Math.multiplyExact(required.amount, required.type.getProgression());
                    amounts.put(pool, Math.addExact(amounts.getOrDefault(pool, 0), raw));
                    orderedEvents.add(new Cost(pool, raw));
                } catch (ArithmeticException overflow) { reject(Status.UNSUPPORTED, "Knowledge cost overflow"); }
            }
        }
        List<Cost> costs = new ArrayList<>();
        for (var amount : amounts.entrySet()) {
            Pool pool = amount.getKey();
            if (knowledge.getKnowledgeRaw(pool.type(), pool.category()) < amount.getValue()) reject(Status.INSUFFICIENT_KNOWLEDGE, "Insufficient aggregate raw knowledge");
            costs.add(new Cost(pool, amount.getValue()));
        }
        return new Plan(orderedEvents, costs, java.util.Arrays.stream(inventoryDebits).boxed().toList());
    }

    private static boolean nonempty(Object[] array) { return array != null && array.length > 0; }
    private static boolean sameTokens(Object[] actual, List<ItemToken> expected) {
        if (actual == null) return expected.isEmpty();
        if (actual.length != expected.size()) return false;
        for (int i = 0; i < actual.length; i++) if (!expected.get(i).equals(actual[i])) return false;
        return true;
    }
    private static boolean sameResearch(String[] actual, List<String> expected) {
        return actual == null ? expected.isEmpty() : java.util.Arrays.asList(actual).equals(expected);
    }
    private static boolean sameKnowledge(ResearchStage.Knowledge[] actual,
                                         List<thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.KnowledgeToken> expected) {
        if (actual == null) return expected.isEmpty();
        if (actual.length != expected.size()) return false;
        for (int i = 0; i < actual.length; i++) {
            var value = actual[i]; var token = expected.get(i);
            if (value == null || value.type == null || value.type != token.type()
                || value.amount != token.amount() || value.category != ResearchCategories.getResearchCategory(token.category())) return false;
        }
        return true;
    }

    /** Exposed attachment NBT plus raw pools (NBT omits <=0 balances), inventory and XP.
     * Flags on known/catalog/sibling keys are checked separately because NBT omits orphan flags.
     * This is not private-storage equivalence for arbitrary orphan state outside that graph.
     * No load/deserialization and no rollback of listener-owned mutations. */
    private record OwnerBaseline(IPlayerKnowledge knowledge, IPlayerWarp warp, CompoundTag knowledgeNbt,
                                 CompoundTag warpNbt, ListTag inventory, int selected, int level,
                                 int total, float experience, Map<Pool, Integer> rawPools,
                                 Map<String, Integer> flags) {
        static OwnerBaseline capture(ServerPlayer owner) {
            IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(owner);
            IPlayerWarp warp = ThaumcraftCapabilities.getWarp(owner);
            Map<Pool, Integer> raw = new LinkedHashMap<>();
            Set<String> flagKeys = new LinkedHashSet<>(knowledge.getResearchList());
            for (ResearchCategory category : ResearchCategories.researchCategories.values()) {
                for (ResearchEntry entry : category.research.values()) {
                    flagKeys.add(entry.getKey());
                    if (entry.getSiblings() != null) for (String sibling : entry.getSiblings()) if (sibling != null) flagKeys.add(sibling);
                    if (entry instanceof PreparedResearchMetadata.Entry prepared) flagKeys.addAll(prepared.rawSpec().siblings().values());
                }
            }
            Map<String, Integer> flags = new LinkedHashMap<>();
            for (String key : flagKeys) if (key != null) flags.put(key, flagBits(knowledge, key));
            for (EnumKnowledgeType type : EnumKnowledgeType.values()) {
                raw.put(new Pool(type, null), knowledge.getKnowledgeRaw(type, null));
                for (ResearchCategory category : ResearchCategories.researchCategories.values()) {
                    raw.put(new Pool(type, category), knowledge.getKnowledgeRaw(type, category));
                }
            }
            return new OwnerBaseline(knowledge, warp, knowledge.serializeNBT().copy(), warp.serializeNBT().copy(),
                owner.getInventory().save(new ListTag()), owner.getInventory().selected, owner.experienceLevel,
                owner.totalExperience, owner.experienceProgress, Map.copyOf(raw), Map.copyOf(flags));
        }
        boolean unchanged(ServerPlayer owner) {
            if (ThaumcraftCapabilities.getKnowledge(owner) != knowledge || ThaumcraftCapabilities.getWarp(owner) != warp
                || !knowledgeNbt.equals(knowledge.serializeNBT()) || !warpNbt.equals(warp.serializeNBT())
                || !inventory.equals(owner.getInventory().save(new ListTag())) || selected != owner.getInventory().selected
                || level != owner.experienceLevel || total != owner.totalExperience
                || Float.floatToIntBits(experience) != Float.floatToIntBits(owner.experienceProgress)) return false;
            for (var pool : rawPools.entrySet()) if (knowledge.getKnowledgeRaw(pool.getKey().type(), pool.getKey().category()) != pool.getValue()) return false;
            for (var entry : flags.entrySet()) if (flagBits(knowledge, entry.getKey()) != entry.getValue()) return false;
            return true;
        }
        private static int flagBits(IPlayerKnowledge knowledge, String key) {
            int bits = 0;
            for (EnumResearchFlag flag : EnumResearchFlag.values()) if (knowledge.hasResearchFlag(key, flag)) bits |= 1 << flag.ordinal();
            return bits;
        }
    }

    private static final class Rejected extends RuntimeException {
        final Status status;
        Rejected(Status status, String detail) { super(detail); this.status = status; }
    }
    private static void reject(Status status, String detail) { throw new Rejected(status, detail); }
}
