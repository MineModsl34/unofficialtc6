package thaumcraft.common.lib.events;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.lib.research.LegacyResearchCrafting;

/** Source crafted-item warp registrations and research receipts, on the server owner. */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class ResearchCraftingEvents {
    private ResearchCraftingEvents() {}

    @SubscribeEvent
    public static void onCrafting(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer owner) {
            if (!thaumcraft.common.config.ModConfig.CONFIG_MISC.wussMode) {
                int warp = thaumcraft.api.ThaumcraftApi.getWarp(event.getCrafting());
                if (warp > 0) thaumcraft.common.lib.research.ResearchManager.addWarpToPlayer(owner, warp,
                    thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType.NORMAL);
            }
            LegacyResearchCrafting.recordCrafting(owner, event.getCrafting());
        }
    }
}
