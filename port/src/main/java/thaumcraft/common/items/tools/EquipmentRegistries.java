package thaumcraft.common.items.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.projectile.EntityGrapple;
import thaumcraft.common.items.curios.ItemLootBag;
import thaumcraft.common.items.curios.ItemPechWand;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;

/** Explicit common integration hooks. Shared ConfigItems/Registrar owns actual registration. */
public final class EquipmentRegistries {
    public static EntityType<EntityGrapple> GRAPPLE;
    private EquipmentRegistries() {}
    public static Map<String,Item> createItems(){
        var result=new LinkedHashMap<String,Item>();
        result.put("thaumium_axe",ItemsTC.thaumiumAxe=new ItemThaumiumAxe());result.put("thaumium_shovel",ItemsTC.thaumiumShovel=new ItemThaumiumShovel());
        result.put("thaumium_pick",ItemsTC.thaumiumPick=new ItemThaumiumPickaxe());result.put("thaumium_hoe",ItemsTC.thaumiumHoe=new ItemThaumiumHoe());
        result.put("void_axe",ItemsTC.voidAxe=new ItemVoidAxe());result.put("void_shovel",ItemsTC.voidShovel=new ItemVoidShovel());
        result.put("void_pick",ItemsTC.voidPick=new ItemVoidPickaxe());result.put("void_hoe",ItemsTC.voidHoe=new ItemVoidHoe());
        result.put("elemental_axe",ItemsTC.elementalAxe=new ItemElementalAxe());result.put("elemental_shovel",ItemsTC.elementalShovel=new ItemElementalShovel());
        result.put("elemental_pick",ItemsTC.elementalPick=new ItemElementalPickaxe());result.put("elemental_hoe",ItemsTC.elementalHoe=new ItemElementalHoe());result.put("elemental_sword",ItemsTC.elementalSword=new ItemElementalSword());
        result.put("primal_crusher",ItemsTC.primalCrusher=new ItemPrimalCrusher());result.put("crimson_blade",ItemsTC.crimsonBlade=new ItemCrimsonBlade());
        result.put("grapple_gun",ItemsTC.grappleGun=new ItemGrappleGun());result.put("sanity_checker",ItemsTC.sanityChecker=new ItemSanityChecker());result.put("pech_wand",ItemsTC.pechWand=new ItemPechWand());
        result.put("loot_bag_common",ItemsTC.lootBag=new ItemLootBag(0));result.put("loot_bag_uncommon",new ItemLootBag(1));result.put("loot_bag_rare",new ItemLootBag(2));
        return result;
    }
    public static Map<String,EntityType<?>> createEntityTypes(){
        GRAPPLE=EntityType.Builder.<EntityGrapple>of(EntityGrapple::new,MobCategory.MISC).sized(.1F,.1F).clientTrackingRange(4).updateInterval(20).setShouldReceiveVelocityUpdates(true).build("thaumcraft:grapple");
        return Map.of("grapple",GRAPPLE);
    }
    public static ItemStack defaultStack(Item item){
        ItemStack stack=new ItemStack(item);
        if(item==ItemsTC.elementalAxe){enchant(stack,EnumInfusionEnchantment.BURROWING,1);enchant(stack,EnumInfusionEnchantment.COLLECTOR,1);}
        else if(item==ItemsTC.elementalPick){enchant(stack,EnumInfusionEnchantment.REFINING,1);enchant(stack,EnumInfusionEnchantment.SOUNDING,2);}
        else if(item==ItemsTC.elementalShovel)enchant(stack,EnumInfusionEnchantment.DESTRUCTIVE,1);
        else if(item==ItemsTC.elementalSword)enchant(stack,EnumInfusionEnchantment.ARCING,2);
        else if(item==ItemsTC.primalCrusher){enchant(stack,EnumInfusionEnchantment.DESTRUCTIVE,1);enchant(stack,EnumInfusionEnchantment.REFINING,1);}
        return stack;
    }
    private static void enchant(ItemStack stack,EnumInfusionEnchantment enchantment,int level){EnumInfusionEnchantment.addInfusionEnchantment(stack,enchantment,level);}
    public static List<ItemStack> creativeStacks(){return List.of(defaultStack(ItemsTC.elementalAxe),defaultStack(ItemsTC.elementalPick),defaultStack(ItemsTC.elementalShovel),defaultStack(ItemsTC.elementalSword),defaultStack(ItemsTC.primalCrusher));}
    public static Map<net.minecraft.tags.TagKey<Item>,Item[]> itemTags(){return Map.of(
        net.minecraft.tags.ItemTags.SWORDS,new Item[]{ItemsTC.elementalSword,ItemsTC.crimsonBlade},
        net.minecraft.tags.ItemTags.AXES,new Item[]{ItemsTC.thaumiumAxe,ItemsTC.voidAxe,ItemsTC.elementalAxe},
        net.minecraft.tags.ItemTags.PICKAXES,new Item[]{ItemsTC.thaumiumPick,ItemsTC.voidPick,ItemsTC.elementalPick,ItemsTC.primalCrusher},
        net.minecraft.tags.ItemTags.SHOVELS,new Item[]{ItemsTC.thaumiumShovel,ItemsTC.voidShovel,ItemsTC.elementalShovel,ItemsTC.primalCrusher},
        net.minecraft.tags.ItemTags.HOES,new Item[]{ItemsTC.thaumiumHoe,ItemsTC.voidHoe,ItemsTC.elementalHoe},
        net.minecraft.tags.ItemTags.DURABILITY_ENCHANTABLE,new Item[]{ItemsTC.thaumiumAxe,ItemsTC.thaumiumPick,ItemsTC.thaumiumShovel,ItemsTC.thaumiumHoe,ItemsTC.voidAxe,ItemsTC.voidPick,ItemsTC.voidShovel,ItemsTC.voidHoe,ItemsTC.elementalAxe,ItemsTC.elementalPick,ItemsTC.elementalShovel,ItemsTC.elementalHoe,ItemsTC.elementalSword,ItemsTC.crimsonBlade,ItemsTC.primalCrusher});}
}
