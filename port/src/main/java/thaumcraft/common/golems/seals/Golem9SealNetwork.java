package thaumcraft.common.golems.seals;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumcraft.common.golems.client.gui.*;
import thaumcraft.api.golems.seals.SealPos;
/** Seals use read-only client snapshots; client changes use native bound menu packets. */
public final class Golem9SealNetwork {
 private Golem9SealNetwork(){}
 public record Snapshot(ResourceKey<Level> dimension,ChunkPos chunk,boolean reset,List<CompoundTag> rows,int menu,int category)implements CustomPacketPayload{
  public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.parse("thaumcraft:seal_snapshot"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->{b.writeResourceLocation(p.dimension.location());b.writeLong(p.chunk.toLong());b.writeBoolean(p.reset);b.writeVarInt(p.rows.size());for(var n:p.rows)b.writeNbt(n);b.writeVarInt(p.menu);b.writeByte(p.category);},b->{var dim=ResourceKey.create(Registries.DIMENSION,b.readResourceLocation());var chunk=new ChunkPos(b.readLong());boolean reset=b.readBoolean();int size=b.readVarInt();if(size<0||size>16)throw new io.netty.handler.codec.DecoderException("Seal snapshot count");var rows=new ArrayList<CompoundTag>();for(int i=0;i<size;i++){var tag=b.readNbt(NbtAccounter.create(131072));if(!(tag instanceof CompoundTag n))throw new io.netty.handler.codec.DecoderException("Seal snapshot type");rows.add(n);}return new Snapshot(dim,chunk,reset,List.copyOf(rows),b.readVarInt(),b.readByte());});
  public Snapshot{if(rows.size()>16||menu< -1||category< -1||category>4)throw new IllegalArgumentException("Seal snapshot bound");}
  public Type<Snapshot> type(){return TYPE;}
 }
 public record LogisticsAction(int menu,int action,int slot,int count,String search)implements CustomPacketPayload{
  public static final Type<LogisticsAction> TYPE=new Type<>(ResourceLocation.parse("thaumcraft:logistics_action"));
  public static final StreamCodec<RegistryFriendlyByteBuf,LogisticsAction> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.menu);b.writeByte(p.action);b.writeByte(p.slot);b.writeVarInt(p.count);b.writeUtf(p.search,10);},b->new LogisticsAction(b.readVarInt(),b.readUnsignedByte(),b.readUnsignedByte(),b.readVarInt(),b.readUtf(10)));
  public LogisticsAction{if(menu<0||action<0||action>1||slot<0||slot>80||count<0||count>4096||search==null||search.length()>10)throw new IllegalArgumentException("Logistics action bound");}public Type<LogisticsAction> type(){return TYPE;}
 }
 public static void receive(Snapshot packet,IPayloadContext context){context.enqueueWork(()->{var world=context.player().level();if(!world.isClientSide||!world.dimension().equals(packet.dimension))return;var map=SealHandler.client(packet.dimension);if(packet.reset)map.entrySet().removeIf(e->new ChunkPos(e.getKey().pos).equals(packet.chunk));for(var tag:packet.rows){int face=tag.getByte("face");if(face<0||face>5)continue;var pos=new SealPos(BlockPos.of(tag.getLong("pos")),Direction.from3DDataValue(face));if(!new ChunkPos(pos.pos).equals(packet.chunk))continue;if(tag.getBoolean("removed"))map.remove(pos);else{try{var seal=new SealEntity();seal.readNBT(tag,world.registryAccess());map.put(pos,seal);if(context.player().containerMenu instanceof SealBaseContainer menu&&menu.containerId==packet.menu&&menu.seal.sealPos.equals(pos))menu.applySnapshot(tag,packet.category);}catch(RuntimeException bad){thaumcraft.Thaumcraft.log.warn("Refused invalid seal client snapshot at {}",pos.pos);}}}});}
 public static void receive(LogisticsAction action,IPayloadContext context){context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player&&player.containerMenu instanceof ContainerLogistics menu&&menu.containerId==action.menu&&menu.stillValid(player))menu.action(player,action.action,action.slot,action.count,action.search);});}
 public static void sync(Level world,SealEntity seal){if(world instanceof ServerLevel level)PacketDistributor.sendToPlayersTrackingChunk(level,new ChunkPos(seal.sealPos.pos),new Snapshot(level.dimension(),new ChunkPos(seal.sealPos.pos),false,List.of(seal.writeClientNBT(level.registryAccess())),-1,-1));}
 public static void menu(ServerPlayer player,SealBaseContainer menu){PacketDistributor.sendToPlayer(player,new Snapshot(player.level().dimension(),new ChunkPos(menu.seal.sealPos.pos),false,List.of(menu.seal.writeClientNBT(player.registryAccess())),menu.containerId,menu.category));}
 public static void remove(ServerLevel level,SealPos pos){var n=new CompoundTag();n.putLong("pos",pos.pos.asLong());n.putByte("face",(byte)pos.face.ordinal());n.putBoolean("removed",true);PacketDistributor.sendToPlayersTrackingChunk(level,new ChunkPos(pos.pos),new Snapshot(level.dimension(),new ChunkPos(pos.pos),false,List.of(n),-1,-1));}
 public static void chunk(ServerPlayer player,ServerLevel level,ChunkPos chunk){var rows=new ArrayList<CompoundTag>();boolean reset=true;for(var seal:SealHandler.getSealsInChunk(level,chunk)){try{rows.add(seal.writeClientNBT(level.registryAccess()));}catch(IllegalArgumentException invalid){thaumcraft.Thaumcraft.log.warn("Oversized addon seal snapshot withheld at {}",seal.sealPos.pos);continue;}if(rows.size()==4){PacketDistributor.sendToPlayer(player,new Snapshot(level.dimension(),chunk,reset,List.copyOf(rows),-1,-1));reset=false;rows.clear();}}PacketDistributor.sendToPlayer(player,new Snapshot(level.dimension(),chunk,reset,List.copyOf(rows),-1,-1));}
 public static void forget(ServerPlayer player,ServerLevel level,ChunkPos chunk){PacketDistributor.sendToPlayer(player,new Snapshot(level.dimension(),chunk,true,List.of(),-1,-1));}
}
