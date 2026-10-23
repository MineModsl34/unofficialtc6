package thaumcraft.common.lib.validation;

import static thaumcraft.common.lib.validation.TcpAlchemyAuditIO.*;
import static thaumcraft.common.lib.validation.TcpAlchemyScenario.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.items.IScribeTools;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchEvent;
import thaumcraft.common.container.ContainerArcaneWorkbench;
import thaumcraft.common.entities.EntitySpecialItem;
import thaumcraft.common.lib.crafting.CrucibleRecipes;
import thaumcraft.common.lib.crafting.InfusionRecipes;
import thaumcraft.common.lib.crafting.InfusionMatrixRules;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;
import thaumcraft.common.lib.network.playerdata.PacketSyncProgressToServer;
import thaumcraft.common.lib.network.playerdata.ServerResearchRequestHandler;
import thaumcraft.common.lib.research.LegacyScanIdentity;
import thaumcraft.common.lib.utils.ScanTargeting;
import thaumcraft.common.tiles.crafting.TileCrucible;
import thaumcraft.common.tiles.crafting.TileArcaneWorkbench;
import thaumcraft.common.tiles.crafting.TileInfusionMatrix;
import thaumcraft.common.tiles.crafting.TilePedestal;
import thaumcraft.common.tiles.crafting.TileResearchTable;
import thaumcraft.common.tiles.essentia.TileAlembic;
import thaumcraft.common.tiles.essentia.TileJarFillable;
import thaumcraft.common.tiles.essentia.TileSmelter;
import thaumcraft.common.tiles.essentia.TileTube;
import thaumcraft.common.world.aura.AuraHandler;

/** Private dedicated TCP acceptance. Only tangible materials, empty hardware and arena/teleport
 * fixtures are written during generation. Client actions enter the ordinary PLAY handlers.
 * Research, aspects, recipe effects, cards and RNG always belong to production code. Resume
 * and final reload write no fixtures and observe naturally loaded chunks before their first tick. */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class TcpAlchemyAudit {
    private static Run active;
    private TcpAlchemyAudit() {}
    public static void observeRequest(ServerPlayer player, PacketSyncProgressToServer packet, ServerResearchRequestHandler.Result result) {
        Run run = active; if (!Boolean.getBoolean(ENABLE_PROPERTY) || run == null || run.finished || player.getServer() != run.server) return;
        try { run.request(player, packet, result); } catch (Exception failure) { run.finish(false, failure.toString()); }
    }
    public static void observeArcanePaymentBefore(ServerPlayer player, ContainerArcaneWorkbench menu, ItemStack output, int cost) {
        Run run = active; if (!Boolean.getBoolean(ENABLE_PROPERTY) || run == null || run.finished || player.getServer() != run.server) return;
        try { run.arcanePaymentBefore(player, menu, output, cost); } catch (Exception failure) { run.finish(false, failure.toString()); }
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        try { active = new Run(event.getServer()); }
        catch (Exception failure) { Thaumcraft.log.error("Private TCP alchemy refused startup", failure); event.getServer().halt(false); }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        Run run = active; if (run == null || run.finished || run.server != event.getServer()) return;
        try { run.step(); } catch (Exception failure) { run.finish(false, failure.toString()); }
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        Run run = active; if (run != null && !run.finished && run.server == event.getServer()) run.finish(false, "Stopped before private alchemy completed");
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void loaded(ChunkEvent.Load event) {
        Run run = active; if (run == null || run.finished || event.getLevel() != run.server.overworld() || !(event.getChunk() instanceof LevelChunk chunk)) return;
        try { run.loaded(chunk); } catch (Exception failure) { run.finish(false, failure.toString()); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        Run run = active; if (run == null || run.finished || !(event.getEntity() instanceof ServerPlayer owner) || owner.getServer() != run.server) return;
        try { run.crafted(owner, event.getCrafting(), event.getInventory()); } catch (Exception failure) { run.finish(false, failure.toString()); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void knowledge(ResearchEvent.Knowledge event) {
        Run run = active; if (run == null || run.finished || !(event.getPlayer() instanceof ServerPlayer owner) || owner.getServer() != run.server) return;
        try { run.knowledge(owner, event); } catch (Exception failure) { run.finish(false, failure.toString()); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void interactionBlock(PlayerInteractEvent.RightClickBlock event) {
        interaction(event);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void interactionItem(PlayerInteractEvent.RightClickItem event) {
        interaction(event);
    }
    private static void interaction(PlayerInteractEvent event) {
        Run run = active; if (run == null || run.finished || !(event.getEntity() instanceof ServerPlayer owner) || owner.getServer() != run.server) return;
        if (!(event instanceof PlayerInteractEvent.RightClickBlock) && !(event instanceof PlayerInteractEvent.RightClickItem)) return;
        try { run.interaction(owner, event); } catch (Exception failure) { run.finish(false, failure.toString()); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void storageOpened(PlayerContainerEvent.Open event) {
        Run run = active; if (run == null || run.finished || !(event.getContainer() instanceof ChestMenu menu)
            || !(event.getEntity() instanceof ServerPlayer owner) || owner.getServer() != run.server) return;
        try { run.storageOpened(owner, menu); } catch (Exception failure) { run.finish(false, failure.toString()); }
    }

    private static final class Run {
        final MinecraftServer server; final Path directory, world; final JsonObject marker, source, sourceBaseline;
        final String mode; final List<Action> actions = generateActions();
        final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(30);
        final JsonArray nativeResearchRequests = new JsonArray(), nativeCraftReceipts = new JsonArray(), nativeScanReceipts = new JsonArray();
        final JsonArray nativeTheoryReceipts = new JsonArray(), nativeKnowledgeReceipts = new JsonArray(), nativePhialReceipts = new JsonArray(), cruciblePaymentReceipts = new JsonArray(), arcanePaymentReceipts = new JsonArray();
        final JsonArray essentiaConservationRecords = new JsonArray(), fixtures = new JsonArray(), checks = new JsonArray(), sourceWorldLoadedTiles = new JsonArray();
        final JsonArray nativeStorageReceipts = new JsonArray(); final Set<String> deferredSupplies = new HashSet<>();
        final Set<BlockPos> loadedPositions = new HashSet<>();
        final Map<String, Integer> inputConsumed = new HashMap<>(), produced = new HashMap<>();
        ServerPlayer owner; net.minecraft.network.Connection connection;
        boolean finished, actionPrepared, actualOldConnectionClosed, reconnectAllowed, reconnectProven, loadedJobObserved;
        int actionIndex, actionCraftCount, interactionCount, ownerEpoch = 1, eventSequence, quietSince, lastPublished = -100;
        String phase = "login"; ItemEntity scanEntity;
        JsonObject actionBefore, persistenceBaseline, quietCandidate, infusionCheckpoint, resumedInfusion, sourceLoadedPlayer, operatorAbortRequest;
        JsonObject storageBefore, nativeInfusionStartObservation, pendingArcanePayment; Container storageContainer; String nativeStorageAcceptedId = "";
        CompoundTag initialInfusionCosts; int paidAfterReload;

        Run(MinecraftServer server) throws Exception {
            this.server = server; require(server.isSameThread(), "Native alchemy requires owning server MAIN");
            directory = directory(Path.of(System.getProperty("user.dir")), "server"); marker = validateDirectory(directory, "server");
            source = sourceReport(directory, marker); sourceBaseline = source.getAsJsonObject("persistenceBaseline").deepCopy(); mode = string(marker, "auditPhase");
            require(server.isDedicatedServer() && "127.0.0.1".equals(server.getLocalIp()) && !server.usesAuthentication()
                && server.getPort() == marker.get("port").getAsInt() && !server.isHardcore() && server.getPlayerCount() == 0,
                "Exact initially empty dedicated offline loopback server required");
            world = safePath(directory, server.getWorldPath(LevelResource.ROOT).toRealPath());
            require(world.getParent().equals(directory) && world.getFileName().toString().equals(WORLD), "Wrong private copied alchemy world");
            assertDiskPlayer(sourceBaseline);
            if (!mode.equals("generate")) {
                require(sourceBaseline.has("worldTiles"), "Missing exact source disk tile ledger");
                require(!server.overworld().hasChunk(new ChunkPos(MATRIX).x, new ChunkPos(MATRIX).z), "Far arena was loaded before audit registered");
                if (mode.equals("resume")) {
                    infusionCheckpoint = source.getAsJsonObject("infusionCheckpoint").deepCopy();
                    initialInfusionCosts = TagParser.parseTag(string(infusionCheckpoint, "initialCosts"));
                    resumedInfusion = new JsonObject(); resumedInfusion.addProperty("loadedJobNbt", string(infusionCheckpoint, "jobNbt"));
                }
            }
            event("source-disk-owner-verified-before-login", new JsonObject()); publish();
        }
        void owner(ServerPlayer value) {
            require(value != null && value.getServer() == server && value.level() == server.overworld() && value.isAlive() && !value.isRemoved()
                && OWNER_UUID.equals(value.getUUID()) && USERNAME.equals(value.getGameProfile().getName())
                && value.gameMode.getGameModeForPlayer() == GameType.SURVIVAL && !value.getAbilities().instabuild,
                "Actual native survival owner differs");
            var tcp = value.connection.getConnection(); require(tcp.isConnected() && !tcp.isMemoryConnection()
                && tcp.getRemoteAddress() instanceof InetSocketAddress address && address.getAddress().isLoopbackAddress(), "Real loopback TCP PLAY connection required");
        }
        void step() throws Exception {
            require(server.isSameThread(), "Native alchemy requires its owning server MAIN");
            if (server.getTickCount() % 10 == 0 && operatorAbort()) return;
            require(server.isSameThread() && System.nanoTime() < deadline, "Private native alchemy exceeded thirty-minute deadline");
            for (ServerPlayer value : server.getPlayerList().getPlayers()) require(OWNER_UUID.equals(value.getUUID()) && USERNAME.equals(value.getGameProfile().getName()), "Unexpected private audit player");
            JsonObject terminal = readPeer(directory, marker, "client", true);
            if (terminal != null) {
                require(terminal.get("passed").getAsBoolean(), "Native alchemy client failed: " + string(terminal, "failure"));
                if (mode.equals("generate") && phase.equals("checkpoint")) { finish(true, null); return; }
                if (mode.equals("resume") && phase.equals("finish") && reconnectProven) { finish(true, null); return; }
                if (mode.equals("reload") && phase.equals("loaded")) { finish(true, null); return; }
            }
            JsonObject client = readPeer(directory, marker, "client", false);
            if (owner == null) {
                ServerPlayer value = server.getPlayerList().getPlayerByName(USERNAME); if (value == null) return;
                owner(value); assertPlayer(value, sourceBaseline, false); owner = value; connection = value.connection.getConnection(); sourceLoadedPlayer = playerSnapshot(owner);
                event("actual-source-owner-loaded-by-native-tcp", playerSnapshot(owner));
                if (mode.equals("generate")) { prepareArena(); phase = "generate"; }
                else phase = "await-source-chunks";
                publish();
            }
            if (phase.equals("await-source-chunks")) {
                if (loadedPositions.containsAll(tilePositions(sourceBaseline))) {
                    if (mode.equals("resume")) { require(loadedJobObserved, "Paid infusion job did not load before its first native tick"); phase = "resume-job"; }
                    else { persistenceBaseline = playerSnapshot(owner); persistenceBaseline.add("worldTiles", sourceBaseline.get("worldTiles").deepCopy()); phase = "loaded"; }
                    publish();
                } else { if (server.getTickCount() - lastPublished >= 10) publish(); return; }
            }
            if (phase.equals("reconnect")) { reconnect(client); return; }
            owner(owner);
            if (mode.equals("generate") && phase.equals("generate")) generate(client);
            else if (mode.equals("resume")) resume(client);
            if (server.getTickCount() - lastPublished >= 10) publish();
        }
        void prepareArena() throws Exception {
            require(mode.equals("generate"), "Arena fixtures are generation-only");
            for (int x = 256; x <= 279; x++) for (int z = -4; z <= 39; z++) {
                block(new BlockPos(x, 64, z), Blocks.STONE);
                for (int y = 65; y <= 70; y++) block(new BlockPos(x, y, z), Blocks.AIR);
            }
            block(CRAFT, Blocks.CRAFTING_TABLE); block(WORKBENCH, BlocksTC.arcaneWorkbench); block(CRUCIBLE.below(), Blocks.MAGMA_BLOCK);
            block(WORKBENCH.above(), BlocksTC.arcaneWorkbenchCharger);
            JsonObject charger = new JsonObject(); charger.addProperty("name", "ordinary-workbench-charger"); charger.add("position", vector(WORKBENCH.above()));
            charger.addProperty("block", id(BlocksTC.arcaneWorkbenchCharger)); charger.addProperty("chargedAuraChunks", 9); charger.addProperty("auraValuesWritten", false);
            charger.addProperty("nativeCraftingOfChargerProven", false); charger.addProperty("ordinaryResourceGatheringProven", false); fixtures.add(charger); event("fixture", charger.deepCopy());
            block(CRUCIBLE, Blocks.CAULDRON); block(THEORY, BlocksTC.tableWood);
            block(STORAGE, Blocks.CHEST); block(STORAGE_PARTNER, Blocks.CHEST);
            // RIGHT is the native combined container's first 27 slots. Face south so
            // the first fixture at x260 joins its left partner at x261.
            server.overworld().setBlock(STORAGE, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH).setValue(ChestBlock.TYPE, ChestType.RIGHT), 3);
            server.overworld().setBlock(STORAGE_PARTNER, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH).setValue(ChestBlock.TYPE, ChestType.LEFT), 3);
            require(((ChestBlockEntity) server.overworld().getBlockEntity(STORAGE)).isEmpty()
                && ((ChestBlockEntity) server.overworld().getBlockEntity(STORAGE_PARTNER)).isEmpty(), "Storage fixtures must start physically empty");
            List<Block> crystals = List.of(BlocksTC.crystalAir, BlocksTC.crystalFire, BlocksTC.crystalWater, BlocksTC.crystalEarth, BlocksTC.crystalOrder, BlocksTC.crystalEntropy);
            for (int i = 0; i < crystals.size(); i++) block(CRYSTAL_SCANS.get(i), crystals.get(i));
            for (BlockPos pos : List.of(SMELTER, SMELTER_CRYSTAL, SMELTER_TRAP)) {
                if (!pos.equals(SMELTER)) block(pos, BlocksTC.smelterBasic);
                block(pos.above(), BlocksTC.alembic); block(pos.above().east(), BlocksTC.tube); block(pos.above().east(2), BlocksTC.tube); block(pos.east(2), BlocksTC.jarNormal);
                require(((TileAlembic) server.overworld().getBlockEntity(pos.above())).amount == 0
                    && ((TileJarFillable) server.overworld().getBlockEntity(pos.east(2))).amount == 0, "Supplied actual hardware must be empty");
            }
            block(CENTER, BlocksTC.pedestalArcane); block(LEFT, BlocksTC.pedestalArcane); block(RIGHT, BlocksTC.pedestalArcane);
            for (int x : List.of(-1, 1)) for (int z : List.of(-1, 1)) block(MATRIX.offset(x, -2, z), BlocksTC.pillarArcane);
            block(MATRIX.below(), Blocks.STONE); // Explicit temporary native BlockItem placement support, removed after placement.
            JsonObject row = new JsonObject(); row.addProperty("name", "empty-material-arena"); row.addProperty("xOffset", 256);
            row.addProperty("hardwareEssentiaInitiallyZero", true); row.addProperty("storageInitiallyEmpty", true);
            row.addProperty("ordinaryResourceGatheringProven", false); fixtures.add(row);
            check("source-owner-loaded-without-research-or-knowledge-grants");
        }
        void block(BlockPos pos, Block block) { require(mode.equals("generate"), "World fixture writes forbidden after generation"); server.overworld().setBlock(pos, block.defaultBlockState(), 3); }
        Action action() { return actionIndex < actions.size() ? actions.get(actionIndex) : null; }
        void generate(JsonObject client) throws Exception {
            Action action = action(); require(action != null, "Missing finite alchemy action");
            if (!actionPrepared) begin(action);
            observeStorage(client);
            observePipeline();
            if (action.kind() == Kind.THEORY) supplyDisplayedTheoryMaterials(client);
            if (action.kind() == Kind.OUTPUT_PICKUP && count(action.item(), "") < action.amount()) {
                var outputs = server.overworld().getEntitiesOfClass(EntitySpecialItem.class, new AABB(CRUCIBLE).inflate(4), entity -> id(entity.getItem()).equals(action.item()));
                if (!outputs.isEmpty()) teleport(outputs.getFirst().position());
            }
            if (action.kind() == Kind.CHECKPOINT) {
                var matrix = matrix(); int remaining = matrix.getAspects().visSize();
                require(matrix.active && matrix.crafting && initialInfusionCosts != null, "Actual paid native infusion must still be running");
                if (remaining < aspectTotal(initialInfusionCosts) && remaining > 0 && owner.containerMenu == owner.inventoryMenu) {
                    phase = "checkpoint"; persistenceBaseline = playerSnapshot(owner); captureCheckpoint(false); publish();
                }
                return;
            }
            if (client == null || !client.has("actionIndex") || client.get("actionIndex").getAsInt() != actionIndex
                || !action.id().equals(string(client, "actionId")) || !client.has("actionComplete") || !client.get("actionComplete").getAsBoolean()) return;
            if (!postcondition(action)) return;
            if (action.kind() == Kind.SCAN_ITEM) { require(scanEntity != null, "Missing real scanned fixture"); scanEntity.discard(); scanEntity = null; }
            if (action.id().equals("place-native-matrix")) block(MATRIX.below(), Blocks.AIR);
            JsonObject row = new JsonObject(); row.addProperty("actionIndex", actionIndex); row.addProperty("actionId", action.id());
            row.addProperty("actualClientS2CAcknowledged", true); row.add("after", playerSnapshot(owner)); event("native-reviewed-action-completed", row);
            actionIndex++; actionPrepared = false; publish();
        }
        void begin(Action action) throws Exception {
            require(mode.equals("generate"), "Generation action outside fixture cohort");
            actionCraftCount = interactionCount = 0;
            Map<String, Integer> required = new HashMap<>();
            for (Ingredient ingredient : action.ingredients()) required.merge(ingredient.item() + "|" + ingredient.aspect(), ingredient.count(), Integer::sum);
            if (!action.item().isBlank() && action.kind() != Kind.SCAN_BLOCK && action.kind() != Kind.SCAN_ITEM && action.kind() != Kind.OUTPUT_PICKUP
                && action.kind() != Kind.CRAFT && action.kind() != Kind.PHIAL_EXTRACT)
                required.merge(action.item() + "|" + (action.kind() == Kind.PEDESTAL_PLACE ? action.research() : ""), action.amount(), Math::max);
            for (var entry : required.entrySet()) { String[] key = entry.getKey().split("\\|", -1); give(key[0], key[1], entry.getValue()); }
            if (action.kind() == Kind.SCAN_ITEM) {
                scanEntity = new ItemEntity(server.overworld(), SCAN_ITEM.getX() + .5, SCAN_ITEM.getY() + .2, SCAN_ITEM.getZ() + .5, stack(action.item(), "", 1));
                scanEntity.setNoGravity(true); scanEntity.setDeltaMovement(Vec3.ZERO); scanEntity.setNeverPickUp(); require(server.overworld().addFreshEntity(scanEntity), "Real scan ItemEntity fixture could not spawn");
                fixture("rendered-native-item-scan-target", scanEntity.getItem());
            }
            BlockPos pos = action.position();
            if (action.kind() == Kind.SCAN_ITEM) teleport(new Vec3(258.5, 65, 20.5));
            else if (action.kind() == Kind.SCAN_BLOCK) teleport(new Vec3(pos.getX() - 3.5, 65, pos.getZ() + .5));
            else if (action.kind() == Kind.PEDESTAL_PLACE) teleport(new Vec3(pos.getX() + .5, 65, pos.getZ() + 2.5));
            else if (action.kind() == Kind.MATRIX_ACTIVATE || action.kind() == Kind.MATRIX_START || action.kind() == Kind.CHECKPOINT) teleport(new Vec3(MATRIX.getX() + .5, 65, 3.5));
            else teleport(new Vec3(pos.getX() - 1.5, 65, pos.getZ() + .5));
            actionBefore = playerSnapshot(owner); actionBefore.add("knowledgePools", pools());
            actionPrepared = true; JsonObject row = new JsonObject(); row.addProperty("index", actionIndex); row.addProperty("id", action.id()); row.addProperty("kind", action.kind().name()); event("reviewed-action-began", row); publish();
        }
        void teleport(Vec3 position) { require(mode.equals("generate"), "Teleport fixtures forbidden after generation"); owner.teleportTo(position.x, position.y, position.z); }
        ItemStack stack(String item, String aspect, int count) {
            ItemStack value = !aspect.isBlank() ? ThaumcraftApiHelper.makeCrystal(Aspect.getAspect(aspect))
                : new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(item)));
            require(!value.isEmpty() && id(value).equals(item), "Unregistered reviewed material: " + item + " " + aspect); value.setCount(count); return value;
        }
        void give(String item, String aspect, int needed) throws Exception {
            require(mode.equals("generate"), "Material fixtures forbidden after generation");
            int deficit = needed - count(item, aspect); if (deficit <= 0) return;
            require(!item.equals("thaumcraft:nitor_yellow") && !item.equals("thaumcraft:alumentum") && !item.equals("thaumcraft:infusion_matrix")
                && !item.equals("thaumcraft:smelter_basic") && !item.equals("thaumcraft:ingot_brass") && !item.equals("thaumcraft:phial_filled"), "Native outputs cannot be replaced by material fixtures: " + item);
            ItemStack value = stack(item, aspect, deficit), receipt = value.copy();
            require(canFit(value), "Real owner inventory cannot hold bounded supplied materials");
            require(owner.getInventory().add(value) && value.isEmpty(), "Real owner inventory cannot hold bounded supplied materials");
            owner.getInventory().setChanged(); owner.containerMenu.broadcastChanges(); fixture("supplied-tangible-material", receipt);
        }
        boolean canFit(ItemStack wanted) {
            int room = 0; for (ItemStack existing : owner.getInventory().items) {
                if (existing.isEmpty()) room += wanted.getMaxStackSize();
                else if (ItemStack.isSameItemSameComponents(existing, wanted)) room += Math.max(0, wanted.getMaxStackSize() - existing.getCount());
            } return room >= wanted.getCount();
        }
        void giveOptional(String item, String aspect, int needed) throws Exception {
            int deficit = needed - count(item, aspect); if (deficit <= 0) return;
            ItemStack wanted = stack(item, aspect, deficit);
            if (!canFit(wanted)) {
                if (deferredSupplies.add(item + "|" + aspect)) {
                    JsonObject row = new JsonObject(); row.addProperty("item", item); row.addProperty("aspect", aspect); row.addProperty("count", deficit);
                    row.addProperty("noInventoryMutation", true); event("optional-theory-material-deferred-for-native-storage", row);
                } return;
            }
            deferredSupplies.remove(item + "|" + aspect); give(item, aspect, needed);
        }
        void fixture(String name, ItemStack stack) throws Exception {
            JsonObject row = new JsonObject(); row.addProperty("name", name); row.addProperty("actionIndex", actionIndex); row.addProperty("item", id(stack));
            row.addProperty("count", stack.getCount()); row.addProperty("stackNbt", stack.save(server.registryAccess()).toString()); fixtures.add(row); event("fixture", row.deepCopy());
        }
        boolean postcondition(Action action) throws Exception {
            if (owner.containerMenu != owner.inventoryMenu || !owner.containerMenu.getCarried().isEmpty()) return false;
            return switch (action.kind()) {
                case SCAN_ITEM -> known(LegacyScanIdentity.itemResearchKey(scanEntity.getItem()));
                case SCAN_BLOCK -> known(LegacyScanIdentity.itemResearchKey(new ItemStack(server.overworld().getBlockState(action.position()).getBlock())));
                case RESEARCH -> ThaumcraftCapabilities.getKnowledge(owner).getResearchStage(action.research()) == resultingStage(action);
                case THEORY -> raw(EnumKnowledgeType.THEORY, action.research()) >= action.amount();
                case CRAFT -> actionCraftCount >= 1 && count(action.item(), "") >= action.amount();
                case OUTPUT_PICKUP -> count(action.item(), "") >= action.amount();
                case USE_BLOCK -> interactionCount >= action.amount() && (!action.id().equals("dust-native-crucible")
                    || server.overworld().getBlockEntity(CRUCIBLE) instanceof TileCrucible && actionCraftCount == 1);
                case SMELTER_LOAD -> interactionCount >= 1 && ((TileSmelter) server.overworld().getBlockEntity(action.position())).furnaceBurnTime > 0;
                case WAIT_TRANSFER -> pipelineIdle(action.research()) && jar(action.position()).amount >= action.amount() && jar(action.position()).aspect == Aspect.getAspect(action.research());
                case PHIAL_EXTRACT -> interactionCount >= 1 && hasFilledPhial(action.research());
                case PHIAL_INSERT -> interactionCount >= 1;
                case PEDESTAL_PLACE -> interactionCount >= 1 && id(((TilePedestal) server.overworld().getBlockEntity(action.position())).getItem(0)).equals(action.item());
                case MATRIX_ACTIVATE -> matrix().active && !matrix().crafting && interactionCount >= 1 && matrix().stability >= matrix().stabilityCap;
                case MATRIX_START -> matrix().active && matrix().crafting && interactionCount >= 1 && initialInfusionCosts != null;
                case CHECKPOINT -> false;
            };
        }
        boolean known(String key) { return ThaumcraftCapabilities.getKnowledge(owner).isResearchKnown(key); }
        int raw(EnumKnowledgeType type, String category) { return ThaumcraftCapabilities.getKnowledge(owner).getKnowledgeRaw(type, ResearchCategories.getResearchCategory(category)); }
        JsonObject pools() {
            JsonObject result = new JsonObject(); for (String category : List.of("BASICS", "ALCHEMY", "AUROMANCY", "INFUSION")) {
                result.addProperty("OBSERVATION;" + category, raw(EnumKnowledgeType.OBSERVATION, category)); result.addProperty("THEORY;" + category, raw(EnumKnowledgeType.THEORY, category));
            } return result;
        }
        int count(String item, String aspect) {
            int total = 0; for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
                ItemStack stack = owner.getInventory().getItem(i); if (id(stack).equals(item) && (aspect.isBlank()
                    || stack.getItem() instanceof thaumcraft.api.aspects.IEssentiaContainerItem container && container.getAspects(stack) != null
                        && container.getAspects(stack).getAmount(Aspect.getAspect(aspect)) > 0)) total += stack.getCount();
            } return total;
        }
        boolean hasFilledPhial(String aspect) {
            for (ItemStack stack : owner.getInventory().items) if (id(stack).equals("thaumcraft:phial_filled")
                && stack.getItem() instanceof thaumcraft.api.aspects.IEssentiaContainerItem container && container.getAspects(stack) != null
                && container.getAspects(stack).getAmount(Aspect.getAspect(aspect)) == 10) return true; return false;
        }
        void interaction(ServerPlayer player, PlayerInteractEvent event) throws Exception {
            owner(player);
            if (!mode.equals("generate")) {
                require(mode.equals("resume") && phase.equals("output") && event instanceof PlayerInteractEvent.RightClickBlock block
                    && block.getPos().equals(CENTER) && event.getItemStack().isEmpty(), "Resume permits only native empty-hand access to its real output pedestal");
                JsonObject row = new JsonObject(); row.add("position", vector(CENTER)); row.addProperty("actualTCP", true); event("native-resumed-output-pedestal-access", row); return;
            }
            Action action = action(); require(action != null && actionPrepared, "Native interaction outside reviewed generation action");
            if (event instanceof PlayerInteractEvent.RightClickItem && event.getItemStack().is(ItemsTC.thaumometer)) {
                require(action.kind() == Kind.SCAN_ITEM || action.kind() == Kind.SCAN_BLOCK, "Thaumometer use outside reviewed target action");
                Object actual = ScanTargeting.scanTarget(player); require(action.kind() == Kind.SCAN_ITEM ? actual == scanEntity : action.position().equals(actual), "Actual server ray target differs from reviewed real target");
                JsonObject row = new JsonObject(); row.addProperty("researchKey", action.kind() == Kind.SCAN_ITEM ? LegacyScanIdentity.itemResearchKey(scanEntity.getItem())
                    : LegacyScanIdentity.itemResearchKey(new ItemStack(server.overworld().getBlockState(action.position()).getBlock())));
                row.addProperty("actionId", action.id()); row.addProperty("actualTCP", true); row.addProperty("mainThread", server.isSameThread());
                row.add("knowledgeBefore", pools()); row.addProperty("knowledgeBeforeNbt", ThaumcraftCapabilities.getKnowledge(owner).serializeNBT().toString());
                nativeScanReceipts.add(row); event("native-server-ray-scan-receipt", row.deepCopy()); interactionCount++; return;
            }
            if (event instanceof PlayerInteractEvent.RightClickBlock block) {
                require(!block.isCanceled(), "Native right-click block was cancelled");
                if (action.kind() == Kind.THEORY) {
                    require(block.getPos().equals(THEORY) || block.getPos().equals(STORAGE) || block.getPos().equals(STORAGE_PARTNER), "Theory interaction escaped its reviewed table/storage");
                    if (!block.getPos().equals(THEORY)) require(((TileResearchTable) server.overworld().getBlockEntity(THEORY)).data == null
                        && owner.containerMenu == owner.inventoryMenu && owner.containerMenu.getCarried().isEmpty(), "Native storage requires an ordinary closed menu and real note boundary");
                }
                interactionCount++; JsonObject row = new JsonObject(); row.addProperty("actionId", action.id()); row.add("position", vector(block.getPos()));
                row.addProperty("heldItem", id(event.getItemStack())); row.addProperty("actualTCP", true); event("native-right-click-block", row);
                if (action.kind() == Kind.MATRIX_START) observeInfusionStartBeforePayment(block.getPos(), event.getItemStack());
                if (action.kind() == Kind.PHIAL_EXTRACT || action.kind() == Kind.PHIAL_INSERT) {
                    require(block.getPos().equals(JAR), "Reviewed phial must use the real paid air jar");
                    JsonObject phial = row.deepCopy(); phial.addProperty("aspect", "aer"); phial.addProperty("direction", action.kind() == Kind.PHIAL_INSERT ? "insert" : "extract");
                    phial.addProperty("beforeAmount", jar(JAR).amount); phial.addProperty("expectedAfterAmount", jar(JAR).amount + (action.kind() == Kind.PHIAL_INSERT ? 10 : -10));
                    phial.addProperty("inventoryBeforeNbt", owner.getInventory().save(new ListTag()).toString()); phial.addProperty("nativeItemUseFirst", true);
                    nativePhialReceipts.add(phial);
                }
            }
        }
        void request(ServerPlayer player, PacketSyncProgressToServer packet, ServerResearchRequestHandler.Result result) throws Exception {
            owner(player); Action action = action(); require(mode.equals("generate") && actionPrepared && action != null && action.kind() == Kind.RESEARCH,
                "Contextual research outside finite reviewed research action");
            require(packet != null && result != null && result.applied() && packet.researchKey().equals(action.research()) && packet.expectedStage() == action.stage(),
                "Exact actual applied contextual research request required");
            int after = ThaumcraftCapabilities.getKnowledge(owner).getResearchStage(packet.researchKey()); require(after == resultingStage(action), "Native research stored stage jump differs");
            JsonObject row = new JsonObject(); row.addProperty("researchKey", packet.researchKey()); row.addProperty("expectedStage", packet.expectedStage());
            row.addProperty("storedStageBefore", action.stage()); row.addProperty("storedStageAfter", after); row.addProperty("status", result.status().name());
            row.addProperty("actualContextualTCP", true); row.addProperty("mainThread", true); row.addProperty("catalogDigest", packet.catalogDigest());
            row.add("knowledgeBefore", actionBefore.get("knowledgePools").deepCopy()); row.add("knowledgeAfter", pools());
            row.addProperty("knowledgeBeforeNbt", string(actionBefore, "knowledgeNbt")); row.addProperty("knowledgeAfterNbt", ThaumcraftCapabilities.getKnowledge(owner).serializeNBT().toString());
            row.addProperty("inventoryBeforeNbt", string(actionBefore, "inventoryNbt")); row.addProperty("inventoryAfterNbt", owner.getInventory().save(new ListTag()).toString());
            nativeResearchRequests.add(row); event("actual-applied-contextual-research", row.deepCopy());
        }
        void crafted(ServerPlayer player, ItemStack output, Container inventory) throws Exception {
            owner(player); String item = id(output); JsonObject row = new JsonObject(); row.addProperty("item", item); row.addProperty("count", output.getCount());
            row.addProperty("actualTCP", true); row.addProperty("mainThread", true); row.addProperty("actionId", mode.equals("generate") && action() != null ? action().id() : phase);
            Integer hash = thaumcraft.common.lib.research.ResearchItemResolver.craftReference(output);
            row.addProperty("craftMarker", hash == null ? "" : "[#]" + hash);
            nativeCraftReceipts.add(row); event("native-production-item-crafted", row.deepCopy()); actionCraftCount++;
            if (owner.containerMenu instanceof ContainerArcaneWorkbench menu) {
                int cost = item.equals("thaumcraft:smelter_basic") ? 50 : item.equals("thaumcraft:infusion_matrix") ? 150 : -1;
                require(cost >= 0 && pendingArcanePayment != null && string(pendingArcanePayment, "item").equals(item)
                    && string(pendingArcanePayment, "actionId").equals(action().id()) && pendingArcanePayment.get("menuId").getAsInt() == menu.containerId,
                    "Native arcane result lacks its actual contextual before-payment observation");
                int before = pendingArcanePayment.get("visBefore").getAsInt(), after = ((TileArcaneWorkbench)menu.table()).availableVis();
                require(before - after == cost, "Native arcane crafting did not debit exact source vis cost");
                JsonObject payment = row.deepCopy(); payment.addProperty("visBefore", before); payment.addProperty("visAfter", after); payment.addProperty("visPaid", cost);
                JsonArray auraBefore = pendingArcanePayment.getAsJsonArray("auraChunksBefore"), auraAfter = chargedAuraSnapshot(); double floatPaid = 0;
                for (int index = 0; index < 9; index++) {
                    JsonObject previous = auraBefore.get(index).getAsJsonObject(), actual = auraAfter.get(index).getAsJsonObject();
                    require(previous.get("chunkX").getAsInt() == actual.get("chunkX").getAsInt() && previous.get("chunkZ").getAsInt() == actual.get("chunkZ").getAsInt()
                        && previous.get("base").getAsInt() == actual.get("base").getAsInt() && previous.get("fluxBits").getAsInt() == actual.get("fluxBits").getAsInt(),
                        "Actual charged arcane payment changed its source identity/base/flux");
                    double debit = previous.get("vis").getAsDouble() - actual.get("vis").getAsDouble(); require(debit >= 0, "Actual charged payment added source aura"); floatPaid += debit;
                }
                require(Math.abs(floatPaid - cost) <= .0001, "Actual nine charged aura floats do not pay the full native arcane source cost");
                payment.addProperty("chargedWorkbench", true); payment.addProperty("nativeBeforePaymentObserved", true); payment.addProperty("visFloatPaid", floatPaid);
                payment.add("beforePayment", pendingArcanePayment.deepCopy()); payment.add("auraChunksBefore", auraBefore.deepCopy()); payment.add("auraChunksAfter", auraAfter);
                JsonArray crystals = new JsonArray(); for (int index = 9; index < 15; index++) {
                    int paid = inventory.getItem(index).getCount() - menu.table().getItem(index).getCount(); require(paid >= 0, "Native crystal payment went backwards"); crystals.add(paid);
                } payment.add("crystalsPaid", crystals); arcanePaymentReceipts.add(payment); event("native-arcane-payment", payment.deepCopy()); pendingArcanePayment = null;
            } else if (item.equals("thaumcraft:nitor_yellow") || item.equals("thaumcraft:alumentum") || item.equals("thaumcraft:ingot_brass")) {
                TileCrucible crucible = (TileCrucible) server.overworld().getBlockEntity(CRUCIBLE);
                var recipe = CrucibleRecipes.findMatching(owner, crucible.aspects, inventory.getItem(0));
                require(recipe != null && id(recipe.getRecipeOutput()).equals(item), "Native crucible receipt must correspond to a matching paid owner recipe");
                JsonObject payment = row.deepCopy(); payment.addProperty("aspectsBeforeNbt", aspects(crucible.aspects).toString());
                payment.addProperty("aspectsAfterNbt", aspects(recipe.removeMatching(crucible.aspects.copy())).toString());
                payment.addProperty("costAspectsNbt", aspects(recipe.getAspects()).toString()); payment.addProperty("waterBefore", crucible.tank.getFluidAmount());
                payment.addProperty("waterAfter", Math.max(0, crucible.tank.getFluidAmount() - 50)); payment.addProperty("sourceEventBeforePayment", true);
                payment.addProperty("postPaymentObserved", false); cruciblePaymentReceipts.add(payment);
            }
        }
        void arcanePaymentBefore(ServerPlayer player, ContainerArcaneWorkbench menu, ItemStack output, int cost) throws Exception {
            owner(player); Action action = action();
            require(server.isSameThread() && mode.equals("generate") && phase.equals("generate") && actionPrepared && action != null && action.kind() == Kind.CRAFT
                && action.position().equals(WORKBENCH) && id(output).equals(action.item()) && output.getCount() == 1
                && cost == (action.item().equals("thaumcraft:smelter_basic") ? 50 : action.item().equals("thaumcraft:infusion_matrix") ? 150 : -1)
                && owner.containerMenu == menu && menu.table() == server.overworld().getBlockEntity(WORKBENCH)
                && server.overworld().getBlockState(WORKBENCH.above()).is(BlocksTC.arcaneWorkbenchCharger) && pendingArcanePayment == null,
                "Before-payment hook differs from the actual contextual reviewed charged arcane operation");
            JsonObject row = new JsonObject(); row.addProperty("item", id(output)); row.addProperty("actionId", action.id()); row.addProperty("menuId", menu.containerId);
            row.addProperty("outputNbt", output.save(server.registryAccess()).toString()); row.addProperty("visBefore", ((TileArcaneWorkbench)menu.table()).availableVis());
            require(row.get("visBefore").getAsInt() >= cost, "Native charged workbench before-payment source lacks its real cost");
            row.add("auraChunksBefore", chargedAuraSnapshot()); row.addProperty("chargedWorkbench", true); row.addProperty("actualTCP", true); row.addProperty("mainThread", true);
            row.addProperty("nativeBeforePaymentObserved", true); pendingArcanePayment = row; event("native-arcane-before-payment-observed", row.deepCopy());
        }
        JsonArray chargedAuraSnapshot() {
            JsonArray result = new JsonArray(); int centerX = WORKBENCH.getX() >> 4, centerZ = WORKBENCH.getZ() >> 4;
            for (int x = centerX - 1; x <= centerX + 1; x++) for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                var chunk = AuraHandler.getAuraChunk(server.overworld(), x, z);
                require(server.overworld().hasChunk(x, z) && chunk != null && chunk.isGenerated() && chunk.getLoc() != null
                    && chunk.getLoc().x == x && chunk.getLoc().z == z && Float.isFinite(chunk.getVis()) && chunk.getVis() >= 0 && Float.isFinite(chunk.getFlux()) && chunk.getFlux() >= 0,
                    "Charged workbench must observe nine actual naturally loaded generated aura chunks");
                JsonObject row = new JsonObject(); row.addProperty("chunkX", x); row.addProperty("chunkZ", z); row.addProperty("base", chunk.getBase());
                row.addProperty("vis", chunk.getVis()); row.addProperty("flux", chunk.getFlux()); row.addProperty("visBits", Float.floatToIntBits(chunk.getVis()));
                row.addProperty("fluxBits", Float.floatToIntBits(chunk.getFlux())); row.addProperty("actuallyLoaded", true); result.add(row);
            } return result;
        }
        void knowledge(ServerPlayer player, ResearchEvent.Knowledge event) throws Exception {
            owner(player); require(!event.isCanceled(), "Native knowledge event cancelled");
            require(mode.equals("generate"), "Unexpected research/knowledge mutation after checkpoint");
            String category = event.getCategory() == null ? "" : event.getCategory().key;
            JsonObject row = new JsonObject(); row.addProperty("category", category); row.addProperty("type", event.getType().name());
            row.addProperty("amount", event.getAmount()); row.addProperty("actionId", action() == null ? phase : action().id());
            if (!category.isBlank()) { int before = raw(event.getType(), category); row.addProperty("beforeRaw", before); row.addProperty("afterRaw", Math.max(0, before + event.getAmount())); }
            if (event.getType() == EnumKnowledgeType.THEORY && event.getAmount() > 0) {
                require(action() != null && action().kind() == Kind.THEORY, "Theory awards require actual reviewed native table note");
                var table = (TileResearchTable) server.overworld().getBlockEntity(THEORY); require(table.data != null && table.data.isComplete() && table.data.ownedBy(owner), "Theory receipt requires actual completed owned source note");
                row.addProperty("awardedRaw", event.getAmount()); row.addProperty("sourceNoteNbt", table.data.serialize(server.registryAccess()).toString());
                row.addProperty("actualSourceNoteFinished", true); nativeTheoryReceipts.add(row.deepCopy());
            }
            nativeKnowledgeReceipts.add(row.deepCopy());
            event("native-knowledge-event", row);
        }
        void supplyDisplayedTheoryMaterials(JsonObject client) throws Exception {
            var table = (TileResearchTable) server.overworld().getBlockEntity(THEORY);
            if (storageBefore != null || client != null && client.has("nativeStorage") && !client.get("nativeStorage").isJsonNull()
                || !(owner.containerMenu instanceof thaumcraft.common.container.ContainerResearchTable)) return;
            // A queued native PICKUP sequence returns to its original inventory slot. Never
            // refill that slot between the source pickup and the final cursor-clearing click.
            if (!owner.containerMenu.getCarried().isEmpty()) return;
            if (client != null && client.has("researchDriver")) {
                JsonObject driver = client.getAsJsonObject("researchDriver");
                if (driver.has("queuedInventoryClicks") && driver.get("queuedInventoryClicks").getAsInt() > 0
                    || driver.has("pendingTheoryAction") && !driver.get("pendingTheoryAction").isJsonNull()
                        && !driver.get("pendingTheoryAction").getAsString().isBlank()) return;
            }
            if (table.getItem(1).isEmpty()) giveOptional("minecraft:paper", "", 64);
            ItemStack ink = table.getItem(0);
            if (ink.isEmpty() || !(ink.getItem() instanceof IScribeTools) || ink.getDamageValue() >= ink.getMaxDamage() - 2) {
                boolean usable = owner.getInventory().items.stream().anyMatch(stack -> stack.getItem() instanceof IScribeTools
                    && stack.getDamageValue() < stack.getMaxDamage() - 2);
                if (!usable) giveOptional("thaumcraft:scribing_tools", "", count("thaumcraft:scribing_tools", "") + 1);
            }
            if (table.data == null) return;
            for (var choice : table.data.cardChoices) {
                ItemStack[] required = choice.card.getRequiredItems(); if (required == null) continue;
                for (ItemStack desired : required) if (desired != null && !desired.isEmpty() && desired.is(ItemsTC.crystalEssence)) {
                    var tags = ((thaumcraft.api.aspects.IEssentiaContainerItem) desired.getItem()).getAspects(desired);
                    if (tags != null && tags.size() == 1) giveOptional(id(desired), tags.getAspects()[0].getTag(), Math.max(2, desired.getCount()));
                }
            }
        }
        void storageOpened(ServerPlayer player, ChestMenu menu) throws Exception {
            owner(player); Action action = action();
            require(mode.equals("generate") && phase.equals("generate") && actionPrepared && action != null && action.kind() == Kind.THEORY,
                "Native material storage is permitted only during reviewed theory");
            JsonObject client = readPeer(directory, marker, "client", false);
            require(client != null && client.has("nativeStorage") && !client.get("nativeStorage").isJsonNull(), "Native chest open lacks its finite storage request");
            JsonObject request = client.getAsJsonObject("nativeStorage"); validateStorageRequest(request, action);
            require(string(request, "phase").equals("opening") && storageBefore == null
                && !string(request, "transactionId").equals(nativeStorageAcceptedId), "Native chest opened outside a new reviewed storage request");
            require(menu == owner.containerMenu && menu.getRowCount() == 6 && menu.getContainer().getContainerSize() == 54
                && menu.getCarried().isEmpty() && ((TileResearchTable)server.overworld().getBlockEntity(THEORY)).data == null,
                "Native material storage requires the actual empty-cursor double chest and note boundary");
            validateStorageDriverBoundary(client);
            require(ordinaryEmptySlots() < 8, "Native material storage opened without the reviewed capacity need");
            storageContainer = menu.getContainer();
            require(storageContainer instanceof CompoundContainer compound
                && compound.contains((ChestBlockEntity)server.overworld().getBlockEntity(STORAGE))
                && compound.contains((ChestBlockEntity)server.overworld().getBlockEntity(STORAGE_PARTNER))
                && field(storageContainer, "container1") == server.overworld().getBlockEntity(STORAGE)
                && field(storageContainer, "container2") == server.overworld().getBlockEntity(STORAGE_PARTNER),
                "Native double chest is not the reviewed physical first/second fixture");
            storageBefore = new JsonObject(); storageBefore.addProperty("transactionId", string(request, "transactionId"));
            storageBefore.addProperty("actionIndex", actionIndex); storageBefore.addProperty("theoryCategory", action.research()); storageBefore.add("position", vector(STORAGE));
            storageBefore.addProperty("menuId", menu.containerId); storageBefore.addProperty("inventoryBeforeNbt", owner.getInventory().save(new ListTag()).toString());
            storageBefore.addProperty("chestBeforeNbt", chestNbt(storageContainer).toString()); storageBefore.addProperty("ordinaryEmptyBefore", ordinaryEmptySlots());
            storageBefore.addProperty("noteBoundaryNoActiveData", true); storageBefore.addProperty("cursorEmpty", true); storageBefore.addProperty("nativeChestMenu54", true);
            if (nativeStorageReceipts.isEmpty()) require(TagParser.parseTag(string(storageBefore, "chestBeforeNbt")).getList("Items", Tag.TAG_COMPOUND).isEmpty(),
                "First actual native storage chest was not physically empty");
            else require(TagParser.parseTag(string(storageBefore, "chestBeforeNbt")).equals(TagParser.parseTag(string(nativeStorageReceipts.get(nativeStorageReceipts.size() - 1).getAsJsonObject(), "chestAfterNbt"))),
                "Actual storage contents changed outside the preceding conserved native transaction");
            event("native-theory-material-storage-opened", storageBefore.deepCopy());
        }
        void validateStorageRequest(JsonObject request, Action action) {
            require(string(request, "transactionId").matches("theory-storage-[1-9][0-9]{0,3}")
                && request.get("actionIndex").getAsInt() == actionIndex && string(request, "theoryCategory").equals(action.research())
                && position(request.getAsJsonArray("position")).equals(STORAGE), "Native storage request differs from its finite reviewed theory action");
        }
        void validateStorageDriverBoundary(JsonObject client) {
            require(client.has("researchDriver"), "Native storage lacks its source research driver state");
            JsonObject driver = client.getAsJsonObject("researchDriver");
            require(driver.get("queuedInventoryClicks").getAsInt() == 0
                && (!driver.has("pendingTheoryAction") || driver.get("pendingTheoryAction").isJsonNull() || string(driver, "pendingTheoryAction").isBlank()),
                "Native storage overlaps pending source theory clicks");
        }
        void observeStorage(JsonObject client) throws Exception {
            if (client == null || !client.has("nativeStorage") || client.get("nativeStorage").isJsonNull()) {
                require(storageBefore == null, "Client abandoned an observed native storage transaction"); return;
            }
            JsonObject request = client.getAsJsonObject("nativeStorage"); Action action = action();
            require(action != null && action.kind() == Kind.THEORY, "Native storage escaped its reviewed theory action"); validateStorageRequest(request, action);
            if (!string(request, "phase").equals("await-server")) return;
            if (string(request, "transactionId").equals(nativeStorageAcceptedId)) return;
            require(storageBefore != null && storageContainer != null && string(request, "transactionId").equals(string(storageBefore, "transactionId")),
                "Native storage result lacks its actual server menu-open snapshot");
            require(owner.containerMenu == owner.inventoryMenu && owner.containerMenu.getCarried().isEmpty()
                && ((TileResearchTable)server.overworld().getBlockEntity(THEORY)).data == null, "Native storage result requires a real closed menu and empty source note");
            validateStorageDriverBoundary(client);
            require(request.get("actualNativeMenuId").getAsInt() == storageBefore.get("menuId").getAsInt()
                && request.get("actualNativeChestMenu").getAsBoolean(), "Native storage client received a different actual double-chest menu");
            ListTag inventoryBefore = inventoryTag(string(storageBefore, "inventoryBeforeNbt")), inventoryAfter = owner.getInventory().save(new ListTag());
            CompoundTag chestBefore = TagParser.parseTag(string(storageBefore, "chestBeforeNbt")), chestAfter = chestNbt(storageContainer);
            require(inventoryBefore.equals(inventoryTag(string(request, "inventoryBeforeNbt"))) && chestBefore.equals(TagParser.parseTag(string(request, "chestBeforeNbt")))
                && inventoryAfter.equals(inventoryTag(string(request, "inventoryAfterNbt"))) && chestAfter.equals(TagParser.parseTag(string(request, "chestAfterNbt"))),
                "Actual server inventory/chest snapshots differ from received native client snapshots");
            require(storageTotals(inventoryBefore, chestBefore.getList("Items", Tag.TAG_COMPOUND)).equals(storageTotals(inventoryAfter, chestAfter.getList("Items", Tag.TAG_COMPOUND))),
                "Native material storage failed full typed item conservation");
            require(nonCrystals(inventoryBefore).equals(nonCrystals(inventoryAfter)) && nonCrystals(chestBefore.getList("Items", Tag.TAG_COMPOUND)).equals(nonCrystals(chestAfter.getList("Items", Tag.TAG_COMPOUND))),
                "Native material storage changed a reviewed non-crystal item");
            int moved = crystalCount(inventoryBefore) - crystalCount(inventoryAfter);
            require(moved > 0 && crystalCount(chestAfter.getList("Items", Tag.TAG_COMPOUND)) - crystalCount(chestBefore.getList("Items", Tag.TAG_COMPOUND)) == moved
                && ordinaryEmptySlots() >= 8 && ordinaryEmptySlots() == request.get("ordinaryEmptyAfter").getAsInt()
                && storageBefore.get("ordinaryEmptyBefore").getAsInt() == request.get("ordinaryEmptyBefore").getAsInt(), "Native storage did not move actual crystal materials into bounded free capacity");
            JsonArray clicks = request.getAsJsonArray("nativeClicks"); require(clicks != null && !clicks.isEmpty() && clicks.size() <= 36
                && clicks.size() == request.get("nativeQuickMoveCount").getAsInt(), "Native storage lacks finite actual QUICK_MOVE receipts");
            for (JsonObject click : objects(clicks)) require(click.get("menuId").getAsInt() == storageBefore.get("menuId").getAsInt()
                && click.get("slot").getAsInt() >= 54 && click.get("slot").getAsInt() < 90 && click.get("button").getAsInt() == 0
                && string(click, "clickType").equals("QUICK_MOVE") && click.get("nativeC2S").getAsBoolean()
                && string(click, "transactionId").equals(string(storageBefore, "transactionId"))
                && TagParser.parseTag(string(click, "stackBeforeNbt")).getString("id").equals("thaumcraft:crystal_essence"), "Native storage click escaped its actual crystal-only reviewed menu");
            JsonObject receipt = storageBefore.deepCopy(); receipt.addProperty("inventoryAfterNbt", inventoryAfter.toString()); receipt.addProperty("chestAfterNbt", chestAfter.toString());
            receipt.addProperty("ordinaryEmptyAfter", ordinaryEmptySlots()); receipt.addProperty("nativeQuickMoveCount", clicks.size()); receipt.add("nativeClicks", clicks.deepCopy());
            receipt.addProperty("crystalsMoved", moved); receipt.addProperty("actualServerAccepted", true); receipt.addProperty("actualTCP", true); receipt.addProperty("mainThread", server.isSameThread());
            nativeStorageReceipts.add(receipt); event("native-theory-material-storage-conserved", receipt.deepCopy());
            nativeStorageAcceptedId = string(receipt, "transactionId"); storageBefore = null; storageContainer = null; publish();
        }
        int ordinaryEmptySlots() { int result = 0; for (ItemStack stack : owner.getInventory().items) if (stack.isEmpty()) result++; return result; }
        CompoundTag chestNbt(Container container) {
            require(container.getContainerSize() == 54, "Actual material storage snapshot needs fifty-four native slots");
            ListTag items = new ListTag(); for (int index = 0; index < container.getContainerSize(); index++) {
                ItemStack stack = container.getItem(index); if (stack.isEmpty()) continue;
                CompoundTag value = (CompoundTag)stack.save(server.registryAccess()); value.putByte("Slot", (byte)index); items.add(value);
            } CompoundTag result = new CompoundTag(); result.putInt("Size", 54); result.put("Items", items); return result;
        }
        ListTag inventoryTag(String value) throws Exception { return TagParser.parseTag("{Items:" + value + "}").getList("Items", Tag.TAG_COMPOUND); }
        Map<CompoundTag, Integer> storageTotals(ListTag inventory, ListTag chest) {
            Map<CompoundTag, Integer> result = new HashMap<>(); for (ListTag items : List.of(inventory, chest)) for (Tag item : items) {
                CompoundTag value = ((CompoundTag)item).copy(); int count = value.getInt("count"); require(count > 0, "Native storage contains a nonpositive typed stack");
                value.remove("Slot"); value.remove("count"); result.merge(value, count, Integer::sum);
            } return result;
        }
        ListTag nonCrystals(ListTag items) { ListTag result = new ListTag(); for (Tag item : items) if (!((CompoundTag)item).getString("id").equals("thaumcraft:crystal_essence")) result.add(item.copy()); return result; }
        int crystalCount(ListTag items) { int result = 0; for (Tag item : items) if (((CompoundTag)item).getString("id").equals("thaumcraft:crystal_essence")) result += ((CompoundTag)item).getInt("count"); return result; }
        void observePipeline() throws Exception {
            for (JsonObject receipt : objects(nativePhialReceipts)) if (!receipt.has("afterAmount")) {
                require(jar(JAR).amount == receipt.get("expectedAfterAmount").getAsInt(), "Native quiet phial conversion did not transfer exactly ten paid air units");
                receipt.addProperty("afterAmount", jar(JAR).amount); receipt.addProperty("transferredAmount", 10);
                receipt.addProperty("inventoryAfterNbt", owner.getInventory().save(new ListTag()).toString()); receipt.addProperty("actualInventoryAndBlockPaymentObserved", true);
                event("native-ten-unit-phial-conversion-observed", receipt.deepCopy());
            }
            for (JsonObject payment : objects(cruciblePaymentReceipts)) if (!payment.get("postPaymentObserved").getAsBoolean()) {
                var tile = (TileCrucible) server.overworld().getBlockEntity(CRUCIBLE);
                require(tile.tank.getFluidAmount() == payment.get("waterAfter").getAsInt()
                    && aspects(tile.aspects).equals(TagParser.parseTag(string(payment, "aspectsAfterNbt"))), "Native crucible payment/output postcondition differs from real pre-payment receipt");
                payment.addProperty("postPaymentObserved", true); event("native-crucible-payment-observed", payment.deepCopy());
            }
            if (!nativeScanReceipts.isEmpty()) {
                JsonObject row = nativeScanReceipts.get(nativeScanReceipts.size() - 1).getAsJsonObject();
                if (!row.has("knowledgeAfter") && known(string(row, "researchKey"))) { row.add("knowledgeAfter", pools()); row.addProperty("knowledgeAfterNbt", ThaumcraftCapabilities.getKnowledge(owner).serializeNBT().toString()); }
            }
            for (String aspect : List.of("aer", "vitreus", "vinculum")) {
                BlockPos pos = sourceSmelter(aspect); if (!(server.overworld().getBlockEntity(pos) instanceof TileSmelter smelter)) continue;
                if (action() != null && action().kind() == Kind.SMELTER_LOAD && action().research().equals(aspect) && smelter.furnaceBurnTime > 0 && !inputConsumed.containsKey(aspect)) {
                    inputConsumed.put(aspect, 0); produced.put(aspect, 0);
                }
                if (!inputConsumed.containsKey(aspect)) continue;
                int batch = aspect.equals("aer") ? 64 : aspect.equals("vitreus") ? 8 : 32;
                int consumed = batch - smelter.getItem(0).getCount(); if (consumed <= inputConsumed.get(aspect)) continue;
                int total = pipelineAmount(aspect), previous = produced.get(aspect), unitsPerInput = aspect.equals("vitreus") ? 5 : 1;
                require(total >= previous && total - previous <= (consumed - inputConsumed.get(aspect)) * unitsPerInput,
                    "Native smelter/tube/jar chain created unpaid essentia or lost stored output");
                JsonObject row = pipeline(aspect); row.addProperty("inputConsumedBefore", inputConsumed.get(aspect)); row.addProperty("inputConsumedAfter", consumed);
                row.addProperty("producedBefore", previous); row.addProperty("producedAfter", total); row.addProperty("naturalEfficiencyLoss", (consumed - inputConsumed.get(aspect)) * unitsPerInput - (total - previous));
                row.addProperty("nativeFuelBurned", smelter.furnaceBurnTime > 0); essentiaConservationRecords.add(row);
                event("native-essentia-conservation", row.deepCopy()); inputConsumed.put(aspect, consumed); produced.put(aspect, total);
            }
            if (initialInfusionCosts != null && matrix().crafting) validateInfusionSourcePayment();
        }
        void observeInfusionStartBeforePayment(BlockPos position, ItemStack held) throws Exception {
            require(position.equals(MATRIX) && id(held).equals("thaumcraft:caster_basic") && matrix().active && !matrix().crafting
                && matrix().stability >= matrix().stabilityCap && initialInfusionCosts == null && Float.compare(matrix().costMult, 1F) == 0,
                "Native infusion start must observe the actual settled reviewed matrix before its first payment");
            ItemStack central = ((TilePedestal)server.overworld().getBlockEntity(CENTER)).getItem(0).copy();
            List<ItemStack> components = List.of(((TilePedestal)server.overworld().getBlockEntity(LEFT)).getItem(0).copy(),
                ((TilePedestal)server.overworld().getBlockEntity(RIGHT)).getItem(0).copy());
            require(central.is(ItemsTC.crystalEssence) && central.getCount() == 1
                && ((thaumcraft.api.aspects.IEssentiaContainerItem)central.getItem()).getAspects(central).getAmount(Aspect.AIR) == 1
                && id(components.get(0)).equals("minecraft:wheat_seeds") && components.get(0).getCount() == 1
                && id(components.get(1)).equals("thaumcraft:salis_mundus") && components.get(1).getCount() == 1,
                "Actual source infusion input/components differ from the reviewed native recipe");
            var recipe = InfusionRecipes.findMatching(components, central, owner);
            require(recipe != null && recipe.getClass() == thaumcraft.api.crafting.InfusionRecipe.class && recipe.getResearch().equals("CRYSTALFARMER")
                && known("CRYSTALFARMER") && recipe.getInstability(owner, central, components) == 0, "Native source owner/input recipe gate differs");
            AspectList original = recipe.getAspects(owner, central, components), costs = new AspectList();
            for (Aspect aspect : original.getAspects()) {
                int cost = InfusionMatrixRules.cost(original.getAmount(aspect), matrix().costMult); if (cost > 0) costs.add(aspect, cost);
            }
            require(costs.size() == 3 && costs.getAmount(Aspect.AIR) == 10 && costs.getAmount(Aspect.CRYSTAL) == 10 && costs.getAmount(Aspect.TRAP) == 5,
                "Actual reviewed source infusion cost is not its complete original twenty-five paid units");
            initialInfusionCosts = aspects(costs); JsonObject jars = new JsonObject();
            for (Aspect aspect : costs.getAspects()) {
                require(pipelineIdle(aspect.getTag()), "Native source infusion payment requires actual quiet paid jars");
                jars.addProperty(aspect.getTag(), jar(sourceSmelter(aspect.getTag()).east(2)).amount);
            }
            JsonObject row = new JsonObject(); row.addProperty("actionId", action().id()); row.addProperty("recipeResearch", recipe.getResearch());
            row.addProperty("centralInputNbt", central.save(server.registryAccess()).toString()); ListTag inputs = new ListTag();
            for (ItemStack component : components) inputs.add(component.save(server.registryAccess())); row.addProperty("componentsNbt", inputs.toString());
            row.addProperty("initialCosts", initialInfusionCosts.toString()); row.add("jarAmountsBefore", jars); row.addProperty("costMultiplier", matrix().costMult);
            row.addProperty("nativeMatrixCountBefore", matrix().count); row.addProperty("nativeSourceRecipeClass", recipe.getClass().getName());
            row.addProperty("nativeServerInteractionObservedBeforeFirstPayment", true); row.addProperty("actualTCP", true); row.addProperty("mainThread", server.isSameThread());
            nativeInfusionStartObservation = row; event("native-infusion-source-costs-observed-before-payment", row.deepCopy());
        }
        void validateInfusionSourcePayment() {
            require(nativeInfusionStartObservation != null, "Actual source infusion lacks its before-payment recipe observation");
            JsonObject jars = nativeInfusionStartObservation.getAsJsonObject("jarAmountsBefore");
            for (Tag value : initialInfusionCosts.getList("Aspects", Tag.TAG_COMPOUND)) {
                CompoundTag cost = (CompoundTag)value; String tag = cost.getString("key"); int initial = cost.getInt("amount"), remaining = matrix().getAspects().getAmount(Aspect.getAspect(tag));
                require(remaining >= 0 && remaining <= initial && jars.get(tag).getAsInt() - jar(sourceSmelter(tag).east(2)).amount == initial - remaining,
                    "Actual source infusion outstanding cost and real paid jar debit do not balance: " + tag);
            }
        }
        BlockPos sourceSmelter(String aspect) { return aspect.equals("aer") ? SMELTER : aspect.equals("vitreus") ? SMELTER_CRYSTAL : SMELTER_TRAP; }
        int pipelineAmount(String aspect) {
            BlockPos pos = sourceSmelter(aspect); Aspect tag = Aspect.getAspect(aspect); int total = ((TileSmelter) server.overworld().getBlockEntity(pos)).aspects.getAmount(tag);
            total += ((TileAlembic) server.overworld().getBlockEntity(pos.above())).containerContains(tag);
            total += jar(pos.east(2)).containerContains(tag);
            for (int x : List.of(1, 2)) total += ((TileTube) server.overworld().getBlockEntity(pos.above().east(x))).getEssentiaType(null) == tag
                ? ((TileTube) server.overworld().getBlockEntity(pos.above().east(x))).getEssentiaAmount(null) : 0;
            return total;
        }
        JsonObject pipeline(String aspect) {
            BlockPos pos = sourceSmelter(aspect); Aspect tag = Aspect.getAspect(aspect); JsonObject row = new JsonObject(); row.addProperty("aspect", aspect);
            row.addProperty("smelterAmount", ((TileSmelter) server.overworld().getBlockEntity(pos)).aspects.getAmount(tag));
            row.addProperty("alembicAmount", ((TileAlembic) server.overworld().getBlockEntity(pos.above())).containerContains(tag)); row.addProperty("jarAmount", jar(pos.east(2)).containerContains(tag));
            int tubes = 0; for (int x : List.of(1, 2)) tubes += ((TileTube) server.overworld().getBlockEntity(pos.above().east(x))).getEssentiaAmount(null);
            row.addProperty("tubeAmount", tubes); row.addProperty("totalStored", pipelineAmount(aspect)); return row;
        }
        boolean pipelineIdle(String aspect) {
            BlockPos pos = sourceSmelter(aspect); return ((TileSmelter) server.overworld().getBlockEntity(pos)).getItem(0).isEmpty()
                && ((TileSmelter) server.overworld().getBlockEntity(pos)).aspects.visSize() == 0
                && ((TileAlembic) server.overworld().getBlockEntity(pos.above())).amount == 0
                && ((TileTube) server.overworld().getBlockEntity(pos.above().east())).getEssentiaAmount(null) == 0
                && ((TileTube) server.overworld().getBlockEntity(pos.above().east(2))).getEssentiaAmount(null) == 0;
        }
        TileJarFillable jar(BlockPos position) { return (TileJarFillable) server.overworld().getBlockEntity(position); }
        TileInfusionMatrix matrix() { return (TileInfusionMatrix) server.overworld().getBlockEntity(MATRIX); }

        void captureCheckpoint(boolean disk) throws Exception {
            validateInfusionSourcePayment();
            CompoundTag job = disk ? diskTile(MATRIX) : liveMatrixNbt(matrix());
            require(job.getBoolean("active") && job.getBoolean("crafting") && job.getString("recipeplayer").equals(USERNAME), "Real source owned infusion job is not active");
            CompoundTag remaining = aspects(matrix().getAspects()); int initial = aspectTotal(initialInfusionCosts), left = aspectTotal(remaining);
            require(left > 0 && left < initial, "Checkpoint must have both naturally paid and outstanding essentia");
            infusionCheckpoint = new JsonObject(); infusionCheckpoint.addProperty("jobNbt", job.toString()); infusionCheckpoint.addProperty("initialCosts", initialInfusionCosts.toString());
            infusionCheckpoint.addProperty("remainingCosts", remaining.toString()); infusionCheckpoint.addProperty("paidUnits", initial - left); infusionCheckpoint.addProperty("totalRemaining", left);
            CompoundTag paid = initialInfusionCosts.copy(); var list = paid.getList("Aspects", Tag.TAG_COMPOUND);
            for (var value : list) { var entry = (CompoundTag)value; entry.putInt("amount", entry.getInt("amount") - matrix().getAspects().getAmount(Aspect.getAspect(entry.getString("key")))); }
            infusionCheckpoint.addProperty("paidCosts", paid.toString()); infusionCheckpoint.addProperty("actualNativeCasterStarted", true); infusionCheckpoint.addProperty("savedDiskJob", disk);
            infusionCheckpoint.add("costObservation", nativeInfusionStartObservation.deepCopy());
            JsonObject jarsAfter = new JsonObject(); for (String aspect : List.of("aer", "vitreus", "vinculum")) jarsAfter.addProperty(aspect, jar(sourceSmelter(aspect).east(2)).amount);
            infusionCheckpoint.add("jarAmountsAfter", jarsAfter); infusionCheckpoint.addProperty("actualSourceCostAndJarPaymentBalanced", true);
        }
        void loaded(LevelChunk chunk) throws Exception {
            if (mode.equals("generate")) return;
            for (JsonObject row : objects(sourceBaseline.getAsJsonArray("worldTiles"))) {
                BlockPos position = position(row.getAsJsonArray("position")); if (!new ChunkPos(position).equals(chunk.getPos()) || loadedPositions.contains(position)) continue;
                require(id(chunk.getBlockState(position).getBlock()).equals(string(row, "block")), "Naturally loaded source block differs: " + position);
                if (row.has("nbt")) {
                    BlockEntity tile = chunk.getBlockEntity(position); require(tile != null, "Naturally loaded source tile missing");
                    CompoundTag actual = tile instanceof TileInfusionMatrix matrix ? liveMatrixNbt(matrix) : tile.saveWithFullMetadata(server.registryAccess());
                    CompoundTag expected = TagParser.parseTag(string(row, "nbt"));
                    // Vanilla's chunk container adds this serialization marker after native BE save.
                    if (expected.contains("keepPacked")) actual.putBoolean("keepPacked", false);
                    require(normalizeTile(actual).equals(normalizeTile(expected)), "Loaded native persistent tile differs before first tick: " + position);
                    row = row.deepCopy(); row.addProperty("actualLoadedNbt", actual.toString());
                    if (position.equals(MATRIX) && mode.equals("resume")) {
                        require(((TileInfusionMatrix)tile).active && ((TileInfusionMatrix)tile).crafting && ((TileInfusionMatrix)tile).getAspects().visSize() > 0, "Saved partially paid native job did not resume");
                        loadedJobObserved = true; resumedInfusion.addProperty("loadBeforeFirstTick", true); resumedInfusion.addProperty("loadedJobNbt", actual.toString());
                        resumedInfusion.addProperty("outstandingAtLoad", ((TileInfusionMatrix)tile).getAspects().visSize());
                    }
                }
                loadedPositions.add(position); JsonObject receipt = row.deepCopy(); receipt.addProperty("beforeFirstChunkTick", true); sourceWorldLoadedTiles.add(receipt.deepCopy()); event("natural-source-chunk-loaded", receipt);
            }
        }
        void resume(JsonObject client) throws Exception {
            if (phase.equals("resume-job")) {
                var matrix = matrix(); int remaining = matrix.getAspects().visSize();
                paidAfterReload = resumedInfusion.get("outstandingAtLoad").getAsInt() - remaining;
                if (!matrix.crafting) {
                    var center = (TilePedestal)server.overworld().getBlockEntity(CENTER); require(id(center.getItem(0)).equals("thaumcraft:crystal_aer") && center.getItem(0).getCount() == 1,
                        "Actual naturally resumed source infusion did not produce its paid real output");
                    require(remaining == 0 && ((TilePedestal)server.overworld().getBlockEntity(LEFT)).isEmpty() && ((TilePedestal)server.overworld().getBlockEntity(RIGHT)).isEmpty(),
                        "Resumed native infusion did not pay all outstanding essentia/components");
                    resumedInfusion.addProperty("paidAfterReload", paidAfterReload); resumedInfusion.addProperty("outputItem", "thaumcraft:crystal_aer");
                    resumedInfusion.addProperty("outputCount", 1); resumedInfusion.addProperty("peripheralPedestalsEmpty", true); resumedInfusion.addProperty("completedByNativeTicks", true);
                    phase = "output"; publish();
                }
            } else if (phase.equals("output")) {
                if (client != null && client.has("resumedOutputPickedUp") && client.get("resumedOutputPickedUp").getAsBoolean()) {
                    require(count("thaumcraft:crystal_aer", "") == 1 && ((TilePedestal)server.overworld().getBlockEntity(CENTER)).isEmpty(), "Actual native pedestal output was not collision-picked into owner inventory");
                    phase = "reconnect"; quietSince = server.getTickCount(); publish();
                }
            }
        }
        void reconnect(JsonObject client) throws Exception {
            ServerPlayer current = server.getPlayerList().getPlayerByName(USERNAME);
            if (!reconnectAllowed) {
                owner(owner); if (client == null || !client.has("reconnectReady") || !client.get("reconnectReady").getAsBoolean()) return;
                require(owner.containerMenu == owner.inventoryMenu && owner.containerMenu.getCarried().isEmpty(), "Reconnect needs actual closed native menu");
                JsonObject candidate = playerSnapshot(owner);
                if (quietCandidate == null || !samePlayer(candidate, quietCandidate)) { quietCandidate = candidate; quietSince = server.getTickCount(); }
                if (server.getTickCount() - quietSince >= 40) { persistenceBaseline = candidate; reconnectAllowed = true; publish(); }
            } else if (current == null && !connection.isConnected()) {
                require(server.getPlayerCount() == 0, "Actual native old owner did not leave server list"); actualOldConnectionClosed = true; publish();
            } else if (current != null && current != owner) {
                require(actualOldConnectionClosed && !connection.isConnected() && current.connection.getConnection() != connection, "Reconnect did not replace actual closed native TCP owner");
                owner(current); assertPlayer(current, persistenceBaseline, false); owner = current; connection = current.connection.getConnection(); ownerEpoch = 2;
                reconnectProven = true; phase = "finish"; check("native-second-owner-tcp-reconnect"); publish();
            }
        }
        boolean samePlayer(JsonObject a, JsonObject b) throws Exception {
            JsonObject left = a.deepCopy(), right = b.deepCopy(); String lk = string(left, "knowledgeNbt"), rk = string(right, "knowledgeNbt");
            left.remove("knowledgeNbt"); right.remove("knowledgeNbt");
            return left.equals(right) && TcpBootstrapReloadAudit.canonicalKnowledge(TagParser.parseTag(lk), false).equals(TcpBootstrapReloadAudit.canonicalKnowledge(TagParser.parseTag(rk), false));
        }
        JsonArray savedTiles() throws Exception {
            JsonArray tiles = new JsonArray(); for (BlockPos pos : machinePositions()) {
                JsonObject row = new JsonObject(); row.add("position", vector(pos)); row.addProperty("block", id(server.overworld().getBlockState(pos).getBlock()));
                if (server.overworld().getBlockEntity(pos) != null) row.addProperty("nbt", diskTile(pos).toString()); tiles.add(row);
            } return tiles;
        }
        CompoundTag diskTile(BlockPos pos) {
            ChunkPos coordinate = new ChunkPos(pos); CompoundTag saved = server.overworld().getChunkSource().chunkMap.read(coordinate).orTimeout(10, TimeUnit.SECONDS).join()
                .orElseThrow(() -> new IllegalStateException("Actual saved native chunk missing"));
            require(saved.getString("Status").equals("minecraft:full") && saved.getInt("xPos") == coordinate.x && saved.getInt("zPos") == coordinate.z, "Saved native arena chunk identity differs");
            for (Tag value : saved.getList("block_entities", Tag.TAG_COMPOUND)) { CompoundTag tile = (CompoundTag)value;
                if (tile.getInt("x") == pos.getX() && tile.getInt("y") == pos.getY() && tile.getInt("z") == pos.getZ()) return tile.copy();
            } throw new IllegalStateException("Actual disk native tile missing: " + pos);
        }
        void assertDiskPlayer(JsonObject expected) throws Exception {
            CompoundTag saved = NbtIo.readCompressed(safePath(directory, world.resolve("playerdata").resolve(OWNER_UUID + ".dat")), NbtAccounter.create(4L * 1024 * 1024));
            require(saved.hasUUID("UUID") && saved.getUUID("UUID").equals(OWNER_UUID), "Actual saved owner UUID differs");
            CompoundTag attachments = saved.getCompound("neoforge:attachments");
            require(TcpBootstrapReloadAudit.canonicalKnowledge(attachments.getCompound("thaumcraft:knowledge"), false).equals(
                TcpBootstrapReloadAudit.canonicalKnowledge(TagParser.parseTag(string(expected, "knowledgeNbt")), false)), "Actual saved full knowledge differs");
            require(attachments.getCompound("thaumcraft:warp").equals(TagParser.parseTag(string(expected, "warpNbt")))
                && saved.getList("Inventory", Tag.TAG_COMPOUND).equals(TcpBootstrapReloadAudit.inventory(expected)), "Actual saved warp/inventory differs");
            require(saved.getInt("SelectedItemSlot") == expected.get("selectedSlot").getAsInt() && saved.getInt("XpTotal") == expected.get("totalExperience").getAsInt()
                && saved.getInt("XpLevel") == expected.get("experienceLevel").getAsInt()
                && Float.floatToIntBits(saved.getFloat("XpP")) == Float.floatToIntBits(expected.get("experienceProgress").getAsFloat()), "Actual saved selected slot/XP differs");
        }
        void finish(boolean passed, String failure) {
            if (finished) return; finished = true; JsonObject result = state();
            try {
                if (passed) {
                    require(owner != null && owner.containerMenu == owner.inventoryMenu, "Terminal native owner/menu required");
                    persistenceBaseline = playerSnapshot(owner); server.getPlayerList().saveAll(); require(server.saveEverything(true, true, true), "Actual native world save did not flush");
                    assertDiskPlayer(persistenceBaseline); persistenceBaseline.add("worldTiles", savedTiles());
                    if (mode.equals("generate")) captureCheckpoint(true);
                    result.addProperty("saveFlushed", true); result.addProperty("diskNbtVerified", true);
                    result.addProperty("diskPlayerBytes", Files.size(world.resolve("playerdata").resolve(OWNER_UUID + ".dat")));
                }
                result.addProperty("passed", passed); result.addProperty("status", passed ? "completed" : "failed"); result.addProperty("dedicatedServer", true);
                result.addProperty("noKnowledgeResearchFixtures", true); result.addProperty("noAspectOrRecipeEffectFixtures", true); result.addProperty("rngNotOverridden", true);
                result.addProperty("noArenaOrMaterialsWritten", !mode.equals("generate")); result.addProperty("checkpointedInfusion", passed && mode.equals("generate"));
                result.addProperty("alchemyComplete", passed && !mode.equals("generate")); result.addProperty("newJvmReloadProven", passed && mode.equals("reload"));
                result.addProperty("loadedJobObservedBeforeFirstTick", loadedJobObserved); result.addProperty("finalFirststepsStage", 4);
                result.add("nativeResearchRequests", nativeResearchRequests); result.add("nativeCraftReceipts", nativeCraftReceipts); result.add("nativeScanReceipts", nativeScanReceipts);
                result.add("nativeTheoryReceipts", nativeTheoryReceipts); result.add("cruciblePaymentReceipts", cruciblePaymentReceipts); result.add("arcanePaymentReceipts", arcanePaymentReceipts);
                result.add("nativeKnowledgeReceipts", nativeKnowledgeReceipts); result.add("nativePhialReceipts", nativePhialReceipts);
                result.add("nativeStorageReceipts", nativeStorageReceipts);
                if (nativeInfusionStartObservation != null) result.add("nativeInfusionStartObservation", nativeInfusionStartObservation);
                result.add("essentiaConservationRecords", essentiaConservationRecords); result.add("fixtures", fixtures); result.add("checks", checks);
                if (operatorAbortRequest != null) { result.addProperty("operatorAborted", true); result.add("operatorAbortRequest", operatorAbortRequest); }
                if (sourceLoadedPlayer != null) result.add("sourceLoadedPlayer", sourceLoadedPlayer);
                result.add("sourceWorldLoadedTiles", sourceWorldLoadedTiles);
                if (persistenceBaseline != null) { result.add("persistenceBaseline", persistenceBaseline); JsonObject ledger = new JsonObject(); if (persistenceBaseline.has("worldTiles")) ledger.add("tiles", persistenceBaseline.get("worldTiles").deepCopy()); result.add("worldBaseline", ledger); }
                if (infusionCheckpoint != null) result.add("infusionCheckpoint", infusionCheckpoint); if (resumedInfusion != null) result.add("resumedInfusion", resumedInfusion);
                if (failure != null) result.addProperty("failure", failure); writeTerminal(directory, "server", result);
                phase = passed ? "completed" : "failed"; publish(); Thaumcraft.log.info("Private native TCP alchemy {} passed={}: {}", mode, passed, failure);
            } catch (Exception error) {
                Thaumcraft.log.error("Private TCP alchemy terminal failed", error);
                try { if (!Files.exists(directory.resolve("server-report.json"))) { result.addProperty("passed", false); result.addProperty("status", "failed"); result.addProperty("failure", error.toString()); writeTerminal(directory, "server", result); } }
                catch (Exception ignored) { Thaumcraft.log.error("Private TCP alchemy report could not be sealed", ignored); }
            } finally { server.halt(false); }
        }
        boolean operatorAbort() throws Exception {
            Path abort = safePath(directory, directory.resolve("operator-abort.json"));
            JsonObject request = readDocument(abort, 4096, true); if (request == null) return false;
            require(string(request, "runId").equals(string(marker, "runId")) && string(request, "token").equals(string(marker, "token")),
                "Operator abort identity differs from this exact native cohort");
            String reason = string(request, "reason"); require(!reason.isBlank() && reason.length() <= 512, "Operator abort needs a bounded explicit reason");
            operatorAbortRequest = request.deepCopy(); event("explicit-operator-abort", request.deepCopy());
            finish(false, "Operator aborted native cohort: " + reason); return true;
        }
        void check(String name) throws Exception { JsonObject row = new JsonObject(); row.addProperty("name", name); row.addProperty("passed", true); checks.add(row); event("check", row); }
        void event(String name, JsonObject details) throws Exception {
            JsonObject row = identity(marker); row.addProperty("sequence", ++eventSequence); row.addProperty("name", name); row.addProperty("phase", phase);
            row.addProperty("serverTick", server.getTickCount()); row.addProperty("utc", Instant.now().toString()); row.addProperty("mainThread", server.isSameThread()); row.add("details", details);
            appendEvent(directory, "server", row);
        }
        JsonObject state() {
            JsonObject value = identity(marker); value.addProperty("status", finished ? (phase.equals("failed") ? "failed" : "completed") : "running"); value.addProperty("phase", phase);
            value.addProperty("actionPrepared", actionPrepared);
            value.addProperty("nativeStorageAcceptedId", nativeStorageAcceptedId);
            value.addProperty("actionIndex", actionIndex); Action action = action(); if (action != null) { value.addProperty("actionId", action.id()); value.addProperty("actionKind", action.kind().name()); }
            value.addProperty("serverTick", server.getTickCount()); value.addProperty("ownerUuid", OWNER_UUID.toString()); value.addProperty("ownerEpoch", ownerEpoch);
            value.addProperty("reconnectAllowed", reconnectAllowed); value.addProperty("reconnectProven", reconnectProven); value.addProperty("actualOldConnectionClosed", actualOldConnectionClosed);
            value.addProperty("loadedJobObserved", loadedJobObserved); value.addProperty("loadedJobObservedBeforeFirstTick", loadedJobObserved);
            value.addProperty("outputItem", "thaumcraft:crystal_aer"); value.addProperty("outputAmount", 1); value.add("outputPosition", vector(CENTER));
            if (owner != null) { value.addProperty("actualTCP", !connection.isMemoryConnection()); value.add("knowledgePools", pools()); }
            if (persistenceBaseline != null) value.add("persistenceBaseline", persistenceBaseline.deepCopy());
            if (mode.equals("generate") && phase.equals("checkpoint") && infusionCheckpoint != null) value.add("infusionCheckpoint", infusionCheckpoint.deepCopy());
            return value;
        }
        void publish() throws Exception { writeState(directory, "server", state()); lastPublished = server.getTickCount(); }
    }
    private static String id(ItemStack stack) { return stack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    private static String id(Block block) { return BuiltInRegistries.BLOCK.getKey(block).toString(); }
    private static CompoundTag aspects(AspectList list) { CompoundTag value = new CompoundTag(); list.writeToNBT(value); return value; }
    private static int aspectTotal(CompoundTag tag) { int total = 0; for (Tag value : tag.getList("Aspects", Tag.TAG_COMPOUND)) total += ((CompoundTag)value).getInt("amount"); return total; }
    private static List<JsonObject> objects(JsonArray array) { return java.util.stream.StreamSupport.stream(array.spliterator(), false).map(value -> value.getAsJsonObject()).toList(); }
    private static Set<BlockPos> tilePositions(JsonObject baseline) { Set<BlockPos> positions = new HashSet<>(); for (JsonObject row : objects(baseline.getAsJsonArray("worldTiles"))) positions.add(position(row.getAsJsonArray("position"))); return positions; }
    private static Object field(Object owner, String name) throws Exception { Field value = owner.getClass().getDeclaredField(name); value.setAccessible(true); return value.get(owner); }
    /** Read the native job without saveAdditional's source-only count++ side effect. */
    private static CompoundTag liveMatrixNbt(TileInfusionMatrix matrix) throws Exception {
        CompoundTag value = matrix.writeSyncNBT(new CompoundTag()); value.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(matrix.getType()).toString());
        value.putInt("x", MATRIX.getX()); value.putInt("y", MATRIX.getY()); value.putInt("z", MATRIX.getZ());
        @SuppressWarnings("unchecked") List<ItemStack> ingredients = (List<ItemStack>)field(matrix, "recipeIngredients");
        if (!ingredients.isEmpty()) { ListTag rows = new ListTag(); for (ItemStack stack : ingredients) if (!stack.isEmpty()) rows.add(stack.save(matrix.getLevel().registryAccess())); value.put("recipein", rows); }
        Object output = field(matrix, "recipeOutput"); if (output instanceof ItemStack stack) { value.putString("rotype", "@"); value.put("recipeout", stack.save(matrix.getLevel().registryAccess())); }
        else if (output instanceof Tag tag) { value.putString("rotype", (String)field(matrix, "recipeOutputLabel")); value.put("recipeout", tag.copy()); }
        ItemStack input = (ItemStack)field(matrix, "recipeInput"); if (!input.isEmpty()) value.put("recipeinput", input.save(matrix.getLevel().registryAccess()));
        value.putInt("recipetype", (Integer)field(matrix, "recipeType")); value.putInt("recipexp", (Integer)field(matrix, "recipeXP"));
        String player = (String)field(matrix, "recipePlayer"); value.putString("recipeplayer", player == null ? "" : player); return value;
    }
    private static CompoundTag normalizeTile(CompoundTag nbt) {
        CompoundTag value = nbt.copy(); for (Tag row : value.getList("recipein", Tag.TAG_COMPOUND)) ((CompoundTag)row).remove("item"); return value;
    }
}
