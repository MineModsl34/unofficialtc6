package thaumcraft.common.lib.network.misc;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/** Finite TC6 owner-only heartbeat/mist presentation. No arbitrary effect or entity requests. */
public record PacketMiscEvent(byte eventType) implements CustomPacketPayload {
    public static final byte WARP_EVENT = 0, MIST_EVENT = 1, MIST_EVENT_SHORT = 2;
    public static final Type<PacketMiscEvent> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "misc_event"));
    public static final StreamCodec<ByteBuf, PacketMiscEvent> STREAM_CODEC =
        ByteBufCodecs.BYTE.map(PacketMiscEvent::new, PacketMiscEvent::eventType);
    public PacketMiscEvent {
        if (eventType < 0 || eventType > 2) throw new IllegalArgumentException("Unknown TC6 misc event");
    }
    @Override public Type<PacketMiscEvent> type() { return TYPE; }
}
