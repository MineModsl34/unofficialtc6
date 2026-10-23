package thaumcraft.common.lib.network.research;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Collections;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Menu-bound request only; no card seed, totals, player identifier or client NBT authority. */
public record PacketTheorycraftAction(int menuId, BlockPos position, long session, long revision, long sequence, int button, Set<String> aids) implements CustomPacketPayload {
    public static final int MAX_BYTES = 8192, MAX_AIDS = 32, MAX_KEY = 128;
    public static final Type<PacketTheorycraftAction> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("thaumcraft", "theorycraft_action"));
    public static final StreamCodec<ByteBuf, PacketTheorycraftAction> STREAM_CODEC = StreamCodec.of(PacketTheorycraftAction::encode, PacketTheorycraftAction::decode);
    public PacketTheorycraftAction {
        if (position == null || aids == null || menuId < 0 || session == 0 || revision < 0 || sequence <= 0 || button < 0 || button > 9 || aids.size() > MAX_AIDS)
            throw new IllegalArgumentException("Invalid theory action");
        for (String key : aids) if (key == null || key.isEmpty() || key.length() > MAX_KEY || !key.matches("[A-Za-z0-9_.$]+")) throw new IllegalArgumentException("Invalid aid key");
        if (button != 0 && !aids.isEmpty()) throw new IllegalArgumentException("Only theory creation selects aids");
        position = position.immutable(); aids = Collections.unmodifiableSet(new HashSet<>(aids));
    }
    private static void encode(ByteBuf buffer, PacketTheorycraftAction packet) {
        buffer.writeInt(packet.menuId); buffer.writeLong(packet.position.asLong()); buffer.writeLong(packet.session); buffer.writeLong(packet.revision); buffer.writeLong(packet.sequence);
        buffer.writeByte(packet.button); buffer.writeByte(packet.aids.size());
        for (String aid : packet.aids) { buffer.writeShort(aid.length()); buffer.writeCharSequence(aid, StandardCharsets.US_ASCII); }
    }
    private static PacketTheorycraftAction decode(ByteBuf buffer) {
        if (buffer.readableBytes() < 38 || buffer.readableBytes() > MAX_BYTES) throw new DecoderException("Theory action byte bound");
        int menu = buffer.readInt(); BlockPos pos = BlockPos.of(buffer.readLong()); long session = buffer.readLong(), revision = buffer.readLong(), sequence = buffer.readLong();
        int button = buffer.readUnsignedByte(), count = buffer.readUnsignedByte(); if (count > MAX_AIDS) throw new DecoderException("Aid count bound");
        Set<String> aids = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            if (buffer.readableBytes() < 2) throw new DecoderException("Truncated aid key");
            int length = buffer.readUnsignedShort(); if (length < 1 || length > MAX_KEY || buffer.readableBytes() < length) throw new DecoderException("Aid key byte bound");
            String key = buffer.readCharSequence(length, StandardCharsets.US_ASCII).toString();
            if (!aids.add(key)) throw new DecoderException("Duplicate aid");
        }
        if (buffer.isReadable()) throw new DecoderException("Trailing theory action bytes");
        try { return new PacketTheorycraftAction(menu, pos, session, revision, sequence, button, aids); }
        catch (IllegalArgumentException invalid) { throw new DecoderException("Invalid theory action", invalid); }
    }
    @Override public Type<PacketTheorycraftAction> type() { return TYPE; }
}
