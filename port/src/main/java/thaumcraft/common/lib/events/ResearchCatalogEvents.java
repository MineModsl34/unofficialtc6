package thaumcraft.common.lib.events;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.Thaumcraft;
import thaumcraft.common.lib.capabilities.PlayerDataAttachmentPersistence;
import thaumcraft.common.lib.research.catalog.ResearchCatalogRuntime;

/**
 * GAME-bus hooks. Login OnDatapackSyncEvent (PlayerList:208) precedes PlayerLoggedIn (275), so the
 * public metadata packet is queued before owner-only knowledge/warp. On /reload the same event
 * occurs only after MinecraftServer installed accepted resources (1532/1540); a later listener
 * failure therefore cannot publish half a reload. No knowledge NBT is reapplied here.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class ResearchCatalogEvents {
    private ResearchCatalogEvents() {}

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(ResearchCatalogRuntime.listener(event.getServerResources()));
    }

    @SubscribeEvent
    public static void aboutToStart(ServerAboutToStartEvent event) {
        var server = event.getServer();
        ResearchCatalogRuntime.commitAccepted(server);
        // Before levels/players exist: missing initial metadata is a startup failure, not a grant.
        if (ResearchCatalogRuntime.currentPayload(server) == null) {
            throw new IllegalStateException("No accepted Thaumcraft research catalog for this server");
        }
    }

    @SubscribeEvent
    public static void datapackSync(OnDatapackSyncEvent event) {
        var server = event.getPlayerList().getServer();
        if (event.getPlayer() == null) ResearchCatalogRuntime.commitAccepted(server);
        var payload = ResearchCatalogRuntime.currentPayload(server);
        event.getRelevantPlayers().filter(PlayerDataAttachmentPersistence::allowsLogin).forEach(player -> {
            if (payload == null) {
                player.connection.disconnect(Component.literal("Thaumcraft research metadata is not available. "
                    + "Your saved player data was not changed."));
            } else {
                PacketDistributor.sendToPlayer(player, payload);
                if (Boolean.getBoolean(thaumcraft.common.lib.research.catalog.validation.ResearchStageFourNetworkAudit.ENABLE_PROPERTY))
                    thaumcraft.common.lib.research.catalog.validation.ResearchStageFourNetworkAudit.observeSent(player, payload);
                if (Boolean.getBoolean("thaumcraft.researchProgressNetworkAudit")) {
                    thaumcraft.common.lib.research.catalog.validation.ResearchProgressNetworkAudit.observeSent(player, payload);
                }
                if (Boolean.getBoolean("thaumcraft.researchCatalogNetworkAudit")) {
                    thaumcraft.common.lib.research.catalog.validation.ResearchCatalogNetworkAudit.observeSent(player, payload);
                }
            }
        });
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { ResearchCatalogRuntime.clearServer(event.getServer()); }
}
