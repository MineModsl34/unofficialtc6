package thaumcraft.api.research;

import java.util.Arrays;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Mutable common metadata, not research progress. TC6's nullable arrays and direct references
 * are retained: in particular a null stage array is not equivalent to an empty stage array.
 */
public class ResearchEntry {
    String key;
    String category;
    String name;
    String[] parents;
    String[] siblings;
    int displayColumn;
    int displayRow;
    Object[] icons;
    EnumResearchMeta[] meta;
    ItemStack[] rewardItem;
    ResearchStage.Knowledge[] rewardKnow;
    ResearchStage[] stages;
    ResearchAddendum[] addenda;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public Component getLocalizedName() {
        return Component.translatable(getName());
    }

    public void setName(String name) {
        this.name = name;
    }

    public String[] getParents() {
        return parents;
    }

    public String[] getParentsClean() {
        String[] result = getParentsStripped();
        if (result != null) {
            for (int i = 0; i < result.length; i++) {
                if (result[i].contains("@")) {
                    result[i] = result[i].substring(0, result[i].indexOf('@'));
                }
            }
        }
        return result;
    }

    public String[] getParentsStripped() {
        if (parents == null) {
            return null;
        }
        String[] result = new String[parents.length];
        for (int i = 0; i < result.length; i++) {
            // TC6 coerces a null parent to the literal "null" before stripping a single '~'.
            result[i] = "" + parents[i];
            if (result[i].startsWith("~")) {
                result[i] = result[i].substring(1);
            }
        }
        return result;
    }

    public void setParents(String[] parents) {
        this.parents = parents;
    }

    public String[] getSiblings() {
        return siblings;
    }

    public void setSiblings(String[] siblings) {
        this.siblings = siblings;
    }

    public int getDisplayColumn() {
        return displayColumn;
    }

    public void setDisplayColumn(int displayColumn) {
        this.displayColumn = displayColumn;
    }

    public int getDisplayRow() {
        return displayRow;
    }

    public void setDisplayRow(int displayRow) {
        this.displayRow = displayRow;
    }

    public Object[] getIcons() {
        return icons;
    }

    public void setIcons(Object[] icons) {
        this.icons = icons;
    }

    public EnumResearchMeta[] getMeta() {
        return meta;
    }

    public boolean hasMeta(EnumResearchMeta value) {
        return meta != null && Arrays.asList(meta).contains(value);
    }

    public void setMeta(EnumResearchMeta[] meta) {
        this.meta = meta;
    }

    public ResearchStage[] getStages() {
        return stages;
    }

    public void setStages(ResearchStage[] stages) {
        this.stages = stages;
    }

    public ItemStack[] getRewardItem() {
        return rewardItem;
    }

    public void setRewardItem(ItemStack[] rewardItem) {
        this.rewardItem = rewardItem;
    }

    public ResearchStage.Knowledge[] getRewardKnow() {
        return rewardKnow;
    }

    public void setRewardKnow(ResearchStage.Knowledge[] rewardKnow) {
        this.rewardKnow = rewardKnow;
    }

    public ResearchAddendum[] getAddenda() {
        return addenda;
    }

    public void setAddenda(ResearchAddendum[] addenda) {
        this.addenda = addenda;
    }

    public enum EnumResearchMeta {
        ROUND,
        SPIKY,
        REVERSE,
        HIDDEN,
        AUTOUNLOCK,
        HEX
    }
}
