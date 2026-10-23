package thaumcraft.common.golems.client.gui;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.golems.seals.*;
import thaumcraft.common.golems.seals.*;
/** Stable native slot indices: source category ghost slots are merely hidden, not rebuilt. */
public final class SealBaseContainer extends AbstractContainerMenu {
 public final SealEntity seal;public final int[] categories;public int category;private final int ghosts;private final Player menuPlayer;
 public SealBaseContainer(int id,Inventory inv,SealEntity seal){super(Golem9SealRegistries.SEAL_MENU,id);menuPlayer=inv.player;this.seal=seal;categories=seal.seal instanceof ISealGui gui?gui.getGuiCategories():new int[]{0};category=categories[0];ghosts=seal.seal instanceof ISealConfigFilter f?f.getFilterSize():0;
  var temp=new SimpleContainer(ghosts);for(int i=0;i<ghosts;i++){final int index=i;int sx=16+(ghosts-1)%3*12,sy=16+(ghosts-1)/3*12;addSlot(new Slot(temp,i,88+i%3*24-sx+8,72+i/3*24-sy+8){public ItemStack getItem(){return ((ISealConfigFilter)SealBaseContainer.this.seal.seal).getFilterSlot(index);}public boolean isActive(){return category==1;}public boolean mayPlace(ItemStack s){return false;}public boolean mayPickup(Player p){return false;}});}
  for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(new Slot(inv,x+y*9+9,8+x*18,150+y*18));for(int x=0;x<9;x++)addSlot(new Slot(inv,x,8+x*18,208));
 }
 public void applySnapshot(CompoundTag n,int cat){seal.readNBT(n,menuPlayer.registryAccess());if(Arrays.stream(categories).anyMatch(x->x==cat))category=cat;}
 public boolean stillValid(Player player){if(player.level().isClientSide)return player.isAlive();return player instanceof ServerPlayer server&&player.isAlive()&&!player.isSpectator()&&SealHandler.getSealEntity(player.level(),seal.sealPos)==seal&&SealHandler.canPlayerEdit(server,seal);}
 private void changed(Player p){if(p instanceof ServerPlayer server){SealHandler.markChunkAsDirty(server.level().dimension(),seal.sealPos.pos);seal.syncToClient(server.level());Golem9SealNetwork.menu(server,this);broadcastChanges();}}
 public boolean clickMenuButton(Player player,int id){if(!stillValid(player)||player.level().isClientSide)return false;boolean owner=seal.getOwner().equals(player.getUUID().toString());
  if(id>=0&&id<categories.length)category=categories[id];
  else if(category==3&&seal.seal instanceof ISealConfigToggles t&&id>=30&&id<30+t.getToggles().length)t.setToggle(id-30,true);
  else if(category==3&&seal.seal instanceof ISealConfigToggles t&&id>=60&&id<60+t.getToggles().length)t.setToggle(id-60,false);
  else if(category==0&&id>=25&&id<=26&&owner)seal.setLocked(id==25);
  else if(id>=27&&id<=28&&owner)seal.setRedstoneSensitive(id==27);
  else if(category==1&&seal.seal instanceof ISealConfigFilter f&&id>=20&&id<=21)f.setBlacklist(id==20);
  else if(category==0&&id==80)seal.setPriority((byte)(seal.getPriority()-1));else if(category==0&&id==81)seal.setPriority((byte)(seal.getPriority()+1));
  else if(category==0&&id==82)seal.setColor((byte)(seal.getColor()-1));else if(category==0&&id==83)seal.setColor((byte)(seal.getColor()+1));
  else if(category==2&&seal.seal instanceof ISealConfigArea&&id>=90&&id<=95){int axis=(id-90)/2,step=(id&1)==0?-1:1;seal.setArea(seal.getArea().offset(axis==1?step:0,axis==0?step:0,axis==2?step:0));}
  else return false;changed(player);return true;
 }
 @Override public void clicked(int slot,int button,ClickType mode,Player player){if(!stillValid(player))return;if(slot>=0&&slot<ghosts){if(category!=1||button<0||button>1||mode!=ClickType.PICKUP&&mode!=ClickType.QUICK_MOVE)return;if(player.level().isClientSide)return;var f=(ISealConfigFilter)seal.seal;var carried=getCarried();var sample=f.getFilterSlot(slot);boolean limit=f.hasStacksizeLimiters();int size=f.getFilterSlotSize(slot),step=mode==ClickType.QUICK_MOVE?10:1;
   if(button==1){if(!limit){f.setFilterSlot(slot,ItemStack.EMPTY);size=0;}else if(carried.isEmpty()){if(!sample.isEmpty())size-=step;}else if(!sample.isEmpty()&&ItemStack.isSameItemSameComponents(carried,sample))size-=carried.getCount();if(size<0){f.setFilterSlot(slot,ItemStack.EMPTY);size=0;}}
   else if(carried.isEmpty()){if(limit&&!sample.isEmpty())size+=step;}else{if(carried.copyWithCount(1).save(player.registryAccess()).sizeInBytes()>8192)return;if(!limit)size=0;else if(ItemStack.isSameItemSameComponents(carried,sample))size+=carried.getCount();else size=0;f.setFilterSlot(slot,carried.copyWithCount(1));}f.setFilterSlotSize(slot,size);changed(player);return;}
  super.clicked(slot,button,mode,player);
 }
 @Override public ItemStack quickMoveStack(Player p,int slot){return ItemStack.EMPTY;}
}
