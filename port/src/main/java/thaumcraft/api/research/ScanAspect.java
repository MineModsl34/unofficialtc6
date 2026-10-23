package thaumcraft.api.research;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.common.lib.research.LegacyScanIdentity;

public class ScanAspect implements IScanThing {
    String research;
    Aspect aspect;

    public ScanAspect(String research, Aspect aspect) {
        this.research = research;
        this.aspect = aspect;
    }

    @Override
    public boolean checkThing(Player player, Object object) {
        if (object == null) return false;
        AspectList aspects = null;
        if (object instanceof Entity entity && !(object instanceof ItemEntity)) {
            aspects = AspectHelper.getEntityAspects(entity);
        } else {
            ItemStack stack = null;
            if (object instanceof ItemStack item) stack = item;
            if (object instanceof ItemEntity entity && entity.getItem() != null) stack = entity.getItem();
            if (object instanceof BlockPos pos) {
                // Deliberately no pick-block or bucket fallback: ScanAspect differs from ScanGeneric.
                stack = LegacyScanIdentity.directBlockStack(player.level().getBlockState(pos));
            }
            if (stack != null) aspects = AspectHelper.getObjectAspects(stack);
        }
        // TC6 does not require knowledge of a compound aspect's components.
        return aspects != null && aspects.getAmount(aspect) > 0;
    }

    @Override
    public void onSuccess(Player player, Object object) {
        ThaumcraftApi.internalMethods.addKnowledge(player, IPlayerKnowledge.EnumKnowledgeType.OBSERVATION,
            ResearchCategories.getResearchCategory("AUROMANCY"), 1);
        ThaumcraftApi.internalMethods.addKnowledge(player, IPlayerKnowledge.EnumKnowledgeType.OBSERVATION,
            ResearchCategories.getResearchCategory("BASICS"), 1);
        ThaumcraftApi.internalMethods.addKnowledge(player, IPlayerKnowledge.EnumKnowledgeType.OBSERVATION,
            ResearchCategories.getResearchCategory("ALCHEMY"), 1);
    }

    @Override
    public String getResearchKey(Player player, Object object) { return research; }
}
