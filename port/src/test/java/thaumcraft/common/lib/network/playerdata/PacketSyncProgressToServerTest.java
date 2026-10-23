package thaumcraft.common.lib.network.playerdata;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Codec-only tests: no handler, grant, registration, engine bootstrap or TCP claim. */
class PacketSyncProgressToServerTest {
    private static final String DIGEST = "0123456789abcdef".repeat(4);

    @Test
    void onlyThreeUntrustedRequestFieldsAndNoPlayerUuidOrPermissionBooleans() {
        assertEquals(List.of("researchKey", "expectedStage", "catalogDigest"),
            Arrays.stream(PacketSyncProgressToServer.class.getRecordComponents()).map(c -> c.getName()).toList());
        var packet = new PacketSyncProgressToServer("FIRSTSTEPS", -1, DIGEST);
        assertEquals("thaumcraft:sync_progress_to_server", packet.type().id().toString());
        assertEquals(50, packet.encodedBytes());
        assertEquals(packet, roundtrip(packet));
    }

    @Test
    void independentBigEndianFixtureMatchesExactKeyStageAndRawDigestBytes() {
        String key = "MiXeD 雪 😀";
        byte[] expected = wire(key.getBytes(StandardCharsets.UTF_8), 17);
        assertArrayEquals(expected, encoded(new PacketSyncProgressToServer(key, 17, DIGEST)));
    }

    @Test
    void transportPreservesOpaqueCaseWhitespaceAndQualifiedSyntaxWithoutAuthorizingIt() {
        for (String key : List.of("MiXeD", " !not-a-grant ", "[#]1543993555", "X@2", "A&&B||C", "~parent", "\u0000")) {
            assertEquals(key, roundtrip(new PacketSyncProgressToServer(key, 0, DIGEST)).researchKey());
        }
        for (int stage : new int[]{-1, 0, 1, Integer.MAX_VALUE}) {
            assertEquals(stage, roundtrip(new PacketSyncProgressToServer("X", stage, DIGEST)).expectedStage());
        }
    }

    @Test
    void characterAndUtf8BoundsAreIndependentAndExactMultibyteBoundaryFits() {
        assertEquals("X".repeat(256), roundtrip(new PacketSyncProgressToServer("X".repeat(256), -1, DIGEST)).researchKey());
        var exact = new PacketSyncProgressToServer("😀".repeat(128), 0, DIGEST);
        assertEquals(552, exact.encodedBytes());
        assertEquals(PacketSyncProgressToServer.MAX_ENCODED_BYTES, exact.encodedBytes());
        assertEquals(exact, roundtrip(exact));
        assertDoesNotThrow(() -> new PacketSyncProgressToServer("雪".repeat(170), 0, DIGEST));
        assertThrows(IllegalArgumentException.class, () -> new PacketSyncProgressToServer("雪".repeat(171), 0, DIGEST));
        assertThrows(IllegalArgumentException.class, () -> new PacketSyncProgressToServer("X".repeat(257), 0, DIGEST));
    }

    @Test
    void constructorsRejectEmptyNullMalformedUtf16InvalidStageAndNoncanonicalDigest() {
        assertThrows(NullPointerException.class, () -> new PacketSyncProgressToServer(null, 0, DIGEST));
        assertThrows(NullPointerException.class, () -> new PacketSyncProgressToServer("X", 0, null));
        for (String key : List.of("", "\uD800", "\uDC00", "x\uD800y")) {
            assertThrows(IllegalArgumentException.class, () -> new PacketSyncProgressToServer(key, 0, DIGEST));
        }
        for (int stage : new int[]{-2, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new PacketSyncProgressToServer("X", stage, DIGEST));
        }
        for (String digest : List.of("", DIGEST.substring(1), DIGEST + "0", DIGEST.toUpperCase(), "g".repeat(64), "\u0000".repeat(64))) {
            assertThrows(IllegalArgumentException.class, () -> new PacketSyncProgressToServer("X", 0, digest));
        }
    }

    @Test
    void decoderRejectsEveryTruncationAndAnyTrailingData() {
        byte[] valid = encoded(new PacketSyncProgressToServer("FIRSTSTEPS", -1, DIGEST));
        for (int length = 0; length < valid.length; length++) reject(Arrays.copyOf(valid, length));
        reject(Arrays.copyOf(valid, valid.length + 1));
        reject(Arrays.copyOf(valid, PacketSyncProgressToServer.MAX_ENCODED_BYTES + 1));
    }

    @Test
    void decoderRejectsZeroNegativeOversizedAndInconsistentLengthBeforeAllocation() {
        for (int length : new int[]{0, -1, Integer.MIN_VALUE, 513, Integer.MAX_VALUE, 2}) {
            byte[] bytes = wire(new byte[]{'X'}, 0);
            ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
            try { buffer.setInt(0, length); } finally { buffer.release(); }
            reject(bytes);
        }
        reject(wire("X".repeat(257).getBytes(StandardCharsets.UTF_8), 0));
    }

    @Test
    void decoderRejectsOverlongSurrogateInvalidContinuationAndIncompleteUtf8() {
        for (byte[] key : List.of(new byte[]{(byte) 0xc0, (byte) 0xaf},
            new byte[]{(byte) 0xed, (byte) 0xa0, (byte) 0x80}, new byte[]{(byte) 0x80},
            new byte[]{(byte) 0xe2, (byte) 0x82}, new byte[]{(byte) 0xf4, (byte) 0x90, (byte) 0x80, (byte) 0x80})) {
            reject(wire(key, 0));
        }
        reject(wire(new byte[]{'X'}, -2));
    }

    @Test
    void rawDigestIsOnlyRevisionMetadataAndIsNotAuthenticatedByCodec() {
        byte[] bytes = encoded(new PacketSyncProgressToServer("X", 0, DIGEST));
        bytes[bytes.length - 1] ^= 1;
        ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
        try {
            var decoded = PacketSyncProgressToServer.STREAM_CODEC.decode(buffer);
            assertNotEquals(DIGEST, decoded.catalogDigest());
            assertEquals(64, decoded.catalogDigest().length());
        } finally { buffer.release(); }
    }

    @Test
    void readerIndexOffsetWorksAndEncodingAppendsWithoutOverwritingPrefix() {
        var packet = new PacketSyncProgressToServer("OFFSET", 0, DIGEST);
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeLong(0x1020304050607080L);
            PacketSyncProgressToServer.STREAM_CODEC.encode(buffer, packet);
            assertEquals(0x1020304050607080L, buffer.readLong());
            assertEquals(packet, PacketSyncProgressToServer.STREAM_CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }

    @Test
    void everyBundledSourceKeyFitsTheRequestWithoutRewritingOrGrantingIt() throws Exception {
        int count = 0;
        for (String name : List.of("basics", "alchemy", "auromancy", "artifice", "infusion", "golemancy", "eldritch", "scans")) {
            String raw = Files.readString(Path.of("src", "generated", "resources", "data", "thaumcraft", "research", name + ".json"));
            for (var entry : JsonParser.parseString(raw).getAsJsonObject().getAsJsonArray("entries")) {
                String key = entry.getAsJsonObject().get("key").getAsString();
                assertEquals(key, roundtrip(new PacketSyncProgressToServer(key, -1, DIGEST)).researchKey());
                count++;
            }
        }
        assertEquals(148, count);
    }

    private static PacketSyncProgressToServer roundtrip(PacketSyncProgressToServer packet) {
        ByteBuf buffer = Unpooled.wrappedBuffer(encoded(packet));
        try {
            var decoded = PacketSyncProgressToServer.STREAM_CODEC.decode(buffer);
            assertFalse(buffer.isReadable());
            return decoded;
        } finally { buffer.release(); }
    }

    private static byte[] encoded(PacketSyncProgressToServer packet) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            PacketSyncProgressToServer.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet.encodedBytes(), buffer.readableBytes());
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            return bytes;
        } finally { buffer.release(); }
    }

    private static byte[] wire(byte[] key, int stage) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeInt(key.length); buffer.writeBytes(key); buffer.writeInt(stage);
            buffer.writeBytes(HexFormat.of().parseHex(DIGEST));
            byte[] bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes);
            return bytes;
        } finally { buffer.release(); }
    }

    private static void reject(byte[] bytes) {
        ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
        try { assertThrows(DecoderException.class, () -> PacketSyncProgressToServer.STREAM_CODEC.decode(buffer)); }
        finally { buffer.release(); }
    }
}
