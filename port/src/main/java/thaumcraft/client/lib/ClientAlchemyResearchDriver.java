package thaumcraft.client.lib;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.items.IScribeTools;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.client.gui.GuiResearchBrowser;
import thaumcraft.client.gui.GuiResearchPage;
import thaumcraft.client.gui.GuiResearchTable;
import thaumcraft.client.gui.model.ResearchBrowserProjection;
import thaumcraft.common.container.ContainerResearchTable;
import thaumcraft.common.lib.research.theorycraft.SourceTheorycraftCard;
import thaumcraft.common.lib.research.theorycraft.TheorycraftInventory;

/** Default-off native GUI driver for the private dedicated alchemy cohort. Every mutation
 * is an ordinary screen click or vanilla inventory transaction; reflections only read the
 * displayed source model. Draws, note ownership, requirements, card RNG and awards remain
 * production server code. The controller opens the real book/table and records screenshots.
 * No stage/knowledge grants, direct server helper, packet construction or RNG replacement. */
public final class ClientAlchemyResearchDriver {
    private static final int MAX_NOTES = 128, MAX_CARD_SELECTIONS = 2048;
    private final Minecraft client;
    private final JsonArray events = new JsonArray(), pendingEvents = new JsonArray();
    private final JsonArray researchReceipts = new JsonArray(), theoryReceipts = new JsonArray();
    private final ArrayDeque<Integer> inventoryClicks = new ArrayDeque<>();
    private final ArrayList<String> screenshots = new ArrayList<>();
    private final Set<String> requestedScreenshots = new HashSet<>();
    private int ticks, nextActionTick, notesStarted, cardsSelected, notesFinished, notesScrapped;
    private String researchKey, pendingResearchKey, theoryCategory, waiting = "idle";
    private int researchTarget, pendingResearchStage, pendingResearchAt;
    private JsonObject pendingResearch;
    private String pendingTheoryAction;
    private long pendingTheoryGeneration, pendingCardSeed;
    private int pendingTheoryAt, pendingCardIndex;
    private String pendingCardKey;
    private JsonObject pendingTheory;
    private JsonObject finishExpected;
    private JsonObject finishBefore;
    private int finishSentTick;

    public ClientAlchemyResearchDriver(Minecraft client) {
        this.client = client;
        require(Boolean.getBoolean("thaumcraft.tcpAlchemyAudit") || Boolean.getBoolean("thaumcraft.tcpCasterAudit"), "Private native research driver is disabled");
    }

    /** Caller must open the real Thaumonomicon. A target is the exact source stored stage,
     * including source final-page skipping (e.g. ALUMENTUM1->3, INFUSION2->4). */
    public boolean tickResearch(String key, int targetStoredStage) throws Exception {
        enterTick();
        require(key != null && !key.isBlank() && targetStoredStage > 0, "Invalid native research goal");
        var view = ClientResearchPresentation.view();
        if (view == null) { waiting = "accepted-owner-research-snapshot"; return false; }
        if (pendingResearchKey != null) {
            int actual = view.storedStage(pendingResearchKey);
            if (actual == pendingResearchStage) {
                require(ticks - pendingResearchAt < 400, "Native research request did not advance " + pendingResearchKey);
                waiting = "native-research-owner-s2c"; return false;
            }
            require(actual > pendingResearchStage, "Actual native research stage moved backwards");
            pendingResearch.addProperty("storedStageAfter", actual);
            pendingResearch.addProperty("completeAfter", view.knowsStrict(pendingResearchKey));
            pendingResearch.addProperty("actualAcceptedOwnerS2C", true);
            researchReceipts.add(pendingResearch.deepCopy());
            record("accepted-native-research-owner-snapshot", pendingResearch);
            pendingResearchKey = null; pendingResearch = null; nextActionTick = ticks + 8;
        }
        researchKey = key; researchTarget = targetStoredStage;
        if (view.storedStage(key) >= targetStoredStage) { waiting = "research-goal-received"; return true; }
        if (ticks < nextActionTick) return false;
        if (client.screen instanceof GuiResearchPage page) {
            if (!key.equals(field(page, "key"))) {
                require(page.mouseClicked((page.width - 256) / 2D + 128, (page.height - 181) / 2D + 196, 0),
                    "Native research-page back control rejected click");
                nextActionTick = ticks + 5; waiting = "native-research-page-back"; return false;
            }
            var request = ClientResearchPresentation.prepare(key);
            if (request == null || request.packet().expectedStage() < 0 || !(Boolean) readMethod(page, "canAttempt")) {
                waiting = "native-research-requirements"; return false;
            }
            int groups = ((List<?>) field(page, "requirements")).size();
            int stage = request.packet().expectedStage();
            screenshot("research-" + key.toLowerCase(Locale.ROOT) + "-" + stage + ".png");
            JsonObject row = researchBefore(key, stage, request.packet().catalogDigest());
            row.addProperty("displayedRequirementGroups", groups);
            require(page.mouseClicked((page.width - 256) / 2D + 32,
                (page.height - 181) / 2D + 180 - groups * 18, 0), "Native paid research page control rejected click");
            pendingResearch(key, stage, row, "native-research-page-submit"); return false;
        }
        if (!(client.screen instanceof GuiResearchBrowser browser)) { waiting = "native-thaumonomicon-screen"; return false; }
        var entry = view.spec().entriesByKey().get(key);
        require(entry != null, "Native research goal is absent from accepted catalog: " + key);
        var model = (ResearchBrowserProjection.Browser) field(browser, "browser");
        if (!entry.category().equals(model.selected())) {
            int index = -1;
            for (int i = 0; i < model.tabs().size(); i++) if (model.tabs().get(i).key().equals(entry.category())) index = i;
            require(index >= 0, "Native research category is still locked: " + entry.category());
            require(browser.mouseClicked(8, 18 + (index + 1) * 24, 0), "Native category tab rejected click");
            nextActionTick = ticks + 4; waiting = "native-category-tab"; return false;
        }
        var node = model.nodes().stream().filter(value -> value.rawSpec().key().equals(key)).findFirst().orElse(null);
        if (node == null || !view.availability(key).mayRequestProgress()) { waiting = "native-browser-node-availability"; return false; }
        double panX = ((Number) field(browser, "panX")).doubleValue();
        double panY = ((Number) field(browser, "panY")).doubleValue();
        double zoom = ((Number) field(browser, "zoom")).doubleValue();
        double x = (24D + node.x() - panX) / zoom, y = (24D + node.y() - panY) / zoom;
        if (x < 20 || x >= browser.width - 20 || y < 20 || y >= browser.height - 20) {
            require(browser.mouseDragged(browser.width / 2D, browser.height / 2D, 0,
                browser.width / 2D - x, browser.height / 2D - y), "Native browser drag rejected");
            nextActionTick = ticks + 4; waiting = "native-browser-pan"; return false;
        }
        int stage = view.storedStage(key);
        JsonObject row = researchBefore(key, stage, view.digest());
        row.addProperty("category", entry.category());
        require(browser.mouseClicked(x, y, 0), "Native research node rejected click");
        require(client.screen instanceof GuiResearchPage, "Native node click must open the genuine research page");
        if (stage == -1) pendingResearch(key, stage, row, "native-browser-start-research");
        else { record("native-browser-open-known-research", row); nextActionTick = ticks + 8; }
        return false;
    }

    private JsonObject researchBefore(String key, int stage, String digest) {
        JsonObject row = new JsonObject(); row.addProperty("researchKey", key);
        row.addProperty("storedStageBefore", stage); row.addProperty("catalogDigest", digest);
        row.addProperty("nativeScreenClick", true); row.add("knowledgeBefore", knowledgePools());
        return row;
    }
    private void pendingResearch(String key, int stage, JsonObject row, String action) {
        pendingResearchKey = key; pendingResearchStage = stage; pendingResearchAt = ticks;
        pendingResearch = row.deepCopy(); record(action, row); waiting = "native-research-owner-s2c";
    }

    /** Caller opens the real owned table. Goals are raw source pools (one theory=32 raw).
     * Naturally drawn cards are scored from received notes and paid from real inventory.
     * Paper and replacement ink are placed by ordinary vanilla PICKUP transactions. */
    public boolean tickTheory(String category, int requiredRaw) throws Exception {
        enterTick(); require(requiredRaw > 0 && (category.equals("ALCHEMY") || category.equals("INFUSION")), "Invalid native theory goal");
        theoryCategory = category;
        var view = ClientResearchPresentation.view();
        if (view == null) { waiting = "accepted-owner-theory-snapshot"; return false; }
        if (!(client.screen instanceof GuiResearchTable screen) || !(client.player.containerMenu instanceof ContainerResearchTable menu)) {
            waiting = "native-research-table-screen"; return false;
        }
        if (!menu.theoryReady() || !menu.theoryOwned()) { waiting = "owned-native-theory-note-state"; return false; }
        if (finishExpected != null) {
            boolean arrived = true;
            for (var entry : finishExpected.entrySet()) if (view.getKnowledgeRaw(EnumKnowledgeType.THEORY, entry.getKey())
                != finishBefore.get(entry.getKey()).getAsInt() + entry.getValue().getAsInt()) arrived = false;
            if (!arrived) {
                require(ticks - finishSentTick < 400, "Completed native theory did not produce exact accepted knowledge pools");
                waiting = "native-theory-award-owner-s2c"; return false;
            }
            JsonObject row = new JsonObject(); row.add("expectedAwards", finishExpected.deepCopy());
            row.add("theoryBefore", finishBefore.deepCopy()); row.add("knowledgeAfter", knowledgePools());
            row.addProperty("actualAcceptedOwnerS2C", true); theoryReceipts.add(row.deepCopy());
            record("accepted-native-theory-award", row); finishExpected = null; finishBefore = null;
        }
        if (pendingTheoryAction != null && !acceptTheoryAction(menu)) return false;
        if (!inventoryClicks.isEmpty()) {
            if (ticks >= nextActionTick) {
                int slot = inventoryClicks.removeFirst();
                client.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, client.player);
                JsonObject row = new JsonObject(); row.addProperty("menuId", menu.containerId); row.addProperty("slot", slot);
                row.addProperty("nativeVanillaC2S", true); record("native-theory-inventory-pickup", row);
                nextActionTick = ticks + 4;
            }
            waiting = "native-theory-material-transactions"; return false;
        }
        ResearchTableData data = menu.theoryData();
        if (view.getKnowledgeRaw(EnumKnowledgeType.THEORY, category) >= requiredRaw && data == null) {
            require(menu.getCarried().isEmpty(), "Native theory goal left an inventory cursor");
            waiting = "theory-goal-received"; return true;
        }
        if (ticks < nextActionTick) return false;
        if (!prepareTableMaterials(menu)) return false;
        if (data == null) {
            require(notesStarted < MAX_NOTES, "Native theory exceeded bounded natural note attempts");
            JsonObject row = theoryBefore(menu); row.addProperty("goalCategory", category);
            require(screen.mouseClicked(left(screen) + 128, top(screen) + 29, 0), "Native create-theory button rejected");
            notesStarted++; pendingTheory(menu, "create", row); return false;
        }
        if (data.cardChoices.stream().anyMatch(choice -> choice.selected)) {
            waiting = "native-gui-selected-card-animation"; return false; // Actual GUI commits itself after350ms.
        }
        if (data.isComplete() && data.cardChoices.isEmpty()) {
            JsonObject row = theoryBefore(menu); row.add("categoryTotals", totals(data));
            finishExpected = awards(data); finishBefore = theoryPools(); finishSentTick = ticks;
            row.add("expectedAwards", finishExpected.deepCopy()); screenshot("theory-" + category.toLowerCase(Locale.ROOT) + "-complete.png");
            require(screen.mouseClicked(left(screen) + 190, top(screen) + 103, 0), "Native finish-theory button rejected");
            notesFinished++; pendingTheory(menu, "finish", row); return false;
        }
        if (data.cardChoices.isEmpty()) {
            JsonObject row = theoryBefore(menu); row.addProperty("nativeDrawCount", data.bonusDraws > 0 ? 3 : 2);
            require(screen.mouseClicked(left(screen) + 60, top(screen) + 95, 0), "Native draw-card surface rejected");
            pendingTheory(menu, "draw", row); return false;
        }
        int selected = chooseCard(data, category);
        if (selected < 0) {
            JsonObject row = theoryBefore(menu); row.add("receivedOfferedCards", cards(data));
            require(screen.mouseClicked(left(screen) + 128, top(screen) + 173, 0), "Native scrap-theory button rejected");
            notesScrapped++; pendingTheory(menu, "scrap", row); return false;
        }
        require(cardsSelected < MAX_CARD_SELECTIONS, "Native theory exceeded bounded natural card selections");
        var choice = data.cardChoices.get(selected);
        pendingCardIndex = selected; pendingCardKey = choice.key; pendingCardSeed = choice.card.serialize().getLong("seed");
        JsonObject row = theoryBefore(menu); row.addProperty("choiceIndex", selected); row.addProperty("cardKey", choice.key);
        row.add("sourceCardNbt", stringTag(choice.card.serialize().toString())); row.add("requiredItems", requirements(choice));
        row.add("receivedOfferedCards", cards(data)); row.add("inventoryBefore", stringTag(client.player.getInventory().save(new net.minecraft.nbt.ListTag()).toString()));
        screenshot("theory-" + category.toLowerCase(Locale.ROOT) + "-cards.png");
        double x = left(screen) + 55 + 128 - 55 * data.cardChoices.size() + 110 * selected;
        require(screen.mouseClicked(x, top(screen) + 100, 0), "Native card selection surface rejected");
        cardsSelected++; pendingTheory(menu, "select", row); return false;
    }

    private boolean acceptTheoryAction(ContainerResearchTable menu) {
        if (menu.receivedStateGeneration() <= pendingTheoryGeneration) {
            require(ticks - pendingTheoryAt < 300, "Native theory action received no server note state: " + pendingTheoryAction);
            waiting = "native-theory-state-s2c"; return false;
        }
        var data = menu.theoryData();
        boolean accepted = switch (pendingTheoryAction) {
            case "create" -> data != null;
            case "draw" -> data != null && !data.cardChoices.isEmpty();
            case "select" -> data != null && (data.cardChoices.stream().anyMatch(choice -> choice.selected
                && choice.key.equals(pendingCardKey) && choice.card.serialize().getLong("seed") == pendingCardSeed)
                || data.lastDraw != null && data.lastDraw.key.equals(pendingCardKey)
                    && data.lastDraw.card.serialize().getLong("seed") == pendingCardSeed);
            case "finish", "scrap" -> data == null;
            default -> false;
        };
        require(accepted, "Actual native theory server state rejected action: " + pendingTheoryAction);
        pendingTheory.addProperty("receivedStateGenerationAfter", menu.receivedStateGeneration());
        pendingTheory.addProperty("sourceRevisionAfter", menu.viewRevision());
        pendingTheory.addProperty("actualAcceptedNoteS2C", true);
        if (data != null) { pendingTheory.addProperty("inspirationAfter", data.inspiration); pendingTheory.add("categoryTotalsAfter", totals(data)); }
        pendingTheory.addProperty("inkDamageAfter", menu.table().getItem(0).getDamageValue());
        pendingTheory.addProperty("paperAfter", menu.table().getItem(1).getCount());
        if (pendingTheoryAction.equals("select")) {
            pendingTheory.add("inventoryAfter", stringTag(client.player.getInventory().save(new net.minecraft.nbt.ListTag()).toString()));
        }
        record("accepted-native-theory-" + pendingTheoryAction, pendingTheory);
        pendingTheoryAction = null; pendingTheory = null; nextActionTick = ticks + 9; return true;
    }
    private void pendingTheory(ContainerResearchTable menu, String action, JsonObject row) {
        pendingTheoryAction = action; pendingTheoryGeneration = menu.receivedStateGeneration(); pendingTheoryAt = ticks;
        pendingTheory = row.deepCopy(); record("native-theory-" + action + "-screen-click", row); waiting = "native-theory-state-s2c";
    }
    private boolean prepareTableMaterials(ContainerResearchTable menu) {
        require(menu.getCarried().isEmpty(), "Unowned cursor at native theory table");
        ItemStack ink = menu.table().getItem(0);
        if (ink.isEmpty() || !(ink.getItem() instanceof IScribeTools) || ink.getDamageValue() >= ink.getMaxDamage()) {
            int replacement = inventorySlot(menu, stack -> !stack.isEmpty() && stack.getItem() instanceof IScribeTools
                && stack.getDamageValue() < stack.getMaxDamage());
            if (replacement < 0) { waiting = "real-replacement-scribing-tools"; return false; }
            if (!ink.isEmpty()) {
                int destination = inventorySlot(menu, ItemStack::isEmpty);
                require(destination >= 0, "Exhausted native ink needs an empty ordinary inventory slot");
                inventoryClicks.add(0); inventoryClicks.add(destination);
            }
            inventoryClicks.add(replacement); inventoryClicks.add(0); inventoryClicks.add(replacement);
            waiting = "native-replacement-ink-transactions"; return false;
        }
        if (menu.table().getItem(1).isEmpty()) {
            int paper = inventorySlot(menu, stack -> stack.is(Items.PAPER));
            if (paper < 0) { waiting = "real-theory-paper"; return false; }
            inventoryClicks.add(paper); inventoryClicks.add(1); inventoryClicks.add(paper);
            waiting = "native-paper-transactions"; return false;
        }
        require(menu.table().getItem(1).is(Items.PAPER), "Native paper table slot contains an unexpected item");
        return true;
    }
    private int chooseCard(ResearchTableData data, String goal) {
        int result = -1, score = Integer.MIN_VALUE;
        for (int index = 0; index < data.cardChoices.size(); index++) {
            var choice = data.cardChoices.get(index); var card = choice.card;
            if (choice.selected || !card.requirementsResolved() || card.getInspirationCost() <= 0
                || card.getInspirationCost() > data.inspiration
                || TheorycraftInventory.payment(client.player.getInventory().items, card.getRequiredItems(), card.getRequiredItemsConsumed()) == null) continue;
            if (card instanceof SourceTheorycraftCard source && Set.of(SourceTheorycraftCard.Kind.ANALYZE,
                SourceTheorycraftCard.Kind.DARK_WHISPERS, SourceTheorycraftCard.Kind.GLYPHS, SourceTheorycraftCard.Kind.REVELATION,
                SourceTheorycraftCard.Kind.REALIZATION, SourceTheorycraftCard.Kind.TRUTH, SourceTheorycraftCard.Kind.AWARENESS,
                SourceTheorycraftCard.Kind.ENCHANTMENT, SourceTheorycraftCard.Kind.SPELLBINDING).contains(source.kind())) continue;
            int candidate = goal.equals(card.getResearchCategory()) ? 100 : 0;
            // These ordinary neutral choices can grow the infusion note before a foreign
            // category's cards and Inspired repeatedly amplify its already dominant total.
            if (goal.equals("INFUSION") && card instanceof SourceTheorycraftCard source
                && card.getResearchCategory() == null
                && (source.kind() == SourceTheorycraftCard.Kind.PONDER
                    || source.kind() == SourceTheorycraftCard.Kind.EXPERIMENTATION)) candidate = 80;
            if (card instanceof SourceTheorycraftCard source) candidate += switch (source.kind()) {
                case SYNTHESIS -> 40;
                case REACTIONS, CHANNEL -> 25;
                case INSPIRED -> 30;
                case MEASURE, CONCENTRATE -> 20;
                case EXPERIMENTATION -> 15;
                case PONDER -> 10;
                default -> 5;
            };
            if (candidate > score) { score = candidate; result = index; }
        }
        return result;
    }
    private JsonObject theoryBefore(ContainerResearchTable menu) {
        JsonObject row = new JsonObject(); row.addProperty("menuId", menu.containerId);
        row.addProperty("sourceRevisionBefore", menu.viewRevision()); row.addProperty("receivedStateGenerationBefore", menu.receivedStateGeneration());
        row.addProperty("nativeScreenClick", true); row.addProperty("inkDamageBefore", menu.table().getItem(0).getDamageValue());
        row.addProperty("paperBefore", menu.table().getItem(1).getCount());
        if (menu.theoryData() != null) row.addProperty("inspirationBefore", menu.theoryData().inspiration);
        return row;
    }
    private JsonArray cards(ResearchTableData data) {
        JsonArray result = new JsonArray();
        for (var choice : data.cardChoices) {
            JsonObject row = new JsonObject(); row.addProperty("key", choice.key); row.addProperty("sourceSeed", choice.card.serialize().getLong("seed"));
            row.addProperty("category", choice.card.getResearchCategory()); row.addProperty("inspirationCost", choice.card.getInspirationCost());
            row.addProperty("fromPhysicalAid", choice.fromAid); row.add("requiredItems", requirements(choice)); result.add(row);
        }
        return result;
    }
    private JsonArray requirements(ResearchTableData.CardChoice choice) {
        JsonArray result = new JsonArray(); ItemStack[] required = choice.card.getRequiredItems();
        boolean[] consumed = choice.card.getRequiredItemsConsumed();
        if (required != null) for (int index = 0; index < required.length; index++) {
            JsonObject row = new JsonObject(); row.addProperty("stackNbt", required[index].saveOptional(client.player.registryAccess()).toString());
            row.addProperty("consumed", consumed != null && index < consumed.length && consumed[index]); result.add(row);
        }
        return result;
    }
    private static JsonObject totals(ResearchTableData data) {
        JsonObject result = new JsonObject(); data.categoryTotals.forEach(result::addProperty); return result;
    }
    private static JsonObject awards(ResearchTableData data) {
        JsonObject result = new JsonObject(); int index = 0;
        for (var entry : data.categoryTotals.entrySet().stream().sorted((a, b) -> b.getValue().compareTo(a.getValue())).toList()) {
            int value = thaumcraft.common.tiles.crafting.TileResearchTable.theoryAward(entry.getValue(), index++, data.penaltyStart);
            if (value > 0) result.addProperty(entry.getKey(), value);
        }
        return result;
    }
    private JsonObject theoryPools() {
        JsonObject result = new JsonObject(); var view = ClientResearchPresentation.view();
        for (String key : List.of("BASICS", "ALCHEMY", "INFUSION", "AUROMANCY", "ARTIFICE", "GOLEMANCY", "ELDRITCH"))
            result.addProperty(key, view.getKnowledgeRaw(EnumKnowledgeType.THEORY, key));
        return result;
    }
    private JsonObject knowledgePools() {
        JsonObject result = new JsonObject(); var view = ClientResearchPresentation.view();
        if (view == null) return result;
        for (EnumKnowledgeType type : EnumKnowledgeType.values()) for (String key : List.of("BASICS", "ALCHEMY", "INFUSION", "AUROMANCY", "ARTIFICE", "GOLEMANCY", "ELDRITCH"))
            result.addProperty(type.getAbbreviation() + "_" + key, view.getKnowledgeRaw(type, key));
        return result;
    }
    private int inventorySlot(AbstractContainerMenu menu, Predicate<ItemStack> predicate) {
        for (int index = 0; index < menu.slots.size(); index++) {
            var slot = menu.getSlot(index);
            if (slot.container == client.player.getInventory() && slot.getContainerSlot() >= 0
                && slot.getContainerSlot() < 36 && predicate.test(slot.getItem())) return index;
        }
        return -1;
    }
    private static JsonObject stringTag(String value) { JsonObject result = new JsonObject(); result.addProperty("snbt", value); return result; }
    private static double left(GuiResearchTable screen) { return (screen.width - 255) / 2D; }
    private static double top(GuiResearchTable screen) { return (screen.height - 255) / 2D; }
    private static Object field(Object target, String name) throws Exception { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static Object readMethod(Object target, String name) throws Exception { Method method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(target); }
    private void enterTick() {
        require(client.isSameThread() && client.player != null && client.level != null && client.gameMode != null,
            "Native alchemy research driver needs its actual connected client owner"); ticks++;
    }
    private void record(String action, JsonObject data) {
        JsonObject row = new JsonObject(); row.addProperty("action", action); row.addProperty("driverTick", ticks);
        row.addProperty("observedUtc", Instant.now().toString()); row.add("data", data.deepCopy());
        events.add(row.deepCopy()); pendingEvents.add(row);
    }
    private void screenshot(String name) { if (requestedScreenshots.add(name)) screenshots.add(name); }
    public JsonArray drainEvents() { JsonArray result = pendingEvents.deepCopy(); pendingEvents.asList().clear(); return result; }
    public List<String> drainScreenshotRequests() { List<String> result = List.copyOf(screenshots); screenshots.clear(); return result; }
    /** The controller may suspend only after every native material/award transaction settled. */
    public boolean canSuspendTheoryAtBoundary() {
        return inventoryClicks.isEmpty() && pendingTheoryAction == null && finishExpected == null;
    }
    public JsonObject state() {
        JsonObject result = new JsonObject(); result.addProperty("waiting", waiting); result.addProperty("driverTick", ticks);
        result.addProperty("researchKey", researchKey); result.addProperty("researchTarget", researchTarget);
        result.addProperty("pendingResearchKey", pendingResearchKey); result.addProperty("theoryCategory", theoryCategory);
        result.addProperty("pendingTheoryAction", pendingTheoryAction); result.addProperty("queuedInventoryClicks", inventoryClicks.size());
        result.addProperty("notesStarted", notesStarted); result.addProperty("cardsSelected", cardsSelected);
        result.addProperty("notesFinished", notesFinished); result.addProperty("notesScrapped", notesScrapped);
        var view = ClientResearchPresentation.view();
        if (view != null && researchKey != null && view.spec().entriesByKey().containsKey(researchKey)) {
            result.addProperty("receivedResearchStage", view.storedStage(researchKey));
            result.addProperty("researchAvailability", view.availability(researchKey).reason().name());
        }
        result.add("receivedKnowledge", knowledgePools()); return result;
    }
    public JsonObject report() {
        JsonObject result = state(); result.add("researchReceipts", researchReceipts.deepCopy());
        result.add("theoryReceipts", theoryReceipts.deepCopy()); result.add("events", events.deepCopy());
        result.addProperty("knowledgeOrResearchFixtures", false); result.addProperty("forcedRandomSeed", false);
        result.addProperty("nativeTheoryGui", true); result.addProperty("nativeResearchGui", true); return result;
    }
    private static void require(boolean accepted, String message) { if (!accepted) throw new IllegalStateException(message); }
}
