package thaumcraft.common.lib.network.playerdata;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/** TC6 owner-only warp change notification; not a storage update or a C2S award. */
public record PacketWarpMessage(int amount, byte warpType) implements CustomPacketPayload {
    public static final Type<PacketWarpMessage> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "warp_message"));
    public static final StreamCodec<ByteBuf, PacketWarpMessage> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT, PacketWarpMessage::amount, ByteBufCodecs.BYTE, PacketWarpMessage::warpType,
        PacketWarpMessage::new);
    public PacketWarpMessage {
        if (warpType < 0 || warpType > 2) throw new IllegalArgumentException("Unknown TC6 warp message type");
    }
    @Override public Type<PacketWarpMessage> type() { return TYPE; }
}
