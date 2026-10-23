package thaumcraft.common.lib.network.research;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class PacketTheorycraftActionTest {
    private static PacketTheorycraftAction packet() {
        return new PacketTheorycraftAction(7, new BlockPos(-145, 64, 678), -9183L, 31L, 9L, 0,
            Set.of("thaumcraft.api.research.theorycraft.AidBookshelf", "thaumcraft.common.lib.research.theorycraft.AidPortal$AidPortalEnd"));
    }
    @Test void roundTripPreservesEveryAuthorityFieldAndDefensivelyCopiesAids() {
        var original = packet(); var buffer = Unpooled.buffer();
        try { PacketTheorycraftAction.STREAM_CODEC.encode(buffer, original); assertEquals(original, PacketTheorycraftAction.STREAM_CODEC.decode(buffer)); assertFalse(buffer.isReadable()); }
        finally { buffer.release(); }
        var aids = new HashSet<String>(); aids.add("Test.Aid");
        var copy = new PacketTheorycraftAction(0, BlockPos.ZERO, 1, 0, 1, 0, aids); aids.clear();
        assertEquals(Set.of("Test.Aid"), copy.aids()); assertThrows(UnsupportedOperationException.class, () -> copy.aids().clear());
    }
    @Test void rejectTrailingOversizedAndEveryTruncatedBody() {
        var buffer = Unpooled.buffer();
        try {
            PacketTheorycraftAction.STREAM_CODEC.encode(buffer, packet()); int length = buffer.readableBytes();
            for (int count = 0; count < length; count++) { var truncated = buffer.copy(0, count); try { assertThrows(RuntimeException.class, () -> PacketTheorycraftAction.STREAM_CODEC.decode(truncated)); } finally { truncated.release(); } }
            buffer.writeByte(0); assertThrows(DecoderException.class, () -> PacketTheorycraftAction.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
        var huge = Unpooled.buffer(); try { huge.writeZero(PacketTheorycraftAction.MAX_BYTES + 1); assertThrows(DecoderException.class, () -> PacketTheorycraftAction.STREAM_CODEC.decode(huge)); } finally { huge.release(); }
    }
    @Test void rejectDuplicateAidAndInvalidFieldDomains() {
        var buffer = Unpooled.buffer();
        try {
            buffer.writeInt(1).writeLong(0).writeLong(1).writeLong(0).writeLong(1).writeByte(0).writeByte(2);
            buffer.writeShort(1).writeByte('A').writeShort(1).writeByte('A');
            assertThrows(DecoderException.class, () -> PacketTheorycraftAction.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class, () -> new PacketTheorycraftAction(1, BlockPos.ZERO, 0, 0, 1, 0, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new PacketTheorycraftAction(1, BlockPos.ZERO, 1, -1, 1, 0, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new PacketTheorycraftAction(1, BlockPos.ZERO, 1, 0, 0, 0, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new PacketTheorycraftAction(1, BlockPos.ZERO, 1, 0, 1, 4, Set.of("A")));
        assertThrows(IllegalArgumentException.class, () -> new PacketTheorycraftAction(1, BlockPos.ZERO, 1, 0, 1, 0, Set.of("../A")));
        assertThrows(IllegalArgumentException.class, () -> new PacketTheorycraftAction(1, BlockPos.ZERO, 1, 0, 1, 0, Set.of("A".repeat(129))));
    }
}
