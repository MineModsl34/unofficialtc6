package thaumcraft.client.lib;

import net.minecraft.client.Minecraft;
import thaumcraft.Thaumcraft;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.internal.CommonInternals;
import thaumcraft.common.lib.network.misc.PacketSyncObjectTags;

/** Client-side packet handling. Loaded only when the handler runs on the client. */
public final class ClientPacketHandlers {
    private ClientPacketHandlers() {
    }

    public static void handleObjectTags(PacketSyncObjectTags packet, net.minecraft.network.Connection connection) {
        if (!ClientAspectSnapshot.accept(packet, connection)) return;
        // In singleplayer the server is in the same JVM and has already filled the shared table
        if (Minecraft.getInstance().hasSingleplayerServer()) {
            return;
        }

        CommonInternals.objectTags.clear();
        CommonInternals.objectTagStacks.clear();
        for (PacketSyncObjectTags.Entry e : packet.entries()) {
            ThaumcraftApi.registerObjectTag(e.stack(), e.aspects());
        }
        Thaumcraft.log.info("Received aspects for {} item stacks from server", packet.entries().size());
    }
}
