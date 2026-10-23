package thaumcraft.common.lib.research;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.IScanThing;
import thaumcraft.common.lib.TCTags;
import thaumcraft.common.lib.capabilities.PlayerDataSync;

/** TC6 celestial note scanner. CELESTIALSCANNING must already be strictly complete;
 * its actual knowledge-cost progression remains outside the narrow current research bridge.
 * Daily marker storage/events are real, not an automatic unlock or arbitrary C2S grant. */
public final class ScanSky implements IScanThing {
    @Override
    public boolean checkThing(Player player, Object object) {
        return basicConditions(player, object)
            // Source checked provider TYPE, not the level/dimension ID.
            && player.level().dimensionTypeRegistration().is(BuiltinDimensionTypes.OVERWORLD)
            && ThaumcraftCapabilities.knowsResearchStrict(player, "CELESTIALSCANNING")
            && aim(player).scannable();
    }

    private static boolean basicConditions(Player player, Object object) {
        return object == null && !(player.getXRot() > 0.0F)
            // Old Entity.getPosition floors y+0.5 before ScanSky adds up().
            && player.level().canSeeSky(BlockPos.containing(player.getX(), player.getY() + 0.5D, player.getZ()).above());
    }

    private static LegacySkyScanGeometry.Aim aim(Player player) {
        var type = player.level().dimensionTypeRegistration();
        float angle;
        // Preserve original vanilla provider overrides if onSuccess is called directly.
        if (type.is(BuiltinDimensionTypes.NETHER)) angle = 0.5F;
        else if (type.is(BuiltinDimensionTypes.END)) angle = 0.0F;
        else angle = LegacySkyScanGeometry.celestialAngle(player.level().dimensionType().fixedTime()
            .orElse(player.level().getDayTime()));
        // For addon dimensions this is the explicit modern fixed-time/vanilla-angle bridge;
        // arbitrary old WorldProvider angle overrides have no equivalent API here.
        return LegacySkyScanGeometry.aim(player.getYRot(), player.getXRot(), angle);
    }

    @Override
    public void onSuccess(Player player, Object object) {
        if (!(player instanceof ServerPlayer owner) || !basicConditions(player, object)
            || !ThaumcraftCapabilities.knowsResearchStrict(player, "CELESTIALSCANNING")) return;
        // Deliberately NO dimension-type check: TC6 omitted it in onSuccess itself.
        var note = LegacySkyScanGeometry.note(aim(player), player.level().getDayTime(), player.getYRot());
        if (note == null) return;
        String prefix = LegacySkyScanGeometry.dayPrefix(player.level().getGameTime());
        String key = prefix + note.suffix();
        if (ThaumcraftCapabilities.knowsResearch(player, key)) {
            failure(player, "tc.celestial.fail.1");
            return; // Duplicates do not consume, clean old markers or mark dirty.
        }
        if (hasScribingTools(player) && consumePaper(player)) {
            ItemStack stack = new ItemStack(ItemsTC.celestialNotesVariants[note.metadata()]);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            // Source grants notes BEFORE its cancellable progress event. Do not roll back
            // paper/note on cancellation or invent a successful daily key after rejection.
            ThaumcraftApi.internalMethods.progressResearch(player, key);
        } else {
            failure(player, "tc.celestial.fail.2");
        }
        cleanResearch(owner, prefix); // Also when supplies are missing or progress canceled.
    }

    private static boolean hasScribingTools(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            // Exact source Item identity; the 32767 damage sentinel ignores wear/NBT.
            // IScribeTools is an API marker, NOT permission to use arbitrary addon tools here.
            if (!stack.isEmpty() && stack.is(ItemsTC.scribingTools)) return true;
        }
        return false;
    }

    private static boolean consumePaper(Player player) {
        var inventory = player.getInventory().items; // The 36 main slots, NOT armor/offhand.
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.get(slot);
            // Original ore=true accepted OreDictionary paper aliases. The narrow tag bridge
            // is explicit; this does not implement the whole old OreDictionary graph.
            if (!stack.isEmpty() && (stack.is(Items.PAPER) || stack.is(TCTags.Items.SCAN_PAPER))) {
                stack.shrink(1);
                if (stack.isEmpty()) inventory.set(slot, ItemStack.EMPTY);
                return true;
            }
        }
        return false;
    }

    private static void cleanResearch(ServerPlayer player, String prefix) {
        var knowledge = ThaumcraftCapabilities.getKnowledge(player);
        var stale = new ArrayList<String>();
        for (String key : knowledge.getResearchList()) {
            if (key.startsWith("CEL_") && !key.startsWith(prefix)) stale.add(key);
        }
        for (String key : stale) knowledge.removeResearch(key);
        PlayerDataSync.markKnowledgeDirty(player);
    }

    private static void failure(Player player, String key) {
        player.displayClientMessage(Component.translatable(key, ""), true);
    }

    @Override
    public String getResearchKey(Player player, Object object) { return ""; }
}
