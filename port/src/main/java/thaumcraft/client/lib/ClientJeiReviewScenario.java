package thaumcraft.client.lib;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.items.casters.ItemCaster;
import thaumcraft.common.lib.capabilities.PlayerDataSync;

/** Explicit private-run audit controller. No JEI imports and no behavior without the opt-in property.
 * This creates one fresh test world, grants research only as a documented audit fixture, and never
 * opens an existing user save. Runtime JEI/screenshot/transfer probes communicate through JSON.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ClientJeiReviewScenario {
    private static final String WORLD = "jei-review";
    private static final long SEED = 62121120261009L;
    private static final ResourceLocation API_ID = ResourceLocation.fromNamespaceAndPath("thaumcraft", "jei_review_api");
    private static final JsonArray STEPS = new JsonArray();
    private static String phase = "initialize";
    private static Path directory, worldPath, auditPath;
    private static long startedNanos, phaseNanos, baselineObservation, baselineConnection, baselineAspects, baselineRecipes;
    private static String baselineKnowledgeDigest;
    private static long ticks, phaseTicks;
    private static boolean initialized, created, stopped, withJei, originalHide, configCaptured;
    private static volatile MinecraftServer ownedServer;
    private static volatile CompletableFuture<Void> operation;
    private static volatile CrucibleRecipe apiFixture;
    private static volatile int grantedResearch;
    private static JsonObject waitingDiagnostic;
    private static boolean casterVisual, casterScreenshotPending;
    private static int casterSettleTicks;
    private static volatile JsonObject casterFixtures, casterServerSwap;
    private static JsonObject casterSwap, clientProcess;
    private static final JsonArray CASTER_FRAMES = new JsonArray(), CASTER_ATLAS = new JsonArray(), CASTER_SCREENSHOTS = new JsonArray();

    private ClientJeiReviewScenario() {}
    public static boolean enabled() { return Boolean.getBoolean("thaumcraft.jeiReview"); }
    public static String phase() { return phase; }
    public static Path directory() { return directory; }
    public static Path worldPath() { return worldPath; }

    public static boolean owns(MinecraftServer server) {
        return created && server != null && server == ownedServer && !server.isPublished()
            && server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(worldPath);
    }

    public static boolean isReady() {
        if (!enabled() || !initialized || !created || stopped) return false;
        var minecraft = Minecraft.getInstance();
        return minecraft.isSameThread() && minecraft.level != null && minecraft.player != null
            && owns(minecraft.getSingleplayerServer()) && minecraft.getConnection() != null
            && minecraft.getConnection().getConnection().isMemoryConnection()
            && ClientAspectSnapshot.view() != null && ClientResearchPresentation.view() != null
            && ClientJeiRecipeSnapshot.ready();
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!enabled() || stopped) return;
        var minecraft = Minecraft.getInstance();
        try {
            if (!initialized) initialize(minecraft);
            ticks++; phaseTicks++;
            if (System.nanoTime() - startedNanos > 1_800_000_000_000L
                || System.nanoTime() - phaseNanos > 600_000_000_000L) throw new IllegalStateException("Timed out in private JEI review phase " + phase);
            switch (phase) {
                case "waiting-title" -> {
                    if (minecraft.screen instanceof AccessibilityOnboardingScreen onboarding) onboarding.onClose();
                    else if (minecraft.screen instanceof TitleScreen title) createWorld(minecraft, title);
                }
                case "waiting-world" -> {
                    captureServer(minecraft);
                    reportWaitingDiagnostic(minecraft);
                    if (isReady()) {
                        recordStep("fresh-private-world-ready", commonEvidence());
                        if (casterVisual) transition("caster-fixtures");
                        else if (withJei) transition("initial-audit"); else finishWithoutJei();
                    } else if (phaseTicks > 40 && minecraft.screen instanceof TitleScreen)
                        throw new IllegalStateException("Native fresh-world creation returned to title without a world");
                }
                case "caster-fixtures", "caster-empty-frame", "caster-swap-receipt", "caster-focused-settle", "caster-focused-frame" -> casterVisualTick(minecraft);
                case "initial-audit" -> {
                    JsonObject audit = passingAudit();
                    if (audit != null) {
                        require(audit.get("hideUnknownResearch").getAsBoolean(), "Initial audit must observe default research hiding");
                        recordStep("initial-research-hiding", audit);
                        baselineObservation = observation(audit);
                        ModConfig.CONFIG_JEI.hideRecipesIfMissingResearch = false;
                        transition("hide-disabled-audit");
                    }
                }
                case "hide-disabled-audit" -> {
                    JsonObject audit = passingAudit();
                    if (audit != null && observation(audit) > baselineObservation && !audit.get("hideUnknownResearch").getAsBoolean()) {
                        recordStep("hide-disabled-live-runtime", audit); transition("capture-screens");
                    }
                }
                case "capture-screens" -> {
                    JsonObject screens = readReport(directory.resolve("jei-screens.json"));
                    if (passed(screens)) {
                        recordStep("eight-rendered-jei-screens", screens);
                        ModConfig.CONFIG_JEI.hideRecipesIfMissingResearch = true;
                        JsonObject beforeUnlock = passingAudit(); baselineObservation = observation(beforeUnlock);
                        baselineKnowledgeDigest = beforeUnlock.get("knowledgeDigest").getAsString();
                        unlockOwner(minecraft); transition("research-unlock");
                    }
                }
                case "research-unlock" -> {
                    if (operationDone()) {
                        JsonObject audit = passingAudit();
                        if (audit != null && observation(audit) > baselineObservation && audit.get("hideUnknownResearch").getAsBoolean()
                            && !baselineKnowledgeDigest.equals(audit.get("knowledgeDigest").getAsString()) && grantedResearch > 0) {
                            JsonObject details = audit.deepCopy(); details.addProperty("fixtureResearchKeysGranted", grantedResearch);
                            recordStep("actual-owner-knowledge-s2c-unlock", details);
                            if (Boolean.getBoolean("thaumcraft.sourceRecipeClientAudit")) {
                                JsonObject sourceRecipes = thaumcraft.client.gui.SourceRecipeClientAudit.run(minecraft);
                                Files.writeString(directory.resolve("source-recipe-client-audit.json"), sourceRecipes.toString() + "\n", StandardCharsets.UTF_8);
                                recordStep("all-live-source-recipe-displays", sourceRecipes);
                                require(sourceRecipes.get("passed").getAsBoolean(), "Source book/model audit failures: "
                                    + sourceRecipes.get("missingBookRecipes") + "; " + sourceRecipes.get("missingModels"));
                            }
                            require(isReady(), "Live API fixture requires the private owner connection");
                            baselineRecipes = ClientJeiRecipeSnapshot.revision();
                            serverOperation(minecraft, server -> {
                                require(!ThaumcraftApi.getCraftingRecipes().containsKey(API_ID), "Reserved review API recipe ID already exists");
                                apiFixture = new CrucibleRecipe("", new ItemStack(Items.DIAMOND), Items.APPLE, new AspectList().add(Aspect.AIR, 1));
                                ThaumcraftApi.addCrucibleRecipe(API_ID, apiFixture);
                            }); transition("api-add");
                        }
                    }
                }
                case "api-add" -> {
                    if (operationDone() && apiReceived(Items.DIAMOND, 1, true)) {
                        recordStep("live-api-add-accepted", apiEvidence()); baselineRecipes = ClientJeiRecipeSnapshot.revision();
                        serverOperation(minecraft, server -> apiFixture.setAspects(new AspectList().add(Aspect.AIR, 2)));
                        transition("api-mutate");
                    }
                }
                case "api-mutate" -> {
                    if (operationDone() && apiReceived(Items.DIAMOND, 2, true)) {
                        recordStep("live-api-mutable-cost-update-accepted", apiEvidence()); baselineRecipes = ClientJeiRecipeSnapshot.revision();
                        serverOperation(minecraft, server -> {
                            apiFixture = new CrucibleRecipe("", new ItemStack(Items.EMERALD), Items.APPLE, new AspectList().add(Aspect.AIR, 2));
                            ThaumcraftApi.addCrucibleRecipe(API_ID, apiFixture);
                        }); transition("api-replace");
                    }
                }
                case "api-replace" -> {
                    if (operationDone() && apiReceived(Items.EMERALD, 2, true)) {
                        recordStep("live-api-result-replacement-accepted", apiEvidence()); baselineRecipes = ClientJeiRecipeSnapshot.revision();
                        serverOperation(minecraft, server -> { ThaumcraftApi.getCraftingRecipes().remove(API_ID, apiFixture); apiFixture = null; });
                        transition("api-delete");
                    }
                }
                case "api-delete" -> {
                    if (operationDone() && apiReceived(Items.EMERALD, 2, false)) {
                        recordStep("live-api-delete-accepted-and-cleaned", apiEvidence());
                        JsonObject audit = passingAudit(); baselineObservation = observation(audit);
                        baselineAspects = ClientAspectSnapshot.view().revision(); baselineRecipes = ClientJeiRecipeSnapshot.revision();
                        serverReload(minecraft); transition("reload");
                    }
                }
                case "reload" -> {
                    if (operationDone()) {
                        JsonObject audit = passingAudit();
                        if (audit != null && observation(audit) > baselineObservation && ClientAspectSnapshot.view().revision() > baselineAspects) {
                            JsonObject details = audit.deepCopy(); details.addProperty("previousRecipeRevision", baselineRecipes);
                            details.addProperty("unchangedApiBytesMayRetainRecipeRevision", true);
                            recordStep("actual-datapack-reload-and-bookmark-generation", details);
                            baselineConnection = audit.get("connectionGeneration").getAsLong(); baselineObservation = observation(audit);
                            minecraft.level.disconnect(); ownedServer = null;
                            minecraft.disconnect(new TitleScreen()); transition("reopen");
                        }
                    }
                }
                case "reopen" -> {
                    if (minecraft.getSingleplayerServer() == null && minecraft.screen instanceof TitleScreen) {
                        validateWorldPath(minecraft, true);
                        minecraft.createWorldOpenFlows().openWorld(WORLD, () -> fail("Native reopen failed for the newly created private review world"));
                        transition("reconnect-audit");
                    }
                }
                case "reconnect-audit" -> {
                    captureServer(minecraft);
                    reportWaitingDiagnostic(minecraft);
                    if (isReady()) {
                        JsonObject audit = passingAudit();
                        if (audit != null && observation(audit) > baselineObservation
                            && audit.get("connectionGeneration").getAsLong() > baselineConnection) {
                            recordStep("actual-disconnect-reconnect-new-owner-connection", audit); transition("transfer");
                        }
                    }
                }
                case "transfer" -> {
                    JsonObject transfer = readReport(directory.resolve("jei-transfer.json"));
                    if (passed(transfer)) { recordStep("real-client-transfer-and-paid-take", transfer); if (ClientGameplayReview.enabled()) transition("gameplay-menus"); else finish(true, null); }
                }
                case "gameplay-menus" -> { if (ClientGameplayReview.tick(minecraft)) { recordStep("native-gameplay-menus-and-spa-button", ClientGameplayReview.evidence()); finish(true, null); } }
                default -> { }
            }
        } catch (Throwable failure) { fail(failure.toString()); }
    }

    private static void initialize(Minecraft minecraft) throws IOException {
        startedNanos = phaseNanos = System.nanoTime();
        String configured = System.getProperty("thaumcraft.jeiReviewRoot");
        require(configured != null && !configured.isBlank(), "Private review launcher must specify thaumcraft.jeiReviewRoot");
        Path root = Path.of(configured).toAbsolutePath().normalize();
        require(root.getFileName().toString().equals("jei-review") && root.getParent().getFileName().toString().equals("build")
            && root.getParent().getParent().getFileName().toString().equals("port"), "Review root must be port/build/jei-review");
        Path actual = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        require(actual.getFileName().toString().equals("client") && actual.getParent().getParent().equals(root)
            && actual.getParent().getFileName().toString().matches("[A-Za-z0-9_-]{1,80}"), "Client must be in one private jei-review run");
        verifyUnlinked(actual); directory = actual;
        worldPath = actual.resolve("saves").resolve(WORLD);
        String audit = System.getProperty("thaumcraft.jeiAuditReport", actual.resolve("jei-audit.json").toString());
        auditPath = Path.of(audit).toAbsolutePath().normalize();
        require(auditPath.getParent().equals(directory), "JEI audit report must stay inside this private client directory");
        require(minecraft.level == null && minecraft.getSingleplayerServer() == null, "Review starts at title with no existing world");
        validateWorldPath(minecraft, false);
        require(!Files.exists(directory.resolve("jei-review-scenario.json")), "Private review run already has a scenario report");
        casterVisual = Boolean.getBoolean("thaumcraft.casterVisualReview");
        if (casterVisual) {
            require(!Boolean.getBoolean("thaumcraft.tcpBootstrapAudit") && !Boolean.getBoolean("thaumcraft.tcpBootstrapReloadAudit")
                && !Boolean.getBoolean("thaumcraft.tcpAlchemyAudit"), "Caster visual fixtures must be separate from native survival audits");
            var process = ProcessHandle.current(); var info = process.info(); clientProcess = new JsonObject();
            clientProcess.addProperty("pid", process.pid()); clientProcess.addProperty("startUtc", info.startInstant().orElseThrow().toString());
            clientProcess.addProperty("executable", info.command().orElseThrow());
            JsonArray arguments = new JsonArray(); for (String argument : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) arguments.add(argument);
            clientProcess.add("vmInputArguments", arguments); clientProcess.addProperty("actualNativeMainThread", minecraft.isSameThread());
        }
        withJei = ModList.get().isLoaded("jei");
        require(withJei == !Boolean.getBoolean("thaumcraft.jeiReviewWithoutJei"), "Actual JEI loading differs from the requested review mode");
        originalHide = ModConfig.CONFIG_JEI.hideRecipesIfMissingResearch; configCaptured = true;
        require(!withJei || originalHide, "With-JEI review begins with default hide-unknown setting enabled");
        minecraft.options.pauseOnLostFocus = false;
        initialized = true; transition("waiting-title");
    }

    /** Two declared creative fixtures in the existing fresh-world harness. Only a
     * genuine vanilla SWAP moves the received owner inventory on the client. */
    private static void casterVisualTick(Minecraft minecraft) throws Exception {
        require(casterVisual && isReady() && minecraft.isSameThread(), "Caster view requires its actual private native owner");
        require(minecraft.player.containerMenu == minecraft.player.inventoryMenu && minecraft.player.inventoryMenu.getCarried().isEmpty()
            && minecraft.player.getInventory().selected == 0, "Caster view requires the ordinary selected empty-cursor owner menu");
        if (phase.equals("caster-fixtures")) {
            if (phaseTicks == 1) {
                java.util.UUID owner = minecraft.player.getUUID();
                serverOperation(minecraft, server -> {
                    var player = server.getPlayerList().getPlayer(owner);
                    require(player != null && player.getAbilities().instabuild && player.getInventory().isEmpty()
                        && player.containerMenu == player.inventoryMenu, "Caster fixtures require an actual empty fresh creative owner");
                    var empty = new ItemStack(ItemsTC.casterBasic); var focused = new ItemStack(ItemsTC.casterBasic);
                    ((ItemCaster) focused.getItem()).setFocus(focused, new ItemStack(ItemsTC.focus1));
                    JsonObject proof = new JsonObject(); proof.addProperty("ownerUuid", owner.toString());
                    proof.addProperty("actualOwnedServerThread", server.isSameThread()); proof.addProperty("actualCreativeOwner", true);
                    proof.addProperty("inventoryBeforeNbt", player.getInventory().save(new ListTag()).toString());
                    player.getInventory().setItem(0, empty); player.getInventory().setItem(1, focused); player.getInventory().setChanged();
                    proof.addProperty("inventoryAfterNbt", player.getInventory().save(new ListTag()).toString());
                    proof.addProperty("emptyStackNbt", empty.saveOptional(server.registryAccess()).toString());
                    proof.addProperty("focusedStackNbt", focused.saveOptional(server.registryAccess()).toString());
                    player.inventoryMenu.sendAllDataToRemote(); proof.addProperty("nativeFullMenuS2CPublished", true);
                    proof.addProperty("actualServerMenuId", player.inventoryMenu.containerId);
                    proof.addProperty("serverPublishedStateId", player.inventoryMenu.getStateId()); casterFixtures = proof;
                });
            }
            if (!operationDone() || casterFixtures == null || minecraft.player.inventoryMenu.getStateId() != casterFixtures.get("serverPublishedStateId").getAsInt()
                || !casterInventoryMatches(minecraft, casterFixtures.get("inventoryAfterNbt").getAsString()) || minecraft.screen != null) return;
            if (++casterSettleTicks < 20) return;
            casterFixtures = casterFixtures.deepCopy(); casterFixtures.addProperty("actualAcceptedS2CStateId", minecraft.player.inventoryMenu.getStateId());
            casterFixtures.addProperty("actualOwnerS2C", true); recordStep("caster-owned-server-fixtures-received", casterFixtures);
            for (String name : new String[]{"caster_basic", "caster_advanced"}) {
                var id = ResourceLocation.parse("thaumcraft:entity/armor/" + name);
                var sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id); var contents = sprite.contents();
                require(contents.name().equals(id) && contents.width() == 32 && contents.height() == 32, "Original caster sprite missing from actual native block atlas: " + id);
                JsonObject row = new JsonObject(); row.addProperty("spriteId", id.toString()); row.addProperty("width", contents.width()); row.addProperty("height", contents.height()); row.addProperty("missing", false); CASTER_ATLAS.add(row);
            }
            JsonObject atlas = new JsonObject(); atlas.add("sprites", CASTER_ATLAS.deepCopy()); recordStep("caster-two-source-atlas-sprites", atlas);
            captureCasterFrame(minecraft, false); transition("caster-empty-frame"); return;
        }
        if (casterScreenshotPending) return;
        if (phase.equals("caster-empty-frame")) {
            require(casterInventoryMatches(minecraft, casterFixtures.get("inventoryAfterNbt").getAsString()), "Empty caster fixture changed before native swap");
            casterSwap = new JsonObject(); casterSwap.addProperty("menuId", minecraft.player.inventoryMenu.containerId);
            casterSwap.addProperty("menuStateIdBefore", minecraft.player.inventoryMenu.getStateId()); casterSwap.addProperty("slot", 37); casterSwap.addProperty("button", 0);
            casterSwap.addProperty("clickType", "SWAP"); casterSwap.addProperty("nativeC2S", true);
            casterSwap.addProperty("inventoryBeforeNbt", minecraft.player.getInventory().save(new ListTag()).toString());
            minecraft.gameMode.handleInventoryMouseClick(casterSwap.get("menuId").getAsInt(), 37, 0, ClickType.SWAP, minecraft.player);
            recordStep("caster-native-swap-dispatched", casterSwap); transition("caster-swap-receipt"); return;
        }
        if (phase.equals("caster-swap-receipt")) {
            if (casterServerSwap == null) {
                if (!operationDone()) return;
                java.util.UUID owner = minecraft.player.getUUID();
                serverOperation(minecraft, server -> {
                    var player = server.getPlayerList().getPlayer(owner); require(player != null && player.containerMenu == player.inventoryMenu, "Caster swap owner changed");
                    try {
                        if (!TagParser.parseTag(player.getInventory().getItem(0).saveOptional(server.registryAccess()).toString()).equals(TagParser.parseTag(casterFixtures.get("focusedStackNbt").getAsString()))
                            || !TagParser.parseTag(player.getInventory().getItem(1).saveOptional(server.registryAccess()).toString()).equals(TagParser.parseTag(casterFixtures.get("emptyStackNbt").getAsString()))) return;
                    } catch (Exception invalid) { throw new IllegalStateException(invalid); }
                    JsonObject proof = casterSwap.deepCopy(); proof.addProperty("inventoryAfterNbt", player.getInventory().save(new ListTag()).toString());
                    proof.addProperty("actualServerAccepted", true); proof.addProperty("actualOwnedServerThread", server.isSameThread());
                    player.inventoryMenu.sendAllDataToRemote(); proof.addProperty("nativeFullMenuS2CPublished", true);
                    proof.addProperty("serverPublishedStateId", player.inventoryMenu.getStateId()); casterServerSwap = proof;
                }); return;
            }
            if (minecraft.player.inventoryMenu.getStateId() != casterServerSwap.get("serverPublishedStateId").getAsInt()
                || !casterInventoryMatches(minecraft, casterServerSwap.get("inventoryAfterNbt").getAsString())) return;
            casterSwap = casterServerSwap.deepCopy(); casterSwap.addProperty("actualAcceptedS2CStateId", minecraft.player.inventoryMenu.getStateId());
            casterSwap.addProperty("actualOwnerS2C", true); recordStep("caster-native-swap-server-and-s2c-received", casterSwap);
            casterSettleTicks = 0; transition("caster-focused-settle"); return;
        }
        if (phase.equals("caster-focused-settle")) {
            require(casterInventoryMatches(minecraft, casterSwap.get("inventoryAfterNbt").getAsString()), "Focused native caster inventory changed");
            if (++casterSettleTicks < 20) return;
            captureCasterFrame(minecraft, true); transition("caster-focused-frame"); return;
        }
        require(CASTER_FRAMES.size() == 2 && CASTER_SCREENSHOTS.size() == 2, "Both actual first-person caster frames must complete");
        recordStep("caster-texture-visual-complete", commonEvidence()); finish(true, null);
    }

    private static boolean casterInventoryMatches(Minecraft minecraft, String expected) throws Exception {
        return TagParser.parseTag("{Items:" + minecraft.player.getInventory().save(new ListTag()) + "}")
            .equals(TagParser.parseTag("{Items:" + expected + "}"));
    }

    private static void captureCasterFrame(Minecraft minecraft, boolean focused) throws Exception {
        ItemStack stack = minecraft.player.getMainHandItem(); require(stack.is(ItemsTC.casterBasic) && minecraft.screen == null
            && minecraft.options.getCameraType().isFirstPerson(), "Actual caster frame must render the registered held item in first person");
        var property = net.minecraft.client.renderer.item.ItemProperties.getProperty(stack, ResourceLocation.withDefaultNamespace("focus"));
        float actualProperty = property == null ? -1 : property.call(stack, minecraft.level, minecraft.player, 0);
        require(actualProperty == (focused ? 1 : 0), "Native caster focus property did not select its original source model");
        var model = minecraft.getItemRenderer().getModel(stack, minecraft.level, minecraft.player, 0);
        var quads = model.getQuads(null, null, net.minecraft.util.RandomSource.create(0)); int tint0 = 0, tint1 = 0;
        java.util.Set<String> spriteNames = new java.util.TreeSet<>();
        for (var quad : quads) {
            String sprite = quad.getSprite().contents().name().toString(); require(sprite.equals("thaumcraft:entity/armor/caster_basic"), "Native held caster uses missing/wrong texture: " + sprite); spriteNames.add(sprite);
            if (quad.getTintIndex() == 0) tint0++; else if (quad.getTintIndex() == 1) tint1++;
        }
        require(quads.size() == (focused ? 15 : 10) && tint0 == 10 && tint1 == (focused ? 5 : 0), "Native caster baked geometry differs from original gauntlet/focus faces");
        JsonObject frame = new JsonObject(); String variant = focused ? "focused" : "empty"; frame.addProperty("variant", variant);
        frame.addProperty("heldStackNbt", stack.saveOptional(minecraft.level.registryAccess()).toString()); frame.addProperty("focusProperty", actualProperty);
        frame.addProperty("actualOwnerS2C", true); frame.addProperty("actualAcceptedS2CStateId", minecraft.player.inventoryMenu.getStateId());
        frame.addProperty("actualNativeItemModel", true); frame.addProperty("firstPerson", true); frame.addProperty("nativeClientTick", ticks);
        frame.addProperty("quads", quads.size()); frame.addProperty("tint0Quads", tint0); frame.addProperty("tint1Quads", tint1);
        JsonArray sprites = new JsonArray(); spriteNames.forEach(sprites::add); frame.add("sprites", sprites); CASTER_FRAMES.add(frame);
        recordStep("caster-native-" + variant + "-model", frame);
        require(minecraft.getMainRenderTarget().width == 1280 && minecraft.getMainRenderTarget().height == 720, "Caster frame requires the actual requested 1280x720 native framebuffer");
        String name = "caster-basic-" + variant + ".png"; Path file = directory.resolve("screenshots").resolve(name);
        require(!Files.exists(file), "Caster native screenshot must be fresh"); casterScreenshotPending = true;
        Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(), message -> minecraft.execute(() -> {
            try {
                require(Files.isRegularFile(file) && Files.size(file) > 1024, "Actual caster framebuffer screenshot did not finish");
                JsonObject shot = new JsonObject(); shot.addProperty("variant", variant); shot.addProperty("path", file.toString());
                shot.addProperty("width", 1280); shot.addProperty("height", 720); shot.addProperty("nativeFramebufferCallback", true); shot.addProperty("nativeClientTick", ticks);
                CASTER_SCREENSHOTS.add(shot); casterScreenshotPending = false; recordStep("caster-native-" + variant + "-screenshot", shot);
            } catch (Exception failure) { fail(failure.toString()); }
        }));
    }

    private static void verifyUnlinked(Path path) throws IOException {
        for (Path cursor = path; cursor != null; cursor = cursor.getParent()) {
            if (!Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) continue;
            BasicFileAttributes attributes = Files.readAttributes(cursor, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            require(!attributes.isSymbolicLink() && !attributes.isOther() && cursor.toRealPath().equals(cursor), "Linked review path is forbidden: " + cursor);
            if (cursor.getFileSystem().getSeparator().equals("\\")) {
                Object raw = Files.getAttribute(cursor, "dos:attributes", LinkOption.NOFOLLOW_LINKS);
                require(!(raw instanceof Number number) || (number.intValue() & 0x400) == 0, "Reparse review path is forbidden: " + cursor);
            }
        }
    }

    private static void validateWorldPath(Minecraft minecraft, boolean mustExist) throws IOException {
        require(minecraft.getLevelSource().getBaseDir().toAbsolutePath().normalize().equals(directory.resolve("saves")), "Native save source must belong to this private directory");
        require(minecraft.getLevelSource().getLevelPath(WORLD).toAbsolutePath().normalize().equals(worldPath), "Native world path differs from the reserved fresh save");
        verifyUnlinked(worldPath);
        require(Files.exists(worldPath, LinkOption.NOFOLLOW_LINKS) == mustExist, mustExist ? "New review world is missing" : "Refusing an existing jei-review save");
    }

    private static void createWorld(Minecraft minecraft, TitleScreen title) throws IOException {
        validateWorldPath(minecraft, false);
        var rules = new GameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        var settings = new LevelSettings("Thaumcraft JEI private review", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
            rules, WorldDataConfiguration.DEFAULT);
        created = true; transition("waiting-world");
        minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(SEED, false, false),
            registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), title);
    }

    private static void captureServer(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (created && server != null && !server.isPublished() && server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(worldPath)) ownedServer = server;
    }

    /** One bounded current observation, refreshed every five seconds while gates are pending. */
    private static void reportWaitingDiagnostic(Minecraft minecraft) {
        if (phaseTicks != 1 && phaseTicks % 100 != 0) return;
        var row = new JsonObject();
        var server = minecraft.getSingleplayerServer();
        var listener = minecraft.getConnection();
        var aspects = ClientAspectSnapshot.view();
        var research = ClientResearchPresentation.view();
        var catalog = ClientResearchCatalogHandlers.acceptedViewForCurrentConnection();
        row.addProperty("observedAt", Instant.now().toString());
        row.addProperty("phaseTicks", phaseTicks);
        row.addProperty("levelPresent", minecraft.level != null);
        row.addProperty("playerPresent", minecraft.player != null);
        row.addProperty("serverPresent", server != null);
        row.addProperty("serverOwned", owns(server));
        row.addProperty("serverIdentityCaptured", server != null && server == ownedServer);
        row.addProperty("expectedWorldPath", worldPath.toString());
        if (server != null) {
            row.addProperty("actualWorldPath", server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString());
            row.addProperty("serverPublished", server.isPublished());
        }
        row.addProperty("connectionPresent", listener != null);
        row.addProperty("memoryConnection", listener != null && listener.getConnection().isMemoryConnection());
        row.addProperty("aspectReady", aspects != null);
        row.addProperty("aspectRevision", aspects == null ? 0 : aspects.revision());
        row.addProperty("researchReady", research != null);
        row.addProperty("researchCatalogAccepted", catalog != null);
        row.addProperty("researchCatalogOwnerReady", ClientResearchCatalogHandlers.readyForCurrentConnection());
        if (catalog != null) row.addProperty("researchCatalogDigest", catalog.digest());
        row.addProperty("metadataReady", ClientJeiRecipeSnapshot.ready());
        row.addProperty("metadataRevision", ClientJeiRecipeSnapshot.revision());
        row.addProperty("screen", minecraft.screen == null ? "none" : minecraft.screen.getClass().getName());
        waitingDiagnostic = row;
        writeState(false, null);
    }

    private static JsonObject commonEvidence() {
        var report = new JsonObject(); report.addProperty("privateWorld", worldPath.toString()); report.addProperty("fixedSeed", SEED);
        report.addProperty("creative", true); report.addProperty("cheats", true); report.addProperty("peaceful", true);
        report.addProperty("flat", true); report.addProperty("actualMemoryConnection", Minecraft.getInstance().getConnection().getConnection().isMemoryConnection());
        report.addProperty("aspectRevision", ClientAspectSnapshot.view().revision()); report.addProperty("recipeRevision", ClientJeiRecipeSnapshot.revision());
        report.addProperty("researchCatalogDigest", ClientResearchPresentation.view().digest()); return report;
    }

    private static void finishWithoutJei() {
        require(!ModList.get().isLoaded("jei"), "Without-JEI review unexpectedly loaded JEI");
        JsonObject report = commonEvidence(); report.addProperty("jeiLoaded", false);
        report.addProperty("commonNativeRecipeClassesLoaded", !ClientJeiRecipeSnapshot.crucible().isEmpty() && !ClientJeiRecipeSnapshot.infusion().isEmpty());
        require(report.get("commonNativeRecipeClassesLoaded").getAsBoolean(), "Without-JEI connection must accept native recipe metadata");
        recordStep("without-jei-native-client-and-three-accepted-snapshots", report); finish(true, null);
    }

    private static void unlockOwner(Minecraft minecraft) {
        java.util.UUID owner = minecraft.player.getUUID();
        serverOperation(minecraft, server -> {
            var player = server.getPlayerList().getPlayer(owner); require(player != null, "Actual private owner missing");
            var knowledge = ThaumcraftCapabilities.getKnowledge(player); int count = 0;
            for (var category : ResearchCategories.researchCategories.values()) for (var entry : category.research.values()) {
                knowledge.addResearch(entry.getKey()); knowledge.setResearchStage(entry.getKey(), entry.getStages() == null ? 1 : entry.getStages().length + 1); count++;
            }
            grantedResearch = count; PlayerDataSync.syncKnowledgeNow(player);
        });
    }

    private interface ServerWork { void run(MinecraftServer server); }
    private static void serverOperation(Minecraft minecraft, ServerWork work) {
        var server = minecraft.getSingleplayerServer(); require(isReady() && owns(server), "Server fixture may only touch the owned private world");
        operation = new CompletableFuture<>(); var pending = operation;
        server.execute(() -> { try { require(owns(server), "Private server ownership changed"); work.run(server); pending.complete(null); }
            catch (Throwable failure) { pending.completeExceptionally(failure); } });
    }

    private static void serverReload(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer(); require(isReady() && owns(server), "Reload may only touch the owned private world");
        operation = new CompletableFuture<>(); var pending = operation;
        server.execute(() -> { try { require(owns(server), "Private reload ownership changed");
            server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((ignored, failure) -> {
                if (failure == null) pending.complete(null); else pending.completeExceptionally(failure);
            }); } catch (Throwable failure) { pending.completeExceptionally(failure); } });
    }

    private static boolean operationDone() {
        if (operation == null || !operation.isDone()) return false;
        operation.join(); return true;
    }

    private static boolean apiReceived(net.minecraft.world.item.Item output, int air, boolean present) throws IOException {
        if (!isReady() || ClientJeiRecipeSnapshot.revision() <= baselineRecipes) return false;
        var holder = ClientJeiRecipeSnapshot.crucible().stream().filter(recipe -> recipe.id().equals(API_ID)).findFirst();
        if (present != holder.isPresent()) return false;
        if (present && (!holder.get().value().getRecipeOutput().is(output) || holder.get().value().getAspects().getAmount(Aspect.AIR) != air)) return false;
        JsonObject audit = passingAudit();
        return audit != null && audit.get("recipeRevision").getAsLong() == ClientJeiRecipeSnapshot.revision();
    }

    private static JsonObject apiEvidence() {
        var report = new JsonObject(); report.addProperty("sourceId", API_ID.toString()); report.addProperty("acceptedRevision", ClientJeiRecipeSnapshot.revision());
        var holder = ClientJeiRecipeSnapshot.crucible().stream().filter(recipe -> recipe.id().equals(API_ID)).findFirst();
        report.addProperty("present", holder.isPresent());
        holder.ifPresent(recipe -> { report.addProperty("result", recipe.value().getRecipeOutput().getItem().toString()); report.addProperty("air", recipe.value().getAspects().getAmount(Aspect.AIR)); });
        return report;
    }

    private static JsonObject passingAudit() throws IOException {
        JsonObject audit = readReport(auditPath);
        if (audit == null) return null;
        require(!audit.has("passed") || audit.get("passed").getAsBoolean(), "Actual JEI runtime audit failed: " + audit.get("failures"));
        return passed(audit) ? audit : null;
    }
    private static boolean passed(JsonObject report) { return report != null && report.has("passed") && report.get("passed").getAsBoolean(); }
    private static long observation(JsonObject report) { return report == null ? 0 : report.get("observation").getAsLong(); }
    private static JsonObject readReport(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) return null;
        require(Files.size(path) < 64L * 1024 * 1024, "Review report exceeds bounded size");
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    public static void recordStep(String name, JsonObject details) {
        if (!enabled() || directory == null || stopped) return;
        if (!Minecraft.getInstance().isSameThread()) {
            JsonObject copy = details == null ? null : details.deepCopy();
            Minecraft.getInstance().execute(() -> recordStep(name, copy)); return;
        }
        var row = new JsonObject(); row.addProperty("step", name); row.addProperty("at", Instant.now().toString());
        JsonObject evidence = details == null ? new JsonObject() : details.deepCopy();
        evidence.remove("observations"); // Full runtime history already lives in jei-audit.json.
        row.add("details", evidence); STEPS.add(row); writeState(false, null);
    }
    private static void transition(String next) {
        phase = next; phaseNanos = System.nanoTime(); phaseTicks = 0; writeState(false, null);
    }
    public static void fail(String reason) {
        if (!enabled() || stopped) return;
        if (!Minecraft.getInstance().isSameThread()) { Minecraft.getInstance().execute(() -> fail(reason)); return; }
        Thaumcraft.log.error("Private JEI review failed: {}", reason); finish(false, reason);
    }
    private static void finish(boolean passed, String error) {
        if (stopped) return;
        if (configCaptured) ModConfig.CONFIG_JEI.hideRecipesIfMissingResearch = originalHide;
        var server = ownedServer;
        if (apiFixture != null && owns(server)) server.execute(() -> ThaumcraftApi.getCraftingRecipes().remove(API_ID, apiFixture));
        phase = passed ? "complete" : "failed"; writeState(passed, error); stopped = true;
        Minecraft.getInstance().stop();
    }
    private static void writeState(boolean passed, String error) {
        if (directory == null) return;
        var report = new JsonObject(); report.addProperty("schemaVersion", 1); report.addProperty("purpose", casterVisual ? "thaumcraft-caster-texture-review" : "thaumcraft-jei-private-client-review");
        report.addProperty("phase", phase); report.addProperty("passed", passed); report.addProperty("withJei", withJei);
        report.addProperty("clientDirectory", directory.toString()); report.addProperty("world", WORLD); report.addProperty("createdFreshWorld", created);
        report.addProperty("observedAt", Instant.now().toString()); report.addProperty("clientTicks", ticks);
        if (waitingDiagnostic != null) report.add("waitingDiagnostic", waitingDiagnostic.deepCopy());
        if (casterVisual) {
            report.addProperty("casterVisualReview", true); report.addProperty("mode", "caster-textures"); report.addProperty("registeredCasterItem", "thaumcraft:caster_basic");
            report.addProperty("advancedItemTested", false); report.addProperty("researchFixturesGranted", grantedResearch);
            report.add("clientProcess", clientProcess.deepCopy()); report.add("casterFrames", CASTER_FRAMES.deepCopy());
            report.add("casterAtlas", CASTER_ATLAS.deepCopy()); report.add("casterScreenshots", CASTER_SCREENSHOTS.deepCopy());
            if (casterFixtures != null) report.add("casterFixtures", casterFixtures.deepCopy()); if (casterSwap != null) report.add("casterSwap", casterSwap.deepCopy());
        }
        if (error != null) report.addProperty("failure", error); report.add("steps", STEPS.deepCopy()); if (ClientGameplayReview.enabled() && (phase.equals("gameplay-menus") || phase.equals("failed") || phase.equals("complete"))) report.add("gameplayReview", ClientGameplayReview.evidence());
        Path target = directory.resolve("jei-review-scenario.json");
        // An invalid reused run must not overwrite its previous review evidence.
        if (!initialized && Files.exists(target)) target = directory.resolve("jei-review-bootstrap-failed-" + System.nanoTime() + ".json");
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            if (casterVisual) Files.writeString(directory.resolve("caster-texture-review.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
        } catch (IOException failure) { Thaumcraft.log.error("Cannot write private JEI review state", failure); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
