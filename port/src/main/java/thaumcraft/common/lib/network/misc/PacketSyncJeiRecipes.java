package thaumcraft.common.lib.network.misc;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/** Server-owned recipe bytes. Even an in-memory connection cannot borrow mutable API recipes. */
public final class PacketSyncJeiRecipes implements CustomPacketPayload {
    public static final int MAX_CHUNK_BYTES = 900 * 1024;
    public static final int MAX_CHUNKS = 128;
    public static final int MAX_RECIPES = 16384;
    public static final int MAX_SNAPSHOT_BYTES = 32 * 1024 * 1024;
    public static final Type<PacketSyncJeiRecipes> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "sync_jei_recipes"));
    public static final StreamCodec<ByteBuf, PacketSyncJeiRecipes> STREAM_CODEC = StreamCodec.of(
        (buffer, value) -> {
            buffer.writeLong(value.revision); buffer.writeInt(value.index); buffer.writeInt(value.chunks);
            buffer.writeInt(value.recipes.size());
            for (byte[] bytes : value.recipes) { buffer.writeInt(bytes.length); buffer.writeBytes(bytes); }
        }, buffer -> {
            if (buffer.readableBytes() > MAX_CHUNK_BYTES || buffer.readableBytes() < 20) throw new DecoderException("Invalid recipe chunk size");
            long revision = buffer.readLong(); int index = buffer.readInt(), chunks = buffer.readInt(), count = buffer.readInt();
            if (revision < 1 || chunks < 1 || chunks > MAX_CHUNKS || index < 0 || index >= chunks || count < 0 || count > MAX_RECIPES)
                throw new DecoderException("Invalid recipe chunk header");
            List<byte[]> recipes = new ArrayList<>(Math.min(count, 256));
            for (int i = 0; i < count; i++) {
                if (buffer.readableBytes() < 4) throw new DecoderException("Truncated recipe chunk");
                int length = buffer.readInt();
                if (length < 1 || length > MAX_CHUNK_BYTES - 24 || length > buffer.readableBytes()) throw new DecoderException("Invalid recipe length");
                byte[] body = new byte[length]; buffer.readBytes(body); recipes.add(body);
            }
            if (buffer.isReadable()) throw new DecoderException("Trailing recipe chunk bytes");
            return new PacketSyncJeiRecipes(revision, index, chunks, recipes);
        });
    private final long revision;
    private final int index, chunks;
    private final List<byte[]> recipes;

    public PacketSyncJeiRecipes(long revision, int index, int chunks, List<byte[]> recipes) {
        if (revision < 1 || chunks < 1 || chunks > MAX_CHUNKS || index < 0 || index >= chunks || recipes.size() > MAX_RECIPES)
            throw new IllegalArgumentException("Invalid recipe chunk header");
        int bytes = 20;
        List<byte[]> copy = new ArrayList<>();
        for (byte[] body : recipes) {
            if (body == null || body.length < 1 || body.length > MAX_CHUNK_BYTES - 24) throw new IllegalArgumentException("Invalid recipe body");
            bytes = Math.addExact(bytes, 4 + body.length);
            if (bytes > MAX_CHUNK_BYTES) throw new IllegalArgumentException("Recipe chunk exceeds payload bound");
            copy.add(body.clone());
        }
        this.revision = revision; this.index = index; this.chunks = chunks; this.recipes = List.copyOf(copy);
    }
    public long revision() { return revision; }
    public int index() { return index; }
    public int chunks() { return chunks; }
    public List<byte[]> recipes() { return recipes.stream().map(byte[]::clone).toList(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
