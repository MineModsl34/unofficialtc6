package thaumcraft.common.lib.network.fx;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** Source S2C packet; no damage, target selection or gameplay is accepted from clients. */
public record PacketFXWispZap(int source,int target) implements CustomPacketPayload {
    public PacketFXWispZap{if(source<0||target<0)throw new IllegalArgumentException("Negative entity ID");}
    public static final Type<PacketFXWispZap> TYPE=new Type<>(ResourceLocation.parse("thaumcraft:fx_wisp_zap"));
    public static final StreamCodec<ByteBuf,PacketFXWispZap> STREAM_CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,PacketFXWispZap::source,ByteBufCodecs.VAR_INT,PacketFXWispZap::target,PacketFXWispZap::new);
    @Override public Type<PacketFXWispZap> type(){return TYPE;}
}
