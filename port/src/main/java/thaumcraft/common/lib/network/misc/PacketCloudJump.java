package thaumcraft.common.lib.network.misc;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** No coordinates/player identity/client velocity: server checks the current wearer and airborne state. */
public record PacketCloudJump(int sequence) implements CustomPacketPayload {
    public static final Type<PacketCloudJump> TYPE=new Type<>(ResourceLocation.parse("thaumcraft:cloud_jump"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PacketCloudJump> STREAM_CODEC=StreamCodec.of((buffer,packet)->buffer.writeVarInt(packet.sequence),buffer->new PacketCloudJump(buffer.readVarInt()));
    public PacketCloudJump {if(sequence<=0)throw new IllegalArgumentException("Cloud jump sequence must be positive");}
    @Override public Type<PacketCloudJump> type(){return TYPE;}
}
