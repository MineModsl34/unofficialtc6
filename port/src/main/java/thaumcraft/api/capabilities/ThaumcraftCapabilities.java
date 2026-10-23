package thaumcraft.api.capabilities;

import java.util.function.Supplier;
import javax.annotation.Nonnull;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import thaumcraft.common.lib.TCAttachments;
import thaumcraft.common.lib.capabilities.PlayerKnowledge;
import thaumcraft.common.lib.capabilities.PlayerWarp;

/** Public personal-data access. Attachment types replace the removed Forge Capability objects. */
public final class ThaumcraftCapabilities {
    /** Assigned during attachment registration, not fake CapabilityInject compatibility objects. */
    public static AttachmentType<PlayerKnowledge> KNOWLEDGE;
    public static AttachmentType<PlayerWarp> WARP;

    private ThaumcraftCapabilities() {}

    public static IPlayerKnowledge getKnowledge(@Nonnull Player player) {
        return player.getData(TCAttachments.KNOWLEDGE);
    }

    public static IPlayerWarp getWarp(@Nonnull Player player) {
        return player.getData(TCAttachments.WARP);
    }

    public static boolean knowsResearch(@Nonnull Player player, @Nonnull String... research) {
        return evaluate(() -> getKnowledge(player), false, research);
    }

    public static boolean knowsResearchStrict(@Nonnull Player player, @Nonnull String... research) {
        return evaluate(() -> getKnowledge(player), true, research);
    }

    /** Pure data query using the same source evaluator, including its original OR quirks/errors. */
    public static boolean knowsResearchData(IPlayerKnowledge knowledge, String... research) {
        return evaluate(() -> knowledge, false, research);
    }

    /** Pure strict query; a qualified KEY@N is known-stage checked, not completion checked. */
    public static boolean knowsResearchStrictData(IPlayerKnowledge knowledge, String... research) {
        return evaluate(() -> knowledge, true, research);
    }

    private static boolean evaluate(Supplier<IPlayerKnowledge> knowledge, boolean strict, String... research) {
        for (String key : research) {
            if (key.contains("&&")) {
                if (!evaluate(knowledge, strict, key.split("&&"))) {
                    return false;
                }
            } else if (key.contains("||")) {
                // Deliberately preserve TC6's regex split, failed-OR fall-through and early return.
                for (String alternative : key.split("||")) {
                    if (evaluate(knowledge, strict, alternative)) {
                        return true;
                    }
                }
            } else if (strict && !key.contains("@")) {
                if (!knowledge.get().isResearchComplete(key)) {
                    return false;
                }
            } else if (!knowledge.get().isResearchKnown(key)) {
                return false;
            }
        }
        return true;
    }
}
