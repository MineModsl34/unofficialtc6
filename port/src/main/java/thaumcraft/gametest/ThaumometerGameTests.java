package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.items.tools.ItemThaumometer;
import thaumcraft.common.lib.network.misc.PacketAuraToClient;
import thaumcraft.common.lib.utils.ScanTargeting;
import thaumcraft.common.world.aura.AuraHandler;

/** Actual world rays and inventory callbacks, not a claim of real-client HUD acceptance. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class ThaumometerGameTests {
    private static ServerPlayer player(GameTestHelper h, String name) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos position = h.absolutePos(new BlockPos(2, 2, 1));
        player.setPos(position.getX() + .5D, position.getY(), position.getZ() + .5D);
        player.setXRot(0);
        player.setYRot(0);
        return player;
    }

    @GameTest(template = "scan_test")
    public static void realEntityPriorityNineAndWallEyeLineOfSight(GameTestHelper h) {
        ServerPlayer player = player(h, "TC_Scan_Ray");
        var entity = h.spawn(EntityType.ARMOR_STAND, new BlockPos(2, 2, 7));
        entity.setNoGravity(true);
        h.assertTrue(ScanTargeting.scanTarget(player) == entity, "entity beyond five-block block reach is picked by nine-block entity ray");
        for (int y = 2; y <= 4; y++) h.setBlock(2, y, 4, Blocks.STONE);
        h.assertTrue(ScanTargeting.pointedEntity(h.getLevel(), player, 1, 9, 0, true) == null,
            "solid wall blocks candidate eye visibility");
        h.assertTrue(h.absolutePos(new BlockPos(2, 3, 4)).equals(ScanTargeting.scanTarget(player)),
            "blocked entity falls back to the authoritative wall block");
        h.succeed();
    }

    @GameTest(template = "scan_test")
    public static void realSourceOnlyFluidRaySkipsFlowingWaterAndHasLegacyFiveReach(GameTestHelper h) {
        ServerPlayer player = player(h, "TC_Scan_Fluid");
        h.setBlock(2, 3, 3, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1));
        h.setBlock(2, 3, 5, Blocks.WATER);
        var hit = ScanTargeting.legacyBlockHit(player);
        h.assertTrue(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(h.absolutePos(new BlockPos(2, 3, 5))),
            "source liquid is selected, nearer flowing liquid is ignored");
        h.setBlock(2, 3, 5, Blocks.AIR);
        h.setBlock(2, 3, 7, Blocks.STONE);
        h.assertTrue(ScanTargeting.legacyBlockHit(player).getType() == HitResult.Type.MISS,
            "six-block block has no invented modern creative reach extension");
        h.succeed();
    }

    @GameTest(template = "scan_test", skyAccess = true)
    public static void realRayMissReturnsNullForSkyScan(GameTestHelper h) {
        ServerPlayer player = player(h, "TC_Scan_Sky");
        player.setXRot(-90);
        h.assertTrue(ScanTargeting.scanTarget(player) == null, "empty sky passes null to the source scanning dispatcher");
        h.succeed();
    }

    @GameTest(template = "scan_test")
    public static void realInventoryTicksPreserveLocalSlotZeroOffhandAndOwnerAuraCadence(GameTestHelper h) throws Exception {
        ServerPlayer owner = player(h, "TC_Scan_Aura");
        h.assertTrue(ItemsTC.thaumometer instanceof ItemThaumometer, "actual registered tool uses thaumometer behavior");
        ItemStack item = new ItemStack(ItemsTC.thaumometer);
        h.assertTrue(item.getMaxStackSize() == 1 && item.getRarity() == Rarity.UNCOMMON, "source stack size and rarity");
        owner.getInventory().selected = 4;
        owner.getInventory().items.set(0, item);
        owner.getInventory().items.set(1, item.copy()); // neither selected nor local0
        owner.getInventory().items.set(4, item.copy());
        owner.getInventory().armor.set(0, item.copy()); // original armor compartment local0 = boots
        owner.getInventory().offhand.set(0, item.copy());
        var position = owner.blockPosition();
        h.getLevel().getChunkAt(position); // Fixture loads its existing test chunk, never item code.
        var aura = AuraHandler.getAuraChunk(h.getLevel(), position.getX() >> 4, position.getZ() >> 4);
        h.assertTrue(aura != null, "real loaded test chunk has aura cache");
        short originalBase = aura.getBase();
        float originalVis = aura.getVis(), originalFlux = aura.getFlux();
        record Delivery(ServerPlayer owner, PacketAuraToClient packet) {}
        List<Delivery> delivered = new ArrayList<>();
        try (var capture = ItemThaumometer.captureGameTestAuraPackets((player, packet) -> delivered.add(new Delivery(player, packet)))) {
            aura.setBase((short) 101);
            aura.setVis(70.5F);
            aura.setFlux(2.25F);
            owner.tickCount = 19;
            owner.getInventory().tick();
            h.assertTrue(delivered.isEmpty(), "no aura packets off cadence");
            owner.tickCount = 20;
            owner.getInventory().tick();
            h.assertTrue(delivered.size() == 4, "selected/items0/armor0/offhand0 send; unselected items1 does not");
            for (Delivery delivery : delivered) {
                h.assertTrue(delivery.owner() == owner && delivery.packet().equals(new PacketAuraToClient((short) 101, 70.5F, 2.25F)),
                    "exact immutable snapshot is addressed only to the inventory owner");
            }
            h.assertFalse(owner.getCooldowns().isOnCooldown(ItemsTC.thaumometer), "source tool does not introduce a cooldown");
            // Intentional command-only fixture: a tool is not normally wearable. The original
            // InventoryPlayer compared selected hotbar1 against armor's LOCAL slot1 as well.
            owner.getInventory().clearContent();
            owner.getInventory().selected = 1;
            owner.getInventory().armor.set(1, item.copy());
            delivered.clear();
            owner.getInventory().tick();
            h.assertTrue(delivered.size() == 1 && delivered.getFirst().owner() == owner,
                "command-installed armor local1 retains selected-hotbar1 aura callback despite modern global37 flag=false");
            owner.getInventory().selected = 2;
            delivered.clear();
            owner.getInventory().tick();
            h.assertTrue(delivered.isEmpty(), "armor local1 is not selected when current hotbar is2");
        } finally {
            aura.setBase(originalBase);
            aura.setVis(originalVis);
            aura.setFlux(originalFlux);
        }
        h.succeed();
    }

    @GameTest(template = "scan_test")
    public static void absentAuraCacheDoesNotLoadFarChunkOrSend(GameTestHelper h) throws Exception {
        ServerPlayer owner = player(h, "TC_Scan_NoAura");
        // No world player registration or chunk request; only this detached fake player's position changes.
        owner.setPos(1600000.5D, 100, 1600000.5D);
        int chunkX = owner.blockPosition().getX() >> 4, chunkZ = owner.blockPosition().getZ() >> 4;
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(chunkX, chunkZ) == null, "far chunk initially unloaded");
        h.assertTrue(AuraHandler.getAuraChunk(h.getLevel(), chunkX, chunkZ) == null, "far aura initially absent");
        List<PacketAuraToClient> delivered = new ArrayList<>();
        try (var capture = ItemThaumometer.captureGameTestAuraPackets((player, packet) -> delivered.add(packet))) {
            owner.tickCount = 20;
            ((ItemThaumometer) ItemsTC.thaumometer).inventoryTick(new ItemStack(ItemsTC.thaumometer), h.getLevel(), owner, 40, false);
            h.assertTrue(delivered.isEmpty(), "absent aura sends nothing");
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(chunkX, chunkZ) == null
                && AuraHandler.getAuraChunk(h.getLevel(), chunkX, chunkZ) == null, "item created neither a chunk nor an aura");
        }
        h.succeed();
    }

    @GameTest(template = "scan_test")
    public static void sourceSlotAndFluxThresholdBoundariesRemainExact(GameTestHelper h) {
        for (int hotbar = 0; hotbar < 9; hotbar++) {
            for (int slot = 0; slot <= 40; slot++) {
                boolean expected = slot == 0 || slot == 36 || slot == 40 || slot == hotbar
                    || slot >= 36 && slot < 40 && slot - 36 == hotbar;
                h.assertTrue(ItemThaumometer.isLegacyHeldSlot(slot, false, hotbar) == expected,
                    "source local0 and compartment-local selected index both preserved");
                h.assertTrue(ItemThaumometer.isLegacyHeldSlot(slot, true, hotbar), "selected flag also permits original callback");
            }
        }
        h.assertFalse(ItemThaumometer.exceedsFluxWarning((short) 101, 100F, 33F), "equal integer base/3 is not over threshold");
        h.assertTrue(ItemThaumometer.exceedsFluxWarning((short) 101, 100F, 33.1F), "integer division must not become 33.666 floating division");
        h.assertFalse(ItemThaumometer.exceedsFluxWarning((short) 300, 10F, 10F), "equal vis is not over threshold");
        h.assertTrue(ItemThaumometer.exceedsFluxWarning((short) 300, 10F, 10.1F), "strictly greater vis warns");
        h.succeed();
    }
}
