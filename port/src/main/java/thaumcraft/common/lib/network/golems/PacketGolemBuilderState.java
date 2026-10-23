package thaumcraft.common.lib.network.golems;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded source ingredient-availability preview plus actual persistent machine state. */
public record PacketGolemBuilderState(int menuId,BlockPos position,long session,long revision,long selected,long job,int cost,int maximum,long available) implements CustomPacketPayload {
    public static final Type<PacketGolemBuilderState> TYPE = new Type<>(ResourceLocation.parse("thaumcraft:golem_builder_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PacketGolemBuilderState> STREAM_CODEC = StreamCodec.of(
        (out,p) -> { out.writeInt(p.menuId); out.writeLong(p.position.asLong()); out.writeLong(p.session); out.writeLong(p.revision); out.writeLong(p.selected); out.writeLong(p.job); out.writeInt(p.cost); out.writeInt(p.maximum); out.writeLong(p.available); },
        in -> { if (in.readableBytes() != 60) throw new DecoderException("Golem builder state length"); return new PacketGolemBuilderState(in.readInt(),BlockPos.of(in.readLong()),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readInt(),in.readInt(),in.readLong()); });
    public PacketGolemBuilderState { if (menuId < 0 || position == null || session == 0 || revision < 0 || cost < 0 || maximum < cost || maximum > 65535) throw new IllegalArgumentException("Golem builder state"); position = position.immutable(); }
    @Override public Type<PacketGolemBuilderState> type() { return TYPE; }
}
