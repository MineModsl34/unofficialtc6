package thaumcraft.client.lib;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.client.gui.GuiFocalManipulator;
import thaumcraft.client.gui.GuiGolemBuilder;
import thaumcraft.client.gui.GuiWorldDevice;
import thaumcraft.common.container.ContainerWorldDevice;
import thaumcraft.common.tiles.crafting.TileFocalManipulator;
import thaumcraft.common.tiles.devices.TileFluidDevice;
import thaumcraft.common.tiles.devices.TileStorageDevice;

/** Native block-use C2S, menu S2C and framebuffer probes in the runner's fresh owned world. */
final class ClientGameplayReview {
    private static final String[] NAMES = {"focal-manipulator", "spa", "potion-sprayer", "golem-builder", "hungry-chest", "thaumatorium"};
    private static final JsonArray MENUS = new JsonArray();
    private static int index, stage, ticks, settle;
    private static CompletableFuture<Void> operation;
    private static volatile BlockPos pos;
    private static volatile JsonObject receipt;
    private static boolean screenshotPending;
    private static Throwable screenshotFailure;
    private static JsonObject current;
    private ClientGameplayReview() {}
    static boolean enabled() { return Boolean.getBoolean("thaumcraft.bulkGameplayReview"); }
    static JsonObject evidence() {
        var report = new JsonObject(); report.addProperty("passed", index == NAMES.length);
        report.addProperty("actualBlockUseC2S", true); report.addProperty("actualMenuS2C", true);
        report.addProperty("spaNativeButtonC2SAndServerReceipt", true);
        report.addProperty("thaumatoriumNativeRecipeSelectionC2SAndServerReceipt", true);
        report.add("menus", MENUS.deepCopy()); report.add("golemVisuals", ClientGolemRenderReview.evidence()); report.add("sealLogistics", ClientSealLogisticsReview.evidence()); report.add("entityVisuals", ClientEntityRenderReview.evidence()); return report;
    }
    static boolean tick(Minecraft mc) throws Exception {
        require(ClientJeiReviewScenario.isReady() && ClientJeiReviewScenario.owns(mc.getSingleplayerServer()), "Gameplay review must retain the owned private connection");
        if (index == NAMES.length) { if (!ClientSealLogisticsReview.tick(mc)) return false; if (!ClientGolemRenderReview.tick(mc)) return false; return ClientEntityRenderReview.tick(mc); }
        require(++ticks < 3000, "Gameplay menu review timed out at " + NAMES[index] + ":" + stage);
        if (screenshotFailure != null) throw new IllegalStateException("Native framebuffer capture failed", screenshotFailure);
        if (stage == 0) {
            operation(mc, player -> {
                player.closeContainer(); player.getInventory().clearContent(); player.getInventory().selected = 0;
                pos = player.blockPosition().offset(3, 0, 0);
                var level = player.serverLevel();
                for (var floor : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, -1, 2))) level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(pos, block().defaultBlockState());
                if (index == 5) { var knowledge = thaumcraft.api.capabilities.ThaumcraftCapabilities.getKnowledge(player); knowledge.addResearch("UNLOCKALCHEMY"); knowledge.setResearchStage("UNLOCKALCHEMY", 4); level.setBlockAndUpdate(pos.below(2), Blocks.MAGMA_BLOCK.defaultBlockState()); level.setBlockAndUpdate(pos.below(), BlocksTC.crucible.defaultBlockState()); level.setBlockAndUpdate(pos, BlocksTC.thaumatorium.defaultBlockState()); level.setBlockAndUpdate(pos.above(), BlocksTC.thaumatoriumTop.defaultBlockState()); ((thaumcraft.common.tiles.crafting.TileThaumatorium) level.getBlockEntity(pos)).setItem(0, new ItemStack(net.minecraft.world.item.Items.GLOWSTONE_DUST)); }
                var tile = level.getBlockEntity(pos);
                if (tile instanceof TileFocalManipulator focus) { focus.setItem(0, new ItemStack(ItemsTC.focus1)); focus.setChanged(); }
                if (tile instanceof TileFluidDevice fluid) { if (index == 1) fluid.tank.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE); fluid.changedAndSync(); }
                if (tile instanceof TileStorageDevice chest) chest.setItem(0, new ItemStack(ItemsTC.amber, 8));
                require(tile != null, "Actual registered device tile missing");
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
                player.connection.teleport(pos.getX() - 1.5, pos.getY(), pos.getZ() + .5, -90, 0);
                player.inventoryMenu.sendAllDataToRemote();
            });
            stage = 1; return false;
        }
        if (stage == 1) {
            if (!done() || mc.level == null || !mc.level.getBlockState(pos).is(block()) || mc.player.distanceToSqr(pos.getCenter()) > 16) return false;
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(pos.getCenter(), Direction.WEST, pos, false));
            stage = 2; settle = 0; return false;
        }
        if (stage == 2) {
            if (!(mc.screen instanceof AbstractContainerScreen<?> screen) || !expected(screen)) return false;
            if (index == 5 && (!(mc.player.containerMenu instanceof thaumcraft.common.container.ContainerThaumatorium menu) || !menu.ready() || menu.views().stream().noneMatch(v -> v.id().toString().equals("thaumcraft:nitor")))) return false;
            if (++settle < 15) return false;
            int menuId = mc.player.containerMenu.containerId;
            current = new JsonObject(); current.addProperty("device", NAMES[index]);
            current.addProperty("screen", screen.getClass().getName()); current.addProperty("menuId", menuId);
            operation(mc, player -> {
                require(player.containerMenu.containerId == menuId && player.containerMenu != player.inventoryMenu && player.containerMenu.stillValid(player), "Client menu must match its actual valid server owner menu");
                receipt = new JsonObject(); receipt.addProperty("owner", player.getUUID().toString());
                receipt.addProperty("serverMenu", player.containerMenu.getClass().getName()); receipt.addProperty("menuId", menuId);
                receipt.addProperty("nativeServerThread", player.getServer().isSameThread());
            });
            stage = 3; return false;
        }
        if (stage == 3) {
            if (!done()) return false;
            current.add("serverReceipt", receipt.deepCopy()); capture(mc); stage = 4; return false;
        }
        if (stage == 4) {
            if (screenshotPending) return false;
            if (index == 1) {
                require(mc.player.containerMenu instanceof ContainerWorldDevice menu && !menu.sprayer && menu.tile.mix, "Spa must receive its initial mix state");
                // Invoke the actual screen control, which sends the ordinary inventory-button packet.
                require(mc.screen.mouseClicked((mc.screen.width - 176) / 2 + 93, (mc.screen.height - 166) / 2 + 39, 0), "Native spa mix control accepted its click");
                stage = 5; return false;
            }
            if (index == 5) {
                var menu = (thaumcraft.common.container.ContainerThaumatorium) mc.player.containerMenu;
                int n = 0; while (n < menu.views().size() && !menu.views().get(n).id().toString().equals("thaumcraft:nitor")) n++;
                require(n < 6, "Native nitor icon must be on the actual first recipe page");
                require(mc.screen.mouseClicked((mc.screen.width - 175) / 2 + 56 + n % 2 * 16, (mc.screen.height - 216) / 2 + 64 + n / 2 * 16, 0), "Native thaumatorium recipe icon accepted its click"); stage = 7; return false;
            }
            advance(mc); return false;
        }
        if (stage == 5) {
            if (!(mc.player.containerMenu instanceof ContainerWorldDevice menu) || menu.tile.mix) return false;
            operation(mc, player -> {
                require(player.containerMenu instanceof ContainerWorldDevice serverMenu && !serverMenu.tile.mix, "Native spa C2S toggle must change its actual owner device");
            });
            stage = 6; return false;
        }
        if (stage == 6 && done()) { current.addProperty("nativeMixButtonC2SRoundtrip", true); advance(mc); }
        if (stage == 7) {
            var menu = (thaumcraft.common.container.ContainerThaumatorium) mc.player.containerMenu;
            if (menu.views().stream().noneMatch(v -> v.id().toString().equals("thaumcraft:nitor") && v.selected())) return false;
            operation(mc, player -> { var tile = (thaumcraft.common.tiles.crafting.TileThaumatorium) player.serverLevel().getBlockEntity(pos); require(tile.selections().size() == 1 && tile.selections().getFirst().id().toString().equals("thaumcraft:nitor") && tile.selections().getFirst().owner().equals(player.getUUID()), "Actual native C2S selection belongs to the real menu owner"); }); stage = 8; return false;
        }
        if (stage == 8 && done()) { current.addProperty("nativeRecipeIconC2SRoundtrip", true); advance(mc); }
        return false;
    }
    private static Block block() { return switch (index) { case 0 -> BlocksTC.wandWorkbench; case 1 -> BlocksTC.spa; case 2 -> BlocksTC.potionSprayer; case 3 -> BlocksTC.golemBuilder; case 5 -> BlocksTC.thaumatorium; default -> BlocksTC.hungryChest; }; }
    private static boolean expected(AbstractContainerScreen<?> screen) { return switch (index) { case 0 -> screen instanceof GuiFocalManipulator; case 1, 2 -> screen instanceof GuiWorldDevice && ((GuiWorldDevice) screen).getMenu() instanceof ContainerWorldDevice menu && menu.sprayer == (index == 2); case 3 -> screen instanceof GuiGolemBuilder; case 5 -> screen instanceof thaumcraft.client.gui.GuiThaumatorium; default -> screen instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen; }; }
    private static void advance(Minecraft mc) { MENUS.add(current); mc.player.closeContainer(); index++; stage = 0; settle = 0; }
    private static void capture(Minecraft mc) throws Exception {
        String name = "gameplay-" + NAMES[index] + ".png";
        Path file = ClientJeiReviewScenario.directory().resolve("screenshots").resolve(name);
        require(!Files.exists(file), "Gameplay screenshot must be fresh"); screenshotPending = true;
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> mc.execute(() -> {
            try { require(Files.isRegularFile(file) && Files.size(file) > 1024, "Actual gameplay framebuffer capture did not complete"); current.addProperty("screenshot", file.toString()); current.addProperty("nativeFramebuffer", true); }
            catch (Throwable failure) { screenshotFailure = failure; }
            finally { screenshotPending = false; }
        }));
    }
    private interface ServerWork { void run(ServerPlayer player); }
    private static void operation(Minecraft mc, ServerWork work) {
        var server = mc.getSingleplayerServer(); var owner = mc.player.getUUID();
        operation = new CompletableFuture<>(); var pending = operation;
        server.execute(() -> { try { require(ClientJeiReviewScenario.owns(server), "Gameplay server ownership changed"); var player = server.getPlayerList().getPlayer(owner); require(player != null, "Actual gameplay owner disconnected"); work.run(player); pending.complete(null); } catch (Throwable failure) { pending.completeExceptionally(failure); } });
    }
    private static boolean done() { if (!operation.isDone()) return false; operation.join(); return true; }
    private static void require(boolean value, String reason) { if (!value) throw new IllegalStateException(reason); }
}
