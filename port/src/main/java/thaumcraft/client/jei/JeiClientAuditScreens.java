package thaumcraft.client.jei;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.lwjgl.glfw.GLFW;
import thaumcraft.Thaumcraft;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.client.lib.ClientJeiReviewScenario;

/** Native JEI screen/render evidence, opt-in and gated to the fresh private review scenario. */
public final class JeiClientAuditScreens {
    private record Shot(String name, RecipeType<?> type, Object recipe, String source,
        List<IFocus<?>> focuses, boolean scroll) {}
    private enum Phase { WAITING, OPEN, SETTLING, SAVING, COMPLETE, FAILED }
    private static final int RENDER_TICKS = 30, SAVE_TIMEOUT_TICKS = 600;
    private static final int NATIVE_WIDTH = 1280, NATIVE_HEIGHT = 720, STABLE_WINDOW_TICKS = 10;
    private static final long WINDOW_WAIT_NANOS = 90_000_000_000L;
    private static final JsonArray CAPTURES = new JsonArray();
    private static final JsonArray FAILURES = new JsonArray();
    private static List<Shot> shots = List.of();
    private static Phase phase = Phase.WAITING;
    private static int index, waitTicks;
    private static Screen previousScreen;
    private static Path directory;
    private static JsonObject activeCapture;
    private static final JsonArray WINDOW_EVENTS = new JsonArray();
    private static JsonObject windowProbe;
    private static long unusableWindowSince, lastWindowReset;
    private static int windowRecoveryAttempts, stableWindowTicks, windowWaitTicks;
    private static boolean windowReflowed, pendingNativeSizeRestore;
    private static Integer originalGuiScalePreference;

    private JeiClientAuditScreens() {}

    public static boolean isComplete() { return phase == Phase.COMPLETE; }

    public static void tick(IJeiRuntime runtime) {
        if (!Boolean.getBoolean("thaumcraft.jeiReview") || runtime == null || phase == Phase.COMPLETE || phase == Phase.FAILED) return;
        var minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) throw new IllegalStateException("JEI screenshots require client MAIN");
        if (!ClientJeiReviewScenario.isReady() || !ClientJeiReviewScenario.phase().equals("capture-screens")) return;
        try {
            if (phase == Phase.WAITING) {
                directory = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
                if (!isPrivateDirectory(directory)) throw new IllegalStateException("JEI screenshots require build/jei-review/<run>/client");
                shots = representatives(runtime);
                if (shots.size() != 9) throw new IllegalStateException("All five visible categories, three dynamic infusion kinds and a scroll probe are required");
                previousScreen = minecraft.screen; phase = Phase.OPEN;
                ClientJeiReviewScenario.recordStep("screens-start", report());
            }
            if (phase != Phase.SAVING && !renderableWindow(minecraft)) {
                waitTicks = 0; return;
            }
            if (phase == Phase.OPEN) {
                var shot = shots.get(index);
                runtime.getRecipesGui().showTypes(List.of(shot.type()));
                showRecipe(runtime, shot.type(), shot.recipe(), shot.focuses());
                activeCapture = new JsonObject();
                activeCapture.addProperty("name", shot.name()); activeCapture.addProperty("category", shot.type().getUid().toString());
                activeCapture.addProperty("sourceId", shot.source()); activeCapture.addProperty("openedAt", Instant.now().toString());
                activeCapture.addProperty("settleTicksRequired", RENDER_TICKS);
                activeCapture.addProperty("requiredCategoryCapture", !shot.scroll());
                activeCapture.addProperty("focusMethod", shot.focuses().isEmpty() ? "none" : "automated-native-JEI-API");
                activeCapture.addProperty("manualKeypressTested", false);
                if (!shot.focuses().isEmpty()) activeCapture.addProperty("focusRole", shot.focuses().getFirst().getRole().name());
                CAPTURES.add(activeCapture); waitTicks = 0; phase = Phase.SETTLING; saveReport(); return;
            }
            if (phase == Phase.SETTLING && ++waitTicks >= RENDER_TICKS) {
                if (minecraft.screen == null || !minecraft.screen.getClass().getName().startsWith("mezz.jei."))
                    throw new IllegalStateException("Native JEI screen is absent before capture");
                var nativeLayout = nativeLayout(minecraft.screen, shots.get(index));
                activeCapture.addProperty("actualNativeLayout", nativeLayout.getClass().getName());
                activeCapture.addProperty("actualNativeSlotCount", nativeLayout.getRecipeSlotsView().getSlotViews().size());
                activeCapture.add("nativeGeometry", geometry(minecraft, nativeLayout));
                if (!shots.get(index).focuses().isEmpty()) {
                    var actualFocuses = ((IFocusGroup)field(nativeLayout, "focuses")).getAllFocuses();
                    var expectedFocus = shots.get(index).focuses().getFirst();
                    Object expectedValue = expectedFocus.getTypedValue().getIngredient();
                    boolean preserved = actualFocuses.size() == 1 && actualFocuses.getFirst().getRole() == expectedFocus.getRole();
                    if (preserved) {
                        Object actualValue = actualFocuses.getFirst().getTypedValue().getIngredient();
                        preserved = expectedValue instanceof net.minecraft.world.item.ItemStack expectedStack
                            && actualValue instanceof net.minecraft.world.item.ItemStack actualStack
                            ? net.minecraft.world.item.ItemStack.isSameItemSameComponents(expectedStack, actualStack)
                            : expectedValue.equals(actualValue);
                    }
                    activeCapture.addProperty("actualNativeFocusPreserved", preserved);
                    if (!preserved) throw new IllegalStateException("Native JEI layout did not retain the requested R/U role and ingredient");
                }
                if (shots.get(index).scroll() && !activeCapture.has("scroll")) {
                    activeCapture.add("scroll", scrollNative(minecraft, nativeLayout));
                    waitTicks = 0; saveReport(); return;
                }
                String filename = shots.get(index).name() + ".png";
                if (Files.exists(directory.resolve("screenshots").resolve(filename)))
                    throw new IllegalStateException("Fresh review screenshot already exists: " + filename);
                activeCapture.addProperty("screenClass", minecraft.screen.getClass().getName());
                activeCapture.addProperty("width", minecraft.getWindow().getWidth());
                activeCapture.addProperty("height", minecraft.getWindow().getHeight());
                activeCapture.addProperty("guiWidth", minecraft.getWindow().getGuiScaledWidth());
                activeCapture.addProperty("guiHeight", minecraft.getWindow().getGuiScaledHeight());
                activeCapture.addProperty("settledTicks", waitTicks);
                activeCapture.add("nativeWindowProbe", windowProbe.deepCopy());
                activeCapture.addProperty("file", directory.resolve("screenshots").resolve(filename).toString());
                phase = Phase.SAVING; waitTicks = 0; saveReport();
                Screenshot.grab(minecraft.gameDirectory, filename, minecraft.getMainRenderTarget(), message ->
                    minecraft.execute(() -> saved(filename, message.getString())));
            } else if (phase == Phase.SAVING && ++waitTicks > SAVE_TIMEOUT_TICKS) {
                throw new IllegalStateException("Timed out saving the native JEI screenshot");
            }
        } catch (RuntimeException | LinkageError | ReflectiveOperationException failure) { fail(failure.toString()); }
    }

    private static void saved(String filename, String message) {
        if (phase != Phase.SAVING) return;
        try {
            Path file = directory.resolve("screenshots").resolve(filename);
            if (!Files.isRegularFile(file) || Files.size(file) < 24) throw new IOException("Screenshot was not saved: " + message);
            byte[] header;
            try (var input = Files.newInputStream(file)) { header = input.readNBytes(24); }
            if (!java.util.Arrays.equals(java.util.Arrays.copyOf(header, 8), new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10}))
                throw new IOException("Captured file is not PNG: " + filename);
            var pngDimensions = java.nio.ByteBuffer.wrap(header, 16, 8);
            int pngWidth = pngDimensions.getInt(), pngHeight = pngDimensions.getInt();
            if (pngWidth != NATIVE_WIDTH || pngHeight != NATIVE_HEIGHT)
                throw new IOException("Native screenshot pixels differ from the ready framebuffer: " + pngWidth + "x" + pngHeight);
            activeCapture.addProperty("pngWidth", pngWidth); activeCapture.addProperty("pngHeight", pngHeight);
            activeCapture.addProperty("saved", true); activeCapture.addProperty("bytes", Files.size(file));
            activeCapture.addProperty("completedAt", Instant.now().toString());
            activeCapture.addProperty("message", message);
            index++;
            if (index == shots.size()) {
                phase = Phase.COMPLETE;
                Minecraft.getInstance().setScreen(previousScreen);
                saveReport(); ClientJeiReviewScenario.recordStep("screens-complete", report());
            } else { phase = Phase.OPEN; saveReport(); }
        } catch (IOException | RuntimeException failure) { fail(failure.toString()); }
    }

    private static <T> void showRecipe(IJeiRuntime runtime, RecipeType<T> type, Object recipe, List<IFocus<?>> focuses) {
        T value = type.getRecipeClass().cast(recipe);
        var category = runtime.getRecipeManager().getRecipeCategory(type);
        if (category == null) throw new IllegalArgumentException("Missing category " + type.getUid());
        runtime.getRecipesGui().showRecipes(category, List.of(value), focuses);
    }

    private static List<Shot> representatives(IJeiRuntime runtime) {
        List<Shot> result = new ArrayList<>();
        var arcane = visible(runtime, ThaumcraftJeiPlugin.ARCANE);
        var crucible = visible(runtime, ThaumcraftJeiPlugin.CRUCIBLE);
        var infusion = visible(runtime, ThaumcraftJeiPlugin.INFUSION);
        var combinations = runtime.getRecipeManager().createRecipeLookup(AspectJeiRecipes.COMBINATIONS).get().toList();
        var contents = runtime.getRecipeManager().createRecipeLookup(AspectJeiRecipes.CONTENTS).get().toList();
        if (arcane.isEmpty() || crucible.isEmpty() || combinations.isEmpty() || contents.isEmpty()) return List.of();
        var fixed = infusion.stream().filter(holder -> !(holder.value() instanceof InfusionDisplayRecipe)).findFirst();
        var enchantment = infusion.stream().filter(holder -> holder.value() instanceof InfusionDisplayRecipe display
            && display.kind() == InfusionDisplayRecipe.Kind.ENCHANTMENT)
            .max(java.util.Comparator.comparingInt(holder -> ((InfusionDisplayRecipe)holder.value()).level()));
        var runic = infusion.stream().filter(holder -> holder.value() instanceof InfusionDisplayRecipe display
            && display.kind() == InfusionDisplayRecipe.Kind.RUNIC && display.level() == 6).findFirst();
        if (runic.isEmpty()) runic = infusion.stream().filter(holder -> holder.value() instanceof InfusionDisplayRecipe display
            && display.kind() == InfusionDisplayRecipe.Kind.RUNIC).findFirst();
        var tag = infusion.stream().filter(holder -> holder.value() instanceof InfusionDisplayRecipe display
            && display.kind() == InfusionDisplayRecipe.Kind.TAG).findFirst();
        if (fixed.isEmpty() || enchantment.isEmpty() || runic.isEmpty() || tag.isEmpty()) return List.of();
        result.add(shot("01-arcane", ThaumcraftJeiPlugin.ARCANE, arcane.getFirst()));
        var apiHedge = crucible.stream().filter(holder -> JeiRecipeIdentity.sourceId(holder).getPath().startsWith("hedge_")).findFirst().orElse(crucible.getFirst());
        result.add(shot("02-crucible", ThaumcraftJeiPlugin.CRUCIBLE, apiHedge));
        result.add(shot("03-infusion", ThaumcraftJeiPlugin.INFUSION, fixed.get()));
        var combination = combinations.getFirst();
        var focusFactory = runtime.getJeiHelpers().getFocusFactory();
        var aspectFocus = focusFactory.createFocus(RecipeIngredientRole.OUTPUT, AspectIngredientHelper.TYPE,
            new JeiAspectIngredient(combination.result(), 1));
        result.add(new Shot("04-aspect-combinations", AspectJeiRecipes.COMBINATIONS, combination,
            combination.result(), List.of(aspectFocus), false));
        var composition = contents.stream().filter(recipe -> recipe.aspects().size() >= 3).findFirst().orElse(contents.getFirst());
        var itemFocus = focusFactory.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, composition.stack());
        result.add(new Shot("05-item-aspects", AspectJeiRecipes.CONTENTS, composition,
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(composition.stack().getItem()).toString(), List.of(itemFocus), false));
        result.add(shot("06-infusion-enchantment", ThaumcraftJeiPlugin.INFUSION, enchantment.get()));
        result.add(shot("07-infusion-runic", ThaumcraftJeiPlugin.INFUSION, runic.get()));
        result.add(shot("08-infusion-tag", ThaumcraftJeiPlugin.INFUSION, tag.get()));
        // Native source recipes have at most eight components; their 7x2 grid is already
        // complete. Enchantment details overflow the text viewport and provide a real
        // wheel/viewport probe without inventing an executable long recipe.
        result.add(new Shot("09-infusion-scroll", ThaumcraftJeiPlugin.INFUSION, enchantment.get(),
            JeiRecipeIdentity.sourceId(enchantment.get()).toString(), List.of(), true));
        return List.copyOf(result);
    }

    private static <R extends Recipe<?>> List<RecipeHolder<R>> visible(IJeiRuntime runtime, RecipeType<RecipeHolder<R>> type) {
        return runtime.getRecipeManager().createRecipeLookup(type).get().filter(holder -> JeiRecipeIdentity.isCurrent(type, holder)).toList();
    }
    private static <R extends Recipe<?>> Shot shot(String name, RecipeType<RecipeHolder<R>> type, RecipeHolder<R> holder) {
        return new Shot(name, type, holder, JeiRecipeIdentity.sourceId(holder).toString(), List.of(), false);
    }

    /** Read the actual displayed layout using pinned JEI 19.57 internals; never mutate them. */
    private static IRecipeLayoutDrawable<?> nativeLayout(Screen screen, Shot shot) throws ReflectiveOperationException {
        if (!screen.getClass().getName().equals("mezz.jei.gui.recipes.RecipesGui"))
            throw new IllegalStateException("Unexpected native JEI screen implementation " + screen.getClass().getName());
        Object layouts = field(screen, "layouts");
        Object entries = field(layouts, "recipeLayoutsWithButtons");
        if (!(entries instanceof List<?> list)) throw new IllegalStateException("Native JEI layout list is unavailable");
        for (Object entry : list) {
            Object layout = entry.getClass().getMethod("getRecipeLayout").invoke(entry);
            if (layout instanceof IRecipeLayoutDrawable<?> drawable && drawable.getRecipe() == shot.recipe()
                && drawable.getRecipeCategory().getRecipeType().equals(shot.type())) return drawable;
        }
        throw new IllegalStateException("Requested actual recipe is absent from native displayed JEI layouts: " + shot.name());
    }

    private static JsonObject scrollNative(Minecraft minecraft, IRecipeLayoutDrawable<?> layout) throws ReflectiveOperationException {
        Object widgets = field(layout, "allWidgets");
        if (!(widgets instanceof List<?> list)) throw new IllegalStateException("Native JEI widgets are unavailable");
        for (Object value : list) {
            if (!(value instanceof IRecipeWidget widget) || !value.getClass().getName().startsWith("mezz.jei.library.gui.widgets.Scroll")) continue;
            int hidden = ((Number) method(value, "getHiddenAmount")).intValue();
            if (hidden <= 0) continue;
            var area = widget.getScreenRectangle();
            if (area == null) continue;
            double x = layout.getRect().getX() + area.left() + Math.min(8, area.width() / 2.0);
            double y = layout.getRect().getY() + area.top() + Math.min(8, area.height() / 2.0);
            float before = ((Number) field(value, "scrollOffsetY")).floatValue();
            boolean handled = minecraft.screen.mouseScrolled(x, y, 0, -1);
            float after = ((Number) field(value, "scrollOffsetY")).floatValue();
            var evidence = new JsonObject(); evidence.addProperty("method", "native-Screen.mouseScrolled");
            evidence.addProperty("widget", value.getClass().getName()); evidence.addProperty("mouseX", x); evidence.addProperty("mouseY", y);
            evidence.addProperty("hiddenAmount", hidden); evidence.addProperty("offsetBefore", before); evidence.addProperty("offsetAfter", after);
            evidence.addProperty("handled", handled); evidence.addProperty("viewportMoved", after > before);
            evidence.add("viewport", rectangle(layout.getRect().getX() + area.left(), layout.getRect().getY() + area.top(), area.width(), area.height()));
            if (!handled || !(after > before)) throw new IllegalStateException("Native wheel failed to move the overflowing JEI viewport: " + evidence);
            return evidence;
        }
        throw new IllegalStateException("Expected native enchantment details viewport to overflow and permit scrolling");
    }

    private static JsonObject geometry(Minecraft minecraft, IRecipeLayoutDrawable<?> layout) throws ReflectiveOperationException {
        var rect = layout.getRect(); var border = layout.getRectWithBorder();
        Object panel = minecraft.screen.getClass().getMethod("getArea").invoke(minecraft.screen);
        int panelX = ((Number)method(panel, "x")).intValue(), panelY = ((Number)method(panel, "y")).intValue();
        int panelWidth = ((Number)method(panel, "width")).intValue(), panelHeight = ((Number)method(panel, "height")).intValue();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth(), screenHeight = minecraft.getWindow().getGuiScaledHeight();
        boolean inScreen = border.getX() >= 0 && border.getY() >= 0 && border.getX() + border.getWidth() <= screenWidth
            && border.getY() + border.getHeight() <= screenHeight;
        boolean inPanel = border.getX() >= panelX && border.getY() >= panelY && border.getX() + border.getWidth() <= panelX + panelWidth
            && border.getY() + border.getHeight() <= panelY + panelHeight;
        var result = new JsonObject(); result.add("layout", rectangle(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight()));
        result.add("border", rectangle(border.getX(), border.getY(), border.getWidth(), border.getHeight()));
        result.add("panel", rectangle(panelX, panelY, panelWidth, panelHeight));
        result.add("screen", rectangle(0, 0, screenWidth, screenHeight));
        result.addProperty("layoutWithinScreen", inScreen); result.addProperty("layoutWithinPanel", inPanel);
        var viewports = new JsonArray(); result.add("scrollViewports", viewports); boolean widgetsFit = true;
        Object widgets = field(layout, "allWidgets");
        if (!(widgets instanceof List<?> list)) throw new IllegalStateException("Native JEI widgets are unavailable");
        for (Object value : list) {
            if (!(value instanceof IRecipeWidget widget) || !value.getClass().getName().startsWith("mezz.jei.library.gui.widgets.Scroll")) continue;
            var area = widget.getScreenRectangle(); if (area == null) continue;
            boolean fits = area.left() >= 0 && area.top() >= 0 && area.right() <= rect.getWidth() && area.bottom() <= rect.getHeight();
            widgetsFit &= fits; var row = new JsonObject(); row.addProperty("widget", value.getClass().getName());
            row.add("bounds", rectangle(rect.getX() + area.left(), rect.getY() + area.top(), area.width(), area.height()));
            row.addProperty("withinLayout", fits); row.addProperty("hiddenAmount", ((Number)method(value, "getHiddenAmount")).intValue()); viewports.add(row);
        }
        result.addProperty("scrollViewportsWithinLayout", widgetsFit);
        if (!inScreen || !inPanel || !widgetsFit) throw new IllegalStateException("Native JEI recipe card or scroll viewport is cropped: " + result);
        return result;
    }

    private static JsonObject rectangle(int x, int y, int width, int height) {
        var json = new JsonObject(); json.addProperty("x", x); json.addProperty("y", y);
        json.addProperty("width", width); json.addProperty("height", height); return json;
    }

    /** Early-window handoff or minimization can leave an unavailable 1x1 framebuffer.
     * Restore the owned native window through GLFW/Window, then trust only actual native
     * framebuffer callbacks. Never manufacture Window cached sizes or change GUI scale.
     */
    private static boolean renderableWindow(Minecraft minecraft) {
        var window = minecraft.getWindow(); long handle = window.getWindow();
        int[] actualWidth = new int[1], actualHeight = new int[1];
        int[] actualWindowWidth = new int[1], actualWindowHeight = new int[1];
        GLFW.glfwGetFramebufferSize(handle, actualWidth, actualHeight);
        GLFW.glfwGetWindowSize(handle, actualWindowWidth, actualWindowHeight);
        boolean iconified = GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;
        boolean visible = GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_VISIBLE) == GLFW.GLFW_TRUE;
        var probe = new JsonObject(); windowProbe = probe;
        probe.addProperty("nativeFramebufferWidth", actualWidth[0]); probe.addProperty("nativeFramebufferHeight", actualHeight[0]);
        probe.addProperty("cachedFramebufferWidth", window.getWidth()); probe.addProperty("cachedFramebufferHeight", window.getHeight());
        probe.addProperty("nativeWindowWidth", actualWindowWidth[0]); probe.addProperty("nativeWindowHeight", actualWindowHeight[0]);
        probe.addProperty("renderTargetWidth", minecraft.getMainRenderTarget().width); probe.addProperty("renderTargetHeight", minecraft.getMainRenderTarget().height);
        probe.addProperty("guiWidth", window.getGuiScaledWidth()); probe.addProperty("guiHeight", window.getGuiScaledHeight());
        probe.addProperty("iconified", iconified); probe.addProperty("visible", visible);
        probe.addProperty("guiScalePreference", minecraft.options.guiScale().get());
        if (originalGuiScalePreference == null) originalGuiScalePreference = minecraft.options.guiScale().get();
        if (!originalGuiScalePreference.equals(minecraft.options.guiScale().get()))
            throw new IllegalStateException("Private screenshots must preserve the original GUI scale preference");
        boolean nativeSize = !iconified && visible && actualWidth[0] == NATIVE_WIDTH && actualHeight[0] == NATIVE_HEIGHT
            && window.getWidth() == actualWidth[0] && window.getHeight() == actualHeight[0];
        long now = System.nanoTime();
        if (unusableWindowSince == 0) unusableWindowSince = now;
        if (pendingNativeSizeRestore) {
            // Setting the already-current native size need not emit a framebuffer callback.
            // Wait for the actual one-pixel native resize, then restore the requested size
            // on a later tick so GLFW emits real callbacks for both physical framebuffers.
            if (actualWidth[0] == NATIVE_WIDTH - 1 && actualHeight[0] == NATIVE_HEIGHT
                && window.getWidth() == actualWidth[0] && window.getHeight() == actualHeight[0]) {
                window.setWindowed(NATIVE_WIDTH, NATIVE_HEIGHT); pendingNativeSizeRestore = false;
                var event = probe.deepCopy(); event.addProperty("action", "native-size-callback-restore-1280x720");
                event.addProperty("at", Instant.now().toString()); WINDOW_EVENTS.add(event); saveReport();
            }
            if (now - unusableWindowSince > WINDOW_WAIT_NANOS)
                throw new IllegalStateException("Native review resize callback did not arrive within 90 seconds: " + probe);
            if (++windowWaitTicks % 20 == 0) saveReport();
            return false;
        }
        if (nativeSize && !windowReflowed) {
            // ResizeDisplay derives GUI scale and initializes the actual current Screen
            // from the real framebuffer. The player's configured scale remains intact.
            minecraft.resizeDisplay(); windowReflowed = true; stableWindowTicks = 0;
            var event = probe.deepCopy(); event.addProperty("action", "native-framebuffer-ready-reflow");
            event.addProperty("at", Instant.now().toString()); WINDOW_EVENTS.add(event); saveReport(); return false;
        }
        boolean guiReady = nativeSize && window.getGuiScaledWidth() >= 320 && window.getGuiScaledHeight() >= 240
            && minecraft.getMainRenderTarget().width == actualWidth[0] && minecraft.getMainRenderTarget().height == actualHeight[0]
            && (minecraft.screen == null || minecraft.screen.width == window.getGuiScaledWidth()
                && minecraft.screen.height == window.getGuiScaledHeight());
        if (guiReady) {
            stableWindowTicks = Math.min(STABLE_WINDOW_TICKS, stableWindowTicks + 1);
            probe.addProperty("stableTicks", stableWindowTicks);
            if (stableWindowTicks == STABLE_WINDOW_TICKS) { unusableWindowSince = 0; return true; }
        } else {
            stableWindowTicks = 0; windowReflowed = false;
            if (windowRecoveryAttempts < 3 && (lastWindowReset == 0 || now - lastWindowReset >= 3_000_000_000L)) {
                var event = probe.deepCopy(); event.addProperty("action", "owned-native-windowed-1280x720");
                event.addProperty("at", Instant.now().toString()); WINDOW_EVENTS.add(event);
                if (iconified) GLFW.glfwRestoreWindow(handle);
                if (!visible) {
                    GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
                    GLFW.glfwShowWindow(handle);
                }
                boolean staleCache = actualWidth[0] == NATIVE_WIDTH && actualHeight[0] == NATIVE_HEIGHT
                    && (window.getWidth() != actualWidth[0] || window.getHeight() != actualHeight[0]);
                pendingNativeSizeRestore = staleCache;
                if (staleCache) event.addProperty("actualNativeResizeWidthBeforeRestore", NATIVE_WIDTH - 1);
                window.setWindowed(staleCache ? NATIVE_WIDTH - 1 : NATIVE_WIDTH, NATIVE_HEIGHT);
                windowRecoveryAttempts++; lastWindowReset = now;
                ClientJeiReviewScenario.recordStep("native-review-window-recovery", event); saveReport();
            }
        }
        if (++windowWaitTicks % 20 == 0) saveReport();
        if (now - unusableWindowSince > WINDOW_WAIT_NANOS)
            throw new IllegalStateException("Native review framebuffer did not become usable within 90 seconds: " + probe);
        return false;
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target);
            } catch (NoSuchFieldException absent) { /* Follow the pinned widget's superclass. */ }
        }
        throw new NoSuchFieldException(target.getClass().getName() + "." + name);
    }

    private static Object method(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var method = type.getDeclaredMethod(name); method.setAccessible(true); return method.invoke(target);
            } catch (NoSuchMethodException absent) { /* Follow the pinned widget's superclass. */ }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }
    private static boolean isPrivateDirectory(Path path) {
        return path.getFileName() != null && path.getFileName().toString().equals("client")
            && path.getParent() != null && path.getParent().getParent() != null
            && path.getParent().getParent().getFileName().toString().equals("jei-review");
    }
    private static void fail(String reason) {
        FAILURES.add(reason); phase = Phase.FAILED;
        if (directory != null && isPrivateDirectory(directory)) {
            Minecraft.getInstance().setScreen(previousScreen);
            saveReport();
        }
        Thaumcraft.log.error("Opt-in native JEI screen capture failed: {}", reason);
        ClientJeiReviewScenario.fail("JEI screens: " + reason);
    }
    private static JsonObject report() {
        var json = new JsonObject(); json.addProperty("schemaVersion", 1); json.addProperty("purpose", "thaumcraft-jei-native-screens");
        json.addProperty("phase", phase.name()); json.addProperty("completedCaptures", index);
        json.addProperty("expectedCaptures", 8); json.addProperty("passed", phase == Phase.COMPLETE && FAILURES.size() == 0);
        json.addProperty("completedRequiredCaptures", Math.min(index, 8)); json.addProperty("supplementalCaptures", Math.max(0, index - 8));
        json.addProperty("plannedNativeCaptures", shots.size()); json.addProperty("manualRUPressesTested", false);
        json.addProperty("focusVisualMethod", "automated-native-JEI-API");
        var recovery = new JsonObject(); recovery.addProperty("expectedNativeWidth", NATIVE_WIDTH);
        recovery.addProperty("expectedNativeHeight", NATIVE_HEIGHT); recovery.addProperty("attempts", windowRecoveryAttempts);
        recovery.addProperty("stableTicksRequired", STABLE_WINDOW_TICKS); recovery.addProperty("stableTicks", stableWindowTicks);
        if (originalGuiScalePreference != null) recovery.addProperty("originalGuiScalePreference", originalGuiScalePreference);
        recovery.addProperty("guiScalePreferenceChanged", false); recovery.add("events", WINDOW_EVENTS.deepCopy());
        if (windowProbe != null) recovery.add("lastProbe", windowProbe.deepCopy()); json.add("nativeWindowRecovery", recovery);
        json.add("captures", CAPTURES.deepCopy()); json.add("failures", FAILURES.deepCopy()); return json;
    }
    private static void saveReport() {
        try {
            Path target = directory.resolve("jei-screens.json"), temporary = directory.resolve("jei-screens.json.tmp");
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report()) + "\n", StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException failure) { Thaumcraft.log.error("Cannot save opt-in JEI screen report", failure); }
    }
}
