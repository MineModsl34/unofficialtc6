package thaumcraft.common.entities;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
/** Original inherited 1.12 zombie goals, excluding 1.21 turtle hunting and turtle-egg destruction. */
public final class SourceZombieGoals {
    private SourceZombieGoals(){}
    public static void install(Zombie zombie,GoalSelector goals,GoalSelector targets){goals.addGoal(0,new FloatGoal(zombie));goals.addGoal(2,new ZombieAttackGoal(zombie,1,false));goals.addGoal(5,new MoveTowardsRestrictionGoal(zombie,1));goals.addGoal(6,new MoveThroughVillageGoal(zombie,1,false,4,zombie::canBreakDoors));goals.addGoal(7,new WaterAvoidingRandomStrollGoal(zombie,1));goals.addGoal(8,new LookAtPlayerGoal(zombie,Player.class,8));goals.addGoal(8,new RandomLookAroundGoal(zombie));targets.addGoal(1,new HurtByTargetGoal(zombie).setAlertOthers(ZombifiedPiglin.class));targets.addGoal(2,new NearestAttackableTargetGoal<>(zombie,Player.class,true));targets.addGoal(3,new NearestAttackableTargetGoal<>(zombie,Villager.class,false));targets.addGoal(3,new NearestAttackableTargetGoal<>(zombie,IronGolem.class,true));}
}
