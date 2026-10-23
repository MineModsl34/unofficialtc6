package thaumcraft.common.entities.monster.mods;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.entities.ai.combat.SourceMeleeAttackGoal;

@EventBusSubscriber(modid=Thaumcraft.MODID,bus=EventBusSubscriber.Bus.MOD)
public final class ChampionSystem {
    public static Holder<Attribute> CHAMPION_MOD,TAINTED_MOD;
    private static final Set<EntityType<?>> WITHOUT_ATTACK = new HashSet<>();
    private static final AttributeModifier TAINTED_AI = ChampionModifier.modifier("2cb22137-a9d8-4417-ae06-de0e70f11b4c",1,AttributeModifier.Operation.ADD_VALUE);
    private ChampionSystem() {}
    public static void register(RegisterEvent.RegisterHelper<Attribute> helper) {
        var champion=new RangedAttribute("attribute.thaumcraft.champion",-2,-2,100).setSyncable(true);
        var tainted=new RangedAttribute("attribute.thaumcraft.tainted",0,0,1).setSyncable(true);
        helper.register(ResourceLocation.parse("thaumcraft:champion_mod"),champion);
        helper.register(ResourceLocation.parse("thaumcraft:tainted_mod"),tainted);
        CHAMPION_MOD=BuiltInRegistries.ATTRIBUTE.wrapAsHolder(champion);
        TAINTED_MOD=BuiltInRegistries.ATTRIBUTE.wrapAsHolder(tainted);
        thaumcraft.api.ThaumcraftApiHelper.CHAMPION_MOD=CHAMPION_MOD;
    }
    @SubscribeEvent public static void attributes(EntityAttributeModificationEvent event) {
        for(var type:event.getTypes()) {
            if(type==EntityType.PLAYER)continue;
            if(!event.has(type,CHAMPION_MOD))event.add(type,CHAMPION_MOD);
            if(!event.has(type,TAINTED_MOD))event.add(type,TAINTED_MOD);
            if(!event.has(type,Attributes.ATTACK_DAMAGE)) {WITHOUT_ATTACK.add(type);event.add(type,Attributes.ATTACK_DAMAGE,0);}
            if(!event.has(type,Attributes.MAX_ABSORPTION))event.add(type,Attributes.MAX_ABSORPTION,0);
        }
    }
    public static int modifier(LivingEntity entity) {
        if(CHAMPION_MOD==null)return -2;
        var attribute=entity.getAttribute(CHAMPION_MOD);
        return attribute==null?-2:(int)attribute.getValue();
    }
    /** Legacy EntityCreature excludes EntityFlying and owned constructs, even where native inheritance changed. */
    public static boolean sourceCreature(LivingEntity entity){return entity instanceof PathfinderMob
        &&!(entity instanceof thaumcraft.common.entities.construct.EntityOwnedConstruct)
        &&!(entity instanceof thaumcraft.common.entities.monster.EntityWisp);}
    public static Component getModifierName(Mob mob) {
        int id=modifier(mob);return id>=0&&id<ChampionModifier.mods.length?ChampionModifier.mods[id].getModNameLocalized():Component.empty();
    }
    public static void addAbsorption(LivingEntity mob,float amount) {
        float target=mob.getAbsorptionAmount()+amount;
        var limit=mob.getAttribute(Attributes.MAX_ABSORPTION);
        if(limit!=null && limit.getValue()<target)limit.setBaseValue(Math.max(limit.getBaseValue(),target));
        mob.setAbsorptionAmount(target);
    }
    public static void ensureTaintedAttack(LivingEntity mob) {
        var attack=mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if(attack!=null && WITHOUT_ATTACK.contains(mob.getType()) && attack.getBaseValue()==0)
            attack.setBaseValue(ChampionRules.critterDamage(mob.getBbHeight(),mob.getBbWidth()));
    }
    public static boolean originallyHadAttack(LivingEntity mob) {return !WITHOUT_ATTACK.contains(mob.getType());}
    public static void resetTaintedAI(PathfinderMob mob) {
        var attribute=mob.getAttribute(TAINTED_MOD);
        if(attribute==null)return;
        if(!(mob instanceof Monster) && attribute.getValue()==0) {
            mob.goalSelector.removeAllGoals(goal->true);mob.targetSelector.removeAllGoals(goal->true);
            mob.getNavigation().stop();mob.setTarget(null);
            mob.goalSelector.addGoal(0,new FloatGoal(mob));
            mob.goalSelector.addGoal(2,new thaumcraft.common.entities.ai.combat.EntityCritterAIAttackMelee(mob,1.2,false));
            mob.goalSelector.addGoal(5,new MoveTowardsRestrictionGoal(mob,1));
            mob.goalSelector.addGoal(7,new RandomStrollGoal(mob,1));
            mob.goalSelector.addGoal(8,new LookAtPlayerGoal(mob,Player.class,8));
            mob.goalSelector.addGoal(8,new RandomLookAroundGoal(mob));
            mob.goalSelector.addGoal(6,new MoveThroughVillageGoal(mob,1,false,4,()->false));
            mob.targetSelector.addGoal(1,new HurtByTargetGoal(mob).setAlertOthers(ZombifiedPiglin.class));
            mob.targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(mob,Player.class,true));
            attribute.removeModifier(TAINTED_AI.id());attribute.addPermanentModifier(TAINTED_AI);
        }
        ensureTaintedAttack(mob);
    }
    public static void rearmTaintedAI(Mob mob) {
        var attribute=mob.getAttribute(TAINTED_MOD);if(attribute!=null)attribute.removeModifier(TAINTED_AI.id());
    }
}
