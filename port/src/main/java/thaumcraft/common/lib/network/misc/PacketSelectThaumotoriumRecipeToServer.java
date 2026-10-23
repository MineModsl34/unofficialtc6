package thaumcraft.common.lib.network.misc;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The source misspelled packet name is retained; native recipe id replaces unstable legacy hash. */
public record PacketSelectThaumotoriumRecipeToServer(int menuId,BlockPos position,long session,long revision,long sequence,ResourceLocation recipe) implements CustomPacketPayload {
    public static final int MAX_BYTES = 1024;
    public static final Type<PacketSelectThaumotoriumRecipeToServer> TYPE = new Type<>(ResourceLocation.parse("thaumcraft:thaumatorium_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PacketSelectThaumotoriumRecipeToServer> STREAM_CODEC = StreamCodec.of(
        (out,packet) -> { out.writeInt(packet.menuId); out.writeLong(packet.position.asLong()); out.writeLong(packet.session); out.writeLong(packet.revision); out.writeLong(packet.sequence); out.writeUtf(packet.recipe.toString(),256); },
        in -> {
            if (in.readableBytes() < 37 || in.readableBytes() > MAX_BYTES) throw new DecoderException("Thaumatorium selection byte bound");
            int menu = in.readInt(); BlockPos pos = BlockPos.of(in.readLong()); long session = in.readLong(),revision = in.readLong(),sequence = in.readLong();
            ResourceLocation id = ResourceLocation.tryParse(in.readUtf(256));
            if (in.isReadable()) throw new DecoderException("Trailing thaumatorium selection data");
            try { return new PacketSelectThaumotoriumRecipeToServer(menu,pos,session,revision,sequence,id); }
            catch (IllegalArgumentException invalid) { throw new DecoderException("Invalid thaumatorium selection",invalid); }
        });
    public PacketSelectThaumotoriumRecipeToServer {
        if (menuId < 0 || position == null || session == 0 || revision < 0 || sequence <= 0 || recipe == null || recipe.toString().length() > 256) throw new IllegalArgumentException("Invalid thaumatorium selection");
        position = position.immutable();
    }
    @Override public Type<PacketSelectThaumotoriumRecipeToServer> type() { return TYPE; }
}
