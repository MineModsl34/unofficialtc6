package thaumcraft.common.lib.research;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.lib.research.catalog.PreparedResearchMetadata;

/** Preflight the actual completion only; absent future content cannot become a dropped reward. */
final class ResearchCompletionRewards {
    private ResearchCompletionRewards() {}
    static List<ItemStack> items(ResearchEntry entry) {
        return items(entry, null);
    }
    static List<ItemStack> items(ResearchEntry entry, net.minecraft.core.HolderLookup.Provider registries) {
        var result = new ArrayList<ItemStack>();
        if (entry instanceof PreparedResearchMetadata.Entry prepared) {
            for (var token : prepared.rawSpec().rewardItems().values()) {
                ItemStack stack = ResearchItemResolver.resolve(token, registries);
                if (stack.isEmpty()) throw new IllegalArgumentException("Source reward item is not available: " + token.name());
                result.add(stack);
            }
            if (entry.getRewardItem() != null) {
                if (entry.getRewardItem().length != result.size()) throw new IllegalArgumentException("Raw/API reward item count differs");
                for (int i = 0; i < result.size(); i++) if (!ItemStack.matches(result.get(i), entry.getRewardItem()[i]))
                    throw new IllegalArgumentException("Raw/API reward item differs");
            }
        } else if (entry.getRewardItem() != null) for (ItemStack stack : entry.getRewardItem()) {
            if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Invalid native item reward");
            result.add(stack.copy());
        }
        return result;
    }
    static String reason(ResearchEntry entry) {
        return reason(entry, null);
    }
    static String reason(ResearchEntry entry, net.minecraft.core.HolderLookup.Provider registries) {
        try {
            items(entry, registries);
            var rewards = entry.getRewardKnow();
            if (entry instanceof PreparedResearchMetadata.Entry prepared) {
                var source = prepared.rawSpec().rewardKnowledge().values();
                if ((rewards == null ? 0 : rewards.length) != source.size()) return "Raw/API knowledge reward count differs";
                for (int i = 0; i < source.size(); i++) {
                    var raw = source.get(i); var actual = rewards[i];
                    if (actual == null || actual.type != raw.type() || actual.amount != raw.amount()
                        || !java.util.Objects.equals(actual.category == null ? null : actual.category.key, raw.category()))
                        return "Raw/API knowledge reward differs";
                }
            }
            if (rewards != null) for (ResearchStage.Knowledge reward : rewards) {
                if (reward == null || reward.type == null || reward.amount <= 0) return "Invalid knowledge reward";
                if (reward.type.hasFields() && (reward.category == null
                    || ResearchCategories.getResearchCategory(reward.category.key) != reward.category)) return "Noncanonical knowledge reward category";
                Math.multiplyExact(reward.amount, reward.type.getProgression());
                new thaumcraft.common.lib.network.misc.PacketKnowledgeGain((byte) reward.type.ordinal(), reward.category == null ? null : reward.category.key);
            }
            return null;
        } catch (RuntimeException invalid) { return invalid.getMessage(); }
    }
    static void award(ServerPlayer owner, ResearchEntry entry) {
        for (ItemStack reward : items(entry, owner.registryAccess())) {
            // Modern inventory.add mutates a partially accepted stack: drop only its remainder.
            // Never duplicate the full source descriptor when the inventory accepts part of it.
            if (!owner.getInventory().add(reward) && !reward.isEmpty()) owner.spawnAtLocation(reward, 1F);
        }
        if (entry.getRewardKnow() != null) for (ResearchStage.Knowledge reward : entry.getRewardKnow())
            ResearchManager.addKnowledge(owner, reward.type, reward.category, reward.amount * reward.type.getProgression());
    }
}
