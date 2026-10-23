package thaumcraft.client.lib;

import static thaumcraft.common.lib.validation.TcpAlchemyAuditIO.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.client.gui.GuiResearchBrowser;
import thaumcraft.client.gui.GuiResearchPage;
import thaumcraft.common.container.ContainerArcaneWorkbench;
import thaumcraft.common.container.ContainerResearchTable;
import thaumcraft.common.container.ContainerSmelter;
import thaumcraft.common.items.consumables.ItemPhial;
import thaumcraft.common.items.resources.ItemCrystalEssence;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchCosts;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchRecipes;
import thaumcraft.common.lib.research.LegacyScanIdentity;
import thaumcraft.common.lib.utils.ScanTargeting;
import thaumcraft.common.lib.validation.TcpAlchemyScenario;
import thaumcraft.common.lib.validation.TcpAlchemyScenario.Action;
import thaumcraft.common.lib.validation.TcpBootstrapReloadAudit;
import thaumcraft.common.tiles.crafting.TileCrucible;
import thaumcraft.common.tiles.crafting.TileInfusionMatrix;
import thaumcraft.common.tiles.crafting.TilePedestal;
import thaumcraft.common.tiles.essentia.TileAlembic;
import thaumcraft.common.tiles.essentia.TileJarFillable;
import thaumcraft.common.tiles.essentia.TileSmelter;

/** Private default-off native TCP driver. It moves actual acquired inventory only through
 * ordinary menu packets, uses native item/block interactions and the original research
 * screens. Materials, arena blocks, scan targets and teleports are explicit server fixtures.
 * This controller never writes item stacks, world blocks or owner research/knowledge.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ClientTcpAlchemyAudit {
    private static Run active;
    private static boolean stopped;
    private ClientTcpAlchemyAudit() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || stopped) return;
        Minecraft client = Minecraft.getInstance();
        try { if (active == null) active = new Run(client); active.step(); }
        catch (Exception failure) {
            Thaumcraft.log.error("Private TCP alchemy client failed", failure);
            if (active == null) { stopped = true; client.stop(); } else active.fail(failure.toString());
        }
    }

    private record Click(int menu, int slot, int button, ClickType type) {}

    private static final class Run {
        private static final int MENU_OPEN_SETTLE_TICKS = 8, MENU_OPEN_RETRY_TICKS = 40, MAX_MENU_OPEN_RETRIES = 8;
        final Minecraft client;
        final Path directory;
        final JsonObject marker;
        final String mode;
        final ClientAlchemyResearchDriver research;
        final List<Action> reviewed;
        final JsonArray actions = new JsonArray(), screenshots = new JsonArray(), ownerSnapshots = new JsonArray();
        final JsonArray reloadedWorldObservations = new JsonArray();
        final JsonArray nativeStorageReceipts = new JsonArray();
        final ArrayDeque<Click> clicks = new ArrayDeque<>();
        final long started = System.nanoTime();
        Connection connection, firstConnection;
        LocalPlayer owner, firstOwner;
        Action current;
        String phase = "login", lastOwnerDigest;
        int actionIndex = -1, actionStep, heldTicks, settleTicks, uses, craftBefore, craftTakes, menuOpenRetries;
        int screenshotPending, connectionEpoch = 1, reconnectTitleTicks;
        int storageTicks, storageOpenRetries, storageTransactions;
        long ticks, actionStarted = started;
        boolean actionComplete, filled, takePending, sealed;
        boolean checkpointObserved, loadedJobObserved, resumedOutputPickedUp;
        boolean reloadedWorldObserved;
        boolean reconnectReady, reconnectStarted, reconnectConnectSent, reconnectProven;
        String scanMarker;
        String craftWaitContents, publishedResearchSupplyGate;
        int craftWaitHighestVis = -1;
        long craftWaitProgressAt;
        JsonObject reconnectBaseline;
        JsonObject nativeStorage;
        JsonObject craftWait;

        Run(Minecraft client) throws Exception {
            this.client = client;
            require(client.isSameThread() && !ClientJeiReviewScenario.enabled(), "Private alchemy initializes exclusively on native client MAIN");
            require(!Boolean.getBoolean("thaumcraft.tcpBootstrapAudit") && !Boolean.getBoolean("thaumcraft.tcpBootstrapReloadAudit"),
                "Private alchemy and FIRSTSTEPS cohorts must be exclusive");
            directory = directory(client.gameDirectory.toPath(), "client");
            marker = validateDirectory(directory, "client");
            mode = string(marker, "auditPhase");
            require(List.of("generate", "resume", "reload").contains(mode), "Unknown private alchemy cohort mode");
            require(USERNAME.equals(client.getUser().getName()), "Only the private inherited offline owner is allowed");
            client.options.pauseOnLostFocus = false;
            research = new ClientAlchemyResearchDriver(client);
            reviewed = TcpAlchemyScenario.generateActions();
            event("client-initialized", new JsonObject()); persist();
        }

        void step() throws Exception {
            ticks++;
            require(client.isSameThread() && !client.hasSingleplayerServer() && client.getSingleplayerServer() == null,
                "Native alchemy requires a real dedicated TCP server");
            require(System.nanoTime() - started < TimeUnit.MINUTES.toNanos(40)
                && System.nanoTime() - actionStarted < TimeUnit.MINUTES.toNanos(12), "Timed out in private alchemy action " + actionIndex);
            JsonObject terminal = readPeer(directory, marker, "server", true);
            if (terminal != null) {
                require(sealed && terminal.has("passed") && terminal.get("passed").getAsBoolean(), "Server alchemy terminal rejected or arrived before client acceptance");
                stopped = true; client.stop(); return;
            }
            JsonObject control = readPeer(directory, marker, "server", false);
            if (control == null) return;
            require(!"failed".equals(string(control, "status")), "Private server alchemy failed");
            String next = string(control, "phase");
            if (!next.equals(phase)) { phase = next; event("server-phase", control); persist(); }
            if (reconnectStarted && !reconnectProven) { reconnect(control); return; }
            if (connection == null) {
                if (client.getConnection() == null || client.player == null || client.gameMode == null) return;
                connection = client.getConnection().getConnection(); owner = client.player;
                checkConnection(); event("actual-tcp-play-owner", connectionEvidence());
            }
            checkConnection();
            if (sealed) return;
            observeOwner();
            if ("generate".equals(mode)) generate(control);
            else if ("resume".equals(mode)) resume(control);
            else reload(control);
            drainResearch();
            if (ticks % 100 == 0) persist();
        }

        void checkConnection() {
            require(client.getConnection() != null && client.player == owner && client.getConnection().getConnection() == connection
                && connection.isConnected() && !connection.isMemoryConnection()
                && connection.getRemoteAddress() instanceof InetSocketAddress remote && remote.getAddress().isLoopbackAddress()
                && remote.getPort() == marker.get("port").getAsInt() && OWNER_UUID.equals(owner.getUUID())
                && USERNAME.equals(owner.getGameProfile().getName()) && client.gameMode != null
                && client.gameMode.getPlayerMode() == GameType.SURVIVAL && !owner.getAbilities().instabuild,
                "Private alchemy requires one real remote survival PLAY owner");
        }

        void generate(JsonObject control) throws Exception {
            if (!control.has("actionIndex")) return;
            int next = control.get("actionIndex").getAsInt();
            require(next >= 0 && next <= reviewed.size(), "Server action index escaped the reviewed finite scenario");
            if (next == reviewed.size()) { checkpoint(control); return; }
            if (!control.has("actionPrepared") || !control.get("actionPrepared").getAsBoolean()) return;
            Action candidate = reviewed.get(next);
            require(candidate.id().equals(string(control, "actionId"))
                && candidate.kind().name().equals(string(control, "actionKind")), "Server action differs from the reviewed native scenario");
            if (next != actionIndex) {
                require(next == actionIndex + 1 && (current == null || actionComplete), "Server skipped an unfinished native client action");
                require(clicks.isEmpty() && owner.containerMenu.getCarried().isEmpty(), "Native action changed with pending inventory movement");
                actionIndex = next; current = candidate;
                actionStep = heldTicks = settleTicks = uses = craftTakes = menuOpenRetries = 0;
                actionComplete = filled = takePending = false; scanMarker = null; actionStarted = System.nanoTime();
                craftWait = null; craftWaitContents = null; craftWaitHighestVis = -1; craftWaitProgressAt = 0;
                JsonObject row = new JsonObject(); row.addProperty("index", next); row.addProperty("id", candidate.id());
                row.addProperty("kind", candidate.kind().name()); event("reviewed-native-action", row); persist();
            }
            if (actionComplete) return;
            switch (current.kind()) {
                case RESEARCH -> nativeResearch(current);
                case THEORY -> nativeTheory(current, control);
                case CRAFT -> craft(current);
                case USE_BLOCK -> useReviewedBlock(current);
                case SMELTER_LOAD -> smelter(current);
                case WAIT_TRANSFER -> waitTransfer(current);
                case PHIAL_EXTRACT -> phial(current, false);
                case PHIAL_INSERT -> phial(current, true);
                case PEDESTAL_PLACE -> pedestal(current);
                case MATRIX_ACTIVATE -> matrix(current, false);
                case MATRIX_START -> matrix(current, true);
                case SCAN_BLOCK -> scan(current, false);
                case SCAN_ITEM -> scan(current, true);
                case OUTPUT_PICKUP -> outputPickup(current);
                case CHECKPOINT -> checkpoint(control);
            }
        }

        void complete(String reason) throws Exception {
            if (actionComplete) return;
            require(clicks.isEmpty() && owner.containerMenu.getCarried().isEmpty(), "Native action completed with a carried or queued item");
            actionComplete = true;
            JsonObject row = new JsonObject(); row.addProperty("reason", reason); row.addProperty("actionIndex", actionIndex);
            row.addProperty("actionId", current == null ? "none" : current.id());
            row.addProperty("actualAcceptedS2C", true); event("native-action-completed", row); persist();
        }

        void nativeResearch(Action action) throws Exception {
            var view = ClientResearchPresentation.view();
            if (view == null || !ClientResearchCatalogHandlers.readyForCurrentConnection()) return;
            if (!(client.screen instanceof GuiResearchBrowser) && !(client.screen instanceof GuiResearchPage)) {
                if (!selectHeld(stack -> stack.is(ItemsTC.thaumonomicon))) return;
                closeScreen(); useItem("native-thaumonomicon-open"); return;
            }
            if (research.tickResearch(action.research(), TcpAlchemyScenario.resultingStage(action))) complete("native research screen and received owner transition");
        }

        void nativeTheory(Action action, JsonObject control) throws Exception {
            if (nativeStorage != null) { storeTheoryMaterials(action, control); return; }
            if (!(owner.containerMenu instanceof ContainerResearchTable)) {
                closeScreen();
                if (!inReach(action.position())) return;
                if (actionStep == 0 || ++settleTicks >= 40) { useBlock(action.position(), "native-research-table-open"); actionStep = 1; settleTicks = 0; }
                return;
            }
            var menu = (ContainerResearchTable) owner.containerMenu;
            if (menu.theoryReady() && menu.theoryOwned() && menu.theoryData() == null
                && menu.getCarried().isEmpty() && research.canSuspendTheoryAtBoundary() && ordinaryEmptySlots() < 8) {
                beginTheoryStorage(action); return;
            }
            if (research.tickTheory(action.research(), action.amount())) {
                // The helper can accept the final award and become quiescent in this tick.
                // Preserve ordinary working space before the next real research/craft action.
                if (ordinaryEmptySlots() < 8) { beginTheoryStorage(action); return; }
                closeScreen(); complete("original native research-table theory and received knowledge");
            }
        }

        void beginTheoryStorage(Action action) throws Exception {
            require(owner.containerMenu instanceof ContainerResearchTable table && table.theoryReady() && table.theoryOwned()
                && table.theoryData() == null && table.getCarried().isEmpty() && research.canSuspendTheoryAtBoundary()
                && clicks.isEmpty(), "Theory storage requires a settled actual note boundary");
            nativeStorage = new JsonObject(); nativeStorage.addProperty("transactionId", "theory-storage-" + ++storageTransactions);
            nativeStorage.addProperty("actionIndex", actionIndex); nativeStorage.addProperty("phase", "opening");
            nativeStorage.addProperty("theoryCategory", action.research()); nativeStorage.add("position", vector(TcpAlchemyScenario.MATERIAL_CHEST));
            nativeStorage.addProperty("nativeQuickMoveCount", 0); nativeStorage.add("nativeClicks", new JsonArray());
            storageTicks = storageOpenRetries = 0; closeScreen(); event("native-theory-storage-requested", nativeStorage); persist();
        }

        void storeTheoryMaterials(Action action, JsonObject control) throws Exception {
            String storagePhase = string(nativeStorage, "phase");
            if (storagePhase.equals("await-server")) {
                require(owner.containerMenu == owner.inventoryMenu && owner.containerMenu.getCarried().isEmpty(),
                    "Native storage acceptance needs the actual chest menu closed");
                if (!string(nativeStorage, "transactionId").equals(string(control, "nativeStorageAcceptedId"))) return;
                nativeStorage.addProperty("actualServerAccepted", true); nativeStorageReceipts.add(nativeStorage.deepCopy());
                event("native-theory-storage-completed", nativeStorage); nativeStorage = null; actionStep = settleTicks = storageTicks = 0; persist(); return;
            }
            if (!(owner.containerMenu instanceof ChestMenu menu)) {
                require(storagePhase.equals("opening"), "Actual native storage menu vanished during item movement");
                if (owner.containerMenu != owner.inventoryMenu || !owner.containerMenu.getCarried().isEmpty() || !clicks.isEmpty()
                    || !inReach(TcpAlchemyScenario.MATERIAL_CHEST)) { storageTicks = 0; return; }
                int wait = storageOpenRetries == 0 ? MENU_OPEN_SETTLE_TICKS : MENU_OPEN_RETRY_TICKS;
                if (++storageTicks < wait) return;
                require(storageOpenRetries < MAX_MENU_OPEN_RETRIES, "Native theory storage menu did not open");
                JsonObject row = new JsonObject(); row.addProperty("transactionId", string(nativeStorage, "transactionId"));
                row.addProperty("attempt", ++storageOpenRetries); row.addProperty("waitNormalTicks", wait);
                event("native-theory-storage-open-attempt", row); useBlock(TcpAlchemyScenario.MATERIAL_CHEST, "native-theory-storage-open");
                storageTicks = 0; return;
            }
            require(menu.getContainer().getContainerSize() == 54 && menu.getRowCount() == 6 && menu.getCarried().isEmpty()
                && clicks.isEmpty(), "Only the actual empty-cursor double-chest menu may store reviewed materials");
            if (storagePhase.equals("opening")) {
                nativeStorage.addProperty("phase", "moving"); nativeStorage.addProperty("actualNativeMenuId", menu.containerId);
                nativeStorage.addProperty("actualNativeChestMenu", true); nativeStorage.addProperty("ordinaryEmptyBefore", ordinaryEmptySlots());
                nativeStorage.addProperty("inventoryBeforeNbt", inventorySnbt());
                nativeStorage.addProperty("chestBeforeNbt", chestSnbt(menu.getContainer()));
                event("native-theory-storage-menu-received", nativeStorage); screenshot(string(nativeStorage, "transactionId") + "-before.png");
                storageTicks = 0; persist(); return;
            }
            require(storagePhase.equals("moving"), "Unknown native theory storage phase");
            if (ordinaryEmptySlots() >= 8) {
                if (++storageTicks < 10) return;
                nativeStorage.addProperty("ordinaryEmptyAfter", ordinaryEmptySlots()); nativeStorage.addProperty("inventoryAfterNbt", inventorySnbt());
                nativeStorage.addProperty("chestAfterNbt", chestSnbt(menu.getContainer()));
                require(storageTotals(string(nativeStorage, "inventoryBeforeNbt"), string(nativeStorage, "chestBeforeNbt"))
                    .equals(storageTotals(string(nativeStorage, "inventoryAfterNbt"), string(nativeStorage, "chestAfterNbt"))),
                    "Native storage must conserve every actual typed inventory and chest item");
                screenshot(string(nativeStorage, "transactionId") + "-after.png"); closeScreen(); nativeStorage.addProperty("phase", "await-server");
                event("native-theory-storage-await-server", nativeStorage); persist(); return;
            }
            int moves = nativeStorage.get("nativeQuickMoveCount").getAsInt();
            if (++storageTicks < (moves == 0 ? MENU_OPEN_SETTLE_TICKS : 3)) return;
            int source = inventorySlot(menu, stack -> stack.is(ItemsTC.crystalEssence));
            require(source >= 54, "Native theory storage needs actual surplus crystal essence in ordinary inventory");
            ItemStack stack = menu.getSlot(source).getItem();
            require(chestCanFit(menu.getContainer(), stack), "Declared native storage chest cannot preserve another actual crystal stack");
            JsonObject row = new JsonObject(); row.addProperty("transactionId", string(nativeStorage, "transactionId"));
            row.addProperty("menuId", menu.containerId); row.addProperty("slot", source); row.addProperty("button", 0);
            row.addProperty("clickType", ClickType.QUICK_MOVE.name()); row.addProperty("stackBeforeNbt", stack.saveOptional(client.level.registryAccess()).toString());
            row.addProperty("nativeC2S", true); nativeStorage.getAsJsonArray("nativeClicks").add(row.deepCopy());
            nativeStorage.addProperty("nativeQuickMoveCount", moves + 1); click(new Click(menu.containerId, source, 0, ClickType.QUICK_MOVE));
            event("native-theory-storage-quick-move", row); storageTicks = 0;
        }

        int ordinaryEmptySlots() {
            int result = 0; for (ItemStack stack : owner.getInventory().items) if (stack.isEmpty()) result++; return result;
        }

        String inventorySnbt() { return owner.getInventory().save(new ListTag()).toString(); }

        String chestSnbt(Container container) {
            require(container.getContainerSize() == 54, "Native storage snapshot requires fifty-four actual slots");
            ListTag items = new ListTag();
            for (int index = 0; index < container.getContainerSize(); index++) {
                ItemStack stack = container.getItem(index); if (stack.isEmpty()) continue;
                CompoundTag value = (CompoundTag) stack.save(client.level.registryAccess()); value.putByte("Slot", (byte) index); items.add(value);
            }
            CompoundTag value = new CompoundTag(); value.putInt("Size", 54); value.put("Items", items); return value.toString();
        }

        boolean chestCanFit(Container container, ItemStack stack) {
            int room = 0;
            for (int index = 0; index < container.getContainerSize(); index++) {
                ItemStack stored = container.getItem(index);
                if (stored.isEmpty()) room += Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
                else if (ItemStack.isSameItemSameComponents(stored, stack)) room += Math.max(0, Math.min(container.getMaxStackSize(), stored.getMaxStackSize()) - stored.getCount());
            }
            return room >= stack.getCount();
        }

        java.util.Map<CompoundTag, Integer> storageTotals(String inventory, String chest) throws Exception {
            java.util.Map<CompoundTag, Integer> result = new java.util.HashMap<>();
            List<ListTag> sources = List.of(TagParser.parseTag("{Items:" + inventory + "}").getList("Items", Tag.TAG_COMPOUND),
                TagParser.parseTag(chest).getList("Items", Tag.TAG_COMPOUND));
            for (ListTag items : sources) for (Tag item : items) {
                CompoundTag value = ((CompoundTag) item).copy(); int count = value.getInt("count");
                require(count > 0, "Native storage snapshot contains a nonpositive actual stack"); value.remove("Slot"); value.remove("count");
                result.merge(value, count, Integer::sum);
            }
            return result;
        }

        void craft(Action action) throws Exception {
            if (actionStep == 0) {
                closeScreen(); if (!inReach(action.position())) return;
                if (++settleTicks < MENU_OPEN_SETTLE_TICKS) return;
                useBlock(action.position(), "native-crafting-menu-open"); actionStep = 1; settleTicks = 0;
                craftBefore = count(stack -> item(stack, action.item())); return;
            }
            AbstractContainerMenu menu = owner.containerMenu;
            boolean expectedArcane = action.position().equals(TcpAlchemyScenario.WORKBENCH);
            require(expectedArcane || action.position().equals(TcpAlchemyScenario.CRAFT), "Unreviewed native crafting menu position");
            if (!(expectedArcane ? menu instanceof ContainerArcaneWorkbench : menu instanceof CraftingMenu)) {
                require(!filled && !takePending, "Native crafting menu changed after actual matrix movement");
                retryMenuOpen(action, expectedArcane ? "arcane-workbench" : "crafting-table", "native-crafting-menu-open-retry"); return;
            }
            if (actionStep == 1) { actionStep = 2; settleTicks = 0; }
            int last = menu instanceof ContainerArcaneWorkbench ? 15 : 9;
            if (!filled) {
                require(menu.getCarried().isEmpty(), "A genuine empty crafting cursor is required");
                for (int slot = 1; slot <= last; slot++) require(menu.getSlot(slot).getItem().isEmpty(), "Fresh reviewed crafting matrix required");
                for (var ingredient : action.ingredients()) {
                    require(ingredient.slot() >= 0 && ingredient.slot() < last, "Ingredient slot outside reviewed native matrix");
                    moveIngredient(menu, ingredient.slot() + 1, ingredient.count(), stack -> ingredient(stack, ingredient.item(), ingredient.aspect()));
                }
                filled = true;
            }
            if (!clicks.isEmpty()) { if (ticks % 3 == 0) click(clicks.remove()); return; }
            if (!menu.getCarried().isEmpty()) {
                require(item(menu.getCarried(), action.item()), "Native crafting output cursor differs from reviewed real recipe");
                int destination = inventorySlot(menu, ItemStack::isEmpty);
                require(destination >= 0, "Native crafting output needs an actual empty inventory slot");
                click(new Click(menu.containerId, destination, 0, ClickType.PICKUP)); takePending = false; return;
            }
            if (count(stack -> item(stack, action.item())) >= craftBefore + action.amount()) {
                for (int slot = 1; slot <= last; slot++) if (!menu.getSlot(slot).getItem().isEmpty()) {
                    click(new Click(menu.containerId, slot, 0, ClickType.QUICK_MOVE)); return;
                }
                if (++settleTicks < 10) return;
                closeScreen(); complete("native paid crafting result, destination and empty matrix"); return;
            }
            if (takePending || !item(menu.getSlot(0).getItem(), action.item())) { observeCraftWait(action, menu); return; }
            craftWait = null;
            if (++settleTicks < 10) return;
            JsonObject row = new JsonObject(); row.addProperty("expectedOutput", action.item());
            row.addProperty("result", menu.getSlot(0).getItem().toString()); row.addProperty("take", ++craftTakes);
            if (menu instanceof ContainerArcaneWorkbench arcane) row.addProperty("availableVis", arcane.availableVis());
            screenshot("craft-" + actionIndex + "-" + craftTakes + ".png");
            click(new Click(menu.containerId, 0, 0, ClickType.PICKUP));
            event("native-reviewed-result-take", row); takePending = true; settleTicks = 0;
        }

        void observeCraftWait(Action action, AbstractContainerMenu menu) throws Exception {
            if (ticks % 100 != 0 && craftWait != null) return;
            JsonObject snapshot = new JsonObject(); snapshot.addProperty("actionId", action.id());
            snapshot.addProperty("actualNativeMenuId", menu.containerId); snapshot.addProperty("actualNativeStateId", menu.getStateId());
            snapshot.addProperty("expectedOutput", action.item()); snapshot.addProperty("takePending", takePending);
            snapshot.addProperty("nativeClientTick", ticks); snapshot.addProperty("receivedNativeMenu", true);
            snapshot.addProperty("cursorNbt", menu.getCarried().saveOptional(client.level.registryAccess()).toString());
            JsonArray slots = new JsonArray(); int last = menu instanceof ContainerArcaneWorkbench ? 15 : 9;
            for (int index = 0; index <= last; index++) {
                JsonObject slot = new JsonObject(); slot.addProperty("menuSlot", index);
                slot.addProperty("role", index == 0 ? "result" : index <= 9 ? "crafting" : "crystal");
                slot.addProperty("stackNbt", menu.getSlot(index).getItem().saveOptional(client.level.registryAccess()).toString());
                slots.add(slot);
            }
            snapshot.add("actualSlots", slots);
            int vis = -1;
            if (menu instanceof ContainerArcaneWorkbench arcane) {
                vis = arcane.availableVis(); snapshot.addProperty("availableVis", vis);
                var recipe = arcane.displayedArcaneRecipe();
                if (recipe != null) {
                    JsonObject required = new JsonObject();
                    for (var holder : client.level.getRecipeManager().getAllRecipesFor(ArcaneWorkbenchRecipes.TYPE))
                        if (holder.value() == recipe) { required.addProperty("recipeId", holder.id().toString()); break; }
                    required.addProperty("research", recipe.research()); required.addProperty("vis", recipe.vis());
                    float discount = ArcaneWorkbenchCosts.discount(owner);
                    required.addProperty("actualOwnerDiscount", discount);
                    required.addProperty("effectiveVisCost", ArcaneWorkbenchCosts.visCost(recipe.vis(), discount));
                    required.addProperty("actualReceivedOwnerResearchKnown", ThaumcraftCapabilities.getKnowledge(owner).isResearchKnown(recipe.research()));
                    JsonArray crystals = new JsonArray(); for (int amount : recipe.crystals()) crystals.add(amount);
                    required.add("crystalCosts", crystals); required.addProperty("actualNativeGridAndCrystalsMatch", true);
                    snapshot.add("actualDisplayedArcaneRecipe", required);
                }
            }
            String contents = slots.toString() + snapshot.get("cursorNbt") + snapshot.get("actualDisplayedArcaneRecipe") + takePending;
            long now = System.nanoTime();
            if (!contents.equals(craftWaitContents) || vis > craftWaitHighestVis) {
                craftWaitContents = contents; craftWaitHighestVis = vis; craftWaitProgressAt = now;
            }
            long unchangedSeconds = TimeUnit.NANOSECONDS.toSeconds(now - craftWaitProgressAt);
            snapshot.addProperty("highestReceivedVis", craftWaitHighestVis); snapshot.addProperty("unchangedSeconds", unchangedSeconds);
            craftWait = snapshot;
            event("native-crafting-wait-observed", snapshot); persist();
            require(unchangedSeconds < 180, "Native crafting result made no received slot/recipe/research or higher-vis progress for 180 seconds: " + action.id());
        }

        void smelter(Action action) throws Exception {
            if (actionStep == 0) {
                closeScreen(); if (!inReach(action.position())) return;
                if (++settleTicks < MENU_OPEN_SETTLE_TICKS) return;
                useBlock(action.position(), "native-smelter-open"); actionStep = 1; settleTicks = 0; return;
            }
            if (!(owner.containerMenu instanceof ContainerSmelter menu)) {
                require(!filled, "Native smelter menu changed after actual input movement");
                retryMenuOpen(action, "essentia-smelter", "native-smelter-open-retry"); return;
            }
            if (actionStep == 1) { actionStep = 2; settleTicks = 0; }
            if (!filled) {
                require(menu.getCarried().isEmpty(), "Native smelter cursor must start empty");
                for (var ingredient : action.ingredients()) {
                    require(ingredient.slot() == 0 || ingredient.slot() == 1, "Smelter only accepts reviewed input/fuel slots");
                    moveIngredient(menu, ingredient.slot(), ingredient.count(), stack -> ingredient(stack, ingredient.item(), ingredient.aspect()));
                }
                filled = true;
            }
            if (!clicks.isEmpty()) { if (ticks % 3 == 0) click(clicks.remove()); return; }
            if (!menu.getCarried().isEmpty() || menu.burnScaled(100) <= 0 || ++settleTicks < 15) return;
            screenshot("smelter-" + actionIndex + ".png");
            JsonObject row = new JsonObject(); row.addProperty("burnScaled", menu.burnScaled(100)); row.addProperty("cookScaled", menu.cookScaled(100));
            row.addProperty("visScaled", menu.visScaled(256)); row.addProperty("actualNativeContainerData", true); event("native-smelter-burning", row);
            closeScreen(); complete("native smelter input/fuel menus and synchronized burning data");
        }

        void retryMenuOpen(Action action, String expectedMenu, String nativeAction) throws Exception {
            // The file-backed fixture signal can precede native teleport acknowledgement.
            // Only a still-closed real menu may retry; accepted menus own every later click.
            if (owner.containerMenu != owner.inventoryMenu || !owner.containerMenu.getCarried().isEmpty()
                || !clicks.isEmpty() || filled || takePending || !inReach(action.position())) { settleTicks = 0; return; }
            if (++settleTicks < MENU_OPEN_RETRY_TICKS) return;
            require(menuOpenRetries < MAX_MENU_OPEN_RETRIES, "Native server never opened reviewed menu " + expectedMenu);
            JsonObject row = new JsonObject(); row.addProperty("retry", ++menuOpenRetries);
            row.addProperty("waitNormalTicks", MENU_OPEN_RETRY_TICKS); row.addProperty("expectedMenu", expectedMenu);
            row.addProperty("actualMenuId", owner.containerMenu.containerId); row.addProperty("actualInventoryMenu", true);
            row.addProperty("actualCursorEmpty", true); row.addProperty("pendingInventoryClicks", clicks.size());
            event("native-menu-open-retry", row); useBlock(action.position(), nativeAction); settleTicks = 0;
        }

        void useReviewedBlock(Action action) throws Exception {
            closeScreen();
            if (actionStep == 0) {
                if (!selectHeld(stack -> item(stack, action.item())) || !inReach(action.position())) return;
                if (client.level.getBlockEntity(action.position()) instanceof TileCrucible crucible
                    && !action.item().equals("minecraft:water_bucket") && !crucible.boiling()) return;
                if (uses == 0) craftBefore = count(stack -> item(stack, action.item()));
                useBlock(action.position(), "native-reviewed-block-use"); uses++; actionStep = 1; settleTicks = 0; return;
            }
            if (count(stack -> item(stack, action.item())) > craftBefore - uses) {
                // The source scribing tool is a reusable interaction, not a paid ingredient.
                if (!action.item().equals("thaumcraft:scribing_tools") || ++settleTicks < 20) return;
            }
            if (uses < action.amount()) { actionStep = 0; return; }
            if (++settleTicks < 5) return;
            complete("native block use and received inventory payment");
        }

        void waitTransfer(Action action) throws Exception {
            closeScreen();
            lookAt(Vec3.atCenterOf(action.position()));
            if (!(client.level.getBlockEntity(action.position()) instanceof TileJarFillable jar)
                || jar.amount < action.amount()) return;
            if (action.research() != null && !action.research().isBlank()
                && (jar.aspect == null || !jar.aspect.getTag().equals(action.research()))) return;
            if (++settleTicks < 5) return;
            screenshot("essentia-jar-" + actionIndex + ".png");
            JsonObject row = new JsonObject(); row.addProperty("amount", jar.amount);
            row.addProperty("aspect", jar.aspect == null ? "none" : jar.aspect.getTag()); row.addProperty("nativeBlockEntityS2C", true);
            event("native-pipe-jar-transfer-observed", row); complete("native synchronized pipe destination jar");
        }

        void phial(Action action, boolean insert) throws Exception {
            closeScreen();
            if (actionStep == 0) {
                Predicate<ItemStack> held = insert ? stack -> stack.is(ItemPhial.filledVariant) : stack -> stack.is(ItemPhial.emptyVariant);
                if (!selectHeld(held) || !inReach(action.position())) return;
                if (insert) {
                    var contents = ((ItemPhial) owner.getMainHandItem().getItem()).getAspects(owner.getMainHandItem());
                    require(contents != null && contents.size() == 1 && contents.visSize() == 10, "Native filled phial must contain exactly ten actual units");
                    if (action.research() != null && !action.research().isBlank()) require(
                        contents.getAspects()[0].getTag().equals(action.research()), "Native filled phial aspect differs from reviewed transfer");
                }
                int amount = aspectAmount(action.position());
                if (!insert && amount < 10) return;
                if (!insert && action.research() != null && !action.research().isBlank()
                    && !action.research().equals(aspectType(action.position()))) return;
                craftBefore = amount; uses = count(held); useBlock(action.position(), insert ? "native-phial-insert" : "native-phial-extract");
                actionStep = 1; settleTicks = 0; return;
            }
            int after = aspectAmount(action.position());
            boolean received = insert ? count(stack -> stack.is(ItemPhial.emptyVariant)) > 0 && after >= craftBefore + 10
                : count(stack -> stack.is(ItemPhial.filledVariant)) > 0 && after <= craftBefore - 10;
            if (!received || ++settleTicks < 5) return;
            JsonObject row = new JsonObject(); row.addProperty("before", craftBefore); row.addProperty("after", after);
            row.addProperty("actualTenUnitItemConversion", true); row.addProperty("nativeBlockEntityS2C", true);
            row.addProperty("aspectAfter", aspectType(action.position()));
            event(insert ? "native-phial-insert-received" : "native-phial-extract-received", row);
            screenshot("phial-" + actionIndex + ".png"); complete("native ten-unit phial and synchronized container");
        }

        int aspectAmount(BlockPos position) {
            var tile = client.level.getBlockEntity(position);
            return tile instanceof TileJarFillable jar ? jar.amount : tile instanceof TileAlembic alembic ? alembic.amount : -1;
        }

        String aspectType(BlockPos position) {
            var tile = client.level.getBlockEntity(position);
            Aspect aspect = tile instanceof TileJarFillable jar ? jar.aspect : tile instanceof TileAlembic alembic ? alembic.aspect : null;
            return aspect == null ? "none" : aspect.getTag();
        }

        void pedestal(Action action) throws Exception {
            closeScreen();
            if (!(client.level.getBlockEntity(action.position()) instanceof TilePedestal pedestal)) return;
            if (actionStep == 0) {
                if (!pedestal.isEmpty() || !selectHeld(stack -> ingredient(stack, action.item(), action.research())) || !inReach(action.position())) return;
                craftBefore = count(stack -> ingredient(stack, action.item(), action.research())); useBlock(action.position(), "native-infusion-pedestal-place"); actionStep = 1; return;
            }
            if (!ingredient(pedestal.getSyncedStackInSlot(0), action.item(), action.research())
                || count(stack -> ingredient(stack, action.item(), action.research())) != craftBefore - 1) return;
            JsonObject row = new JsonObject(); row.addProperty("position", action.position().toShortString());
            row.addProperty("stack", pedestal.getSyncedStackInSlot(0).toString()); row.addProperty("nativeBlockEntityS2C", true);
            event("native-infusion-pedestal-received", row); complete("native pedestal placement and received one-item debit");
        }

        void matrix(Action action, boolean start) throws Exception {
            closeScreen();
            if (!(client.level.getBlockEntity(action.position()) instanceof TileInfusionMatrix matrix)) return;
            if (actionStep == 0) {
                if (!selectHeld(stack -> item(stack, action.item())) || !inReach(action.position())) return;
                require(start ? matrix.active && !matrix.crafting : !matrix.active && !matrix.crafting,
                    "Native matrix interaction must start from the reviewed inactive/active state");
                useBlock(action.position(), start ? "native-infusion-matrix-start" : "native-infusion-matrix-activate"); actionStep = 1; return;
            }
            if (!matrix.active || (start && !matrix.crafting)) return;
            JsonObject row = new JsonObject(); row.addProperty("active", matrix.active); row.addProperty("crafting", matrix.crafting);
            row.addProperty("nativeBlockEntityS2C", true); event("native-matrix-state-received", row);
            screenshot((start ? "infusion-start-" : "infusion-active-") + actionIndex + ".png");
            complete("native caster matrix interaction and actual synchronized state");
        }

        void scan(Action action, boolean entityTarget) throws Exception {
            closeScreen();
            if (scanMarker != null) {
                var view = ClientResearchPresentation.view();
                if (view != null && view.knowsLiteral(scanMarker)) {
                    JsonObject row = new JsonObject(); row.addProperty("marker", scanMarker); row.addProperty("actualAcceptedS2C", true);
                    event("native-alchemy-scan-received", row); complete("natural scan marker from actual owner S2C");
                }
                return;
            }
            if (!selectHeld(stack -> stack.is(ItemsTC.thaumometer))) { settleTicks = 0; return; }
            Object target;
            Vec3 aim;
            String key;
            if (entityTarget) {
                String scanAspect = action.research() != null && action.research().startsWith("!")
                    && Aspect.getAspect(action.research().substring(1)) != null ? action.research().substring(1) : null;
                var entities = client.level.getEntitiesOfClass(ItemEntity.class, new AABB(action.position()).inflate(1.5),
                    entity -> ingredient(entity.getItem(), action.item(), scanAspect));
                if (entities.size() != 1) { settleTicks = 0; return; }
                var entity = entities.getFirst(); target = entity; aim = entity.getBoundingBox().getCenter();
                key = action.research() == null || action.research().isBlank() ? LegacyScanIdentity.itemResearchKey(entity.getItem()) : action.research();
            } else {
                if (!BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(action.position()).getBlock()).toString().equals(action.item())) return;
                target = action.position(); aim = Vec3.atCenterOf(action.position());
                key = action.research() == null || action.research().isBlank()
                    ? LegacyScanIdentity.itemResearchKey(new ItemStack(client.level.getBlockState(action.position()).getBlock())) : action.research();
            }
            Vec3 delta = aim.subtract(owner.getEyePosition());
            if (delta.length() > (entityTarget ? 8D : ScanTargeting.LEGACY_BLOCK_REACH - .1D)) { settleTicks = 0; return; }
            owner.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90D));
            owner.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
            Object actual = ScanTargeting.scanTarget(owner);
            if (!(entityTarget ? actual == target : target.equals(actual))) { settleTicks = 0; return; }
            if (++settleTicks < 8) return;
            var view = ClientResearchPresentation.view();
            if (view == null) return;
            require(!view.knowsLiteral(key), "Reviewed native scan marker must be fresh");
            useItem("native-alchemy-thaumometer-scan"); scanMarker = key;
            JsonObject row = new JsonObject(); row.addProperty("marker", key); row.addProperty("targetPosition", action.position().toShortString());
            row.addProperty("itemEntity", entityTarget); row.addProperty("readOnlyClientRayMatches", true);
            row.addProperty("normalMovementSettleTicks", settleTicks); row.addProperty("clientTargetPayload", false);
            event("native-alchemy-scan-dispatched", row);
        }

        void checkpoint(JsonObject control) throws Exception {
            if (!"checkpoint".equals(phase) || !control.has("persistenceBaseline")) return;
            closeScreen();
            if (screenshotPending != 0 || !clicks.isEmpty() || !owner.containerMenu.getCarried().isEmpty()) return;
            if (!baselineMatches(control.getAsJsonObject("persistenceBaseline"))) return;
            if (!(client.level.getBlockEntity(TcpAlchemyScenario.MATRIX) instanceof TileInfusionMatrix matrix)
                || !matrix.active || !matrix.crafting
                || !(client.level.getBlockEntity(TcpAlchemyScenario.CENTER) instanceof TilePedestal center)
                || center.isEmpty()) return;
            JsonObject row = new JsonObject(); row.addProperty("active", matrix.active); row.addProperty("crafting", matrix.crafting);
            row.addProperty("syncNbt", matrix.writeSyncNBT(new CompoundTag()).toString());
            row.addProperty("centerStack", center.getSyncedStackInSlot(0).toString()); row.addProperty("nativeBlockEntityS2C", true);
            event("native-in-flight-infusion-checkpoint-observed", row); checkpointObserved = true;
            if (current != null && current.kind() == TcpAlchemyScenario.Kind.CHECKPOINT) complete("native paid in-flight matrix and exact received owner baseline");
            seal(control, "in-flight-native-infusion");
        }

        void resume(JsonObject control) throws Exception {
            if ("resume-job".equals(phase)) { observeResume(control); return; }
            if ("output".equals(phase)) { resumedOutput(control); return; }
            if ("reconnect".equals(phase)) { reconnect(control); return; }
            if ("finish".equals(phase)) { if (reconnectProven && screenshotPending == 0) seal(control, "native-resumed-infusion-and-reconnect"); }
        }

        void outputPickup(Action action) throws Exception {
            closeScreen();
            if (count(stack -> item(stack, action.item())) < action.amount()) return;
            JsonObject row = new JsonObject(); row.addProperty("item", action.item());
            row.addProperty("inventoryCount", count(stack -> item(stack, action.item()))); row.addProperty("nativeInventoryS2C", true);
            row.addProperty("serverCollisionPositionFixture", true); event("native-crucible-output-pickup-received", row);
            complete("actual native collision output pickup and received inventory");
        }

        void observeResume(JsonObject control) throws Exception {
            if (!(client.level.getBlockEntity(TcpAlchemyScenario.MATRIX) instanceof TileInfusionMatrix matrix)) return;
            if (actionStep != 0) return;
            JsonObject row = new JsonObject(); row.addProperty("active", matrix.active); row.addProperty("crafting", matrix.crafting);
            row.addProperty("syncNbt", matrix.writeSyncNBT(new CompoundTag()).toString()); row.addProperty("nativeBlockEntityS2C", true);
            row.addProperty("newClientJvm", true); event("native-loaded-infusion-job-observed", row); loadedJobObserved = true; actionStep = 1; persist();
        }

        void resumedOutput(JsonObject control) throws Exception {
            require("thaumcraft:crystal_aer".equals(string(control, "outputItem"))
                && control.get("outputAmount").getAsInt() == 1
                && TcpAlchemyScenario.CENTER.equals(position(control.getAsJsonArray("outputPosition"))),
                "Resumed native infusion output differs from reviewed recipe");
            if (!(client.level.getBlockEntity(TcpAlchemyScenario.CENTER) instanceof TilePedestal pedestal)) return;
            lookAt(Vec3.atCenterOf(TcpAlchemyScenario.CENTER));
            if (actionStep < 2) {
                if (!item(pedestal.getSyncedStackInSlot(0), "thaumcraft:crystal_aer")) return;
                closeScreen(); if (!selectEmptyHand() || !inReach(TcpAlchemyScenario.CENTER)) return;
                craftBefore = count(stack -> item(stack, "thaumcraft:crystal_aer"));
                screenshot("infusion-resumed-output-pedestal.png");
                useBlock(TcpAlchemyScenario.CENTER, "native-resumed-infusion-output-remove"); actionStep = 2; settleTicks = 0; return;
            }
            if (!pedestal.isEmpty() || count(stack -> item(stack, "thaumcraft:crystal_aer")) != craftBefore + 1) return;
            if (++settleTicks < 5) return;
            if (actionStep == 2) {
                JsonObject row = new JsonObject(); row.addProperty("item", "thaumcraft:crystal_aer"); row.addProperty("actualOutputCount", 1);
                row.addProperty("centerPedestalEmpty", true); row.addProperty("nativeInventoryS2C", true); row.addProperty("nativeBlockEntityS2C", true);
                event("native-resumed-infusion-output-pickup-received", row); actionStep = 3; resumedOutputPickedUp = true;
                screenshot("infusion-resumed-output-picked-up.png"); persist();
            }
        }

        void reload(JsonObject control) throws Exception {
            if (!"loaded".equals(phase) || !control.has("persistenceBaseline")) return;
            JsonObject baseline = control.getAsJsonObject("persistenceBaseline");
            if (!baselineMatches(baseline) || !reloadWorldMatches(baseline, false)) { settleTicks = 0; return; }
            if (++settleTicks < 40) return;
            if (!reloadedWorldObserved) {
                require(reloadWorldMatches(baseline, true), "Native loaded world changed during the forty-tick acceptance window");
                reloadedWorldObserved = true; screenshot("alchemy-reloaded-world.png"); persist(); return;
            }
            if (screenshotPending != 0) return;
            seal(control, "new-jvm-native-owner-and-world-reload");
        }

        boolean reloadWorldMatches(JsonObject baseline, boolean record) throws Exception {
            if (!baseline.has("worldTiles")) return false;
            var tiles = baseline.getAsJsonArray("worldTiles");
            require(tiles.size() == TcpAlchemyScenario.machinePositions().size(), "Native reload world ledger differs from reviewed finite machine cohort");
            java.util.Set<BlockPos> seen = new java.util.HashSet<>();
            for (var value : tiles) {
                var expected = value.getAsJsonObject(); BlockPos position = position(expected.getAsJsonArray("position"));
                require(TcpAlchemyScenario.machinePositions().contains(position) && seen.add(position), "Unexpected/duplicate native reload target");
                if (!BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(position).getBlock()).toString().equals(string(expected, "block"))) return false;
                JsonObject row = new JsonObject(); row.add("position", vector(position)); row.addProperty("block", string(expected, "block"));
                row.addProperty("actualNativeChunkS2C", true);
                if (expected.has("nbt")) {
                    var tile = client.level.getBlockEntity(position); if (tile == null) return false;
                    CompoundTag disk = TagParser.parseTag(string(expected, "nbt"));
                    CompoundTag visible = tile.getUpdateTag(client.level.registryAccess());
                    for (String key : visible.getAllKeys()) {
                        // The native smelter broadcasts only a decreasing burn counter. Its
                        // saved inventories/aspects are independently checked before first
                        // server chunk tick; the read-only client never opens a fake menu.
                        if (tile instanceof TileSmelter && key.equals("BurnTime")) continue;
                        if (key.equals("renderNote")) {
                            if (visible.getBoolean(key) != disk.contains("note", Tag.TAG_COMPOUND)) return false;
                        } else if (!disk.contains(key) || !sameVisibleTag(visible.get(key), disk.get(key), key)) return false;
                    }
                    row.addProperty("actualPublicBlockEntityS2C", true); row.addProperty("publicSyncNbt", visible.toString());
                    row.addProperty("privateDiskFieldsComparedOnServer", true);
                    if (tile instanceof TileSmelter) row.addProperty("nativeBurnCounterMayAdvance", true);
                }
                if (record) reloadedWorldObservations.add(row);
            }
            return true;
        }

        boolean sameVisibleTag(Tag actual, Tag expected, String key) {
            if (actual instanceof ListTag actualList && expected instanceof ListTag expectedList && key.equals("Aspects")) {
                java.util.Map<String, Integer> left = new java.util.TreeMap<>(), right = new java.util.TreeMap<>();
                for (Tag value : actualList) { CompoundTag row = (CompoundTag)value; left.put(row.getString("key"), row.getInt("amount")); }
                for (Tag value : expectedList) { CompoundTag row = (CompoundTag)value; right.put(row.getString("key"), row.getInt("amount")); }
                return left.equals(right);
            }
            return actual != null && actual.equals(expected);
        }

        void reconnect(JsonObject control) throws Exception {
            if (!reconnectStarted) {
                if (!reconnectReady) {
                    closeScreen();
                    require(clicks.isEmpty() && owner.containerMenu.getCarried().isEmpty(), "Reconnect requires no pending native item movement");
                    reconnectReady = true; event("native-reconnect-ready", new JsonObject()); persist(); return;
                }
                if (!control.has("reconnectAllowed") || !control.get("reconnectAllowed").getAsBoolean()) return;
                reconnectBaseline = control.getAsJsonObject("persistenceBaseline").deepCopy();
                if (!baselineMatches(reconnectBaseline)) return;
                firstConnection = connection; firstOwner = owner; reconnectStarted = true;
                client.level.disconnect(); client.disconnect(new TitleScreen());
                event("native-tcp-disconnect", connectionEvidence()); persist(); return;
            }
            if (!reconnectConnectSent) {
                require(!firstConnection.isConnected(), "Original owned TCP connection must close before replacement");
                if (client.getConnection() != null || client.player != null || client.level != null || !(client.screen instanceof TitleScreen)) return;
                require(ClientResearchPresentation.view() == null && ClientResearchCatalogHandlers.acceptedViewForCurrentConnection() == null
                    && ClientAspectSnapshot.view() == null && !ClientJeiRecipeSnapshot.ready(), "Disconnect must clear all connection-owned client facades");
                if (++reconnectTitleTicks < 20) return;
                String address = "127.0.0.1:" + marker.get("port").getAsInt();
                ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address),
                    new ServerData("Private TCP alchemy reconnect", address, ServerData.Type.OTHER), false, null);
                reconnectConnectSent = true;
                JsonObject row = new JsonObject(); row.addProperty("address", address); row.addProperty("nativeConnectScreen", true);
                row.addProperty("allOwnerFacadesCleared", true); event("native-tcp-reconnect-dispatched", row); persist(); return;
            }
            if (client.getConnection() == null || client.player == null || client.gameMode == null) return;
            require(client.getConnection().getConnection() != firstConnection && client.player != firstOwner,
                "Native reconnect must replace Connection and LocalPlayer identities");
            connection = client.getConnection().getConnection(); owner = client.player; checkConnection();
            if (!baselineMatches(reconnectBaseline)) return;
            connectionEpoch = 2; reconnectProven = true;
            JsonObject row = connectionEvidence(); row.addProperty("newConnectionIdentity", true); row.addProperty("newLocalPlayerIdentity", true);
            row.addProperty("fullInventoryKnowledgeWarpXpMatch", true); row.addProperty("onlyPopupExcluded", true);
            event("actual-tcp-reconnect-owner-restored", row); persist();
        }

        boolean baselineMatches(JsonObject baseline) throws Exception {
            var view = ClientResearchPresentation.view();
            if (baseline == null || view == null || !ClientResearchCatalogHandlers.readyForCurrentConnection()
                || ClientAspectSnapshot.view() == null || !ClientJeiRecipeSnapshot.ready()) return false;
            return TcpBootstrapReloadAudit.canonicalKnowledge(TagParser.parseTag(string(baseline, "knowledgeNbt")), true).equals(
                    TcpBootstrapReloadAudit.canonicalKnowledge(ThaumcraftCapabilities.getKnowledge(owner).serializeNBT(), false))
                && TagParser.parseTag(string(baseline, "warpNbt")).equals(ThaumcraftCapabilities.getWarp(owner).serializeNBT())
                && TcpBootstrapReloadAudit.inventory(baseline).equals(owner.getInventory().save(new ListTag()))
                && owner.getInventory().selected == baseline.get("selectedSlot").getAsInt()
                && owner.totalExperience == baseline.get("totalExperience").getAsInt()
                && owner.experienceLevel == baseline.get("experienceLevel").getAsInt()
                && Float.floatToIntBits(owner.experienceProgress) == Float.floatToIntBits(baseline.get("experienceProgress").getAsFloat());
        }

        boolean item(ItemStack stack, String id) {
            return !stack.isEmpty() && id != null && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id);
        }

        boolean ingredient(ItemStack stack, String id, String aspect) {
            if (!item(stack, id)) return false;
            return aspect == null || aspect.isBlank() || stack.getItem() instanceof ItemCrystalEssence crystal
                && crystal.getAspects(stack).getAmount(Aspect.getAspect(aspect)) > 0;
        }

        int count(Predicate<ItemStack> predicate) {
            int result = 0;
            for (int index = 0; index < owner.getInventory().getContainerSize(); index++) {
                ItemStack stack = owner.getInventory().getItem(index); if (predicate.test(stack)) result += stack.getCount();
            }
            return result;
        }

        int inventorySlot(AbstractContainerMenu menu, Predicate<ItemStack> predicate) {
            for (var slot : menu.slots) if (slot.container == owner.getInventory() && slot.getContainerSlot() >= 0
                && slot.getContainerSlot() < 36 && predicate.test(slot.getItem())) return slot.index;
            return -1;
        }

        void moveIngredient(AbstractContainerMenu menu, int target, int amount, Predicate<ItemStack> predicate) {
            require(amount > 0 && amount <= 64, "Reviewed native ingredient count outside one actual stack");
            int source = inventorySlot(menu, stack -> !stack.isEmpty() && stack.getCount() >= amount && predicate.test(stack));
            require(source >= 0 && menu.getSlot(target).getItem().isEmpty(), "Missing actual native ingredient or occupied target slot " + target);
            clicks.add(new Click(menu.containerId, source, 0, ClickType.PICKUP));
            for (int count = 0; count < amount; count++) clicks.add(new Click(menu.containerId, target, 1, ClickType.PICKUP));
            clicks.add(new Click(menu.containerId, source, 0, ClickType.PICKUP));
        }

        void click(Click click) throws Exception {
            require(owner.containerMenu.containerId == click.menu(), "Native menu changed during queued inventory movement");
            client.gameMode.handleInventoryMouseClick(click.menu(), click.slot(), click.button(), click.type(), owner);
            JsonObject row = new JsonObject(); row.addProperty("menuId", click.menu()); row.addProperty("slot", click.slot());
            row.addProperty("button", click.button()); row.addProperty("clickType", click.type().name()); row.addProperty("nativeC2S", true);
            event("vanilla-menu-click", row);
        }

        boolean selectHeld(Predicate<ItemStack> predicate) throws Exception {
            if (owner.containerMenu != owner.inventoryMenu) { closeScreen(); heldTicks = 0; return false; }
            if (owner.getInventory().selected != 0) { owner.getInventory().selected = 0; heldTicks = 0; }
            if (predicate.test(owner.getMainHandItem())) return ++heldTicks >= 3;
            int slot = inventorySlot(owner.inventoryMenu, predicate);
            if (slot < 0) return false;
            click(new Click(owner.inventoryMenu.containerId, slot, 0, ClickType.SWAP)); heldTicks = 0; return false;
        }

        boolean selectEmptyHand() throws Exception {
            if (owner.containerMenu != owner.inventoryMenu) { closeScreen(); heldTicks = 0; return false; }
            if (owner.getInventory().selected != 0) { owner.getInventory().selected = 0; heldTicks = 0; }
            if (owner.getMainHandItem().isEmpty()) return ++heldTicks >= 3;
            int empty = inventorySlot(owner.inventoryMenu, ItemStack::isEmpty);
            require(empty >= 0, "Native output retrieval needs an actual empty inventory slot");
            click(new Click(owner.inventoryMenu.containerId, empty, 0, ClickType.SWAP)); heldTicks = 0; return false;
        }

        void closeScreen() {
            if (owner.containerMenu != owner.inventoryMenu) owner.closeContainer();
            else if (client.screen != null) client.setScreen(null);
        }

        boolean inReach(BlockPos position) { return position != null && owner.getEyePosition().distanceTo(Vec3.atCenterOf(position)) <= 4.5D; }

        void useBlock(BlockPos position, String action) throws Exception {
            require(inReach(position), "Reviewed native block target lies outside normal interaction reach");
            lookAt(Vec3.atCenterOf(position));
            var result = client.gameMode.useItemOn(owner, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
            JsonObject row = new JsonObject(); row.addProperty("position", position.toShortString());
            row.addProperty("heldItem", owner.getMainHandItem().toString()); row.addProperty("result", result.toString());
            row.addProperty("nativeC2S", true); event(action, row);
        }

        void lookAt(Vec3 target) {
            Vec3 delta = target.subtract(owner.getEyePosition());
            owner.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90D));
            owner.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
        }

        void useItem(String action) throws Exception {
            var result = client.gameMode.useItem(owner, InteractionHand.MAIN_HAND);
            JsonObject row = new JsonObject(); row.addProperty("heldItem", owner.getMainHandItem().toString());
            row.addProperty("result", result.toString()); row.addProperty("nativeC2S", true); event(action, row);
        }

        void drainResearch() throws Exception {
            JsonArray received = research.drainEvents();
            for (var row : received) event("native-research-driver", row.getAsJsonObject());
            for (String name : research.drainScreenshotRequests()) screenshot(name);
            if (received.size() > 0 && current != null && current.kind() == TcpAlchemyScenario.Kind.THEORY && nativeStorage == null) {
                JsonObject driver = research.state(); String gate = researchSupplyGate(driver);
                if (driver.get("queuedInventoryClicks").getAsInt() == 0
                    && (!driver.has("pendingTheoryAction") || driver.get("pendingTheoryAction").isJsonNull()
                        || driver.get("pendingTheoryAction").getAsString().isBlank())
                    && !gate.equals(publishedResearchSupplyGate)) persist();
            }
        }

        String researchSupplyGate(JsonObject driver) {
            return driver.get("queuedInventoryClicks").getAsInt() + "/" + driver.get("pendingTheoryAction");
        }

        void observeOwner() throws Exception {
            var view = ClientResearchPresentation.view();
            if (view == null) return;
            String digest = connectionEpoch + "/" + view.digest() + "/" + view.readStateDigest();
            if (digest.equals(lastOwnerDigest)) return;
            lastOwnerDigest = digest;
            JsonObject row = new JsonObject(); row.addProperty("connectionEpoch", connectionEpoch);
            row.addProperty("catalogDigest", view.digest()); row.addProperty("readStateDigest", view.readStateDigest());
            row.addProperty("firststepsStage", view.storedStage("FIRSTSTEPS")); row.addProperty("actualAcceptedS2C", true);
            for (String key : List.of("ALCHEMY", "BASEALCHEMY", "ESSENTIA", "INFUSION", "CRYSTALFARMER")) row.addProperty(key, view.storedStage(key));
            ownerSnapshots.add(row.deepCopy()); event("accepted-owner-snapshot", row);
        }

        void screenshot(String name) throws Exception {
            require(name.matches("[A-Za-z0-9_-]+\\.png"), "Native screenshot must have a finite simple name");
            Path file = safePath(directory, directory.resolve("screenshots").resolve(name));
            require(!Files.exists(file), "Native screenshot must be new"); screenshotPending++;
            Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> client.execute(() -> {
                try {
                    require(Files.isRegularFile(file) && Files.size(file) > 1024, "Native screenshot capture did not finish");
                    screenshots.add(file.toString()); JsonObject row = new JsonObject(); row.addProperty("path", file.toString());
                    event("native-screenshot", row); screenshotPending--;
                } catch (Exception failure) { fail(failure.toString()); }
            }));
        }

        JsonObject connectionEvidence() {
            JsonObject row = new JsonObject(); row.addProperty("memory", connection.isMemoryConnection());
            row.addProperty("integratedServer", client.hasSingleplayerServer()); row.addProperty("ownerUuid", owner.getUUID().toString());
            var remote = (InetSocketAddress) connection.getRemoteAddress();
            row.addProperty("remoteHost", remote.getAddress().getHostAddress()); row.addProperty("remotePort", remote.getPort());
            row.addProperty("connected", connection.isConnected()); return row;
        }

        JsonObject state() {
            JsonObject row = identity(marker); row.addProperty("status", sealed ? "completed" : "running");
            row.addProperty("phase", phase); row.addProperty("clientTick", ticks); row.addProperty("observedUtc", Instant.now().toString());
            row.addProperty("actionIndex", actionIndex); row.addProperty("actionId", current == null ? "none" : current.id());
            row.addProperty("actionComplete", actionComplete); row.addProperty("pendingClicks", clicks.size());
            row.addProperty("actionStep", actionStep); row.addProperty("takePending", takePending);
            row.addProperty("checkpointObserved", checkpointObserved); row.addProperty("loadedJobObserved", loadedJobObserved);
            row.addProperty("resumedOutputPickedUp", resumedOutputPickedUp);
            row.addProperty("reloadedWorldObserved", reloadedWorldObserved);
            row.addProperty("screen", client.screen == null ? "none" : client.screen.getClass().getName());
            row.addProperty("connectionEpoch", connectionEpoch); row.addProperty("reconnectReady", reconnectReady);
            row.addProperty("reconnectStarted", reconnectStarted); row.addProperty("reconnectProven", reconnectProven);
            row.add("researchDriver", research.state());
            if (nativeStorage != null) row.add("nativeStorage", nativeStorage.deepCopy());
            if (craftWait != null) row.add("craftWait", craftWait.deepCopy());
            if (connection != null) { row.add("connection", connectionEvidence()); row.addProperty("ownerUuid", owner.getUUID().toString()); }
            return row;
        }

        void persist() throws Exception {
            JsonObject snapshot = state(); writeState(directory, "client", snapshot);
            publishedResearchSupplyGate = researchSupplyGate(snapshot.getAsJsonObject("researchDriver"));
        }

        void event(String action, JsonObject detail) throws Exception {
            JsonObject row = identity(marker); row.addProperty("action", action); row.addProperty("phase", phase);
            row.addProperty("actionIndex", actionIndex); row.addProperty("clientTick", ticks); row.addProperty("observedUtc", Instant.now().toString());
            row.add("detail", detail.deepCopy()); actions.add(row.deepCopy()); appendEvent(directory, "client", row);
        }

        void seal(JsonObject control, String proof) throws Exception {
            require(!sealed && screenshotPending == 0 && clicks.isEmpty() && owner.containerMenu.getCarried().isEmpty()
                && nativeStorage == null, "Native alchemy cannot seal incomplete UI/item work");
            var view = ClientResearchPresentation.view();
            if (view == null || !view.knowsStrict("FIRSTSTEPS") || view.storedStage("FIRSTSTEPS") != 4) return;
            require(view.knowsStrict("INFUSION") && view.knowsStrict("CRYSTALFARMER") && view.knowsStrict("ESSENTIASMELTER"),
                "Accepted native alchemy cohort must preserve completed original research gates");
            require("generate".equals(mode) ? checkpointObserved : "resume".equals(mode)
                ? resumedOutputPickedUp && reconnectProven && connectionEpoch == 2 : reloadedWorldObserved && baselineMatches(control.getAsJsonObject("persistenceBaseline")),
                "Missing mode-specific native gameplay/persistence proof");
            JsonObject report = state(); report.addProperty("passed", true); report.addProperty("status", "completed");
            report.addProperty("actualTCP", true); report.addProperty("proof", proof); report.addProperty("clientInventoryStackWrites", false);
            report.addProperty("clientWorldWrites", false); report.addProperty("clientKnowledgeGrants", false); report.addProperty("fullSurvivalAcquisition", false);
            report.addProperty("loadedWithoutGrants", !"generate".equals(mode)); report.addProperty("zeroGameplayActions", "reload".equals(mode));
            report.add("loadedPlayer", playerSnapshot(owner)); report.add("actions", actions.deepCopy());
            report.add("acceptedOwnerSnapshots", ownerSnapshots.deepCopy()); report.add("screenshots", screenshots.deepCopy()); report.add("researchReport", research.report());
            report.add("reloadedWorldObservations", reloadedWorldObservations.deepCopy());
            report.add("nativeStorageReceipts", nativeStorageReceipts.deepCopy());
            if (control.has("persistenceBaseline")) report.add("receivedServerBaseline", control.get("persistenceBaseline").deepCopy());
            writeTerminal(directory, "client", report); sealed = true; persist();
            Thaumcraft.log.info("Private TCP alchemy client accepted {} native observations", mode);
        }

        void fail(String detail) {
            if (stopped) return;
            try {
                if (!sealed) {
                    JsonObject report = identity(marker); report.addProperty("status", "failed"); report.addProperty("passed", false);
                    report.addProperty("phase", phase); report.addProperty("actionIndex", actionIndex); report.addProperty("failure", detail);
                    if (craftWait != null) report.add("craftWait", craftWait.deepCopy());
                    report.add("actions", actions.deepCopy()); writeTerminal(directory, "client", report);
                }
            } catch (Exception failure) { Thaumcraft.log.error("Unable to record private TCP alchemy failure", failure); }
            stopped = true; client.stop();
        }
    }
}
