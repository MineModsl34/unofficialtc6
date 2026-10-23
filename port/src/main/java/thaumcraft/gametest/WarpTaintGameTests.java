package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.world.taint.BlockFluxGoo;
import thaumcraft.common.blocks.world.taint.BlockTaintFibre;
import thaumcraft.common.blocks.world.taint.WarpTaintRegistries;
import thaumcraft.common.entities.WarpEntities;
import thaumcraft.common.lib.potions.WarpEffects;

/** Actual finite-world/entity dependencies, independently of the GUI and probabilistic warp selection. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class WarpTaintGameTests {
    private WarpTaintGameTests() {}
    // empty.nbt is 3x3x3 and the framework encases its Y=3 roof in BARRIER.
    // The central cell and all four cardinal neighbors must be actual interior air.
    private static final BlockPos CENTER=new BlockPos(1,1,1);
    private static BlockFluxGoo goo() { return (BlockFluxGoo)BlocksTC.fluxGoo; }
    private static RandomSource firstRoll(int bound,int expected) {
        for(int seed=0;seed<10000;seed++) { RandomSource candidate=RandomSource.create(seed);if(candidate.nextInt(bound)==expected)return RandomSource.create(seed); }
        throw new IllegalStateException("no deterministic fixture seed");
    }
    @GameTest(template="empty") public static void finiteGooFallsAndPreservesOverflow(GameTestHelper helper) {
        var world=helper.getLevel();BlockPos pos=helper.absolutePos(CENTER);
        world.setBlockAndUpdate(pos.below(),goo().defaultBlockState().setValue(BlockFluxGoo.LEVEL,5));
        world.setBlockAndUpdate(pos,goo().defaultBlockState().setValue(BlockFluxGoo.LEVEL,2));
        goo().finiteFlow(world,pos,world.getBlockState(pos),RandomSource.create(1));
        helper.assertTrue(goo().quanta(world,pos.below())==8&&goo().quanta(world,pos)==1,"6+3 source quanta becomes 8 below and 1 above; no infinite source replication");
        helper.assertFalse(world.getFluidState(pos.below()).isSource(),"even full source8 quanta is not infinite modern water");
        helper.assertTrue(Math.abs(world.getFluidState(pos.below()).getOwnHeight()-8/9F)<.00001,"source finite render percentage8/9");
        world.setBlockAndUpdate(pos.below(),Blocks.AIR.defaultBlockState());
        world.setBlockAndUpdate(pos,goo().defaultBlockState().setValue(BlockFluxGoo.LEVEL,2));
        goo().finiteFlow(world,pos,world.getBlockState(pos),RandomSource.create(1));
        helper.assertTrue(world.isEmptyBlock(pos)&&world.getFluidState(pos).isEmpty()&&goo().quanta(world,pos.below())==3,"source unobstructed fall removes all upper fluid instead of modern removeBlock retaining the original goo");helper.succeed();
    }
    @GameTest(template="empty") public static void finiteGooHorizontalRedistributionConservesVolume(GameTestHelper helper) {
        var world=helper.getLevel();BlockPos pos=helper.absolutePos(CENTER);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.setBlockAndUpdate(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState());
        for(Direction side:Direction.Plane.HORIZONTAL){world.setBlockAndUpdate(pos.relative(side),Blocks.AIR.defaultBlockState());helper.assertTrue(world.isEmptyBlock(pos.relative(side)),"finite redistribution fixture exposes four air cells, not framework barriers");}
        world.setBlockAndUpdate(pos,goo().defaultBlockState());
        goo().finiteFlow(world,pos,world.getBlockState(pos),RandomSource.create(17));
        int total=goo().quanta(world,pos),nonempty=total>0?1:0;
        for(Direction side:Direction.Plane.HORIZONTAL){int q=goo().quanta(world,pos.relative(side));helper.assertTrue(q>=1&&q<=2,"eight quanta shared among five cells, never a new full source");total+=q;if(q>0)nonempty++;}
        helper.assertTrue(total==8&&nonempty==5,"literal finite8-quanta distribution conserves volume");helper.succeed();
    }
    @GameTest(template="empty") public static void gooSpawnsActualSourceSizedSlime(GameTestHelper helper) {
        var world=helper.getLevel();BlockPos pos=helper.absolutePos(CENTER);world.setBlockAndUpdate(pos.above(),Blocks.AIR.defaultBlockState());
        world.setBlockAndUpdate(pos,goo().defaultBlockState().setValue(BlockFluxGoo.LEVEL,6));
        helper.assertTrue(world.isEmptyBlock(pos.above())&&world.getBlockState(pos).getValue(BlockFluxGoo.LEVEL)==6,"source meta6 slime branch fixture has air above");
        goo().tick(world.getBlockState(pos),world,pos,firstRoll(50,0));
        var slimes=world.getEntities(WarpEntities.THAUMIC_SLIME,net.minecraft.world.phys.AABB.ofSize(pos.getCenter(),2,2,2),e->true);
        helper.assertTrue(world.isEmptyBlock(pos),"source slime branch consumes the actual goo cell");
        helper.assertTrue(slimes.size()==1,"source goo spawns exactly one actual TC slime in the cell; actual count="+slimes.size());
        helper.assertTrue(slimes.getFirst().getSize()==2,"source meta6 => actual TC slime size2, not vanilla replacement");
        helper.assertTrue(slimes.getFirst().getHealth()==4,"source growth resets size-squared health");helper.succeed();
    }
    @GameTest(template="empty") public static void gooContactSlowsAndAddsUncurableVisExhaust(GameTestHelper helper) {
        var world=helper.getLevel();var player=FakePlayerFactory.get(world,new GameProfile(UUID.randomUUID(),"TC_Goo_Contact"));
        player.setDeltaMovement(2,3,4);BlockPos pos=helper.absolutePos(CENTER);
        goo().entityInside(goo().defaultBlockState().setValue(BlockFluxGoo.LEVEL,3),world,pos,player);
        helper.assertTrue(player.getDeltaMovement().equals(new net.minecraft.world.phys.Vec3(1,3,2)),"source halves X/Z at quanta4 and preserves Y");
        var effect=player.getEffect(WarpEffects.VIS_EXHAUST);
        helper.assertTrue(effect!=null&&effect.getDuration()==600&&effect.getAmplifier()==1&&effect.getCures().isEmpty(),"source600 ticks meta/3 exhaustion with no milk cure");helper.succeed();
    }
    @GameTest(template="empty") public static void sourceFibreGrowthUsesOldPositionPackingAndLosesUnsupportedSeedlessFibre(GameTestHelper helper) {
        BlockTaintFibre fibre=(BlockTaintFibre)BlocksTC.taintFibre;var world=helper.getLevel();BlockPos pos=helper.absolutePos(CENTER);
        helper.assertTrue(BlockTaintFibre.sourcePositionSeed(new BlockPos(1,2,3))==(1L<<38|2L<<26|3),"use old X/Y/Z bit layout, not modern X/Z/Y; source growth pattern remains same");
        world.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());world.setBlockAndUpdate(pos,fibre.defaultBlockState());
        helper.assertTrue(world.getBlockState(pos).getValue(BlockTaintFibre.DOWN),"persist calculated attachment for modern multipart renderer");
        helper.assertFalse(world.getBlockState(pos).getCollisionShape(world,pos).isEmpty(),"source physical face thickness survives");
        world.getBlockState(pos).randomTick(world,pos,RandomSource.create(1));
        helper.assertTrue(world.isEmptyBlock(pos),"source !nearTaintSeed dies; warp branch never generates a seed");helper.succeed();
    }
    @GameTest(template="empty") public static void sourceSlimeDeathSplitsIntoExactSizeSingles(GameTestHelper helper) {
        var world=helper.getLevel();var slime=WarpEntities.THAUMIC_SLIME.create(world);slime.moveTo(helper.absolutePos(CENTER).getCenter());slime.setSize(4,true);world.addFreshEntity(slime);
        var bounds=slime.getBoundingBox().inflate(4);slime.setHealth(0);slime.remove(Entity.RemovalReason.KILLED);
        var children=world.getEntities(WarpEntities.THAUMIC_SLIME,bounds,e->!e.isRemoved());
        helper.assertTrue(children.size()==4&&children.stream().allMatch(e->e.getSize()==1),"source size4 dies into exactly four size1 children; no vanilla half-size additional children");
        helper.assertTrue(slime.getSize()==4,"removal does not mutate original size to suppress modern Slime.remove");helper.succeed();
    }
    @GameTest(template="empty") public static void sourceSlimeSpitsShrinksAndResetsHealth(GameTestHelper helper) {
        var world=helper.getLevel();BlockPos pos=helper.absolutePos(CENTER);var player=FakePlayerFactory.get(world,new GameProfile(UUID.randomUUID(),"TC_Slime_Spit"));player.moveTo(pos.getX()+6,pos.getY(),pos.getZ());
        world.addNewPlayer(player);
        try {
            var slime=WarpEntities.THAUMIC_SLIME.create(world);slime.moveTo(pos.getCenter());slime.setSize(4,true);slime.setHealth(2);slime.setNoAi(true);slime.setNoGravity(true);world.addFreshEntity(slime);
            for(int i=0;i<100;i++)slime.tick();
            var shots=world.getEntities(WarpEntities.THAUMIC_SLIME,slime.getBoundingBox().inflate(12),e->e!=slime&&e.getSize()==1);
            helper.assertTrue(slime.getSize()==3&&slime.getHealth()==9&&slime.sourceSpitCounter()==101,"source spit interval100 -> reset101, lose one size and restore full squared health");
            helper.assertTrue(shots.size()==1&&shots.getFirst().getDeltaMovement().length()>1.4,"actual size1 projectile Gaussian1.5 velocity");
            helper.assertTrue(slime.sourceLaunchedTicks()==10,"source server launched stays10; decrement was intentionally client-only");
        } finally { world.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);thaumcraft.common.lib.capabilities.PlayerDataSync.forget(player); }
        helper.succeed();
    }
    @GameTest(template="empty") public static void fluxTaintHealsTaintedSlimeDamagesLivingAndExcludesUndead(GameTestHelper helper) {
        var world=helper.getLevel();var slime=WarpEntities.THAUMIC_SLIME.create(world);slime.setSize(2,true);slime.setHealth(1);
        var effect=WarpEffects.FLUX_TAINT.value();effect.applyEffectTick(slime,0);helper.assertTrue(slime.getHealth()==2,"source ITaintedMob heals1");
        var zombie=EntityType.ZOMBIE.create(world);float undead=zombie.getHealth();effect.applyEffectTick(zombie,0);helper.assertTrue(zombie.getHealth()==undead,"source undead excluded");
        var pig=EntityType.PIG.create(world);float living=pig.getHealth();effect.applyEffectTick(pig,0);helper.assertTrue(pig.getHealth()==living-1,"source living takes1 real bypass-armor taint damage");
        helper.assertTrue(effect.shouldApplyEffectTickThisTick(40,0)&&!effect.shouldApplyEffectTickThisTick(39,0)&&effect.shouldApplyEffectTickThisTick(1,6),"source cadence40>>amplifier, every tick at high amp");helper.succeed();
    }
}
