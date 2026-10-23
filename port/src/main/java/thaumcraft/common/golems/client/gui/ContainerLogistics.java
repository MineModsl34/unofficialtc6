package thaumcraft.common.golems.client.gui;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.common.golems.seals.*;
public final class ContainerLogistics extends AbstractContainerMenu {
 public final InventoryLogistics input=new InventoryLogistics();public int start,end;public String searchText="";public final BlockPos target;public final Direction side;
 private final Player player;private final List<ItemStack> items=new ArrayList<>();private long lastRefresh=Long.MIN_VALUE,lastRequest=Long.MIN_VALUE;
 public ContainerLogistics(int id,Inventory inventory,BlockPos target,int face){super(Golem9SealRegistries.LOGISTICS_MENU,id);if(face< -1||face>5)throw new IllegalArgumentException("Logistics side");player=inventory.player;this.target=face<0?null:target.immutable();side=face<0?null:Direction.from3DDataValue(face);
  for(int i=0;i<81;i++)addSlot(new Slot(input,i,19+i%9*19,19+i/9*19){public boolean mayPlace(ItemStack s){return false;}public boolean mayPickup(Player p){return false;}});
  addDataSlot(new DataSlot(){public int get(){return start;}public void set(int v){start=v;}});addDataSlot(new DataSlot(){public int get(){return end;}public void set(int v){end=v;}});
  if(!player.level().isClientSide)refreshItemList(true);
 }
 public static boolean open(ServerPlayer player,BlockPos pos){if(!player.isAlive()||player.isSpectator()||!ThaumcraftCapabilities.knowsResearch(player,"GOLEMLOGISTICS"))return false;Direction side=null;
  if(!pos.equals(player.blockPosition())){if(!player.level().hasChunkAt(pos)||player.distanceToSqr(Vec3.atCenterOf(pos))>64||!player.level().mayInteract(player,pos))return false;var hit=player.level().clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(),player.getEyePosition().add(player.getLookAngle().scale(5)),net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,player));if(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos))side=hit.getDirection();if(side==null||SealInventoryOps.getItemHandlerAt(player.level(),pos,side)==null)return false;}
  final int face=side==null?-1:side.ordinal();player.openMenu(new SimpleMenuProvider((id,inv,p)->new ContainerLogistics(id,inv,pos,face),Component.translatable("container.logistics")),b->{b.writeBlockPos(pos);b.writeByte(face);});return true;
 }
 @Override public boolean stillValid(Player p){return p==player&&p.isAlive()&&!p.isSpectator()&&(p.level().isClientSide||ThaumcraftCapabilities.knowsResearch(p,"GOLEMLOGISTICS"))&&(target==null||p.level().hasChunkAt(target)&&p.distanceToSqr(Vec3.atCenterOf(target))<=64&&p.level().mayInteract(p,target));}
 public void refreshItemList(boolean full){if(full){items.clear();for(var seal:SealHandler.getSealsInRange(player.level(),player.blockPosition(),32))if(seal.seal instanceof SealProvide provider&&seal.getOwner().equals(player.getUUID().toString())){var inv=SealInventoryOps.getItemHandlerAt(player.level(),seal.sealPos.pos,seal.sealPos.face);if(inv==null)continue;for(int i=0;i<inv.getSlots();i++){var stack=inv.getStackInSlot(i);if(!provider.matchesFilters(stack)||!searchText.isEmpty()&&!stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(searchText.toLowerCase(Locale.ROOT)))continue;var old=items.stream().filter(s->ItemStack.isSameItemSameComponents(s,stack)).findFirst().orElse(null);if(old==null)items.add(stack.copy());else old.setCount((int)Math.min(Integer.MAX_VALUE,(long)old.getCount()+stack.getCount()));}}items.sort(Comparator.comparing(s->s.getHoverName().getString()));}
  end=Math.max(0,items.size()/9-8);start=Math.max(0,Math.min(end,start));input.clearContent();for(int i=0;i<81&&start*9+i<items.size();i++)input.setItem(i,items.get(start*9+i).copy());
 }
 @Override public void broadcastChanges(){if(!player.level().isClientSide&&SealRules.refreshDue(player.level().getGameTime(),lastRefresh)){lastRefresh=player.level().getGameTime();refreshItemList(true);}super.broadcastChanges();}
 @Override public boolean clickMenuButton(Player p,int id){if(!stillValid(p)||p.level().isClientSide)return false;if(id==22)refreshItemList(true);else if(id==0){start++;refreshItemList(false);}else if(id==1){start--;refreshItemList(false);}else if(id>=100&&id<=100+end){start=id-100;refreshItemList(false);}else return false;broadcastChanges();return true;}
 public void action(ServerPlayer p,int action,int slot,int count,String search){if(!stillValid(p))return;if(action==0){searchText=search;refreshItemList(true);broadcastChanges();return;}
  if(slot<0||slot>=81||count<1||count>4096||SealRules.requestThrottled(p.level().getGameTime(),lastRequest))return;var sample=input.getItem(slot);if(sample.isEmpty()||count>sample.getCount()||count>sample.getMaxStackSize()*64)return;lastRequest=p.level().getGameTime();int ui=0;for(int remaining=count;remaining>0;){int n=Math.min(remaining,sample.getMaxStackSize());var part=sample.copyWithCount(n);if(target==null)GolemHelper.requestProvisioning(p.level(),p,part,ui++);else if(SealInventoryOps.getItemHandlerAt(p.level(),target,side)!=null)GolemHelper.requestProvisioning(p.level(),target,side,part,ui++);else break;remaining-=n;}
 }
 @Override public void clicked(int slot,int button,ClickType type,Player p){} // Source full ghost catalogue may never yield an item.
 @Override public ItemStack quickMoveStack(Player p,int slot){return ItemStack.EMPTY;}
}
