package thaumcraft.api.research;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.capabilities.IPlayerKnowledge;

/** Stage requirements and presentation metadata; this class does not enforce requirements. */
public class ResearchStage {
    String text;
    ResourceLocation[] recipes;
    Object[] obtain;
    Object[] craft;
    int[] craftReference;
    Knowledge[] know;
    String[] research;
    String[] researchIcon;
    int warp;

    public String getText() {
        return text;
    }

    public Component getTextLocalized() {
        return Component.translatable(getText());
    }

    public void setText(String text) {
        this.text = text;
    }

    public ResourceLocation[] getRecipes() {
        return recipes;
    }

    public void setRecipes(ResourceLocation[] recipes) {
        this.recipes = recipes;
    }

    public Object[] getObtain() {
        return obtain;
    }

    public void setObtain(Object[] obtain) {
        this.obtain = obtain;
    }

    public Object[] getCraft() {
        return craft;
    }

    public void setCraft(Object[] craft) {
        this.craft = craft;
    }

    public int[] getCraftReference() {
        return craftReference;
    }

    public void setCraftReference(int[] craftReference) {
        this.craftReference = craftReference;
    }

    public Knowledge[] getKnow() {
        return know;
    }

    public void setKnow(Knowledge[] know) {
        this.know = know;
    }

    public String[] getResearch() {
        return research;
    }

    public void setResearch(String[] research) {
        this.research = research;
    }

    public String[] getResearchIcon() {
        return researchIcon;
    }

    public void setResearchIcon(String[] researchIcon) {
        this.researchIcon = researchIcon;
    }

    public int getWarp() {
        return warp;
    }

    public void setWarp(int warp) {
        this.warp = warp;
    }

    public static class Knowledge {
        public IPlayerKnowledge.EnumKnowledgeType type;
        public ResearchCategory category;
        public int amount = 0;

        public Knowledge(IPlayerKnowledge.EnumKnowledgeType type, ResearchCategory category, int amount) {
            this.type = type;
            this.category = category;
            this.amount = amount;
        }

        /**
         * TC6 parsing semantics, including default-locale uppercasing and invalid enum exceptions.
         * Malformed amounts become zero and reject the specification; keys are not resource IDs.
         */
        public static Knowledge parse(String text) {
            String[] fields = text.split(";");
            if (fields.length == 2) {
                int amount = parseAmount(fields[1]);
                IPlayerKnowledge.EnumKnowledgeType type = IPlayerKnowledge.EnumKnowledgeType.valueOf(fields[0].toUpperCase());
                if (type != null && !type.hasFields() && amount > 0) {
                    return new Knowledge(type, null, amount);
                }
            } else if (fields.length == 3) {
                int amount = parseAmount(fields[2]);
                IPlayerKnowledge.EnumKnowledgeType type = IPlayerKnowledge.EnumKnowledgeType.valueOf(fields[0].toUpperCase());
                ResearchCategory category = ResearchCategories.getResearchCategory(fields[1].toUpperCase());
                if (type != null && category != null && amount > 0) {
                    return new Knowledge(type, category, amount);
                }
            }
            return null;
        }

        private static int parseAmount(String amount) {
            try {
                return Integer.parseInt(amount);
            } catch (Exception ignored) {
                return 0;
            }
        }
    }
}
