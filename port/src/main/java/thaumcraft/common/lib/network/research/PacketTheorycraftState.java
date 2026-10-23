package thaumcraft.common.lib.network.research;

import io.netty.handler.codec.DecoderException;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Owner menu's authoritative table view. Inventory slots still use ordinary vanilla transport. */
public record PacketTheorycraftState(int menuId, BlockPos position, long session, long revision, int availableInspiration, boolean owned,
    Set<String> aids, CompoundTag note) implements CustomPacketPayload {
    public static final int MAX_BYTES = 262144, MAX_NOTE_HEAP = 1048576;
    public static final Type<PacketTheorycraftState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("thaumcraft", "theorycraft_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketTheorycraftState> STREAM_CODEC = StreamCodec.of(PacketTheorycraftState::encode, PacketTheorycraftState::decode);
    public PacketTheorycraftState {
        if (menuId < 0 || position == null || session == 0 || revision < 0 || availableInspiration < 0 || availableInspiration > 15 || aids == null
            || aids.size() > PacketTheorycraftAction.MAX_AIDS || note == null || note.sizeInBytes() > MAX_NOTE_HEAP) throw new IllegalArgumentException("Invalid theory state");
        for (String aid : aids) if (aid.length() > PacketTheorycraftAction.MAX_KEY) throw new IllegalArgumentException("Aid key bound");
        position = position.immutable(); aids = Set.copyOf(aids); note = note.copy();
    }
    @Override public CompoundTag note() { return note.copy(); }
    private static void encode(RegistryFriendlyByteBuf out, PacketTheorycraftState packet) {
        int start = out.writerIndex();
        out.writeInt(packet.menuId); out.writeLong(packet.position.asLong()); out.writeLong(packet.session); out.writeLong(packet.revision); out.writeByte(packet.availableInspiration);
        out.writeBoolean(packet.owned); out.writeByte(packet.aids.size()); for (String aid : packet.aids) out.writeUtf(aid, PacketTheorycraftAction.MAX_KEY);
        out.writeNbt(packet.note); if (out.writerIndex() - start > MAX_BYTES) throw new IllegalArgumentException("Theory state byte bound");
    }
    private static PacketTheorycraftState decode(RegistryFriendlyByteBuf in) {
        if (in.readableBytes() < 32 || in.readableBytes() > MAX_BYTES) throw new DecoderException("Theory state byte bound");
        int menu = in.readInt(); BlockPos pos = BlockPos.of(in.readLong()); long session = in.readLong(), revision = in.readLong(); int inspiration = in.readUnsignedByte(); boolean owned = in.readBoolean();
        int count = in.readUnsignedByte(); if (count > PacketTheorycraftAction.MAX_AIDS) throw new DecoderException("Aid count bound");
        Set<String> aids = new LinkedHashSet<>(); for (int i = 0; i < count; i++) if (!aids.add(in.readUtf(PacketTheorycraftAction.MAX_KEY))) throw new DecoderException("Duplicate aid");
        Tag tag = in.readNbt(new NbtAccounter(MAX_NOTE_HEAP, 32));
        if (!(tag instanceof CompoundTag note) || in.isReadable()) throw new DecoderException("Invalid theory note body");
        try { return new PacketTheorycraftState(menu, pos, session, revision, inspiration, owned, aids, note); }
        catch (IllegalArgumentException invalid) { throw new DecoderException("Invalid theory state", invalid); }
    }
    @Override public Type<PacketTheorycraftState> type() { return TYPE; }
}
