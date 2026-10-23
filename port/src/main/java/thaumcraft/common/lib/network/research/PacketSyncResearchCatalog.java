package thaumcraft.common.lib.network.research;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/**
 * S2C-only research metadata transport. There are deliberately no handlers, registry
 * hooks, JSON parsing, item resolution, player data or progression in this class. Registration
 * identities are the original locations (e.g. thaumcraft:research/basics WITHOUT an added .json
 * suffix); a future resource loader appends .json only when absent, as ResearchManager.java:263-265
 * did. Registered foo/foo.json remain DISTINCT identities even when their physical resource is
 * the same; this transport never merges/strips suffixes. Raw text and source
 * iteration order survive exactly, including opaque research keys, BOM, whitespace and case.
 *
 * Wire: int frameBytes, int version, int sourceCount, repeated [int idBytes, canonical UTF-8 ID,
 * int textBytes, strict UTF-8 text], SHA-256[32]. Lengths exclude their own prefix. The digest covers
 * version/count/all length-prefixed fields in order, not the outer length or digest. The decoder
 * expects the ENTIRE remaining custom-payload body and rejects trailing bytes inside/outside the
 * frame. SHA-256 is snapshot/integrity metadata, not authentication of a server or its JSON.
 *
 * Fixed-width bounded lengths avoid lenient UTF-8 and untrusted collection/string allocations.
 * Limits are below MC1.21.1 ClientboundCustomPayloadPacket's 1MiB custom-payload ceiling. This
 * StreamCodec uses ByteBuf (a supertype of RegistryFriendlyByteBuf) and needs no registry lookup.
 */
public final class PacketSyncResearchCatalog implements CustomPacketPayload {
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_SOURCES = 64;
    public static final int MAX_SOURCE_UTF8_BYTES = 512 * 1024;
    public static final int MAX_IDENTIFIER_UTF8_BYTES = 1024;
    /** Includes the outer frame-length prefix, but not the enclosing custom-payload type ID. */
    public static final int MAX_ENCODED_BYTES = 900 * 1024;
    private static final int DIGEST_BYTES = 32;
    private static final int MIN_FRAME_BYTES = 2 * Integer.BYTES + DIGEST_BYTES;

    public static final Type<PacketSyncResearchCatalog> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "sync_research_catalog"));
    public static final StreamCodec<ByteBuf, PacketSyncResearchCatalog> STREAM_CODEC =
        StreamCodec.of(PacketSyncResearchCatalog::encode, PacketSyncResearchCatalog::decode);

    private final Map<ResourceLocation, String> sources;
    private final List<EncodedSource> encodedSources;
    private final byte[] digest;
    private final int encodedBytes;

    /** Copies the ordered map; invalid UTF-16/oversized text is rejected BEFORE UTF-8 allocation. */
    public PacketSyncResearchCatalog(Map<ResourceLocation, String> sources) {
        Objects.requireNonNull(sources, "research sources");
        if (sources.size() > MAX_SOURCES) throw new IllegalArgumentException("Too many research sources");
        Map<ResourceLocation, String> copy = new LinkedHashMap<>();
        List<EncodedSource> encoded = new ArrayList<>();
        int totalSourceBytes = 0;
        int size = Integer.BYTES + MIN_FRAME_BYTES;
        for (var entry : sources.entrySet()) {
            if (encoded.size() >= MAX_SOURCES) throw new IllegalArgumentException("Too many research sources");
            ResourceLocation location = Objects.requireNonNull(entry.getKey(), "research registration identity");
            String raw = Objects.requireNonNull(entry.getValue(), "raw research source");
            String identity = location.toString();
            if (!validCanonicalIdentifier(identity)) throw new IllegalArgumentException("Invalid research registration identity");
            if (copy.containsKey(location)) throw new IllegalArgumentException("Duplicate research registration identity");
            int idBytes = strictUtf8Length(identity, MAX_IDENTIFIER_UTF8_BYTES);
            int textBytes = strictUtf8Length(raw, MAX_SOURCE_UTF8_BYTES - totalSourceBytes);
            totalSourceBytes += textBytes;
            size += 2 * Integer.BYTES + idBytes + textBytes;
            if (size > MAX_ENCODED_BYTES) throw new IllegalArgumentException("Encoded research payload exceeds bound");
            copy.put(location, raw);
            encoded.add(new EncodedSource(encodeUtf8(identity), encodeUtf8(raw)));
        }
        this.sources = Collections.unmodifiableMap(copy);
        encodedSources = List.copyOf(encoded);
        encodedBytes = size;
        MessageDigest hash = sha256();
        updateInt(hash, PROTOCOL_VERSION);
        updateInt(hash, encoded.size());
        for (EncodedSource source : encoded) {
            updateInt(hash, source.identity().length); hash.update(source.identity());
            updateInt(hash, source.text().length); hash.update(source.text());
        }
        digest = hash.digest();
    }

    public Map<ResourceLocation, String> sources() { return sources; }
    public int protocolVersion() { return PROTOCOL_VERSION; }
    public int encodedBytes() { return encodedBytes; }
    /** Lowercase SHA-256 of the exact ordered wire content, including lengths and version. */
    public String digest() { return HexFormat.of().formatHex(digest); }

    @Override
    public Type<PacketSyncResearchCatalog> type() { return TYPE; }

    private record EncodedSource(byte[] identity, byte[] text) {}

    private static void encode(ByteBuf buffer, PacketSyncResearchCatalog payload) {
        // Constructor established all limits and cached private immutable byte arrays before writes.
        buffer.ensureWritable(payload.encodedBytes);
        buffer.writeInt(payload.encodedBytes - Integer.BYTES);
        buffer.writeInt(PROTOCOL_VERSION);
        buffer.writeInt(payload.encodedSources.size());
        for (EncodedSource source : payload.encodedSources) {
            buffer.writeInt(source.identity().length); buffer.writeBytes(source.identity());
            buffer.writeInt(source.text().length); buffer.writeBytes(source.text());
        }
        buffer.writeBytes(payload.digest);
    }

    private static PacketSyncResearchCatalog decode(ByteBuf buffer) {
        int available = buffer.readableBytes();
        if (available < Integer.BYTES || available > MAX_ENCODED_BYTES) throw invalid("Encoded frame exceeds bounds or lacks length");
        int frameBytes = buffer.readInt();
        if (frameBytes < MIN_FRAME_BYTES || frameBytes > MAX_ENCODED_BYTES - Integer.BYTES) throw invalid("Invalid frame length");
        if (buffer.readableBytes() != frameBytes) throw invalid("Truncated frame or trailing outer bytes");
        // Bounded slices/views; do not allocate a byte[] from an untrusted declared frame length.
        ByteBuf frame = buffer.readSlice(frameBytes);
        int contentBytes = frameBytes - DIGEST_BYTES;
        MessageDigest hash = sha256();
        hash.update(frame.nioBuffer(0, contentBytes));
        byte[] expectedDigest = new byte[DIGEST_BYTES];
        frame.getBytes(contentBytes, expectedDigest);
        if (!MessageDigest.isEqual(hash.digest(), expectedDigest)) throw invalid("Research catalog digest mismatch");
        ByteBuf content = frame.slice(0, contentBytes);
        int version = readInt(content, "version");
        if (version != PROTOCOL_VERSION) throw invalid("Unsupported research catalog protocol version");
        int sourceCount = readInt(content, "source count");
        if (sourceCount < 0 || sourceCount > MAX_SOURCES) throw invalid("Invalid research source count");
        Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        int totalSourceBytes = 0;
        for (int i = 0; i < sourceCount; i++) {
            String identity = readUtf8(content, MAX_IDENTIFIER_UTF8_BYTES, "registration identity");
            if (!validCanonicalIdentifier(identity)) throw invalid("Invalid/noncanonical research registration identity");
            ResourceLocation location = ResourceLocation.tryParse(identity);
            if (location == null || !identity.equals(location.toString())) throw invalid("Invalid research registration identity");
            // Duplicate is rejected before even reading/allocating the second raw source field.
            if (sources.containsKey(location)) throw invalid("Duplicate research registration identity");
            int textBytes = readInt(content, "source text length");
            if (textBytes < 0 || textBytes > MAX_SOURCE_UTF8_BYTES - totalSourceBytes) throw invalid("Aggregate research source bytes exceed bound");
            totalSourceBytes += textBytes;
            sources.put(location, readUtf8Bytes(content, textBytes, "source text"));
        }
        if (content.isReadable()) throw invalid("Trailing bytes inside research catalog frame");
        // Re-encoding is bounded too; no raw mutable buffers/maps survive the decode boundary.
        return new PacketSyncResearchCatalog(sources);
    }

    private static int readInt(ByteBuf buffer, String field) {
        if (buffer.readableBytes() < Integer.BYTES) throw invalid("Truncated " + field);
        return buffer.readInt();
    }

    private static String readUtf8(ByteBuf buffer, int maxBytes, String field) {
        int bytes = readInt(buffer, field + " length");
        if (bytes < 0 || bytes > maxBytes) throw invalid("Invalid " + field + " byte length");
        return readUtf8Bytes(buffer, bytes, field);
    }

    private static String readUtf8Bytes(ByteBuf buffer, int bytes, String field) {
        if (bytes > buffer.readableBytes()) throw invalid("Truncated " + field);
        try {
            String value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(buffer.nioBuffer(buffer.readerIndex(), bytes)).toString();
            buffer.skipBytes(bytes);
            return value;
        } catch (CharacterCodingException malformed) {
            throw new DecoderException("Malformed UTF-8 in " + field, malformed);
        }
    }

    /** Counts UTF-8 bytes without allocating and rejects Java's unpaired UTF-16 surrogates. */
    private static int strictUtf8Length(String text, int maxBytes) {
        if (text.length() > maxBytes) throw new IllegalArgumentException("Research UTF-8 field exceeds byte bound");
        int bytes = 0;
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if (value < 0x80) bytes++;
            else if (value < 0x800) bytes += 2;
            else if (Character.isHighSurrogate(value)) {
                if (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1))) {
                    throw new IllegalArgumentException("Malformed UTF-16 research source");
                }
                i++; bytes += 4;
            } else if (Character.isLowSurrogate(value)) throw new IllegalArgumentException("Malformed UTF-16 research source");
            else bytes += 3;
            if (bytes > maxBytes) throw new IllegalArgumentException("Research UTF-8 field exceeds byte bound");
        }
        return bytes;
    }

    private static byte[] encodeUtf8(String text) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException malformed) {
            throw new IllegalArgumentException("Malformed UTF-16 research source", malformed);
        }
    }

    /** Require explicit, lowercase modern namespace:path, not ResourceLocation's implicit default. */
    private static boolean validCanonicalIdentifier(String identity) {
        int colon = identity.indexOf(':');
        if (colon <= 0 || colon == identity.length() - 1 || identity.indexOf(':', colon + 1) >= 0) return false;
        for (int i = 0; i < identity.length(); i++) {
            if (i == colon) continue;
            char value = identity.charAt(i);
            if (value >= 'a' && value <= 'z' || value >= '0' && value <= '9' || value == '_' || value == '-' || value == '.'
                || i > colon && value == '/') continue;
            return false;
        }
        return true;
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("Required SHA-256 unavailable", unavailable); }
    }

    private static void updateInt(MessageDigest hash, int value) {
        hash.update((byte) (value >>> 24)); hash.update((byte) (value >>> 16));
        hash.update((byte) (value >>> 8)); hash.update((byte) value);
    }

    private static DecoderException invalid(String message) { return new DecoderException(message); }
}
