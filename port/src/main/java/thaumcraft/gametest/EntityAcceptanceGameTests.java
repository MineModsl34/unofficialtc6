package thaumcraft.gametest;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.entities.Mob8NaturalRegistries;
import thaumcraft.common.entities.construct.*;
import thaumcraft.common.entities.monster.*;

/** All registered entity persistence, plus actual damage/ammunition boundaries. */
@GameTestHolder(Thaumcraft.MODID)
public final class EntityAcceptanceGameTests {
    @GameTestGenerator public static Collection<TestFunction> remainingEntities() {
        var tests = new ArrayList<TestFunction>();
        for (var id : BuiltInRegistries.ENTITY_TYPE.keySet()) if (id.getNamespace().equals(Thaumcraft.MODID)) {
            var type = BuiltInRegistries.ENTITY_TYPE.get(id);
            tests.add(new TestFunction("bulk_entity_persistence", "thaumcraft.bulk_entity_save_" + id.getPath(), "thaumcraft:scan_test", 100, 0, true, h -> persistence(h,type)));
        }
        tests.add(test("wisp_damage", EntityAcceptanceGameTests::wisp));
        tests.add(test("wisp_ai", EntityAcceptanceGameTests::wispAi));
        tests.add(test("turret_basic", h -> turret(h,false))); tests.add(test("turret_advanced", h -> turret(h,true)));
        tests.add(test("bore_tool", EntityAcceptanceGameTests::bore));
        tests.add(test("boss_golem_phase",EntityAcceptanceGameTests::golemBoss));
        tests.add(test("boss_warden_frenzy",EntityAcceptanceGameTests::wardenBoss));
        tests.add(test("boss_leader_support",EntityAcceptanceGameTests::leaderBoss));
        return tests;
    }
    private static TestFunction test(String key, java.util.function.Consumer<GameTestHelper> body) { return new TestFunction("bulk_entity_actions", "thaumcraft.bulk_entity_" + key, "thaumcraft:scan_test", 240, 0, true, body); }
    private static void persistence(GameTestHelper h, EntityType<?> type) {
        var entity = type.create(h.getLevel()); h.assertTrue(entity != null, "native registered type constructs");
        entity.setPos(h.absolutePos(new BlockPos(5,2,5)).getCenter()); entity.setCustomName(Component.literal("native persistence"));
        if (entity instanceof LivingEntity living) { h.assertTrue(Float.isFinite(living.getMaxHealth()) && living.getMaxHealth()>0, "registered native attributes exist"); living.setHealth(living.getMaxHealth()*.75F); living.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.STICK,2)); }
        if (entity instanceof EntityOwnedConstruct owned) { owned.setOwnerId(UUID.randomUUID()); owned.setOwned(true); owned.setValidSpawn(); }
        if (entity instanceof EntityWisp wisp) wisp.setWispType(Aspect.AIR.getTag());
        if (entity instanceof EntityPech pech) { pech.setPechType(2); pech.setAnger(17); pech.setTamed(true); pech.loot.set(0,new ItemStack(Items.GOLD_INGOT,3)); }
        var data = entity.saveWithoutId(new CompoundTag()); var restored = type.create(h.getLevel()); h.assertTrue(restored != null,"native restore type exists"); restored.load(data);
        h.assertTrue(restored.getUUID().equals(entity.getUUID()) && restored.position().distanceToSqr(entity.position())<.00001 && Objects.equals(restored.getCustomName(),entity.getCustomName()), "native entity UUID, position and name survive NBT");
        if (entity instanceof LivingEntity living && restored instanceof LivingEntity copy) h.assertTrue(copy.getHealth()==living.getHealth() && ItemStack.matches(copy.getMainHandItem(),living.getMainHandItem()), "native health and component-bearing equipment survive NBT");
        if (entity instanceof EntityOwnedConstruct owned && restored instanceof EntityOwnedConstruct copy) h.assertTrue(copy.isOwned() && Objects.equals(copy.getOwnerId(),owned.getOwnerId()), "construct native ownership survives NBT");
        if (restored instanceof EntityWisp wisp) h.assertTrue(wisp.getWispType().equals(Aspect.AIR.getTag()), "source wisp aspect survives native save");
        if (restored instanceof EntityPech pech) h.assertTrue(pech.getPechType()==2 && pech.getAnger()==17 && pech.isTamed() && pech.loot.get(0).getCount()==3, "source pech type/anger/taming/loot survives native save");
        entity.discard(); restored.discard(); h.succeed();
    }
    private static void wisp(GameTestHelper h) {
        var wisp = Mob8NaturalRegistries.WISP.create(h.getLevel()); var attacker = h.spawnWithNoFreeWill(EntityType.COW,3,2,5); h.assertTrue(wisp != null,"registered wisp"); wisp.setPos(attacker.position().add(2,0,0));
        h.assertTrue(wisp.hurt(h.getLevel().damageSources().mobAttack(attacker),2) && wisp.getTarget()==attacker && wisp.aggroCooldown==200 && wisp.getHealth()==20, "actual source damage registers retaliation target, cooldown and health loss"); attacker.discard(); wisp.discard(); h.succeed();
    }
    private static void wispAi(GameTestHelper h) {
        BulkGameplayFixtures.floor(h); var wisp = h.spawn(Mob8NaturalRegistries.WISP,5,3,5); wisp.setWispType(Aspect.AIR.getTag()); var target = h.spawnWithNoFreeWill(EntityType.COW,7,2,5); target.setNoGravity(true); wisp.setTarget(target); wisp.aggroCooldown=1000; wisp.attackCounter=19;
        h.succeedWhen(() -> { h.assertTrue(target.getHealth()<target.getMaxHealth(),"actual native wisp AI damages its stationary target"); h.assertTrue(wisp.getWispType().equals(Aspect.AIR.getTag()),"native aspect remains valid during AI"); wisp.discard(); target.discard(); });
    }
    private static void turret(GameTestHelper h, boolean advanced) {
        BulkGameplayFixtures.floor(h); var type = advanced ? Mob8ConstructRegistries.ADVANCED : Mob8ConstructRegistries.BASIC; var turret = type.create(h.getLevel()); h.assertTrue(turret!=null,"registered native turret"); turret.setPos(h.absolutePos(new BlockPos(3,2,5)).getCenter()); turret.setValidSpawn(); turret.setNoAi(true); turret.setOwnerId(UUID.randomUUID()); turret.setOwned(true); h.getLevel().addFreshEntity(turret);
        var target=h.spawnWithNoFreeWill(EntityType.ZOMBIE,7,2,5); turret.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.ARROW,2)); turret.performRangedAttack(target,.5F);
        h.assertTrue(turret.getMainHandItem().getCount()==1 && h.getLevel().getEntitiesOfClass(Arrow.class,h.getBounds()).stream().anyMatch(a->a.getOwner()==turret && a.pickup==net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED), "real turret pays one arrow and creates a native owned non-pickup projectile");
        turret.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY); long count=h.getLevel().getEntitiesOfClass(Arrow.class,h.getBounds()).stream().filter(a->a.getOwner()==turret).count(); turret.performRangedAttack(target,.5F); h.assertTrue(h.getLevel().getEntitiesOfClass(Arrow.class,h.getBounds()).stream().filter(a->a.getOwner()==turret).count()==count,"empty turret cannot create free ammunition");
        for(var arrow:h.getLevel().getEntitiesOfClass(Arrow.class,h.getBounds())) if(arrow.getOwner()==turret)arrow.discard(); turret.discard(); target.discard(); h.succeed();
    }
    private static void bore(GameTestHelper h) {
        var bore=Mob8ConstructRegistries.BORE.create(h.getLevel()); h.assertTrue(bore!=null,"registered bore"); bore.setValidSpawn();
        bore.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_PICKAXE)); h.assertTrue(bore.validInventory() && bore.getDigSpeed(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState())>0,"real native tool enables bore and controls speed"); bore.getMainHandItem().setDamageValue(bore.getMainHandItem().getMaxDamage()-1); h.assertTrue(!bore.validInventory(),"bore stops before destroying last durability"); bore.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.STICK)); h.assertTrue(!bore.validInventory() && bore.getDigSpeed(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState())==0,"non-pick cannot enable excavation"); bore.discard(); h.succeed();
    }
    private static void golemBoss(GameTestHelper h){
        BulkGameplayFixtures.floor(h);var boss=h.spawn(thaumcraft.common.entities.Mob8BossRegistries.ELDRITCH_GOLEM,4,2,5);boss.setNoAi(true);var target=h.spawnWithNoFreeWill(EntityType.COW,7,2,5);
        h.assertTrue(!boss.hurt(h.getLevel().damageSources().generic(),boss.getHealth()+1)&&boss.isHeadless()&&boss.isAlive(),"first lethal hit triggers real headless phase rather than death");
        var clone=thaumcraft.common.entities.Mob8BossRegistries.ELDRITCH_GOLEM.create(h.getLevel());h.assertTrue(clone!=null,"native boss clone");clone.load(boss.saveWithoutId(new CompoundTag()));h.assertTrue(clone.isHeadless(),"native save restores headless boss phase and goal");
        h.assertTrue(boss.doHurtTarget(target)&&target.getHealth()<target.getMaxHealth(),"headless native melee deals damage and knocks back");float health=target.getHealth();h.assertTrue(!boss.doHurtTarget(target)&&target.getHealth()==health,"source attack timer prevents same-tick double hit");clone.discard();boss.discard();target.discard();h.succeed();
    }
    private static void wardenBoss(GameTestHelper h){
        BulkGameplayFixtures.floor(h);var boss=h.spawn(thaumcraft.common.entities.Mob8BossRegistries.ELDRITCH_WARDEN,5,2,5);boss.setNoAi(true);boss.setAbsorptionAmount(0);
        h.assertTrue(boss.hurt(h.getLevel().damageSources().generic(),1),"native warden accepts unshielded hit");float health=boss.getHealth();h.assertTrue(!boss.hurt(h.getLevel().damageSources().generic(),2)&&boss.getHealth()==health,"unshielded native hit activates frenzy immunity");h.assertTrue(boss.isInvulnerableTo(h.getLevel().damageSources().wither())&&boss.isInvulnerableTo(h.getLevel().damageSources().drown()),"source warden elemental immunity remains active");boss.discard();h.succeed();
    }
    private static void leaderBoss(GameTestHelper h){
        BulkGameplayFixtures.floor(h);var leader=h.spawn(thaumcraft.common.entities.Mob8BossRegistries.CULTIST_LEADER,3,2,5);var knight=h.spawnWithNoFreeWill(thaumcraft.common.entities.CultEntities.KNIGHT,5,2,5);var target=h.spawnWithNoFreeWill(EntityType.COW,7,2,5);
        h.assertTrue(leader.isAlliedTo(knight)&&!leader.canAttackType(knight.getType()),"native leader protects source cult faction");leader.performRangedAttack(target,.5F);h.assertTrue(!h.getLevel().getEntitiesOfClass(thaumcraft.common.entities.projectile.EntityGolemOrb.class,h.getBounds()).isEmpty(),"actual leader attack creates source homing orb");
        h.succeedWhen(()->{h.assertTrue(knight.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION),"actual native leader AI grants regeneration to nearby cultist");for(var orb:h.getLevel().getEntitiesOfClass(thaumcraft.common.entities.projectile.EntityGolemOrb.class,h.getBounds()))orb.discard();leader.discard();knight.discard();target.discard();});
    }
}
