package thaumcraft.common.lib.capabilities;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;

/**
 * Production attachment factory for TC6 PlayerKnowledge.DefaultImpl. Mutations are not auto-synced.
 * Fresh data is empty; only a non-null NBT load grants metadata marked AUTOUNLOCK, as in TC6.
 */
public final class PlayerKnowledge implements IPlayerKnowledge {
    private final HashSet<String> research = new HashSet<>();
    private final Map<String, Integer> stages = new HashMap<>();
    private final Map<String, HashSet<EnumResearchFlag>> flags = new HashMap<>();
    private final Map<String, Integer> knowledge = new HashMap<>();

    public PlayerKnowledge() {}

    @Override
    public void clear() {
        research.clear();
        flags.clear();
        stages.clear();
        knowledge.clear();
    }

    @Override
    public EnumResearchStatus getResearchStatus(String key) {
        if (!isResearchKnown(key)) {
            return EnumResearchStatus.UNKNOWN;
        }
        // The original lookup is literal: a qualified KEY@N need not have a catalog entry.
        ResearchEntry entry = ResearchCategories.getResearch(key);
        return entry != null && entry.getStages() != null && getResearchStage(key) <= entry.getStages().length
            ? EnumResearchStatus.IN_PROGRESS : EnumResearchStatus.COMPLETE;
    }

    @Override
    public boolean isResearchKnown(String key) {
        if (key == null) {
            return false;
        }
        if (key.equals("")) {
            return true;
        }
        String[] parts = key.split("@");
        return parts.length > 1 && getResearchStage(parts[0]) < parseIntOrZero(parts[1])
            ? false : research.contains(parts[0]);
    }

    private static int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    @Override
    public boolean isResearchComplete(String key) {
        return getResearchStatus(key) == EnumResearchStatus.COMPLETE;
    }

    @Override
    public int getResearchStage(String key) {
        if (key != null && research.contains(key)) {
            Integer stage = stages.get(key);
            return stage == null ? 0 : stage;
        }
        return -1;
    }

    @Override
    public boolean setResearchStage(String key, int stage) {
        if (key != null && research.contains(key) && stage > 0) {
            stages.put(key, stage);
            return true;
        }
        return false;
    }

    @Override
    public boolean addResearch(String key) {
        if (!isResearchKnown(key)) {
            research.add(key);
            return true;
        }
        return false;
    }

    @Override
    public boolean removeResearch(String key) {
        if (isResearchKnown(key)) {
            research.remove(key);
            return true;
        }
        return false;
    }

    @Override
    public Set<String> getResearchList() {
        return Collections.unmodifiableSet(research);
    }

    @Override
    public boolean setResearchFlag(String key, EnumResearchFlag flag) {
        HashSet<EnumResearchFlag> list = flags.get(key);
        if (list == null) {
            list = new HashSet<>();
            flags.put(key, list);
        }
        if (list.contains(flag)) {
            return false;
        }
        list.add(flag);
        return true;
    }

    @Override
    public boolean clearResearchFlag(String key, EnumResearchFlag flag) {
        HashSet<EnumResearchFlag> list = flags.get(key);
        if (list != null) {
            boolean removed = list.remove(flag);
            if (list.isEmpty()) {
                // Source quirk: the set, not the key, is removed. Empty flag buckets remain.
                flags.remove(research);
            }
            return removed;
        }
        return false;
    }

    @Override
    public boolean hasResearchFlag(String key, EnumResearchFlag flag) {
        return flags.get(key) != null && flags.get(key).contains(flag);
    }

    private String getKey(EnumKnowledgeType type, ResearchCategory category) {
        return type.getAbbreviation() + "_" + (category == null ? "" : category.key);
    }

    @Override
    public boolean addKnowledge(EnumKnowledgeType type, ResearchCategory category, int amount) {
        String key = getKey(type, category);
        int current = getKnowledgeRaw(type, category);
        // Ordinary signed int overflow is intentionally retained before the negative check.
        if (current + amount < 0) {
            return false;
        }
        current += amount;
        knowledge.put(key, current);
        return true;
    }

    @Override
    public int getKnowledge(EnumKnowledgeType type, ResearchCategory category) {
        int current = getKnowledgeRaw(type, category);
        return (int) Math.floor((double) current / type.getProgression());
    }

    @Override
    public int getKnowledgeRaw(EnumKnowledgeType type, ResearchCategory category) {
        String key = getKey(type, category);
        return knowledge.containsKey(key) ? knowledge.get(key) : 0;
    }

    @Override
    public void sync(ServerPlayer player) {
        // TC6 constructs the packet from the player's attached data, not necessarily this instance.
        PlayerDataSync.syncKnowledgeNow(player);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag root = new CompoundTag();
        ListTag researchList = new ListTag();
        for (String key : research) {
            CompoundTag tag = new CompoundTag();
            tag.putString("key", key);
            if (stages.containsKey(key)) {
                tag.putInt("stage", stages.get(key));
            }
            if (flags.containsKey(key)) {
                HashSet<EnumResearchFlag> list = flags.get(key);
                if (list != null) {
                    String flagNames = "";
                    for (EnumResearchFlag flag : list) {
                        if (!flagNames.isEmpty()) {
                            flagNames += ",";
                        }
                        flagNames += flag.name();
                    }
                    tag.putString("flags", flagNames);
                }
            }
            researchList.add(tag);
        }
        root.put("research", researchList);
        ListTag knowledgeList = new ListTag();
        for (String key : knowledge.keySet()) {
            Integer amount = knowledge.get(key);
            if (amount != null && amount > 0 && key != null && !key.isEmpty()) {
                CompoundTag tag = new CompoundTag();
                tag.putString("key", key);
                tag.putInt("amount", amount);
                knowledgeList.add(tag);
            }
        }
        root.put("knowledge", knowledgeList);
        return root;
    }

    @Override
    public void deserializeNBT(CompoundTag root) {
        if (root == null) {
            return;
        }
        clear();
        ListTag researchList = root.getList("research", Tag.TAG_COMPOUND);
        for (int i = 0; i < researchList.size(); i++) {
            CompoundTag tag = researchList.getCompound(i);
            String key = tag.getString("key");
            if (key != null && !isResearchKnown(key)) {
                research.add(key);
                int stage = tag.getInt("stage");
                if (stage > 0) {
                    stages.put(key, stage);
                }
                String flagNames = tag.getString("flags");
                if (!flagNames.isEmpty()) {
                    for (String name : flagNames.split(",")) {
                        EnumResearchFlag flag = null;
                        try {
                            flag = EnumResearchFlag.valueOf(name);
                        } catch (Exception ignored) {
                            // TC6 silently drops individual unknown or differently cased enum names.
                        }
                        if (flag != null) {
                            setResearchFlag(key, flag);
                        }
                    }
                }
            }
        }
        ListTag knowledgeList = root.getList("knowledge", Tag.TAG_COMPOUND);
        for (int i = 0; i < knowledgeList.size(); i++) {
            CompoundTag tag = knowledgeList.getCompound(i);
            knowledge.put(tag.getString("key"), tag.getInt("amount"));
        }
        addAutoUnlockResearch();
    }

    private void addAutoUnlockResearch() {
        for (ResearchCategory category : ResearchCategories.researchCategories.values()) {
            for (ResearchEntry entry : category.research.values()) {
                if (entry.hasMeta(ResearchEntry.EnumResearchMeta.AUTOUNLOCK)) {
                    addResearch(entry.getKey());
                }
            }
        }
    }
}
