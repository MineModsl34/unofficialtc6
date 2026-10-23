package thaumcraft.common.lib.events;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.items.curios.ItemThaumonomicon;
import thaumcraft.common.items.resources.ItemCrystalEssence;

/** Narrow TC6 PlayerEvents:130-176 bootstrap. Raw known0 markers and immediate owner sync,
 * not progression/ResearchEvents/XP/flags/dirty. The old event was pickup PRE: an inventory
 * insertion, target-owner check or later event listener may still prevent actual acquisition.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class BookAcquisitionEvents {
    private BookAcquisitionEvents() {}

    @SubscribeEvent
    public static void pickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer owner) || event.getItemEntity() == null
            || owner.level().isClientSide || event.canPickup().isFalse()) return;
        ItemEntity item = event.getItemEntity();
        // Old EntityItem:391 returns BEFORE posting PRE when pickupDelay > 0. Modern PRE is
        // earlier; preserve that boundary even when an addon explicitly forces pickup TRUE.
        if (item.hasPickUpDelay()) return;
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        var stack = item.getItem();
        if (stack.getItem() instanceof ItemCrystalEssence && !knowledge.isResearchKnown("!gotcrystals")) {
            knowledge.addResearch("!gotcrystals");
            knowledge.sync(owner);
            owner.sendSystemMessage(Component.translatable("got.crystals")
                .withStyle(ChatFormatting.DARK_PURPLE));
            if (ModConfig.CONFIG_MISC.noSleep && !knowledge.isResearchKnown("!gotdream")) giveDreamJournal(owner);
        }
        if (stack.getItem() instanceof ItemThaumonomicon && !knowledge.isResearchKnown("!gotthaumonomicon")) {
            knowledge.addResearch("!gotthaumonomicon");
            knowledge.sync(owner);
        }
    }

    @SubscribeEvent
    public static void wakeUp(PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer owner && !owner.level().isClientSide) {
            var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
            // TC6 deliberately checks neither dawn, duration, wakeImmediately nor updateLevel.
            if (knowledge.isResearchKnown("!gotcrystals") && !knowledge.isResearchKnown("!gotdream")) giveDreamJournal(owner);
        }
    }

    /** Typed data-component bridge for ConfigItems.startBook, not a new research item or grant.
     * NeoForge's common LanguageHook loads default mod translations on the dedicated server.
     * Original pages are translated JSON component strings; decode them instead of displaying
     * their surrounding JSON quotation marks. Translation is fixed when the journal is given,
     * not a client-side optimistic action or a client-class localization dependency.
     * Pack fix: a dedicated server only has en_us in Language, so the text is taken from the mod's
     * lang file for the receiving player's client language (falls back to Language per key).
     */
    public static ItemStack createDreamJournal(Player player) {
        Language language = Language.getInstance();
        Map<String, String> playerLang = player instanceof ServerPlayer sp
            ? langFile(sp.clientInformation().language()) : Map.of();
        List<Filterable<Component>> pages = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            String raw = playerLang.getOrDefault("book.start." + page, language.getOrDefault("book.start." + page));
            Component component;
            try {
                component = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(raw)).getOrThrow();
            } catch (RuntimeException invalidTranslatedPage) {
                // Same plain-text fallback as vanilla's old written-book page resolution.
                component = Component.literal(raw);
            }
            pages.add(Filterable.passThrough(component));
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
            Filterable.passThrough(playerLang.getOrDefault("book.start.title", language.getOrDefault("book.start.title"))), player.getName().getString(),
            3, List.copyOf(pages), false));
        return book;
    }

    /** The mod's own assets/thaumcraft/lang/<code>.json, or empty when missing or unreadable. */
    private static Map<String, String> langFile(String code) {
        String name = "/assets/thaumcraft/lang/" + code.toLowerCase(Locale.ROOT) + ".json";
        try (InputStream in = BookAcquisitionEvents.class.getResourceAsStream(name)) {
            if (in == null) return Map.of();
            Map<String, String> out = new HashMap<>();
            Language.loadFromJson(in, out::put);
            return out;
        } catch (IOException | RuntimeException unreadable) {
            return Map.of();
        }
    }

    private static void giveDreamJournal(ServerPlayer owner) {
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        knowledge.addResearch("!gotdream");
        knowledge.sync(owner); // Source sync precedes the inventory insertion/drop.
        ItemStack book = createDreamJournal(owner);
        if (!owner.getInventory().add(book)) {
            // Exact InventoryUtils.dropItemAtEntity: position + half eye-height, vanilla item
            // default launch velocity/no pickup delay. This is NOT the later special dust drop.
            ItemEntity dropped = new ItemEntity(owner.level(), owner.getX(),
                owner.getY() + owner.getEyeHeight() / 2.0F, owner.getZ(), book.copy());
            owner.level().addFreshEntity(dropped);
        }
        try {
            owner.sendSystemMessage(Component.translatable("got.dream")
                .withStyle(ChatFormatting.DARK_PURPLE));
        } catch (RuntimeException ignored) {
            // TC6 catches the optional chat notification after marker/sync/journal delivery.
        }
    }
}
