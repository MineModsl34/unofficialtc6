package thaumcraft.common.lib.network.playerdata;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.HexFormat;
import java.util.Objects;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/** Bounded read acknowledgement. It cannot set flags, grant research or choose an owner. */
public record PacketResearchReadToServer(PacketSyncProgressToServer reference, String readStateDigest)
    implements CustomPacketPayload {
    public static final Type<PacketResearchReadToServer> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "research_read"));
    public static final StreamCodec<ByteBuf, PacketResearchReadToServer> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBytes(HexFormat.of().parseHex(packet.readStateDigest()));
            PacketSyncProgressToServer.STREAM_CODEC.encode(buffer, packet.reference());
        }, buffer -> {
            if (buffer.readableBytes() < 32 || buffer.readableBytes() > 32 + PacketSyncProgressToServer.MAX_ENCODED_BYTES)
                throw new DecoderException("Research read body bounds");
            byte[] digest = new byte[32];
            buffer.readBytes(digest);
            return new PacketResearchReadToServer(PacketSyncProgressToServer.STREAM_CODEC.decode(buffer),
                HexFormat.of().formatHex(digest));
        });

    public PacketResearchReadToServer {
        Objects.requireNonNull(reference);
        if (readStateDigest == null || !readStateDigest.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Read snapshot digest must be SHA-256 lowercase hex");
    }
    @Override public Type<PacketResearchReadToServer> type() { return TYPE; }
}
