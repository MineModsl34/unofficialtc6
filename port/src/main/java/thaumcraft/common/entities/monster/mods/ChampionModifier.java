package thaumcraft.common.entities.monster.mods;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** Exact source modifier ordering, roles and persistent UUID identity mapped to native IDs. */
public final class ChampionModifier {
    public final int id, type;
    public final String name;
    public final AttributeModifier attributeMod;
    public final IChampionModifierEffect effect;
    public static final ChampionModifier[] mods = {
        new ChampionModifier(0,"bold",-1,"40289aa1-907f-4ac6-ad79-e6681efe2cbc"),
        new ChampionModifier(1,"spine",2,"365eead5-3f15-42a8-9e68-36100faef945"),
        new ChampionModifier(2,"armor",2,"4e23758d-348e-42a8-8de6-08ae0a59033c"),
        new ChampionModifier(3,"mighty",-1,"6d2ffe79-f034-4a06-b288-e1916c21e385"),
        new ChampionModifier(4,"grim",1,"0f23321e-f921-4246-90b8-21ef202de224"),
        new ChampionModifier(5,"warded",0,"b622c4d8-abc6-4db7-b3ee-5cf71b8e5286"),
        new ChampionModifier(6,"warp",1,"107da049-af7a-4409-989a-6de23c8fe036"),
        new ChampionModifier(7,"undying",0,"cb9484d3-6255-4893-a4f2-3ecc375692ee"),
        new ChampionModifier(8,"fiery",1,"6b567fdf-9245-48f5-8314-f93fe5db1427"),
        new ChampionModifier(9,"sickly",1,"b5718868-9ab0-424c-af1f-8b35e836b46e"),
        new ChampionModifier(10,"venomous",1,"ab9a132e-c619-4c0a-a103-10cbbcfba1a2"),
        new ChampionModifier(11,"vampiric",1,"3412251e-af81-4c3c-93ba-2e1c33b049ea"),
        new ChampionModifier(12,"infested",2,"9c577fbe-ddbc-4ea2-a661-770ea775f43b"),
        new ChampionModifier(13,"tainted",0,"a3bb2595-8221-4140-bc73-538abcd1bbd2")
    };
    public static final AttributeModifier ATTRIBUTE_MOD_NONE = modifier("1e645a3d-9115-4807-a61c-705172839f87",1,AttributeModifier.Operation.ADD_VALUE);
    public static final AttributeModifier ATTRIBUTE_MINUS_ONE = modifier("f48eb416-8321-46e6-8c07-a07bed729a0c",-1,AttributeModifier.Operation.ADD_VALUE);
    private ChampionModifier(int id,String name,int type,String uuid) {
        this.id=id;this.name=name;this.type=type;
        attributeMod=modifier(uuid,id+2,AttributeModifier.Operation.ADD_VALUE);
        effect=(mob,target,source,amount)->ChampionEffects.perform(id,mob,target,source,amount);
    }
    public Component getModNameLocalized() { return Component.translatable("champion.mod."+name); }
    public static AttributeModifier modifier(String uuid,double amount,AttributeModifier.Operation operation) {
        return new AttributeModifier(ResourceLocation.fromNamespaceAndPath("thaumcraft","legacy/"+uuid),amount,operation);
    }
}
