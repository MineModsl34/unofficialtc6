package thaumcraft.common.lib.network.playerdata;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PacketFocalState(int menuId,BlockPos position,long session,long revision,CompoundTag view,boolean rejected) implements CustomPacketPayload {
    public static final Type<PacketFocalState> TYPE = new Type<>(ResourceLocation.parse("thaumcraft:focal_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PacketFocalState> STREAM_CODEC = StreamCodec.of(
        (out,p) -> { int start = out.writerIndex(); out.writeInt(p.menuId); out.writeLong(p.position.asLong()); out.writeLong(p.session); out.writeLong(p.revision); out.writeBoolean(p.rejected); out.writeNbt(p.view); if (out.writerIndex() - start > PacketFocusEdit.MAX_BYTES) throw new IllegalArgumentException("Focus state bytes"); },
        in -> { if (in.readableBytes() < 31 || in.readableBytes() > PacketFocusEdit.MAX_BYTES) throw new DecoderException("Focus state bytes");
            int menu = in.readInt(); BlockPos pos = BlockPos.of(in.readLong()); long session = in.readLong(),revision = in.readLong(); boolean rejected = in.readBoolean(); Tag body = in.readNbt(new NbtAccounter(PacketFocusEdit.MAX_HEAP,16));
            if (!(body instanceof CompoundTag view) || in.isReadable()) throw new DecoderException("Focus state body");
            try { return new PacketFocalState(menu,pos,session,revision,view,rejected); } catch (IllegalArgumentException invalid) { throw new DecoderException("Invalid focus state",invalid); }
        });
    public PacketFocalState { if (menuId < 0 || position == null || session == 0 || revision < 0 || view == null || view.sizeInBytes() > PacketFocusEdit.MAX_HEAP) throw new IllegalArgumentException("Invalid focus state"); position = position.immutable(); view = view.copy(); }
    @Override public CompoundTag view() { return view.copy(); }
    @Override public Type<PacketFocalState> type() { return TYPE; }
}
