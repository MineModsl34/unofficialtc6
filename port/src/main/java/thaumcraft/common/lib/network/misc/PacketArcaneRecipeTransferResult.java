package thaumcraft.common.lib.network.misc;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Receipt for one exact request; actual inventories still use vanilla menu synchronization. */
public record PacketArcaneRecipeTransferResult(int requestId, int menuId, int stateId, boolean accepted) implements CustomPacketPayload {
    public static final Type<PacketArcaneRecipeTransferResult> TYPE = new Type<>(ResourceLocation.parse("thaumcraft:arcane_recipe_transfer_result"));
    public static final StreamCodec<ByteBuf, PacketArcaneRecipeTransferResult> STREAM_CODEC = StreamCodec.of((out, packet) -> {
        out.writeInt(packet.requestId()); out.writeByte(packet.menuId()); out.writeShort(packet.stateId()); out.writeByte(packet.accepted() ? 1 : 0);
    }, in -> {
        if (in.readableBytes() != 8) throw new DecoderException("Arcane transfer receipt bounds");
        int request = in.readInt(), menu = in.readUnsignedByte(), state = in.readUnsignedShort(), accepted = in.readUnsignedByte();
        if (request < 1 || state > 32767 || accepted > 1) throw new DecoderException("Arcane transfer receipt identity");
        return new PacketArcaneRecipeTransferResult(request, menu, state, accepted == 1);
    });
    public PacketArcaneRecipeTransferResult {
        if (requestId < 1 || menuId < 0 || menuId > 255 || stateId < 0 || stateId > 32767) throw new IllegalArgumentException("Arcane transfer receipt identity");
    }
    @Override public Type<PacketArcaneRecipeTransferResult> type() { return TYPE; }
}
