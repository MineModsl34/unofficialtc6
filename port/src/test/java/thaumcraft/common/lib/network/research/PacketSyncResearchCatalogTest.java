package thaumcraft.common.lib.network.research;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** ByteBuf codec tests: no registry bootstrap, packet registration, client application or sockets. */
class PacketSyncResearchCatalogTest {
    private static final ResourceLocation FIRST = id("thaumcraft:research/basics");
    private static final ResourceLocation SECOND = id("addon:custom/catalog.json");

    @Test
    void typeVersionAndEmptyFrameHaveStableMetadataOnlyContract() {
        var packet = new PacketSyncResearchCatalog(Map.of());
        assertEquals(id("thaumcraft:sync_research_catalog"), packet.type().id());
        assertEquals(1, packet.protocolVersion());
        assertEquals(44, packet.encodedBytes());
        assertTrue(packet.sources().isEmpty());
        assertEquals(64, packet.digest().length());
        assertEquals(packet.digest(), roundtrip(packet).digest());
    }

    @Test
    void copiesOrderedMapAndNeverExposesMutableSources() {
        Map<ResourceLocation, String> input = new LinkedHashMap<>();
        input.put(SECOND, "second"); input.put(FIRST, "first");
        var packet = new PacketSyncResearchCatalog(input);
        input.clear(); input.put(id("addon:changed"), "changed");
        assertEquals(List.of(SECOND, FIRST), List.copyOf(packet.sources().keySet()));
        assertEquals("first", packet.sources().get(FIRST));
        assertThrows(UnsupportedOperationException.class, () -> packet.sources().clear());
        assertThrows(UnsupportedOperationException.class, () -> packet.sources().put(FIRST, "changed"));
        assertEquals(List.of(SECOND, FIRST), List.copyOf(roundtrip(packet).sources().keySet()));
    }

    @Test
    void exactRawTextWhitespaceBomUnicodeAndOpaqueResearchKeysSurvive() {
        String raw = "\uFEFF \r\n{\"entries\":[{\"key\":\"!Mixed@2||~X\",\"name\":\"Снег 雪 😀\"}]}\t\n";
        var packet = new PacketSyncResearchCatalog(Map.of(FIRST, raw));
        var decoded = roundtrip(packet);
        assertEquals(raw, decoded.sources().get(FIRST));
        assertArrayEquals(raw.getBytes(StandardCharsets.UTF_8), decoded.sources().get(FIRST).getBytes(StandardCharsets.UTF_8));
        assertEquals(packet.digest(), decoded.digest());
    }

    @Test
    void transportDoesNotParseOrRewriteJsonAndAcceptsEmptyRawSource() {
        Map<ResourceLocation, String> input = new LinkedHashMap<>();
        input.put(FIRST, "not JSON at all \u0000 { broken"); input.put(SECOND, "");
        assertEquals(input, roundtrip(new PacketSyncResearchCatalog(input)).sources());
    }

    @Test
    void originalRegistrationIdentityAndJsonSuffixVariantsStayDistinct() {
        ResourceLocation bare = id("addon:research/foo");
        ResourceLocation suffixed = id("addon:research/foo.json");
        Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        sources.put(bare, "same physical text"); sources.put(suffixed, "same physical text");
        var decoded = roundtrip(new PacketSyncResearchCatalog(sources));
        assertEquals(List.of(bare, suffixed), List.copyOf(decoded.sources().keySet()));
        assertEquals(2, decoded.sources().size());
        assertTrue(decoded.sources().containsKey(bare)); assertTrue(decoded.sources().containsKey(suffixed));
    }

    @Test
    void digestIsDeterministicButSensitiveToOrderIdentityAndExactRawText() {
        Map<ResourceLocation, String> forward = new LinkedHashMap<>();
        forward.put(FIRST, "raw"); forward.put(SECOND, "other");
        Map<ResourceLocation, String> reverse = new LinkedHashMap<>();
        reverse.put(SECOND, "other"); reverse.put(FIRST, "raw");
        var first = new PacketSyncResearchCatalog(forward);
        assertEquals(first.digest(), new PacketSyncResearchCatalog(new LinkedHashMap<>(forward)).digest());
        assertNotEquals(first.digest(), new PacketSyncResearchCatalog(reverse).digest());
        forward.put(FIRST, "raw ");
        assertNotEquals(first.digest(), new PacketSyncResearchCatalog(forward).digest());
        assertNotEquals(new PacketSyncResearchCatalog(Map.of(FIRST, "raw")).digest(), new PacketSyncResearchCatalog(Map.of(SECOND, "raw")).digest());
    }

    @Test
    void encoderMatchesIndependentLengthPrefixedBigEndianFixtureAndSha256() throws Exception {
        Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        sources.put(SECOND, "\r\nЯ雪😀"); sources.put(FIRST, " {} ");
        var packet = new PacketSyncResearchCatalog(sources);
        ByteBuf expected = wire(body -> {
            body.writeInt(1); body.writeInt(2);
            field(body, SECOND.toString()); field(body, sources.get(SECOND));
            field(body, FIRST.toString()); field(body, sources.get(FIRST));
        });
        try {
            byte[] actual = encoded(packet);
            assertArrayEquals(bytes(expected), actual);
            byte[] content = Arrays.copyOfRange(actual, 4, actual.length - 32);
            assertEquals(java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)), packet.digest());
        } finally { expected.release(); }
    }

    @Test
    void codecWorksAtNonzeroReaderWriterIndexesWithoutIncludingUnrelatedPrefix() {
        var packet = new PacketSyncResearchCatalog(Map.of(FIRST, "raw"));
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeInt(0xcafebabe);
            int start = buffer.writerIndex();
            PacketSyncResearchCatalog.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet.encodedBytes(), buffer.writerIndex() - start);
            assertEquals(0xcafebabe, buffer.getInt(0));
            buffer.readerIndex(start);
            assertEquals(packet.sources(), PacketSyncResearchCatalog.STREAM_CODEC.decode(buffer).sources());
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }

    @Test
    void decodedSnapshotDoesNotAliasInputBufferAndCanBeReencodedAfterRelease() {
        var packet = new PacketSyncResearchCatalog(Map.of(FIRST, "immutable raw"));
        ByteBuf buffer = Unpooled.wrappedBuffer(encoded(packet));
        PacketSyncResearchCatalog decoded;
        try {
            decoded = PacketSyncResearchCatalog.STREAM_CODEC.decode(buffer);
            buffer.setZero(0, buffer.capacity());
            assertEquals("immutable raw", decoded.sources().get(FIRST));
        } finally { buffer.release(); }
        assertEquals(packet.digest(), roundtrip(decoded).digest());
        assertEquals(packet.sources(), roundtrip(decoded).sources());
    }

    @Test
    void strictUtf8DecodesFromDirectAndFragmentedCompositeBuffers() {
        var packet = new PacketSyncResearchCatalog(Map.of(FIRST, "雪😀 Я"));
        byte[] encoded = encoded(packet);
        ByteBuf direct = Unpooled.directBuffer(encoded.length);
        ByteBuf composite = Unpooled.wrappedBuffer(Arrays.copyOfRange(encoded, 0, 9),
            Arrays.copyOfRange(encoded, 9, 23), Arrays.copyOfRange(encoded, 23, encoded.length));
        try {
            direct.writeBytes(encoded);
            assertEquals(packet.sources(), PacketSyncResearchCatalog.STREAM_CODEC.decode(direct).sources());
            assertEquals(packet.sources(), PacketSyncResearchCatalog.STREAM_CODEC.decode(composite).sources());
        } finally { direct.release(); composite.release(); }
    }

    @Test
    void sourceByteBoundAllowsExactAsciiLimitAndRejectsOneExtraByte() {
        String maximum = "x".repeat(PacketSyncResearchCatalog.MAX_SOURCE_UTF8_BYTES);
        var packet = new PacketSyncResearchCatalog(Map.of(FIRST, maximum));
        assertEquals(maximum, roundtrip(packet).sources().get(FIRST));
        assertTrue(packet.encodedBytes() < PacketSyncResearchCatalog.MAX_ENCODED_BYTES);
        assertThrows(IllegalArgumentException.class, () -> new PacketSyncResearchCatalog(Map.of(FIRST, maximum + "x")));
    }

    @Test
    void sourceByteBoundCountsMultibyteCharactersAndSurrogatePairsRatherThanJavaChars() {
        for (String maximum : List.of("é".repeat(PacketSyncResearchCatalog.MAX_SOURCE_UTF8_BYTES / 2),
            "雪".repeat(PacketSyncResearchCatalog.MAX_SOURCE_UTF8_BYTES / 3) + "a".repeat(PacketSyncResearchCatalog.MAX_SOURCE_UTF8_BYTES % 3),
            "😀".repeat(PacketSyncResearchCatalog.MAX_SOURCE_UTF8_BYTES / 4))) {
            assertEquals(PacketSyncResearchCatalog.MAX_SOURCE_UTF8_BYTES, maximum.getBytes(StandardCharsets.UTF_8).length);
            assertEquals(maximum, roundtrip(new PacketSyncResearchCatalog(Map.of(FIRST, maximum))).sources().get(FIRST));
            assertThrows(IllegalArgumentException.class, () -> new PacketSyncResearchCatalog(Map.of(FIRST, maximum + "x")));
        }
    }

    @Test
    void constructorEnforcesAggregateRatherThanOnlyIndividualSourceBound() {
        Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        sources.put(FIRST, "x".repeat(PacketSyncResearchCatalog.MAX_SOURCE_UTF8_BYTES)); sources.put(SECOND, "");
        assertEquals(sources, roundtrip(new PacketSyncResearchCatalog(sources)).sources());
        sources.put(SECOND, "x");
        assertThrows(IllegalArgumentException.class, () -> new PacketSyncResearchCatalog(sources));
    }

    @Test
    void constructorAllows64SourcesAndRejects65() {
        Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        for (int i = 0; i < 64; i++) sources.put(id("addon:research/" + i), "");
        assertEquals(64, roundtrip(new PacketSyncResearchCatalog(sources)).sources().size());
        sources.put(id("addon:research/extra"), "");
        assertThrows(IllegalArgumentException.class, () -> new PacketSyncResearchCatalog(sources));
    }

    @Test
    void maximumSourceAndIdentifierBudgetsRemainBelow900KiBAndVanilla1MiB() {
        Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        for (int i = 0; i < 64; i++) {
            String suffix = Integer.toString(i);
            String location = "addon:" + "x".repeat(PacketSyncResearchCatalog.MAX_IDENTIFIER_UTF8_BYTES - 6 - suffix.length()) + suffix;
            sources.put(id(location), "x".repeat(PacketSyncResearchCatalog.MAX_SOURCE_UTF8_BYTES / 64));
        }
        var packet = new PacketSyncResearchCatalog(sources);
        assertEquals(44 + 64 * (8 + 1024) + 512 * 1024, packet.encodedBytes());
        assertTrue(packet.encodedBytes() <= PacketSyncResearchCatalog.MAX_ENCODED_BYTES);
        assertTrue(packet.encodedBytes() + PacketSyncResearchCatalog.TYPE.id().toString().length() + 5 < 1_048_576);
        assertEquals(sources, roundtrip(packet).sources());
    }

    @Test
    void constructorRejectsOverlongAndEmptyRegistrationIdentities() {
        ResourceLocation maximum = id("aa:" + "x".repeat(1021));
        assertEquals(maximum, roundtrip(new PacketSyncResearchCatalog(Map.of(maximum, ""))).sources().keySet().iterator().next());
        assertThrows(IllegalArgumentException.class, () -> new PacketSyncResearchCatalog(Map.of(id("aa:" + "x".repeat(1022)), "")));
        assertThrows(IllegalArgumentException.class, () -> new PacketSyncResearchCatalog(Map.of(ResourceLocation.fromNamespaceAndPath("minecraft", ""), "")));
        assertThrows(IllegalArgumentException.class, () -> new PacketSyncResearchCatalog(Map.of(ResourceLocation.fromNamespaceAndPath("", "valid"), "")));
    }

    @Test
    void constructorRejectsMalformedUtf16InsteadOfReplacingItDuringEncoding() {
        for (String malformed : List.of("before" + (char) 0xd800 + "after", "before" + (char) 0xdc00 + "after",
            "" + (char) 0xd800 + (char) 0xd800, "" + (char) 0xdc00 + (char) 0xd800)) {
            assertThrows(IllegalArgumentException.class, () -> new PacketSyncResearchCatalog(Map.of(FIRST, malformed)));
        }
    }

    @Test
    void nullSourcesKeysAndTextsFailConstructor() {
        assertThrows(NullPointerException.class, () -> new PacketSyncResearchCatalog(null));
        Map<ResourceLocation, String> source = new LinkedHashMap<>(); source.put(null, "raw");
        assertThrows(NullPointerException.class, () -> new PacketSyncResearchCatalog(source));
        source.clear(); source.put(FIRST, null);
        assertThrows(NullPointerException.class, () -> new PacketSyncResearchCatalog(source));
    }

    @Test
    void decoderRejectsNegativeTinyAndHugeDeclaredFramesBeforeAllocating() {
        for (int length : new int[]{-1, Integer.MIN_VALUE, 0, 39, PacketSyncResearchCatalog.MAX_ENCODED_BYTES, Integer.MAX_VALUE}) {
            ByteBuf buffer = Unpooled.buffer(4);
            try {
                buffer.writeInt(length);
                assertThrows(DecoderException.class, () -> PacketSyncResearchCatalog.STREAM_CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
    }

    @Test
    void decoderRejectsActualOversizedFrameWithoutEvenReadingLength() {
        ByteBuf buffer = Unpooled.buffer(PacketSyncResearchCatalog.MAX_ENCODED_BYTES + 1);
        try {
            buffer.writeZero(PacketSyncResearchCatalog.MAX_ENCODED_BYTES + 1);
            assertEquals(0, buffer.readerIndex());
            assertThrows(DecoderException.class, () -> PacketSyncResearchCatalog.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readerIndex(), "actual bound check precedes any field read/allocation");
        } finally { buffer.release(); }
    }

    @Test
    void everyTruncationOfSmallValidFrameIsRejected() {
        byte[] valid = encoded(new PacketSyncResearchCatalog(Map.of()));
        for (int length = 0; length < valid.length; length++) {
            ByteBuf buffer = Unpooled.wrappedBuffer(Arrays.copyOf(valid, length));
            try { assertThrows(DecoderException.class, () -> PacketSyncResearchCatalog.STREAM_CODEC.decode(buffer)); }
            finally { buffer.release(); }
        }
    }

    @Test
    void decoderRejectsTrailingBytesOutsideDeclaredFrame() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            PacketSyncResearchCatalog.STREAM_CODEC.encode(buffer, new PacketSyncResearchCatalog(Map.of())); buffer.writeByte(99);
            assertTrue(assertThrows(DecoderException.class, () -> PacketSyncResearchCatalog.STREAM_CODEC.decode(buffer)).getMessage().contains("outer"));
        } finally { buffer.release(); }
    }

    @Test
    void decoderRejectsTrailingBytesInsideCorrectlyHashedFrame() {
        rejects(body -> { body.writeInt(1); body.writeInt(0); body.writeByte(99); }, "Trailing bytes inside");
    }

    @Test
    void decoderRejectsUnsupportedVersionsBeforeParsingAnySources() {
        for (int version : new int[]{-1, 0, 2, Integer.MAX_VALUE}) {
            rejects(body -> { body.writeInt(version); body.writeInt(0); }, "Unsupported");
        }
    }

    @Test
    void decoderRejectsNegativeAndHugeSourceCountsBeforeCollectionAllocation() {
        for (int count : new int[]{-1, 65, Integer.MAX_VALUE}) {
            rejects(body -> { body.writeInt(1); body.writeInt(count); }, "source count");
        }
    }

    @Test
    void decoderRejectsNegativeAndHugeIdentifierLengthsBeforeStringAllocation() {
        for (int length : new int[]{-1, 1025, Integer.MAX_VALUE}) {
            rejects(body -> { body.writeInt(1); body.writeInt(1); body.writeInt(length); }, "identity byte length");
        }
    }

    @Test
    void decoderRejectsNegativeAndHugeRawTextLengthsBeforeStringAllocation() {
        for (int length : new int[]{-1, PacketSyncResearchCatalog.MAX_SOURCE_UTF8_BYTES + 1, Integer.MAX_VALUE}) {
            rejects(body -> { body.writeInt(1); body.writeInt(1); field(body, FIRST.toString()); body.writeInt(length); }, "Aggregate");
        }
    }

    @Test
    void decoderChecksAggregateSourceByteBudgetAcrossDistinctSources() {
        rejects(body -> {
            body.writeInt(1); body.writeInt(2); field(body, FIRST.toString());
            field(body, "x".repeat(PacketSyncResearchCatalog.MAX_SOURCE_UTF8_BYTES));
            field(body, SECOND.toString()); body.writeInt(1); body.writeByte('x');
        }, "Aggregate");
    }

    @Test
    void decoderRejectsTruncatedBoundedFieldsBeforeNioViewOrStringAllocation() {
        rejects(body -> { body.writeInt(1); body.writeInt(1); body.writeInt(10); body.writeByte('x'); }, "Truncated registration");
        rejects(body -> { body.writeInt(1); body.writeInt(1); field(body, FIRST.toString()); body.writeInt(10); }, "Truncated source");
        rejects(body -> { body.writeInt(1); body.writeInt(1); field(body, FIRST.toString()); }, "Truncated source text length");
    }

    @Test
    void decoderRejectsAllMalformedUtf8RawSourcesRatherThanReplacingBytes() {
        List<byte[]> malformed = List.of(new byte[]{(byte) 0x80}, new byte[]{(byte) 0xc0, (byte) 0xaf}, new byte[]{(byte) 0xc2},
            new byte[]{(byte) 0xe2, (byte) 0x82}, new byte[]{(byte) 0xed, (byte) 0xa0, (byte) 0x80},
            new byte[]{(byte) 0xf4, (byte) 0x90, (byte) 0x80, (byte) 0x80}, new byte[]{(byte) 0xe2, 0x28, (byte) 0xa1},
            new byte[]{(byte) 0xf0, (byte) 0x80, (byte) 0x80, (byte) 0xaf}, new byte[]{(byte) 0xff});
        for (byte[] raw : malformed) rejects(body -> {
            body.writeInt(1); body.writeInt(1); field(body, FIRST.toString()); field(body, raw);
        }, "Malformed UTF-8 in source text");
    }

    @Test
    void decoderRejectsMalformedUtf8IdentifiersBeforeResourceLocationParsing() {
        rejects(body -> { body.writeInt(1); body.writeInt(1); field(body, new byte[]{(byte) 0xc0, (byte) 0xaf}); field(body, ""); },
            "Malformed UTF-8 in registration identity");
    }

    @Test
    void decoderRejectsNoncanonicalInvalidAndImplicitDefaultRegistrationIds() {
        for (String identity : List.of("", "implicit", ":foo", "minecraft:", "MineCraft:foo", "minecraft:Upper", "minecraft:foo:bar",
            "mine/craft:foo", "мод:research", "minecraft:foo space", "minecraft:foo\u0000", "minecraft:foo\\bar")) {
            rejects(body -> { body.writeInt(1); body.writeInt(1); field(body, identity); field(body, ""); }, "registration identity");
        }
    }

    @Test
    void decoderRejectsDuplicateIdentityBeforeReadingItsHugeSecondTextLength() {
        rejects(body -> {
            body.writeInt(1); body.writeInt(2); field(body, FIRST.toString()); field(body, "first raw");
            field(body, FIRST.toString()); body.writeInt(Integer.MAX_VALUE);
        }, "Duplicate research registration identity");
    }

    @Test
    void decoderRejectsCorruptDigestAndRawContentTamperingBeforeTextAllocation() {
        byte[] original = encoded(new PacketSyncResearchCatalog(Map.of(FIRST, "raw")));
        for (int changedIndex : new int[]{4, original.length - 33, original.length - 1}) {
            byte[] corrupt = original.clone(); corrupt[changedIndex] ^= 1;
            ByteBuf buffer = Unpooled.wrappedBuffer(corrupt);
            try {
                assertTrue(assertThrows(DecoderException.class, () -> PacketSyncResearchCatalog.STREAM_CODEC.decode(buffer)).getMessage().contains("digest mismatch"));
            } finally { buffer.release(); }
        }
    }

    @Test
    void privateEightSourceFilesRoundtripAsOriginalRegistrationIdsAndExactRawText() throws Exception {
        String configured = System.getProperty("thaumcraft.originalResearchDirectory");
        Path directory = configured == null ? Path.of("..", "reference", "tc6", "src", "assets", "thaumcraft", "research") : Path.of(configured);
        Assumptions.assumeTrue(Files.isDirectory(directory), "Private original sources are never bundled by transport");
        Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        int total = 0;
        for (String name : List.of("basics", "alchemy", "auromancy", "artifice", "infusion", "golemancy", "eldritch", "scans")) {
            String raw = Files.readString(directory.resolve(name + ".json"), StandardCharsets.UTF_8);
            sources.put(id("thaumcraft:research/" + name), raw); total += raw.getBytes(StandardCharsets.UTF_8).length;
        }
        assertTrue(total <= PacketSyncResearchCatalog.MAX_SOURCE_UTF8_BYTES);
        var packet = new PacketSyncResearchCatalog(sources);
        var decoded = roundtrip(packet);
        assertEquals(sources, decoded.sources());
        assertEquals(List.copyOf(sources.keySet()), List.copyOf(decoded.sources().keySet()));
        assertTrue(decoded.sources().keySet().stream().noneMatch(location -> location.getPath().endsWith(".json")));
        assertEquals(packet.digest(), decoded.digest());
    }

    private static PacketSyncResearchCatalog roundtrip(PacketSyncResearchCatalog packet) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            PacketSyncResearchCatalog.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet.encodedBytes(), buffer.readableBytes());
            PacketSyncResearchCatalog result = PacketSyncResearchCatalog.STREAM_CODEC.decode(buffer);
            assertFalse(buffer.isReadable()); return result;
        } finally { buffer.release(); }
    }

    private static byte[] encoded(PacketSyncResearchCatalog packet) {
        ByteBuf buffer = Unpooled.buffer();
        try { PacketSyncResearchCatalog.STREAM_CODEC.encode(buffer, packet); return bytes(buffer); }
        finally { buffer.release(); }
    }

    private static byte[] bytes(ByteBuf buffer) {
        byte[] copy = new byte[buffer.readableBytes()]; buffer.getBytes(buffer.readerIndex(), copy); return copy;
    }

    /** Independent fixture writer; raw fields may deliberately violate the production limits. */
    private static ByteBuf wire(Consumer<ByteBuf> writer) {
        ByteBuf body = Unpooled.buffer();
        try {
            writer.accept(body);
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update(body.nioBuffer(0, body.writerIndex()));
            byte[] digest = hash.digest();
            ByteBuf wire = Unpooled.buffer();
            wire.writeInt(body.writerIndex() + digest.length); wire.writeBytes(body, 0, body.writerIndex()); wire.writeBytes(digest);
            return wire;
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        finally { body.release(); }
    }

    private static void rejects(Consumer<ByteBuf> writer, String message) {
        ByteBuf buffer = wire(writer);
        try {
            DecoderException error = assertThrows(DecoderException.class, () -> PacketSyncResearchCatalog.STREAM_CODEC.decode(buffer));
            assertTrue(error.getMessage().contains(message), error.getMessage());
        } finally { buffer.release(); }
    }

    private static void field(ByteBuf buffer, String value) { field(buffer, value.getBytes(StandardCharsets.UTF_8)); }
    private static void field(ByteBuf buffer, byte[] bytes) { buffer.writeInt(bytes.length); buffer.writeBytes(bytes); }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
}
