package thaumcraft.common.lib.events;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.entities.IEldritchMob;
import thaumcraft.api.entities.ITaintedMob;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.config.ConfigEntities;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.entities.construct.EntityOwnedConstruct;
import thaumcraft.common.entities.monster.EntityBrainyZombie;
import thaumcraft.common.entities.monster.boss.EntityThaumcraftBoss;
import thaumcraft.common.entities.monster.mods.*;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.potions.WarpEffects;
import thaumcraft.common.lib.utils.EntityUtils;

/** The new stage-8 branches of source EntityEvents; stage-4/7 hooks are not duplicated. */
@EventBusSubscriber(modid=Thaumcraft.MODID)
public final class ChampionEvents {
    private ChampionEvents() {}
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if(event.getLevel().isClientSide || !(event.getEntity() instanceof Mob mob)||!ChampionSystem.sourceCreature(mob))return;
        if(ChampionSystem.modifier(mob)==13)ChampionSystem.rearmTaintedAI(mob);
        if(!(mob instanceof Monster)||ChampionSystem.modifier(mob)>=-1)return;
        var biome=mob.level().getBiome(mob.blockPosition());int value=0;boolean whitelist=false;
        for(var row:ConfigEntities.championModWhitelist.entrySet())if(row.getKey().isAssignableFrom(mob.getClass())) {
            whitelist=true;value=Math.max(value,row.getValue());
        }
        int score=ChampionRules.score(mob.level().random.nextInt(100),mob.level().getDifficulty()==Difficulty.EASY,
            mob.level().getDifficulty()==Difficulty.HARD,ModConfig.CONFIG_WORLD.allowChampionMobs,
            mob.level().dimension().location().equals(ResourceLocation.parse("thaumcraft:outer_lands")),
            biome.is(Tags.Biomes.IS_SPOOKY)||biome.is(BiomeTags.IS_NETHER)||biome.is(Tags.Biomes.IS_END),
            value,mob instanceof EntityThaumcraftBoss);
        // Source isDangerousLocation always returns false. "Disabled" only adds score penalties.
        if(whitelist&&score<=0&&mob.getAttribute(Attributes.MAX_HEALTH).getBaseValue()>=10)EntityUtils.makeChampion(mob,false);
        else {var attribute=mob.getAttribute(ChampionSystem.CHAMPION_MOD);if(attribute!=null){
            attribute.removeModifier(ChampionModifier.ATTRIBUTE_MOD_NONE.id());attribute.addPermanentModifier(ChampionModifier.ATTRIBUTE_MOD_NONE);
        }}
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if(event.getEntity().level().isClientSide || !(event.getEntity() instanceof PathfinderMob mob)||!mob.isAlive()||!ChampionSystem.sourceCreature(mob))return;
        int id=ChampionSystem.modifier(mob);
        if(id>=ChampionModifier.mods.length){mob.discard();return;} // Source malformed champion IDs discard the mob.
        if(id>=0&&ChampionModifier.mods[id].type==0)ChampionModifier.mods[id].effect.performEffect(mob,null,null,0);
    }
    @SubscribeEvent public static void hurt(LivingIncomingDamageEvent event) {
        LivingEntity victim=event.getEntity();if(event.isCanceled()||victim.level().isClientSide||victim instanceof Player)return;
        // Incoming precedes shield/cooldown rejection. ARMOR is set only for an admitted
        // actuallyHurt pass (including armor-bypassing damage), as old LivingHurtEvent was.
        // Never lower the incoming amount: that would also change native lastHurt/cooldown.
        event.addReductionModifier(DamageContainer.Reduction.ARMOR,(container,previousReduction)->{
            float admitted=container.getNewDamage();
            float modified=dispatchHurt(victim,event.getSource(),admitted);
            if(modified==admitted)return previousReduction;
            float armor=victim.getArmorValue(),toughness=(float)victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            float reduction=ChampionDamageRules.bridgeArmorReduction(admitted,modified,armor,toughness,previousReduction);
            // LOW WearableArmorEvents and later callbacks must see the source-modified
            // admitted amount. Native armor durability has already run; see source notes.
            container.setNewDamage(modified);
            return reduction;
        });
    }
    private static float dispatchHurt(LivingEntity victim,DamageSource source,float amount) {
        if(victim.getHealth()<2&&!victim.getType().is(EntityTypeTags.UNDEAD)&&victim.isAlive()
            &&!(victim instanceof EntityOwnedConstruct)&&!(victim instanceof ITaintedMob)
            &&victim.hasEffect(WarpEffects.FLUX_TAINT)&&victim.getRandom().nextBoolean()) {EntityUtils.makeTainted(victim);return amount;}
        // Deliberate source quirk: offensive champion dispatch is INSIDE the EntityMob victim branch.
        if(!(victim instanceof Monster))return amount;
        int id=ChampionSystem.modifier(victim);LivingEntity attacker=source.getEntity() instanceof LivingEntity living?living:null;
        if(id>=0&&id<ChampionModifier.mods.length || victim instanceof IEldritchMob) {
            if((id==5||victim instanceof IEldritchMob)&&victim.getAbsorptionAmount()>0){
                thaumcraft.common.lib.fx.SourceFX.shield(victim,source);
                victim.playSound(SoundsTC.runicShieldCharge,.66F,1.1F+victim.level().getRandom().nextFloat()*.1F);
            }
            else if(id>=0&&id<ChampionModifier.mods.length&&ChampionModifier.mods[id].type==2&&attacker!=null)
                amount=ChampionModifier.mods[id].effect.performEffect(victim,attacker,source,amount);
        }
        if(amount>0 && attacker instanceof Mob) {
            int other=ChampionSystem.modifier(attacker);
            if(other>=0&&other<ChampionModifier.mods.length&&ChampionModifier.mods[other].type==1)
                amount=ChampionModifier.mods[other].effect.performEffect(attacker,victim,source,amount);
        }
        return amount;
    }
    @SubscribeEvent public static void drops(LivingDropsEvent event) {
        if(event.isCanceled()||!(event.getEntity().level() instanceof ServerLevel level))return;
        var mob=event.getEntity();var source=event.getSource().getEntity();
        int looting=source instanceof LivingEntity living?EnchantmentHelper.getEnchantmentLevel(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING),living):0;
        int id=ChampionSystem.modifier(mob);
        if(!(source instanceof FakePlayer)&&event.isRecentlyHit()&&mob instanceof Monster&&!(mob instanceof EntityThaumcraftBoss)&&id>=0&&id!=13) {
            ExperienceOrb.award(level,mob.position(),5+level.random.nextInt(3));
            int tier=ChampionRules.lootTier(level.random.nextInt(9),looting);
            var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse("thaumcraft:loot_bag_"+new String[]{"common","uncommon","rare"}[tier]));
            event.getDrops().add(new ItemEntity(level,mob.getX(),mob.getEyeY(),mob.getZ(),new ItemStack(item)));
        }
        if(mob instanceof Zombie&&!(mob instanceof EntityBrainyZombie)&&event.isRecentlyHit()&&level.random.nextInt(10)-looting<1)
            event.getDrops().add(new ItemEntity(level,mob.getX(),mob.getEyeY(),mob.getZ(),new ItemStack(ItemsTC.brain)));
    }
}
