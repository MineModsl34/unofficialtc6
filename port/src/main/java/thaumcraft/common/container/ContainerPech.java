package thaumcraft.common.container;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import thaumcraft.common.entities.*;
import thaumcraft.common.entities.ai.pech.AIPechItemEntityGoto;
import thaumcraft.common.entities.monster.EntityPech;
import thaumcraft.common.lib.SoundsTC;

/** Server-authoritative source trade algorithm; standard menu-button payload is bound to this menu. */
public final class ContainerPech extends AbstractContainerMenu {
    private final EntityPech pech;private final Player owner;private final InventoryPech inventory=new InventoryPech();private long nextTrade;
    public ContainerPech(int id,Inventory playerInventory,FriendlyByteBuf data){this(id,playerInventory,playerInventory.player.level().getEntity(data.readVarInt()) instanceof EntityPech entity?entity:null);}
    public ContainerPech(int id,Inventory playerInventory,EntityPech entity){super(Mob8NaturalRegistries.PECH_MENU,id);pech=entity;owner=playerInventory.player;addSlot(new Slot(inventory,0,36,29));for(int y=0;y<2;y++)for(int x=0;x<2;x++)addSlot(new Slot(inventory,1+x+y*2,106+x*18,20+y*18){@Override public boolean mayPlace(ItemStack stack){return false;}});for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(new Slot(playerInventory,9+y*9+x,8+x*18,84+y*18));for(int x=0;x<9;x++)addSlot(new Slot(playerInventory,x,8+x*18,142));}
    public EntityPech getPech(){return pech;}public InventoryPech getMerchantInventory(){return inventory;}
    public boolean ready(){if(pech==null||!pech.isValued(inventory.getItem(0)))return false;for(int i=1;i<5;i++)if(!inventory.getItem(i).isEmpty())return false;return !inventory.getItem(0).isEmpty();}
    @Override public boolean stillValid(Player player){return pech!=null&&pech.isAlive()&&pech.isTamed()&&player==owner&&player.distanceToSqr(pech)<=64&&(player.level().isClientSide||pech.isTradingWith(player));}
    @Override public boolean clickMenuButton(Player player,int button){if(button!=0||player.level().isClientSide||player.containerMenu!=this||!stillValid(player)||!ready()||player.level().getGameTime()<nextTrade)return false;nextTrade=player.level().getGameTime()+2;generateContents();broadcastChanges();return true;}
    private void generateContents(){PechTrades.ensure(owner.level());int value=Math.max(0,Math.min(4096,pech.getValue(inventory.getItem(0))));var random=owner.level().getRandom();if(random.nextInt(100)<=value/2){pech.setTamed(false);pech.playSound(SoundsTC.pech_trade,.4F,1);}if(random.nextInt(5)==0)value+=random.nextInt(3);else if(random.nextBoolean())value-=random.nextInt(3);List<PechTrades.Trade> rows=EntityPech.tradeInventory.getOrDefault(pech.getPechType(),List.of());
        while(value>0){int tier=Mob8NaturalRules.tradeTier(value,random.nextInt(value));value-=tier;List<Integer> occupied=new ArrayList<>();for(int i=0;i<pech.loot.size();i++)if(!pech.loot.get(i).isEmpty())occupied.add(i);
            if(tier==1&&random.nextBoolean()&&!occupied.isEmpty()){int index=occupied.get(random.nextInt(occupied.size()));ItemStack reward=pech.loot.get(index).copyWithCount(1);if(addStack(reward))pech.loot.get(index).shrink(1);}
            else if(tier<4||!random.nextBoolean()){if(rows.stream().noneMatch(row->row.tier()==tier))continue;PechTrades.Trade selected=null;for(int attempts=0;attempts<1024;attempts++){var candidate=rows.get(random.nextInt(rows.size()));if(candidate.tier()==tier){selected=candidate;break;}}if(selected==null)continue;ItemStack reward=selected.stack();reward.onCraftedBy(owner.level(),owner,0);addStack(reward);}}
        inventory.removeItem(0,1);
    }
    /** Fix source reward-duplication loop. Strict '< max' and slot order retained; components cannot be laundered. */
    private boolean addStack(ItemStack reward){int[] counts=new int[4],limits=new int[4];boolean[] same=new boolean[4];for(int i=0;i<4;i++){var stack=inventory.getItem(i+1);counts[i]=stack.getCount();limits[i]=stack.isEmpty()?reward.getMaxStackSize():stack.getMaxStackSize();same[i]=ItemStack.isSameItemSameComponents(stack,reward);}int slot=Mob8NaturalRules.rewardSlot(counts,limits,same,reward.getCount());if(slot<0)return false;var current=inventory.getItem(slot+1);if(current.isEmpty())inventory.setItem(slot+1,reward);else{current.grow(reward.getCount());inventory.setChanged();}return true;}
    @Override public ItemStack quickMoveStack(Player player,int index){if(index<0||index>=slots.size())return ItemStack.EMPTY;Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;ItemStack stack=slot.getItem(),copy=stack.copy();if(index<5?!moveItemStackTo(stack,5,41,true):!moveItemStackTo(stack,0,1,true))return ItemStack.EMPTY;if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();if(stack.getCount()==copy.getCount())return ItemStack.EMPTY;slot.onTake(player,stack);return copy;}
    @Override public void removed(Player player){super.removed(player);if(pech!=null)pech.closeTrade(player);if(!player.level().isClientSide)for(int i=0;i<5;i++){ItemStack stack=inventory.removeItemNoUpdate(i);if(!stack.isEmpty()){var dropped=player.drop(stack,false);if(dropped!=null)dropped.getPersistentData().putBoolean(AIPechItemEntityGoto.TRADE_DROP,true);}}}
}
