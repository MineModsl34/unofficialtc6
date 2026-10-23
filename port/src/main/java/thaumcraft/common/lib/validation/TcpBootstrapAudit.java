package thaumcraft.common.lib.validation;

import static thaumcraft.common.lib.validation.TcpBootstrapAuditIO.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.ResearchEvent;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.common.container.ContainerArcaneWorkbench;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchCosts;
import thaumcraft.common.lib.research.LegacyScanIdentity;
import thaumcraft.common.lib.utils.ScanTargeting;
import thaumcraft.common.tiles.crafting.TileArcaneWorkbench;
import thaumcraft.common.world.aura.AuraHandler;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.entities.EntitySpecialItem;
import thaumcraft.common.lib.network.playerdata.PacketSyncProgressToServer;
import thaumcraft.common.lib.network.playerdata.ServerResearchRequestHandler;

/**
 * DEFAULT OFF fresh dedicated loopback TCP bootstrap scenario. Only tangible material/arena
 * fixtures are supplied. Crystal and book acquisition use actual world collision pickup;
 * the wake is an explicitly controlled native stopSleepInBed hook. All crafting, dust uses,
 * book GUI and research requests originate in the separate actual LocalPlayer client.
 * This cohort does not claim resource gathering, ordinary walking/sleeping or a playthrough.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class TcpBootstrapAudit {
    public static final BlockPos CRAFT = new BlockPos(2, 65, 0);
    public static final BlockPos BOOKSHELF = new BlockPos(2, 65, 2);
    public static final BlockPos WORKBENCH = new BlockPos(2, 65, 4);
    public static final List<Block> SCAN_BLOCKS = List.of(Blocks.STONE, Blocks.DIRT, Blocks.GRASS_BLOCK,
        Blocks.SAND, Blocks.GRAVEL, Blocks.OAK_LOG, Blocks.POPPY);
    public static final List<BlockPos> SCANS = java.util.stream.IntStream.range(0, SCAN_BLOCKS.size())
        .mapToObj(index -> new BlockPos(4, 65, index)).toList();
    private static Run active;

    private TcpBootstrapAudit() {}

    /** Called only after the actual PLAY contextual enqueueWork/tryHandle production receipt. */
    public static void observeRequest(ServerPlayer player, PacketSyncProgressToServer packet, ServerResearchRequestHandler.Result result) {
        Run run = active;
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || run == null || run.finished || player.getServer() != run.server) return;
        try {
            run.owner(player);
            require(packet != null && result != null && result.applied() && "FIRSTSTEPS".equals(packet.researchKey()),
                "Bootstrap expects only actual applied FIRSTSTEPS contextual requests");
            int index = run.contextualRequests.size();
            require(index < 3 && packet.expectedStage() == (index == 0 ? -1 : index)
                && run.phase.equals(index == 0 ? "firststeps1" : index == 1 ? "firststeps2" : "firststeps-complete"),
                "Actual contextual request count/stage/phase differs from native bootstrap sequence");
            int after = ThaumcraftCapabilities.getKnowledge(player).getResearchStage("FIRSTSTEPS");
            require(after == (index == 0 ? 1 : index == 1 ? 2 : 4), "Applied contextual receipt has unexpected final FIRSTSTEPS stage");
            JsonObject receipt = new JsonObject();
            receipt.addProperty("researchKey", packet.researchKey()); receipt.addProperty("expectedStage", packet.expectedStage());
            receipt.addProperty("catalogDigest", packet.catalogDigest()); receipt.addProperty("result", result.status().name());
            receipt.addProperty("resultingStage", after); receipt.addProperty("mainThread", run.server.isSameThread());
            receipt.addProperty("actualTCP", !player.connection.getConnection().isMemoryConnection());
            receipt.addProperty("ownerUuid", player.getUUID().toString()); receipt.addProperty("serverTick", run.server.getTickCount());
            if (index == 2) {
                int remaining = run.basicRaw();
                require(run.observationPaymentEvents == 1 && run.observationPaymentBefore >= 16
                    && remaining == run.observationPaymentBefore - 16 && ThaumcraftCapabilities.getKnowledge(player).isResearchComplete("FIRSTSTEPS"),
                    "Full native FIRSTSTEPS completion must pay exactly16 raw BASICS observations");
                receipt.addProperty("basicsRawBefore", run.observationPaymentBefore);
                receipt.addProperty("basicsRawAfter", remaining);
                receipt.addProperty("rawObservationsPaid", 16);
            }
            run.contextualRequests.add(receipt);
            run.event("actual-contextual-research-receipt", receipt.deepCopy());
        } catch (Exception failure) { run.finish(false, failure.toString()); }
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        try { active = new Run(event.getServer()); }
        catch (Exception failure) {
            Thaumcraft.log.error("Private TCP bootstrap refused startup", failure);
            event.getServer().halt(false);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        Run run = active;
        if (run == null || run.finished || event.getServer() != run.server) return;
        try { run.step(); }
        catch (Exception failure) { run.finish(false, failure.toString()); }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        Run run = active;
        if (run != null && !run.finished && event.getServer() == run.server) {
            run.finish(false, "Server stopped before actual TCP bootstrap completed");
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void levelEnd(LevelTickEvent.Post event) {
        Run run = active;
        if (run != null && !run.finished && event.getLevel() == run.server.overworld()) {
            run.overworldEndEvents++;
            // LOWEST observes normal AuraEvents after its 20-tick regeneration/exchange.
            // This private arena contains no other aura-consuming mechanism.
            run.latestNaturalAura = AuraHandler.getVis(run.server.overworld(), WORKBENCH);
            run.latestNaturalAuraTick = run.server.getTickCount();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        Run run = active;
        if (run == null || run.finished || !(event.getEntity() instanceof ServerPlayer player) || player.getServer() != run.server) return;
        try { run.crafted(player, event.getCrafting(), event.getInventory()); }
        catch (Exception failure) { run.finish(false, failure.toString()); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void research(ResearchEvent.Research event) {
        Run run = active;
        if (run == null || run.finished || !(event.getPlayer() instanceof ServerPlayer player) || player.getServer() != run.server) return;
        try {
            run.owner(player);
            JsonObject details = new JsonObject();
            details.addProperty("key", event.getResearchKey());
            details.addProperty("preMutationStage", ThaumcraftCapabilities.getKnowledge(player).getResearchStage(event.getResearchKey()));
            details.addProperty("cancelled", event.isCanceled());
            run.event("native-research-pre-event", details);
            if ("FIRSTSTEPS".equals(event.getResearchKey()) && !event.isCanceled()) run.firstStepsEvents++;
            if (run.phase.equals("scans") && run.scanIndex < SCANS.size()
                && run.scanKey(run.scanIndex).equals(event.getResearchKey())) {
                require(!event.isCanceled() && run.thaumometerUseEvents == run.scanIndex + 1
                    && SCANS.get(run.scanIndex).equals(ScanTargeting.scanTarget(player))
                    && !ThaumcraftCapabilities.getKnowledge(player).isResearchKnown(event.getResearchKey()),
                    "Native generic scan marker requires one real targeted item-use event");
                run.scanResearchEvents++;
            }
        } catch (Exception failure) { run.finish(false, failure.toString()); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void itemUse(PlayerInteractEvent.RightClickItem event) {
        Run run = active;
        if (run == null || run.finished || !(event.getEntity() instanceof ServerPlayer player)
            || player.getServer() != run.server || !event.getItemStack().is(ItemsTC.thaumometer)) return;
        try {
            run.owner(player);
            require(run.phase.equals("scans") && run.scanIndex < SCANS.size() && !event.isCanceled()
                && event.getHand() == InteractionHand.MAIN_HAND && event.getItemStack().getCount() == 1
                && run.thaumometerUseEvents == run.scanIndex && run.scanResearchEvents == run.scanIndex
                && SCANS.get(run.scanIndex).equals(ScanTargeting.scanTarget(player)),
                "Thaumometer scanning requires exact ordered real TCP item-use/target geometry");
            run.thaumometerUseEvents++;
            run.scanRawBefore = run.basicRaw();
            run.scanBasicAwards = 0;
            JsonObject details = new JsonObject();
            details.addProperty("scanIndex", run.scanIndex); details.addProperty("researchKey", run.scanKey(run.scanIndex));
            details.addProperty("basicsRawBefore", run.scanRawBefore);
            details.add("target", Run.vector(SCANS.get(run.scanIndex)));
            details.addProperty("actualTCP", !player.connection.getConnection().isMemoryConnection());
            run.event("native-thaumometer-right-click-item", details);
        } catch (Exception failure) { run.finish(false, failure.toString()); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void knowledge(ResearchEvent.Knowledge event) {
        Run run = active;
        if (run == null || run.finished || !(event.getPlayer() instanceof ServerPlayer player) || player.getServer() != run.server) return;
        try {
            run.owner(player);
            JsonObject details = new JsonObject();
            details.addProperty("type", event.getType().name());
            details.addProperty("category", event.getCategory() == null ? "" : event.getCategory().key);
            details.addProperty("amount", event.getAmount()); details.addProperty("cancelled", event.isCanceled());
            if (event.getType() == EnumKnowledgeType.OBSERVATION && event.getCategory() == ResearchCategories.getResearchCategory("BASICS")) {
                details.addProperty("preMutationRaw", run.basicRaw());
                require(!event.isCanceled(), "Native BASICS award/payment event was cancelled");
                if (event.getAmount() >= 0) {
                    require(run.phase.equals("scans") && run.thaumometerUseEvents == run.scanIndex + 1,
                        "Native observation awards must occur inside the actual targeted item-use");
                    run.scanBasicAwards += event.getAmount();
                    run.basicAwardEvents++;
                } else {
                    require(run.phase.equals("firststeps-complete") && run.scanIndex == SCANS.size() && event.getAmount() == -16
                        && ++run.observationPaymentEvents == 1 && run.basicRaw() >= 16,
                        "Only actual FIRSTSTEPS source requirement may debit exactly16 raw observations");
                    run.observationPaymentBefore = run.basicRaw();
                }
            }
            run.event("native-knowledge-pre-event", details);
        } catch (Exception failure) { run.finish(false, failure.toString()); }
    }

    private static final class Run {
        final MinecraftServer server;
        final Path directory, world;
        final JsonObject marker, report;
        final JsonArray checks = new JsonArray(), nativeEvents = new JsonArray(), contextualRequests = new JsonArray(), scanReceipts = new JsonArray();
        final List<ItemEntity> crystals = new ArrayList<>();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1200);
        long phaseDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(600);
        final boolean previousNoSleep;
        ServerPlayer player;
        net.minecraft.network.Connection connection;
        EntitySpecialItem droppedBook;
        CompoundTag initialKnowledge, initialWarp;
        String phase = "login";
        int actionId, nativeRecipeCraftCount, bookCraftEvents, workbenchCraftEvents, firstStepsEvents, lastObservedStage = -1;
        int crystalPickupCount, eventSequence, bookshelfConsumedTick = -1, workbenchConsumedTick = -1;
        int overworldEndEvents, bookshelfConsumedEndEvents = -1, workbenchConsumedEndEvents = -1;
        int thaumometerCraftEvents, thaumometerUseEvents, scanResearchEvents, scanIndex, scanRawBefore, scanBasicAwards;
        int basicAwardEvents, observationPaymentEvents, observationPaymentBefore = -1, scansCompleteRaw = -1;
        int nextScanEarliestTick, craftVisBefore = -1, craftVisAfter = -1;
        float craftAuraBefore = -1, craftAuraAfter = -1, latestNaturalAura = -1;
        int latestNaturalAuraTick = -1;
        JsonObject persistenceBaseline, quietCandidate;
        int reconnectQuietSince = -1, ownerEpoch = 1;
        boolean reconnectAllowed, disconnectedObserved, reconnectProven;
        boolean controlledWake, finished, terminalAttempted;

        Run(MinecraftServer server) throws Exception {
            this.server = server;
            require(server.isSameThread(), "Bootstrap startup must run on owning server MAIN");
            directory = directory(Path.of(System.getProperty("user.dir")), "server");
            marker = validateDirectory(directory, "server");
            require(!marker.has("auditPhase") || "generate".equals(string(marker, "auditPhase")),
                "Fresh native progression controller only accepts generation cohort");
            require(server.isDedicatedServer() && "127.0.0.1".equals(server.getLocalIp()) && !server.usesAuthentication()
                && server.getPort() == marker.get("port").getAsInt() && !server.isHardcore(),
                "Exact offline loopback dedicated server required");
            require(server.getPlayerCount() == 0, "No preexisting server sessions allowed");
            world = safePath(directory, server.getWorldPath(LevelResource.ROOT).toRealPath());
            require(world.getParent().equals(directory) && world.getFileName().toString().equals(WORLD),
                "Only newly prepared bootstrap world allowed");
            Path players = safePath(directory, world.resolve("playerdata"));
            if (Files.isDirectory(players, LinkOption.NOFOLLOW_LINKS)) try (var files = Files.list(players)) {
                require(files.noneMatch(path -> path.getFileName().toString().endsWith(".dat")), "Existing player data forbidden");
            }
            report = identity(marker);
            report.addProperty("startedUtc", Instant.now().toString());
            report.addProperty("dedicatedServer", true);
            report.addProperty("actualTCP", true);
            report.addProperty("noKnowledgeResearchFixtures", true);
            report.addProperty("survivalPlaythroughProven", false);
            report.addProperty("resourceGatheringProven", false);
            report.addProperty("ordinarySleepOrWalkingProven", false);
            report.addProperty("reloadReconnectProven", false);
            report.addProperty("controlledArenaAndMaterials", true);
            report.add("checks", checks);
            report.add("nativeEvents", nativeEvents);
            report.add("actualContextualRequests", contextualRequests);
            report.add("nativeScanReceipts", scanReceipts);
            report.addProperty("auraFixtures", false);
            previousNoSleep = ModConfig.CONFIG_MISC.noSleep;
            ModConfig.CONFIG_MISC.noSleep = false;
            report.addProperty("noSleepDisabledForControlledWake", true);
            prepareArena();
            publish("running");
            event("private-server-ready", new JsonObject());
        }

        void prepareArena() {
            var level = server.overworld();
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
            for (int x = -3; x <= 6; x++) for (int z = -3; z <= 8; z++) {
                level.setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), 3);
                for (int y = 65; y <= 69; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            }
            require(level.setBlock(CRAFT, Blocks.CRAFTING_TABLE.defaultBlockState(), 3)
                && level.setBlock(BOOKSHELF, Blocks.BOOKSHELF.defaultBlockState(), 3)
                && level.setBlock(WORKBENCH, Blocks.CRAFTING_TABLE.defaultBlockState(), 3), "Arena fixture placements failed");
            for (int index = 0; index < SCANS.size(); index++) {
                BlockPos position = SCANS.get(index);
                if (SCAN_BLOCKS.get(index) == Blocks.POPPY) level.setBlock(position.below(), Blocks.DIRT.defaultBlockState(), 3);
                require(level.setBlock(position, SCAN_BLOCKS.get(index).defaultBlockState(), 3), "Physical native scan fixture placement failed");
            }
        }

        void owner(ServerPlayer actual) {
            require(server.isSameThread() && actual.getServer() == server && actual.serverLevel() == server.overworld(),
                "Real owning overworld server MAIN required");
            require(USERNAME.equals(actual.getGameProfile().getName()) && OWNER_UUID.equals(actual.getUUID()),
                "Only exact fresh offline bootstrap owner allowed");
            require(player == null || player == actual, "Replacing/respawning audit owner is forbidden in bootstrap cohort");
            var actualConnection = actual.connection.getConnection();
            require(actualConnection.isConnected() && !actualConnection.isMemoryConnection()
                && actualConnection.getRemoteAddress() instanceof InetSocketAddress address && address.getAddress().isLoopbackAddress(),
                "Actual connected loopback TCP required");
            require(connection == null || connection == actualConnection, "Bootstrap connection replacement forbidden");
            require(!actual.getAbilities().instabuild && actual.gameMode.getGameModeForPlayer() == GameType.SURVIVAL,
                "Actual dust consumption requires survival mode");
        }

        void prepareOwner(ServerPlayer actual) throws Exception {
            require(OWNER_UUID.equals(actual.getUUID()) && USERNAME.equals(actual.getGameProfile().getName()), "Unexpected private profile");
            actual.setGameMode(GameType.SURVIVAL);
            owner(actual);
            player = actual;
            connection = actual.connection.getConnection();
            var knowledge = ThaumcraftCapabilities.getKnowledge(player);
            initialKnowledge = knowledge.serializeNBT().copy();
            initialWarp = ThaumcraftCapabilities.getWarp(player).serializeNBT().copy();
            require(!knowledge.isResearchKnown("!gotcrystals") && !knowledge.isResearchKnown("!gotdream")
                && !knowledge.isResearchKnown("!gotthaumonomicon") && knowledge.getResearchStage("FIRSTSTEPS") == -1,
                "Fresh owner must not carry bootstrap research");
            require(player.getInventory().isEmpty() && player.containerMenu == player.inventoryMenu && player.totalExperience == 0,
                "Fresh owner requires empty actual inventory/menu and zero XP");
            player.teleportTo(server.overworld(), .5, 65, 1.5, Set.of(), 0, 0);
            player.getInventory().setItem(0, new ItemStack(Items.BOWL));
            player.getInventory().setItem(1, new ItemStack(Items.FLINT));
            player.getInventory().setItem(2, new ItemStack(Items.REDSTONE, 2));
            for (Aspect aspect : List.of(Aspect.AIR, Aspect.FIRE, Aspect.WATER)) {
                ItemStack stack = ThaumcraftApiHelper.makeCrystal(aspect).copyWithCount(2);
                ItemEntity entity = new ItemEntity(server.overworld(), player.getX(), player.getY(), player.getZ(), stack);
                entity.setNoPickUpDelay();
                entity.setDeltaMovement(Vec3.ZERO);
                require(server.overworld().addFreshEntity(entity), "Real crystal entity insertion failed");
                crystals.add(entity);
            }
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            JsonObject details = new JsonObject();
            details.addProperty("crystalEntities", 3);
            details.addProperty("crystalsPerDistinctAspect", 2);
            details.addProperty("bowl", 1); details.addProperty("flint", 1); details.addProperty("redstone", 2);
            details.addProperty("actualPickupCollisionRequired", true);
            details.addProperty("researchWrites", 0);
            event("tangible-material-fixture", details);
            check("fresh-native-owner", "Actual fresh offline TCP owner; no initial acquisition markers or FIRSTSTEPS stage");
            transition("materials");
        }

        void crafted(ServerPlayer actual, ItemStack output, Container sourceMatrix) throws Exception {
            owner(actual);
            JsonObject details = new JsonObject();
            details.addProperty("item", BuiltInRegistries.ITEM.getKey(output.getItem()).toString());
            details.addProperty("count", output.getCount());
            details.addProperty("actualMenuType", actual.containerMenu.getClass().getName());
            details.addProperty("firststepsStage", ThaumcraftCapabilities.getKnowledge(actual).getResearchStage("FIRSTSTEPS"));
            if (output.is(ItemsTC.salisMundus)) {
                require(phase.equals("craft-dust") && actual.containerMenu instanceof CraftingMenu && output.getCount() == 1,
                    "Dust must arise from actual native table result take in craft phase");
                require(++nativeRecipeCraftCount <= 2, "Unexpected extra native dust crafting take");
            } else if (output.is(ItemsTC.thaumonomicon)) {
                require(phase.equals("bookshelf") && ++bookCraftEvents == 1, "Book conversion must occur exactly once");
                require(actual.serverLevel().getBlockState(BOOKSHELF).is(Blocks.BOOKSHELF), "Source book crafting event must precede delayed conversion");
                bookshelfConsumedTick = server.getTickCount();
                bookshelfConsumedEndEvents = overworldEndEvents;
            } else if (output.is(BlocksTC.arcaneWorkbench.asItem())) {
                require(phase.equals("workbench") && ++workbenchCraftEvents == 1, "Workbench conversion must occur exactly once");
                require(ThaumcraftCapabilities.getKnowledge(actual).getResearchStage("FIRSTSTEPS") == 1
                    && actual.serverLevel().getBlockState(WORKBENCH).is(Blocks.CRAFTING_TABLE),
                    "Native workbench craft requires prior stage1 and still-original table");
                workbenchConsumedTick = server.getTickCount();
                workbenchConsumedEndEvents = overworldEndEvents;
            } else if (output.is(ItemsTC.thaumometer)) {
                require(phase.equals("thaumometer") && ++thaumometerCraftEvents == 1 && output.getCount() == 1
                    && actual.containerMenu instanceof ContainerArcaneWorkbench && sourceMatrix.getContainerSize() == 15
                    && ThaumcraftCapabilities.getKnowledge(actual).getResearchStage("FIRSTSTEPS") == 2,
                    "Actual paid thaumometer take must originate from native arcane menu at FIRSTSTEPS@2");
                var menu = (ContainerArcaneWorkbench) actual.containerMenu;
                require(menu.table() == server.overworld().getBlockEntity(WORKBENCH) && menu.table() instanceof TileArcaneWorkbench,
                    "Native thaumometer craft must own the actual converted table");
                for (int index = 0; index < 9; index++) {
                    Item expected = index == 4 ? Items.GLASS_PANE : index == 1 || index == 3 || index == 5 || index == 7 ? Items.GOLD_INGOT : Items.AIR;
                    ItemStack original = sourceMatrix.getItem(index);
                    require(expected == Items.AIR ? original.isEmpty() : original.is(expected) && original.getCount() == 1,
                        "Native thaumometer source craft matrix differs from four gold/glass cross");
                }
                for (int aspect = 0; aspect < 6; aspect++) require(sourceMatrix.getItem(9 + aspect).getCount() == 1
                    && ArcaneWorkbenchCosts.crystal(sourceMatrix.getItem(9 + aspect), aspect),
                    "Native thaumometer source requires one exact component-bearing crystal of every primal");
                require(menu.table().isEmpty(), "Native thaumometer commit must consume all fifteen actual table inputs");
                // clicked()/quickMoveStack refresh immediately before takeCraft. The cached menu
                // availability is still its precise integer pre-payment value during this callback;
                // spendVis and the native event execute synchronously on server MAIN. Natural aura
                // regeneration runs on separate level ticks, never between this refresh and receipt.
                craftVisBefore = menu.availableVis();
                craftAuraBefore = latestNaturalAura;
                craftAuraAfter = AuraHandler.getVis(server.overworld(), WORKBENCH);
                craftVisAfter = (int) craftAuraAfter;
                require(craftVisBefore >= 20 && craftVisBefore - craftVisAfter == 20
                    && latestNaturalAuraTick >= 0 && server.getTickCount() - latestNaturalAuraTick <= 1
                    && Math.abs((craftAuraBefore - craftAuraAfter) - 20F) < 0.0001F
                    && ArcaneWorkbenchCosts.discount(player) == 0
                    && !server.overworld().getBlockState(WORKBENCH.above()).is(BlocksTC.arcaneWorkbenchCharger),
                    "Native thaumometer must pay exactly20 natural local vis without a charger");
                details.addProperty("visBefore", craftVisBefore); details.addProperty("visAfter", craftVisAfter);
                details.addProperty("auraFloatBefore", craftAuraBefore); details.addProperty("auraFloatAfter", craftAuraAfter);
                details.addProperty("naturalAuraObservedServerTick", latestNaturalAuraTick); details.addProperty("visPaid", 20);
                details.addProperty("sourceMatrixSlots", sourceMatrix.getContainerSize());
                details.addProperty("allInputsConsumedBeforeCallback", true);
            } else throw new IllegalStateException("Unexpected crafted output in private bootstrap: " + output);
            event("native-item-crafted-event", details);
        }

        void step() throws Exception {
            require(System.nanoTime() <= deadline && System.nanoTime() <= phaseDeadline, "Private TCP bootstrap timeout in " + phase);
            for (ServerPlayer current : server.getPlayerList().getPlayers()) {
                require(USERNAME.equals(current.getGameProfile().getName()) && OWNER_UUID.equals(current.getUUID()), "Unexpected player entered private server");
            }
            if (player == null) {
                ServerPlayer actual = server.getPlayerList().getPlayerByName(USERNAME);
                if (actual != null) prepareOwner(actual);
                return;
            }
            JsonObject peer = readPeer(directory, marker, "client", false);
            if (peer != null) require(!"failed".equals(string(peer, "status")), "Actual TCP client failed: " + string(peer, "failure"));
            if (phase.equals("reconnect")) { reconnect(peer); return; }
            owner(player);
            require(!ModConfig.CONFIG_MISC.noSleep, "Controlled bootstrap must retain noSleep=false");
            var knowledge = ThaumcraftCapabilities.getKnowledge(player);
            int stage = knowledge.getResearchStage("FIRSTSTEPS");
            if (stage != lastObservedStage) {
                require(stage == 1 && phase.equals("firststeps1") || stage == 2 && phase.equals("firststeps2")
                    || stage == 4 && phase.equals("firststeps-complete"),
                    "Unexpected native FIRSTSTEPS transition at " + phase + ": " + lastObservedStage + " to " + stage);
                JsonObject details = new JsonObject();
                details.addProperty("before", lastObservedStage); details.addProperty("after", stage);
                details.addProperty("uncancelledPreEvents", firstStepsEvents);
                require(firstStepsEvents == (stage == 1 ? 1 : stage == 2 ? 2 : 3)
                    && contextualRequests.size() == (stage == 1 ? 1 : stage == 2 ? 2 : 3),
                    "Stage change lacks actual native Research event/contextual TCP receipt");
                event("native-firststeps-commit-observed", details);
                lastObservedStage = stage;
            }
            switch (phase) {
                case "materials" -> {
                    require(!knowledge.isResearchKnown("!gotdream") && !knowledge.isResearchKnown("!gotthaumonomicon") && stage == -1,
                        "Crystal pickup alone must not produce dream/book/progression");
                    if (inventoryCount(ItemsTC.crystalEssence) == 6 && crystals.stream().allMatch(ItemEntity::isRemoved)) {
                        require(knowledge.isResearchKnown("!gotcrystals") && knowledge.getResearchStage("!gotcrystals") == 0,
                            "Actual crystal collision must produce raw known0 acquisition marker");
                        crystalPickupCount = 6;
                        check("actual-crystal-collision-pickup", "Three native ItemEntity stacks removed after real tick collisions; six component-bearing crystals inserted");
                        player.stopSleepInBed(true, true);
                        controlledWake = true;
                        require(knowledge.isResearchKnown("!gotdream") && knowledge.getResearchStage("!gotdream") == 0
                            && player.getInventory().items.stream().anyMatch(stack -> stack.is(Items.WRITTEN_BOOK) && stack.has(DataComponents.WRITTEN_BOOK_CONTENT)),
                            "Native controlled wake must produce the actual source dream journal");
                        check("controlled-native-wake", "Actual stopSleepInBed wake event with noSleep=false; source dream journal and raw marker, no direct grant");
                        transition("craft-dust");
                    }
                }
                case "craft-dust" -> {
                    require(stage == -1 && !knowledge.isResearchKnown("!gotthaumonomicon"), "Ordinary crafting must not bypass acquisition");
                    if (nativeRecipeCraftCount == 2 && inventoryCount(ItemsTC.salisMundus) == 2) {
                        require(totalCount(Items.BOWL) == 1 && totalCount(Items.FLINT) == 1 && totalCount(Items.REDSTONE) == 0
                            && totalCount(ItemsTC.crystalEssence) == 0, "Two real dust takes must consume redstone/crystals and return bowl/flint exactly");
                        check("native-dust-result-takes-and-remainders", "Two real CraftingMenu result takes; two dust, all six crystals/two redstone consumed, bowl/flint conserved");
                        transition("bookshelf");
                    }
                }
                case "bookshelf" -> {
                    require(stage == -1 && !knowledge.isResearchKnown("!gotthaumonomicon"), "Shelf conversion must not directly grant book marker");
                    if (bookCraftEvents == 1 && server.overworld().getBlockState(BOOKSHELF).isAir()) {
                        require(overworldEndEvents - bookshelfConsumedEndEvents >= 51 && inventoryCount(ItemsTC.salisMundus) == 1,
                            "Source shelf conversion must wait 51 actual level END events and consume one actual dust");
                        var drops = server.overworld().getEntitiesOfClass(EntitySpecialItem.class, new AABB(BOOKSHELF).inflate(2),
                            entity -> entity.getItem().is(ItemsTC.thaumonomicon));
                        require(drops.size() == 1, "Exactly one actual source special book entity required");
                        droppedBook = drops.getFirst();
                        check("native-delayed-bookshelf-conversion", "Actual client dust-use craft receipt, consumed one dust, 51 actual level END events, real special book entity");
                        // Controlled arena positioning only. Normal subsequent world tick collision
                        // performs pickup; no playerTouch, inventory book insertion or marker call.
                        player.teleportTo(server.overworld(), droppedBook.getX(), 65, droppedBook.getZ(), Set.of(), 0, 0);
                        event("controlled-book-collision-positioning", new JsonObject());
                        transition("book-pickup");
                    }
                }
                case "book-pickup" -> {
                    require(stage == -1, "Book pickup must precede native FIRSTSTEPS request");
                    if (droppedBook.isRemoved() && inventoryCount(ItemsTC.thaumonomicon) == 1) {
                        require(knowledge.isResearchKnown("!gotthaumonomicon") && knowledge.getResearchStage("!gotthaumonomicon") == 0,
                            "Actual book collision must produce raw known0 pickup marker");
                        check("native-special-book-collision-pickup", "Special drop removed and one real normal Thaumonomicon inserted by world tick collision");
                        transition("firststeps1");
                    }
                }
                case "firststeps1" -> {
                    if (stage == 1) {
                        require(bookCraftEvents == 1 && server.overworld().getBlockState(WORKBENCH).is(Blocks.CRAFTING_TABLE),
                            "First stage native request must not bypass workbench requirement");
                        check("native-firststeps-start", "Real separate client research request starts unknown -1 at stage1 after actual acquisition");
                        transition("workbench");
                    }
                }
                case "workbench" -> {
                    require(stage == 1, "Workbench dust conversion may not change FIRSTSTEPS directly");
                    if (workbenchCraftEvents == 1 && server.overworld().getBlockState(WORKBENCH).is(BlocksTC.arcaneWorkbench)) {
                        require(overworldEndEvents - workbenchConsumedEndEvents >= 51 && inventoryCount(ItemsTC.salisMundus) == 0,
                            "Source workbench conversion must wait51 actual level END events and consume second dust");
                        check("native-paid-workbench-conversion", "Real client dust use at stage1; second dust consumed; table became registered workbench after51 actual level END events");
                        transition("firststeps2");
                    }
                }
                case "firststeps2" -> {
                    if (stage == 2) {
                        require(workbenchCraftEvents == 1 && !knowledge.isResearchComplete("FIRSTSTEPS"),
                            "Actual source required craft must advance only to stage2");
                        require(ThaumcraftCapabilities.getWarp(player).serializeNBT().equals(initialWarp), "Bootstrap changed warp unexpectedly");
                        check("native-firststeps-craft-receipt-accepted", "Real separate client request checks actual source workbench crafting receipt and commits stage1 to2");
                        prepareThaumometerMaterials();
                        transition("thaumometer");
                    }
                }
                case "thaumometer" -> {
                    require(stage == 2, "Arcane thaumometer crafting must retain actual FIRSTSTEPS@2");
                    if (thaumometerCraftEvents == 1 && inventoryCount(ItemsTC.thaumometer) == 1) {
                        require(inventoryCount(Items.GOLD_INGOT) == 0 && inventoryCount(Items.GLASS_PANE) == 0
                            && inventoryCount(ItemsTC.crystalEssence) == 0 && player.containerMenu.getCarried().isEmpty(),
                            "Paid native arcane output must enter actual inventory and consume tangible materials");
                        check("native-paid-thaumometer-arcane-craft", "Native arcane menu source result take consumes four gold, one pane, six exact primal crystals and20 natural local vis");
                        positionForScan();
                        transition("scans");
                    }
                }
                case "scans" -> {
                    require(stage == 2 && thaumometerCraftEvents == 1 && inventoryCount(ItemsTC.thaumometer) == 1,
                        "Real scans must follow one paid thaumometer with FIRSTSTEPS stillstage2");
                    if (knowledge.isResearchKnown(scanKey(scanIndex)) && server.getTickCount() >= nextScanEarliestTick) {
                        require(thaumometerUseEvents == scanIndex + 1 && scanResearchEvents == scanIndex + 1
                            && knowledge.getResearchStage(scanKey(scanIndex)) == 0 && basicRaw() == scanRawBefore + scanBasicAwards,
                            "Generic scan marker and exact uncancelled natural BASICS awards require one actual item use");
                        JsonObject receipt = new JsonObject();
                        receipt.addProperty("scanIndex", scanIndex); receipt.addProperty("block", BuiltInRegistries.BLOCK.getKey(SCAN_BLOCKS.get(scanIndex)).toString());
                        receipt.addProperty("researchKey", scanKey(scanIndex)); receipt.add("position", vector(SCANS.get(scanIndex)));
                        receipt.addProperty("basicsRawBefore", scanRawBefore); receipt.addProperty("basicsRawAfter", basicRaw());
                        receipt.addProperty("basicsRawAwarded", scanBasicAwards); receipt.addProperty("actualItemUseCount", thaumometerUseEvents);
                        receipt.addProperty("serverTick", server.getTickCount()); receipt.addProperty("actualTCP", true);
                        scanReceipts.add(receipt); event("native-block-scan-committed", receipt.deepCopy());
                        scanIndex++;
                        if (scanIndex < SCANS.size()) {
                            positionForScan(); actionId++; publish("running");
                        } else {
                            scansCompleteRaw = basicRaw();
                            require(scansCompleteRaw >= 16 && scanReceipts.size() == 7 && thaumometerUseEvents == 7 && scanResearchEvents == 7,
                                "Seven actual native block scans must earn at least16 raw BASICS observations");
                            check("native-seven-targeted-block-scans", "Seven separate native TCP item uses, exact server ray targets, new source generic markers and actual BASICS awards; no knowledge grants");
                            transition("firststeps-complete");
                        }
                    }
                }
                case "firststeps-complete" -> {
                    if (stage == 4) {
                        require(knowledge.isResearchComplete("FIRSTSTEPS") && observationPaymentEvents == 1
                            && observationPaymentBefore == scansCompleteRaw && basicRaw() == scansCompleteRaw - 16
                            && ThaumcraftCapabilities.getWarp(player).serializeNBT().equals(initialWarp),
                            "Actual source completion requires exact16 natural observations debit and unchanged warp");
                        check("native-paid-firststeps-completion", "Actual contextual TCP request validates crafted thaumometer, pays16 raw BASICS observations, performs source last-page skip from2 to4 and completes FIRSTSTEPS");
                        transition("reconnect");
                    }
                }
                case "finish" -> {
                    JsonObject client = readPeer(directory, marker, "client", true);
                    if (client != null) {
                        require(client.has("passed") && client.get("passed").getAsBoolean() && "completed".equals(string(client, "status"))
                            && client.has("actualTCP") && client.get("actualTCP").getAsBoolean()
                            && client.get("firststepsStage").getAsInt() == 4 && OWNER_UUID.toString().equals(string(client, "ownerUuid"))
                            && client.has("reconnectProven") && client.get("reconnectProven").getAsBoolean()
                            && client.has("connectionEpoch") && client.get("connectionEpoch").getAsInt() == 2 && reconnectProven,
                            "Actual client must seal successful native bootstrap report while still connected");
                        report.add("clientAcceptance", client.deepCopy());
                        require(sameBaseline(baseline(), persistenceBaseline), "Final reconnected owner changed after captured persistence baseline");
                        persistenceBaseline = baseline();
                        report.add("persistenceBaseline", persistenceBaseline.deepCopy());
                        check("actual-client-native-gui-and-payload-acceptance", "Matching separate client sealed successful paid crafting/scanning/fullresearch and second TCP connection report");
                        finish(true, null);
                    }
                }
                default -> throw new IllegalStateException("Unknown private bootstrap phase");
            }
            if (!finished && server.getTickCount() % 5 == 0) publish("running");
        }

        void prepareThaumometerMaterials() throws Exception {
            require(player.containerMenu == player.inventoryMenu && inventoryCount(ItemsTC.crystalEssence) == 0
                && inventoryCount(Items.GOLD_INGOT) == 0 && inventoryCount(Items.GLASS_PANE) == 0
                && inventoryCount(ItemsTC.thaumometer) == 0 && basicRaw() == 0,
                "Arcane material fixture requires prior ordinary bootstrap with no future tool or observations");
            require(AuraHandler.getAuraChunk(server.overworld(), WORKBENCH.getX() >> 4, WORKBENCH.getZ() >> 4) != null
                && AuraHandler.getVis(server.overworld(), WORKBENCH) >= 20,
                "Fresh naturally generated workbench aura must already provide native20vis cost");
            require(player.getInventory().add(new ItemStack(Items.GOLD_INGOT, 4))
                && player.getInventory().add(new ItemStack(Items.GLASS_PANE)), "Tangible gold/pane fixture must fit actual inventory");
            for (Aspect aspect : List.of(Aspect.AIR, Aspect.FIRE, Aspect.WATER, Aspect.EARTH, Aspect.ORDER, Aspect.ENTROPY)) {
                require(player.getInventory().add(ThaumcraftApiHelper.makeCrystal(aspect).copyWithCount(1)),
                    "Tangible primal component crystal must fit actual inventory");
            }
            player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges();
            JsonObject details = new JsonObject();
            details.addProperty("gold", 4); details.addProperty("glassPane", 1); details.addProperty("eachPrimalCrystal", 1);
            details.addProperty("naturalAuraVis", AuraHandler.getVis(server.overworld(), WORKBENCH));
            details.addProperty("naturalAuraBase", AuraHandler.getAuraBase(server.overworld(), WORKBENCH));
            details.addProperty("auraWrites", 0); details.addProperty("knowledgeWrites", 0); details.addProperty("researchWrites", 0);
            event("tangible-arcane-material-fixture", details);
        }

        int basicRaw() {
            return ThaumcraftCapabilities.getKnowledge(player).getKnowledgeRaw(EnumKnowledgeType.OBSERVATION,
                ResearchCategories.getResearchCategory("BASICS"));
        }

        String scanKey(int index) { return LegacyScanIdentity.itemResearchKey(new ItemStack(SCAN_BLOCKS.get(index))); }

        void positionForScan() throws Exception {
            BlockPos target = SCANS.get(scanIndex);
            require(server.overworld().getBlockState(target).is(SCAN_BLOCKS.get(scanIndex)), "Exact physical scan block disappeared");
            require(server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(5)).isEmpty(),
                "Nearby dropped item entities may not interfere with exact native block ray targeting");
            player.teleportTo(server.overworld(), 1.5, 65, target.getZ() + .5, Set.of(), -90, 0);
            nextScanEarliestTick = server.getTickCount() + 20;
            JsonObject details = new JsonObject(); details.addProperty("scanIndex", scanIndex);
            details.add("target", vector(target)); details.addProperty("researchKey", scanKey(scanIndex));
            event("controlled-native-scan-positioning", details);
        }

        JsonObject baseline() {
            JsonObject value = new JsonObject();
            var knowledge = ThaumcraftCapabilities.getKnowledge(player);
            value.addProperty("ownerUuid", player.getUUID().toString());
            value.addProperty("knowledgeNbt", knowledge.serializeNBT().toString());
            value.addProperty("warpNbt", ThaumcraftCapabilities.getWarp(player).serializeNBT().toString());
            value.addProperty("inventoryNbt", player.getInventory().save(new ListTag()).toString());
            value.addProperty("selectedSlot", player.getInventory().selected);
            value.addProperty("totalExperience", player.totalExperience);
            value.addProperty("experienceLevel", player.experienceLevel);
            value.addProperty("experienceProgress", player.experienceProgress);
            value.addProperty("firststepsStage", knowledge.getResearchStage("FIRSTSTEPS"));
            value.addProperty("basicsRaw", basicRaw());
            value.addProperty("workbenchBlock", BuiltInRegistries.BLOCK.getKey(server.overworld().getBlockState(WORKBENCH).getBlock()).toString());
            value.addProperty("bookshelfBlock", BuiltInRegistries.BLOCK.getKey(server.overworld().getBlockState(BOOKSHELF).getBlock()).toString());
            value.addProperty("bookshelfAir", server.overworld().getBlockState(BOOKSHELF).isAir());
            var tile = server.overworld().getBlockEntity(WORKBENCH);
            require(tile instanceof TileArcaneWorkbench && ((TileArcaneWorkbench) tile).isEmpty(),
                "Persistence baseline requires real fully paid empty native workbench");
            value.addProperty("workbenchNbt", tile.saveWithFullMetadata(player.registryAccess()).toString());
            return value;
        }

        static CompoundTag canonicalKnowledge(String snbt) throws Exception {
            CompoundTag tag = TagParser.parseTag(snbt);
            for (String listName : List.of("research", "knowledge")) {
                ListTag input = tag.getList(listName, Tag.TAG_COMPOUND), sorted = new ListTag();
                List<CompoundTag> rows = new ArrayList<>();
                for (int index = 0; index < input.size(); index++) {
                    CompoundTag row = input.getCompound(index).copy();
                    if (row.contains("flags", Tag.TAG_STRING)) {
                        String flags = row.getString("flags");
                        if (flags.isBlank()) row.remove("flags");
                        else row.putString("flags", java.util.Arrays.stream(flags.split(",")).sorted().collect(java.util.stream.Collectors.joining(",")));
                    }
                    rows.add(row);
                }
                rows.sort(java.util.Comparator.comparing(row -> row.getString("key"))); rows.forEach(sorted::add);
                tag.put(listName, sorted);
            }
            return tag;
        }

        static boolean sameBaseline(JsonObject before, JsonObject after) throws Exception {
            if (before == null || after == null) return false;
            JsonObject left = before.deepCopy(), right = after.deepCopy();
            String leftKnowledge = string(left, "knowledgeNbt"), rightKnowledge = string(right, "knowledgeNbt");
            left.remove("knowledgeNbt"); right.remove("knowledgeNbt");
            return left.equals(right) && canonicalKnowledge(leftKnowledge).equals(canonicalKnowledge(rightKnowledge));
        }

        void reconnect(JsonObject peer) throws Exception {
            ServerPlayer current = server.getPlayerList().getPlayerByName(USERNAME);
            if (!reconnectAllowed) {
                owner(player);
                require(current == player, "Original native owner must remain online before reconnect is authorized");
                if (peer == null || !peer.has("reconnectReady") || !peer.get("reconnectReady").getAsBoolean()) return;
                require(player.containerMenu == player.inventoryMenu && player.containerMenu.getCarried().isEmpty(),
                    "Reconnect baseline requires real GUI/menu closed and no carried stack");
                JsonObject candidate = baseline();
                if (!sameBaseline(candidate, quietCandidate)) { quietCandidate = candidate; reconnectQuietSince = server.getTickCount(); }
                if (server.getTickCount() - reconnectQuietSince >= 40) {
                    persistenceBaseline = candidate;
                    reconnectAllowed = true;
                    JsonObject details = new JsonObject(); details.addProperty("quietTicks", 40);
                    details.add("persistenceBaseline", persistenceBaseline.deepCopy());
                    event("native-owner-reconnect-baseline", details);
                    publish("running");
                }
                return;
            }
            if (current == null && !connection.isConnected()) {
                if (!disconnectedObserved) {
                    require(server.getPlayerCount() == 0, "Private dedicated server must be actually empty after disconnect");
                    disconnectedObserved = true;
                    event("actual-owner-tcp-disconnected", new JsonObject()); publish("running");
                }
                return;
            }
            if (current == player) {
                if (connection.isConnected()) require(sameBaseline(baseline(), persistenceBaseline), "Original owner changed after reconnect baseline");
                return;
            }
            if (current != null) {
                var prior = player;
                var priorConnection = connection;
                require(disconnectedObserved && !priorConnection.isConnected() && current != prior
                    && current.connection.getConnection() != priorConnection && !server.getPlayerList().getPlayers().contains(prior),
                    "Reconnect must replace actual closed TCP connection and removed native ServerPlayer");
                player = current; connection = current.connection.getConnection(); owner(player);
                require(sameBaseline(baseline(), persistenceBaseline), "Native disk-backed reconnect changed knowledge/warp/inventory/XP or real table");
                ownerEpoch = 2; reconnectProven = true;
                check("native-second-tcp-owner-reconnect", "Actual server list became empty and old TCP closed; a distinct new ServerPlayer/TCP connection loaded identical UUID, research, knowledge, warp, inventory, XP and paid world state");
                transition("finish");
            }
        }

        int inventoryCount(Item item) {
            int count = 0;
            for (ItemStack stack : player.getInventory().items) if (stack.is(item)) count += stack.getCount();
            for (ItemStack stack : player.getInventory().offhand) if (stack.is(item)) count += stack.getCount();
            return count;
        }

        int totalCount(Item item) {
            int count = inventoryCount(item);
            if (player.containerMenu instanceof CraftingMenu menu) {
                for (int slot = 1; slot <= 9; slot++) {
                    ItemStack stack = menu.getSlot(slot).getItem();
                    if (stack.is(item)) count += stack.getCount();
                }
            }
            ItemStack carried = player.containerMenu.getCarried();
            if (carried.is(item)) count += carried.getCount();
            return count;
        }

        void transition(String next) throws Exception {
            phase = next;
            actionId++;
            phaseDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
            event("phase", new JsonObject());
            publish("running");
            Thaumcraft.log.info("Private native TCP bootstrap phase {}", next);
        }

        void check(String name, String detail) throws Exception {
            JsonObject row = new JsonObject();
            row.addProperty("name", name); row.addProperty("detail", detail); row.addProperty("serverTick", server.getTickCount());
            row.addProperty("passed", true);
            checks.add(row);
            event("check", row.deepCopy());
        }

        void event(String name, JsonObject details) throws Exception {
            require(nativeEvents.size() < 256, "Unbounded native bootstrap event transcript");
            JsonObject row = new JsonObject();
            row.addProperty("sequence", ++eventSequence); row.addProperty("name", name); row.addProperty("phase", phase);
            row.addProperty("serverTick", server.getTickCount()); row.addProperty("utc", Instant.now().toString());
            row.addProperty("mainThread", server.isSameThread());
            if (player != null) row.addProperty("ownerUuid", player.getUUID().toString());
            row.add("details", details);
            nativeEvents.add(row);
            appendEvent(directory, "server", row);
        }

        JsonObject state(String status) {
            JsonObject value = identity(marker);
            value.addProperty("status", status); value.addProperty("phase", phase);
            value.addProperty("actionId", actionId); value.addProperty("serverTick", server.getTickCount());
            value.addProperty("overworldEndEvents", overworldEndEvents);
            value.addProperty("ownerUuid", OWNER_UUID.toString());
            value.addProperty("nativeRecipeCraftCount", nativeRecipeCraftCount);
            value.addProperty("dustCraftCount", nativeRecipeCraftCount);
            value.addProperty("bookCraftEvents", bookCraftEvents); value.addProperty("workbenchCraftEvents", workbenchCraftEvents);
            value.addProperty("controlledWake", controlledWake);
            value.addProperty("thaumometerCraftEvents", thaumometerCraftEvents);
            value.addProperty("thaumometerUseEvents", thaumometerUseEvents);
            value.addProperty("scanIndex", scanIndex);
            value.addProperty("scanResearchEvents", scanResearchEvents);
            value.addProperty("reconnectAllowed", reconnectAllowed);
            value.addProperty("disconnectedObserved", disconnectedObserved);
            value.addProperty("reconnectProven", reconnectProven);
            value.addProperty("ownerEpoch", ownerEpoch);
            if (persistenceBaseline != null) value.add("persistenceBaseline", persistenceBaseline.deepCopy());
            JsonObject positions = new JsonObject();
            positions.add("craft", vector(CRAFT)); positions.add("bookshelf", vector(BOOKSHELF)); positions.add("workbench", vector(WORKBENCH));
            JsonArray scans = new JsonArray(), scanKeys = new JsonArray();
            for (int index = 0; index < SCANS.size(); index++) { scans.add(vector(SCANS.get(index))); scanKeys.add(scanKey(index)); }
            positions.add("scans", scans); value.add("scanKeys", scanKeys);
            value.add("positions", positions);
            if (player != null) {
                var knowledge = ThaumcraftCapabilities.getKnowledge(player);
                value.addProperty("firststepsStage", knowledge.getResearchStage("FIRSTSTEPS"));
                value.addProperty("gotDream", knowledge.isResearchKnown("!gotdream"));
                value.addProperty("gotBook", knowledge.isResearchKnown("!gotthaumonomicon"));
                value.addProperty("dustCount", inventoryCount(ItemsTC.salisMundus));
                value.addProperty("crystalCount", inventoryCount(ItemsTC.crystalEssence));
                value.addProperty("thaumometerCount", inventoryCount(ItemsTC.thaumometer));
                value.addProperty("basicsRaw", basicRaw());
                value.addProperty("firststepsComplete", knowledge.isResearchComplete("FIRSTSTEPS"));
                value.addProperty("workbenchBlock", BuiltInRegistries.BLOCK.getKey(server.overworld().getBlockState(WORKBENCH).getBlock()).toString());
                value.addProperty("knowledgeNbt", knowledge.serializeNBT().toString());
                value.addProperty("warpNbt", ThaumcraftCapabilities.getWarp(player).serializeNBT().toString());
                value.addProperty("actualTCP", !connection.isMemoryConnection());
            }
            return value;
        }

        void publish(String status) throws Exception { writeState(directory, "server", state(status)); }

        void verifySavedPlayer() throws Exception {
            owner(player);
            Path file = safePath(directory, world.resolve("playerdata").resolve(player.getUUID() + ".dat"));
            require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) > 0 && Files.size(file) <= 1024 * 1024,
                "Real private player save missing/unsafe");
            CompoundTag saved = NbtIo.readCompressed(file, NbtAccounter.create(4L * 1024 * 1024));
            require(saved.contains("UUID", Tag.TAG_INT_ARRAY) && saved.getUUID("UUID").equals(player.getUUID()),
                "Saved real UUID differs from live owner");
            CompoundTag attachments = saved.getCompound("neoforge:attachments");
            require(attachments.contains("thaumcraft:knowledge", Tag.TAG_COMPOUND)
                && attachments.getCompound("thaumcraft:knowledge").equals(ThaumcraftCapabilities.getKnowledge(player).serializeNBT())
                && attachments.contains("thaumcraft:warp", Tag.TAG_COMPOUND)
                && attachments.getCompound("thaumcraft:warp").equals(ThaumcraftCapabilities.getWarp(player).serializeNBT()),
                "Real disk knowledge/warp does not match native final live owner");
            require(saved.getList("Inventory", Tag.TAG_COMPOUND).equals(player.getInventory().save(new ListTag()))
                && saved.getInt("SelectedItemSlot") == player.getInventory().selected
                && saved.getInt("XpTotal") == player.totalExperience && saved.getInt("XpLevel") == player.experienceLevel
                && Float.floatToIntBits(saved.getFloat("XpP")) == Float.floatToIntBits(player.experienceProgress),
                "Real disk inventory/selected slot/XP must exactly match final native reconnected owner");
            report.addProperty("diskNbtVerified", true);
            report.addProperty("diskPlayerBytes", Files.size(file));
            report.addProperty("savedKnowledgeNbt", attachments.getCompound("thaumcraft:knowledge").toString());
            report.addProperty("savedWarpNbt", attachments.getCompound("thaumcraft:warp").toString());
        }

        void finish(boolean passed, String failure) {
            if (finished) return;
            finished = true;
            try {
                try {
                    if (player != null) server.getPlayerList().saveAll();
                    boolean flushed = server.saveEverything(true, true, true);
                    report.addProperty("saveFlushed", flushed);
                    if (passed) {
                        require(flushed, "Private dedicated world save did not flush");
                        verifySavedPlayer();
                    }
                } catch (Exception saving) {
                    passed = false;
                    failure = (failure == null ? "" : failure + "; ") + "Real private save verification failed: " + saving;
                }
                phase = passed ? "completed" : "failed";
                report.addProperty("passed", passed); report.addProperty("status", phase);
                report.addProperty("finishedUtc", Instant.now().toString());
                report.addProperty("ownerUuid", OWNER_UUID.toString());
                report.addProperty("nativeRecipeCraftCount", nativeRecipeCraftCount);
                report.addProperty("controlledWake", controlledWake);
                report.addProperty("crystalPickupCount", crystalPickupCount);
                report.addProperty("bookCraftEvents", bookCraftEvents); report.addProperty("workbenchCraftEvents", workbenchCraftEvents);
                report.addProperty("nativeFirstStepsResearchEvents", firstStepsEvents);
                report.addProperty("actualContextualRequestCount", contextualRequests.size());
                report.addProperty("thaumometerCraftEvents", thaumometerCraftEvents);
                report.addProperty("thaumometerUseEvents", thaumometerUseEvents);
                report.addProperty("scanResearchEvents", scanResearchEvents);
                report.addProperty("completedScanCount", scanReceipts.size());
                report.addProperty("basicAwardEvents", basicAwardEvents);
                report.addProperty("scansCompleteBasicsRaw", scansCompleteRaw);
                report.addProperty("observationPaymentEvents", observationPaymentEvents);
                report.addProperty("observationPaymentBefore", observationPaymentBefore);
                report.addProperty("craftVisBefore", craftVisBefore); report.addProperty("craftVisAfter", craftVisAfter);
                report.addProperty("craftAuraFloatBefore", craftAuraBefore); report.addProperty("craftAuraFloatAfter", craftAuraAfter);
                report.addProperty("craftVisPaid", craftVisBefore - craftVisAfter);
                report.addProperty("reconnectProven", reconnectProven); report.addProperty("ownerEpoch", ownerEpoch);
                report.addProperty("actualOldConnectionClosed", disconnectedObserved);
                report.addProperty("bookshelfLevelEndEventsAfterUse", bookshelfConsumedEndEvents >= 0 ? overworldEndEvents - bookshelfConsumedEndEvents : -1);
                report.addProperty("workbenchLevelEndEventsAfterUse", workbenchConsumedEndEvents >= 0 ? overworldEndEvents - workbenchConsumedEndEvents : -1);
                if (player != null) {
                    var knowledge = ThaumcraftCapabilities.getKnowledge(player);
                    report.addProperty("finalFirststepsStage", knowledge.getResearchStage("FIRSTSTEPS"));
                    report.addProperty("finalFirststepsComplete", knowledge.isResearchComplete("FIRSTSTEPS"));
                    report.addProperty("finalBasicsRaw", basicRaw());
                    report.addProperty("gotDream", knowledge.isResearchKnown("!gotdream")); report.addProperty("gotBook", knowledge.isResearchKnown("!gotthaumonomicon"));
                    report.addProperty("workbenchBlock", BuiltInRegistries.BLOCK.getKey(server.overworld().getBlockState(WORKBENCH).getBlock()).toString());
                    report.addProperty("initialKnowledgeNbt", initialKnowledge.toString());
                }
                if (failure != null) report.addProperty("failure", failure);
                try { publish(phase); }
                catch (Exception progress) {
                    passed = false; report.addProperty("passed", false); report.addProperty("status", "failed");
                    report.addProperty("failure", (failure == null ? "" : failure + "; ") + "Terminal state write failed: " + progress);
                }
                terminalAttempted = true;
                writeTerminal(directory, "server", report);
                Thaumcraft.log.info("Private native TCP bootstrap passed={}: {}", passed, failure);
            } catch (Exception terminal) { Thaumcraft.log.error("Private TCP bootstrap terminal report failed", terminal); }
            finally {
                ModConfig.CONFIG_MISC.noSleep = previousNoSleep;
                server.halt(false);
            }
        }

        static JsonArray vector(BlockPos position) {
            JsonArray value = new JsonArray();
            value.add(position.getX()); value.add(position.getY()); value.add(position.getZ());
            return value;
        }
    }
}
