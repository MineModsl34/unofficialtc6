package thaumcraft.common.lib.utils;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import thaumcraft.common.entities.monster.boss.EntityThaumcraftBoss;
import thaumcraft.common.entities.monster.mods.*;

/** Source champion buffs and range/ownership helpers, on native holders and IDs. */
public final class EntityUtils {
    private EntityUtils() {}
    private static AttributeModifier mod(String uuid,double amount,AttributeModifier.Operation op){return ChampionModifier.modifier(uuid,amount,op);}
    public static final AttributeModifier CHAMPION_HEALTH=mod("a62bef38-48cc-42a6-ac5e-ef913841c4fd",100,AttributeModifier.Operation.ADD_VALUE);
    public static final AttributeModifier CHAMPION_DAMAGE=mod("a340d2db-d881-4c25-ac62-f0ad14cd63b0",2,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    public static final AttributeModifier BOLDBUFF=mod("4b1edd33-caa9-47ae-a702-d86c05701037",.3,AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    public static final AttributeModifier MIGHTYBUFF=mod("7163897f-07f5-49b3-9ce4-b74beb83d2d3",2,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    public static final AttributeModifier[] HPBUFF={
        mod("54d621c1-dd4d-4b43-8bd2-5531c8875797",50,AttributeModifier.Operation.ADD_VALUE),
        mod("f51257dc-b7fa-4f7a-92d7-75d68e8592c4",50,AttributeModifier.Operation.ADD_VALUE),
        mod("3d6b2e42-4141-4364-b76d-0e8664bbd0bb",50,AttributeModifier.Operation.ADD_VALUE),
        mod("02c97a08-801c-4131-afa2-1427a6151934",50,AttributeModifier.Operation.ADD_VALUE),
        mod("0f354f6a-33c5-40be-93be-81b1338567f1",50,AttributeModifier.Operation.ADD_VALUE),
        mod("0f354f6a-33c5-40be-93be-81b1338567f1",25,AttributeModifier.Operation.ADD_VALUE)};
    public static final AttributeModifier[] DMGBUFF={
        mod("534f8c57-929a-48cf-bbd6-0fd851030748",.5,AttributeModifier.Operation.ADD_VALUE),
        mod("d317a76e-0e7c-4c61-acfd-9fa286053b32",.5,AttributeModifier.Operation.ADD_VALUE),
        mod("ff462d63-26a2-4363-830e-143ed97e2a4f",.5,AttributeModifier.Operation.ADD_VALUE),
        mod("cf1eb39e-0c67-495f-887c-0d3080828d2f",.5,AttributeModifier.Operation.ADD_VALUE),
        mod("3cfab9da-2701-43d8-ac07-885f16fa4117",.5,AttributeModifier.Operation.ADD_VALUE)};
    private static void apply(AttributeInstance attribute,AttributeModifier modifier){if(attribute!=null){attribute.removeModifier(modifier.id());attribute.addPermanentModifier(modifier);}}
    public static void makeChampion(Mob entity,boolean persist) {
        if(!ChampionSystem.sourceCreature(entity))return;
        var champion=entity.getAttribute(ChampionSystem.CHAMPION_MOD);
        if(champion==null||champion.getValue()>-2)return;
        int type=entity.level().getRandom().nextInt(ChampionModifier.mods.length);if(entity instanceof Creeper)type=0;
        apply(champion,ChampionModifier.mods[type].attributeMod);
        if(entity instanceof EntityThaumcraftBoss boss)boss.generateName();
        else {
            apply(entity.getAttribute(Attributes.MAX_HEALTH),CHAMPION_HEALTH);
            apply(entity.getAttribute(Attributes.ATTACK_DAMAGE),CHAMPION_DAMAGE);
            entity.heal(25);entity.setCustomName(ChampionModifier.mods[type].getModNameLocalized().copy().append(" ").append(entity.getName()));
        }
        if(persist)entity.setPersistenceRequired();
        if(type==0)apply(entity.getAttribute(Attributes.MOVEMENT_SPEED),BOLDBUFF);
        if(type==3)apply(entity.getAttribute(Attributes.ATTACK_DAMAGE),MIGHTYBUFF);
        if(type==5)ChampionSystem.addAbsorption(entity,ChampionRules.shield(entity.getAttribute(Attributes.MAX_HEALTH).getBaseValue()));
    }
    public static void makeTainted(LivingEntity entity) {
        if(!ChampionSystem.sourceCreature(entity))return;
        var champion=entity.getAttribute(ChampionSystem.CHAMPION_MOD);if(champion==null||champion.getValue()>-1)return;
        if(champion.getValue()==-1)apply(champion,ChampionModifier.ATTRIBUTE_MINUS_ONE);
        apply(champion,ChampionModifier.mods[13].attributeMod);
        if(entity instanceof EntityThaumcraftBoss boss)boss.generateName();
        else {
            apply(entity.getAttribute(Attributes.MAX_HEALTH),HPBUFF[5]);
            if(ChampionSystem.originallyHadAttack(entity))apply(entity.getAttribute(Attributes.ATTACK_DAMAGE),DMGBUFF[0]);
            else ChampionSystem.ensureTaintedAttack(entity);
            entity.heal(25);
        }
    }
    public static boolean isFriendly(Entity source,Entity target) {
        if(source==null||target==null)return false;
        if(source==target||source.isPassengerOfSameVehicle(target)||source.isAlliedTo(target))return true;
        if(target instanceof OwnableEntity owned&&source.equals(owned.getOwner()))return true;
        return !target.level().isClientSide&&target instanceof net.minecraft.world.entity.player.Player
            &&target.getServer()!=null&&!target.getServer().isPvpAllowed();
    }
    public static <T extends Entity> List<T> getEntitiesInRange(Level level,BlockPos pos,Entity except,Class<T> type,double range) {
        return getEntitiesInRange(level,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,except,type,range);
    }
    public static <T extends Entity> List<T> getEntitiesInRange(Level level,double x,double y,double z,Entity except,Class<T> type,double range) {
        return level.getEntitiesOfClass(type,new AABB(x,y,z,x,y,z).inflate(range),entity->entity!=except);
    }
    public static <T extends Entity> List<T> getEntitiesInRangeSorted(Level level,Entity entity,Class<T> type,double range) {
        return getEntitiesInRange(level,entity.getX(),entity.getY(),entity.getZ(),entity,type,range).stream()
            .sorted(java.util.Comparator.comparingDouble(entity::distanceToSqr)).toList();
    }
}
