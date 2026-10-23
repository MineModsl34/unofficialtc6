package thaumcraft.common.items.curios;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.research.ResearchManager;

/** Normal TC6 book. The source GUI12 has no server container; a Dist.CLIENT registration
 * supplies its opener without loading client classes on the dedicated server.
 * No cheat variant, recursive cheat grants, client classes or invented unlock.
 * Acquisition markers belong to the source pickup PRE event, never to Item.use.
 */
public class ItemThaumonomicon extends Item {
    private static volatile Consumer<Player> clientOpener = ignored -> {};

    /** Installed on client setup MAIN only. Common code never names a client class. */
    public static void registerClientOpener(Consumer<Player> opener) {
        clientOpener = Objects.requireNonNull(opener, "Client book opener");
    }

    public ItemThaumonomicon() { this(new Properties()); }

    public ItemThaumonomicon(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.UNCOMMON).setNoRepair());
    }

    // Imported lang keys preserve the source normal metadata variant; registry id stays
    // thaumonomicon so existing research JSON and legacy scan identity remain unchanged.
    @Override public String getDescriptionId() { return "item.thaumcraft.thaumonomicon_normal"; }
    @Override public String getDescriptionId(ItemStack stack) { return getDescriptionId(); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundsTC.page,
                SoundSource.PLAYERS, 1.0F, 1.0F, false);
            clientOpener.accept(player);
        } else if (player instanceof ServerPlayer owner) {
            // TC6 ItemThaumonomicon:71-84 repairs siblings of ANY known entry, including known0.
            // Preserve registration order and the known (not strictly complete) predicate.
            // The existing Manager supplies bounded graph/cycle and unresolved-effect guards;
            // unsupported siblings remain unsupported, rather than becoming cheat-sheet grants.
            for (var category : ResearchCategories.researchCategories.values()) {
                for (var entry : category.research.values()) {
                    if (ThaumcraftCapabilities.knowsResearch(owner, entry.getKey()) && entry.getSiblings() != null) {
                        for (String sibling : entry.getSiblings()) {
                            if (!ThaumcraftCapabilities.knowsResearch(owner, sibling)) {
                                ResearchManager.completeResearch(owner, sibling);
                            }
                        }
                    }
                }
            }
            ThaumcraftCapabilities.getKnowledge(owner).sync(owner);
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}
