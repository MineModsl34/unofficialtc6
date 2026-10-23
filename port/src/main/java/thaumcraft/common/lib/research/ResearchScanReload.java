package thaumcraft.common.lib.research;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import thaumcraft.common.config.ConfigResearch;

/** Registries are world-owned in 1.21; refresh on reload and on remote-server registry sync. */
@EventBusSubscriber(modid = "thaumcraft")
public final class ResearchScanReload {
    private ResearchScanReload() {}

    @SubscribeEvent
    public static void tags(TagsUpdatedEvent event) {
        if (event.shouldUpdateStaticData()) {
            ConfigResearch.refreshEnchantmentScans(event.getRegistryAccess());
            thaumcraft.common.config.ModConfig.modCompatibility();
        }
    }
}
