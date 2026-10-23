package thaumcraft.common.lib.network.playerdata;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

class PacketResearchReadToServerTest {
    private static PacketResearchReadToServer packet() {
        return new PacketResearchReadToServer(new PacketSyncProgressToServer("FIRSTSTEPS", 3, "a".repeat(64)), "b".repeat(64));
    }
    @Test void RoundtripCarriesOnlyExactKeyStageCatalogAndOwnerSnapshot() {
        var buffer = Unpooled.buffer();
        try {
            PacketResearchReadToServer.STREAM_CODEC.encode(buffer, packet());
            assertEquals(packet(), PacketResearchReadToServer.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test void TrailingBytesAreRejectedByTheNestedBoundedBody() {
        var buffer = Unpooled.buffer();
        try {
            PacketResearchReadToServer.STREAM_CODEC.encode(buffer, packet()); buffer.writeByte(1);
            assertThrows(DecoderException.class, () -> PacketResearchReadToServer.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void TruncatedAndOversizedBodiesAreRejectedBeforeAllocatingStrings() {
        for (int size : new int[] {0, 31, 32, 64, 32 + PacketSyncProgressToServer.MAX_ENCODED_BYTES + 1}) {
            var buffer = Unpooled.buffer();
            try {
                buffer.writeZero(size);
                assertThrows(DecoderException.class, () -> PacketResearchReadToServer.STREAM_CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
    }
    @Test void SnapshotDigestIsNotAnUnboundedOrArbitraryClaim() {
        var reference = packet().reference();
        for (String invalid : new String[] {"", "b".repeat(63), "B".repeat(64), "z".repeat(64), "b".repeat(65)})
            assertThrows(IllegalArgumentException.class, () -> new PacketResearchReadToServer(reference, invalid));
    }
}
