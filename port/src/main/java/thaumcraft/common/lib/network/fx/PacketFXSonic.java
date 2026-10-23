package thaumcraft.common.lib.network.fx;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/** Source nearby-player S2C sonic visual, carrying only the source entity ID. */
public record PacketFXSonic(int sourceEntity) implements CustomPacketPayload {
    public static final Type<PacketFXSonic> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "fx_sonic"));
    public static final StreamCodec<ByteBuf, PacketFXSonic> STREAM_CODEC = ByteBufCodecs.INT.map(PacketFXSonic::new, PacketFXSonic::sourceEntity);
    @Override public Type<PacketFXSonic> type() { return TYPE; }
}
