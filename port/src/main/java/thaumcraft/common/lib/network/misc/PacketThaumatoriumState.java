package thaumcraft.common.lib.network.misc;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-owned display snapshot; never used as executable client recipe/payment data. */
public record PacketThaumatoriumState(int menuId,BlockPos position,long session,long revision,CompoundTag view) implements CustomPacketPayload {
    public static final int MAX_BYTES = 262144,MAX_HEAP = 1048576;
    public static final Type<PacketThaumatoriumState> TYPE = new Type<>(ResourceLocation.parse("thaumcraft:thaumatorium_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PacketThaumatoriumState> STREAM_CODEC = StreamCodec.of(
        (out,packet) -> { int start = out.writerIndex(); out.writeInt(packet.menuId); out.writeLong(packet.position.asLong()); out.writeLong(packet.session); out.writeLong(packet.revision); out.writeNbt(packet.view); if (out.writerIndex() - start > MAX_BYTES) throw new IllegalArgumentException("Thaumatorium view byte bound"); },
        in -> {
            if (in.readableBytes() < 32 || in.readableBytes() > MAX_BYTES) throw new DecoderException("Thaumatorium view byte bound");
            int menu = in.readInt(); BlockPos pos = BlockPos.of(in.readLong()); long session = in.readLong(),revision = in.readLong();
            Tag value = in.readNbt(new NbtAccounter(MAX_HEAP,32));
            if (!(value instanceof CompoundTag view) || in.isReadable()) throw new DecoderException("Invalid thaumatorium view body");
            try { return new PacketThaumatoriumState(menu,pos,session,revision,view); }
            catch (IllegalArgumentException invalid) { throw new DecoderException("Invalid thaumatorium view",invalid); }
        });
    public PacketThaumatoriumState {
        if (menuId < 0 || position == null || session == 0 || revision < 0 || view == null || view.sizeInBytes() > MAX_HEAP) throw new IllegalArgumentException("Invalid thaumatorium view");
        position = position.immutable(); view = view.copy();
    }
    @Override public CompoundTag view() { return view.copy(); }
    @Override public Type<PacketThaumatoriumState> type() { return TYPE; }
}
