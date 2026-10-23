package thaumcraft.common.lib.validation;

import static thaumcraft.common.lib.validation.TcpCasterAuditIO.*;
import static thaumcraft.common.lib.validation.TcpCasterScenario.*;

import com.google.gson.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.*;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.*;
import thaumcraft.api.items.*;
import thaumcraft.api.research.*;
import thaumcraft.common.container.*;
import thaumcraft.common.items.casters.*;
import thaumcraft.common.lib.crafting.*;
import thaumcraft.common.lib.network.misc.PacketCasterAction;
import thaumcraft.common.lib.network.playerdata.*;
import thaumcraft.common.lib.research.*;
import thaumcraft.common.lib.utils.ScanTargeting;
import thaumcraft.common.tiles.crafting.*;
import thaumcraft.common.world.aura.AuraHandler;

/** Dedicated native caster acceptance. Observers cannot author research, focus packages,
 * costs, aura, XP, damage, card outcomes or recipe effects. Only reviewed tangible fixtures
 * and teleports are written in generation; copied reloads write none. */
@EventBusSubscriber(modid=Thaumcraft.MODID)
public final class TcpCasterAudit {
    private static Run active;
    private TcpCasterAudit() {}
    private interface Observation { void accept(Run run) throws Exception; }
    private static void observe(Observation action) {
        Run r=active;if(!Boolean.getBoolean(ENABLE_PROPERTY)||r==null||r.finished)return;
        try { action.accept(r); } catch(Exception failure) { r.finish(false,failure.toString()); }
    }
    public static void observeRequest(ServerPlayer p,PacketSyncProgressToServer packet,ServerResearchRequestHandler.Result result) { observe(r->r.research(p,packet,result)); }
    public static void observeArcanePaymentBefore(ServerPlayer p,ContainerArcaneWorkbench menu,ItemStack output,int cost) { observe(r->r.arcaneBefore(p,menu,output,cost)); }
    public static void observeFocalBefore(ServerPlayer p,ContainerFocalManipulator menu,PacketFocusEdit packet) { observe(r->r.focalBefore(p,menu,packet)); }
    public static void observeFocalAfter(ServerPlayer p,ContainerFocalManipulator menu,PacketFocusEdit packet,boolean accepted) { observe(r->r.focalAfter(p,menu,packet,accepted)); }
    public static void observeFocalAuraBefore(TileFocalManipulator tile,float request) { observe(r->r.focalAuraBefore(tile,request)); }
    public static void observeFocalAuraAfter(TileFocalManipulator tile,float request,float consumed) { observe(r->r.focalAuraAfter(tile,request,consumed)); }
    public static void observeCastBefore(ItemStack stack,Player p,float amount,boolean crafting,boolean simulate,int area) { observe(r->r.castBefore(stack,p,amount,crafting,simulate,area)); }
    public static void observeCastAfter(ItemStack stack,Player p,float amount,boolean crafting,boolean simulate,int area,boolean success) { observe(r->r.castAfter(stack,p,amount,crafting,simulate,area,success)); }
    public static void observeFocusSwitchBefore(ServerPlayer p,ItemStack held,PacketCasterAction packet) { observe(r->r.switchBefore(p,held,packet)); }
    public static void observeFocusSwitchAfter(ServerPlayer p,ItemStack held,PacketCasterAction packet) { observe(r->r.switchAfter(p,held,packet)); }
    @SubscribeEvent public static void start(ServerStartedEvent e) {
        if(!Boolean.getBoolean(ENABLE_PROPERTY))return;
        try { active=new Run(e.getServer()); } catch(Exception failure) { Thaumcraft.log.error("Private TCP caster refused startup",failure);e.getServer().halt(false); }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e) { Run r=active;if(r!=null&&!r.finished&&r.server==e.getServer())observe(Run::step); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e) { Run r=active;if(r!=null&&!r.finished&&r.server==e.getServer())r.finish(false,"Stopped before caster audit completed"); }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void load(ChunkEvent.Load e) { observe(r->{if(e.getLevel()==r.server.overworld()&&e.getChunk() instanceof LevelChunk c)r.loaded(c);}); }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void entityLoaded(EntityJoinLevelEvent e) { observe(r->{if(e.loadedFromDisk()&&e.getLevel()==r.server.overworld()&&e.getEntity() instanceof Cow c)r.entityLoaded(c);}); }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void craft(PlayerEvent.ItemCraftedEvent e) { observe(r->{if(e.getEntity() instanceof ServerPlayer p)r.crafted(p,e.getCrafting(),e.getInventory());}); }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void block(PlayerInteractEvent.RightClickBlock e) { observe(r->{if(e.getEntity() instanceof ServerPlayer p)r.interacted(p,e);}); }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void item(PlayerInteractEvent.RightClickItem e) { observe(r->{if(e.getEntity() instanceof ServerPlayer p)r.interacted(p,e);}); }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void opened(PlayerContainerEvent.Open e) { observe(r->{if(e.getEntity() instanceof ServerPlayer p)r.opened(p,e.getContainer());}); }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damageBefore(LivingDamageEvent.Pre e) { observe(r->r.damageBefore(e)); }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damageAfter(LivingDamageEvent.Post e) { observe(r->r.damageAfter(e)); }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void knowledge(ResearchEvent.Knowledge e) { observe(r->{if(e.getPlayer() instanceof ServerPlayer p)r.knowledge(p,e);}); }

    private static final class Run {
        final MinecraftServer server;final Path directory,world;final JsonObject marker,source,sourceBaseline;final String mode;
        final List<Action> actions=generateActions();final Set<BlockPos> loadedPositions=new HashSet<>();
        final long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(30);
        final JsonArray nativeResearchRequests=new JsonArray(),nativeCraftReceipts=new JsonArray(),nativeScanReceipts=new JsonArray(),nativeKnowledgeReceipts=new JsonArray();
        final JsonArray nativeDiscoveryReceipts=new JsonArray(),cruciblePaymentReceipts=new JsonArray(),arcanePaymentReceipts=new JsonArray(),nativeFocalRequests=new JsonArray();
        final JsonArray focalAuraPaymentReceipts=new JsonArray(),castPaymentReceipts=new JsonArray(),nativeCastEffectReceipts=new JsonArray(),nativeFocusSwitchReceipts=new JsonArray();
        final JsonArray nativePouchReceipts=new JsonArray(),nativeStorageReceipts=new JsonArray(),fixtures=new JsonArray(),sourceWorldLoadedTiles=new JsonArray(),sourceLoadedEntities=new JsonArray(),nativeReconnectEntities=new JsonArray();
        final Map<UUID,Cow> targets=new LinkedHashMap<>();
        ServerPlayer owner;Connection connection;ItemEntity scanEntity;Cow target;
        int actionIndex,eventSequence,lastPublished=-100,ownerEpoch=1,interactions,crafts,outputCountBefore,quietSince,storageAwaitTick=-1,pouchAwaitTick=-1,entityAwaitTick=-1,entityStableTick=-1;
        boolean finished,prepared,fixturesPrepared,reconnectAllowed,oldClosed,reconnectProven;
        String phase="login",nativeStorageAcceptedId="";
        JsonObject before,sourceLoadedPlayer,persistenceBaseline,quietCandidate,pendingScan,pendingArcane,pendingFocal,pendingFocalAura,pendingCast,pendingSwitch,pendingDamage,storageBefore,pouchBefore,nativeFinalEntityAvailability;
        JsonArray reconnectEntityBaseline;
        Container storageContainer;ContainerFocusPouch pouchMenu;
        Run(MinecraftServer server) throws Exception {
            this.server=server;require(server.isSameThread(),"Caster acceptance requires owning MAIN");
            directory=directory(Path.of(System.getProperty("user.dir")),"server");marker=validateDirectory(directory,"server");
            source=sourceReport(directory,marker);sourceBaseline=source.getAsJsonObject("persistenceBaseline").deepCopy();mode=string(marker,"auditPhase");
            require(server.isDedicatedServer()&&"127.0.0.1".equals(server.getLocalIp())&&!server.usesAuthentication()&&server.getPort()==marker.get("port").getAsInt()
                &&server.getPlayerCount()==0&&!server.isHardcore(),"Exact initially empty offline dedicated loopback server required");
            world=safePath(directory,server.getWorldPath(LevelResource.ROOT).toRealPath());require(world.getParent().equals(directory)&&world.getFileName().toString().equals(WORLD),"Wrong copied caster world");
            require(sourceBaseline.has("worldTiles")&&!server.overworld().hasChunk(WORKBENCH.getX()>>4,WORKBENCH.getZ()>>4),"Exact source far tiles must await natural chunk loading");
            assertDisk(sourceBaseline);event("owned-caster-audit-started",identity(marker));publish();
        }
        Action action(){return actionIndex<actions.size()?actions.get(actionIndex):null;}
        void owner(ServerPlayer p) {
            require(p!=null&&p.getServer()==server&&server.isSameThread()&&p.isAlive()&&!p.isSpectator()&&!p.isCreative()
                &&p.getUUID().equals(OWNER_UUID)&&p.getGameProfile().getName().equals(USERNAME)&&p.level()==server.overworld(),"Actual native surviving owner/context differs");
            Connection c=p.connection.getConnection();require(c.isConnected()&&!c.isMemoryConnection()&&c.getRemoteAddress() instanceof InetSocketAddress a&&a.getAddress().isLoopbackAddress(),"Actual loopback PLAY TCP connection required");
            require(server.getPlayerCount()==1,"Only exact native owner may join");
        }
        void step() throws Exception {
            require(System.nanoTime()<deadline,"Bounded caster acceptance deadline elapsed");
            JsonObject abort=readDocument(directory.getParent().resolve("operator-abort.json"),16384,true);
            if(abort!=null){require(string(abort,"runId").equals(string(marker,"runId"))&&string(abort,"token").equals(string(marker,"token")),"Operator abort identity differs");finish(false,"Explicit operator abort: "+string(abort,"reason"));return;}
            JsonObject client=readPeer(directory,marker,"client",false);JsonObject terminal=readPeer(directory,marker,"client",true);
            if(terminal!=null&&terminal.has("passed")&&!terminal.get("passed").getAsBoolean()){finish(false,"Native client failed: "+string(terminal,"failure"));return;}
            if(owner==null){ServerPlayer p=server.getPlayerList().getPlayerByName(USERNAME);if(p==null)return;owner(p);assertPlayer(p,sourceBaseline,false);owner=p;connection=p.connection.getConnection();sourceLoadedPlayer=playerSnapshot(p);phase="await-source-chunks";event("actual-source-owner-loaded-by-native-tcp",sourceLoadedPlayer.deepCopy());publish();}
            if(phase.equals("await-source-chunks")){
                if(!loadedPositions.containsAll(tilePositions(sourceBaseline)))return;
                if(client==null||!client.has("sourceOwnerObserved")||!client.get("sourceOwnerObserved").getAsBoolean())return;
                if(mode.equals("generate")){prepareArena();phase="generate";}else{persistenceBaseline=sourceBaseline.deepCopy();phase="loaded";quietSince=server.getTickCount();}
                publish();return;
            }
            if(phase.equals("loaded")){assertPlayer(owner,sourceBaseline,false);if(sourceBaseline.has("worldEntities")&&targets.size()!=sourceBaseline.getAsJsonArray("worldEntities").size())return;if(terminal!=null&&terminal.get("passed").getAsBoolean()&&server.getTickCount()-quietSince>=40&&ready(client)&&finalTargetsReady())finish(true,null);return;}
            if(phase.equals("reconnect")){reconnect(client);return;}
            if(phase.equals("finish")){if(terminal!=null&&terminal.get("passed").getAsBoolean()&&reconnectProven&&finalTargetsReady())finish(true,null);return;}
            owner(owner);Action a=action();require(a!=null,"Finite caster actions exhausted without checkpoint");
            if(!prepared){begin(a);publish();return;}
            finishEffect();finishStorage(client);finishPouch(client);
            if(a.kind()==Kind.CHECKPOINT){if(client!=null&&completed(client)&&ready(client)){phase="reconnect";quietSince=server.getTickCount();publish();}return;}
            if(client!=null&&completed(client)&&postcondition(a)){
                JsonObject row=receipt(a);row.add("before",before.deepCopy());row.add("after",playerSnapshot(owner));row.addProperty("actualClientS2CAcknowledged",true);
                if(a.kind()==Kind.SCAN_ITEM){require(pendingScan!=null,"Native scan completion lacks actual before-source ray observation");pendingScan.add("knowledgeAfter",pools());pendingScan.addProperty("knowledgeAfterNbt",ThaumcraftCapabilities.getKnowledge(owner).serializeNBT().toString());pendingScan.addProperty("actualClientS2CAcknowledged",true);nativeScanReceipts.add(pendingScan);event("native-server-ray-scan-receipt",pendingScan.deepCopy());pendingScan=null;}
                if(a.kind()==Kind.WAIT_MARKER){row.addProperty("researchKey",a.research());row.add("ownerBefore",before.deepCopy());row.add("ownerAfter",playerSnapshot(owner));row.add("position",vector(a.position()));nativeDiscoveryReceipts.add(row.deepCopy());event("native-discovery",row.deepCopy());}
                if(a.id().equals("crucible-native-basic-focus")){
                    TileCrucible c=crucible();row.addProperty("item","thaumcraft:focus_1");row.addProperty("aspectsBeforeNbt",string(before,"crucibleAspectsNbt"));row.addProperty("aspectsAfterNbt",aspects(c.aspects).toString());
                    row.addProperty("waterBefore",before.get("crucibleWater").getAsInt());row.addProperty("waterAfter",c.tank.getFluidAmount());cruciblePaymentReceipts.add(row.deepCopy());event("native-crucible-payment",row.deepCopy());}
                event("native-reviewed-action-completed",row);actionIndex++;prepared=false;publish();return;
            }
            if(server.getTickCount()-lastPublished>=5)publish();
        }
        boolean ready(JsonObject client){return client!=null&&(!client.has("screenshotPending")||client.get("screenshotPending").getAsInt()==0);}
        boolean completed(JsonObject c){return c.has("actionIndex")&&c.get("actionIndex").getAsInt()==actionIndex&&string(c,"actionId").equals(action().id())&&c.has("actionComplete")&&c.get("actionComplete").getAsBoolean();}
        JsonObject receipt(Action a){JsonObject r=new JsonObject();r.addProperty("actionId",a.id());r.addProperty("actionIndex",actionIndex);r.addProperty("actualTCP",true);r.addProperty("mainThread",server.isSameThread());return r;}
        void event(String name,JsonObject details)throws Exception {JsonObject r=identity(marker);r.addProperty("sequence",++eventSequence);r.addProperty("event",name);r.addProperty("serverTick",server.getTickCount());r.addProperty("observedUtc",Instant.now().toString());r.add("details",details);appendEvent(directory,"server",r);}
        void begin(Action a)throws Exception {
            interactions=crafts=0;before=playerSnapshot(owner);before.add("knowledgePools",pools());outputCountBefore=count(a.item(),"");
            Map<String,Integer> required=new LinkedHashMap<>();for(Ingredient i:a.ingredients())required.merge(i.item()+"|"+i.aspect(),i.count(),Integer::sum);
            for(var e:required.entrySet()){String[] k=e.getKey().split("\\|",-1);give(k[0],k[1],e.getValue());}
            if(a.kind()==Kind.USE_BLOCK&&!a.item().equals("thaumcraft:crystal_essence")&&!a.id().equals("place-native-focal-manipulator"))give(a.item(),"",a.amount());
            if(a.kind()==Kind.SCAN_ITEM){scanEntity=new ItemEntity(server.overworld(),SCAN_ITEM.getX()+.5,SCAN_ITEM.getY()+.2,SCAN_ITEM.getZ()+.5,new ItemStack(Items.PISTON));scanEntity.setNoGravity(true);scanEntity.setDeltaMovement(Vec3.ZERO);scanEntity.setNeverPickUp();require(server.overworld().addFreshEntity(scanEntity),"Real piston scan fixture failed");fixture("native-piston-scan-target",scanEntity.getItem());
                CompoundTag actual=new CompoundTag();scanEntity.saveWithoutId(actual);JsonObject row=receipt(a);row.addProperty("uuid",scanEntity.getUUID().toString());row.addProperty("positionNbt",actual.getList("Pos",Tag.TAG_DOUBLE).toString());row.addProperty("motionNbt",actual.getList("Motion",Tag.TAG_DOUBLE).toString());row.addProperty("noGravity",actual.getBoolean("NoGravity"));row.addProperty("pickupDelay",actual.getShort("PickupDelay"));row.addProperty("entityNbt",actual.toString());event("native-stationary-scan-fixture",row);}
            Vec3 location=new Vec3(a.position().getX()-1.5,65,a.position().getZ()+.5);
            if(a.kind()==Kind.WAIT_MARKER)location=Vec3.atBottomCenterOf(a.position());
            else if(a.kind()==Kind.SCAN_ITEM)location=new Vec3(258.5,65,20.5);
            else if(a.kind()==Kind.CAST_TARGET){target=createTarget(a);location=new Vec3(a.position().getX()-2.5,65,a.position().getZ()+.5);}
            else if(a.kind()==Kind.OUTPUT_PICKUP)location=new Vec3(CRUCIBLE.getX()+.5,65,CRUCIBLE.getZ()+.5);
            else if(a.kind()==Kind.FOCAL_DRAFT||a.kind()==Kind.FOCAL_CRAFT||a.kind()==Kind.FOCAL_PICKUP||a.kind()==Kind.POUCH_INSERT||a.kind()==Kind.POUCH_EXTRACT||a.kind()==Kind.FOCUS_SELECT||a.kind()==Kind.FOCUS_REMOVE||a.kind()==Kind.EQUIP_GOGGLES||a.kind()==Kind.CHECKPOINT)location=new Vec3(FOCAL.getX()-1.5,65,FOCAL.getZ()+.5);
            teleport(location);before=playerSnapshot(owner);before.add("knowledgePools",pools());before.addProperty("crucibleAspectsNbt",aspects(crucible().aspects).toString());before.addProperty("crucibleWater",crucible().tank.getFluidAmount());
            if(a.kind()==Kind.USE_BLOCK&&a.item().equals("thaumcraft:crystal_essence")){AspectList actual=ThaumcraftCraftingManager.getObjectTags(stack(a.item(),a.research(),1));require(actual!=null&&actual.size()==1&&actual.getAmount(Aspect.getAspect(a.research()))==1,"Reviewed dissolved crystal must have actual single unit source aspects");}
            prepared=true;JsonObject row=receipt(a);row.add("position",vector(a.position()));row.add("before",before.deepCopy());event("native-reviewed-action-prepared",row);
        }
        void prepareArena()throws Exception {
            require(!fixturesPrepared&&mode.equals("generate")&&loadedPositions.containsAll(tilePositions(sourceBaseline)),"Tangible fixtures require observed inherited tiles");
            for(BlockPos p:newPositions())require(server.overworld().getBlockEntity(p)==null,"New caster fixture must not replace an inherited tile");
            put(FOCAL.below(),Blocks.STONE);put(FOCAL.above(),BlocksTC.arcaneWorkbenchCharger);
            for(BlockPos p:List.of(LOW,HIGH)){for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){put(p.offset(x,-1,z),Blocks.STONE);for(int y=0;y<=2;y++)put(p.offset(x,y,z),Blocks.AIR);}}
            for(BlockPos p:roofPositions())put(p,Blocks.STONE);
            put(FIRE.below(),Blocks.NETHERRACK);put(FIRE,Blocks.FIRE);fixturesPrepared=true;
        }
        void put(BlockPos p,Block b)throws Exception {require(mode.equals("generate"),"Reload cannot write arena blocks");server.overworld().setBlockAndUpdate(p,b.defaultBlockState());JsonObject row=new JsonObject();row.addProperty("name","physical-caster-arena");row.add("position",vector(p));row.addProperty("block",id(b));fixtures.add(row);event("fixture",row.deepCopy());}
        void teleport(Vec3 p)throws Exception {require(mode.equals("generate"),"Reload cannot teleport owner fixtures");owner.teleportTo(p.x,p.y,p.z);JsonObject row=new JsonObject();row.addProperty("name","owner-native-arena-teleport");row.addProperty("x",p.x);row.addProperty("y",p.y);row.addProperty("z",p.z);row.addProperty("actionId",action().id());fixtures.add(row);event("fixture",row.deepCopy());}
        Cow createTarget(Action a)throws Exception {
            Cow cow=EntityType.COW.create(server.overworld());require(cow!=null,"Native physical cow target missing");cow.setUUID(UUID.nameUUIDFromBytes((string(marker,"token")+":"+a.id()).getBytes(StandardCharsets.UTF_8)));
            cow.moveTo(a.position().getX()+.5,65,a.position().getZ()+.5,0,0);cow.setNoAi(true);cow.setPersistenceRequired();require(cow.getHealth()==cow.getMaxHealth()&&cow.getHealth()==10,"Native cow target must retain default ten health");
            require(server.overworld().addFreshEntity(cow),"Native physical target failed to spawn");targets.put(cow.getUUID(),cow);JsonObject row=entity(cow);row.addProperty("name","physical-native-cow-target");row.addProperty("actionId",a.id());fixtures.add(row);event("fixture",row.deepCopy());return cow;
        }
        void give(String id,String aspect,int needed)throws Exception {
            if(needed<=0)return;int deficit=needed-count(id,aspect);if(deficit<=0)return;
            require(!Set.of("thaumcraft:caster_basic","thaumcraft:focus_1","thaumcraft:wand_workbench","thaumcraft:vis_resonator","thaumcraft:thaumometer","thaumcraft:focus_pouch","thaumcraft:girdle_mundane","thaumcraft:goggles").contains(id),"Reviewed paid outputs cannot be material fixtures");
            ItemStack value=stack(id,aspect,deficit);int room=0;for(ItemStack s:owner.getInventory().items){if(s.isEmpty())room+=value.getMaxStackSize();else if(ItemStack.isSameItemSameComponents(s,value))room+=Math.max(0,s.getMaxStackSize()-s.getCount());}
            require(room>=deficit,"Native owner has no bounded capacity for declared tangible materials");fixture("tangible-recipe-material",value.copy());require(owner.getInventory().add(value)&&value.isEmpty(),"Native material inventory add failed");owner.getInventory().setChanged();owner.inventoryMenu.broadcastChanges();
        }
        void fixture(String name,ItemStack s)throws Exception {JsonObject r=receipt(action());r.addProperty("name",name);r.addProperty("item",id(s));r.addProperty("count",s.getCount());r.addProperty("stackNbt",s.save(server.registryAccess()).toString());fixtures.add(r);event("fixture",r.deepCopy());}
        ItemStack stack(String id,String aspect,int count){ItemStack s=new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id)),count);require(!s.isEmpty(),"Registered fixture item missing: "+id);if(!aspect.isBlank())((IEssentiaContainerItem)s.getItem()).setAspects(s,new AspectList().add(Aspect.getAspect(aspect),1));return s;}
        int count(String id,String aspect){int n=0;if(id.isBlank())return 0;for(ItemStack s:owner.getInventory().items)if(id(s).equals(id)&&(aspect.isBlank()||s.get(TCDataComponents.ASPECTS)!=null&&s.get(TCDataComponents.ASPECTS).getAmount(Aspect.getAspect(aspect))==1))n+=s.getCount();return n;}
        int emptySlots(){int n=0;for(ItemStack s:owner.getInventory().items)if(s.isEmpty())n++;return n;}
        TileCrucible crucible(){return (TileCrucible)server.overworld().getBlockEntity(CRUCIBLE);}
        TileFocalManipulator focal(){return (TileFocalManipulator)server.overworld().getBlockEntity(FOCAL);}
        boolean authored(ItemStack s){var p=ItemFocus.getPackage(s);return s!=null&&s.is(ItemsTC.focus1)&&p!=null&&p.getComplexity()==4&&p.nodes.size()==3&&p.nodes.get(0).getKey().equals("ROOT")&&p.nodes.get(1).getKey().equals("thaumcraft.TOUCH")&&p.nodes.get(2).getKey().equals("thaumcraft.FIRE");}
        ItemStack pouch(){for(ItemStack s:owner.getInventory().items)if(s.is(ItemsTC.focusPouch))return s;throw new IllegalStateException("Real crafted pouch absent");}
        boolean pouchHasFocus(){for(ItemStack s:((ItemFocusPouch)pouch().getItem()).getInventory(pouch()))if(authored(s))return true;return false;}
        ItemStack held(){return owner.getMainHandItem();}
        boolean postcondition(Action a)throws Exception {
            if(a.kind()==Kind.FOCAL_DRAFT)return nativeFocalRequests.size()>=1&&!focal().busy()&&focal().data.size()==3&&focal().focusName.equals(FOCUS_NAME);
            if(a.kind()==Kind.FOCAL_CRAFT)return nativeFocalRequests.size()==2&&!focal().busy()&&authored(focal().getItem(0))&&paidFocal()==43F;
            if(owner.containerMenu!=owner.inventoryMenu||!owner.containerMenu.getCarried().isEmpty())return false;
            return switch(a.kind()){
                case STORAGE -> !nativeStorageAcceptedId.isEmpty()&&count("thaumcraft:crystal_essence","")==0&&emptySlots()>=8;
                case RESEARCH -> ThaumcraftCapabilities.getKnowledge(owner).getResearchStage(a.research())==resultingStage(a);
                case WAIT_MARKER -> ThaumcraftCapabilities.getKnowledge(owner).isResearchKnown(a.research());
                case SCAN_ITEM -> ThaumcraftCapabilities.getKnowledge(owner).isResearchKnown("!machina")&&interactions>0;
                case CRAFT -> crafts>0&&count(a.item(),"")>=outputCountBefore+a.amount();
                case USE_BLOCK -> interactions>=a.amount()&&(!a.id().equals("place-native-focal-manipulator")||server.overworld().getBlockEntity(FOCAL) instanceof TileFocalManipulator)
                    &&(!a.id().equals("crucible-native-basic-focus")||crafts==1);
                case OUTPUT_PICKUP -> count(a.item(),"")>=a.amount();
                case FOCAL_PICKUP -> focal().isEmpty()&&owner.getInventory().items.stream().anyMatch(this::authored);
                case POUCH_INSERT -> pouchHasFocus()&&pouchBefore==null;
                case POUCH_EXTRACT -> !pouchHasFocus()&&owner.getInventory().items.stream().anyMatch(this::authored)&&pouchBefore==null;
                case FOCUS_SELECT -> authored(((ItemCaster)held().getItem()).getFocusStack(held()))&&pendingSwitch==null;
                case FOCUS_REMOVE -> ((ItemCaster)held().getItem()).getFocusStack(held())==null&&pouchHasFocus()&&pendingSwitch==null;
                case EQUIP_GOGGLES -> owner.getItemBySlot(EquipmentSlot.HEAD).is(ItemsTC.goggles);
                case CAST_TARGET -> castPaymentReceipts.size()>=(a.research().equals("plain")?1:2)&&nativeCastEffectReceipts.size()>=(a.research().equals("plain")?1:2);
                case FOCAL_DRAFT,FOCAL_CRAFT,CHECKPOINT -> false;
            };
        }
        void interacted(ServerPlayer p,PlayerInteractEvent e)throws Exception {
            owner(p);require(mode.equals("generate")&&phase.equals("generate")&&prepared&&action()!=null,"Native interaction outside finite caster generation");
            Action a=action();require(e instanceof PlayerInteractEvent.RightClickBlock b&&!b.isCanceled()||e instanceof PlayerInteractEvent.RightClickItem i&&!i.isCanceled(),"Native caster interaction canceled");JsonObject row=receipt(a);row.addProperty("heldItem",id(e.getItemStack()));row.addProperty("heldNbt",e.getItemStack().isEmpty()?"{}":e.getItemStack().save(server.registryAccess()).toString());
            if(e instanceof PlayerInteractEvent.RightClickBlock b){
                require(b.getPos().equals(a.position())||a.kind()==Kind.STORAGE&&(b.getPos().equals(STORAGE)||b.getPos().equals(STORAGE_PARTNER)),"Native block ray escaped reviewed position");
                row.add("position",vector(b.getPos()));event("native-right-click-block",row);interactions++;
            }else if(e instanceof PlayerInteractEvent.RightClickItem){
                if(a.kind()==Kind.SCAN_ITEM){require(e.getItemStack().is(ItemsTC.thaumometer)&&ScanTargeting.scanTarget(p)==scanEntity,"Actual native ray did not target declared real piston");
                    row.addProperty("researchKey",LegacyScanIdentity.itemResearchKey(scanEntity.getItem()));row.add("knowledgeBefore",pools());row.addProperty("knowledgeBeforeNbt",ThaumcraftCapabilities.getKnowledge(owner).serializeNBT().toString());
                    require(pendingScan==null,"Native reviewed piston scan must be dispatched once");pendingScan=row.deepCopy();event("native-server-ray-scan-before",row.deepCopy());}
                else require(a.kind()==Kind.CAST_TARGET||a.kind()==Kind.POUCH_INSERT||a.kind()==Kind.POUCH_EXTRACT||a.kind()==Kind.RESEARCH,"Native item use escaped reviewed operation");
                interactions++;event("native-right-click-item",row);
            }
        }
        void research(ServerPlayer p,PacketSyncProgressToServer packet,ServerResearchRequestHandler.Result result)throws Exception {
            owner(p);Action a=action();require(mode.equals("generate")&&prepared&&a!=null&&a.kind()==Kind.RESEARCH&&result.applied()
                &&packet.researchKey().equals(a.research())&&packet.expectedStage()==a.stage(),"Exact contextual applied research required");
            int after=ThaumcraftCapabilities.getKnowledge(owner).getResearchStage(a.research());require(after==resultingStage(a),"Source research stored stage differs");
            JsonObject row=receipt(a);row.addProperty("researchKey",a.research());row.addProperty("expectedStage",a.stage());row.addProperty("storedStageBefore",a.stage());row.addProperty("storedStageAfter",after);row.addProperty("status",result.status().name());
            row.addProperty("actualContextualTCP",true);row.addProperty("catalogDigest",packet.catalogDigest());row.add("knowledgeBefore",before.get("knowledgePools").deepCopy());row.add("knowledgeAfter",pools());
            row.addProperty("knowledgeBeforeNbt",string(before,"knowledgeNbt"));row.addProperty("knowledgeAfterNbt",ThaumcraftCapabilities.getKnowledge(owner).serializeNBT().toString());
            row.addProperty("inventoryBeforeNbt",string(before,"inventoryNbt"));row.addProperty("inventoryAfterNbt",owner.getInventory().save(new ListTag()).toString());row.add("ownerBefore",before.deepCopy());row.add("ownerAfter",playerSnapshot(owner));nativeResearchRequests.add(row);event("actual-applied-contextual-research",row.deepCopy());
        }
        JsonObject pools(){JsonObject row=new JsonObject();for(var c:ResearchCategories.researchCategories.values())for(var t:IPlayerKnowledge.EnumKnowledgeType.values())row.addProperty(t.name()+";"+c.key,ThaumcraftCapabilities.getKnowledge(owner).getKnowledgeRaw(t,c));return row;}
        void knowledge(ServerPlayer p,ResearchEvent.Knowledge e)throws Exception {owner(p);require(mode.equals("generate")&&!e.isCanceled(),"Unexpected reload/canceled knowledge mutation");JsonObject row=receipt(action());row.addProperty("category",e.getCategory()==null?"":e.getCategory().key);row.addProperty("type",e.getType().name());row.addProperty("amount",e.getAmount());
            if(e.getCategory()!=null){int raw=ThaumcraftCapabilities.getKnowledge(owner).getKnowledgeRaw(e.getType(),e.getCategory());row.addProperty("beforeRaw",raw);row.addProperty("afterRaw",Math.max(0,raw+e.getAmount()));}nativeKnowledgeReceipts.add(row);event("native-knowledge-event",row.deepCopy());}
        void crafted(ServerPlayer p,ItemStack output,Container inventory)throws Exception {
            owner(p);Action a=action();require(mode.equals("generate")&&prepared&&a!=null&&(a.kind()==Kind.CRAFT||a.id().equals("crucible-native-basic-focus")),"Native craft receipt outside reviewed recipe");
            String expected=a.kind()==Kind.CRAFT?a.item():"thaumcraft:focus_1";require(id(output).equals(expected)&&output.getCount()==1,"Native paid output differs from reviewed recipe");
            JsonObject row=receipt(a);row.addProperty("item",id(output));row.addProperty("count",output.getCount());row.addProperty("outputNbt",output.save(server.registryAccess()).toString());Integer hash=ResearchItemResolver.craftReference(output);row.addProperty("craftMarker",hash==null?"":"[#]"+hash);
            ListTag inputs=new ListTag();for(int i=0;i<inventory.getContainerSize();i++)if(!inventory.getItem(i).isEmpty()){CompoundTag s=(CompoundTag)inventory.getItem(i).save(server.registryAccess());s.putInt("slot",i);inputs.add(s);}row.addProperty("sourceMatrixNbt",inputs.toString());row.addProperty("knowledgeAfterNbt",ThaumcraftCapabilities.getKnowledge(owner).serializeNBT().toString());nativeCraftReceipts.add(row);event("native-server-craft-receipt",row.deepCopy());crafts++;
            if(pendingArcane!=null){JsonObject paid=pendingArcane;require(string(paid,"item").equals(id(output)),"Native arcane output differs from authorized before-payment snapshot");
                paid.add("auraChunksAfter",aura(WORKBENCH,true));paid.addProperty("visAfter",((TileArcaneWorkbench)server.overworld().getBlockEntity(WORKBENCH)).availableVis());paid.addProperty("matrixAfterNbt",server.overworld().getBlockEntity(WORKBENCH).saveWithFullMetadata(server.registryAccess()).toString());
                paid.addProperty("visPaid",auraDebit(paid.getAsJsonArray("auraChunksBefore"),paid.getAsJsonArray("auraChunksAfter")));paid.addProperty("nativePaidRecipeCommitted",true);require(Math.abs(paid.get("visPaid").getAsFloat()-paid.get("cost").getAsInt())<.001,"Real arcane nine-chunk debit differs");arcanePaymentReceipts.add(paid);event("native-arcane-payment",paid.deepCopy());pendingArcane=null;}
        }
        void arcaneBefore(ServerPlayer p,ContainerArcaneWorkbench menu,ItemStack output,int cost)throws Exception {
            owner(p);Action a=action();require(mode.equals("generate")&&prepared&&a.kind()==Kind.CRAFT&&a.position().equals(WORKBENCH)&&id(output).equals(a.item())&&owner.containerMenu==menu&&menu.table()==server.overworld().getBlockEntity(WORKBENCH)&&pendingArcane==null,"Actual validated contextual arcane before-payment operation differs");
            require(server.overworld().getBlockState(WORKBENCH.above()).is(BlocksTC.arcaneWorkbenchCharger),"Actual source charged workbench hardware required");JsonObject row=receipt(a);row.addProperty("item",id(output));row.addProperty("cost",cost);row.addProperty("menuId",menu.containerId);row.addProperty("outputNbt",output.save(server.registryAccess()).toString());row.addProperty("chargedWorkbench",true);row.addProperty("nativeBeforePaymentObserved",true);
            row.addProperty("matrixBeforeNbt",server.overworld().getBlockEntity(WORKBENCH).saveWithFullMetadata(server.registryAccess()).toString());row.addProperty("visBefore",((TileArcaneWorkbench)menu.table()).availableVis());row.add("auraChunksBefore",aura(WORKBENCH,true));pendingArcane=row;event("native-arcane-before-payment-observed",row.deepCopy());
        }
        JsonArray aura(BlockPos center,boolean charged){JsonArray rows=new JsonArray();int cx=center.getX()>>4,cz=center.getZ()>>4;int radius=charged?1:0;
            for(int x=cx-radius;x<=cx+radius;x++)for(int z=cz-radius;z<=cz+radius;z++){var c=AuraHandler.getAuraChunk(server.overworld(),x,z);require(server.overworld().hasChunk(x,z)&&c!=null&&c.isGenerated()&&c.getLoc().x==x&&c.getLoc().z==z&&Float.isFinite(c.getVis())&&c.getVis()>=0,"Actual naturally loaded generated aura chunk required");JsonObject r=new JsonObject();r.addProperty("chunkX",x);r.addProperty("chunkZ",z);r.addProperty("base",c.getBase());r.addProperty("vis",c.getVis());r.addProperty("flux",c.getFlux());r.addProperty("visBits",Float.floatToIntBits(c.getVis()));r.addProperty("fluxBits",Float.floatToIntBits(c.getFlux()));r.addProperty("actuallyLoaded",true);rows.add(r);}return rows;}
        float auraDebit(JsonArray b,JsonArray a){require(b.size()==a.size(),"Aura observations have different chunk coverage");float n=0;for(int i=0;i<b.size();i++){JsonObject x=b.get(i).getAsJsonObject(),y=a.get(i).getAsJsonObject();require(x.get("chunkX").equals(y.get("chunkX"))&&x.get("chunkZ").equals(y.get("chunkZ"))&&x.get("base").equals(y.get("base"))&&x.get("fluxBits").equals(y.get("fluxBits")),"Payment changed source chunk identity/base/flux");n+=x.get("vis").getAsFloat()-y.get("vis").getAsFloat();}return n;}
        void focalBefore(ServerPlayer p,ContainerFocalManipulator menu,PacketFocusEdit packet)throws Exception {
            owner(p);Action a=action();require(mode.equals("generate")&&prepared&&(a.kind()==Kind.FOCAL_DRAFT||a.kind()==Kind.FOCAL_CRAFT)&&p.containerMenu==menu&&packet.position().equals(FOCAL)&&packet.name().equals(FOCUS_NAME)&&packet.craft()==(a.kind()==Kind.FOCAL_CRAFT)&&pendingFocal==null,"Exact native editor context required");
            List<FocusElementNode> rows=TileFocalManipulator.readNodes(packet.graph());require(FocusGraphRules.validate(rows.stream().map(FocusElementNode::rule).toList(),15,true)==4&&rows.size()==3&&rows.get(0).node.getKey().equals("ROOT")&&rows.get(1).node.getKey().equals("thaumcraft.TOUCH")&&rows.get(2).node.getKey().equals("thaumcraft.FIRE")&&rows.get(2).node.getSettingValue("power")==1&&rows.get(2).node.getSettingValue("duration")==0,"Actual GUI graph differs from reviewed source Touch Fire");
            JsonObject r=receipt(a);r.addProperty("actualContextualTCP",true);r.addProperty("menuId",packet.menuId());r.add("position",vector(packet.position()));r.addProperty("session",packet.session());r.addProperty("expectedRevision",packet.revision());r.addProperty("sequence",packet.sequence());r.addProperty("craft",packet.craft());r.addProperty("name",packet.name());r.addProperty("graphNbt",packet.graph().toString());r.add("ownerBefore",playerSnapshot(p));r.addProperty("focalBeforeNbt",focal().saveWithFullMetadata(server.registryAccess()).toString());r.addProperty("revisionBefore",focal().revision());pendingFocal=r;
        }
        void focalAfter(ServerPlayer p,ContainerFocalManipulator menu,PacketFocusEdit packet,boolean accepted)throws Exception {
            owner(p);require(pendingFocal!=null&&accepted,"Actual source editor rejected reviewed native request");JsonObject r=pendingFocal;r.addProperty("accepted",accepted);r.add("ownerAfter",playerSnapshot(p));r.addProperty("focalAfterNbt",focal().saveWithFullMetadata(server.registryAccess()).toString());r.addProperty("revisionAfter",focal().revision());
            if(packet.craft()){require(focal().initialVis()==43&&focal().vis==43&&p.experienceLevel==r.getAsJsonObject("ownerBefore").get("experienceLevel").getAsInt()-2,"Native source focal vis/XP payment differs");r.addProperty("complexity",4);r.addProperty("initialVis",43F);r.addProperty("xpLevelsPaid",2);r.addProperty("crystalsPaidNbt","{aversio:1,ignis:1}");}
            nativeFocalRequests.add(r);event("native-focal-request",r.deepCopy());pendingFocal=null;
        }
        void focalAuraBefore(TileFocalManipulator tile,float request)throws Exception {
            require(mode.equals("generate")&&tile==focal()&&action().kind()==Kind.FOCAL_CRAFT&&pendingFocalAura==null,"Natural focal aura tick escaped reviewed job");JsonObject r=receipt(action());r.addProperty("request",request);r.addProperty("visBefore",tile.vis);r.add("auraChunksBefore",aura(FOCAL,true));pendingFocalAura=r;
        }
        void focalAuraAfter(TileFocalManipulator tile,float request,float consumed)throws Exception {
            require(tile==focal()&&pendingFocalAura!=null,"Native focal payment lacks actual before observation");JsonObject r=pendingFocalAura;r.addProperty("consumed",consumed);r.addProperty("visAfter",Math.max(0,tile.vis-consumed));r.add("auraChunksAfter",aura(FOCAL,true));require(consumed>=0&&consumed<=request+.0001&&Math.abs(auraDebit(r.getAsJsonArray("auraChunksBefore"),r.getAsJsonArray("auraChunksAfter"))-consumed)<.001,"Actual focal aura float debit differs");focalAuraPaymentReceipts.add(r);event("native-focal-aura-payment",r.deepCopy());pendingFocalAura=null;
        }
        float paidFocal(){float n=0;for(JsonElement r:focalAuraPaymentReceipts)n+=r.getAsJsonObject().get("consumed").getAsFloat();return n;}
        void castBefore(ItemStack stack,Player p,float amount,boolean crafting,boolean simulate,int area)throws Exception {
            require(p instanceof ServerPlayer,"Native caster payment owner must be actual server player");owner((ServerPlayer)p);Action a=action();require(mode.equals("generate")&&prepared&&a.kind()==Kind.CAST_TARGET&&p.getMainHandItem()==stack&&authored(((ItemCaster)stack.getItem()).getFocusStack(stack))&&!crafting&&!simulate&&area==0&&amount==.8F&&pendingCast==null,"Actual native spell cast payment differs");
            require(target!=null&&!target.isInWaterRainOrBubble()&&!server.overworld().canSeeSky(target.blockPosition()),"Actual native cast target must be dry under declared physical roof");
            JsonObject dry=new JsonObject();dry.addProperty("worldRaining",server.overworld().isRaining());dry.addProperty("targetInWaterRainOrBubble",target.isInWaterRainOrBubble());dry.addProperty("canSeeSky",server.overworld().canSeeSky(target.blockPosition()));dry.add("targetPosition",vector(target.blockPosition()));JsonArray roof=new JsonArray();
            for(BlockPos b:roofPositions())if(Math.abs(b.getX()-a.position().getX())<=1&&Math.abs(b.getZ()-a.position().getZ())<=1){require(server.overworld().getBlockState(b).is(Blocks.STONE),"Actual native target roof block differs");JsonObject cell=new JsonObject();cell.add("position",vector(b));cell.addProperty("block",id(server.overworld().getBlockState(b).getBlock()));roof.add(cell);}require(roof.size()==9,"Actual native target roof coverage differs");dry.add("roofBlocks",roof);
            float modifier=((ItemCaster)stack.getItem()).getConsumptionModifier(stack,p,false);require(Math.abs(modifier-(a.research().equals("plain")?1F:.95F))<.00001,"Actual equipped goggles discount differs");JsonObject r=receipt(a);r.add("position",vector(p.blockPosition()));r.addProperty("requestedVis",amount);r.addProperty("modifier",modifier);r.addProperty("effectiveCost",amount*modifier);r.addProperty("crafting",crafting);r.addProperty("simulate",simulate);r.addProperty("area",area);r.add("ownerBefore",playerSnapshot(p));r.addProperty("heldBeforeNbt",stack.save(server.registryAccess()).toString());r.add("targetDryBeforeCast",dry);r.add("auraChunksBefore",aura(p.blockPosition(),false));pendingCast=r;
        }
        void castAfter(ItemStack stack,Player p,float amount,boolean crafting,boolean simulate,int area,boolean success)throws Exception {
            owner((ServerPlayer)p);require(pendingCast!=null&&success,"Actual source caster payment failed");JsonObject r=pendingCast;r.addProperty("success",success);r.add("ownerAfter",playerSnapshot(p));r.addProperty("heldAfterNbt",stack.save(server.registryAccess()).toString());r.add("auraChunksAfter",aura(p.blockPosition(),false));r.addProperty("actualVisPaid",auraDebit(r.getAsJsonArray("auraChunksBefore"),r.getAsJsonArray("auraChunksAfter")));require(Math.abs(r.get("actualVisPaid").getAsFloat()-r.get("effectiveCost").getAsFloat())<.001,"Actual source caster float debit differs");castPaymentReceipts.add(r);event("native-cast-payment",r.deepCopy());pendingCast=null;
        }
        void switchBefore(ServerPlayer p,ItemStack held,PacketCasterAction packet)throws Exception {
            owner(p);require(mode.equals("generate")&&prepared&&packet.action()==0&&(action().kind()==Kind.FOCUS_SELECT||action().kind()==Kind.FOCUS_REMOVE)&&p.containerMenu==p.inventoryMenu&&p.getMainHandItem()==held&&pendingSwitch==null,"Exact native focus selection context required");
            require(packet.focus().equals("REMOVE")== (action().kind()==Kind.FOCUS_REMOVE),"Actual selection/removal payload differs");JsonObject r=receipt(action());r.addProperty("requestedKey",packet.focus());r.add("ownerBefore",playerSnapshot(p));r.addProperty("heldBeforeNbt",held.save(server.registryAccess()).toString());r.addProperty("pouchBeforeNbt",pouch().save(server.registryAccess()).toString());pendingSwitch=r;
        }
        void switchAfter(ServerPlayer p,ItemStack held,PacketCasterAction packet)throws Exception {owner(p);require(pendingSwitch!=null,"Native switch lacks before snapshot");JsonObject r=pendingSwitch;r.add("ownerAfter",playerSnapshot(p));r.addProperty("heldAfterNbt",held.save(server.registryAccess()).toString());r.addProperty("pouchAfterNbt",pouch().save(server.registryAccess()).toString());nativeFocusSwitchReceipts.add(r);event("native-focus-switch",r.deepCopy());pendingSwitch=null;}
        void damageBefore(LivingDamageEvent.Pre e)throws Exception {
            if(e.getEntity()==owner&&action()!=null&&action().research().equals("f_onfire")){JsonObject row=receipt(action());row.addProperty("sourceType",e.getSource().typeHolder().unwrapKey().orElseThrow().location().toString());row.addProperty("amount",e.getNewDamage());row.addProperty("healthBefore",owner.getHealth());event("native-owner-fire-damage-accepted",row);return;}
            if(target==null||e.getEntity()!=target||e.getSource().getEntity()!=owner)return;
            require(mode.equals("generate")&&prepared&&action().kind()==Kind.CAST_TARGET&&e.getNewDamage()==4F&&pendingDamage==null,"Actual native Fire damage differs");JsonObject row=receipt(action());row.addProperty("targetUuid",target.getUUID().toString());row.addProperty("ownerUuid",OWNER_UUID.toString());row.addProperty("sourceType",e.getSource().typeHolder().unwrapKey().orElseThrow().location().toString());row.addProperty("healthBefore",target.getHealth());row.addProperty("actualFinalDamage",e.getNewDamage());pendingDamage=row;
        }
        void damageAfter(LivingDamageEvent.Post e)throws Exception {
            if(target==null||e.getEntity()!=target||e.getSource().getEntity()!=owner)return;require(pendingDamage!=null&&e.getNewDamage()==4F,"Native Fire post damage lacks source before");pendingDamage.addProperty("healthAfter",target.getHealth());require(pendingDamage.get("healthBefore").getAsFloat()-target.getHealth()==4F,"Native accepted damage did not remove actual four health");
        }
        void finishEffect()throws Exception {if(pendingDamage==null||!pendingDamage.has("healthAfter"))return;require(target!=null&&target.getRemainingFireTicks()>0,"Actual Fire effect did not ignite its native target");pendingDamage.addProperty("remainingFireTicks",target.getRemainingFireTicks());pendingDamage.addProperty("actualIgnitionObserved",true);nativeCastEffectReceipts.add(pendingDamage);event("native-cast-effect",pendingDamage.deepCopy());pendingDamage=null;}
        void opened(ServerPlayer p,AbstractContainerMenu menu)throws Exception {
            owner(p);if(!mode.equals("generate"))throw new IllegalStateException("Reload unexpectedly opened a native gameplay menu");Action a=action();require(prepared&&a!=null,"Native menu opened before reviewed preparation");
            if(menu instanceof ChestMenu chest){
                require(a.kind()==Kind.STORAGE&&chest.getRowCount()==6&&chest.getContainer().getContainerSize()==54&&chest.getCarried().isEmpty()&&storageBefore==null,"Actual native storage menu differs");
                Container first=(Container)server.overworld().getBlockEntity(STORAGE),second=(Container)server.overworld().getBlockEntity(STORAGE_PARTNER);
                require(chest.getContainer() instanceof CompoundContainer compound&&compound.contains(first)&&compound.contains(second),"Native storage menu does not own reviewed inherited chests");
                storageContainer=chest.getContainer();storageBefore=receipt(a);storageBefore.addProperty("transactionId","caster-storage-1");storageBefore.addProperty("menuId",menu.containerId);storageBefore.add("position",vector(STORAGE));
                storageBefore.addProperty("inventoryBeforeNbt",owner.getInventory().save(new ListTag()).toString());storageBefore.addProperty("chestBeforeNbt",containerNbt(storageContainer).toString());storageBefore.addProperty("ordinaryEmptyBefore",emptySlots());storageBefore.addProperty("cursorEmpty",true);storageBefore.addProperty("nativeChestMenu54",true);storageBefore.addProperty("theoryCategory","");storageBefore.addProperty("purpose","casters");event("native-material-storage-opened",storageBefore.deepCopy());
            }else if(menu instanceof ContainerFocusPouch pouch){
                require((a.kind()==Kind.POUCH_INSERT||a.kind()==Kind.POUCH_EXTRACT)&&pouchBefore==null&&pouch.getCarried().isEmpty()&&pouch.input.getContainerSize()==18,"Native real pouch menu differs");
                pouchMenu=pouch;pouchBefore=receipt(a);pouchBefore.addProperty("menuId",menu.containerId);pouchBefore.addProperty("pouchSlot",pouch.pouchSlot);pouchBefore.addProperty("extract",a.kind()==Kind.POUCH_EXTRACT);pouchBefore.addProperty("inventoryBeforeNbt",owner.getInventory().save(new ListTag()).toString());pouchBefore.addProperty("pouchBeforeNbt",pouch().save(server.registryAccess()).toString());pouchBefore.addProperty("contentsBeforeNbt",containerNbt(pouch.input).toString());event("native-pouch-opened",pouchBefore.deepCopy());
            }
        }
        void finishStorage(JsonObject client)throws Exception {
            if(client==null||!client.has("nativeStorage"))return;JsonObject req=client.getAsJsonObject("nativeStorage");
            require(action().kind()==Kind.STORAGE&&req.get("actionIndex").getAsInt()==actionIndex&&string(req,"transactionId").equals("caster-storage-1"),"Native storage request identity differs");
            if(!string(req,"phase").equals("await-server")||string(req,"transactionId").equals(nativeStorageAcceptedId))return;
            require(storageBefore!=null&&storageContainer!=null,"Native storage result lacks actual source menu open");
            if(owner.containerMenu!=owner.inventoryMenu){if(storageAwaitTick<0)storageAwaitTick=server.getTickCount();require(server.getTickCount()-storageAwaitTick<100&&owner.containerMenu instanceof ChestMenu,"Native chest close did not reach server within bound");return;}
            require(owner.containerMenu.getCarried().isEmpty()&&req.get("actualNativeMenuId").getAsInt()==storageBefore.get("menuId").getAsInt()&&req.get("actualNativeChestMenu").getAsBoolean(),"Native storage cursor/menu receipt differs");
            ListTag ib=list(string(storageBefore,"inventoryBeforeNbt")),ia=owner.getInventory().save(new ListTag());CompoundTag cb=TagParser.parseTag(string(storageBefore,"chestBeforeNbt")),ca=containerNbt(storageContainer);
            require(ib.equals(list(string(req,"inventoryBeforeNbt")))&&ia.equals(list(string(req,"inventoryAfterNbt")))&&cb.equals(TagParser.parseTag(string(req,"chestBeforeNbt")))&&ca.equals(TagParser.parseTag(string(req,"chestAfterNbt"))),"Native client/server typed storage snapshots differ");
            require(totals(ib,cb.getList("Items",10)).equals(totals(ia,ca.getList("Items",10)))&&nonCrystals(ib).equals(nonCrystals(ia)),"Native storage failed full component/count conservation");
            JsonArray clicks=req.getAsJsonArray("nativeClicks");require(clicks!=null&&!clicks.isEmpty()&&count("thaumcraft:crystal_essence","")==0&&emptySlots()>=8,"Native storage must move every inherited crystal");
            for(JsonObject c:objects(clicks))require(c.get("menuId").getAsInt()==storageBefore.get("menuId").getAsInt()&&c.get("slot").getAsInt()>=54&&c.get("slot").getAsInt()<90&&c.get("button").getAsInt()==0&&string(c,"clickType").equals("QUICK_MOVE")&&c.get("nativeC2S").getAsBoolean()&&TagParser.parseTag(string(c,"stackBeforeNbt")).getString("id").equals("thaumcraft:crystal_essence"),"Native storage click escaped finite crystal-only transfer");
            JsonObject row=storageBefore;row.addProperty("inventoryAfterNbt",ia.toString());row.addProperty("chestAfterNbt",ca.toString());row.addProperty("ordinaryEmptyAfter",emptySlots());row.addProperty("nativeQuickMoveCount",clicks.size());row.add("nativeClicks",clicks.deepCopy());row.addProperty("actualServerAccepted",true);nativeStorageReceipts.add(row);event("native-material-storage",row.deepCopy());nativeStorageAcceptedId=string(row,"transactionId");storageBefore=null;storageContainer=null;publish();
        }
        void finishPouch(JsonObject client)throws Exception {
            if(pouchBefore==null||client==null||!completed(client))return;
            if(owner.containerMenu!=owner.inventoryMenu){if(pouchAwaitTick<0)pouchAwaitTick=server.getTickCount();require(server.getTickCount()-pouchAwaitTick<100&&owner.containerMenu==pouchMenu,"Native pouch close did not reach server within bound");return;}
            require(owner.containerMenu.getCarried().isEmpty(),"Native pouch left carried items");ListTag ib=list(string(pouchBefore,"inventoryBeforeNbt")),ia=owner.getInventory().save(new ListTag());
            require(pouchTotals(ib).equals(pouchTotals(ia)),"Native pouch menu did not conserve full typed focus and other components");JsonObject row=pouchBefore;row.addProperty("inventoryAfterNbt",ia.toString());row.addProperty("pouchAfterNbt",pouch().save(server.registryAccess()).toString());row.addProperty("contentsAfterNbt",containerNbt(pouchMenu.input).toString());row.addProperty("actualNativeMenuClosed",true);
            require(client.has("casterReceipt"),"Native pouch lacks received real C2S/S2C transaction receipt");JsonObject c=client.getAsJsonObject("casterReceipt");require(c.get("menuId").getAsInt()==row.get("menuId").getAsInt()&&list(string(c,"inventoryBeforeNbt")).equals(ib)&&list(string(c,"inventoryAfterNbt")).equals(ia)
                &&TagParser.parseTag(string(c,"pouchBeforeNbt")).equals(TagParser.parseTag(string(row,"pouchBeforeNbt")))&&TagParser.parseTag(string(c,"pouchAfterNbt")).equals(TagParser.parseTag(string(row,"pouchAfterNbt")))&&c.get("actualNativePouchS2C").getAsBoolean(),"Native typed pouch receipt differs from actual server menu");row.add("clientReceipt",c.deepCopy());nativePouchReceipts.add(row);event("native-pouch-transfer",row.deepCopy());pouchBefore=null;pouchMenu=null;pouchAwaitTick=-1;
        }
        Map<CompoundTag,Integer> pouchTotals(ListTag inventory)throws Exception {
            ListTag flat=new ListTag();for(Tag t:inventory){CompoundTag n=((CompoundTag)t).copy();if(n.getString("id").equals("thaumcraft:focus_pouch")){ItemStack stack=ItemStack.parse(server.registryAccess(),n).orElseThrow();for(ItemStack s:((ItemFocusPouch)stack.getItem()).getInventory(stack))if(!s.isEmpty())flat.add(s.save(server.registryAccess()));n.getCompound("components").remove("minecraft:container");if(n.getCompound("components").isEmpty())n.remove("components");}flat.add(n);}return totals(flat,new ListTag());
        }
        CompoundTag containerNbt(Container c){CompoundTag t=new CompoundTag();t.putInt("Size",c.getContainerSize());ListTag items=new ListTag();for(int i=0;i<c.getContainerSize();i++)if(!c.getItem(i).isEmpty()){CompoundTag n=(CompoundTag)c.getItem(i).save(server.registryAccess());n.putByte("Slot",(byte)i);items.add(n);}t.put("Items",items);return t;}
        void loaded(LevelChunk chunk)throws Exception {
            for(JsonObject original:objects(sourceBaseline.getAsJsonArray("worldTiles"))){BlockPos p=position(original.getAsJsonArray("position"));if(!new ChunkPos(p).equals(chunk.getPos())||loadedPositions.contains(p))continue;
                require(id(chunk.getBlockState(p).getBlock()).equals(string(original,"block")),"Naturally loaded copied source block differs: "+p);JsonObject row=original.deepCopy();
                if(original.has("nbt")){BlockEntity tile=chunk.getBlockEntity(p);require(tile!=null,"Copied native source BE missing");CompoundTag actual=tile.saveWithFullMetadata(server.registryAccess()),expected=TagParser.parseTag(string(original,"nbt"));if(expected.contains("keepPacked"))actual.putBoolean("keepPacked",false);
                    require(actual.equals(expected),"Copied source full typed tile differs before first chunk tick: "+p);row.addProperty("actualLoadedNbt",actual.toString());}
                loadedPositions.add(p);row.addProperty("beforeFirstChunkTick",true);sourceWorldLoadedTiles.add(row);event("natural-source-chunk-loaded",row.deepCopy());
            }
        }
        void entityLoaded(Cow cow)throws Exception {
            JsonArray expectedRows=mode.equals("reload")?sourceBaseline.getAsJsonArray("worldEntities"):reconnectAllowed?reconnectEntityBaseline:null;
            if(expectedRows==null)return;
            for(JsonObject expected:objects(expectedRows))if(cow.getUUID().toString().equals(string(expected,"uuid"))){
                Cow previous=targets.get(cow.getUUID());require((mode.equals("reload")?previous==null:previous!=null&&previous!=cow)&&string(expected,"type").equals(BuiltInRegistries.ENTITY_TYPE.getKey(cow.getType()).toString()),"Loaded owned target entity identity differs");
                JsonObject actual=entity(cow);CompoundTag old=TagParser.parseTag(string(expected,"nbt")),fresh=TagParser.parseTag(string(actual,"nbt"));
                for(String key:List.of("UUID","Health","NoAI","Pos","PersistenceRequired"))require(old.get(key)!=null&&old.get(key).equals(fresh.get(key)),"Actual reloaded target stable typed field differs: "+key);
                targets.put(cow.getUUID(),cow);if(target==previous)target=cow;actual.addProperty("loadedFromDisk",true);actual.addProperty("beforeFirstEntityTick",true);
                if(mode.equals("reload")){sourceLoadedEntities.add(actual);event("natural-source-entity-loaded",actual.deepCopy());}
                else{require(phase.equals("reconnect")||phase.equals("finish"),"Native reconnect target reloaded outside owned reconnect");actual.addProperty("naturalReconnect",true);actual.addProperty("replacedOriginalObject",true);actual.addProperty("previousObjectRemoved",previous.isRemoved());nativeReconnectEntities.add(actual);event("native-reconnect-target-entity-loaded",actual.deepCopy());}return;
            }
        }
        boolean currentTargetsAvailable(){if(targets.size()!=2)return false;for(Cow cow:targets.values())if(server.overworld().getEntity(cow.getUUID())!=cow||!cow.isAlive()||!server.overworld().areEntitiesLoaded(ChunkPos.asLong(cow.blockPosition()))||!server.overworld().isPositionEntityTicking(cow.blockPosition()))return false;return true;}
        JsonArray actualTargetEntities(){require(currentTargetsAvailable(),"Actual owned targets must be naturally loaded, visible, ticking and alive");JsonArray rows=new JsonArray();for(Cow cow:targets.values())rows.add(entity(cow));return rows;}
        boolean finalTargetsReady()throws Exception {
            owner(owner);int now=server.getTickCount();if(entityAwaitTick<0)entityAwaitTick=now;require(now-entityAwaitTick<600,"Natural final target entity loading did not settle within bounded deadline");
            if(!currentTargetsAvailable()){entityStableTick=-1;return false;}if(entityStableTick<0)entityStableTick=now;if(now-entityStableTick<40)return false;
            if(nativeFinalEntityAvailability==null){JsonObject row=new JsonObject();row.addProperty("actualTCP",true);row.addProperty("mainThread",server.isSameThread());row.addProperty("ownerEpoch",ownerEpoch);row.addProperty("stableTicks",now-entityStableTick);JsonArray entities=actualTargetEntities();for(JsonObject e:objects(entities)){e.addProperty("actualLookupLoaded",true);e.addProperty("actualEntityChunkLoaded",true);e.addProperty("actualEntityTicking",true);e.addProperty("alive",true);}row.add("entities",entities);nativeFinalEntityAvailability=row;event("native-final-target-entities-observed",row.deepCopy());}return true;
        }
        void reconnect(JsonObject client)throws Exception {
            ServerPlayer p=server.getPlayerList().getPlayerByName(USERNAME);
            if(!reconnectAllowed){owner(owner);if(client==null||!client.has("reconnectReady")||!client.get("reconnectReady").getAsBoolean())return;require(owner.containerMenu==owner.inventoryMenu&&owner.containerMenu.getCarried().isEmpty(),"Reconnect needs native closed menus");JsonObject c=playerSnapshot(owner);
                if(quietCandidate==null||!samePlayer(c,quietCandidate)){quietCandidate=c;quietSince=server.getTickCount();}if(server.getTickCount()-quietSince>=40&&currentTargetsAvailable()){persistenceBaseline=c;persistenceBaseline.add("worldTiles",liveTiles());reconnectEntityBaseline=actualTargetEntities();persistenceBaseline.add("worldEntities",reconnectEntityBaseline.deepCopy());reconnectAllowed=true;event("native-reconnect-baseline-sealed",c.deepCopy());publish();}}
            else if(p==null&&!connection.isConnected()){require(server.getPlayerCount()==0,"Old native owner remained online");oldClosed=true;publish();}
            else if(p!=null&&p!=owner){require(oldClosed&&!connection.isConnected()&&p.connection.getConnection()!=connection,"Native reconnect did not replace closed TCP connection");owner(p);assertPlayer(p,persistenceBaseline,false);owner=p;connection=p.connection.getConnection();ownerEpoch=2;reconnectProven=true;phase="finish";event("native-second-owner-tcp-reconnect",playerSnapshot(p));publish();}
        }
        JsonArray liveTiles(){JsonArray rows=new JsonArray();for(BlockPos p:ownedPositions()){JsonObject r=new JsonObject();r.add("position",vector(p));r.addProperty("block",id(server.overworld().getBlockState(p).getBlock()));BlockEntity tile=server.overworld().getBlockEntity(p);if(tile!=null)r.addProperty("nbt",tile.saveWithFullMetadata(server.registryAccess()).toString());rows.add(r);}return rows;}
        Set<BlockPos> ownedPositions(){Set<BlockPos> p=new LinkedHashSet<>(tilePositions(sourceBaseline));p.addAll(newPositions());return p;}
        JsonArray savedTiles(){JsonArray rows=new JsonArray();for(BlockPos p:ownedPositions()){JsonObject r=new JsonObject();r.add("position",vector(p));r.addProperty("block",id(server.overworld().getBlockState(p).getBlock()));if(server.overworld().getBlockEntity(p)!=null)r.addProperty("nbt",diskTile(p).toString());rows.add(r);}return rows;}
        CompoundTag diskTile(BlockPos p){ChunkPos c=new ChunkPos(p);CompoundTag n=server.overworld().getChunkSource().chunkMap.read(c).orTimeout(10,TimeUnit.SECONDS).join().orElseThrow();require(n.getString("Status").equals("minecraft:full")&&n.getInt("xPos")==c.x&&n.getInt("zPos")==c.z,"Native saved chunk identity differs");for(Tag t:n.getList("block_entities",10)){CompoundTag b=(CompoundTag)t;if(b.getInt("x")==p.getX()&&b.getInt("y")==p.getY()&&b.getInt("z")==p.getZ())return b.copy();}throw new IllegalStateException("Native saved tile missing: "+p);}
        void assertDisk(JsonObject expected)throws Exception {CompoundTag n=NbtIo.readCompressed(safePath(directory,world.resolve("playerdata").resolve(OWNER_UUID+".dat")),NbtAccounter.create(4L*1024*1024));require(n.hasUUID("UUID")&&n.getUUID("UUID").equals(OWNER_UUID),"Typed saved owner UUID differs");CompoundTag a=n.getCompound("neoforge:attachments");
            require(TcpBootstrapReloadAudit.canonicalKnowledge(a.getCompound("thaumcraft:knowledge"),false).equals(TcpBootstrapReloadAudit.canonicalKnowledge(TagParser.parseTag(string(expected,"knowledgeNbt")),false))&&a.getCompound("thaumcraft:warp").equals(TagParser.parseTag(string(expected,"warpNbt")))&&n.getList("Inventory",10).equals(list(string(expected,"inventoryNbt"))),"Full typed saved knowledge/warp/inventory differs");
            require(n.getInt("SelectedItemSlot")==expected.get("selectedSlot").getAsInt()&&n.getInt("XpTotal")==expected.get("totalExperience").getAsInt()&&n.getInt("XpLevel")==expected.get("experienceLevel").getAsInt()&&Float.floatToIntBits(n.getFloat("XpP"))==Float.floatToIntBits(expected.get("experienceProgress").getAsFloat()),"Native saved XP/selected slot differs");}
        void finish(boolean passed,String failure){if(finished)return;finished=true;JsonObject result=state();try{
            if(passed){require(owner!=null&&owner.containerMenu==owner.inventoryMenu&&owner.containerMenu.getCarried().isEmpty()&&nativeFinalEntityAvailability!=null,"Actual terminal owner, natural loaded target proof and empty menu required");persistenceBaseline=playerSnapshot(owner);server.getPlayerList().saveAll();require(server.saveEverything(true,true,true),"Native caster world did not flush");assertDisk(persistenceBaseline);persistenceBaseline.add("worldTiles",savedTiles());persistenceBaseline.add("worldEntities",actualTargetEntities());result.addProperty("saveFlushed",true);result.addProperty("diskNbtVerified",true);}
            result.addProperty("passed",passed);result.addProperty("status",passed?"completed":"failed");result.addProperty("castersComplete",passed);result.addProperty("reconnectProven",reconnectProven);result.addProperty("newJvmReloadProven",passed&&mode.equals("reload"));result.addProperty("loadedWithoutGrants",mode.equals("reload"));result.addProperty("noKnowledgeResearchFixtures",true);result.addProperty("noAspectOrRecipeEffectFixtures",true);result.addProperty("noFocusPackageFixtures",true);result.addProperty("noXpAuraDamageSetters",true);result.addProperty("rngNotReplaced",true);result.addProperty("fullSurvivalAcquisition",false);result.addProperty("dedicatedServer",true);
            if(sourceLoadedPlayer!=null)result.add("sourceLoadedPlayer",sourceLoadedPlayer);result.add("sourceWorldLoadedTiles",sourceWorldLoadedTiles);result.add("sourceLoadedEntities",sourceLoadedEntities);result.add("nativeReconnectEntities",nativeReconnectEntities);result.add("reconnectEntityBaseline",reconnectEntityBaseline==null?new JsonArray():reconnectEntityBaseline);if(nativeFinalEntityAvailability!=null)result.add("nativeFinalEntityAvailability",nativeFinalEntityAvailability);if(persistenceBaseline!=null){result.add("persistenceBaseline",persistenceBaseline);JsonObject b=new JsonObject();b.add("tiles",persistenceBaseline.get("worldTiles"));result.add("worldBaseline",b);}
            result.add("fixtures",fixtures);result.add("nativeResearchRequests",nativeResearchRequests);result.add("nativeCraftReceipts",nativeCraftReceipts);result.add("nativeScanReceipts",nativeScanReceipts);result.add("nativeKnowledgeReceipts",nativeKnowledgeReceipts);result.add("nativeDiscoveryReceipts",nativeDiscoveryReceipts);result.add("cruciblePaymentReceipts",cruciblePaymentReceipts);result.add("arcanePaymentReceipts",arcanePaymentReceipts);result.add("nativeFocalRequests",nativeFocalRequests);result.add("focalAuraPaymentReceipts",focalAuraPaymentReceipts);result.add("castPaymentReceipts",castPaymentReceipts);result.add("nativeCastEffectReceipts",nativeCastEffectReceipts);result.add("nativeFocusSwitchReceipts",nativeFocusSwitchReceipts);result.add("nativePouchReceipts",nativePouchReceipts);result.add("nativeStorageReceipts",nativeStorageReceipts);
            if(failure!=null)result.addProperty("failure",failure);writeTerminal(directory,"server",result);publish();
        }catch(Exception terminal){Thaumcraft.log.error("Native caster terminal failed",terminal);try{result.addProperty("passed",false);result.addProperty("status","failed");result.addProperty("failure",terminal.toString());if(!Files.exists(directory.resolve("server-report.json")))writeTerminal(directory,"server",result);}catch(Exception ignored){Thaumcraft.log.error("Unable to record failed caster terminal",ignored);}}finally{server.halt(false);}}
        JsonObject state(){JsonObject row=identity(marker);row.addProperty("status",finished?"completed":"running");row.addProperty("phase",phase);row.addProperty("serverTick",server.getTickCount());row.addProperty("actionIndex",actionIndex);Action a=action();row.addProperty("actionId",a==null?"none":a.id());row.addProperty("actionKind",a==null?"none":a.kind().name());row.addProperty("actionPrepared",prepared);row.addProperty("ownerEpoch",ownerEpoch);row.addProperty("reconnectAllowed",reconnectAllowed);row.addProperty("reconnectProven",reconnectProven);row.addProperty("actualOldConnectionClosed",oldClosed);row.addProperty("nativeStorageAcceptedId",nativeStorageAcceptedId);row.addProperty("actualTCP",true);
            if(persistenceBaseline!=null)row.add("persistenceBaseline",persistenceBaseline.deepCopy());if(target!=null&&a!=null&&a.kind()==Kind.CAST_TARGET){row.addProperty("targetUuid",target.getUUID().toString());row.addProperty("targetEntityId",target.getId());}return row;}
        void publish()throws Exception {writeState(directory,"server",state());lastPublished=server.getTickCount();}
    }
    private static String id(ItemStack s){return s.isEmpty()?"minecraft:air":BuiltInRegistries.ITEM.getKey(s.getItem()).toString();}
    private static String id(Block b){return BuiltInRegistries.BLOCK.getKey(b).toString();}
    private static CompoundTag aspects(AspectList a){CompoundTag t=new CompoundTag();a.writeToNBT(t);return t;}
    private static JsonObject entity(Cow c){JsonObject r=new JsonObject();r.addProperty("uuid",c.getUUID().toString());r.addProperty("type",BuiltInRegistries.ENTITY_TYPE.getKey(c.getType()).toString());CompoundTag tag=new CompoundTag();c.saveWithoutId(tag);r.addProperty("nbt",tag.toString());r.addProperty("health",c.getHealth());return r;}
    private static List<JsonObject> objects(JsonArray a){return java.util.stream.StreamSupport.stream(a.spliterator(),false).map(JsonElement::getAsJsonObject).toList();}
    private static Set<BlockPos> tilePositions(JsonObject b){Set<BlockPos> p=new LinkedHashSet<>();for(JsonObject r:objects(b.getAsJsonArray("worldTiles")))p.add(position(r.getAsJsonArray("position")));return p;}
    private static ListTag list(String s)throws Exception{return TagParser.parseTag("{Items:"+s+"}").getList("Items",10);}
    private static Map<CompoundTag,Integer> totals(ListTag a,ListTag b){Map<CompoundTag,Integer> out=new HashMap<>();for(ListTag values:List.of(a,b))for(Tag value:values){CompoundTag t=((CompoundTag)value).copy();int n=t.getInt("count");require(n>0,"Nonpositive native stack count");t.remove("Slot");t.remove("count");out.merge(t,n,Integer::sum);}return out;}
    private static ListTag nonCrystals(ListTag a){ListTag out=new ListTag();for(Tag v:a)if(!((CompoundTag)v).getString("id").equals("thaumcraft:crystal_essence"))out.add(v.copy());return out;}
    private static boolean samePlayer(JsonObject a,JsonObject b)throws Exception {JsonObject l=a.deepCopy(),r=b.deepCopy();String lk=string(l,"knowledgeNbt"),rk=string(r,"knowledgeNbt");l.remove("knowledgeNbt");r.remove("knowledgeNbt");l.remove("worldTiles");r.remove("worldTiles");l.remove("worldEntities");r.remove("worldEntities");return l.equals(r)&&TcpBootstrapReloadAudit.canonicalKnowledge(TagParser.parseTag(lk),false).equals(TcpBootstrapReloadAudit.canonicalKnowledge(TagParser.parseTag(rk),false));}
}
