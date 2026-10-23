package thaumcraft.common.items.tools;

import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ScanningManager;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.network.misc.PacketAuraToClient;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.utils.ScanTargeting;
import thaumcraft.common.world.aura.AuraChunk;
import thaumcraft.common.world.aura.AuraHandler;

/** 4c server-authoritative scanning/aura slice. Arcane recipe and original rune/HUD rendering are deferred. */
public class ItemThaumometer extends Item {
    private static final ThreadLocal<BiConsumer<ServerPlayer, PacketAuraToClient>> TEST_AURA_SENDER = new ThreadLocal<>();

    public ItemThaumometer() { this(new Properties()); }

    public ItemThaumometer(Properties properties) { super(properties.stacksTo(1).rarity(Rarity.UNCOMMON)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            thaumcraft.common.lib.fx.SourceFX.scanRunes(player);
            level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundsTC.scan,
                SoundSource.PLAYERS, 0.5F, 1.0F, false);
        } else {
            // Normal server Item.use is the only authority. No client-supplied target or new C2S payload.
            ScanningManager.scanTheThing(player, ScanTargeting.scanTarget(player));
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!level.isClientSide && entity instanceof ServerPlayer owner && isLegacyHeldSlot(slot, selected, owner.getInventory().selected)
            && owner.tickCount % 20 == 0) updateAura(owner);
    }

    /** 1.12 passed LOCAL compartment indices AND compared currentItem against that local index.
     * Modern inventoryTick uses a global index/selected flag. In addition to the three local0
     * slots, a command-installed tool in armor[1..3] was selected when currentItem matched 1..3.
     * Preserve that oddity rather than accidentally treating NeoForge's corrected flag as TC6's. */
    public static boolean isLegacyHeldSlot(int slot, boolean selected, int selectedHotbarSlot) {
        int local = slot >= 36 && slot < 40 ? slot - 36 : slot == 40 ? 0 : slot;
        return selected || local == 0 || local == selectedHotbarSlot;
    }

    public static boolean exceedsFluxWarning(short base, float vis, float flux) {
        return flux > vis || flux > base / 3; // Deliberately integer division, as in TC6.
    }

    private static void updateAura(ServerPlayer owner) {
        var position = owner.blockPosition();
        // Cache lookup ONLY; do not load or generate a chunk/aura in response to this item.
        AuraChunk aura = AuraHandler.getAuraChunk(owner.level(), position.getX() >> 4, position.getZ() >> 4);
        if (aura == null) return;
        if (exceedsFluxWarning(aura.getBase(), aura.getVis(), aura.getFlux())
            && !ThaumcraftCapabilities.knowsResearch(owner, "FLUX")) {
            // Complex FLUX progression is currently explicitly unsupported by ResearchManager.
            // Preserve the source warning even if startResearchWithPopup returns false; never fake a grant.
            ResearchManager.startResearchWithPopup(owner, "FLUX");
            owner.displayClientMessage(Component.translatable("research.FLUX.warn").withStyle(ChatFormatting.DARK_PURPLE), true);
        }
        PacketAuraToClient packet = new PacketAuraToClient(aura);
        var capture = TEST_AURA_SENDER.get();
        if (capture != null) capture.accept(owner, packet);
        else PacketDistributor.sendToPlayer(owner, packet);
    }

    /** Owner-route observation for actual GameTests only, never an ordinary runtime network override. */
    public static AutoCloseable captureGameTestAuraPackets(BiConsumer<ServerPlayer, PacketAuraToClient> sender) {
        if (!GameTestHooks.isGametestServer()) throw new IllegalStateException("GameTest aura capture is disabled");
        var previous = TEST_AURA_SENDER.get();
        TEST_AURA_SENDER.set(Objects.requireNonNull(sender));
        return () -> {
            if (previous == null) TEST_AURA_SENDER.remove();
            else TEST_AURA_SENDER.set(previous);
        };
    }
}
