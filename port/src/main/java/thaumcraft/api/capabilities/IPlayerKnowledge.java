package thaumcraft.api.capabilities;

import java.util.Set;
import javax.annotation.Nonnull;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.INBTSerializable;
import thaumcraft.api.research.ResearchCategory;

/** TC6 personal knowledge API, with a registry-provider bridge for NeoForge attachments. */
public interface IPlayerKnowledge extends INBTSerializable<CompoundTag> {
    void clear();
    EnumResearchStatus getResearchStatus(@Nonnull String research);
    boolean isResearchComplete(String research);
    boolean isResearchKnown(String research);
    int getResearchStage(@Nonnull String research);
    boolean addResearch(@Nonnull String research);
    boolean setResearchStage(@Nonnull String research, int stage);
    boolean removeResearch(@Nonnull String research);
    @Nonnull Set<String> getResearchList();
    boolean setResearchFlag(@Nonnull String research, @Nonnull EnumResearchFlag flag);
    boolean clearResearchFlag(@Nonnull String research, @Nonnull EnumResearchFlag flag);
    boolean hasResearchFlag(@Nonnull String research, @Nonnull EnumResearchFlag flag);
    boolean addKnowledge(@Nonnull EnumKnowledgeType type, ResearchCategory category, int amount);
    int getKnowledge(@Nonnull EnumKnowledgeType type, ResearchCategory category);
    int getKnowledgeRaw(@Nonnull EnumKnowledgeType type, ResearchCategory category);
    void sync(ServerPlayer player);

    CompoundTag serializeNBT();
    void deserializeNBT(CompoundTag tag);

    @Override
    default CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return serializeNBT();
    }

    @Override
    default void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        deserializeNBT(tag);
    }

    enum EnumKnowledgeType {
        THEORY(32, true, "T"),
        OBSERVATION(16, true, "O");

        private final short progression;
        private final boolean hasFields;
        private final String abbreviation;

        EnumKnowledgeType(int progression, boolean hasFields, String abbreviation) {
            this.progression = (short) progression;
            this.hasFields = hasFields;
            this.abbreviation = abbreviation;
        }

        public int getProgression() { return progression; }
        public boolean hasFields() { return hasFields; }
        public String getAbbreviation() { return abbreviation; }
    }

    enum EnumResearchFlag { PAGE, RESEARCH, POPUP }
    enum EnumResearchStatus { UNKNOWN, COMPLETE, IN_PROGRESS }
}
