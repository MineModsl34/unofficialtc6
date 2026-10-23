package thaumcraft.common.lib.network.fx;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Native bounded S2C envelope replacing the twelve remaining legacy FX-only IMessage routes.
 * Already ported Sonic/Slash/WispZap/ScanSource keep their original native routes. */
public record PacketSourceFX(Kind kind, double x, double y, double z,
    double tx, double ty, double tz, int color, int extra, int flags, int face, String key)
    implements CustomPacketPayload {
    public enum Kind { POLLUTION, BAMF, MIST, ARC, ZAP, ESSENTIA, INFUSION,
        FOCUS_TRAIL, FOCUS_PARTICLE, FOCUS_IMPACT, FOCUS_BURST, BORE, SHIELD, LOCAL }
    public PacketSourceFX {
        if (kind == null || key == null || key.length() > 1024 || flags < 0 || flags > 3 || face < -1 || face > 5
            || !finite(x,y,z,tx,ty,tz)
            || kind==Kind.ZAP&&!Float.isFinite(Float.intBitsToFloat(extra))) throw new IllegalArgumentException("Invalid source FX description");
    }
    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value) || Math.abs(value) > 33_554_432) return false;
        return true;
    }
    public static final Type<PacketSourceFX> TYPE = new Type<>(ResourceLocation.parse("thaumcraft:source_fx"));
    public static final StreamCodec<FriendlyByteBuf, PacketSourceFX> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeEnum(packet.kind); buffer.writeDouble(packet.x); buffer.writeDouble(packet.y); buffer.writeDouble(packet.z);
            buffer.writeDouble(packet.tx); buffer.writeDouble(packet.ty); buffer.writeDouble(packet.tz);
            buffer.writeInt(packet.color); buffer.writeInt(packet.extra); buffer.writeByte(packet.flags); buffer.writeByte(packet.face);
            buffer.writeUtf(packet.key,1024);
        }, buffer -> new PacketSourceFX(buffer.readEnum(Kind.class),buffer.readDouble(),buffer.readDouble(),buffer.readDouble(),
            buffer.readDouble(),buffer.readDouble(),buffer.readDouble(),buffer.readInt(),buffer.readInt(),buffer.readUnsignedByte(),buffer.readByte(),buffer.readUtf(1024)));
    @Override public Type<PacketSourceFX> type() { return TYPE; }
}
