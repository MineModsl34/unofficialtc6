package thaumcraft.api.internal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.research.ResearchCategory;

// Source public internal-method bridge; dimension IDs are native ResourceKeys, never lossy integers.
public interface IInternalMethodHandler {
    boolean addKnowledge(Player player, IPlayerKnowledge.EnumKnowledgeType type, ResearchCategory category, int amount);

    boolean progressResearch(Player player, String researchKey);

    boolean completeResearch(Player player, String researchKey);

    void addWarpToPlayer(Player player, int amount, thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType type);

    int getActualWarp(Player player);

    boolean doesPlayerHaveRequisites(Player player, String researchKey);

    AspectList getObjectAspects(ItemStack var1);

    AspectList generateTags(ItemStack var1);

    float drainVis(Level level, BlockPos pos, float amount, boolean simulate);

    float drainFlux(Level level, BlockPos pos, float amount, boolean simulate);

    void addVis(Level level, BlockPos pos, float amount);

    void addFlux(Level level, BlockPos pos, float amount, boolean showEffect);

    float getTotalAura(Level level, BlockPos pos);

    float getVis(Level level, BlockPos pos);

    float getFlux(Level level, BlockPos pos);

    int getAuraBase(Level level, BlockPos pos);

    boolean shouldPreserveAura(Level level, Player player, BlockPos pos);

    void registerSeal(thaumcraft.api.golems.seals.ISeal seal);
    thaumcraft.api.golems.seals.ISeal getSeal(String key);
    ItemStack getSealStack(String key);
    thaumcraft.api.golems.seals.ISealEntity getSealEntity(net.minecraft.resources.ResourceKey<Level> dimension,
        thaumcraft.api.golems.seals.SealPos pos);
    void addGolemTask(net.minecraft.resources.ResourceKey<Level> dimension, thaumcraft.api.golems.tasks.Task task);
}
