package thaumcraft.common.world.biomes;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import thaumcraft.common.config.ModConfig;

/** Config-aware native modifier. Startup/reload calendar snapshot follows source postInit timing. */
public record TCMobSpawnModifier() implements BiomeModifier {
    public static final MapCodec<TCMobSpawnModifier> CODEC=MapCodec.unit(new TCMobSpawnModifier());
    public static final TagKey<Biome> LEGACY_POOL=TagKey.create(Registries.BIOME,ResourceLocation.parse("thaumcraft:legacy_overworld_spawn_pool"));
    @Override public void modify(Holder<Biome> biome,Phase phase,ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if(phase!=Phase.ADD)return;
        String key=biome.unwrapKey().map(k->k.location().getNamespace().equals("thaumcraft")?k.location().getPath():"").orElse("");
        var date=java.time.LocalDate.now();
        var flags=new MobSpawnRules.Flags(ModConfig.CONFIG_WORLD.allowSpawnAngryZombie,ModConfig.CONFIG_WORLD.allowSpawnPech,
            ModConfig.CONFIG_WORLD.allowSpawnWisp,ModConfig.CONFIG_WORLD.allowSpawnElder,ModConfig.CONFIG_WORLD.allowSpawnFireBat);
        var spawners=builder.getMobSpawnSettings().getSpawner(MobCategory.MONSTER);
        var rows=MobSpawnRules.spawns(key,biome.is(LEGACY_POOL),biome.is(Tags.Biomes.IS_MAGICAL),biome.is(BiomeTags.IS_NETHER),
            !spawners.isEmpty(),date.getMonthValue()==10&&date.getDayOfMonth()==31,flags);
        for(var row:rows){
            var id=ResourceLocation.fromNamespaceAndPath("thaumcraft",row.entity());
            if(!BuiltInRegistries.ENTITY_TYPE.containsKey(id))throw new IllegalStateException("Missing actual source spawn type "+id);
            var type=BuiltInRegistries.ENTITY_TYPE.get(id);
            // Exact Forge addSpawn replacement semantics, avoiding doubled forest Pech weights.
            int index=-1;for(int i=0;i<spawners.size();i++)if(spawners.get(i).type==type){index=i;break;}
            var spawn=new MobSpawnSettings.SpawnerData(type,row.weight(),row.min(),row.max());
            if(index<0)spawners.add(spawn);else spawners.set(index,spawn);
        }
    }
    @Override public MapCodec<? extends BiomeModifier> codec(){return CODEC;}
}
