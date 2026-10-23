package thaumcraft.client.lib;

import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.golems.parts.*;
import thaumcraft.api.golems.seals.SealPos;
import thaumcraft.common.golems.*;
import thaumcraft.common.golems.client.gui.*;
import thaumcraft.common.golems.seals.*;

/** Real owner menus, screen controls, C2S/S2C, and Provider delivery in the owned world. */
final class ClientSealLogisticsReview {
    private static final JsonObject REPORT=new JsonObject();
    private static CompletableFuture<Void> operation;
    private static volatile SealEntity seal;
    private static volatile BlockPos pos;
    private static volatile int golemId;
    private static int stage,ticks,settle,retries;
    private static boolean capturePending;
    private static Throwable captureFailure;
    static JsonObject evidence(){return REPORT.deepCopy();}
    static boolean tick(Minecraft mc) throws Exception {
        require(ClientJeiReviewScenario.owns(mc.getSingleplayerServer()),"Seal/logistics requires owned server"); if(REPORT.has("passed")&&REPORT.get("passed").getAsBoolean())return true; require(++ticks<900,"Seal/logistics timed out at "+stage+": "+REPORT); if(captureFailure!=null)throw new IllegalStateException(captureFailure);
        if(stage==0){ operation(mc,p->{p.closeContainer();var k=ThaumcraftCapabilities.getKnowledge(p);k.addResearch("GOLEMLOGISTICS");k.setResearchStage("GOLEMLOGISTICS",2); pos=p.blockPosition().offset(3,0,0); var level=p.serverLevel();for(var floor:BlockPos.betweenClosed(pos.offset(-5,-1,-4),pos.offset(3,-1,4)))level.setBlockAndUpdate(floor,Blocks.STONE.defaultBlockState());for(var space:BlockPos.betweenClosed(pos.offset(-5,0,-4),pos.offset(3,3,4)))level.setBlockAndUpdate(space,Blocks.AIR.defaultBlockState());for(var drop:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(6)))drop.discard();p.getInventory().clearContent();p.inventoryMenu.sendAllDataToRemote();level.setBlockAndUpdate(pos,Blocks.CHEST.defaultBlockState());
            seal=new SealEntity(level,new SealPos(pos,Direction.WEST),new SealFillAdvanced());seal.setOwner(p.getUUID().toString());seal.setLocked(true);require(SealHandler.addSealEntity(level,seal),"native advanced seal installed");SealHandler.openMenu(p,seal.getSealPos()); }); stage=1;return false; }
        if(stage==1){if(!done()||!(mc.screen instanceof SealBaseGUI)||!(mc.player.containerMenu instanceof SealBaseContainer menu)||menu.category!=1)return false;if(++settle<15)return false;
            capture(mc,"gameplay-advanced-seal.png");require(mc.screen.mouseClicked((mc.screen.width-176)/2-40,(mc.screen.height-232)/2+45,0),"actual seal category control click");stage=2;return false;}
        if(stage==2){if(capturePending||!(mc.player.containerMenu instanceof SealBaseContainer menu)||menu.category!=3)return false;if(++settle<30)return false;
            require(mc.screen.mouseClicked((mc.screen.width-176)/2+24,(mc.screen.height-232)/2+70,0),"actual existing-only toggle click");stage=3;return false;}
        if(stage==3){if(!(mc.player.containerMenu instanceof SealBaseContainer menu)||!((SealFillAdvanced)menu.seal.seal).getToggles()[4].value)return false;int id=menu.containerId;
            operation(mc,p->{require(p.containerMenu instanceof SealBaseContainer m&&m.containerId==id&&m.stillValid(p)&&((SealFillAdvanced)m.seal.seal).getToggles()[4].value,"actual seal C2S changed matching owner menu and returned S2C snapshot");REPORT.addProperty("advancedToggleNativeC2SS2C",true);p.closeContainer();SealHandler.removeSealEntity(p.serverLevel(),seal.getSealPos(),true);
                var chest=(ChestBlockEntity)p.serverLevel().getBlockEntity(pos);chest.setItem(0,new ItemStack(Items.IRON_INGOT,8));chest.setItem(1,new ItemStack(Items.GOLD_INGOT,5));
                seal=new SealEntity(p.serverLevel(),new SealPos(pos,Direction.WEST),new SealProvide());seal.setOwner(p.getUUID().toString());seal.setLocked(true);require(SealHandler.addSealEntity(p.serverLevel(),seal),"actual Provider installed");
                var props=GolemProperties.fromLong(0);props.setMaterial(java.util.Arrays.stream(GolemMaterial.getMaterials()).filter(x->x.key.equals("IRON")).findFirst().orElseThrow());props.setHead(java.util.Arrays.stream(GolemHead.getHeads()).filter(x->x.key.equals("SMART")).findFirst().orElseThrow());props.setArms(java.util.Arrays.stream(GolemArm.getArms()).filter(x->x.key.equals("FINE")).findFirst().orElseThrow());
                var golem=Golem9EntityRegistries.GOLEM.create(p.serverLevel());require(golem!=null,"actual delivery golem");golem.setPos(p.position().add(1,0,1));golem.setProperties(props);golem.updateEntityAttributes();golem.setHealth(golem.getMaxHealth());golem.setOwnerId(p.getUUID());golem.setOwned(true);golem.setValidSpawn();golem.setPersistenceRequired();require(p.serverLevel().addFreshEntity(golem),"actual delivery golem spawned");golemId=golem.getId();
                require(ContainerLogistics.open(p,p.blockPosition()),"actual researched owner opens logistics"); }); stage=4;settle=0;return false;}
        if(stage==4){if(!done()||!(mc.screen instanceof GuiLogistics)||!(mc.player.containerMenu instanceof ContainerLogistics menu)||java.util.stream.IntStream.range(0,81).map(i->menu.input.getItem(i).getCount()).sum()!=13)return false;if(++settle<15)return false;
            capture(mc,"gameplay-logistics.png");require(mc.screen.mouseClicked((mc.screen.width-215)/2+150,(mc.screen.height-215)/2+202,0),"native logistics search field focuses"); for(char c:"iron".toCharArray())require(mc.screen.charTyped(c,0),"native search character accepted");stage=5;return false;}
        if(stage==5){if(capturePending||!(mc.player.containerMenu instanceof ContainerLogistics menu)||!menu.input.getItem(0).is(Items.IRON_INGOT)||menu.input.getItem(0).getCount()!=8||!menu.input.getItem(1).isEmpty())return false;
            int id=menu.containerId;operation(mc,p->{require(p.containerMenu instanceof ContainerLogistics m&&m.containerId==id&&m.searchText.equals("iron")&&m.input.getItem(0).getCount()==8,"actual native search C2S applies only matching owner menu");REPORT.addProperty("logisticsSearchNativeC2SS2C",true);});stage=6;return false;}
        if(stage==6){if(!done())return false;int x=(mc.screen.width-215)/2,y=(mc.screen.height-215)/2;require(mc.screen.mouseClicked(x+24,y+24,0),"native catalogue slot selects cargo");mc.screen.mouseReleased(x+24,y+24,0);require(mc.screen.mouseClicked(x+105,y+203,0),"native logistics request button sends C2S");stage=7;return false;}
        if(stage==7){if(!done())return false;if(ticks%20!=0)return false;operation(mc,p->{var e=p.serverLevel().getEntity(golemId);require(e instanceof EntityThaumcraftGolem,"actual delivery golem remains alive");var golem=(EntityThaumcraftGolem)e;REPORT.addProperty("sourceCurrent",((ChestBlockEntity)p.serverLevel().getBlockEntity(pos)).getItem(0).getCount());REPORT.addProperty("ownerCurrent",p.getInventory().countItem(Items.IRON_INGOT));REPORT.addProperty("golemCargo",golem.getCarrying().stream().mapToInt(ItemStack::getCount).sum());REPORT.addProperty("golemWithinProviderRange",golem.isWithinRestriction(pos));REPORT.addProperty("activeRequests",thaumcraft.api.golems.GolemHelper.provisionRequests.getOrDefault(p.level().dimension(),new java.util.ArrayList<>()).stream().filter(r->r.getEntity()==p&&!r.isInvalid()).count());var tasks=thaumcraft.common.golems.tasks.TaskHandler.getTasks(p.level().dimension()).values().stream().filter(t->t.getSealPos().equals(seal.getSealPos())&&!t.isSuspended()).toList();REPORT.addProperty("activeTasks",tasks.size());REPORT.addProperty("eligibleTasks",tasks.stream().filter(t->t.canGolemPerformTask(golem)).count());REPORT.addProperty("golemTask",golem.getTask()==null?-1:golem.getTask().getData());});stage=8;return false;}
        if(stage==8){if(!done())return false;if(REPORT.get("ownerCurrent").getAsInt()<1){if(ticks>200&&retries<3&&REPORT.get("sourceCurrent").getAsInt()==8&&REPORT.get("golemCargo").getAsInt()==0&&REPORT.get("activeRequests").getAsInt()==0&&REPORT.get("activeTasks").getAsInt()==0){require(mc.screen.mouseClicked((mc.screen.width-215)/2+105,(mc.screen.height-215)/2+203,0),"native request retry after unlinked legacy ticket cleanup");retries++;REPORT.addProperty("nativeButtonRetries",retries);}stage=7;return false;}operation(mc,p->{require(p.getInventory().countItem(Items.IRON_INGOT)==1&&((ChestBlockEntity)p.serverLevel().getBlockEntity(pos)).getItem(0).getCount()==7,"native Provider/golem pays one source item and delivers actual requested cargo to owner");REPORT.addProperty("nativeRequestAndActualGolemDelivery",true);REPORT.addProperty("sourceBefore",8);REPORT.addProperty("sourceAfter",7);REPORT.addProperty("delivered",1);p.closeContainer();SealHandler.removeSealEntity(p.serverLevel(),seal.getSealPos(),true);var e=p.serverLevel().getEntity(golemId);if(e!=null)e.discard();});stage=9;return false;}
        if(!done()||mc.player.containerMenu instanceof ContainerLogistics||mc.player.getInventory().countItem(Items.IRON_INGOT)!=1)return false;REPORT.addProperty("deliveredInventoryS2C",true);REPORT.addProperty("passed",true);return true;
    }
    private static void operation(Minecraft mc,java.util.function.Consumer<ServerPlayer> action){var server=mc.getSingleplayerServer();var owner=mc.player.getUUID();operation=new CompletableFuture<>();var pending=operation;server.execute(()->{try{require(ClientJeiReviewScenario.owns(server),"owned server changed");var p=server.getPlayerList().getPlayer(owner);require(p!=null,"actual owner remains connected");action.accept(p);pending.complete(null);}catch(Throwable t){pending.completeExceptionally(t);}});}
    private static boolean done(){if(!operation.isDone())return false;operation.join();return true;}
    private static void capture(Minecraft mc,String name){var file=ClientJeiReviewScenario.directory().resolve("screenshots").resolve(name);require(!Files.exists(file),"fresh framebuffer");capturePending=true;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->mc.execute(()->{try{require(Files.isRegularFile(file)&&Files.size(file)>1024,"native screenshot complete");REPORT.addProperty(name,file.toString());}catch(Throwable t){captureFailure=t;}finally{capturePending=false;}}));}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
