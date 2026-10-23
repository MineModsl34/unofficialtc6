package thaumcraft.common.lib.network.misc;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.nio.charset.StandardCharsets;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumcraft.api.casters.ICaster;
import thaumcraft.common.items.casters.CasterManager;
import thaumcraft.common.items.tools.ItemElementalShovel;
/** Client asks only for focus key or bounded G modifier; server rereads the actual held stacks. */
public record PacketCasterAction(int action,int modifier,String focus) implements CustomPacketPayload {
    public static final Type<PacketCasterAction> TYPE=new Type<>(ResourceLocation.parse("thaumcraft:caster_action"));
    public static final StreamCodec<ByteBuf,PacketCasterAction> STREAM_CODEC=StreamCodec.of(PacketCasterAction::encode,PacketCasterAction::decode);
    public PacketCasterAction{if(action<0||action>1||modifier<0||modifier>2||focus==null||focus.length()>1024||action==1&&!focus.isEmpty())throw new IllegalArgumentException("Caster action");}
    public static PacketCasterAction focus(String key){return new PacketCasterAction(0,0,key);}public static PacketCasterAction misc(int mod){return new PacketCasterAction(1,mod,"");}
    private static void encode(ByteBuf out,PacketCasterAction p){byte[] text=p.focus.getBytes(StandardCharsets.UTF_8);out.writeByte(p.action).writeByte(p.modifier).writeShort(text.length).writeBytes(text);}
    private static PacketCasterAction decode(ByteBuf in){if(in.readableBytes()<4||in.readableBytes()>4100)throw new DecoderException("Caster action bounds");int action=in.readUnsignedByte(),mod=in.readUnsignedByte(),length=in.readUnsignedShort();if(length>4096||in.readableBytes()!=length)throw new DecoderException("Focus key bounds");String key=in.readCharSequence(length,StandardCharsets.UTF_8).toString();try{return new PacketCasterAction(action,mod,key);}catch(IllegalArgumentException e){throw new DecoderException(e);}}
    public static void handle(PacketCasterAction packet,IPayloadContext context){context.enqueueWork(()->{if(!(context.player() instanceof ServerPlayer player)||!player.isAlive()||player.isSpectator()||player.containerMenu!=player.inventoryMenu)return;var main=player.getMainHandItem();var off=player.getOffhandItem();var held=main.getItem() instanceof ICaster?main:off.getItem() instanceof ICaster?off:null;if(packet.action==0){if(held!=null){if(Boolean.getBoolean(thaumcraft.common.lib.validation.TcpCasterAuditIO.ENABLE_PROPERTY))thaumcraft.common.lib.validation.TcpCasterAudit.observeFocusSwitchBefore(player,held,packet);CasterManager.changeFocus(held,player.level(),player,packet.focus);if(Boolean.getBoolean(thaumcraft.common.lib.validation.TcpCasterAuditIO.ENABLE_PROPERTY))thaumcraft.common.lib.validation.TcpCasterAudit.observeFocusSwitchAfter(player,held,packet);}}else{if(held!=null)CasterManager.toggleMisc(held,player.level(),player,packet.modifier);if(main.getItem() instanceof ItemElementalShovel)ItemElementalShovel.cycleOrientation(main,player);player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();}});}
    @Override public Type<PacketCasterAction> type(){return TYPE;}
}
