package thaumcraft.common.lib.network.playerdata;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/**
 * A request, never a client grant. The source packet's first/checks/noFlags booleans are
 * deliberately NOT authority here: the contextual sender and current server data decide.
 * expectedStage is exact storage (-1 unknown, 0 known/unstarted, 1+ exposed stage).
 * The digest names the accepted raw catalog revision; it is neither authentication nor a
 * revision of arbitrary mutable/native addon API entries.
 *
 * Wire: int keyBytes, strict UTF-8 opaque key, int expectedStage, SHA-256[32]. No player UUID,
 * flags, requirements or item data. Decoder consumes the ENTIRE remaining payload body and
 * rejects malformed, oversized, truncated and trailing data before unbounded allocations.
 * This ByteBuf codec needs no engine/registry bootstrap; route tests are not TCP acceptance.
 */
public record PacketSyncProgressToServer(String researchKey, int expectedStage, String catalogDigest)
    implements CustomPacketPayload {
    public static final int MAX_KEY_CHARS = 256;
    public static final int MAX_KEY_UTF8_BYTES = 512;
    private static final int DIGEST_BYTES = 32;
    private static final int FIXED_BYTES = 2 * Integer.BYTES + DIGEST_BYTES;
    public static final int MAX_ENCODED_BYTES = FIXED_BYTES + MAX_KEY_UTF8_BYTES;
    public static final Type<PacketSyncProgressToServer> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "sync_progress_to_server"));
    public static final StreamCodec<ByteBuf, PacketSyncProgressToServer> STREAM_CODEC =
        StreamCodec.of(PacketSyncProgressToServer::encode, PacketSyncProgressToServer::decode);

    public PacketSyncProgressToServer {
        Objects.requireNonNull(researchKey, "research key");
        Objects.requireNonNull(catalogDigest, "accepted catalog digest");
        if (researchKey.isEmpty() || researchKey.length() > MAX_KEY_CHARS) {
            throw new IllegalArgumentException("Research key character bound");
        }
        if (strictUtf8(researchKey).length > MAX_KEY_UTF8_BYTES) {
            throw new IllegalArgumentException("Research key UTF-8 byte bound");
        }
        if (expectedStage < -1) throw new IllegalArgumentException("Invalid expected stored stage");
        if (catalogDigest.length() != 2 * DIGEST_BYTES) throw new IllegalArgumentException("Invalid catalog digest length");
        for (int i = 0; i < catalogDigest.length(); i++) {
            char c = catalogDigest.charAt(i);
            if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f')) {
                throw new IllegalArgumentException("Catalog digest must be lowercase hexadecimal");
            }
        }
    }

    @Override
    public Type<PacketSyncProgressToServer> type() { return TYPE; }

    public int encodedBytes() { return FIXED_BYTES + strictUtf8(researchKey).length; }

    private static void encode(ByteBuf buffer, PacketSyncProgressToServer payload) {
        byte[] key = strictUtf8(payload.researchKey);
        buffer.ensureWritable(FIXED_BYTES + key.length);
        buffer.writeInt(key.length);
        buffer.writeBytes(key);
        buffer.writeInt(payload.expectedStage);
        buffer.writeBytes(HexFormat.of().parseHex(payload.catalogDigest));
    }

    private static PacketSyncProgressToServer decode(ByteBuf buffer) {
        int available = buffer.readableBytes();
        if (available < FIXED_BYTES + 1 || available > MAX_ENCODED_BYTES) {
            throw invalid("Progress request body exceeds bounds or is truncated");
        }
        int length = buffer.readInt();
        if (length <= 0 || length > MAX_KEY_UTF8_BYTES) throw invalid("Research key byte bound");
        if (available != FIXED_BYTES + length) throw invalid("Truncated or trailing progress request body");
        byte[] key = new byte[length];
        buffer.readBytes(key);
        String decoded;
        try {
            decoded = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(key)).toString();
        } catch (CharacterCodingException malformed) {
            throw new DecoderException("Malformed research key UTF-8", malformed);
        }
        int stage = buffer.readInt();
        byte[] digest = new byte[DIGEST_BYTES];
        buffer.readBytes(digest);
        try {
            return new PacketSyncProgressToServer(decoded, stage, HexFormat.of().formatHex(digest));
        } catch (IllegalArgumentException invalid) {
            throw new DecoderException("Invalid progress request fields", invalid);
        }
    }

    /** Constructor checks the small character bound before this bounded encoding allocation. */
    private static byte[] strictUtf8(String value) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
            byte[] result = new byte[encoded.remaining()];
            encoded.get(result);
            return result;
        } catch (CharacterCodingException malformed) {
            throw new IllegalArgumentException("Malformed research key UTF-16", malformed);
        }
    }

    private static DecoderException invalid(String reason) { return new DecoderException(reason); }
}
