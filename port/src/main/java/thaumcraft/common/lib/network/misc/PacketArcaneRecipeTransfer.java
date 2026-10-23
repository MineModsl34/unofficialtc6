package thaumcraft.common.lib.network.misc;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.nio.charset.StandardCharsets;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded request: the server resolves every ingredient/cost itself from this recipe ID.
 * No client-supplied items, slots, counts, output or research state are accepted.
 */
public record PacketArcaneRecipeTransfer(int menuId, int stateId, int requestId, ResourceLocation recipeId, boolean maximum) implements CustomPacketPayload {
    public static final int MAX_RECIPE_ID_LENGTH = 256, MAX_BYTES = 10 + MAX_RECIPE_ID_LENGTH;
    public static final Type<PacketArcaneRecipeTransfer> TYPE = new Type<>(ResourceLocation.parse("thaumcraft:arcane_recipe_transfer"));
    public static final StreamCodec<ByteBuf, PacketArcaneRecipeTransfer> STREAM_CODEC = StreamCodec.of((out, packet) -> {
        byte[] id = packet.recipeId().toString().getBytes(StandardCharsets.US_ASCII);
        out.writeByte(packet.menuId()); out.writeShort(packet.stateId()); out.writeByte(packet.maximum() ? 1 : 0);
        out.writeInt(packet.requestId()); out.writeShort(id.length); out.writeBytes(id);
    }, in -> {
        if (in.readableBytes() < 11 || in.readableBytes() > MAX_BYTES) throw new DecoderException("Arcane transfer body bounds");
        int menu = in.readUnsignedByte(), state = in.readUnsignedShort(), maximum = in.readUnsignedByte(), request = in.readInt(), size = in.readUnsignedShort();
        if (state > 32767 || maximum > 1 || request < 1 || size < 1 || size > MAX_RECIPE_ID_LENGTH || in.readableBytes() != size)
            throw new DecoderException("Arcane transfer identity bounds");
        String id = in.readCharSequence(size, StandardCharsets.US_ASCII).toString();
        ResourceLocation recipe = ResourceLocation.tryParse(id);
        if (recipe == null || !recipe.toString().equals(id)) throw new DecoderException("Arcane transfer recipe ID");
        return new PacketArcaneRecipeTransfer(menu, state, request, recipe, maximum == 1);
    });
    public PacketArcaneRecipeTransfer {
        if (menuId < 0 || menuId > 255 || stateId < 0 || stateId > 32767 || requestId < 1 || recipeId == null || recipeId.toString().length() > MAX_RECIPE_ID_LENGTH)
            throw new IllegalArgumentException("Arcane transfer identity");
    }
    @Override public Type<PacketArcaneRecipeTransfer> type() { return TYPE; }
}
