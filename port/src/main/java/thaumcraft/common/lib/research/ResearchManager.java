package thaumcraft.common.lib.research;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchAddendum;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchEvent;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.misc.PacketKnowledgeGain;

/**
 * Trusted, server-owned TC6 progression API, NOT a C2S endpoint or requirement evaluator.
 * Starts, source last-page skipping, every-transition siblings, flags, addenda and XP are retained.
 * A future unresolved stage does not block a safe start. Executed costs require the separate
 * checked service; executed warp/rewards are real, with unresolved later content rejected.
 *
 * Modern safety adaptations: the entire possible sibling execution is projected before our
 * first mutation; malformed, non-progressing/deep recursive graphs and completion loops
 * fail closed. Finite stage-increasing sibling back-edges remain valid. Events remain in source
 * order (a sibling cancellation does NOT roll back its
 * parent). Listener-owned metadata/data changes are retained but invalidate our next commit.
 * Public mutable metadata must be used on its owning thread; no automatic player NBT reloads.
 */
public final class ResearchManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TRANSITIONS = 4096;
    private static final int MAX_SIBLING_DEPTH = 128;
    private static final ThreadLocal<Set<ServerPlayer>> ACTIVE = ThreadLocal.withInitial(
        () -> Collections.newSetFromMap(new IdentityHashMap<>()));
    private static final ThreadLocal<Map<ServerPlayer, Invocation>> INVOCATIONS = ThreadLocal.withInitial(IdentityHashMap::new);
    /** Source global quirk: consumed only by a known completed research with sync=true. */
    public static boolean noFlags;

    private ResearchManager() {}

    public static void addWarpToPlayer(Player player, int amount, IPlayerWarp.EnumWarpType type) {
        thaumcraft.common.lib.warp.WarpGameplay.addWarpToPlayer(player, amount, type);
    }

    /** The one source internal nested unlock; event/addon arbitrary reentry remains rejected. */
    public static boolean completeWarpUnlock(Player player) {
        if (!(player instanceof ServerPlayer owner)) return false;
        requireServerThread(owner);
        var invocation = INVOCATIONS.get().get(owner);
        return invocation == null ? completeResearch(owner, "WARP") : completeActual(owner, "WARP", true, invocation);
    }

    public static boolean addKnowledge(Player player, IPlayerKnowledge.EnumKnowledgeType type,
                                       ResearchCategory category, int amount) {
        if (!(player instanceof ServerPlayer owner)) return false;
        requireServerThread(owner);
        var knowledge = ThaumcraftCapabilities.getKnowledge(player);
        if (!type.hasFields()) category = null;
        if (NeoForge.EVENT_BUS.post(new ResearchEvent.Knowledge(player, type, category, amount)).isCanceled()) {
            return false;
        }
        int before = knowledge.getKnowledge(type, category);
        // Transport bound is a modern safety check: reject an invalid positive-award descriptor
        // after its cancellable event but before mutating raw storage. Never truncate the key.
        PacketKnowledgeGain gainPacket = amount > 0 ? new PacketKnowledgeGain((byte) type.ordinal(),
            category == null ? null : category.key) : null;
        // TC6 deliberately ignores the storage boolean, including refused negative decrements.
        knowledge.addKnowledge(type, category, amount);
        int gained = knowledge.getKnowledge(type, category) - before;
        if (amount > 0) {
            for (int i = 0; i < gained; i++) {
                PlayerDataSync.sendKnowledgeGain(owner, gainPacket);
            }
        }
        PlayerDataSync.markKnowledgeDirty(owner);
        return true;
    }

    public static boolean progressResearch(Player player, String researchKey) {
        return progressResearch(player, researchKey, true);
    }

    public static boolean progressResearch(Player player, String researchKey, boolean sync) {
        if (!(player instanceof ServerPlayer owner) || researchKey == null) return false;
        requireServerThread(owner);
        return invoke(owner, researchKey, sync, false, null, false);
    }

    public static boolean completeResearch(Player player, String researchKey) {
        return completeResearch(player, researchKey, true);
    }

    /** TC6 loop, bounded before any mutation rather than hanging on qualified/negative stages. */
    public static boolean completeResearch(Player player, String researchKey, boolean sync) {
        if (!(player instanceof ServerPlayer owner) || researchKey == null) return false;
        requireServerThread(owner);
        return invoke(owner, researchKey, sync, false, null, true);
    }

    /** Explicit trusted OP-command grant, never a browser/C2S/normal-play entry point.
     * TC6 admin grants bypass stage costs but retain events, warp, rewards and siblings.
     * Malformed graphs and absent reward content still fail preflight rather than invent loot. */
    public static boolean completeResearchAsOperator(ServerPlayer owner, String researchKey, boolean sync) {
        if (owner == null || researchKey == null) return false;
        requireServerThread(owner);
        return invoke(owner, researchKey, sync, false, null, true, true);
    }

    /** ONLY the server requirement service may enter this cost-bearing path. No costless overload. */
    static boolean progressChecked(ServerPlayer owner, String key, boolean sync, int expectedStage,
                                   BooleanSupplier beforeAdvanceCommit) {
        Objects.requireNonNull(beforeAdvanceCommit, "A checked transition requires its cost commit");
        requireServerThread(owner);
        if (key == null || ThaumcraftCapabilities.getKnowledge(owner).getResearchStage(key) != expectedStage) return false;
        return invoke(owner, key, sync, true, beforeAdvanceCommit, false);
    }

    static String checkedTransitionReason(ServerPlayer owner, String key, boolean sync, int expectedStage) {
        requireServerThread(owner);
        IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        if (key == null || knowledge.getResearchStage(key) != expectedStage) return "Stored stage changed";
        return transitionReason(knowledge, key, sync, true, false, false, owner.registryAccess());
    }

    private static boolean invoke(ServerPlayer owner, String key, boolean sync, boolean checked,
                                  BooleanSupplier beforeAdvanceCommit, boolean complete) {
        return invoke(owner, key, sync, checked, beforeAdvanceCommit, complete, false);
    }
    private static boolean invoke(ServerPlayer owner, String key, boolean sync, boolean checked,
                                  BooleanSupplier beforeAdvanceCommit, boolean complete, boolean operatorGrant) {
        Set<ServerPlayer> active = ACTIVE.get();
        // Event-driven reentry on the same owner is a modern cycle guard, not an implicit grant.
        if (!active.add(owner)) return false;
        try {
            IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(owner);
            String reason = transitionReason(knowledge, key, sync, checked, complete, operatorGrant, owner.registryAccess());
            if (reason != null) {
                LOGGER.debug("Research progression for {} deferred: {}", key, reason);
                return false;
            }
            Invocation invocation = new Invocation(captureDescriptorSnapshot(), operatorGrant);
            INVOCATIONS.get().put(owner, invocation);
            return complete ? completeActual(owner, key, sync, invocation)
                : progressActual(owner, key, sync, checked, beforeAdvanceCommit, invocation);
        } finally {
            active.remove(owner);
            INVOCATIONS.get().remove(owner);
            if (INVOCATIONS.get().isEmpty()) INVOCATIONS.remove();
            if (active.isEmpty()) ACTIVE.remove();
        }
    }

    private static final class Invocation {
        final DescriptorSnapshot descriptor;
        final Set<Cursor> path = new HashSet<>();
        int transitions;
        final boolean operatorGrant;
        Invocation(DescriptorSnapshot descriptor, boolean operatorGrant) { this.descriptor = descriptor; this.operatorGrant = operatorGrant; }
    }

    private static boolean completeActual(ServerPlayer owner, String key, boolean sync, Invocation invocation) {
        boolean any = false;
        while (progressActual(owner, key, sync, false, null, invocation)) any = true;
        return any;
    }

    private static boolean progressActual(ServerPlayer owner, String key, boolean sync, boolean checked,
                                          BooleanSupplier beforeAdvanceCommit, Invocation invocation) {
        IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        if (key == null || knowledge.isResearchComplete(key) || !doesPlayerHaveRequisites(owner, key)) return false;
        Cursor cursor = new Cursor(key, knowledge.getResearchStage(key));
        if (!invocation.descriptor.unchanged() || invocation.transitions >= MAX_TRANSITIONS
            || invocation.path.size() >= MAX_SIBLING_DEPTH || !invocation.path.add(cursor)) return false;
        try {
            // Recheck each branch too: a previous sibling listener may have changed player data.
            if (transitionReason(knowledge, key, sync, checked, false, invocation.operatorGrant, owner.registryAccess()) != null) return false;
            ResearchEntry entry = ResearchCategories.getResearch(key);
            OwnerSnapshot baseline = OwnerSnapshot.capture(owner);
            if (NeoForge.EVENT_BUS.post(new ResearchEvent.Research(owner, key)).isCanceled()) return false;
            if (!invocation.descriptor.unchanged() || !baseline.unchanged(owner)
                || ResearchCategories.getResearch(key) != entry || !doesPlayerHaveRequisites(owner, key)) return false;
            LegacyResearchStageTransition.Transition transition = LegacyResearchStageTransition.calculate(entry,
                stageAfterConditionalAdd(knowledge, key));
            if (LegacyResearchStageTransition.executionReason(entry, transition, sync, checked || invocation.operatorGrant, owner.registryAccess()) != null) return false;
            if (beforeAdvanceCommit != null && !beforeAdvanceCommit.getAsBoolean()) return false;
            // Package-private callback contract: only prevalidated raw debits, then true. No event,
            // metadata lookup or rejection point may intervene between that debit and this commit.
            invocation.transitions++;
            boolean completed = applyStateTransition(knowledge, key, entry, transition);
            if (transition.warp() > 0 && !thaumcraft.common.config.ModConfig.CONFIG_MISC.wussMode) {
                int normal = transition.warp() / 2;
                addWarpToPlayer(owner, transition.warp() - normal, IPlayerWarp.EnumWarpType.PERMANENT);
                if (normal > 0) addWarpToPlayer(owner, normal, IPlayerWarp.EnumWarpType.NORMAL);
            }
            if (completed) {
                // TC6 applies warp (and its nested WARP unlock) before these flags/rewards.
                // The global noFlags quirk must therefore be consumed in that source order.
                applyCompletionFlags(knowledge, key, sync);
                if (sync) ResearchCompletionRewards.award(owner, entry);
                notifyAddenda(owner, knowledge, key);
            }
            // Source siblings are OUTSIDE the completion branch: safe FIRSTSTEPS starts matter.
            if (entry != null && entry.getSiblings() != null) {
                for (String sibling : entry.getSiblings()) {
                    if (sibling != null && !knowledge.isResearchComplete(sibling) && doesPlayerHaveRequisites(owner, sibling)) {
                        completeActual(owner, sibling, sync, invocation);
                    }
                }
            }
            if (sync) {
                PlayerDataSync.markKnowledgeDirty(owner);
                if (entry != null) owner.giveExperiencePoints(5);
            }
            return true;
        } finally {
            invocation.path.remove(cursor);
        }
    }

    public static boolean startResearchWithPopup(Player player, String researchKey) {
        boolean started = progressResearch(player, researchKey, true);
        if (started) {
            var knowledge = ThaumcraftCapabilities.getKnowledge(player);
            // Source explicitly sets both after progress, overriding noFlags suppression.
            knowledge.setResearchFlag(researchKey, EnumResearchFlag.POPUP);
            knowledge.setResearchFlag(researchKey, EnumResearchFlag.RESEARCH);
        }
        return started;
    }

    /** Source requisite query checks parents only; it is not a stage requirement/cost check. */
    public static boolean doesPlayerHaveRequisites(Player player, String researchKey) {
        ResearchEntry entry = ResearchCategories.getResearch(researchKey);
        if (entry == null) return true;
        String[] parents = entry.getParentsStripped();
        return parents == null || ThaumcraftCapabilities.knowsResearchStrict(player, parents);
    }

    /** Compatibility query for a fresh START only; player/graph-aware checks use transitionReason. */
    public static String unsupportedReason(ResearchEntry entry) {
        String malformed = LegacyResearchStageTransition.shapeReason(entry);
        return malformed != null ? malformed : LegacyResearchStageTransition.executionReason(entry,
            LegacyResearchStageTransition.calculate(entry, 0), true, false);
    }

    /** Pure preflight, with a private projection: never deserialize/auto-grant AUTOUNLOCK. */
    static String transitionReason(IPlayerKnowledge data, String key, boolean sync, boolean checked, boolean complete) {
        return transitionReason(data, key, sync, checked, complete, false, null);
    }
    private static String transitionReason(IPlayerKnowledge data, String key, boolean sync, boolean checked, boolean complete,
                                           boolean operatorGrant, net.minecraft.core.HolderLookup.Provider registries) {
        try {
            Projection projection = new Projection(data, operatorGrant, registries);
            if (complete) projection.complete(key, sync);
            else projection.progress(key, sync, checked);
            return null;
        } catch (Rejected rejected) {
            return rejected.getMessage();
        } catch (RuntimeException malformed) {
            // Only read-only descriptor/projection work is caught; no actual event/cost failure is hidden.
            return "Malformed research descriptor: " + malformed.getClass().getSimpleName();
        }
    }

    /** Pure data portion shared with bootstrap tests; the executed descriptor is already checked. */
    static boolean advanceSimpleResearch(IPlayerKnowledge knowledge, String key, ResearchEntry entry, boolean sync) {
        var transition = LegacyResearchStageTransition.calculate(entry, stageAfterConditionalAdd(knowledge, key));
        return applyTransition(knowledge, key, entry, transition, sync);
    }

    private static int stageAfterConditionalAdd(IPlayerKnowledge knowledge, String key) {
        return !knowledge.isResearchKnown(key) && !knowledge.getResearchList().contains(key)
            ? 0 : knowledge.getResearchStage(key);
    }

    private static boolean applyTransition(IPlayerKnowledge knowledge, String key, ResearchEntry entry,
                                           LegacyResearchStageTransition.Transition transition, boolean sync) {
        boolean completed = applyStateTransition(knowledge, key, entry, transition);
        if (completed) applyCompletionFlags(knowledge, key, sync);
        return completed;
    }

    private static boolean applyStateTransition(IPlayerKnowledge knowledge, String key, ResearchEntry entry,
                                                LegacyResearchStageTransition.Transition transition) {
        if (!knowledge.isResearchKnown(key)) knowledge.addResearch(key);
        if (entry == null) return false;
        if (transition.writesStage()) knowledge.setResearchStage(key, transition.nextStage());
        return transition.completed();
    }

    private static void applyCompletionFlags(IPlayerKnowledge knowledge, String key, boolean sync) {
        if (sync) {
            knowledge.setResearchFlag(key, EnumResearchFlag.POPUP);
            if (!noFlags) knowledge.setResearchFlag(key, EnumResearchFlag.RESEARCH);
            else noFlags = false;
        }
    }

    private static final class Rejected extends RuntimeException {
        Rejected(String reason) { super(reason); }
    }

    private record Cursor(String key, int stage) {}

    private static final class Projection {
        final Set<String> research;
        final Map<String, Integer> stages = new HashMap<>();
        final Map<String, ResearchEntry> entries = new LinkedHashMap<>();
        final Set<Cursor> path = new HashSet<>();
        int transitions;
        final boolean operatorGrant;

        final net.minecraft.core.HolderLookup.Provider registries;

        Projection(IPlayerKnowledge source, boolean operatorGrant, net.minecraft.core.HolderLookup.Provider registries) {
            this.operatorGrant = operatorGrant;
            this.registries = registries;
            research = new HashSet<>(source.getResearchList());
            for (String key : research) stages.put(key, source.getResearchStage(key));
            for (ResearchCategory category : ResearchCategories.researchCategories.values()) {
                if (category == null || category.research == null) throw new Rejected("Null research category/map");
                for (ResearchEntry entry : category.research.values()) {
                    if (entry == null || entry.getKey() == null) throw new Rejected("Null research entry/key");
                    // The original literal lookup returns the FIRST entry with this key.
                    entries.putIfAbsent(entry.getKey(), entry);
                }
            }
        }

        int stage(String key) { return research.contains(key) ? stages.getOrDefault(key, 0) : -1; }

        boolean known(String key) {
            if (key == null) return false;
            if (key.isEmpty()) return true;
            String[] parts = key.split("@");
            int threshold = 0;
            if (parts.length > 1) {
                try { threshold = Integer.parseInt(parts[1]); }
                catch (NumberFormatException ignored) { /* Source parseIntOrZero. */ }
                if (stage(parts[0]) < threshold) return false;
            }
            return research.contains(parts[0]);
        }

        boolean completed(String key) {
            if (!known(key)) return false;
            ResearchEntry entry = entries.get(key);
            return entry == null || entry.getStages() == null || stage(key) > entry.getStages().length;
        }

        boolean strict(String... keys) {
            for (String key : keys) {
                if (key.contains("&&")) {
                    if (!strict(key.split("&&"))) return false;
                } else if (key.contains("||")) {
                    // Preserve TC6's REGEX split, failed-OR fall-through and early true return.
                    for (String alternative : key.split("||")) if (strict(alternative)) return true;
                } else if (!key.contains("@")) {
                    if (!completed(key)) return false;
                } else if (!known(key)) return false;
            }
            return true;
        }

        boolean parents(String key) {
            ResearchEntry entry = entries.get(key);
            return entry == null || entry.getParentsStripped() == null || strict(entry.getParentsStripped());
        }

        void complete(String key, boolean sync) {
            while (true) {
                Set<String> beforeResearch = new HashSet<>(research);
                Map<String, Integer> beforeStages = new HashMap<>(stages);
                if (!progress(key, sync, false)) return;
                if (!completed(key) && beforeResearch.equals(research) && beforeStages.equals(stages)) {
                    throw new Rejected("Non-progressing completeResearch loop");
                }
            }
        }

        boolean progress(String key, boolean sync, boolean checked) {
            if (key == null) throw new Rejected("Null sibling research key");
            if (completed(key)) return false;
            ResearchEntry entry = entries.get(key);
            String shape = LegacyResearchStageTransition.shapeReason(entry);
            if (shape != null) throw new Rejected(shape);
            if (!parents(key)) return false;
            if (++transitions > MAX_TRANSITIONS) throw new Rejected("Research transition limit exceeded");
            Cursor cursor = new Cursor(key, stage(key));
            if (path.size() >= MAX_SIBLING_DEPTH || !path.add(cursor)) throw new Rejected("Non-progressing/deep recursive sibling graph");
            try {
                if (!known(key)) research.add(key);
                var transition = LegacyResearchStageTransition.calculate(entry, stage(key));
                String reason = LegacyResearchStageTransition.executionReason(entry, transition, sync, checked || operatorGrant, registries);
                if (reason != null) throw new Rejected(reason);
                if (transition.writesStage() && research.contains(key) && transition.nextStage() > 0) stages.put(key, transition.nextStage());
                if (transition.warp() > 0 && !thaumcraft.common.config.ModConfig.CONFIG_MISC.wussMode
                    && strict("FIRSTSTEPS") && !strict("WARP")) complete("WARP", true);
                if (entry != null && entry.getSiblings() != null) {
                    for (String sibling : entry.getSiblings()) {
                        if (sibling == null) throw new Rejected("Null sibling research key");
                        if (!completed(sibling) && parents(sibling)) complete(sibling, sync);
                    }
                }
                return true;
            } finally {
                path.remove(cursor);
            }
        }
    }

    /** Captured on the owner thread; no registry serialization and no reference swaps/rewrites. */
    static DescriptorSnapshot captureDescriptorSnapshot() {
        List<BooleanSupplier> checks = new ArrayList<>();
        var root = ResearchCategories.researchCategories;
        var categories = new LinkedHashMap<>(root);
        var categoryOrder = new ArrayList<>(root.keySet());
        checks.add(() -> ResearchCategories.researchCategories == root && sameMapping(root, categories)
            && new ArrayList<>(root.keySet()).equals(categoryOrder));
        for (ResearchCategory category : categories.values()) {
            if (category == null || category.research == null) {
                checks.add(() -> false);
                continue;
            }
            value(checks, () -> category.key);
            value(checks, () -> category.researchKey);
            value(checks, () -> category.icon);
            value(checks, () -> category.background);
            value(checks, () -> category.background2);
            value(checks, () -> category.minDisplayColumn);
            value(checks, () -> category.maxDisplayColumn);
            value(checks, () -> category.minDisplayRow);
            value(checks, () -> category.maxDisplayRow);
            Object formula = category.formula;
            checks.add(() -> category.formula == formula);
            var map = category.research;
            var entries = new LinkedHashMap<>(map);
            var order = new ArrayList<>(map.keySet());
            checks.add(() -> category.research == map && sameMapping(map, entries) && new ArrayList<>(map.keySet()).equals(order));
            for (ResearchEntry entry : entries.values()) {
                if (entry == null) { checks.add(() -> false); continue; }
                value(checks, entry::getKey);
                value(checks, entry::getCategory);
                value(checks, entry::getName);
                value(checks, entry::getDisplayColumn);
                value(checks, entry::getDisplayRow);
                array(checks, entry::getParents);
                array(checks, entry::getSiblings);
                array(checks, entry::getIcons);
                array(checks, entry::getMeta);
                array(checks, entry::getRewardItem);
                array(checks, entry::getRewardKnow);
                array(checks, entry::getStages);
                if (entry.getStages() != null) for (ResearchStage stage : entry.getStages()) {
                    if (stage == null) { checks.add(() -> false); continue; }
                    value(checks, stage::getText);
                    value(checks, stage::getWarp);
                    array(checks, stage::getObtain);
                    array(checks, stage::getCraft);
                    array(checks, stage::getCraftReference);
                    array(checks, stage::getKnow);
                    array(checks, stage::getResearch);
                    array(checks, stage::getResearchIcon);
                    array(checks, stage::getRecipes);
                }
                array(checks, entry::getAddenda);
                if (entry.getAddenda() != null) for (ResearchAddendum addendum : entry.getAddenda()) {
                    if (addendum == null) { checks.add(() -> false); continue; }
                    value(checks, addendum::getText);
                    array(checks, addendum::getResearch);
                    array(checks, addendum::getRecipes);
                }
            }
        }
        return new DescriptorSnapshot(checks);
    }

    static final class DescriptorSnapshot {
        private final List<BooleanSupplier> checks;
        private DescriptorSnapshot(List<BooleanSupplier> checks) { this.checks = List.copyOf(checks); }
        boolean unchanged() {
            try {
                for (BooleanSupplier check : checks) if (!check.getAsBoolean()) return false;
                return true;
            } catch (RuntimeException malformed) { return false; }
        }
    }

    private static void value(List<BooleanSupplier> checks, Supplier<?> getter) {
        Object original = getter.get();
        checks.add(() -> Objects.equals(original, getter.get()));
    }

    private static boolean sameMapping(Map<?, ?> current, Map<?, ?> captured) {
        if (current.size() != captured.size()) return false;
        for (var pair : captured.entrySet()) if (!current.containsKey(pair.getKey()) || current.get(pair.getKey()) != pair.getValue()) return false;
        return true;
    }

    private interface Stamp { boolean matches(Object current); }

    private static void array(List<BooleanSupplier> checks, Supplier<?> getter) {
        Stamp stamp = stamp(getter.get());
        checks.add(() -> stamp.matches(getter.get()));
    }

    private static Stamp stamp(Object original) {
        if (original == null) return current -> current == null;
        if (original.getClass().isArray()) {
            int length = Array.getLength(original);
            List<Stamp> elements = new ArrayList<>(length);
            for (int i = 0; i < length; i++) elements.add(stamp(Array.get(original, i)));
            return current -> {
                if (current != original || Array.getLength(current) != length) return false;
                for (int i = 0; i < length; i++) if (!elements.get(i).matches(Array.get(current, i))) return false;
                return true;
            };
        }
        if (original instanceof ResearchStage.Knowledge knowledge) {
            var type = knowledge.type;
            var category = knowledge.category;
            String categoryKey = category == null ? null : category.key;
            int amount = knowledge.amount;
            return current -> current == knowledge && knowledge.type == type && knowledge.category == category
                && knowledge.amount == amount && Objects.equals(categoryKey, category == null ? null : category.key);
        }
        if (original instanceof ItemStack stack) {
            ItemStack copy = stack.copy();
            return current -> current == stack && ItemStack.matches(stack, copy);
        }
        if (original instanceof ResearchStage || original instanceof ResearchAddendum) return current -> current == original;
        // Source/Spec strings, enums, resource locations and deferred tokens are immutable;
        // stage/addendum identity is paired with its separately captured mutable fields above.
        return current -> original.equals(current);
    }

    private record OwnerSnapshot(IPlayerKnowledge knowledge, IPlayerWarp warp, CompoundTag knowledgeTag,
                                 CompoundTag warpTag, int level, int total, float progress) {
        static OwnerSnapshot capture(ServerPlayer owner) {
            var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
            var warp = ThaumcraftCapabilities.getWarp(owner);
            return new OwnerSnapshot(knowledge, warp, knowledge.serializeNBT().copy(), warp.serializeNBT().copy(),
                owner.experienceLevel, owner.totalExperience, owner.experienceProgress);
        }
        boolean unchanged(ServerPlayer owner) {
            return ThaumcraftCapabilities.getKnowledge(owner) == knowledge && ThaumcraftCapabilities.getWarp(owner) == warp
                && knowledgeTag.equals(knowledge.serializeNBT()) && warpTag.equals(warp.serializeNBT())
                && owner.experienceLevel == level && owner.totalExperience == total
                && Float.compare(owner.experienceProgress, progress) == 0;
        }
    }

    private static void notifyAddenda(Player player, IPlayerKnowledge knowledge, String key) {
        for (var category : ResearchCategories.researchCategories.values()) {
            for (var entry : category.research.values()) {
                if (entry != null && entry.getAddenda() != null && knowledge.isResearchComplete(entry.getKey())) {
                    for (var addendum : entry.getAddenda()) {
                        if (addendum.getResearch() != null && Arrays.asList(addendum.getResearch()).contains(key)) {
                            player.sendSystemMessage(Component.translatable("tc.addaddendum", entry.getLocalizedName()));
                            knowledge.setResearchFlag(entry.getKey(), EnumResearchFlag.PAGE);
                            break;
                        }
                    }
                }
            }
        }
    }

    private static void requireServerThread(ServerPlayer owner) {
        if (!owner.getServer().isSameThread()) throw new IllegalStateException("Research mutations require the server thread");
    }
}
