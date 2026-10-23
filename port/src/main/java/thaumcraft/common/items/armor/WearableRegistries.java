package thaumcraft.common.items.armor;

import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.items.baubles.*;

/** Called from the shared material/item owner, never registers fake future recipe outputs. */
public final class WearableRegistries {
    public static Holder<ArmorMaterial> SPECIAL, CLOTH, FORTRESS, VOID_ROBE, CULTIST_LEADER;
    private static Map<String,Item> items;
    private WearableRegistries() {}
    public static void registerArmorMaterials(RegisterEvent.RegisterHelper<ArmorMaterial> registry) {
        SPECIAL = material(registry,"special",1,2,3,1,25,1,() -> Ingredient.EMPTY,true,false);
        CLOTH = material(registry,"cloth",1,2,3,1,25,1,() -> Ingredient.of(ItemsTC.fabric),true,true);
        FORTRESS = material(registry,"fortress",3,6,7,3,25,3,() -> Ingredient.of(ItemsTC.ingotThaumium),false,false);
        VOID_ROBE = material(registry,"void_robe",4,7,9,4,10,2,() -> Ingredient.of(ItemsTC.ingotVoid),true,true);
        CULTIST_LEADER = material(registry,"cultist_leader",3,6,7,3,20,1,() -> Ingredient.of(Items.IRON_INGOT),false,false);
    }
    private static Holder<ArmorMaterial> material(RegisterEvent.RegisterHelper<ArmorMaterial> registry,String name,int feet,int legs,int chest,int head,int enchant,float toughness,java.util.function.Supplier<Ingredient> repair,boolean leather,boolean dye) {
        var id = ResourceLocation.fromNamespaceAndPath("thaumcraft",name);
        var layers = dye ? List.of(new ArmorMaterial.Layer(id,"",true),new ArmorMaterial.Layer(id,"_overlay",false)) : List.of(new ArmorMaterial.Layer(id));
        registry.register(id,new ArmorMaterial(Map.of(ArmorItem.Type.BOOTS,feet,ArmorItem.Type.LEGGINGS,legs,ArmorItem.Type.CHESTPLATE,chest,ArmorItem.Type.HELMET,head),enchant,leather ? SoundEvents.ARMOR_EQUIP_LEATHER : SoundEvents.ARMOR_EQUIP_IRON,repair,layers,toughness,0));
        return BuiltInRegistries.ARMOR_MATERIAL.getHolderOrThrow(ResourceKey.create(Registries.ARMOR_MATERIAL,id));
    }
    public static Map<String,Item> createItems() {
        if (items != null) return items;
        if (SPECIAL == null || CLOTH == null || FORTRESS == null || VOID_ROBE == null || CULTIST_LEADER == null) throw new IllegalStateException("Wearable materials must register before items");
        Map<String,Item> out = new LinkedHashMap<>();
        out.put("cloth_chest",new ItemRobeArmor(ArmorItem.Type.CHESTPLATE)); out.put("cloth_legs",new ItemRobeArmor(ArmorItem.Type.LEGGINGS)); out.put("cloth_boots",new ItemRobeArmor(ArmorItem.Type.BOOTS));
        out.put("goggles",new ItemGoggles()); out.put("traveller_boots",new ItemBootsTraveller());
        for (var type : List.of(ArmorItem.Type.HELMET,ArmorItem.Type.CHESTPLATE,ArmorItem.Type.LEGGINGS)) {
            String suffix = type == ArmorItem.Type.HELMET ? "helm" : type == ArmorItem.Type.CHESTPLATE ? "chest" : "legs";
            out.put("fortress_" + suffix,new ItemFortressArmor(type)); out.put("void_robe_" + suffix,new ItemVoidRobeArmor(type)); out.put("crimson_praetor_" + suffix,new ItemCultistLeaderArmor(type));
        }
        out.put("amulet_vis_crafted",new ItemAmuletVis(true)); out.put("verdant_charm",new ItemVerdantCharm()); out.put("voidseer_charm",new ItemVoidseerCharm());
        out.put("curiosity_band",new ItemCuriosityBand()); out.put("cloud_ring",new ItemCloudRing()); out.put("charm_undying",new ItemCharmUndying());
        items = Collections.unmodifiableMap(out); return items;
    }
    public static Collection<Item> registeredItems() { return items == null ? List.of() : items.values(); }
    /** Source creative subitems; no registry duplication or invented per-type recipe objects. */
    public static List<ItemStack> creativeStacks(Item item) { return item instanceof ItemVerdantCharm charm ? charm.getTabVariants() : List.of(new ItemStack(item)); }
    public static Item item(String id) { if (items == null || !items.containsKey(id)) throw new IllegalArgumentException("Unknown wearable " + id); return items.get(id); }
}
