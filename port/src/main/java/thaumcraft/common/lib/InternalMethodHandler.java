package thaumcraft.common.lib;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import thaumcraft.common.world.aura.AuraHandler;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.internal.IInternalMethodHandler;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;
import thaumcraft.common.lib.research.ResearchManager;

// Real source internal-method bridge installed by Thaumcraft, including stage9 seals/tasks.
public class InternalMethodHandler implements IInternalMethodHandler {
    @Override
    public boolean addKnowledge(Player player, IPlayerKnowledge.EnumKnowledgeType type, ResearchCategory category, int amount) {
        return amount != 0 && !player.level().isClientSide ? ResearchManager.addKnowledge(player, type, category, amount) : false;
    }

    @Override
    public boolean progressResearch(Player player, String researchKey) {
        return researchKey != null && !player.level().isClientSide ? ResearchManager.progressResearch(player, researchKey) : false;
    }

    @Override public boolean completeResearch(Player player, String researchKey) {
        return researchKey != null && !player.level().isClientSide && ResearchManager.completeResearch(player, researchKey);
    }

    @Override public void addWarpToPlayer(Player player, int amount,
            thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType type) {
        ResearchManager.addWarpToPlayer(player, amount, type);
    }

    @Override public int getActualWarp(Player player) {
        var warp = thaumcraft.api.capabilities.ThaumcraftCapabilities.getWarp(player);
        return warp.get(thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType.NORMAL)
            + warp.get(thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType.PERMANENT);
    }

    @Override
    public boolean doesPlayerHaveRequisites(Player player, String researchKey) {
        return ResearchManager.doesPlayerHaveRequisites(player, researchKey);
    }

    @Override
    public AspectList getObjectAspects(ItemStack is) {
        return ThaumcraftCraftingManager.getObjectTags(is);
    }

    @Override
    public AspectList generateTags(ItemStack is) {
        return ThaumcraftCraftingManager.generateTags(is);
    }

    @Override
    public float drainFlux(Level level, BlockPos pos, float amount, boolean simulate) {
        return AuraHandler.drainFlux(level, pos, amount, simulate);
    }

    @Override
    public float getFlux(Level level, BlockPos pos) {
        return AuraHandler.getFlux(level, pos);
    }

    @Override
    public float drainVis(Level level, BlockPos pos, float amount, boolean simulate) {
        return AuraHandler.drainVis(level, pos, amount, simulate);
    }

    @Override
    public void addVis(Level level, BlockPos pos, float amount) {
        AuraHandler.addVis(level, pos, amount);
    }

    @Override
    public float getTotalAura(Level level, BlockPos pos) {
        return AuraHandler.getTotalAura(level, pos);
    }

    @Override
    public float getVis(Level level, BlockPos pos) {
        return AuraHandler.getVis(level, pos);
    }

    @Override
    public int getAuraBase(Level level, BlockPos pos) {
        return AuraHandler.getAuraBase(level, pos);
    }

    @Override
    public void addFlux(Level level, BlockPos pos, float amount, boolean showEffect) {
        if (!level.isClientSide) {
            AuraHandler.addFlux(level, pos, amount);
            if (showEffect && amount > 0.0F) {
                thaumcraft.common.lib.fx.SourceFX.pollute(level, pos, amount);
            }
        }
    }

    @Override
    public boolean shouldPreserveAura(Level level, Player player, BlockPos pos) {
        return AuraHandler.shouldPreserveAura(level, player, pos);
    }

    @Override public void registerSeal(thaumcraft.api.golems.seals.ISeal seal) {
        thaumcraft.common.golems.seals.SealHandler.registerSeal(seal);
    }
    @Override public thaumcraft.api.golems.seals.ISeal getSeal(String key) {
        return thaumcraft.common.golems.seals.SealHandler.getSeal(key);
    }
    @Override public ItemStack getSealStack(String key) {
        return thaumcraft.common.golems.seals.ItemSealPlacer.getSealStack(key);
    }
    @Override public thaumcraft.api.golems.seals.ISealEntity getSealEntity(net.minecraft.resources.ResourceKey<Level> dimension,
        thaumcraft.api.golems.seals.SealPos pos) {
        return thaumcraft.common.golems.seals.SealHandler.getSealEntity(dimension, pos);
    }
    @Override public void addGolemTask(net.minecraft.resources.ResourceKey<Level> dimension, thaumcraft.api.golems.tasks.Task task) {
        thaumcraft.common.golems.tasks.TaskHandler.addTask(dimension, task);
    }
}
