package thaumcraft.client.jei;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferListener;
import mezz.jei.api.recipe.transfer.RecipeTransferResult;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import thaumcraft.Thaumcraft;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.client.gui.GuiArcaneWorkbench;
import thaumcraft.client.lib.ClientJeiReviewScenario;
import thaumcraft.common.container.ContainerArcaneWorkbench;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchCosts;
import thaumcraft.common.world.aura.AuraChunk;
import thaumcraft.common.world.aura.AuraHandler;

/** Explicit private-world review only. Uses the actual LocalPlayer, JEI lifecycle context,
 * normal block interaction, transfer C2S/receipt S2C and vanilla container-click C2S.
 * Server-thread item/research/aura writes below are clearly scoped fixture preparation.
 */
public final class JeiTransferClientAudit {
    private enum Stage { START, SINGLE_SETUP, SINGLE_STOCK, SINGLE_MENU, SINGLE_SCREEN, SINGLE_ACK, SINGLE_RENDER, SINGLE_CHECK,
        SINGLE_CRAFT, SINGLE_PAID_CHECK, MAX_SETUP, MAX_STOCK, MAX_MENU, MAX_SCREEN, MAX_ACK, MAX_RENDER, MAX_CHECK, DONE }
    private record ServerResult(JsonObject evidence, String failure) {}
    private record Layout(Object wrapped, IRecipeLayoutDrawable<?> drawable, Object service) {}
    private record AuraFixture(AuraChunk chunk, short base, float vis, float flux) {}
    private static Stage stage = Stage.START;
    private static int ticks, stageStarted, starts, completions;
    private static int arrangedRenderFrames;
    private static boolean renderListening;
    private static final int RENDER_WAIT_TICKS = 30;
    private static final Consumer<ScreenEvent.Render.Post> RENDER_LISTENER = JeiTransferClientAudit::rendered;
    private static IRecipeTransferContext<?, ?> pendingContext;
    private static RecipeTransferResult outcome;
    private static MinecraftServer server;
    private static UUID ownerId;
    private static volatile BlockPos position;
    private static volatile ServerResult serverResult;
    private static List<ItemStack> before = List.of(); // server-thread fixture snapshots only
    private static float beforeCraftVis;
    private static int expectedCraftCost;
    private static final List<AuraFixture> AURA_FIXTURE = new ArrayList<>(); // owning server thread only
    private static final JsonObject REPORT = new JsonObject();
    private static final JsonArray CHECKS = new JsonArray(), FAILURES = new JsonArray();
    private static final IRecipeTransferListener LISTENER = new IRecipeTransferListener() {
        @Override public void beforeRecipeTransfer(IRecipeTransferContext<?, ?> context) {
            if (!active() || context.getRecipeType() != ThaumcraftJeiPlugin.ARCANE) return;
            var client = Minecraft.getInstance();
            require(pendingContext == null && context.getPlayer() == client.player, "JEI before callback must use the actual LocalPlayer exactly once");
            require(context.getContainer() == client.player.containerMenu, "JEI context must use the actual open client menu");
            require(context.getClass().getName().equals("mezz.jei.common.transfer.RecipeTransferContext"), "Context must be constructed by JEI's real transfer service");
            require(context.isMaxTransfer() == (stage == Stage.MAX_SCREEN), "JEI context must carry the requested single/max mode");
            pendingContext = context; starts++;
            var row = new JsonObject(); row.addProperty("event", "before"); row.addProperty("context", context.getClass().getName());
            row.addProperty("transferId", context.getTransferId()); row.addProperty("maximum", context.isMaxTransfer()); CHECKS.add(row);
        }
        @Override public void afterRecipeTransfer(IRecipeTransferContext<?, ?> context, RecipeTransferResult result) {
            if (!active() || context.getRecipeType() != ThaumcraftJeiPlugin.ARCANE) return;
            require(pendingContext == context && outcome == null, "The exact JEI context must complete once after the server receipt");
            outcome = result; completions++;
            var row = new JsonObject(); row.addProperty("event", "after"); row.addProperty("transferId", context.getTransferId());
            row.addProperty("result", result.name()); CHECKS.add(row);
        }
    };
    private JeiTransferClientAudit() {}
    public static IRecipeTransferListener listener() { return LISTENER; }
    private static boolean active() { return Boolean.getBoolean("thaumcraft.jeiReview") && "transfer".equals(ClientJeiReviewScenario.phase()) && stage != Stage.DONE; }

    public static void tick(IJeiRuntime runtime) {
        if (!active() || runtime == null || !ClientJeiReviewScenario.isReady()) return;
        var client = Minecraft.getInstance();
        try {
            require(client.isSameThread(), "Transfer review requires client MAIN");
            require(client.getSingleplayerServer() != null && ClientJeiReviewScenario.owns(client.getSingleplayerServer())
                && client.getConnection() != null && client.getConnection().getConnection().isMemoryConnection(), "Only the private unpublished integrated review world may run transfer fixtures");
            if (++ticks - stageStarted > 400) throw new IllegalStateException("Timed out at " + stage);
            switch (stage) {
                case START -> {
                    server = client.getSingleplayerServer(); ownerId = client.player.getUUID();
                    NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Post.class, RENDER_LISTENER); renderListening = true;
                    REPORT.addProperty("purpose", "actual-client-jei-recipe-transfer"); REPORT.addProperty("memoryConnection", true);
                    REPORT.addProperty("privateWorld", ClientJeiReviewScenario.worldPath().toString());
                    queue(owner -> prepare(owner, false)); move(Stage.SINGLE_SETUP);
                }
                case SINGLE_SETUP -> { if (takeServerResult()) move(Stage.SINGLE_STOCK); }
                case SINGLE_STOCK -> { if (stockReady(false)) { openWorkbench(); move(Stage.SINGLE_MENU); } }
                case SINGLE_MENU -> { if (menuReady(false)) { showRecipe(runtime, "thaumometer"); move(Stage.SINGLE_SCREEN); } }
                case SINGLE_SCREEN -> { if (ticks - stageStarted >= 12) { beginTransfer(runtime, false); move(Stage.SINGLE_ACK); } }
                case SINGLE_ACK -> {
                    if (outcome != null) {
                        require(outcome == RecipeTransferResult.SUCCESS, "The server must accept the single transfer");
                        if (!singleArranged()) break;
                        returnToWorkbench(runtime); arrangedRenderFrames = 0; move(Stage.SINGLE_RENDER);
                    }
                }
                case SINGLE_RENDER -> {
                    requireWorkbenchArrangement(false);
                    if (ticks - stageStarted >= RENDER_WAIT_TICKS && arrangedRenderFrames >= 3) {
                        recordClientArrangement(false, "jei_transfer_single.png"); screenshot("jei_transfer_single.png");
                        queue(owner -> checkSingle(owner)); move(Stage.SINGLE_CHECK);
                    }
                }
                case SINGLE_CHECK -> {
                    if (screenshotReady("jei_transfer_single.png") && takeServerResult()) {
                        require(client.player.containerMenu instanceof ContainerArcaneWorkbench && client.gameMode != null, "Actual client menu remains open for paid crafting");
                        // The normal client controller sends ServerboundContainerClickPacket. No server crafting shortcut.
                        client.gameMode.handleInventoryMouseClick(client.player.containerMenu.containerId, 0, 0, ClickType.PICKUP, client.player);
                        move(Stage.SINGLE_CRAFT);
                    }
                }
                case SINGLE_CRAFT -> {
                    if (client.player.containerMenu.getCarried().is(ItemsTC.thaumometer)) {
                        queue(owner -> checkPaid(owner)); move(Stage.SINGLE_PAID_CHECK);
                    }
                }
                case SINGLE_PAID_CHECK -> {
                    if (takeServerResult()) { resetReceipt(); queue(owner -> prepare(owner, true)); move(Stage.MAX_SETUP); }
                }
                case MAX_SETUP -> { if (takeServerResult()) move(Stage.MAX_STOCK); }
                case MAX_STOCK -> { if (stockReady(true)) { openWorkbench(); move(Stage.MAX_MENU); } }
                case MAX_MENU -> { if (menuReady(true)) { showRecipe(runtime, "workbenchcharger"); move(Stage.MAX_SCREEN); } }
                case MAX_SCREEN -> { if (ticks - stageStarted >= 12) { beginTransfer(runtime, true); move(Stage.MAX_ACK); } }
                case MAX_ACK -> {
                    if (outcome != null) {
                        require(outcome == RecipeTransferResult.SUCCESS, "The server must accept the maximum transfer");
                        if (!maxArranged()) break;
                        returnToWorkbench(runtime); arrangedRenderFrames = 0; move(Stage.MAX_RENDER);
                    }
                }
                case MAX_RENDER -> {
                    requireWorkbenchArrangement(true);
                    if (ticks - stageStarted >= RENDER_WAIT_TICKS && arrangedRenderFrames >= 3) {
                        recordClientArrangement(true, "jei_transfer_max.png"); screenshot("jei_transfer_max.png");
                        queue(owner -> checkMaximum(owner)); move(Stage.MAX_CHECK);
                    }
                }
                case MAX_CHECK -> {
                    if (screenshotReady("jei_transfer_max.png") && takeServerResult()) {
                        require(starts == 2 && completions == 2, "Exactly two real JEI before/after context callbacks must finish");
                        REPORT.addProperty("singleAndMaximumTransfers", true); REPORT.addProperty("paidCraftViaClientPacket", true);
                        finish(true, null);
                    }
                }
                case DONE -> {}
            }
        } catch (Throwable failure) { finish(false, failure.toString()); }
    }

    private static void prepare(ServerPlayer owner, boolean maximum) {
        restoreAuraFixture();
        owner.closeContainer(); owner.setGameMode(GameType.SURVIVAL); owner.getInventory().clearContent();
        owner.inventoryMenu.setCarried(ItemStack.EMPTY);
        if (position == null) position = owner.blockPosition().offset(2, 0, 0).immutable();
        var level = owner.serverLevel(); level.setBlock(position.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(position, BlocksTC.arcaneWorkbench.defaultBlockState(), Block.UPDATE_ALL);
        require(level.getBlockEntity(position) instanceof thaumcraft.common.tiles.crafting.TileArcaneWorkbench, "Fixture must create the registered real workbench block entity");
        var table = (thaumcraft.common.tiles.crafting.TileArcaneWorkbench) level.getBlockEntity(position); table.clearContent();
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        knowledge.addResearch("FIRSTSTEPS"); knowledge.setResearchStage("FIRSTSTEPS", 2); knowledge.addResearch("WORKBENCHCHARGER");
        knowledge.setResearchStage("WORKBENCHCHARGER", 1); PlayerDataSync.syncKnowledgeNow(owner);
        if (maximum) {
            owner.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 64)); owner.getInventory().setItem(1, new ItemStack(BlocksTC.plankGreatwood, 64));
            owner.getInventory().setItem(2, new ItemStack(ItemsTC.visResonator, 64)); owner.getInventory().setItem(3, ThaumcraftApiHelper.makeCrystal(Aspect.AIR, 64));
            owner.getInventory().setItem(4, ThaumcraftApiHelper.makeCrystal(Aspect.ORDER, 64)); table.setItem(11, ThaumcraftApiHelper.makeCrystal(Aspect.WATER, 7));
        } else {
            owner.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 8)); owner.getInventory().setItem(1, new ItemStack(Items.GLASS_PANE, 2));
            for (int aspect = 0; aspect < 6; aspect++) owner.getInventory().setItem(2 + aspect, ThaumcraftApiHelper.makeCrystal(ArcaneWorkbenchCosts.PRIMALS[aspect], 2));
            ItemStack displaced = new ItemStack(Items.DIAMOND, 3); displaced.set(DataComponents.CUSTOM_NAME, Component.literal("Private JEI transfer fixture")); table.setItem(0, displaced);
        }
        isolateAura(owner);
        owner.getInventory().setChanged(); owner.inventoryMenu.broadcastFullState();
        before = stored(table, owner); result("setup", maximum ? "maximum" : "single");
    }
    /** The ordinary aura tick can move four neighbor vis plus 0.25 regeneration between
     * a client click and its observed result. This private fixture keeps that tick active,
     * but makes the five relevant chunks ineligible for diffusion/regeneration. At fixed
     * moon phase zero its excess-vis conversion amount is also exactly zero.
     */
    private static void isolateAura(ServerPlayer owner) {
        var level = owner.serverLevel();
        require(level.getMoonPhase() == 0 && !level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT),
            "Exact paid-craft audit requires the private world's fixed moon-zero fixture");
        int x = position.getX() >> 4, z = position.getZ() >> 4;
        for (int[] offset : new int[][] {{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            level.getChunk(x + offset[0], z + offset[1]);
            var aura = AuraHandler.getAuraChunk(level, x + offset[0], z + offset[1]);
            require(aura != null, "Fixture uses actual loaded aura chunks");
            AURA_FIXTURE.add(new AuraFixture(aura, aura.getBase(), aura.getVis(), aura.getFlux()));
            aura.setBase((short)0);
            if (offset[0] == 0 && offset[1] == 0) { aura.setVis(60); aura.setFlux(0); }
        }
    }
    private static void restoreAuraFixture() {
        for (var saved : AURA_FIXTURE) {
            saved.chunk.setBase(saved.base); saved.chunk.setVis(saved.vis); saved.chunk.setFlux(saved.flux);
        }
        AURA_FIXTURE.clear();
    }
    private static boolean stockReady(boolean maximum) {
        var client = Minecraft.getInstance();
        return client.player.containerMenu == client.player.inventoryMenu
            && (maximum ? client.player.getInventory().countItem(Items.IRON_INGOT) == 64 && client.player.getInventory().countItem(ItemsTC.visResonator) == 64
                : client.player.getInventory().countItem(Items.GOLD_INGOT) == 8 && client.player.getInventory().countItem(Items.GLASS_PANE) == 2)
            && client.level.getBlockState(position).is(BlocksTC.arcaneWorkbench)
            && ThaumcraftCapabilities.getKnowledge(client.player).isResearchKnown(maximum ? "WORKBENCHCHARGER" : "FIRSTSTEPS@2");
    }
    private static void openWorkbench() {
        var client = Minecraft.getInstance(); client.setScreen(null);
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
    }
    private static boolean menuReady(boolean maximum) {
        var client = Minecraft.getInstance();
        if (!(client.screen instanceof GuiArcaneWorkbench) || !(client.player.containerMenu instanceof ContainerArcaneWorkbench menu)) return false;
        return maximum ? menu.table().getItem(11).getCount() == 7 : menu.table().getItem(0).is(Items.DIAMOND) && menu.table().getItem(0).getCount() == 3;
    }
    private static void showRecipe(IJeiRuntime runtime, String source) {
        var holder = runtime.getRecipeManager().createRecipeLookup(ThaumcraftJeiPlugin.ARCANE).get()
            .filter(recipe -> JeiRecipeIdentity.isCurrent(ThaumcraftJeiPlugin.ARCANE, recipe)
                && JeiRecipeIdentity.sourceId(recipe).equals(ResourceLocation.fromNamespaceAndPath("thaumcraft", source))).findFirst().orElseThrow();
        var category = runtime.getRecipeManager().getRecipeCategory(ThaumcraftJeiPlugin.ARCANE);
        runtime.getRecipesGui().showRecipes(category, List.of(holder), List.of());
    }
    private static void beginTransfer(IJeiRuntime runtime, boolean maximum) throws ReflectiveOperationException {
        var client = Minecraft.getInstance(); Layout layout = currentLayout();
        require(client.player.containerMenu instanceof ContainerArcaneWorkbench, "A real workbench remains the JEI parent menu");
        var category = runtime.getRecipeManager().getRecipeCategory(ThaumcraftJeiPlugin.ARCANE);
        require(runtime.getRecipeTransferManager().getRecipeTransferHandler((ContainerArcaneWorkbench)client.player.containerMenu, category)
            .orElseThrow() instanceof ArcaneRecipeTransferHandler, "JEI must resolve the registered Thaumcraft transfer handler");
        if (!maximum) {
            var area = layout.drawable.getRecipeTransferButtonArea(); var bounds = layout.drawable.getRect();
            double x = bounds.getX() + area.getX() + area.getWidth() / 2D, y = bounds.getY() + area.getY() + area.getHeight() / 2D;
            require(client.screen.mouseClicked(x, y, 0), "Native JEI transfer button must handle its real mouse click");
            client.screen.mouseReleased(x, y, 0);
        } else {
            // The pinned JEI implementation exposes no public API for invoking a maximum transfer.
            // Reuse its actual service/layout/parent screen; it creates its real lifecycle context.
            var parent = runtime.getRecipesGui().getParentScreen().orElseThrow();
            require(parent instanceof GuiArcaneWorkbench, "Actual JEI parent screen is the workbench");
            var method = layout.service.getClass().getMethod("transferRecipe", AbstractContainerScreen.class, IRecipeLayoutDrawable.class, Player.class, boolean.class);
            require(Boolean.TRUE.equals(method.invoke(layout.service, parent, layout.drawable, client.player, true)), "JEI service must dispatch maximum transfer");
        }
        require(pendingContext != null, "Real JEI beforeRecipeTransfer must run while dispatching");
    }
    private static Layout currentLayout() throws ReflectiveOperationException {
        Object gui = Minecraft.getInstance().screen;
        require(gui != null && gui.getClass().getName().equals("mezz.jei.gui.recipes.RecipesGui"), "Actual native JEI recipes screen is open");
        Object layouts = read(gui, "layouts"); var recipes = (List<?>) read(layouts, "recipeLayoutsWithButtons");
        require(recipes.size() == 1, "Native JEI screen shows exactly the selected fixture recipe");
        Object wrapped = recipes.getFirst(); var drawable = (IRecipeLayoutDrawable<?>)wrapped.getClass().getMethod("getRecipeLayout").invoke(wrapped);
        Object controller = read(wrapped, "transferButton"); return new Layout(wrapped, drawable, read(controller, "recipeTransferService"));
    }
    private static Object read(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static boolean singleArranged() {
        if (!(Minecraft.getInstance().player.containerMenu instanceof ContainerArcaneWorkbench menu)) return false;
        for (int slot : new int[] {1, 3, 5, 7}) if (!menu.table().getItem(slot).is(Items.GOLD_INGOT) || menu.table().getItem(slot).getCount() != 1) return false;
        if (!menu.table().getItem(4).is(Items.GLASS_PANE) || menu.table().getItem(4).getCount() != 1) return false;
        for (int aspect = 0; aspect < 6; aspect++) if (!ArcaneWorkbenchCosts.crystal(menu.table().getItem(9 + aspect), aspect) || menu.table().getItem(9 + aspect).getCount() != 1) return false;
        return true;
    }
    private static boolean maxArranged() {
        if (!(Minecraft.getInstance().player.containerMenu instanceof ContainerArcaneWorkbench menu)) return false;
        for (int slot : new int[] {1, 3, 5, 6, 8}) if (menu.table().getItem(slot).getCount() != 32) return false;
        return menu.table().getItem(1).is(ItemsTC.visResonator) && menu.table().getItem(3).is(BlocksTC.plankGreatwood.asItem())
            && menu.table().getItem(5).is(BlocksTC.plankGreatwood.asItem()) && menu.table().getItem(6).is(Items.IRON_INGOT)
            && menu.table().getItem(8).is(Items.IRON_INGOT) && ArcaneWorkbenchCosts.crystal(menu.table().getItem(9), 0)
            && ArcaneWorkbenchCosts.crystal(menu.table().getItem(13), 4) && ArcaneWorkbenchCosts.crystal(menu.table().getItem(11), 2)
            && menu.table().getItem(9).getCount() == 64 && menu.table().getItem(13).getCount() == 64 && menu.table().getItem(11).getCount() == 7;
    }
    private static void returnToWorkbench(IJeiRuntime runtime) {
        var client = Minecraft.getInstance();
        if (!(client.screen instanceof GuiArcaneWorkbench)) {
            require(client.screen != null && client.screen.getClass().getName().equals("mezz.jei.gui.recipes.RecipesGui"),
                "Native JEI recipes screen must close through its normal parent-screen lifecycle");
            var parent = runtime.getRecipesGui().getParentScreen().orElseThrow();
            require(parent instanceof GuiArcaneWorkbench workbench && workbench.getMenu() == client.player.containerMenu,
                "JEI parent must be the actual open workbench menu");
            client.screen.onClose();
            require(client.screen == parent, "JEI onClose must restore its actual parent workbench screen");
        }
        require(client.screen instanceof GuiArcaneWorkbench workbench && workbench.getMenu() == client.player.containerMenu,
            "Post-transfer evidence requires the actual workbench screen and menu");
    }
    private static void requireWorkbenchArrangement(boolean maximum) {
        var client = Minecraft.getInstance();
        require(client.screen instanceof GuiArcaneWorkbench workbench && workbench.getMenu() == client.player.containerMenu
                && (maximum ? maxArranged() : singleArranged()),
            "Actual workbench must keep its acknowledged client slot arrangement while rendering");
    }
    private static void rendered(ScreenEvent.Render.Post event) {
        if (!active() || !(event.getScreen() instanceof GuiArcaneWorkbench)) return;
        var client = Minecraft.getInstance();
        if (client.player == null || client.screen != event.getScreen()) return;
        if (stage == Stage.SINGLE_RENDER && singleArranged() || stage == Stage.MAX_RENDER && maxArranged()) arrangedRenderFrames++;
    }
    private static void recordClientArrangement(boolean maximum, String screenshot) {
        requireWorkbenchArrangement(maximum);
        var client = Minecraft.getInstance(); var menu = (ContainerArcaneWorkbench)client.player.containerMenu;
        var evidence = new JsonObject(); evidence.addProperty("event", maximum ? "client-maximum-arrangement" : "client-single-arrangement");
        evidence.addProperty("maximum", maximum); evidence.addProperty("screenshot", screenshot);
        evidence.addProperty("screen", client.screen.getClass().getName()); evidence.addProperty("menuId", menu.containerId);
        evidence.addProperty("stateId", menu.getStateId()); evidence.addProperty("renderWaitTicks", ticks - stageStarted);
        evidence.addProperty("renderedWorkbenchFrames", arrangedRenderFrames); evidence.addProperty("carriedEmpty", menu.getCarried().isEmpty());
        evidence.addProperty("availableVis", menu.availableVis());
        var table = new JsonArray(); for (int slot = 0; slot < 15; slot++) table.add(clientStack(slot, menu.table().getItem(slot)));
        var inventory = new JsonArray(); for (int slot = 0; slot < 36; slot++) inventory.add(clientStack(slot, client.player.getInventory().getItem(slot)));
        evidence.add("tableSlots", table); evidence.add("inventorySlots", inventory); CHECKS.add(evidence);
    }
    private static JsonObject clientStack(int slot, ItemStack stack) {
        var evidence = new JsonObject(); evidence.addProperty("slot", slot);
        evidence.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()); evidence.addProperty("count", stack.getCount());
        for (int aspect = 0; aspect < 6; aspect++) if (ArcaneWorkbenchCosts.crystal(stack, aspect))
            evidence.addProperty("aspect", ArcaneWorkbenchCosts.PRIMALS[aspect].getTag());
        var customName = stack.get(DataComponents.CUSTOM_NAME); if (customName != null) evidence.addProperty("customName", customName.getString());
        return evidence;
    }
    private static ContainerArcaneWorkbench actualMenu(ServerPlayer owner) {
        require(owner.containerMenu instanceof ContainerArcaneWorkbench menu && menu.table() == owner.serverLevel().getBlockEntity(position), "Actual server menu belongs to the private fixture block");
        return (ContainerArcaneWorkbench)owner.containerMenu;
    }
    private static void checkSingle(ServerPlayer owner) {
        var menu = actualMenu(owner); conserved(before, stored(menu.table(), owner));
        for (int slot : new int[] {1, 3, 5, 7}) require(menu.table().getItem(slot).is(Items.GOLD_INGOT) && menu.table().getItem(slot).getCount() == 1, "Server single-transfer grid contains one set");
        for (int aspect = 0; aspect < 6; aspect++) require(ArcaneWorkbenchCosts.crystal(menu.table().getItem(9 + aspect), aspect) && menu.table().getItem(9 + aspect).getCount() == 1, "Server single transfer contains exact six crystal quantities");
        require(menu.getCarried().isEmpty() && menu.getSlot(0).getItem().is(ItemsTC.thaumometer), "Transfer creates only a normal paid-ready preview");
        var recipe = menu.displayedArcaneRecipe();
        require(recipe != null && recipe.vis() == 20 && ArcaneWorkbenchCosts.discount(owner) == 0F,
            "Actual source recipe and unequipped private owner must require exactly 20 vis");
        expectedCraftCost = ArcaneWorkbenchCosts.visCost(recipe.vis(), ArcaneWorkbenchCosts.discount(owner));
        require(AuraHandler.getVis(owner.serverLevel(), position) == 60F,
            "Aura isolation must keep the actual source at 60 vis before crafting");
        beforeCraftVis = AuraHandler.getVis(owner.serverLevel(), position); result("single", "server components/counts conserved; six primal quantities; no crafted output");
    }
    private static void checkPaid(ServerPlayer owner) {
        var menu = actualMenu(owner); require(menu.getCarried().is(ItemsTC.thaumometer) && menu.getCarried().getCount() == 1, "Normal client container click produces one actual paid Thaumometer");
        for (int slot = 0; slot < 15; slot++) require(menu.table().getItem(slot).isEmpty(), "Paid craft consumes the arranged grid and each crystal");
        float after = AuraHandler.getVis(owner.serverLevel(), position), delta = beforeCraftVis - after;
        require(delta == expectedCraftCost && after == 40F,
            "Actual client crafting must consume exactly the source 20 vis in its isolated live aura fixture: " + delta);
        require(owner.getInventory().countItem(Items.GOLD_INGOT) == 4 && owner.getInventory().countItem(Items.GLASS_PANE) == 1, "Paid craft keeps remaining stock");
        var evidence = new JsonObject(); evidence.addProperty("event", "paid-client-craft"); evidence.addProperty("visBefore", beforeCraftVis);
        evidence.addProperty("visAfter", after); evidence.addProperty("visSpent", delta); evidence.addProperty("sourceCost", expectedCraftCost);
        evidence.addProperty("auraFixture", "fixed moon-zero; five zero-base loaded chunks; ordinary aura tick remains active");
        restoreAuraFixture();
        serverResult = new ServerResult(evidence, null);
    }
    private static void checkMaximum(ServerPlayer owner) {
        var menu = actualMenu(owner); conserved(before, stored(menu.table(), owner));
        for (int slot : new int[] {1, 3, 5, 6, 8}) require(menu.table().getItem(slot).getCount() == 32, "Server maximum arrangement contains32sourcebatches");
        require(menu.table().getItem(9).getCount() == 64 && menu.table().getItem(13).getCount() == 64 && menu.table().getItem(11).getCount() == 7,
            "Maximum pays2air/orderperbatch in64stack limits and retains unused water reserve");
        require(owner.getInventory().countItem(ItemsTC.visResonator) == 32 && menu.getCarried().isEmpty(), "Maximum retains leftovers and creates no crafted output");
        restoreAuraFixture();
        result("maximum", "server components/counts conserved;32batches;twoair/ordercost;unusedwaterpreserved");
    }
    private static List<ItemStack> stored(net.minecraft.world.Container table, ServerPlayer owner) {
        List<ItemStack> stacks = new ArrayList<>(); for (int slot = 0; slot < 15; slot++) stacks.add(table.getItem(slot).copy());
        for (int slot = 0; slot < 36; slot++) stacks.add(owner.getInventory().getItem(slot).copy()); return stacks;
    }
    private static int count(List<ItemStack> stacks, ItemStack key) { return stacks.stream().filter(stack -> ItemStack.isSameItemSameComponents(stack, key)).mapToInt(ItemStack::getCount).sum(); }
    private static void conserved(List<ItemStack> before, List<ItemStack> after) {
        for (var key : before) if (!key.isEmpty()) require(count(before, key) == count(after, key), "Actual transfer preserves complete components and item counts");
        for (var key : after) if (!key.isEmpty()) require(before.stream().anyMatch(stack -> ItemStack.isSameItemSameComponents(stack, key)), "Actual transfer creates no new item component variant");
    }
    private static void queue(Consumer<ServerPlayer> action) {
        serverResult = null;
        server.execute(() -> {
            try {
                require(ClientJeiReviewScenario.owns(server) && server.isSameThread(), "Fixture action requires the exact private review server MAIN");
                var owner = server.getPlayerList().getPlayer(ownerId); require(owner != null && owner.connection.getConnection().isMemoryConnection(), "Fixture owner is the actual connected local player");
                action.accept(owner);
            } catch (Throwable failure) {
                if (ClientJeiReviewScenario.owns(server) && server.isSameThread()) restoreAuraFixture();
                serverResult = new ServerResult(null, failure.toString());
            }
        });
    }
    private static void result(String event, String detail) {
        var evidence = new JsonObject(); evidence.addProperty("event", event); evidence.addProperty("detail", detail); serverResult = new ServerResult(evidence, null);
    }
    private static boolean takeServerResult() {
        ServerResult result = serverResult; if (result == null) return false; serverResult = null;
        require(result.failure == null, "Server fixture/check failed: " + result.failure); CHECKS.add(result.evidence); return true;
    }
    private static void screenshot(String name) { var client = Minecraft.getInstance(); Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> Thaumcraft.log.info("JEI transfer review screenshot: {}", message.getString())); }
    private static boolean screenshotReady(String name) {
        try {
            var path = ClientJeiReviewScenario.directory().resolve("screenshots").resolve(name);
            if (!Files.isRegularFile(path) || Files.size(path) <= 8) return false;
            byte[] signature = new byte[8];
            try (var input = Files.newInputStream(path)) { if (input.read(signature) != 8) return false; }
            return java.util.Arrays.equals(signature, new byte[] {(byte)137, 80, 78, 71, 13, 10, 26, 10});
        } catch (java.io.IOException pending) { return false; }
    }
    private static void resetReceipt() { pendingContext = null; outcome = null; }
    private static void move(Stage next) { stage = next; stageStarted = ticks; }
    private static void require(boolean condition, String reason) { if (!condition) throw new IllegalStateException(reason); }
    private static void finish(boolean passed, String failure) {
        if (stage == Stage.DONE) return;
        if (renderListening) { NeoForge.EVENT_BUS.unregister(RENDER_LISTENER); renderListening = false; }
        if (failure != null) FAILURES.add(failure);
        REPORT.addProperty("passed", passed); REPORT.addProperty("stage", stage.name()); REPORT.addProperty("jeiStarts", starts); REPORT.addProperty("jeiCompletions", completions);
        REPORT.add("checks", CHECKS); REPORT.add("failures", FAILURES);
        stage = Stage.DONE;
        try {
            var path = ClientJeiReviewScenario.directory().resolve("jei-transfer.json"); Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(REPORT) + "\n", StandardCharsets.UTF_8);
        } catch (Exception writeFailure) { Thaumcraft.log.error("Cannot write JEI transfer review", writeFailure); }
        if (!passed) ClientJeiReviewScenario.fail("JEI transfer review: " + failure);
    }
}
