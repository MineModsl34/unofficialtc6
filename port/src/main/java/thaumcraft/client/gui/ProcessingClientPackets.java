package thaumcraft.client.gui;

import net.minecraft.client.Minecraft;
import thaumcraft.common.container.ContainerThaumatorium;
import thaumcraft.common.lib.network.misc.PacketThaumatoriumState;

public final class ProcessingClientPackets {
    private ProcessingClientPackets() {}
    public static void state(PacketThaumatoriumState packet) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof ContainerThaumatorium menu) menu.acceptState(packet,player.registryAccess());
    }
}
