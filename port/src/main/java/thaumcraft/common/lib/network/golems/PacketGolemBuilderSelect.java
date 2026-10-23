package thaumcraft.common.lib.network.golems;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Fixed-size request; executable properties/payment are validated by the active SERVER menu. */
public record PacketGolemBuilderSelect(int menuId,BlockPos position,long session,long revision,long sequence,long properties,boolean craft) implements CustomPacketPayload {
    public static final Type<PacketGolemBuilderSelect> TYPE = new Type<>(ResourceLocation.parse("thaumcraft:golem_builder_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PacketGolemBuilderSelect> STREAM_CODEC = StreamCodec.of(
        (out,p) -> { out.writeInt(p.menuId); out.writeLong(p.position.asLong()); out.writeLong(p.session); out.writeLong(p.revision); out.writeLong(p.sequence); out.writeLong(p.properties); out.writeBoolean(p.craft); },
        in -> { if (in.readableBytes() != 45) throw new DecoderException("Golem builder request length"); return new PacketGolemBuilderSelect(in.readInt(),BlockPos.of(in.readLong()),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readBoolean()); });
    public PacketGolemBuilderSelect { if (menuId < 0 || position == null || session == 0 || revision < 0 || sequence < 1) throw new IllegalArgumentException("Golem builder request identity"); position = position.immutable(); }
    @Override public Type<PacketGolemBuilderSelect> type() { return TYPE; }
}
