package thaumcraft.common.lib.network.misc;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Opaque bounded bytes only: no item registry bootstrap, client, server, or sockets. */
class PacketSyncJeiRecipesTest {
    @Test void emptyCatalogAndRecipeBodiesRoundtripWithExactWireOrdering() {
        var empty = roundtrip(new PacketSyncJeiRecipes(1, 0, 1, List.of()));
        assertEquals(1, empty.revision()); assertEquals(0, empty.index()); assertEquals(1, empty.chunks());
        assertTrue(empty.recipes().isEmpty());
        var packet = new PacketSyncJeiRecipes(19, 2, 3, List.of(new byte[]{1, 2}, new byte[]{3, 4, 5}));
        ByteBuf expected = Unpooled.buffer();
        try {
            expected.writeLong(19).writeInt(2).writeInt(3).writeInt(2);
            expected.writeInt(2).writeBytes(new byte[]{1, 2}).writeInt(3).writeBytes(new byte[]{3, 4, 5});
            assertArrayEquals(bytes(expected), encoded(packet));
        } finally { expected.release(); }
        var decoded = roundtrip(packet);
        assertArrayEquals(new byte[]{1, 2}, decoded.recipes().get(0));
        assertArrayEquals(new byte[]{3, 4, 5}, decoded.recipes().get(1));
    }
    @Test void packetCopiesInputAndReturnedByteArraysAcrossMemoryConnections() {
        byte[] body = {1, 2, 3};
        var input = new ArrayList<>(List.of(body));
        var packet = new PacketSyncJeiRecipes(1, 0, 1, input);
        body[0] = 9; input.clear();
        var exported = packet.recipes(); exported.get(0)[1] = 9;
        assertThrows(UnsupportedOperationException.class, () -> exported.clear());
        assertArrayEquals(new byte[]{1, 2, 3}, packet.recipes().get(0));
        assertArrayEquals(new byte[]{1, 2, 3}, roundtrip(packet).recipes().get(0));
    }
    @Test void nonzeroReaderIndexAndReleasedSourceNeverAffectDecodedPacket() {
        ByteBuf buffer = Unpooled.buffer();
        PacketSyncJeiRecipes decoded;
        try {
            buffer.writeInt(0xcafebabe);
            int start = buffer.writerIndex();
            PacketSyncJeiRecipes.STREAM_CODEC.encode(buffer, new PacketSyncJeiRecipes(7, 0, 1, List.of(new byte[]{4, 5})));
            buffer.readerIndex(start);
            decoded = PacketSyncJeiRecipes.STREAM_CODEC.decode(buffer);
            assertFalse(buffer.isReadable());
            buffer.setZero(start, buffer.writerIndex() - start);
        } finally { buffer.release(); }
        assertArrayEquals(new byte[]{4, 5}, decoded.recipes().get(0));
        assertEquals(7, roundtrip(decoded).revision());
    }
    @Test void exactChunkBoundaryIsAcceptedAndOneByteMoreIsRejected() {
        var packet = new PacketSyncJeiRecipes(1, 0, 1, List.of(new byte[PacketSyncJeiRecipes.MAX_CHUNK_BYTES - 24]));
        assertEquals(PacketSyncJeiRecipes.MAX_CHUNK_BYTES, encoded(packet).length);
        assertEquals(PacketSyncJeiRecipes.MAX_CHUNK_BYTES - 24, roundtrip(packet).recipes().get(0).length);
        assertThrows(IllegalArgumentException.class, () -> new PacketSyncJeiRecipes(1, 0, 1,
            List.of(new byte[PacketSyncJeiRecipes.MAX_CHUNK_BYTES - 23])));
        reject(buffer -> buffer.writeZero(PacketSyncJeiRecipes.MAX_CHUNK_BYTES + 1));
    }
    @Test void malformedHeaderIsRejectedBeforeAllocatingDeclaredRecipeBodies() {
        reject(buffer -> header(buffer, 0, 0, 1, 0));
        reject(buffer -> header(buffer, 1, 0, 0, 0));
        reject(buffer -> header(buffer, 1, 0, PacketSyncJeiRecipes.MAX_CHUNKS + 1, 0));
        reject(buffer -> header(buffer, 1, -1, 1, 0));
        reject(buffer -> header(buffer, 1, 1, 1, 0));
        reject(buffer -> header(buffer, 1, 0, 1, -1));
        reject(buffer -> header(buffer, 1, 0, 1, PacketSyncJeiRecipes.MAX_RECIPES + 1));
    }
    @Test void truncationInvalidLengthsAndTrailingDataAreRejected() {
        reject(buffer -> buffer.writeZero(19));
        reject(buffer -> header(buffer, 1, 0, 1, 1));
        reject(buffer -> { header(buffer, 1, 0, 1, 1); buffer.writeInt(0); });
        reject(buffer -> { header(buffer, 1, 0, 1, 1); buffer.writeInt(-1); });
        reject(buffer -> { header(buffer, 1, 0, 1, 1); buffer.writeInt(PacketSyncJeiRecipes.MAX_CHUNK_BYTES); });
        reject(buffer -> { header(buffer, 1, 0, 1, 1); buffer.writeInt(2).writeByte(1); });
        reject(buffer -> { header(buffer, 1, 0, 1, 0); buffer.writeByte(1); });
    }

    private static void header(ByteBuf buffer, long revision, int index, int chunks, int count) {
        buffer.writeLong(revision).writeInt(index).writeInt(chunks).writeInt(count);
    }
    private static byte[] bytes(ByteBuf buffer) {
        byte[] result = new byte[buffer.readableBytes()]; buffer.getBytes(buffer.readerIndex(), result); return result;
    }
    private static byte[] encoded(PacketSyncJeiRecipes packet) {
        ByteBuf buffer = Unpooled.buffer();
        try { PacketSyncJeiRecipes.STREAM_CODEC.encode(buffer, packet); return bytes(buffer); }
        finally { buffer.release(); }
    }
    private static PacketSyncJeiRecipes roundtrip(PacketSyncJeiRecipes packet) {
        ByteBuf buffer = Unpooled.wrappedBuffer(encoded(packet));
        try { return PacketSyncJeiRecipes.STREAM_CODEC.decode(buffer); }
        finally { buffer.release(); }
    }
    private static void reject(Consumer<ByteBuf> fixture) {
        ByteBuf buffer = Unpooled.buffer();
        try { fixture.accept(buffer); assertThrows(DecoderException.class, () -> PacketSyncJeiRecipes.STREAM_CODEC.decode(buffer)); }
        finally { buffer.release(); }
    }
}
