package thaumcraft.client.lib;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import thaumcraft.api.golems.parts.*;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import thaumcraft.common.golems.Golem9EntityRegistries;
import thaumcraft.common.golems.GolemProperties;
import thaumcraft.common.golems.client.GolemObjMeshes;
import thaumcraft.common.golems.client.RenderThaumcraftGolem;

/** One native framebuffer renders every source catalog part using actual S2C entities. */
final class ClientGolemRenderReview {
    private record Definition(String kind, String key, GolemProperties props) {}
    private record Spawned(String kind, String key, long props, int entityId) {}
    private static final List<Spawned> SPAWNED = new ArrayList<>();
    private static final JsonObject REPORT = new JsonObject();
    private static CompletableFuture<Void> operation;
    private static int stage, ticks, renderedFrames;
    private static boolean screenshotPending;
    private static Throwable screenshotFailure;
    private static final String IMAGE = "gameplay-golem-catalog.png";
    private ClientGolemRenderReview() {}
    static JsonObject evidence() { return REPORT.deepCopy(); }
    private static GolemProperties iron() { var p = GolemProperties.fromLong(0); p.setMaterial(java.util.Arrays.stream(GolemMaterial.getMaterials()).filter(m -> m.key.equals("IRON")).findFirst().orElseThrow()); return p; }
    private static List<Definition> definitions() {
        var rows = new ArrayList<Definition>();
        for (var material : GolemMaterial.getMaterials()) { var p = iron(); p.setMaterial(material); rows.add(new Definition("material", material.key, p)); }
        for (var head : GolemHead.getHeads()) { var p = iron(); p.setHead(head); rows.add(new Definition("head", head.key, p)); }
        for (var arms : GolemArm.getArms()) { var p = iron(); p.setArms(arms); rows.add(new Definition("arms", arms.key, p)); }
        for (var legs : GolemLeg.getLegs()) { var p = iron(); p.setLegs(legs); rows.add(new Definition("legs", legs.key, p)); }
        for (var addon : GolemAddon.getAddons()) { var p = iron(); p.setAddon(addon); rows.add(new Definition("addon", addon.key, p)); }
        return rows;
    }
    static boolean tick(Minecraft mc) throws Exception {
        require(ClientJeiReviewScenario.owns(mc.getSingleplayerServer()), "Golem visual fixtures require the owned fresh server");
        require(++ticks < 1200, "Golem visual capture timed out");
        if (screenshotFailure != null) throw new IllegalStateException("Golem framebuffer capture failed", screenshotFailure);
        if (stage == 0) {
            var definitions = definitions(); require(definitions.size() == 24, "Source 6/5/5/4/4 part catalogs must yield 24 views");
            var server = mc.getSingleplayerServer(); var owner = mc.player.getUUID(); operation = new CompletableFuture<>(); var pending = operation;
            server.execute(() -> { try {
                require(ClientJeiReviewScenario.owns(server), "Golem visual server ownership changed"); var player = server.getPlayerList().getPlayer(owner); require(player != null, "Actual visual owner disconnected"); player.closeContainer();
                var anchor = player.blockPosition().offset(-5, 0, 4); var level = player.serverLevel();
                for (int i = 0; i < definitions.size(); i++) {
                    var row = definitions.get(i); var pos = anchor.offset((i % 6) * 2, 0, (i / 6) * 2);
                    level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
                    var golem = Golem9EntityRegistries.GOLEM.create(level); require(golem != null, "Actual registered golem type missing");
                    golem.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5); golem.setProperties(row.props); golem.setOwnerId(owner); golem.setOwned(true); golem.setValidSpawn();
                    golem.updateEntityAttributes(); golem.setHealth(golem.getMaxHealth()); golem.setNoAi(true); golem.setNoGravity(true); golem.setPersistenceRequired();
                    require(level.addFreshEntity(golem), "Actual visual fixture spawn rejected"); SPAWNED.add(new Spawned(row.kind, row.key, row.props.toLong(), golem.getId()));
                }
                pending.complete(null);
            } catch (Throwable failure) { pending.completeExceptionally(failure); } });
            stage = 1; return false;
        }
        if (stage == 1) {
            if (!operation.isDone()) return false; operation.join();
            for (var row : SPAWNED) if (!(mc.level.getEntity(row.entityId) instanceof EntityThaumcraftGolem entity) || entity.getProperties().toLong() != row.props) return false;
            var meshes = new LinkedHashSet<ResourceLocation>(); var textures = new LinkedHashSet<ResourceLocation>(); meshes.add(ResourceLocation.parse("thaumcraft:models/obj/golem_base.obj"));
            for (var row : SPAWNED) {
                var entity = (EntityThaumcraftGolem) mc.level.getEntity(row.entityId); var props = entity.getProperties();
                require(mc.getEntityRenderDispatcher().getRenderer(entity) instanceof RenderThaumcraftGolem, "Actual native golem renderer must be bound"); textures.add(props.getMaterial().texture);
                for (var model : new PartModel[]{props.getHead().model, props.getArms().model, props.getLegs().model, props.getAddon().model}) if (model != null) { meshes.add(model.getObjModel()); if (model.getTexture() != null) textures.add(model.getTexture()); }
            }
            for (var mesh : meshes) require(!GolemObjMeshes.get(mesh).groups().isEmpty(), "Actual source OBJ has renderable groups: " + mesh);
            for (var texture : textures) require(mc.getResourceManager().getResource(texture).isPresent(), "Actual golem texture missing: " + texture);
            REPORT.addProperty("actualSourceObjModels", meshes.size()); REPORT.addProperty("actualMaterialAndPartTextures", textures.size());
            REPORT.addProperty("actualS2CEntities", SPAWNED.size()); REPORT.addProperty("fixtureScope", "source catalog representatives; one part varies at a time, other parts use native defaults");
            var entries = new JsonArray(); for (var row : SPAWNED) { var entry = new JsonObject(); entry.addProperty("kind", row.kind); entry.addProperty("key", row.key); entry.addProperty("entityId", row.entityId); entry.addProperty("props", row.props); entries.add(entry); } REPORT.add("views", entries);
            mc.setScreen(new CatalogScreen()); stage = 2; return false;
        }
        if (stage == 2) {
            if (renderedFrames < 20) return false;
            var file = ClientJeiReviewScenario.directory().resolve("screenshots").resolve(IMAGE); require(!Files.exists(file), "Golem native framebuffer must be fresh"); screenshotPending = true;
            Screenshot.grab(mc.gameDirectory, IMAGE, mc.getMainRenderTarget(), message -> mc.execute(() -> { try { require(Files.isRegularFile(file) && Files.size(file) > 1024, "Native golem framebuffer did not finish"); REPORT.addProperty("screenshot", file.toString()); REPORT.addProperty("nativeFrameRenderCalls", renderedFrames * SPAWNED.size()); REPORT.addProperty("nativeFramebuffer", true); } catch (Throwable failure) { screenshotFailure = failure; } finally { screenshotPending = false; } }));
            stage = 3; return false;
        }
        if (stage == 3) {
            if (screenshotPending) return false;
            mc.setScreen(null); var server = mc.getSingleplayerServer(); operation = new CompletableFuture<>(); var pending = operation;
            server.execute(() -> { try { require(ClientJeiReviewScenario.owns(server), "Golem visual cleanup changed ownership"); for (var row : SPAWNED) { var entity = server.overworld().getEntity(row.entityId); if (entity != null) entity.discard(); } pending.complete(null); } catch (Throwable failure) { pending.completeExceptionally(failure); } }); stage = 4; return false;
        }
        if (!operation.isDone()) return false; operation.join(); REPORT.addProperty("passed", true); return true;
    }
    private static final class CatalogScreen extends Screen {
        CatalogScreen() { super(Component.literal("Thaumcraft source golem catalog")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
            g.fill(0, 0, width, height, 0xff10141b); g.drawCenteredString(font, title, width / 2, 5, 0xffffff);
            int w = width / 6, h = (height - 20) / 4;
            for (int i = 0; i < SPAWNED.size(); i++) {
                var row = SPAWNED.get(i); var entity = (EntityThaumcraftGolem) minecraft.level.getEntity(row.entityId); int x = i % 6 * w, y = 18 + i / 6 * h;
                require(entity != null && entity.getProperties().toLong() == row.props, "Actual viewport entity changed");
                g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xff1d2633);
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 3, y + 3, x + w - 3, y + h - 13, Math.max(18, h - 22), .0625F, x + w / 2F + 10, y + h / 2F, entity);
                g.drawCenteredString(font, font.plainSubstrByWidth(row.kind + ":" + row.key, w - 4), x + w / 2, y + h - 11, 0xffffff);
            }
            g.flush(); renderedFrames++;
        }
    }
    private static void require(boolean value, String reason) { if (!value) throw new IllegalStateException(reason); }
}
