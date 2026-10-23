package thaumcraft.common.golems.seals;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import thaumcraft.api.golems.seals.*;
import thaumcraft.common.golems.tasks.TaskHandler;
public final class SealEntity implements ISealEntity {
 public SealPos sealPos;public ISeal seal;private byte priority,color;private boolean locked,redstone,stopped;private String owner="";private BlockPos area=new BlockPos(1,1,1);
 public SealEntity(){}
 public SealEntity(Level level,SealPos position,ISeal value){sealPos=position;seal=value;if(value instanceof ISealConfigArea)area=new BlockPos(position.face.getStepX()==0?3:1,position.face.getStepY()==0?3:1,position.face.getStepZ()==0?3:1);}
 public void tickSealEntity(Level level){if(seal==null)return;if(isStoppedByRedstone(level)){if(!stopped)for(var t:TaskHandler.getTasks(level.dimension()).values())if(sealPos.equals(t.getSealPos()))t.setSuspended(true);stopped=true;return;}stopped=false;seal.tickSeal(level,this);}
 public boolean isStoppedByRedstone(Level level){return redstone&&(level.hasNeighborSignal(sealPos.pos)||level.hasChunkAt(sealPos.pos.relative(sealPos.face))&&level.hasNeighborSignal(sealPos.pos.relative(sealPos.face)));}
 public ISeal getSeal(){return seal;}public SealPos getSealPos(){return sealPos;}public byte getPriority(){return priority;}public void setPriority(byte b){priority=(byte)Math.max(-5,Math.min(5,b));}
 public byte getColor(){return color;}public void setColor(byte b){color=(byte)Math.max(0,Math.min(16,b));}public String getOwner(){return owner;}public void setOwner(String s){owner=s==null?"":s;}public boolean isLocked(){return locked;}public void setLocked(boolean b){locked=b;}public boolean isRedstoneSensitive(){return redstone;}public void setRedstoneSensitive(boolean b){redstone=b;}public BlockPos getArea(){return area;}
 public void setArea(BlockPos p){area=new BlockPos(Math.max(1,Math.min(8,p.getX())),Math.max(1,Math.min(8,p.getY())),Math.max(1,Math.min(8,p.getZ())));}
 public void readNBT(CompoundTag nbt,HolderLookup.Provider lookup){int face=nbt.getByte("face");if(face<0||face>5)throw new IllegalArgumentException("Seal face");sealPos=new SealPos(BlockPos.of(nbt.getLong("pos")),Direction.from3DDataValue(face));seal=SealHandler.newSeal(nbt.getString("type"));if(seal==null)throw new IllegalArgumentException("Unknown seal type");setPriority(nbt.getByte("priority"));setColor(nbt.getByte("color"));setOwner(nbt.getString("owner"));locked=nbt.getBoolean("locked");redstone=nbt.getBoolean("redstone");seal.readCustomNBT(nbt,lookup);if(seal instanceof ISealConfigArea&&nbt.contains("area",Tag.TAG_LONG))setArea(BlockPos.of(nbt.getLong("area")));if(seal instanceof ISealConfigToggles toggles)for(var t:toggles.getToggles())if(nbt.contains(t.key))t.value=nbt.getBoolean(t.key);}
 public CompoundTag writeNBT(HolderLookup.Provider lookup){var n=new CompoundTag();n.putLong("pos",sealPos.pos.asLong());n.putByte("face",(byte)sealPos.face.ordinal());n.putString("type",seal.getKey());n.putByte("priority",priority);n.putByte("color",color);n.putBoolean("locked",locked);n.putBoolean("redstone",redstone);n.putString("owner",owner);seal.writeCustomNBT(n,lookup);if(seal instanceof ISealConfigArea)n.putLong("area",area.asLong());if(seal instanceof ISealConfigToggles toggles)for(var t:toggles.getToggles())n.putBoolean(t.key,t.value);return n;}
 public void syncToClient(Level level){if(!level.isClientSide)Golem9SealNetwork.sync(level,this);}
 /** Replant task storage is server-private and may grow across a large farm. */
 public CompoundTag writeClientNBT(HolderLookup.Provider lookup){var n=writeNBT(lookup);n.remove("replant");if(n.sizeInBytes()>131072)throw new IllegalArgumentException("Seal client snapshot exceeds bound");return n;}
}
