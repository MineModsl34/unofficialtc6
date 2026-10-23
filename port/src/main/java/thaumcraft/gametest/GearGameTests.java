package thaumcraft.gametest;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.items.GearHelper;
import thaumcraft.api.items.IWarpingGear;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.items.RechargeHelper;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.common.items.baubles.ItemAmuletVis;
import thaumcraft.common.items.baubles.ItemBaubles;
import thaumcraft.common.items.curios.ItemPrimordialPearl;
import thaumcraft.common.world.aura.AuraChunk;
import thaumcraft.common.world.aura.AuraHandler;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public class GearGameTests {
    @GameTest(template = "empty")
    public static void lootGearHasExactOriginalMaterialProperties(GameTestHelper h) {
        List<Item> thaumium = List.of(ItemsTC.thaumiumBoots, ItemsTC.thaumiumLegs, ItemsTC.thaumiumChest, ItemsTC.thaumiumHelm);
        List<Item> voidArmor = List.of(ItemsTC.voidBoots, ItemsTC.voidLegs, ItemsTC.voidChest, ItemsTC.voidHelm);
        int[] durability = {13, 15, 16, 11};
        int[] thaumiumDefense = {2, 5, 6, 2};
        int[] voidDefense = {3, 6, 8, 3};
        for (int index = 0; index < 4; index++) {
            assertArmor(h, thaumium.get(index), durability[index] * 25, thaumiumDefense[index], 25, false);
            assertArmor(h, voidArmor.get(index), durability[index] * 10, voidDefense[index], 10, true);
        }
        assertSword(h, ItemsTC.thaumiumSword, 500, 22, 5.5D);
        assertSword(h, ItemsTC.voidSword, 150, 10, 6.0D);
        h.assertTrue(ItemsTC.thaumiumSword.isValidRepairItem(new ItemStack(ItemsTC.thaumiumSword), new ItemStack(ItemsTC.ingotThaumium)),
            "thaumium sword repairs with thaumium");
        h.assertTrue(ItemsTC.voidSword.isValidRepairItem(new ItemStack(ItemsTC.voidSword), new ItemStack(ItemsTC.ingotVoid)),
            "void sword repairs with void metal");
        h.assertFalse(ItemsTC.voidSword.isValidRepairItem(new ItemStack(ItemsTC.voidSword), new ItemStack(Items.IRON_INGOT)),
            "void sword does not acquire vanilla iron repair");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void primordialPearlCraftingUsesAllEightOriginalStates(GameTestHelper h) {
        ItemPrimordialPearl pearl = (ItemPrimordialPearl) ItemsTC.primordialPearl;
        for (int damage = 0; damage <= 8; damage++) {
            ItemStack stack = new ItemStack(pearl);
            stack.setDamageValue(damage);
            h.assertTrue(stack.getMaxDamage() == 8 && stack.getMaxStackSize() == 1, "pearl is single, durability eight");
            h.assertTrue(stack.getRarity() == Rarity.UNCOMMON, "pearl rarity uncommon");
            h.assertFalse(pearl.isEnchantable(stack), "pearl cannot enchant");
            h.assertFalse(pearl.isValidRepairItem(stack, new ItemStack(pearl)), "pearl cannot repair even with itself");
            h.assertTrue(ItemPrimordialPearl.modelType(stack) == (damage < 3 ? 0 : damage < 6 ? 1 : 2), "three visual stages");
            String suffix = damage < 3 ? "_pearl" : damage < 6 ? "_nodule" : "_mote";
            h.assertTrue(pearl.getDescriptionId(stack).endsWith(suffix), "stage-specific translated name");
            ItemStack remainder = pearl.getCraftingRemainingItem(stack);
            h.assertTrue(pearl.hasCraftingRemainingItem(stack) == (damage < 7), "remaining-item boundary is seven, not max damage eight");
            if (damage < 7) {
                h.assertTrue(remainder.is(pearl) && remainder.getDamageValue() == damage + 1 && remainder.getCount() == 1,
                    "craft advances one state");
            } else {
                h.assertTrue(remainder.isEmpty(), "final mote is consumed");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void voidGearRepairsThroughRealInventoryTicksWithOriginalDoubleArmorHook(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack carried = new ItemStack(ItemsTC.voidChest);
        ItemStack worn = new ItemStack(ItemsTC.voidHelm);
        ItemStack sword = new ItemStack(ItemsTC.voidSword);
        carried.setDamageValue(10);
        worn.setDamageValue(10);
        sword.setDamageValue(10);
        player.getInventory().items.set(0, carried);
        player.getInventory().items.set(1, sword);
        player.getInventory().armor.set(3, worn);
        player.tickCount = 39;
        player.getInventory().tick();
        h.assertTrue(carried.getDamageValue() == 10 && worn.getDamageValue() == 10 && sword.getDamageValue() == 10, "no repair off cadence");
        player.tickCount = 40;
        player.getInventory().tick();
        h.assertTrue(carried.getDamageValue() == 9, "carried armor repairs once");
        h.assertTrue(worn.getDamageValue() == 8, "equipped armor retains both original repair hooks");
        h.assertTrue(sword.getDamageValue() == 9, "unselected sword still repairs once");
        worn.setDamageValue(1);
        player.getInventory().tick();
        h.assertTrue(worn.getDamageValue() == 0, "double hook never repairs below zero");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void voidSwordWeaknessRetainsPvpGateAndSixtyTickDuration(GameTestHelper h) {
        var server = h.getLevel().getServer();
        boolean oldPvp = server.isPvpAllowed();
        try {
            Player attacker = h.makeMockPlayer(GameType.SURVIVAL);
            Player target = h.makeMockPlayer(GameType.SURVIVAL);
            ItemStack sword = new ItemStack(ItemsTC.voidSword);
            server.setPvpAllowed(false);
            ItemsTC.voidSword.hurtEnemy(sword, target, attacker);
            h.assertFalse(target.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS), "player versus player is gated by server PvP");
            server.setPvpAllowed(true);
            ItemsTC.voidSword.hurtEnemy(sword, target, attacker);
            var effect = target.getEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
            h.assertTrue(effect != null && effect.getDuration() == 60 && effect.getAmplifier() == 0, "weakness exactly sixty ticks, level zero");
            server.setPvpAllowed(false);
            var pig = h.spawnWithNoFreeWill(EntityType.PIG, 1, 2, 1);
            ItemsTC.voidSword.hurtEnemy(sword, pig, attacker);
            h.assertTrue(pig.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS), "PvP off does not block player versus mob");
            target.removeEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
            ItemsTC.voidSword.hurtEnemy(sword, target, pig);
            h.assertTrue(target.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS), "non-player attacker bypasses PvP-only predicate");
        } finally {
            server.setPvpAllowed(oldPvp);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void basicBaublesUseActualCuriosSlotsAndEquippedDiscount(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        List<Item> items = baubles();
        String[] slots = {"necklace", "ring", "belt", "ring", "necklace", "ring", "belt"};
        for (int index = 0; index < items.size(); index++) {
            ItemBaubles item = (ItemBaubles) items.get(index);
            ItemStack stack = new ItemStack(item);
            h.assertTrue(item.legacyMeta() == index && stack.getMaxStackSize() == 1 && !stack.isDamageableItem(), "split variant retains original metadata and single stack");
            h.assertTrue(stack.getRarity() == (index >= 3 ? Rarity.UNCOMMON : Rarity.COMMON), "variant rarity");
            h.assertTrue(item.getVisDiscount(stack, player) == (index == 3 ? 5 : 0), "only apprentice ring contributes five percent");
            for (String slot : List.of("necklace", "ring", "belt")) {
                h.assertTrue(item.canEquip(new SlotContext(slot, player, 0, false, true), stack) == slot.equals(slots[index]), "slot cannot drift between split variants");
            }
        }
        ICuriosItemHandler inventory = curios(h, player);
        h.assertTrue(inventory.getStacksHandler("necklace").isPresent() && inventory.getStacksHandler("ring").orElseThrow().getSlots() == 2
            && inventory.getStacksHandler("belt").isPresent(), "real player capability has original necklace/two rings/belt slots");
        inventory.setEquippedCurio("ring", 0, new ItemStack(items.get(3)));
        inventory.setEquippedCurio("ring", 1, new ItemStack(items.get(3)));
        h.assertTrue(Math.abs(GearHelper.getGearVisDiscount(player) - 0.10F) < 0.0001F, "both equipped rings are counted by real Curios capability");
        player.getInventory().items.set(0, new ItemStack(items.get(3)));
        h.assertTrue(Math.abs(GearHelper.getGearVisDiscount(player) - 0.10F) < 0.0001F, "carried ring never contributes");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void gearWarpCountsOnlyOriginalEquipmentCategories(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().items.set(0, new ItemStack(ItemsTC.voidSword));
        player.getInventory().items.set(1, new ItemStack(ItemsTC.voidSword));
        player.getInventory().armor.set(0, new ItemStack(ItemsTC.voidBoots));
        player.getInventory().armor.set(1, new ItemStack(ItemsTC.voidLegs));
        player.getInventory().armor.set(2, new ItemStack(ItemsTC.voidChest));
        player.getInventory().armor.set(3, new ItemStack(ItemsTC.voidHelm));
        player.getInventory().offhand.set(0, new ItemStack(ItemsTC.voidSword));
        h.assertTrue(GearHelper.getWarpFromGear(player) == 5, "main hand plus armor; no carried or offhand contribution");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rechargeComponentCapsConsumptionAndPersistentNetworkRoundTrip(GameTestHelper h) {
        ItemStack stack = probe(h);
        h.assertTrue(RechargeHelper.getCharge(stack) == 0 && !stack.has(TCDataComponents.CHARGE), "fresh recharge target has implicit zero charge");
        h.assertFalse(RechargeHelper.consumeCharge(stack, null, 0), "missing charge tag retains original consume-zero behavior");
        h.assertTrue(RechargeHelper.rechargeItemBlindly(stack, null, 20) == 10 && RechargeHelper.getCharge(stack) == 10, "blind recharge caps at real item capacity");
        h.assertTrue(RechargeHelper.rechargeItemBlindly(stack, null, 1) == 0, "full item cannot overcharge");
        h.assertFalse(RechargeHelper.consumeCharge(stack, null, 11), "cannot consume more than stored");
        h.assertFalse(RechargeHelper.consumeCharge(stack, null, -1), "negative addon requests cannot mint charge");
        h.assertTrue(RechargeHelper.consumeCharge(stack, null, 3) && RechargeHelper.getCharge(stack) == 7, "real component consumption");
        h.assertTrue(Math.abs(RechargeHelper.getChargePercentage(stack, null) - 0.7F) < 0.0001F, "charge fraction follows capacity");
        ItemStack restored = ItemStack.parse(h.getLevel().registryAccess(), stack.save(h.getLevel().registryAccess())).orElseThrow();
        h.assertTrue(RechargeHelper.getCharge(restored) == 7, "persistent registered component survives item NBT roundtrip");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, stack);
            ItemStack synced = ItemStack.STREAM_CODEC.decode(buffer);
            h.assertTrue(RechargeHelper.getCharge(synced) == 7, "registered network component synchronizes charge");
        } finally {
            buffer.release();
        }
        h.assertTrue(RechargeHelper.getCharge(new ItemStack(ItemsTC.amuletVis)) == -1, "amulet itself is not a rechargeable item in TC6");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rechargeDrainsActualAuraAndRetainsIntegerTruncation(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        try (AuraFixture fixture = new AuraFixture(h, player)) {
            ItemStack stack = probe(h);
            fixture.aura.setVis(12.5F);
            h.assertTrue(RechargeHelper.rechargeItem(h.getLevel(), stack, fixture.pos, player, 2) == 2, "real AuraHelper drains two whole units");
            h.assertTrue(RechargeHelper.getCharge(stack) == 2 && fixture.aura.getVis() == 10.5F, "charge and real chunk aura both change");
            fixture.aura.setVis(0.75F);
            h.assertTrue(RechargeHelper.rechargeItem(h.getLevel(), stack, fixture.pos, player, 1) == 0, "TC6 truncates fractional drained aura");
            h.assertTrue(fixture.aura.getVis() == 0 && RechargeHelper.getCharge(stack) == 2, "fraction is actually drained, not credited");
            fixture.aura.setVis(5);
            h.assertFalse(AuraHelper.shouldPreserveAura(h.getLevel(), player, fixture.pos), "unported AURAPRESERVE research must not pretend player researched it");
            h.assertTrue(RechargeHelper.rechargeItem(h.getLevel(), stack, fixture.pos, player, 1) == 1, "low aura alone does not fabricate research gating");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void foundAmuletChargesHotbarThenCuriosThenArmorOneItemPerFortyTicks(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemAmuletVis amulet = (ItemAmuletVis) ItemsTC.amuletVis;
        ItemStack amuletStack = new ItemStack(amulet);
        h.assertTrue(amulet.rechargeInterval() == 40 && amuletStack.getRarity() == Rarity.UNCOMMON, "loot variant is the original found amulet");
        var context = new SlotContext("necklace", player, 0, false, true);
        var inventory = curios(h, player);
        inventory.setEquippedCurio("necklace", 0, amuletStack);
        ItemStack first = probe(h), second = probe(h), curio = probe(h), armor = probe(h), nonHotbar = probe(h);
        player.getInventory().items.set(0, first);
        player.getInventory().items.set(1, second);
        player.getInventory().items.set(9, nonHotbar);
        inventory.setEquippedCurio("ring", 0, curio);
        player.getInventory().armor.set(0, armor);
        try (AuraFixture fixture = new AuraFixture(h, player)) {
            fixture.aura.setVis(20);
            player.tickCount = 39;
            amulet.curioTick(context, amuletStack);
            h.assertTrue(RechargeHelper.getCharge(first) == 0 && fixture.aura.getVis() == 20, "off cadence consumes neither charge nor aura");
            player.tickCount = 40;
            amulet.curioTick(context, amuletStack);
            h.assertTrue(RechargeHelper.getCharge(first) == 1 && RechargeHelper.getCharge(second) == 0
                && RechargeHelper.getCharge(curio) == 0 && RechargeHelper.getCharge(armor) == 0, "first eligible hotbar target exclusively recharges");
            RechargeHelper.rechargeItemBlindly(first, player, 10);
            player.tickCount = 80;
            amulet.curioTick(context, amuletStack);
            h.assertTrue(RechargeHelper.getCharge(second) == 1, "full first target permits next hotbar target");
            RechargeHelper.rechargeItemBlindly(second, player, 10);
            player.tickCount = 120;
            amulet.curioTick(context, amuletStack);
            h.assertTrue(RechargeHelper.getCharge(curio) == 1 && RechargeHelper.getCharge(armor) == 0, "Curios inventory precedes armor");
            RechargeHelper.rechargeItemBlindly(curio, player, 10);
            player.tickCount = 160;
            amulet.curioTick(context, amuletStack);
            h.assertTrue(RechargeHelper.getCharge(armor) == 1 && RechargeHelper.getCharge(nonHotbar) == 0, "armor last, non-hotbar inventory excluded");
            h.assertTrue(fixture.aura.getVis() == 16, "one real aura unit for each of four eligible passes");
            amulet.curioTick(new SlotContext("necklace", player, 0, true, true), amuletStack);
            h.assertTrue(RechargeHelper.getCharge(armor) == 1 && fixture.aura.getVis() == 16, "cosmetic slot never charges");
        }
        h.succeed();
    }

    private static void assertArmor(GameTestHelper h, Item item, int durability, int defense, int enchantability, boolean warp) {
        ItemStack stack = new ItemStack(item);
        ArmorItem armor = (ArmorItem) item;
        h.assertTrue(stack.getMaxDamage() == durability && stack.getMaxStackSize() == 1, "original armor durability and stack size: " + BuiltInRegistries.ITEM.getKey(item));
        h.assertTrue(armor.getDefense() == defense && armor.getToughness() == 1.0F, "original armor defense/toughness");
        h.assertTrue(item.getEnchantmentValue(stack) == enchantability && stack.getRarity() == Rarity.UNCOMMON, "original armor enchantability/rarity");
        h.assertTrue(item.isValidRepairItem(stack, new ItemStack(warp ? ItemsTC.ingotVoid : ItemsTC.ingotThaumium)), "material repairs actual armor");
        h.assertTrue(!(item instanceof IWarpingGear) || ((IWarpingGear) item).getWarp(stack, null) == 1, "each warping armor contributes one");
        h.assertTrue((item instanceof IWarpingGear) == warp, "thaumium armor does not acquire void warp");
    }

    private static void assertSword(GameTestHelper h, Item item, int durability, int enchantability, double attackDamage) {
        ItemStack stack = new ItemStack(item);
        h.assertTrue(item instanceof SwordItem && stack.getMaxDamage() == durability && item.getEnchantmentValue(stack) == enchantability,
            "original sword durability/enchantability");
        var modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        h.assertTrue(modifiers.modifiers().stream().anyMatch(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE)
            && entry.modifier().amount() == attackDamage), "old sword attack attribute without player's base one");
        h.assertTrue(modifiers.modifiers().stream().anyMatch(entry -> entry.attribute().equals(Attributes.ATTACK_SPEED)
            && Math.abs(entry.modifier().amount() + 2.4D) < 0.0001D), "old sword speed minus2.4");
    }

    private static List<Item> baubles() {
        return List.of(ItemsTC.amuletMundane, ItemsTC.ringMundane, ItemsTC.girdleMundane, ItemsTC.ringApprentice,
            ItemsTC.amuletFancy, ItemsTC.ringFancy, ItemsTC.girdleFancy);
    }

    private static ICuriosItemHandler curios(GameTestHelper h, Player player) {
        var inventory = CuriosApi.getCuriosInventory(player);
        h.assertTrue(inventory.isPresent(), "registered Curios capability is available on real player type");
        return inventory.orElseThrow();
    }

    private static ItemStack probe(GameTestHelper h) {
        h.assertTrue(GearTestProbe.item != null && BuiltInRegistries.ITEM.containsKey(GearTestProbe.ID),
            "dedicated test-server fixture requires thaumcraft.gearTestProbe=true, never production registration");
        return new ItemStack(GearTestProbe.item);
    }

    private static final class AuraFixture implements AutoCloseable {
        final BlockPos pos;
        final AuraChunk aura;
        final short base;
        final float vis;
        final float flux;

        AuraFixture(GameTestHelper h, Player player) {
            pos = h.absolutePos(new BlockPos(1, 2, 1));
            player.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            var chunk = h.getLevel().getChunkAt(pos);
            aura = AuraHandler.getAuraChunk(h.getLevel(), chunk.getPos().x, chunk.getPos().z);
            h.assertTrue(aura != null && aura.isGenerated(), "actual loaded chunk aura, no simulated aura backend");
            base = aura.getBase();
            vis = aura.getVis();
            flux = aura.getFlux();
            aura.setBase((short) 100);
        }

        @Override
        public void close() {
            aura.setBase(base);
            aura.setVis(vis);
            aura.setFlux(flux);
        }
    }
}
