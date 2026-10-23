package thaumcraft.common.items.curios;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.items.IScribeTools;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.items.tools.ItemScribingTools;

/** Registered item prerequisites only, not celestial progression, recipes or a visual model audit. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class CelestialPrerequisiteGameTests {
    @GameTest(template = "empty")
    public static void writingToolKeepsSourceDurabilityMarkerAndPassiveItemBehavior(GameTestHelper h) {
        h.assertTrue(ItemsTC.scribingTools instanceof ItemScribingTools && ItemsTC.scribingTools instanceof IScribeTools,
            "real registered writing tool implements the original marker API");
        h.assertTrue(BuiltInRegistries.ITEM.getKey(ItemsTC.scribingTools).equals(tc("scribing_tools")), "tool uses its actual source registry id");
        ItemStack stack = new ItemStack(ItemsTC.scribingTools);
        h.assertTrue(stack.getMaxStackSize() == 1 && stack.isDamageableItem() && stack.getMaxDamage() == 100,
            "source tool is single-stack with one hundred durability");
        h.assertTrue(stack.getRarity() == Rarity.COMMON && stack.getDamageValue() == 0, "source base defaults remain common and unused");
        h.assertFalse(ItemsTC.scribingTools.isRepairable(stack), "ItemTCBase ordinary-repair prohibition retained");
        h.assertFalse(ItemsTC.scribingTools.hasCraftingRemainingItem(stack), "no invented ink/refill crafting remainder");
        h.assertTrue(ItemsTC.scribingTools.getCraftingRemainingItem(stack).isEmpty(), "default remainder is empty");
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        stack.setDamageValue(37);
        owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
        ItemsTC.scribingTools.inventoryTick(stack, h.getLevel(), owner, 0, false);
        var result = ItemsTC.scribingTools.use(h.getLevel(), owner, InteractionHand.MAIN_HAND);
        h.assertTrue(result.getResult() == InteractionResult.PASS && result.getObject() == stack,
            "source passive tool adds no new right-click action");
        h.assertTrue(stack.getDamageValue() == 37 && stack.getCount() == 1,
            "passive tick/use invent neither consumption, recharge nor refill");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void allThirteenRegisteredNotesRetainMetadataSharedNameAndAquaTooltip(GameTestHelper h) {
        h.assertTrue(ItemsTC.celestialNotesVariants.length == 13 && ItemsTC.celestialNotes == ItemsTC.celestialNotesVariants[0],
            "source alias resolves to sun without collapsing the other twelve variants");
        for (int metadata = 0; metadata < 13; metadata++) {
            Item registered = ItemsTC.celestialNotesVariants[metadata];
            h.assertTrue(registered instanceof ItemCelestialNotes, "real note variant uses its behavior class");
            ItemCelestialNotes notes = (ItemCelestialNotes) registered;
            h.assertTrue(notes.metadata() == metadata && notes.variantName().equals(ItemCelestialNotes.variantName(metadata)),
                "registered variant retains the original metadata order");
            h.assertTrue(notes.textureSuffix().equals(ItemCelestialNotes.textureSuffix(metadata)), "original overlay filename helper");
            h.assertTrue(BuiltInRegistries.ITEM.getKey(notes).equals(tc("celestial_notes_" + notes.variantName())),
                "split variant has its own expected registry id");
            ItemStack stack = new ItemStack(notes);
            h.assertTrue(stack.getMaxStackSize() == 64 && !stack.isDamageableItem() && stack.getRarity() == Rarity.COMMON,
                "source notes keep default stack size, no durability and common rarity");
            h.assertTrue(notes.getDescriptionId().equals("item.celestial_notes")
                && notes.getDescriptionId(stack).equals("item.celestial_notes"), "all thirteen display the source shared name");
            List<Component> tooltip = new ArrayList<>();
            notes.appendHoverText(stack, Item.TooltipContext.EMPTY, tooltip, TooltipFlag.NORMAL);
            h.assertTrue(tooltip.size() == 1 && tooltip.getFirst().getContents() instanceof TranslatableContents,
                "source adds exactly one untranslated variant tooltip component");
            var translated = (TranslatableContents) tooltip.getFirst().getContents();
            h.assertTrue(translated.getKey().equals("item.celestial_notes." + notes.variantName() + ".text")
                && tooltip.getFirst().getStyle().getColor().getValue() == ChatFormatting.AQUA.getColor(),
                "tooltip keeps the exact old variant key and AQUA color");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualRegistryStackCodecKeepsEachNoteVariantAndToolDamage(GameTestHelper h) {
        for (int metadata = 0; metadata < 13; metadata++) {
            ItemStack original = new ItemStack(ItemsTC.celestialNotesVariants[metadata], metadata + 1);
            ItemStack decoded = roundTrip(h, original);
            h.assertTrue(decoded.is(original.getItem()) && decoded.getCount() == metadata + 1,
                "actual stack codec retains split note identity and count");
            h.assertTrue(((ItemCelestialNotes) decoded.getItem()).metadata() == metadata,
                "metadata interpretation survives registry-based serialization");
        }
        ItemStack original = new ItemStack(ItemsTC.scribingTools);
        original.setDamageValue(99);
        ItemStack decoded = roundTrip(h, original);
        h.assertTrue(decoded.is(ItemsTC.scribingTools) && decoded.getDamageValue() == 99 && decoded.getMaxDamage() == 100,
            "source tool wear survives the real codec without refill");
        h.succeed();
    }

    private static ItemStack roundTrip(GameTestHelper h, ItemStack original) {
        var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(bytes, original);
            return ItemStack.STREAM_CODEC.decode(bytes);
        } finally { bytes.release(); }
    }

    private static ResourceLocation tc(String path) {
        return ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, path);
    }
}
