package thaumcraft.client.lib;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import thaumcraft.common.tiles.crafting.FocusElementNode;
import thaumcraft.client.gui.GuiFocalManipulator;
import thaumcraft.common.container.ContainerFocalManipulator;
import thaumcraft.common.items.casters.ItemFocus;
import thaumcraft.common.tiles.crafting.TileFocalManipulator;

/** Private native GUI actions. Reflection only reads the actual displayed palette/tree;
 * the screen's own buttons author the draft and construct its production C2S requests. */
public final class ClientCasterFocusDriver {
    private final Minecraft client;
    private final JsonArray events = new JsonArray(), pendingEvents = new JsonArray();
    private final ArrayList<String> screenshots = new ArrayList<>();
    private int ticks, nextTick, draftStep, craftStep, xpBefore;
    private long draftRevision, craftRevision;
    private String waiting = "idle", requestedName;
    private boolean observedBusy;

    public ClientCasterFocusDriver(Minecraft client) {
        this.client = client;
        require(Boolean.getBoolean("thaumcraft.tcpCasterAudit"), "Private native caster GUI driver is disabled");
    }

    public boolean tickDraft(String name) throws Exception {
        enter(); requestedName = name;
        var screen = screen(); var menu = screen.getMenu();
        if (!menu.ready() || menu.busy() || menu.getSlot(0).getItem().isEmpty()) { waiting = "received-focal-menu-and-focus"; return false; }
        if (ticks < nextTick) return false;
        if (draftStep == 0) {
            if (!guiNode(screen, 1, "thaumcraft.TOUCH")) {
                choose(screen, 1, "thaumcraft.TOUCH"); waiting = "native-touch-palette"; return false;
            }
            draftStep = 1;
        }
        if (draftStep == 1) {
            if (!guiNode(screen, 2, "thaumcraft.FIRE")) {
                choose(screen, 2, "thaumcraft.FIRE"); waiting = "native-fire-palette"; return false;
            }
            draftStep = 2;
        }
        if (draftStep == 2) {
            var edit = (EditBox) field(screen, "name");
            if (!edit.getValue().equals(name)) {
                click(screen, 150, 18);
                require(screen.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0), "Native focus name cursor control rejected");
                for (int i = edit.getValue().length(); i > 0; i--) require(screen.keyPressed(GLFW.GLFW_KEY_DELETE, 0, 0), "Native focus name deletion rejected");
                for (char character : name.toCharArray()) require(screen.charTyped(character, 0), "Native focus name typing rejected");
                require(edit.getValue().equals(name), "Native focus name input differs from requested GUI text");
                record("native-focus-name-typed", state()); nextTick = ticks + 4; return false;
            }
            require(guiGraph(screen), "Native GUI must display ROOT to TOUCH to FIRE with source default settings");
            draftRevision = menu.revision(); JsonObject before = state(); before.addProperty("revisionBefore", draftRevision);
            click(screen, 100, 201); record("native-focus-draft-save-button", before);
            draftStep = 3; nextTick = ticks + 4; waiting = "native-focus-draft-s2c"; return false;
        }
        require(!menu.rejected(), "Production server rejected the genuine focus draft");
        if (menu.revision() <= draftRevision || !serverGraph(menu) || !name.equals(menu.view().getString("focusName"))) return false;
        if (draftStep == 3) { record("native-focus-draft-received", state()); screenshots.add("focus-draft.png"); draftStep = 4; }
        waiting = "native-focus-draft-complete"; return true;
    }

    public boolean tickCraft() throws Exception {
        enter(); var screen = screen(); var menu = screen.getMenu();
        if (!menu.ready() || ticks < nextTick) { waiting = "received-focal-craft-menu"; return false; }
        if (craftStep == 0) {
            require(draftStep == 4 && !menu.busy() && serverGraph(menu) && guiGraph(screen), "Native focus craft requires the accepted GUI draft");
            require(ItemFocus.getPackage(menu.getSlot(0).getItem()) == null, "Native source focus input must still be unconstructed");
            xpBefore = client.player.experienceLevel; require(xpBefore >= 2, "Actual survival focus constructor lacks two XP levels");
            craftRevision = menu.revision(); JsonObject before = state(); before.addProperty("xpLevelBefore", xpBefore);
            before.addProperty("revisionBefore", craftRevision); before.addProperty("sourceComplexity", 4); before.addProperty("sourceVis", 43);
            click(screen, 72, 231); record("native-focus-craft-button", before);
            craftStep = 1; waiting = "native-focus-craft-owner-s2c"; nextTick = ticks + 4; return false;
        }
        require(!menu.rejected(), "Production server rejected the genuine focus craft");
        if (menu.revision() <= craftRevision) return false;
        if (menu.busy()) {
            require(menu.view().getFloat("initialVis") == 43 && menu.view().getFloat("vis") > 0 && menu.view().getFloat("vis") <= 43,
                "Received focus job payment/progress differs from source cost");
            if (!observedBusy) { observedBusy = true; record("native-focus-crafting-received", state()); screenshots.add("focus-crafting.png"); }
            waiting = "native-focus-aura-processing"; return false;
        }
        ItemStack output = menu.getSlot(0).getItem(); var pack = ItemFocus.getPackage(output);
        if (pack == null || client.player.experienceLevel != xpBefore - 2) { waiting = "native-focus-result-and-xp-s2c"; return false; }
        require(pack.getComplexity() == 4 && pack.nodes.size() == 3 && pack.nodes.get(0).getKey().equals("ROOT")
            && pack.nodes.get(1).getKey().equals("thaumcraft.TOUCH") && pack.nodes.get(2).getKey().equals("thaumcraft.FIRE")
            && pack.nodes.get(2) instanceof thaumcraft.api.casters.FocusNode fire && fire.getSettingValue("power") == 1 && fire.getSettingValue("duration") == 0
            && output.getHoverName().getString().equals(requestedName), "Received constructed focus differs from the native GUI draft");
        if (craftStep == 1) { JsonObject row = state(); row.addProperty("xpLevelBefore", xpBefore); row.addProperty("xpLevelAfter", client.player.experienceLevel);
            row.addProperty("actualFocusSlotS2C", true); record("native-constructed-focus-received", row); screenshots.add("focus-complete.png"); craftStep = 2; }
        waiting = "native-focus-craft-complete"; return true;
    }

    private void choose(GuiFocalManipulator screen, int selected, String key) throws Exception {
        @SuppressWarnings("unchecked") var graph = (Map<Integer, FocusElementNode>) field(screen, "graph");
        var row = graph.get(selected); require(row != null, "Expected native blank focus tree node is absent");
        if (((Number) field(screen, "selected")).intValue() != selected) {
            double x = ((Number) method(screen, "graphX", FocusElementNode.class, row)).doubleValue();
            double y = ((Number) method(screen, "graphY", FocusElementNode.class, row)).doubleValue();
            require(screen.mouseClicked(x, y, 0), "Native blank graph selection rejected"); nextTick = ticks + 4; return;
        }
        @SuppressWarnings("unchecked") List<String> palette = (List<String>) method(screen, "palette");
        int index = palette.indexOf(key); require(index >= 0, "Native received research palette lacks " + key);
        int page = ((Number) field(screen, "palettePage")).intValue();
        if (page != index / 6) { click(screen, page < index / 6 ? 122 : 20, 179); nextTick = ticks + 4; return; }
        click(screen, 70, 41 + index % 6 * 17); JsonObject rowProof = new JsonObject(); rowProof.addProperty("elementKey", key);
        rowProof.addProperty("selectedNode", selected); rowProof.addProperty("nativePaletteClick", true); record("native-focus-palette-click", rowProof); nextTick = ticks + 4;
    }

    private boolean guiNode(GuiFocalManipulator screen, int id, String key) throws Exception {
        @SuppressWarnings("unchecked") var graph = (Map<Integer, FocusElementNode>) field(screen, "graph");
        var row = graph.get(id); return row != null && row.node != null && row.node.getKey().equals(key);
    }
    private boolean guiGraph(GuiFocalManipulator screen) throws Exception {
        @SuppressWarnings("unchecked") var graph = (Map<Integer, FocusElementNode>) field(screen, "graph");
        return matchesGraph(new ArrayList<>(graph.values()));
    }
    private boolean serverGraph(ContainerFocalManipulator menu) {
        try { return matchesGraph(TileFocalManipulator.readNodes(menu.view())); }
        catch (IllegalArgumentException absent) { return false; }
    }
    private boolean matchesGraph(List<FocusElementNode> rows) {
        if (rows.size() != 3) return false;
        var root = rows.stream().filter(row -> row.id == 0).findFirst().orElse(null);
        var touch = rows.stream().filter(row -> row.id == 1).findFirst().orElse(null);
        var fire = rows.stream().filter(row -> row.id == 2).findFirst().orElse(null);
        return root != null && touch != null && fire != null && root.node != null && root.node.getKey().equals("ROOT") && root.parent == -1
            && java.util.Arrays.equals(root.children, new int[]{1}) && touch.node != null && touch.node.getKey().equals("thaumcraft.TOUCH") && touch.parent == 0
            && java.util.Arrays.equals(touch.children, new int[]{2}) && fire.node != null && fire.node.getKey().equals("thaumcraft.FIRE") && fire.parent == 1
            && fire.children.length == 0 && fire.node.getSettingValue("power") == 1 && fire.node.getSettingValue("duration") == 0;
    }
    private GuiFocalManipulator screen() {
        require(client.screen instanceof GuiFocalManipulator && client.player.containerMenu instanceof ContainerFocalManipulator,
            "Native focus construction requires its genuine received screen/menu"); return (GuiFocalManipulator) client.screen;
    }
    private void click(GuiFocalManipulator screen, int x, int y) {
        require(screen.mouseClicked((screen.width - 356) / 2D + x, (screen.height - 258) / 2D + y, 0), "Native focal GUI control rejected click");
    }
    private void enter() { require(client.isSameThread() && client.player != null && client.level != null, "Native focus UI lost its owner"); ticks++; }
    private static Object field(Object target, String name) throws Exception { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static Object method(Object target, String name) throws Exception { Method method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(target); }
    private static Object method(Object target, String name, Class<?> type, Object argument) throws Exception { Method method = target.getClass().getDeclaredMethod(name, type); method.setAccessible(true); return method.invoke(target, argument); }
    private void record(String action, JsonObject data) { JsonObject row = new JsonObject(); row.addProperty("action", action); row.addProperty("driverTick", ticks);
        row.addProperty("observedUtc", Instant.now().toString()); row.add("data", data.deepCopy()); events.add(row.deepCopy()); pendingEvents.add(row); }
    public JsonArray drainEvents() { JsonArray copy = pendingEvents.deepCopy(); pendingEvents.asList().clear(); return copy; }
    public List<String> drainScreenshotRequests() { List<String> copy = List.copyOf(screenshots); screenshots.clear(); return copy; }
    public JsonObject state() { JsonObject row = new JsonObject(); row.addProperty("waiting", waiting); row.addProperty("driverTick", ticks);
        row.addProperty("draftStep", draftStep); row.addProperty("craftStep", craftStep); row.addProperty("observedNativeBusy", observedBusy);
        if (client.player != null && client.player.containerMenu instanceof ContainerFocalManipulator menu) { row.addProperty("menuId", menu.containerId);
            row.addProperty("receivedRevision", menu.revision()); row.addProperty("receivedStateVersion", menu.stateVersion()); row.addProperty("rejected", menu.rejected());
            row.addProperty("focusSlotNbt", menu.getSlot(0).getItem().saveOptional(client.level.registryAccess()).toString()); row.addProperty("viewNbt", menu.view().toString()); }
        return row; }
    public JsonObject report() { JsonObject row = state(); row.add("events", events.deepCopy()); row.addProperty("nativeGraphGui", true); row.addProperty("directClientFocusPackageWrite", false); return row; }
    private static void require(boolean accepted, String message) { if (!accepted) throw new IllegalStateException(message); }
}
