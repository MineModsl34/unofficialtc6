package thaumcraft.api.internal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.research.ResearchCategory;

public class DummyInternalMethodHandler implements IInternalMethodHandler {
    @Override
    public boolean addKnowledge(Player player, IPlayerKnowledge.EnumKnowledgeType type, ResearchCategory category, int amount) {
        return false;
    }

    @Override
    public boolean progressResearch(Player player, String researchKey) { return false; }

    @Override public boolean completeResearch(Player player, String researchKey) { return false; }

    @Override public void addWarpToPlayer(Player player, int amount,
        thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType type) {}

    @Override public int getActualWarp(Player player) { return 0; }

    @Override
    public boolean doesPlayerHaveRequisites(Player player, String researchKey) { return false; }

    @Override
    public AspectList getObjectAspects(ItemStack is) {
        return null;
    }

    @Override
    public AspectList generateTags(ItemStack is) {
        return null;
    }

    @Override
    public float drainVis(Level level, BlockPos pos, float amount, boolean simulate) {
        return 0.0F;
    }

    @Override
    public float drainFlux(Level level, BlockPos pos, float amount, boolean simulate) {
        return 0.0F;
    }

    @Override
    public void addVis(Level level, BlockPos pos, float amount) {
    }

    @Override
    public void addFlux(Level level, BlockPos pos, float amount, boolean showEffect) {
    }

    @Override
    public float getTotalAura(Level level, BlockPos pos) {
        return 0.0F;
    }

    @Override
    public float getVis(Level level, BlockPos pos) {
        return 0.0F;
    }

    @Override
    public float getFlux(Level level, BlockPos pos) {
        return 0.0F;
    }

    @Override
    public int getAuraBase(Level level, BlockPos pos) {
        return 0;
    }

    @Override
    public boolean shouldPreserveAura(Level level, Player player, BlockPos pos) {
        return false;
    }

    // Original API's harmless fallback before/without the mod. Actual runtime installs InternalMethodHandler.
    @Override public void registerSeal(thaumcraft.api.golems.seals.ISeal seal) {}
    @Override public thaumcraft.api.golems.seals.ISeal getSeal(String key) { return null; }
    @Override public ItemStack getSealStack(String key) { return ItemStack.EMPTY; }
    @Override public thaumcraft.api.golems.seals.ISealEntity getSealEntity(net.minecraft.resources.ResourceKey<Level> dimension,
        thaumcraft.api.golems.seals.SealPos pos) { return null; }
    @Override public void addGolemTask(net.minecraft.resources.ResourceKey<Level> dimension, thaumcraft.api.golems.tasks.Task task) {}
}
