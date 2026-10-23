package thaumcraft.common.lib.research;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.research.IScanThing;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ScanningManager;

public class ScanGeneric implements IScanThing {
    @Override
    public boolean checkThing(Player player, Object object) {
        AspectList aspects = aspects(player, object);
        return aspects != null && aspects.size() > 0;
    }

    private static AspectList aspects(Player player, Object object) {
        if (object == null) return null;
        if (object instanceof Entity entity && !(object instanceof ItemEntity)) return AspectHelper.getEntityAspects(entity);
        ItemStack stack = ScanningManager.getItemFromParms(player, object);
        return stack != null && !stack.isEmpty() ? AspectHelper.getObjectAspects(stack) : null;
    }

    @Override
    public void onSuccess(Player player, Object object) {
        // Recompute as TC6 did; checkThing is not a memoized snapshot.
        AspectList aspects = aspects(player, object);
        if (aspects != null) {
            for (ResearchCategory category : ResearchCategories.researchCategories.values()) {
                ThaumcraftApi.internalMethods.addKnowledge(player, IPlayerKnowledge.EnumKnowledgeType.OBSERVATION,
                    category, category.applyFormula(aspects));
            }
        }
    }

    @Override
    public String getResearchKey(Player player, Object object) {
        if (object instanceof Entity entity && !(object instanceof ItemEntity)) return LegacyScanIdentity.entityResearchKey(entity);
        ItemStack stack = ScanningManager.getItemFromParms(player, object);
        return stack != null && !stack.isEmpty() ? LegacyScanIdentity.itemResearchKey(stack) : null;
    }
}
