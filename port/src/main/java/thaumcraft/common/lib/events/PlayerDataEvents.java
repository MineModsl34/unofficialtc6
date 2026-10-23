package thaumcraft.common.lib.events;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.capabilities.PlayerDataAttachmentPersistence;
import thaumcraft.common.lib.research.catalog.ResearchCatalogRuntime;

/** Modern replacement for source EntityJoinWorld plus the player tick syncList. */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class PlayerDataEvents {
    private PlayerDataEvents() {}

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!PlayerDataAttachmentPersistence.allowsLogin(player)) {
                // NeoForge fires login after vanilla player insertion, but before our owner sync.
                // Logout/save still sees the quarantined attachment and retains its complete raw NBT.
                player.connection.disconnect(Component.literal("Your Thaumcraft player data could not be read. "
                    + "The original data was preserved; contact the server administrator."));
                return;
            }
            if (ResearchCatalogRuntime.currentPayload(player.getServer()) == null) {
                player.connection.disconnect(Component.literal("Thaumcraft research metadata is not available. "
                    + "Your saved player data was not changed."));
                return;
            }
            PlayerDataSync.syncAllNow(player);
        }
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PlayerDataSync.syncAllNow(player);
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PlayerDataSync.syncAllNow(player);
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            thaumcraft.common.lib.warp.WarpGameplay.tick(player);
            PlayerDataSync.flushKnowledgeIfDue(player);
            if (player.tickCount % 200 == 0) thaumcraft.common.config.ConfigResearch.checkPeriodicStuff(player);
        }
    }

    @SubscribeEvent
    public static void finishedUsingItem(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish event) {
        thaumcraft.common.lib.warp.WarpGameplay.finishedUsingItem(event.getEntity(), event.getItem());
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PlayerDataSync.forget(player);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        PlayerDataSync.clearServer(event.getServer());
    }
    // Clone copying is already performed by AttachmentInternals via copyOnDeath + serialization.
}
