package thaumcraft.client.lib;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.client.gui.GuiResearchBrowser;
import thaumcraft.client.gui.GuiResearchPage;
import thaumcraft.client.gui.model.ResearchBrowserProjection;
import thaumcraft.common.container.ContainerArcaneWorkbench;
import thaumcraft.common.items.resources.ItemCrystalEssence;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchCosts;
import thaumcraft.common.lib.capabilities.validation.PlayerDataNetworkAudit;
import thaumcraft.common.lib.research.LegacyScanIdentity;
import thaumcraft.common.lib.utils.ScanTargeting;
import thaumcraft.common.lib.validation.TcpBootstrapAuditIO;

/** Explicit private loopback TCP gameplay driver. Materials/arena/initial position and wake
 * are controlled server fixtures. Every craft, dust use, book use and research request below
 * uses native client menu/item/screen actions. No inventory-stack, knowledge or world writes.
 * Tangible ingredient/scan-block/aura fixtures isolate native acquisition, paid thaumometer,
 * seven natural block scans and full FIRSTSTEPS. This is not a full survival run.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ClientTcpBootstrapAudit {
    private static Run active;
    private static boolean stopped;

    private ClientTcpBootstrapAudit() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(TcpBootstrapAuditIO.ENABLE_PROPERTY) || stopped) return;
        var client = Minecraft.getInstance();
        try {
            if (active == null) active = new Run(client);
            active.step();
        } catch (Exception failure) {
            Thaumcraft.log.error("Private TCP bootstrap client failed", failure);
            if (active != null) active.fail(failure.toString());
            else { stopped = true; client.stop(); }
        }
    }

    private static void require(boolean condition, String detail) { TcpBootstrapAuditIO.require(condition, detail); }

    private record Click(int menu, int slot, int button, ClickType type) {}

    private static final class Run {
        final Minecraft client;
        final Path directory;
        final JsonObject marker;
        final JsonArray actions = new JsonArray();
        final JsonArray snapshots = new JsonArray();
        final JsonArray screenshots = new JsonArray();
        final JsonArray scans = new JsonArray();
        final ArrayDeque<Click> clicks = new ArrayDeque<>();
        final long started = System.nanoTime();
        Connection connection;
        LocalPlayer owner;
        String phase = "login";
        String lastSnapshot;
        long ticks, phaseTicks, phaseStarted = started;
        int dustTakes, craftStep, heldTicks, uiTicks, screenStep;
        boolean filled, bookshelfUsed, workbenchUsed, bookUsed, browserClicked, pageClicked;
        boolean sealed;
        int screenshotPending;
        int thaumometerStep, thaumometerVisBefore, thaumometerVisAfter = -1, scanSentIndex = -1, scanAcceptedIndex = -1;
        int scanLookTicks, scanRawBefore, completionRawBefore = -1;
        String pendingScanKey;
        boolean thaumometerFilled, thaumometerTaken, fullPageClicked, fullScreenshot;
        boolean reconnectReady, reconnectStarted, reconnectConnectSent, reconnectProven;
        int connectionEpoch = 1, reconnectTitleTicks;
        Connection firstConnection;
        LocalPlayer firstOwner;
        CompoundTag reconnectKnowledge, reconnectWarp;
        ListTag reconnectInventory;
        JsonObject reconnectBaseline;

        Run(Minecraft client) throws Exception {
            this.client = client;
            require(client.isSameThread(), "Private bootstrap client initializes on MAIN");
            require(!ClientJeiReviewScenario.enabled(), "JEI integrated review and TCP bootstrap cohorts are exclusive");
            directory = TcpBootstrapAuditIO.directory(client.gameDirectory.toPath(), "client");
            marker = TcpBootstrapAuditIO.validateDirectory(directory, "client");
            require(TcpBootstrapAuditIO.USERNAME.equals(client.getUser().getName()), "Only private TC_Bootstrap profile allowed");
            client.options.pauseOnLostFocus = false;
            event("client-initialized", new JsonObject());
            persist();
        }

        void checkConnection() {
            require(client.isSameThread() && !client.hasSingleplayerServer() && client.getSingleplayerServer() == null,
                "Native bootstrap requires actual dedicated TCP, never an integrated server");
            require(client.getConnection() != null && client.player != null && client.gameMode != null,
                "Missing actual remote PLAY LocalPlayer");
            require(client.getConnection().getConnection() == connection && client.player == owner,
                "Unexpected connection or owner replacement in fresh TCP bootstrap");
            require(connection.isConnected() && !connection.isMemoryConnection()
                && connection.getRemoteAddress() instanceof InetSocketAddress remote
                && remote.getAddress() != null && remote.getAddress().isLoopbackAddress()
                && remote.getPort() == marker.get("port").getAsInt(), "Unexpected remote TCP endpoint");
            require(owner.getUUID().equals(TcpBootstrapAuditIO.OWNER_UUID)
                && owner.getGameProfile().getName().equals(TcpBootstrapAuditIO.USERNAME), "Unexpected actual LocalPlayer identity");
            require(client.gameMode.getPlayerMode() == GameType.SURVIVAL && !owner.getAbilities().instabuild,
                "Bootstrap item payment requires native survival mode");
        }

        void step() throws Exception {
            ticks++; phaseTicks++;
            require(System.nanoTime() - started < TimeUnit.MINUTES.toNanos(20)
                && System.nanoTime() - phaseStarted < TimeUnit.MINUTES.toNanos(8), "Timed out in TCP bootstrap phase " + phase);
            JsonObject terminal = TcpBootstrapAuditIO.readPeer(directory, marker, "server", true);
            if (terminal != null) {
                require(terminal.has("passed") && terminal.get("passed").getAsBoolean(), "Server terminal rejected TCP bootstrap");
                require(sealed, "Server finished before client sealed native observations");
                event("matching-server-terminal", new JsonObject());
                stopped = true;
                client.stop();
                return;
            }
            JsonObject control = TcpBootstrapAuditIO.readPeer(directory, marker, "server", false);
            if (control == null) return;
            require(!"failed".equals(TcpBootstrapAuditIO.string(control, "status")), "Server state failed");
            String nextPhase = TcpBootstrapAuditIO.string(control, "phase");
            if (sealed && "completed".equals(nextPhase)) return;
            require(List.of("login", "materials", "craft-dust", "bookshelf", "book-pickup", "firststeps1", "workbench", "firststeps2", "thaumometer", "scans", "firststeps-complete", "reconnect", "finish").contains(nextPhase), "Unknown server phase " + nextPhase);
            if (!nextPhase.equals(phase)) {
                phase = nextPhase; phaseTicks = 0; phaseStarted = System.nanoTime();
                screenStep = heldTicks = uiTicks = 0;
                JsonObject row = new JsonObject(); row.add("control", control.deepCopy());
                event("server-phase", row); persist();
            }
            if (reconnectStarted && !reconnectProven) {
                require(phase.equals("reconnect") || phase.equals("finish"), "Server left explicit native reconnect handoff unexpectedly");
                reconnect(control); return;
            }
            if (connection == null) {
                require(!client.hasSingleplayerServer(), "Integrated server forbidden in TCP bootstrap");
                if (client.getConnection() == null || client.player == null || client.gameMode == null) return;
                connection = client.getConnection().getConnection(); owner = client.player;
                checkConnection();
                event("actual-tcp-play-owner", connectionEvidence());
            }
            if (sealed) return;
            checkConnection();
            observeOwner();
            observeScanAcknowledgement();
            switch (phase) {
                case "login", "materials", "book-pickup" -> { }
                case "craft-dust" -> craftDust();
                case "bookshelf" -> {
                    if (!bookshelfUsed && selectHeld(stack -> stack.is(ItemsTC.salisMundus))) {
                        if (!client.level.getBlockState(position(control, "bookshelf")).is(Blocks.BOOKSHELF)) return;
                        closeScreen(); useBlock(position(control, "bookshelf"), "bookshelf-dust-use"); bookshelfUsed = true;
                    }
                }
                case "firststeps1" -> firstStepsStart();
                case "workbench" -> {
                    if (!workbenchUsed && selectHeld(stack -> stack.is(ItemsTC.salisMundus))) {
                        var view = ClientResearchPresentation.view();
                        if (view == null || view.storedStage("FIRSTSTEPS") != 1) return;
                        if (!client.level.getBlockState(position(control, "workbench")).is(Blocks.CRAFTING_TABLE)) return;
                        closeScreen(); useBlock(position(control, "workbench"), "workbench-dust-use"); workbenchUsed = true;
                    }
                }
                case "firststeps2" -> firstStepsAdvance(control);
                case "thaumometer" -> craftThaumometer(control);
                case "scans" -> {
                    // The server may publish the scan phase immediately after the genuine
                    // destination click reaches it, before the native menu S2C reaches us.
                    if (thaumometerStep != 2) craftThaumometer(control); else scanBlock(control);
                }
                case "firststeps-complete" -> completeFirstSteps();
                case "reconnect" -> reconnect(control);
                case "finish" -> finish();
            }
            if (ticks % 100 == 0) persist();
        }

        BlockPos position(JsonObject control, String key) {
            var value = control.getAsJsonObject("positions").getAsJsonArray(key);
            BlockPos result = new BlockPos(value.get(0).getAsInt(), value.get(1).getAsInt(), value.get(2).getAsInt());
            BlockPos expected = switch (key) {
                case "craft" -> new BlockPos(2, 65, 0);
                case "bookshelf" -> new BlockPos(2, 65, 2);
                case "workbench" -> new BlockPos(2, 65, 4);
                default -> throw new IllegalStateException("Unknown private arena position");
            };
            require(result.equals(expected), "Unexpected server arena position"); return result;
        }

        void useBlock(BlockPos position, String action) throws Exception {
            require(owner.getEyePosition().distanceTo(Vec3.atCenterOf(position)) <= 4.5, "Private fixture target outside native interaction reach");
            var result = client.gameMode.useItemOn(owner, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
            JsonObject row = new JsonObject(); row.addProperty("position", position.toShortString());
            row.addProperty("result", result.toString()); row.addProperty("heldItem", owner.getMainHandItem().toString());
            row.addProperty("nativeC2S", true);
            event(action, row);
        }

        void closeScreen() {
            if (owner.containerMenu != owner.inventoryMenu) owner.closeContainer();
            else if (client.screen != null) client.setScreen(null);
        }

        void click(Click click) throws Exception {
            require(owner.containerMenu.containerId == click.menu(), "Native menu changed during queued clicks");
            client.gameMode.handleInventoryMouseClick(click.menu(), click.slot(), click.button(), click.type(), owner);
            JsonObject row = new JsonObject(); row.addProperty("menuId", click.menu()); row.addProperty("slot", click.slot());
            row.addProperty("button", click.button()); row.addProperty("clickType", click.type().name());
            row.addProperty("nativeC2S", true); event("vanilla-menu-click", row);
        }

        int inventorySlot(AbstractContainerMenu menu, Predicate<ItemStack> predicate) {
            for (var slot : menu.slots) if (slot.container == owner.getInventory() && predicate.test(slot.getItem())) return slot.index;
            return -1;
        }

        void moveIngredient(AbstractContainerMenu menu, int target, int amount, Predicate<ItemStack> predicate) {
            int source = inventorySlot(menu, stack -> !stack.isEmpty() && stack.getCount() >= amount && predicate.test(stack));
            require(source >= 0 && menu.getSlot(target).getItem().isEmpty(), "Missing real acquired ingredient for crafting slot " + target);
            clicks.add(new Click(menu.containerId, source, 0, ClickType.PICKUP));
            for (int count = 0; count < amount; count++) clicks.add(new Click(menu.containerId, target, 1, ClickType.PICKUP));
            clicks.add(new Click(menu.containerId, source, 0, ClickType.PICKUP));
        }

        boolean crystal(ItemStack stack, Aspect aspect) {
            return stack.is(ItemsTC.crystalEssence) && stack.getItem() instanceof ItemCrystalEssence item
                && item.getAspects(stack).getAmount(aspect) > 0;
        }

        void craftDust() throws Exception {
            if (craftStep == 0) {
                var view = ClientResearchPresentation.view();
                if (view == null || !view.knows("!gotdream")
                    || !ThaumcraftCapabilities.getKnowledge(owner).isResearchKnown("!gotdream")) return;
                useBlock(new BlockPos(2, 65, 0), "native-crafting-table-open"); craftStep = 1; return;
            }
            if (!(owner.containerMenu instanceof CraftingMenu menu)) return;
            if (!filled) {
                require(menu.getCarried().isEmpty(), "Fresh ordinary crafting cursor required");
                moveIngredient(menu, 1, 1, stack -> stack.is(Items.BOWL));
                moveIngredient(menu, 2, 1, stack -> stack.is(Items.FLINT));
                moveIngredient(menu, 3, 2, stack -> stack.is(Items.REDSTONE));
                moveIngredient(menu, 4, 2, stack -> crystal(stack, Aspect.AIR));
                moveIngredient(menu, 5, 2, stack -> crystal(stack, Aspect.FIRE));
                moveIngredient(menu, 6, 2, stack -> crystal(stack, Aspect.WATER));
                filled = true;
            }
            if (!clicks.isEmpty()) {
                if (ticks % 3 == 0) click(clicks.remove());
                return;
            }
            if (!menu.getCarried().isEmpty()) {
                require(menu.getCarried().is(ItemsTC.salisMundus), "Unexpected cursor after native dust take");
                int destination = inventorySlot(menu, ItemStack::isEmpty);
                require(destination >= 0, "Private ordinary inventory needs an empty dust destination");
                click(new Click(menu.containerId, destination, 0, ClickType.PICKUP)); return;
            }
            if (dustTakes < 2) {
                if (!menu.getSlot(0).getItem().is(ItemsTC.salisMundus)) return;
                click(new Click(menu.containerId, 0, 0, ClickType.PICKUP)); dustTakes++;
                JsonObject row = new JsonObject(); row.addProperty("take", dustTakes); event("native-salis-result-take", row); return;
            }
            require(menu.getSlot(1).getItem().is(Items.BOWL) && menu.getSlot(2).getItem().is(Items.FLINT),
                "Native source reusable bowl/flint must remain after two real crafts");
            if (count(stack -> stack.is(ItemsTC.salisMundus)) < 2) return;
            require(count(stack -> stack.is(ItemsTC.salisMundus)) == 2, "Two genuine dust outputs must be in actual inventory");
            owner.closeContainer(); craftStep = 2;
        }

        /** Only native selection/UI state changes locally; item-stack movement goes through SWAP C2S. */
        boolean selectHeld(Predicate<ItemStack> predicate) throws Exception {
            if (owner.containerMenu != owner.inventoryMenu) { closeScreen(); heldTicks = 0; return false; }
            if (owner.getInventory().selected != 0) { owner.getInventory().selected = 0; heldTicks = 0; }
            if (predicate.test(owner.getMainHandItem())) return ++heldTicks >= 3;
            int slot = inventorySlot(owner.inventoryMenu, predicate);
            if (slot < 0) return false;
            click(new Click(owner.inventoryMenu.containerId, slot, 0, ClickType.SWAP)); heldTicks = 0;
            return false;
        }

        void useBook(String action) throws Exception {
            closeScreen();
            var result = client.gameMode.useItem(owner, InteractionHand.MAIN_HAND);
            JsonObject row = new JsonObject(); row.addProperty("result", result.toString());
            row.addProperty("nativeC2S", true); event(action, row); uiTicks = 0;
        }

        void firstStepsStart() throws Exception {
            var view = ClientResearchPresentation.view();
            if (view == null || !view.knows("!gotthaumonomicon")) return;
            if (view.storedStage("FIRSTSTEPS") >= 1) return;
            if (!bookUsed) {
                if (!selectHeld(stack -> stack.is(ItemsTC.thaumonomicon))) return;
                useBook("native-book-use"); bookUsed = true; return;
            }
            if (!(client.screen instanceof GuiResearchBrowser browser) || ++uiTicks < 10) return;
            if (!browserClicked) {
                var prepared = ClientResearchPresentation.prepare("FIRSTSTEPS");
                require(prepared != null && prepared.packet().expectedStage() == -1, "Actual accepted owner/catalog must allow only the unknown FIRSTSTEPS start");
                screenshot("01-thaumonomicon-browser.png");
                var model = (ResearchBrowserProjection.Browser) field(browser, "browser");
                var node = model.nodes().stream().filter(candidate -> candidate.rawSpec().key().equals("FIRSTSTEPS")).findFirst().orElseThrow();
                double panX = ((Number) field(browser, "panX")).doubleValue(), panY = ((Number) field(browser, "panY")).doubleValue();
                double zoom = ((Number) field(browser, "zoom")).doubleValue();
                double x = (24D + node.x() - panX) / zoom, y = (24D + node.y() - panY) / zoom;
                require(x >= 16 && x < browser.width - 16 && y >= 16 && y < browser.height - 16, "FIRSTSTEPS native node must be visible");
                require(browser.mouseClicked(x, y, 0), "Native browser did not accept FIRSTSTEPS node click");
                require(client.screen instanceof GuiResearchPage, "Native browser click must open a genuine research page");
                JsonObject row = new JsonObject(); row.addProperty("expectedStage", -1); row.addProperty("catalogDigest", prepared.packet().catalogDigest());
                row.addProperty("nativeScreenClick", true); event("browser-FIRSTSTEPS-start", row); browserClicked = true;
            }
        }

        void firstStepsAdvance(JsonObject control) throws Exception {
            var view = ClientResearchPresentation.view();
            if (view == null) return;
            if (view.storedStage("FIRSTSTEPS") == 2) return;
            require(view.storedStage("FIRSTSTEPS") == 1, "Advance expects actual accepted stage1");
            BlockPos workbench = position(control, "workbench");
            if (!client.level.getBlockState(workbench).is(BlocksTC.arcaneWorkbench)) return;
            if (screenStep == 0) {
                closeScreen(); useBlock(workbench, "native-workbench-open"); screenStep = 1; uiTicks = 0; return;
            }
            if (screenStep == 1) {
                if (!(owner.containerMenu instanceof ContainerArcaneWorkbench) || ++uiTicks < 10) return;
                screenshot("02-native-arcane-workbench.png"); owner.closeContainer(); screenStep = 2; return;
            }
            if (screenStep == 2) {
                if (!selectHeld(stack -> stack.is(ItemsTC.thaumonomicon))) return;
                useBook("native-book-reopen"); screenStep = 3; return;
            }
            if (screenStep == 3) {
                if (!(client.screen instanceof GuiResearchBrowser browser) || ++uiTicks < 10) return;
                var model = (ResearchBrowserProjection.Browser) field(browser, "browser");
                var node = model.nodes().stream().filter(candidate -> candidate.rawSpec().key().equals("FIRSTSTEPS")).findFirst().orElseThrow();
                double panX = ((Number) field(browser, "panX")).doubleValue(), panY = ((Number) field(browser, "panY")).doubleValue();
                double zoom = ((Number) field(browser, "zoom")).doubleValue();
                require(browser.mouseClicked((24D + node.x() - panX) / zoom, (24D + node.y() - panY) / zoom, 0), "Native known-node click rejected");
                screenStep = 4; uiTicks = 0; return;
            }
            if (screenStep == 4 && !pageClicked) {
                if (!(client.screen instanceof GuiResearchPage page) || ++uiTicks < 15) return;
                var prepared = ClientResearchPresentation.prepare("FIRSTSTEPS");
                if (prepared == null || prepared.packet().expectedStage() != 1) return;
                int groups = ((List<?>) field(page, "requirements")).size();
                require(groups > 0, "Native FIRSTSTEPS workbench requirement must be displayed");
                // A newly applied owner snapshot may precede the next native Screen.tick.
                // The actual page's read-only eligibility must be current before its click.
                if (!(Boolean) invokeReadOnly(page, "canAttempt")) return;
                screenshot("03-firststeps-workbench-requirement.png");
                double left = (page.width - 256) / 2, top = (page.height - 181) / 2;
                require(page.mouseClicked(left + 32, top + 180 - groups * 18, 0), "Native page advance click rejected");
                JsonObject row = new JsonObject(); row.addProperty("expectedStage", 1); row.addProperty("nativeScreenClick", true);
                row.addProperty("catalogDigest", prepared.packet().catalogDigest()); event("page-FIRSTSTEPS-advance", row); pageClicked = true;
            }
        }

        void craftThaumometer(JsonObject control) throws Exception {
            var view = ClientResearchPresentation.view();
            if (view == null || view.storedStage("FIRSTSTEPS") != 2) return;
            if (thaumometerStep == 0) {
                closeScreen(); useBlock(position(control, "workbench"), "native-thaumometer-workbench-open");
                thaumometerStep = 1; uiTicks = 0; return;
            }
            if (!(owner.containerMenu instanceof ContainerArcaneWorkbench menu)) return;
            if (!thaumometerFilled) {
                require(menu.getCarried().isEmpty(), "Native thaumometer crafting needs an empty cursor");
                for (int slot = 1; slot <= 15; slot++) require(menu.getSlot(slot).getItem().isEmpty(), "Native arcane input must initially be empty");
                for (int slot : new int[]{2, 4, 6, 8}) moveIngredient(menu, slot, 1, stack -> stack.is(Items.GOLD_INGOT));
                moveIngredient(menu, 5, 1, stack -> stack.is(Items.GLASS_PANE));
                for (int aspect = 0; aspect < 6; aspect++) {
                    Aspect primal = ArcaneWorkbenchCosts.PRIMALS[aspect];
                    moveIngredient(menu, 10 + aspect, 1, stack -> crystal(stack, primal));
                }
                thaumometerFilled = true;
            }
            if (!clicks.isEmpty()) {
                if (ticks % 3 == 0) click(clicks.remove());
                return;
            }
            if (!thaumometerTaken) {
                if (!menu.getSlot(0).getItem().is(ItemsTC.thaumometer) || ++uiTicks < 15) return;
                require(menu.availableVis() >= 20, "Actual synchronized workbench must expose the required 20vis");
                thaumometerVisBefore = menu.availableVis();
                screenshot("04-native-thaumometer-preview.png");
                JsonObject row = new JsonObject(); row.addProperty("menuId", menu.containerId);
                row.addProperty("availableVisBefore", thaumometerVisBefore); row.addProperty("requiredVis", 20);
                row.addProperty("researchStage", view.storedStage("FIRSTSTEPS"));
                row.addProperty("nativeC2S", true);
                click(new Click(menu.containerId, 0, 0, ClickType.PICKUP));
                event("native-thaumometer-result-take", row); thaumometerTaken = true; return;
            }
            if (!menu.getCarried().isEmpty()) {
                require(menu.getCarried().is(ItemsTC.thaumometer) && menu.getCarried().getCount() == 1,
                    "Native paid output must be one actual thaumometer");
                int destination = inventorySlot(menu, ItemStack::isEmpty);
                require(destination >= 0, "Native thaumometer output needs a genuine empty inventory slot");
                click(new Click(menu.containerId, destination, 0, ClickType.PICKUP)); return;
            }
            if (count(stack -> stack.is(ItemsTC.thaumometer)) != 1) return;
            for (int slot = 1; slot <= 15; slot++) {
                if (!menu.getSlot(slot).getItem().isEmpty()) return;
            }
            thaumometerVisAfter = menu.availableVis();
            JsonObject row = new JsonObject(); row.addProperty("availableVisBefore", thaumometerVisBefore);
            row.addProperty("availableVisAfter", thaumometerVisAfter); row.addProperty("inventoryThaumometers", 1);
            row.addProperty("allFifteenInputsEmpty", true); row.addProperty("actualNativeMenuSynchronization", true);
            event("native-thaumometer-paid-output-received", row);
            owner.closeContainer(); thaumometerStep = 2;
        }

        BlockPos scanPosition(JsonObject control, int index) {
            require(index >= 0 && index < 7, "Private scan index outside the reviewed seven targets");
            var positions = control.getAsJsonObject("positions").getAsJsonArray("scans");
            require(positions != null && positions.size() == 7, "Private scan target cohort must contain exactly seven blocks");
            var position = positions.get(index).getAsJsonArray();
            require(position.size() == 3, "Private scan position must be three coordinates");
            BlockPos result = new BlockPos(position.get(0).getAsInt(), position.get(1).getAsInt(), position.get(2).getAsInt());
            require(result.equals(new BlockPos(4, 65, index)), "Unexpected private natural scan position");
            return result;
        }

        void scanBlock(JsonObject control) throws Exception {
            if (pendingScanKey != null) return; // Only an actual owner S2C acknowledges the previous scan.
            int index = control.get("scanIndex").getAsInt();
            require(index >= 0 && index <= 7 && index <= scanSentIndex + 1, "Server skipped a native client scan");
            if (index == 7) { require(scanAcceptedIndex == 6, "Server finished scans before all seven native S2C acknowledgements"); return; }
            if (index <= scanSentIndex) return;
            if (!selectHeld(stack -> stack.is(ItemsTC.thaumometer))) { scanLookTicks = 0; return; }
            closeScreen();
            BlockPos target = scanPosition(control, index);
            var expectedBlocks = List.of(Blocks.STONE, Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.SAND,
                Blocks.GRAVEL, Blocks.OAK_LOG, Blocks.POPPY);
            if (!client.level.getBlockState(target).is(expectedBlocks.get(index))) return;
            if (Math.abs(owner.getX() - 1.5D) > .05D || Math.abs(owner.getY() - 65D) > .05D
                || Math.abs(owner.getZ() - (target.getZ() + .5D)) > .05D) { scanLookTicks = 0; return; }
            Vec3 eye = owner.getEyePosition(), center = Vec3.atCenterOf(target), delta = center.subtract(eye);
            if (eye.distanceTo(center) > ScanTargeting.LEGACY_BLOCK_REACH - .1D) { scanLookTicks = 0; return; }
            // Rotation belongs to the actual LocalPlayer. Its normal vanilla movement tick and
            // native useItem packet deliver these angles; there is no client-supplied scan target.
            owner.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90D));
            owner.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
            if (!(ScanTargeting.scanTarget(owner) instanceof BlockPos hit) || !hit.equals(target)) { scanLookTicks = 0; return; }
            if (++scanLookTicks < 8) return;
            var view = ClientResearchPresentation.view();
            if (view == null || view.storedStage("FIRSTSTEPS") != 2) return;
            String key = LegacyScanIdentity.itemResearchKey(new ItemStack(expectedBlocks.get(index)));
            var keys = control.getAsJsonArray("scanKeys");
            require(keys != null && keys.size() == 7 && key.equals(keys.get(index).getAsString()), "Unexpected private generic scan identity");
            require(!view.knowsLiteral(key), "Fresh native target must not already have a received generic scan marker");
            scanRawBefore = view.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, "BASICS");
            var result = client.gameMode.useItem(owner, InteractionHand.MAIN_HAND);
            JsonObject row = new JsonObject(); row.addProperty("index", index); row.addProperty("scanKey", key);
            row.addProperty("position", target.toShortString());
            row.addProperty("block", BuiltInRegistries.BLOCK.getKey(expectedBlocks.get(index)).toString());
            row.addProperty("yaw", owner.getYRot()); row.addProperty("pitch", owner.getXRot());
            row.addProperty("normalMovementSettleTicks", scanLookTicks); row.addProperty("readOnlyClientRayMatches", true);
            row.addProperty("basicsRawBefore", scanRawBefore); row.addProperty("result", result.toString());
            row.addProperty("nativeC2S", true); row.addProperty("clientTargetPayload", false);
            event("native-thaumometer-block-use", row);
            scanSentIndex = index; pendingScanKey = key; scanLookTicks = 0;
        }

        void observeScanAcknowledgement() throws Exception {
            if (pendingScanKey == null) return;
            var view = ClientResearchPresentation.view();
            if (view == null || !view.knowsLiteral(pendingScanKey)) return;
            int after = view.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, "BASICS");
            require(after >= scanRawBefore, "Received observation knowledge decreased during native scan acquisition");
            JsonObject row = new JsonObject(); row.addProperty("index", scanSentIndex); row.addProperty("scanKey", pendingScanKey);
            row.addProperty("basicsRawBefore", scanRawBefore); row.addProperty("basicsRawAfter", after);
            row.addProperty("rawGain", after - scanRawBefore); row.addProperty("actualAcceptedS2C", true);
            scans.add(row.deepCopy()); event("accepted-native-scan-owner-snapshot", row);
            scanAcceptedIndex = scanSentIndex; pendingScanKey = null;
            if (scanAcceptedIndex == 6) {
                require(scans.size() == 7 && after >= 16, "Seven actual generic scans must earn the original BASICS observation requirement");
                screenshot("05-native-thaumometer-final-scan.png");
            }
        }

        void completeFirstSteps() throws Exception {
            var view = ClientResearchPresentation.view();
            if (view == null || view.storedStage("FIRSTSTEPS") == 4) return;
            require(view.storedStage("FIRSTSTEPS") == 2, "Full FIRSTSTEPS advance expects received stage2");
            if (scanAcceptedIndex != 6 || scans.size() != 7 || pendingScanKey != null) return;
            int raw = view.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, "BASICS");
            require(raw >= 16 && count(stack -> stack.is(ItemsTC.thaumometer)) == 1,
                "Full progression needs earned BASICS observation and genuinely crafted thaumometer");
            if (screenStep == 0) {
                if (!selectHeld(stack -> stack.is(ItemsTC.thaumonomicon))) return;
                useBook("native-full-firststeps-book-reopen"); screenStep = 1; return;
            }
            if (screenStep == 1) {
                if (!(client.screen instanceof GuiResearchBrowser browser) || ++uiTicks < 10) return;
                var model = (ResearchBrowserProjection.Browser) field(browser, "browser");
                var node = model.nodes().stream().filter(candidate -> candidate.rawSpec().key().equals("FIRSTSTEPS")).findFirst().orElseThrow();
                double panX = ((Number) field(browser, "panX")).doubleValue(), panY = ((Number) field(browser, "panY")).doubleValue();
                double zoom = ((Number) field(browser, "zoom")).doubleValue();
                require(browser.mouseClicked((24D + node.x() - panX) / zoom, (24D + node.y() - panY) / zoom, 0),
                    "Native full FIRSTSTEPS known-node click rejected");
                screenStep = 2; uiTicks = 0; return;
            }
            if (screenStep == 2 && !fullPageClicked) {
                if (!(client.screen instanceof GuiResearchPage page) || ++uiTicks < 15) return;
                var prepared = ClientResearchPresentation.prepare("FIRSTSTEPS");
                if (prepared == null || prepared.packet().expectedStage() != 2 || !(Boolean) invokeReadOnly(page, "canAttempt")) return;
                int groups = ((List<?>) field(page, "requirements")).size();
                require(groups == 2, "Original stage2 must display actual thaumometer craft and BASICS observation requirements");
                completionRawBefore = raw;
                screenshot("06-firststeps-thaumometer-observation-requirements.png");
                double left = (page.width - 256) / 2, top = (page.height - 181) / 2;
                require(page.mouseClicked(left + 32, top + 180 - groups * 18, 0), "Native full FIRSTSTEPS page advance click rejected");
                JsonObject row = new JsonObject(); row.addProperty("expectedStage", 2); row.addProperty("nativeScreenClick", true);
                row.addProperty("catalogDigest", prepared.packet().catalogDigest()); row.addProperty("basicsRawBefore", raw);
                row.addProperty("requiredRawObservation", 16); event("page-FIRSTSTEPS-complete", row); fullPageClicked = true;
            }
        }

        /** Match the production client adaptation exactly: only POPUP is removed on apply.
         * PAGE/RESEARCH remain part of the persistence comparison, with unordered lists/flag
         * sets normalized by the existing common audit helper. */
        CompoundTag clientKnowledge(CompoundTag source) {
            CompoundTag result = source.copy();
            for (Tag value : result.getList("research", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) value;
                if (!entry.contains("flags")) continue;
                String flags = java.util.Arrays.stream(entry.getString("flags").split(","))
                    .filter(flag -> !flag.isEmpty() && !flag.equals("POPUP"))
                    .collect(java.util.stream.Collectors.joining(","));
                if (flags.isEmpty()) entry.remove("flags"); else entry.putString("flags", flags);
            }
            return result;
        }

        boolean observeFullFirstSteps() throws Exception {
            var view = ClientResearchPresentation.view();
            if (view == null || view.storedStage("FIRSTSTEPS") != 4 || !view.knowsStrict("FIRSTSTEPS")) return false;
            require(fullPageClicked && completionRawBefore >= 16 && scanAcceptedIndex == 6 && scans.size() == 7,
                "Full received FIRSTSTEPS requires actual native craft, seven scans and page submission");
            require(view.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, "BASICS") == completionRawBefore - 16,
                "Received full FIRSTSTEPS must expose the exact original16 raw observation debit");
            if (!fullScreenshot) {
                if (!(client.screen instanceof GuiResearchPage) || ++uiTicks < 20) return false;
                screenshot("07-firststeps-completed.png");
                JsonObject row = new JsonObject(); row.addProperty("firststepsStage", 4);
                row.addProperty("strictComplete", true); row.addProperty("basicsRawBefore", completionRawBefore);
                row.addProperty("basicsRawAfter", view.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, "BASICS"));
                row.addProperty("actualAcceptedS2C", true); event("accepted-native-full-firststeps-completion", row);
                fullScreenshot = true;
            }
            return screenshotPending == 0;
        }

        void captureReconnectBaseline(JsonObject control) throws Exception {
            reconnectKnowledge = clientKnowledge(ThaumcraftCapabilities.getKnowledge(owner).serializeNBT());
            reconnectWarp = ThaumcraftCapabilities.getWarp(owner).serializeNBT().copy();
            reconnectInventory = owner.getInventory().save(new ListTag()).copy();
            reconnectBaseline = new JsonObject();
            reconnectBaseline.addProperty("knowledgeNbt", reconnectKnowledge.toString());
            reconnectBaseline.addProperty("knowledgeCanonical", PlayerDataNetworkAudit.canonicalKnowledge(reconnectKnowledge));
            reconnectBaseline.addProperty("warpNbt", reconnectWarp.toString());
            reconnectBaseline.addProperty("inventoryNbt", reconnectInventory.toString());
            reconnectBaseline.addProperty("selectedSlot", owner.getInventory().selected);
            reconnectBaseline.addProperty("totalExperience", owner.totalExperience);
            reconnectBaseline.addProperty("experienceLevel", owner.experienceLevel);
            reconnectBaseline.addProperty("experienceProgress", owner.experienceProgress);
            reconnectBaseline.addProperty("experienceProgressBits", Float.floatToIntBits(owner.experienceProgress));
            reconnectBaseline.addProperty("onlyPopupExcluded", true);
            var expected = control.getAsJsonObject("persistenceBaseline");
            require(expected != null, "Server must publish the stable native reconnect baseline before allowing disconnect");
            require(PlayerDataNetworkAudit.canonicalKnowledge(reconnectKnowledge).equals(PlayerDataNetworkAudit.canonicalKnowledge(
                clientKnowledge(TagParser.parseTag(expected.get("knowledgeNbt").getAsString())))), "Client knowledge differs from quiet native server baseline");
            require(reconnectWarp.equals(TagParser.parseTag(expected.get("warpNbt").getAsString())), "Client warp differs from quiet native server baseline");
            CompoundTag inventory = TagParser.parseTag("{inventory:" + expected.get("inventoryNbt").getAsString() + "}");
            require(reconnectInventory.equals(inventory.getList("inventory", Tag.TAG_COMPOUND)), "Full client native inventory differs from quiet server baseline");
            require(owner.getInventory().selected == expected.get("selectedSlot").getAsInt()
                && owner.totalExperience == expected.get("totalExperience").getAsInt()
                && owner.experienceLevel == expected.get("experienceLevel").getAsInt()
                && Float.floatToIntBits(owner.experienceProgress) == Float.floatToIntBits(expected.get("experienceProgress").getAsFloat()),
                "Client selected slot or exact native XP differs from quiet server baseline");
            event("native-reconnect-baseline-received", reconnectBaseline.deepCopy());
        }

        boolean reconnectContentsMatch() {
            return PlayerDataNetworkAudit.canonicalKnowledge(clientKnowledge(ThaumcraftCapabilities.getKnowledge(owner).serializeNBT()))
                    .equals(PlayerDataNetworkAudit.canonicalKnowledge(reconnectKnowledge))
                && ThaumcraftCapabilities.getWarp(owner).serializeNBT().equals(reconnectWarp)
                && owner.getInventory().save(new ListTag()).equals(reconnectInventory)
                && owner.getInventory().selected == reconnectBaseline.get("selectedSlot").getAsInt()
                && owner.totalExperience == reconnectBaseline.get("totalExperience").getAsInt()
                && owner.experienceLevel == reconnectBaseline.get("experienceLevel").getAsInt()
                && Float.floatToIntBits(owner.experienceProgress) == reconnectBaseline.get("experienceProgressBits").getAsInt();
        }

        void reconnect(JsonObject control) throws Exception {
            if (!reconnectStarted) {
                if (!observeFullFirstSteps()) return;
                if (!reconnectReady) {
                    require(owner.containerMenu.getCarried().isEmpty() && clicks.isEmpty(), "Reconnect requires no pending native item movement");
                    closeScreen();
                    require(owner.containerMenu == owner.inventoryMenu && client.screen == null, "Native menus/screens must close before stable reconnect baseline");
                    reconnectReady = true; event("native-reconnect-ready", new JsonObject()); persist(); return;
                }
                if (!control.has("reconnectAllowed") || !control.get("reconnectAllowed").getAsBoolean()) return;
                require(owner.containerMenu == owner.inventoryMenu && client.screen == null, "Screens changed during the server's quiet baseline period");
                captureReconnectBaseline(control);
                firstConnection = connection; firstOwner = owner;
                reconnectStarted = true;
                client.level.disconnect(); client.disconnect(new TitleScreen());
                event("native-tcp-disconnect", connectionEvidence()); persist(); return;
            }
            if (!reconnectConnectSent) {
                require(!firstConnection.isConnected(), "Old owned TCP connection must be closed before reconnect");
                if (client.getConnection() != null || client.player != null || client.level != null || !(client.screen instanceof TitleScreen)) return;
                require(ClientResearchPresentation.view() == null && ClientResearchCatalogHandlers.acceptedViewForCurrentConnection() == null
                    && ClientAspectSnapshot.view() == null && !ClientJeiRecipeSnapshot.ready(),
                    "Disconnect must clear all actual connection-owned facades before reconnect");
                if (++reconnectTitleTicks < 20) return;
                JsonObject cleared = new JsonObject(); cleared.addProperty("disconnectedTitleTicks", reconnectTitleTicks);
                cleared.addProperty("researchOwnerCleared", true); cleared.addProperty("researchCatalogCleared", true);
                cleared.addProperty("aspectSnapshotCleared", true); cleared.addProperty("jeiRecipeSnapshotCleared", true);
                event("native-tcp-disconnect-facades-cleared", cleared);
                String address = "127.0.0.1:" + marker.get("port").getAsInt();
                reconnectConnectSent = true;
                ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address),
                    new ServerData("Private TCP bootstrap reconnect", address, ServerData.Type.OTHER), false, null);
                JsonObject row = new JsonObject(); row.addProperty("address", address); row.addProperty("nativeConnectScreen", true);
                event("native-tcp-reconnect-dispatched", row); persist(); return;
            }
            if (client.getConnection() == null || client.player == null || client.gameMode == null) return;
            Connection replacement = client.getConnection().getConnection(); LocalPlayer replacementOwner = client.player;
            require(replacement != firstConnection && replacementOwner != firstOwner, "Reconnect must replace the actual native TCP connection and LocalPlayer");
            connection = replacement; owner = replacementOwner;
            checkConnection();
            var view = ClientResearchPresentation.view();
            if (view == null || view.storedStage("FIRSTSTEPS") != 4 || !view.knowsStrict("FIRSTSTEPS")
                || ClientResearchCatalogHandlers.acceptedViewForCurrentConnection() == null || ClientAspectSnapshot.view() == null
                || !ClientJeiRecipeSnapshot.ready()) return;
            for (var scan : scans) if (!view.knowsLiteral(scan.getAsJsonObject().get("scanKey").getAsString())) return;
            // Inventory/warp/XP packets may follow the first valid received owner snapshot.
            // Wait for the actual native values; do not populate them from the baseline.
            if (!reconnectContentsMatch()) return;
            connectionEpoch = 2; reconnectProven = true;
            JsonObject row = connectionEvidence(); row.addProperty("connectionEpoch", connectionEpoch);
            row.addProperty("newConnectionIdentity", true); row.addProperty("newLocalPlayerIdentity", true);
            row.addProperty("firststepsStage", view.storedStage("FIRSTSTEPS")); row.addProperty("strictComplete", view.knowsStrict("FIRSTSTEPS"));
            row.addProperty("sevenGenericMarkers", true); row.addProperty("fullInventoryComponentsMatch", true);
            row.addProperty("knowledgeCanonicalMatch", true); row.addProperty("warpMatch", true);
            row.addProperty("selectedSlotAndExactXpMatch", true); row.addProperty("onlyPopupExcluded", true);
            row.addProperty("actualAcceptedS2C", true); event("actual-tcp-reconnect-owner-restored", row);
            observeOwner(); persist();
        }

        int count(Predicate<ItemStack> predicate) {
            int result = 0;
            for (int index = 0; index < owner.getInventory().getContainerSize(); index++) {
                ItemStack stack = owner.getInventory().getItem(index); if (predicate.test(stack)) result += stack.getCount();
            }
            return result;
        }

        void observeOwner() throws Exception {
            var view = ClientResearchPresentation.view();
            if (view == null) return;
            String key = connectionEpoch + "/" + view.digest() + "/" + view.readStateDigest();
            if (key.equals(lastSnapshot)) return;
            lastSnapshot = key;
            JsonObject row = new JsonObject(); row.addProperty("catalogDigest", view.digest());
            row.addProperty("firststepsStage", view.storedStage("FIRSTSTEPS")); row.addProperty("gotDream", view.knows("!gotdream"));
            row.addProperty("gotBook", view.knows("!gotthaumonomicon")); row.addProperty("actualAcceptedS2C", true);
            row.addProperty("strictFirststepsComplete", view.knowsStrict("FIRSTSTEPS"));
            row.addProperty("basicsRaw", view.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, "BASICS"));
            row.addProperty("connectionEpoch", connectionEpoch);
            snapshots.add(row.deepCopy()); event("accepted-owner-snapshot", row);
        }

        void screenshot(String name) throws Exception {
            Path file = TcpBootstrapAuditIO.safePath(directory, directory.resolve("screenshots").resolve(name));
            require(!Files.exists(file), "Private screenshot must be new");
            screenshotPending++;
            Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> client.execute(() -> {
                try {
                    require(Files.isRegularFile(file) && Files.size(file) > 1024, "Native screenshot did not finish");
                    screenshots.add(file.toString()); JsonObject row = new JsonObject(); row.addProperty("path", file.toString());
                    event("native-screenshot", row); screenshotPending--;
                } catch (Exception failure) { fail(failure.toString()); }
            }));
        }

        Object field(Object target, String name) throws Exception {
            Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
        }

        Object invokeReadOnly(Object target, String name) throws Exception {
            Method method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(target);
        }

        JsonObject connectionEvidence() {
            JsonObject result = new JsonObject(); result.addProperty("memory", connection.isMemoryConnection());
            result.addProperty("integratedServer", client.hasSingleplayerServer()); result.addProperty("ownerUuid", owner.getUUID().toString());
            var remote = (InetSocketAddress) connection.getRemoteAddress(); result.addProperty("remoteHost", remote.getAddress().getHostAddress());
            result.addProperty("remotePort", remote.getPort()); result.addProperty("connected", connection.isConnected()); return result;
        }

        JsonObject state() {
            JsonObject result = TcpBootstrapAuditIO.identity(marker); result.addProperty("status", sealed ? "completed" : "running");
            result.addProperty("phase", phase); result.addProperty("clientTick", ticks); result.addProperty("observedUtc", Instant.now().toString());
            if (connection != null) {
                result.add("connection", connectionEvidence()); result.addProperty("ownerUuid", owner.getUUID().toString());
                result.addProperty("firststepsStage", ThaumcraftCapabilities.getKnowledge(owner).getResearchStage("FIRSTSTEPS"));
            }
            result.addProperty("dustTakes", dustTakes); result.addProperty("pendingClicks", clicks.size());
            result.addProperty("thaumometerTaken", thaumometerTaken);
            result.addProperty("thaumometerVisBefore", thaumometerVisBefore); result.addProperty("thaumometerVisAfter", thaumometerVisAfter);
            result.addProperty("acceptedScans", scans.size()); result.addProperty("scanSentIndex", scanSentIndex);
            result.addProperty("scanAcceptedIndex", scanAcceptedIndex); result.addProperty("pendingScanKey", pendingScanKey);
            result.addProperty("reconnectReady", reconnectReady); result.addProperty("reconnectStarted", reconnectStarted);
            result.addProperty("reconnectProven", reconnectProven); result.addProperty("connectionEpoch", connectionEpoch);
            result.addProperty("screen", client.screen == null ? "none" : client.screen.getClass().getName()); return result;
        }

        void persist() throws Exception { TcpBootstrapAuditIO.writeState(directory, "client", state()); }

        void event(String action, JsonObject detail) throws Exception {
            JsonObject row = TcpBootstrapAuditIO.identity(marker); row.addProperty("action", action); row.addProperty("phase", phase);
            row.addProperty("clientTick", ticks); row.addProperty("observedUtc", Instant.now().toString()); row.add("detail", detail.deepCopy());
            actions.add(row.deepCopy()); TcpBootstrapAuditIO.appendEvent(directory, "client", row);
        }

        void finish() throws Exception {
            if (screenshotPending != 0) return;
            var view = ClientResearchPresentation.view();
            if (view == null || view.storedStage("FIRSTSTEPS") != 4) return;
            require(view.knowsStrict("FIRSTSTEPS"), "Final genuine S2C must expose completed FIRSTSTEPS stage4");
            require(view.knows("!gotdream") && view.knows("!gotthaumonomicon") && count(stack -> stack.is(ItemsTC.thaumonomicon)) == 1,
                "Native book acquisition must survive through received owner state");
            require(bookshelfUsed && workbenchUsed && browserClicked && pageClicked && dustTakes == 2
                && thaumometerTaken && thaumometerStep == 2 && fullPageClicked && fullScreenshot && scans.size() == 7 && scanAcceptedIndex == 6
                && pendingScanKey == null && reconnectProven && connectionEpoch == 2 && reconnectContentsMatch(),
                "All native client actions required before sealing");
            if (!client.level.getBlockState(new BlockPos(2, 65, 2)).isAir()
                || !client.level.getBlockState(new BlockPos(2, 65, 4)).is(BlocksTC.arcaneWorkbench)) return;
            require(count(stack -> stack.is(ItemsTC.salisMundus)) == 0, "Exactly two dust consumed in native survival path");
            require(count(stack -> stack.is(ItemsTC.thaumometer)) == 1
                && view.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, "BASICS") == completionRawBefore - 16,
                "Native paid thaumometer and exact observation debit must survive the second TCP owner");
            require(snapshots.size() >= 4 && screenshots.size() == 7, "Accepted owner snapshots and seven native screenshots required");
            JsonObject report = state(); report.addProperty("passed", true); report.addProperty("status", "completed");
            report.addProperty("actualTCP", true); report.addProperty("firststepsStage", 4);
            report.addProperty("clientInventoryStackWrites", false); report.addProperty("clientKnowledgeGrants", false);
            report.addProperty("fullSurvivalAcquisition", false); report.addProperty("thaumometerOrFullFirststepsTested", true);
            report.addProperty("basicsRawBeforeCompletion", completionRawBefore);
            report.addProperty("basicsRawAfterCompletion", view.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, "BASICS"));
            report.addProperty("knowledgeNbt", ThaumcraftCapabilities.getKnowledge(owner).serializeNBT().toString());
            report.addProperty("warpNbt", ThaumcraftCapabilities.getWarp(owner).serializeNBT().toString());
            report.addProperty("inventoryNbt", owner.getInventory().save(new ListTag()).toString());
            report.addProperty("selectedSlot", owner.getInventory().selected); report.addProperty("totalExperience", owner.totalExperience);
            report.addProperty("experienceLevel", owner.experienceLevel); report.addProperty("experienceProgress", owner.experienceProgress);
            report.addProperty("experienceProgressBits", Float.floatToIntBits(owner.experienceProgress));
            report.add("reconnectBaseline", reconnectBaseline.deepCopy()); report.add("nativeScans", scans.deepCopy());
            report.add("actions", actions.deepCopy()); report.add("acceptedOwnerSnapshots", snapshots.deepCopy()); report.add("screenshots", screenshots.deepCopy());
            TcpBootstrapAuditIO.writeTerminal(directory, "client", report); sealed = true; persist();
            Thaumcraft.log.info("Private native TCP bootstrap client sealed paid FIRSTSTEPS stage4 on second TCP owner; waiting for matching server terminal");
        }

        void fail(String detail) {
            if (stopped) return;
            try {
                if (!sealed) {
                    JsonObject report = TcpBootstrapAuditIO.identity(marker); report.addProperty("status", "failed");
                    report.addProperty("passed", false); report.addProperty("phase", phase); report.addProperty("failure", detail);
                    report.addProperty("clientTick", ticks); report.add("actions", actions.deepCopy());
                    TcpBootstrapAuditIO.writeTerminal(directory, "client", report);
                }
            } catch (Exception writeFailure) { Thaumcraft.log.error("Unable to save private TCP bootstrap client failure", writeFailure); }
            stopped = true; client.stop();
        }
    }
}
