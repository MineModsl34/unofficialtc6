package thaumcraft.common.lib.network.misc;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;
import thaumcraft.common.world.aura.AuraChunk;

/** Owner-only current aura snapshot: exactly the original signed short and two IEEE floats. */
public record PacketAuraToClient(short base, float vis, float flux) implements CustomPacketPayload {
    public static final int WIRE_BYTES = Short.BYTES + 2 * Float.BYTES;
    public static final Type<PacketAuraToClient> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "aura_to_client"));
    public static final StreamCodec<ByteBuf, PacketAuraToClient> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PacketAuraToClient decode(ByteBuf buffer) {
            if (buffer.readableBytes() != WIRE_BYTES) throw new DecoderException("Aura snapshot requires exactly ten bytes");
            return new PacketAuraToClient(buffer.readShort(), buffer.readFloat(), buffer.readFloat());
        }

        @Override
        public void encode(ByteBuf buffer, PacketAuraToClient packet) {
            buffer.writeShort(packet.base());
            buffer.writeFloat(packet.vis());
            buffer.writeFloat(packet.flux());
        }
    };

    public PacketAuraToClient(AuraChunk aura) { this(aura.getBase(), aura.getVis(), aura.getFlux()); }

    @Override
    public Type<PacketAuraToClient> type() { return TYPE; }
}
