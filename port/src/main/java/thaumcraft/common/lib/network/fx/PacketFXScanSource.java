package thaumcraft.common.lib.network.fx;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Source owner-only sounding request: one BlockPos long and one signed rank byte. */
public record PacketFXScanSource(BlockPos pos, int rank) implements CustomPacketPayload {
    public static final Type<PacketFXScanSource> TYPE = new Type<>(ResourceLocation.parse("thaumcraft:fx_scan_source"));
    public static final StreamCodec<ByteBuf, PacketFXScanSource> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> { buffer.writeLong(packet.pos.asLong()); buffer.writeByte(packet.rank); },
        buffer -> new PacketFXScanSource(BlockPos.of(buffer.readLong()), buffer.readByte()));
    public PacketFXScanSource { pos = pos.immutable(); if (rank < 0 || rank > 4) throw new IllegalArgumentException("Sounding rank outside source range"); }
    @Override public Type<PacketFXScanSource> type() { return TYPE; }
}
