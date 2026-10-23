package thaumcraft.common.entities.monster.cult;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.CultEntities;
import thaumcraft.common.entities.ai.combat.AICultistHurtByTarget;
import thaumcraft.common.entities.ai.misc.AIAltarFocus;
import thaumcraft.common.entities.projectile.EntityGolemOrb;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.events.CultistDropEvents;
import thaumcraft.common.lib.research.theorycraft.SourceTheorycraftCard;
import thaumcraft.common.items.curios.ItemCurio;

/** Real registered entities, generated equipment, native loot and source AI/projectile
 * behavior. Explicit GameTest spawns are not a claim of ordinary survival acquisition,
 * client rendering or TCP acceptance. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class CultistGameTests {
    private static Vec3 position(GameTestHelper helper) { return Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(2, 100, 2))); }
    private static long seedFor(boolean above, float threshold) {
        for (long seed = 1; seed < 65536; seed++) if ((new Random(seed).nextFloat() > threshold) == above) return seed;
        throw new IllegalStateException("No source branch seed");
    }
    /** Real PlayerList login and event callbacks with NeoForge's explicit compatible-client
     * GameTest fixture. Vanilla's deprecated helper omits payload setup before datapack sync.
     * This is an embedded channel, NOT proof of remote TCP negotiation; sends/checkPacket
     * are unmodified and no production sender or login callback is intercepted. */
    private static ServerPlayer compatibleMockPlayer(GameTestHelper helper) {
        if (!GameTestHooks.isGametestServer()) throw new IllegalStateException("Cultist player fixture requires GameTest server");
        var profile = new GameProfile(UUID.randomUUID(), "TC_Cult_Player");
        var information = ClientInformation.createDefault();
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, information);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.assertTrue(NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, ResourceLocation.parse("thaumcraft:sync_object_tags")), "mock login uses actual registered modded channels before datapack-sync callback");
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, new CommonListenerCookie(profile, 0, information, false, ConnectionType.NEOFORGE));
        return player;
    }
    private static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        PlayerDataSync.forget(player);
        try { helper.getLevel().getServer().getPlayerList().remove(player); }
        finally { ((EmbeddedChannel)player.connection.getConnection().channel()).finishAndReleaseAll(); }
    }
    @GameTest(template = "scan_test")
    public static void portalRetainsTrueSourceAttributesStationaryMovementPersistenceAndMinions(GameTestHelper helper) {
        var portal = CultEntities.PORTAL.create(helper.getLevel()); Vec3 pos = position(helper); portal.setPos(pos);
        helper.assertTrue(portal.getMaxHealth() == 100 && portal.getArmorValue() == 4 && portal.getAttributeValue(Attributes.ATTACK_DAMAGE) == 0
            && portal.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) == 1 && portal.fireImmune(), "real source100HP/4armor/zeroattack/fireimmuneportal");
        portal.move(MoverType.SELF, new Vec3(3, -8, 5)); helper.assertTrue(portal.position().equals(pos), "source empty move prevents walking/falling");
        helper.assertFalse(portal.isPushable() || portal.removeWhenFarAway(1_000_000), "source portal does not push/despawn");
        helper.assertFalse(portal.addEffect(new MobEffectInstance(MobEffects.POISON, 100), null), "source portal rejects potions");
        portal.setActive(true); portal.activeCounter = 18; portal.pulse = 4; var saved = new CompoundTag(); portal.addAdditionalSaveData(saved);
        var restored = CultEntities.PORTAL.create(helper.getLevel()); restored.readAdditionalSaveData(saved);
        helper.assertTrue(restored.isActive() && restored.activeCounter == 0 && restored.pulse == 0, "source stores active only, not transient animation counters");
        for (boolean knight : new boolean[]{true, false}) {
            long seed = seedFor(knight, .33F); var expected = new Random(seed); expected.nextFloat();
            Vec3 expectedPosition = pos.add(expected.nextFloat() - expected.nextFloat(), .25, expected.nextFloat() - expected.nextFloat()); int damage = 5 + expected.nextInt(5);
            portal.setHealth(100); portal.invulnerableTime = 0; portal.getRandom().setSeed(seed); EntityCultist minion = portal.spawnMinions();
            helper.assertTrue((minion instanceof EntityCultistKnight) == knight && minion.getType() == (knight ? CultEntities.KNIGHT : CultEntities.CLERIC), "genuine source knight/cleric spawn branch");
            helper.assertTrue(minion.position().distanceToSqr(expectedPosition) < 1e-10 && portal.getHealth() == 100 - damage, "sourcefloat offsets,Y+.25,selfdamage5..9");
            helper.assertTrue(minion.getItemBySlot(EquipmentSlot.CHEST).is(knight ? ItemsTC.crimsonPlateChest : ItemsTC.crimsonRobeChest)
                && minion.getItemBySlot(EquipmentSlot.LEGS).is(knight ? ItemsTC.crimsonPlateLegs : ItemsTC.crimsonRobeLegs), "source real crimson equipment, no vanilla substitute");
            helper.assertTrue(helper.getLevel().getEntity(minion.getId()) == minion, "minion added to real server world"); minion.discard();
        }
        portal.discard(); restored.discard(); helper.succeed();
    }
    @GameTest(template = "scan_test")
    public static void portalActuallyActivatesAndSourceScheduledSpawnObeysDifficultyCap(GameTestHelper helper) {
        ServerPlayer player = compatibleMockPlayer(helper); var portal = CultEntities.PORTAL.create(helper.getLevel()); Vec3 pos = position(helper);
        player.setPos(pos.add(2, 0, 0)); portal.setPos(pos); helper.getLevel().addFreshEntity(portal);
        var spawned = new ArrayList<EntityCultist>();
        try {
            for (int tick = 0; tick < 9; tick++) helper.getLevel().tickNonPassenger(portal); helper.assertFalse(portal.isActive(), "not active before source10tick proximity test");
            helper.getLevel().tickNonPassenger(portal); helper.assertTrue(portal.isActive() && portal.activeCounter == 0, "actual nearby player triggers source activation");
            for (int tick = 0; tick < 100; tick++) helper.getLevel().tickNonPassenger(portal);
            AABB bounds = portal.getBoundingBox().inflate(32);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(EntityCultist.class, bounds).isEmpty(), "sourcepostdecrement waits101activeticks forfirstspawn");
            helper.getLevel().tickNonPassenger(portal); spawned.addAll(helper.getLevel().getEntitiesOfClass(EntityCultist.class, bounds));
            helper.assertTrue(spawned.size() == 1, "actual scheduled tick creates one real cultist");
            int maximum = helper.getLevel().getDifficulty() == Difficulty.HARD ? 6 : helper.getLevel().getDifficulty() == Difficulty.NORMAL ? 4 : 2;
            for (int tick = 0; tick < 900; tick++) helper.getLevel().tickNonPassenger(portal);
            var capped = helper.getLevel().getEntitiesOfClass(EntityCultist.class, bounds); spawned.clear(); spawned.addAll(capped);
            helper.assertTrue(capped.size() == maximum && portal.getHealth() > 0, "source32cube cap exactdifficulty6/4/2,no fake zombie replacement");
            int health = (int)portal.getHealth(); for (int tick = 0; tick < 200; tick++) helper.getLevel().tickNonPassenger(portal);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(EntityCultist.class, bounds).size() == maximum && (int)portal.getHealth() == health, "cap prevents further spawns/selfdamage");
        } finally { for (EntityCultist cultist : spawned) cultist.discard(); portal.discard(); removePlayer(helper, player); }
        helper.succeed();
    }
    @GameTest(template = "scan_test")
    public static void actualCultistHomeRitualAllegianceAndCrossClassRetaliationAreSourceBound(GameTestHelper helper) {
        var knight = CultEntities.KNIGHT.create(helper.getLevel()); var cleric = CultEntities.CLERIC.create(helper.getLevel()); var target = EntityType.ZOMBIE.create(helper.getLevel());
        Vec3 pos = position(helper); knight.setPos(pos); cleric.setPos(pos.add(2, 0, 0)); target.setPos(pos.add(4, 0, 0));
        knight.setNoAi(true); cleric.setNoAi(true); target.setNoAi(true); helper.getLevel().addFreshEntity(knight); helper.getLevel().addFreshEntity(cleric); helper.getLevel().addFreshEntity(target);
        try {
            helper.assertTrue(knight.getMaxHealth() == 30 && cleric.getMaxHealth() == 24 && knight.getAttributeValue(Attributes.FOLLOW_RANGE) == 32
                && knight.getAttributeValue(Attributes.ATTACK_DAMAGE) == 4, "real sourceknight30HP/cleric24HP/range32/attack4");
            helper.assertTrue(knight.isAlliedTo(cleric) && !knight.canAttackType(CultEntities.CLERIC) && !cleric.canAttackType(CultEntities.KNIGHT), "both subclasses share source allegiance");
            knight.tickCount = 1; knight.setLastHurtByMob(target); var retaliate = new AICultistHurtByTarget(knight, true);
            helper.assertTrue(retaliate.canUse(), "actual revenge timestamp activates source goal"); retaliate.start();
            helper.assertTrue(knight.getTarget() == target && cleric.getTarget() == target, "source helper alerts BOTHcultists,notmodernsame-concrete-type only");
            BlockPos home = helper.absolutePos(new BlockPos(2, 100, 2)); cleric.restrictTo(home, 8); cleric.setIsRitualist(true); var nbt = new CompoundTag(); cleric.addAdditionalSaveData(nbt);
            var restored = CultEntities.CLERIC.create(helper.getLevel()); restored.readAdditionalSaveData(nbt);
            helper.assertTrue(restored.getIsRitualist() && restored.getRestrictCenter().equals(home) && restored.getRestrictRadius() == 8 && !restored.removeWhenFarAway(99999), "actual sourceHomeXYZD and ritualist NBT");
            restored.setPos(Vec3.atLowerCornerOf(home)); restored.tickCount = 40; new AIAltarFocus(restored).tick();
            helper.assertFalse(restored.getIsRitualist(), "absent real eldritch altar ends ritual,not dummy altar success");
            cleric.setIsRitualist(true); cleric.hurt(helper.getLevel().damageSources().mobAttack(target), 1); helper.assertFalse(cleric.getIsRitualist(), "real incoming hurt cancels ritual before normal damage");
            restored.discard();
        } finally { knight.discard(); cleric.discard(); target.discard(); }
        helper.succeed();
    }
    @GameTest(template = "scan_test")
    public static void genuineClericRangedAttackProducesRedHomingOrbOrThreeSmallFireballs(GameTestHelper helper) {
        var cleric = CultEntities.CLERIC.create(helper.getLevel()); var target = EntityType.COW.create(helper.getLevel()); Vec3 pos = position(helper);
        cleric.setPos(pos); target.setPos(pos.add(10, 0, 0)); target.setDeltaMovement(0, 0, .1); helper.getLevel().addFreshEntity(target);
        AABB bounds = new AABB(pos.add(-4, -4, -4), pos.add(20, 8, 8));
        try {
            cleric.getRandom().setSeed(seedFor(true, .66F)); cleric.performRangedAttack(target, 1);
            var orbs = helper.getLevel().getEntitiesOfClass(EntityGolemOrb.class, bounds);
            helper.assertTrue(orbs.size() == 1 && orbs.getFirst().isRed() && orbs.getFirst().homingTarget() == target && orbs.getFirst().getOwner() == cleric, "sourceorb branch realowner/target/redspawn metadata");
            Vec3 ahead = target.position().add(target.getDeltaMovement().scale(10)).subtract(pos).normalize();
            helper.assertTrue(orbs.getFirst().position().distanceToSqr(new Vec3(pos.x, cleric.getEyeY() - .1F, pos.z).add(ahead)) < 1e-10, "source10tick target extrapolation changes spawn offset");
            orbs.forEach(EntityGolemOrb::discard); cleric.getRandom().setSeed(seedFor(false, .66F)); cleric.performRangedAttack(target, 1);
            var fireballs = helper.getLevel().getEntitiesOfClass(SmallFireball.class, bounds);
            helper.assertTrue(fireballs.size() == 3 && fireballs.stream().allMatch(f -> f.getOwner() == cleric && f.getY() == pos.y + cleric.getBbHeight() / 2F + .5), "sourcefireballbranch THREErealvanillafireballs withsourceY");
            fireballs.forEach(SmallFireball::discard);
        } finally { target.discard(); cleric.discard(); }
        helper.succeed();
    }
    @GameTest(template = "scan_test")
    public static void golemOrbHasSourceGaussianSpreadDamageHomingLifeAndDeflectionQuirk(GameTestHelper helper) {
        var shooter = CultEntities.KNIGHT.create(helper.getLevel()); var target = EntityType.COW.create(helper.getLevel()); Vec3 pos = position(helper);
        shooter.setPos(pos); target.setPos(pos.add(10, 0, 0)); helper.getLevel().addFreshEntity(target);
        try {
            for (boolean red : new boolean[]{false, true}) {
                var orb = new EntityGolemOrb(helper.getLevel(), shooter, target, red); long seed = 72553L; orb.getRandom().setSeed(seed);
                Random expected = new Random(seed); double x = 1.3, y = .2, z = -.6; float length = (float)Math.sqrt(x*x+y*y+z*z), speed = .66F, spread = 3;
                Vec3 movement = new Vec3(x/length + expected.nextGaussian()*.007499999832361937D*spread, y/length + expected.nextGaussian()*.007499999832361937D*spread, z/length + expected.nextGaussian()*.007499999832361937D*spread).scale(speed);
                orb.shoot(x, y, z, speed, spread); helper.assertTrue(orb.getDeltaMovement().distanceToSqr(movement) < 1e-20 && orb.getRandom().nextLong() == expected.nextLong(), "literal sourceGaussian (notmoderntriangle) launchRNG");
                target.invulnerableTime = 0; target.setHealth(10); orb.onHit(new EntityHitResult(target));
                helper.assertTrue(Math.abs(target.getHealth() - (red ? 6 : 7.6F)) < .00001 && orb.isRemoved(), "sourceactualmagic4×red1.0/blue.6 anddiscardonimpact");
                var lifetime = new EntityGolemOrb(helper.getLevel(), shooter, target, red); lifetime.setPos(pos.add(0, 10, 0)); lifetime.setDeltaMovement(Vec3.ZERO);
                int limit = red ? 240 : 160; lifetime.tickCount = limit - 1;
                helper.getLevel().tickNonPassenger(lifetime);
                helper.assertTrue(lifetime.tickCount == limit && !lifetime.isRemoved(), "source strict-greater lifetime retains orb on exact240/160 tick");
                helper.getLevel().tickNonPassenger(lifetime);
                helper.assertTrue(lifetime.tickCount == limit + 1 && lifetime.isRemoved(), "source expires only AFTER240/160 using real outer ticker increment");
                var homing = new EntityGolemOrb(helper.getLevel(), shooter, target, red); homing.setPos(pos.add(0, 10, 0)); homing.setDeltaMovement(.8, -.8, .8); helper.getLevel().tickNonPassenger(homing);
                helper.assertTrue(Math.abs(homing.getDeltaMovement().x) <= .25 && Math.abs(homing.getDeltaMovement().y) <= .25 && Math.abs(homing.getDeltaMovement().z) <= .25, "sourcehomingfloat clampsallaxes.25 afternativefreeflight");
                var deflect = new EntityGolemOrb(helper.getLevel(), shooter, target, red); target.setYRot(90); target.setXRot(10);
                helper.assertTrue(deflect.hurt(helper.getLevel().damageSources().mobAttack(target), 1) && deflect.getDeltaMovement().distanceToSqr(target.getLookAngle().scale(.9)) < 1e-20 && deflect.getOwner() == shooter, "sourcelookvector×.9deflectiondoesnotchangeowner");
                helper.assertFalse(deflect.isPickable(), "sourcequirk: hurtmethodexists butentityisnotpickablebydefault");
                var nbt = new CompoundTag(); deflect.saveWithoutId(nbt); var restored = CultEntities.GOLEM_ORB.create(helper.getLevel()); restored.load(nbt);
                helper.assertTrue(!restored.isRed() && restored.homingTarget() == null, "sourceNBTdoesnotpersisttransientred/target");
                homing.discard(); deflect.discard(); restored.discard();
            }
        } finally { shooter.discard(); target.discard(); }
        helper.succeed();
    }
    @GameTest(template = "scan_test")
    public static void realCrimsonRitesDropEventUsesSourceFourTwentyFiftyAndExcludesFakePlayer(GameTestHelper helper) {
        ServerPlayer player = compatibleMockPlayer(helper); var cultist = CultEntities.KNIGHT.create(helper.getLevel()); cultist.setPos(position(helper));
        try {
            var knowledge = ThaumcraftCapabilities.getKnowledge(player); knowledge.clear(); player.getInventory().clearContent();
            helper.assertTrue(CultistDropEvents.denominator(player) == 4, "sourceunknownCultistStage2 chance1/4");
            knowledge.addResearch("CrimsonCultist"); knowledge.setResearchStage("CrimsonCultist", 3);
            helper.assertTrue(CultistDropEvents.denominator(player) == 4, "sourcequirk: literal !CrimsonCultist is not ordinary CrimsonCultist");
            knowledge.addResearch("!CrimsonCultist"); knowledge.setResearchStage("!CrimsonCultist", 3);
            helper.assertTrue(CultistDropEvents.denominator(player) == 20, "source literal qualified !CrimsonCultist@2 chance1/20");
            var rites = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("thaumcraft:curio_rites")));
            player.getInventory().setItem(9, rites); helper.assertTrue(CultistDropEvents.denominator(player) == 50, "sourceexactritesinventorypresence chance1/50");
            var mark = new CompoundTag(); mark.putString("marker", "not exact NBT"); rites.set(DataComponents.CUSTOM_DATA, CustomData.of(mark));
            helper.assertTrue(CultistDropEvents.denominator(player) == 20, "markedritesdoesnotmatchsourceexactemptyNBT");
            player.getInventory().clearContent(); int found = 0;
            for (int index = 0; index < 1024; index++) {
                var drops = new ArrayList<ItemEntity>(); NeoForge.EVENT_BUS.post(new LivingDropsEvent(cultist, helper.getLevel().damageSources().playerAttack(player), drops, true));
                helper.assertTrue(drops.size() <= 1, "sourceextraeventaddszerorone");
                for (var item : drops) { helper.assertTrue("rites".equals(SourceTheorycraftCard.curioType(item.getItem())) && item.getItem().getCount() == 1 && item.getY() == cultist.getEyeY(), "actualregisteredritesatcultisteyeY"); found++; }
            }
            helper.assertTrue(found > 0, "genuinerealplayereventproducesrites");
            var fake = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TC_Cult_Fake")); var drops = new ArrayList<ItemEntity>();
            for (int index = 0; index < 100; index++) NeoForge.EVENT_BUS.post(new LivingDropsEvent(cultist, helper.getLevel().damageSources().playerAttack(fake), drops, true));
            helper.assertTrue(drops.isEmpty(), "sourceFakePlayerexplicitlyexcluded"); PlayerDataSync.forget(fake);
        } finally { cultist.discard(); removePlayer(helper, player); }
        helper.succeed();
    }
    @GameTest(template = "scan_test")
    public static void genuineCultistLootTableHasSourceGoldAndRitesWithRealLootingEnchantment(GameTestHelper helper) {
        ServerPlayer player = compatibleMockPlayer(helper); var cultist = CultEntities.KNIGHT.create(helper.getLevel()); cultist.setPos(position(helper));
        try {
            var sword = new ItemStack(Items.IRON_SWORD); sword.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING), 3); player.setItemSlot(EquipmentSlot.MAINHAND, sword);
            LootTable table = helper.getLevel().getServer().reloadableRegistries().getLootTable(EntityCultist.LOOT); helper.assertTrue(table != LootTable.EMPTY, "actualcultistregisteredresourceloaded");
            LootParams params = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.THIS_ENTITY, cultist).withParameter(LootContextParams.ORIGIN, cultist.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, helper.getLevel().damageSources().playerAttack(player)).withParameter(LootContextParams.ATTACKING_ENTITY, player)
                .withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, player).withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player).create(LootContextParamSets.ENTITY);
            int rites = 0, gold = 0, zeroGoldRolls = 0, maximumGoldRolls = 0;
            for (int index = 0; index < 2048; index++) for (ItemStack item : table.getRandomItems(params, 72133L + index)) {
                // Source main pool explicitly permits count0. MC1.21.1 createStackSplitter
                // forwards count0 to getRandomItems; ItemStack.getItem then returns AIR and
                // curioType correctly returns null. This is no emitted drop, not extra rites.
                if (item.isEmpty()) { helper.assertTrue(item.getCount() == 0, "only the source zero-count roll may be an empty native output"); zeroGoldRolls++; }
                else if (item.is(Items.GOLD_NUGGET)) {
                    helper.assertTrue(item.getCount() >= 1 && item.getCount() <= 5, "sourcecount0..2plusrealLooting3max5"); gold++;
                    if (item.getCount() == 5) maximumGoldRolls++;
                } else {
                    helper.assertTrue("rites".equals(SourceTheorycraftCard.curioType(item)) && item.getItem() instanceof ItemCurio curio && curio.metadata() == 6
                        && item.getCount() == 1 && item.getComponentsPatch().entrySet().isEmpty(), "source special pool is one exact flattened meta6 rites, got " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()) + " x" + item.getCount()); rites++;
                }
            }
            helper.assertTrue(gold > 0 && rites > 0 && zeroGoldRolls > 0 && maximumGoldRolls > 0, "native source pools produce gold/real meta6 rites, genuine zero rolls and Looting3 upper-bound5");
        } finally { cultist.discard(); removePlayer(helper, player); }
        helper.succeed();
    }
}
