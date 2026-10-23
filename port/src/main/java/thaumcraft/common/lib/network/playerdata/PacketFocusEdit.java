package thaumcraft.common.lib.network.playerdata;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** No executable/cost flags accepted from the client; action 0 saves draft, 1 requests craft. */
public record PacketFocusEdit(int menuId,BlockPos position,long session,long revision,long sequence,
                              boolean craft,String name,CompoundTag graph) implements CustomPacketPayload {
    public static final int MAX_BYTES = 65536,MAX_HEAP = 262144;
    public static final Type<PacketFocusEdit> TYPE = new Type<>(ResourceLocation.parse("thaumcraft:focus_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PacketFocusEdit> STREAM_CODEC = StreamCodec.of(
        (out,p) -> { int start = out.writerIndex(); out.writeInt(p.menuId); out.writeLong(p.position.asLong()); out.writeLong(p.session); out.writeLong(p.revision); out.writeLong(p.sequence); out.writeBoolean(p.craft); out.writeUtf(p.name,50); out.writeNbt(p.graph); if (out.writerIndex() - start > MAX_BYTES) throw new IllegalArgumentException("Focus edit bytes"); },
        in -> { if (in.readableBytes() < 40 || in.readableBytes() > MAX_BYTES) throw new DecoderException("Focus edit bytes");
            int menu = in.readInt(); BlockPos pos = BlockPos.of(in.readLong()); long session = in.readLong(),revision = in.readLong(),seq = in.readLong(); boolean craft = in.readBoolean(); String name = in.readUtf(50); Tag body = in.readNbt(new NbtAccounter(MAX_HEAP,16));
            if (!(body instanceof CompoundTag graph) || in.isReadable()) throw new DecoderException("Focus edit body");
            try { return new PacketFocusEdit(menu,pos,session,revision,seq,craft,name,graph); } catch (IllegalArgumentException invalid) { throw new DecoderException("Invalid focus edit",invalid); }
        });
    public PacketFocusEdit {
        if (menuId < 0 || position == null || session == 0 || revision < 0 || sequence <= 0 || name == null || name.length() > 50 || name.chars().anyMatch(Character::isISOControl)
            || graph == null || graph.sizeInBytes() > MAX_HEAP || graph.getList("nodes",Tag.TAG_COMPOUND).size() > 127) throw new IllegalArgumentException("Invalid focus edit");
        position = position.immutable(); graph = graph.copy();
    }
    @Override public CompoundTag graph() { return graph.copy(); }
    @Override public Type<PacketFocusEdit> type() { return TYPE; }
}
