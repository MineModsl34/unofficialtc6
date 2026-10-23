package thaumcraft.client.gui;

import net.minecraft.client.Minecraft;
import thaumcraft.common.container.ContainerFocalManipulator;
import thaumcraft.common.lib.network.playerdata.PacketFocalState;

public final class FocalClientPackets {
    private FocalClientPackets() {}
    public static void state(PacketFocalState packet) { var player = Minecraft.getInstance().player; if (player != null && player.containerMenu instanceof ContainerFocalManipulator menu) menu.acceptState(packet); }
}
