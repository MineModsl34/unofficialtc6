package thaumcraft.common.lib.network.fx;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Source slash contains only two entity IDs, never damage or authority from a client. */
public record PacketFXSlash(int source, int target) implements CustomPacketPayload {
    public static final Type<PacketFXSlash> TYPE = new Type<>(ResourceLocation.parse("thaumcraft:fx_slash"));
    public static final StreamCodec<ByteBuf, PacketFXSlash> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> { buffer.writeInt(packet.source); buffer.writeInt(packet.target); },
        buffer -> new PacketFXSlash(buffer.readInt(), buffer.readInt()));
    @Override public Type<PacketFXSlash> type() { return TYPE; }
}
