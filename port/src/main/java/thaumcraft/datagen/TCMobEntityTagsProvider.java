package thaumcraft.datagen;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.tags.EntityTypeTags;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import thaumcraft.common.entities.*;

/** Source CreatureAttribute is now datapack-driven, including native enchantment/healing immunity tags. */
public final class TCMobEntityTagsProvider extends EntityTypeTagsProvider {
    public TCMobEntityTagsProvider(PackOutput output,CompletableFuture<HolderLookup.Provider> lookup,ExistingFileHelper files){super(output,lookup,"thaumcraft",files);}
    @Override protected void addTags(HolderLookup.Provider lookup){
        tag(EntityTypeTags.UNDEAD).add(WarpEntities.ELDRITCH_GUARDIAN);
        tag(EntityTypeTags.ZOMBIES).add(Mob8NaturalRegistries.BRAINY_ZOMBIE,Mob8NaturalRegistries.GIANT_BRAINY_ZOMBIE,Mob8NaturalRegistries.INHABITED_ZOMBIE);
        tag(EntityTypeTags.ARTHROPOD).add(WarpEntities.MIND_SPIDER,Mob8NaturalRegistries.ELDRITCH_CRAB,Mob8BossRegistries.TAINT_CRAWLER);
        tag(EntityTypeTags.ARROWS).add(thaumcraft.common.entities.construct.Mob8ConstructRegistries.DART);
        tag(EntityTypeTags.CAN_BREATHE_UNDER_WATER).add(thaumcraft.common.entities.construct.Mob8ConstructRegistries.BASIC,
            thaumcraft.common.entities.construct.Mob8ConstructRegistries.ADVANCED,thaumcraft.common.entities.construct.Mob8ConstructRegistries.BORE,
            thaumcraft.common.golems.Golem9EntityRegistries.GOLEM);
    }
}
