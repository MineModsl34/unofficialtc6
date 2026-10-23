package thaumcraft.gametest;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.HashSet;
import java.util.List;
import java.util.Arrays;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.items.CasterStoredStack;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.common.items.casters.ItemCaster;
import thaumcraft.common.items.casters.ItemFocus;
import thaumcraft.common.items.casters.foci.FocusEffectExchange;

/** Native registered components and whole ItemStack codecs, including the dev-time
 * equality validation that rejected the former mutable ItemStack component values. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class CasterStoredStackGameTests {
    @GameTest(template = "empty")
    public static void focusAndPickedBlockRetainContentWithoutMutableAliases(GameTestHelper h) {
        var item = (ItemCaster) ItemsTC.casterBasic;
        var caster = new ItemStack(item);
        var focus = exchangeFocus();
        var picked = new ItemStack(Items.STONE, 12);
        picked.set(DataComponents.CUSTOM_NAME, Component.literal("Original picked stack"));
        var inputAspects = new AspectList().add(Aspect.AIR, 3); picked.set(TCDataComponents.ASPECTS, inputAspects);
        var expectedFocus = focus.copy(); var expectedPicked = picked.copy();
        expectedPicked.set(TCDataComponents.ASPECTS, inputAspects.copy());
        item.setFocus(caster, focus); item.storePickedBlock(caster, picked);
        var storedFocus = caster.get(TCDataComponents.CASTER_FOCUS);
        var storedPicked = caster.get(TCDataComponents.CASTER_PICKED);
        int focusHash = storedFocus.hashCode(), pickedHash = storedPicked.hashCode();
        var keys = new HashSet<>(List.of(storedFocus, storedPicked));
        byte[] pickedBytes = wireBytes(h, storedPicked.copy());

        focus.setCount(7); focus.remove(DataComponents.CUSTOM_NAME);
        CustomData.update(DataComponents.CUSTOM_DATA, focus, tag -> tag.putString("inputMutation", "independent"));
        picked.setCount(1); picked.remove(DataComponents.CUSTOM_NAME);
        inputAspects.add(Aspect.AIR, 20);
        h.assertTrue(ItemStack.matches(item.getFocusStack(caster), expectedFocus)
            && ItemStack.matches(item.getPickedBlock(caster), expectedPicked), "input mutation cannot alter the native stored focus or picked block");
        var returnedFocus = item.getFocusStack(caster); var returnedPicked = item.getPickedBlock(caster);
        returnedFocus.setCount(2); returnedFocus.remove(DataComponents.CUSTOM_NAME);
        CustomData.update(DataComponents.CUSTOM_DATA, returnedFocus, tag -> tag.putString("returnedMutation", "independent"));
        returnedPicked.shrink(11); returnedPicked.remove(DataComponents.CUSTOM_NAME);
        returnedPicked.get(TCDataComponents.ASPECTS).add(Aspect.FIRE, 5);
        h.assertTrue(ItemStack.matches(item.getFocusStack(caster), expectedFocus)
            && ItemStack.matches(item.getPickedBlock(caster), expectedPicked), "public caster getters return independent native stack copies");
        h.assertTrue(storedFocus.hashCode() == focusHash && storedPicked.hashCode() == pickedHash
            && keys.contains(new CasterStoredStack(expectedFocus)) && keys.contains(new CasterStoredStack(expectedPicked)),
            "equal contents retain stable hashes after both input and returned-copy mutation");
        h.assertTrue(Arrays.equals(pickedBytes, wireBytes(h, storedPicked.copy())),
            "input and returned AspectList mutations cannot change the immutable picked value, hash or native network bytes");
        var changedCount = expectedPicked.copy(); changedCount.setCount(11);
        var changedComponents = expectedPicked.copy(); changedComponents.remove(DataComponents.CUSTOM_NAME);
        h.assertFalse(storedPicked.equals(new CasterStoredStack(changedCount)), "stored equality includes count");
        h.assertTrue(storedPicked.hashCode() != new CasterStoredStack(changedCount).hashCode(), "count contributes to stored hash");
        h.assertFalse(storedPicked.equals(new CasterStoredStack(changedComponents)), "stored equality includes all native components");
        item.setFocus(caster, ItemStack.EMPTY);
        h.assertTrue(!caster.has(TCDataComponents.CASTER_FOCUS) && item.getFocusStack(caster) == null,
            "source empty-focus setter removes the component rather than storing an empty focus");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void registeredCasterCodecsKeepOriginalOptionalStackShapeAndSync(GameTestHelper h) {
        var item = (ItemCaster) ItemsTC.casterBasic;
        var caster = new ItemStack(item); var focus = exchangeFocus(); var picked = new ItemStack(Items.OAK_PLANKS, 9);
        picked.set(DataComponents.CUSTOM_NAME, Component.literal("Serialized picked block"));
        item.setFocus(caster, focus); item.storePickedBlock(caster, picked);
        var registries = h.getLevel().registryAccess();
        // Native component setters above execute equality validation; whole-stack save
        // additionally verifies the registered persistent codecs and their existing shape.
        var saved = (CompoundTag) caster.save(registries); var components = saved.getCompound("components");
        h.assertTrue(components.get("thaumcraft:caster_focus").equals(focus.saveOptional(registries))
            && components.get("thaumcraft:caster_picked").equals(picked.saveOptional(registries)),
            "existing focus/picked components remain plain native ItemStack compounds without an extra wrapper field");
        var restored = ItemStack.parse(registries, saved).orElseThrow();
        h.assertTrue(ItemStack.matches(caster, restored) && ItemStack.matches(item.getFocusStack(restored), focus)
            && ItemStack.matches(item.getPickedBlock(restored), picked), "old-shaped registered NBT restores whole caster content and both public getters");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            ItemStack.STREAM_CODEC.encode(buffer, caster);
            var synced = ItemStack.STREAM_CODEC.decode(buffer);
            h.assertTrue(ItemStack.matches(caster, synced) && ItemStack.matches(item.getFocusStack(synced), focus)
                && ItemStack.matches(item.getPickedBlock(synced), picked) && buffer.readableBytes() == 0,
                "real registered whole-stack network codec preserves both nested components and consumes the packet exactly");
        } finally { buffer.release(); }
        verifyOptionalShape(h, TCDataComponents.CASTER_FOCUS, focus);
        verifyOptionalShape(h, TCDataComponents.CASTER_PICKED, picked);
        verifyOptionalShape(h, TCDataComponents.CASTER_FOCUS, ItemStack.EMPTY);
        verifyOptionalShape(h, TCDataComponents.CASTER_PICKED, ItemStack.EMPTY);
        var zeroCount = new ItemStack(Items.STONE);
        zeroCount.set(TCDataComponents.ASPECTS, new AspectList().add(Aspect.AIR, 3)); zeroCount.setCount(0);
        var emptyPatch = ItemStack.EMPTY.getComponentsPatch();
        h.assertTrue(new CasterStoredStack(zeroCount).copy() == ItemStack.EMPTY
            && ItemStack.EMPTY.getComponentsPatch().equals(emptyPatch),
            "zero-count aspect-bearing input normalizes to native EMPTY without changing its global component patch");
        verifyOptionalShape(h, TCDataComponents.CASTER_PICKED, zeroCount);
        h.succeed();
    }

    private static void verifyOptionalShape(GameTestHelper h, DataComponentType<CasterStoredStack> type, ItemStack original) {
        var registries = h.getLevel().registryAccess(); var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        var oldPayload = ItemStack.OPTIONAL_CODEC.encodeStart(ops, original).getOrThrow();
        var decoded = type.codec().parse(ops, oldPayload).getOrThrow();
        h.assertTrue(decoded.equals(new CasterStoredStack(original))
            && type.codec().encodeStart(ops, decoded).getOrThrow().equals(oldPayload), "registered persistent codec accepts and re-emits the exact original optional stack payload");
        var originalBytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        var componentBytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(originalBytes, original);
            type.streamCodec().encode(componentBytes, new CasterStoredStack(original));
            h.assertTrue(ByteBufUtil.equals(originalBytes, componentBytes), "registered mapped component emits byte-identical original optional stack wire data");
            h.assertTrue(type.streamCodec().decode(componentBytes).equals(decoded) && componentBytes.readableBytes() == 0,
                "registered optional network payload decodes by content and consumes all bytes, including empty");
        } finally { originalBytes.release(); componentBytes.release(); }
    }

    private static ItemStack exchangeFocus() {
        var focus = new ItemStack(ItemsTC.focus1); var pack = new FocusPackage(); pack.addNode(new FocusEffectExchange());
        ItemFocus.setPackage(focus, pack); focus.set(DataComponents.CUSTOM_NAME, Component.literal("Original exchange focus"));
        return focus;
    }

    private static byte[] wireBytes(GameTestHelper h, ItemStack stack) {
        var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try { ItemStack.OPTIONAL_STREAM_CODEC.encode(bytes, stack); return ByteBufUtil.getBytes(bytes); }
        finally { bytes.release(); }
    }
}
