package thaumcraft.common.lib.events;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Static native event bridge contracts only; no Minecraft or visual acceptance. */
class ToolEventNativeContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/thaumcraft/common/lib/events", name + ".java"));
    }

    private static String body(String source, String signature) {
        int signatureStart = source.indexOf(signature);
        assertTrue(signatureStart >= 0, signature);
        int start = source.indexOf('{', signatureStart), depth = 1;
        for (int end = start + 1; end < source.length(); end++) {
            char current = source.charAt(end);
            if (current == '{') depth++;
            else if (current == '}' && --depth == 0) return source.substring(start + 1, end);
        }
        throw new AssertionError("Unclosed method: " + signature);
    }

    @Test void completedOffhandUseCannotSelectToolsForAttackMiningOrEntityDrops() throws Exception {
        String events = source("ToolEvents");
        // Modern stopUsingItem leaves the last-use hand bit set; these actions have their own tool.
        assertFalse(events.contains("getUsedItemHand("));
        for (String method : new String[]{"playerAttack", "breakBlock", "livingDrops"}) {
            assertTrue(body(events, "void " + method + "(").contains("player.getMainHandItem()"), method);
        }
    }

    @Test void blockInteractionUsesItsOwnHandForSoundingStackAndDurability() throws Exception {
        String interaction = body(source("ToolEvents"), "void rightClick(");
        assertTrue(interaction.contains("event.getItemStack()"));
        assertTrue(interaction.contains("event.getHand()"));
        assertTrue(interaction.contains("EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND"));
    }

    @Test void blockDropEnchantmentsUseSnapshotWhileDestructiveDurabilityUsesLiveMiningTool() throws Exception {
        String drops = body(source("ToolEvents"), "void harvestDrops(");
        // Vanilla can pass a pre-break copy: read its enchantments, but do not damage the copy.
        assertTrue(drops.contains("dropTool = event.getTool()"));
        assertTrue(drops.contains("ItemStack tool = dropTool"));
        assertTrue(drops.contains("level(tool, EnumInfusionEnchantment.REFINING)"));
        assertTrue(drops.contains("ItemStack miningTool = player.getMainHandItem()"));
        assertTrue(drops.contains("!miningTool.isEmpty()"));
        assertTrue(drops.contains("effective(miningTool, neighbor)"));
        assertTrue(drops.contains("damage(miningTool, player)"));
        assertFalse(drops.contains("effective(tool, neighbor)"));
        assertFalse(drops.contains("damage(tool, player)"));
    }

    @Test void runicShieldPresentationDoesNotDependOnPriorRecharge() throws Exception {
        String shield = body(source("RunicArmorEvents"), "boolean rendersShield(");
        // A first gear scan can find absorption already supplied by a golden apple or potion.
        assertTrue(shield.contains("getAbsorptionAmount()"));
        assertTrue(shield.contains("maxima.containsKey(player.getUUID())"));
        assertFalse(shield.contains("lastCharge"));
    }
}
