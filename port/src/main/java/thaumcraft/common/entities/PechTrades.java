package thaumcraft.common.entities;

import java.util.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.Level;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.monster.EntityPech;

/** Ordered ConfigEntities BETA26 tables; repeated rows are deliberate probability weights. */
public final class PechTrades {
    public record Trade(int tier,ItemStack stack){public Trade{if(tier<1||tier>5||stack==null)throw new IllegalArgumentException("Invalid Pech trade");stack=stack.copy();} @Override public ItemStack stack(){return stack.copy();}}
    private static final Map<Integer,List<Trade>> owned=new HashMap<>();
    private static Object registry;
    private PechTrades(){}
    public static synchronized void invalidate(){registry=null;}
    public static synchronized void ensure(Level level){if(registry!=level.registryAccess()){registry=level.registryAccess();reload(level);}}
    /** Removes only our previous identities, retaining addon rows and valuedItems overrides. */
    public static synchronized void reload(Level level){for(var entry:owned.entrySet()){var previous=EntityPech.tradeInventory.get(entry.getKey());if(previous!=null){var current=new ArrayList<>(previous);current.removeIf(row->entry.getValue().stream().anyMatch(old->old==row));EntityPech.tradeInventory.put(entry.getKey(),current);}}owned.clear();EntityPech.valuedItems.putIfAbsent(Item.getId(Items.ENDER_PEARL),15);
        List<Trade> f=new ArrayList<>(),m=new ArrayList<>(),a=new ArrayList<>();
        add(f,1,ItemsTC.clusterIron,ItemsTC.clusterGold,ItemsTC.clusterCinnabar,ItemsTC.clusterQuartz);
        for(String metal:List.of("copper","tin","silver","lead"))if(!BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("c","ingots/"+metal))).map(x->x.size()==0).orElse(true))add(f,1,item("cluster_"+metal));
        add(f,2,Items.BLAZE_ROD,BlocksTC.saplingGreatwood.asItem(),Items.DRAGON_BREATH);add(f,3,Items.EXPERIENCE_BOTTLE,Items.EXPERIENCE_BOTTLE,Items.GOLDEN_APPLE);add(f,4,ItemsTC.thaumiumPick,ItemsTC.thaumiumAxe,ItemsTC.thaumiumHoe);add(f,5,Items.ENCHANTED_GOLDEN_APPLE,BlocksTC.saplingSilverwood.asItem(),item("curio_knowledge"));
        for(Aspect aspect:List.of(Aspect.AIR,Aspect.EARTH,Aspect.FIRE,Aspect.WATER,Aspect.ORDER,Aspect.ENTROPY))m.add(new Trade(1,ThaumcraftApiHelper.makeCrystal(aspect)));
        // These two old raw metadata values never contained Potion NBT in Minecraft 1.12.
        add(m,2,Items.POTION,Items.POTION);m.add(new Trade(2,ThaumcraftApiHelper.makeCrystal(Aspect.FLUX)));add(m,3,Items.EXPERIENCE_BOTTLE,Items.EXPERIENCE_BOTTLE);m.add(new Trade(3,ThaumcraftApiHelper.makeCrystal(Aspect.AURA)));add(m,3,Items.GOLDEN_APPLE);add(m,4,ItemsTC.clothBoots,ItemsTC.clothChest,ItemsTC.clothLegs);add(m,5,Items.ENCHANTED_GOLDEN_APPLE,ItemsTC.pechWand,item("curio_knowledge"),ItemsTC.amuletVis);
        add(f,5,Items.TOTEM_OF_UNDYING);
        for(DyeColor color:List.of(DyeColor.BLACK,DyeColor.RED,DyeColor.GREEN,DyeColor.BROWN,DyeColor.BLUE,DyeColor.PURPLE,DyeColor.CYAN,DyeColor.LIGHT_GRAY,DyeColor.GRAY,DyeColor.PINK,DyeColor.LIME,DyeColor.YELLOW,DyeColor.LIGHT_BLUE,DyeColor.MAGENTA,DyeColor.ORANGE))add(a,1,BlocksTC.candles.get(color).asItem());
        add(a,2,Items.GHAST_TEAR);add(f,2,Items.COMPASS);a.add(new Trade(2,book(level,Enchantments.POWER)));add(a,3,Items.EXPERIENCE_BOTTLE,Items.EXPERIENCE_BOTTLE,Items.GOLDEN_APPLE);add(a,4,ItemsTC.eldritchEye,Items.ENCHANTED_GOLDEN_APPLE);add(f,4,Items.SPECTRAL_ARROW);add(a,5,ItemsTC.ringApprentice);a.add(new Trade(5,book(level,Enchantments.FLAME)));a.add(new Trade(5,book(level,Enchantments.INFINITY)));add(a,5,item("curio_knowledge"));
        install(0,f);install(1,m);install(2,a);
    }
    private static void install(int type,List<Trade> rows){var current=new ArrayList<>(EntityPech.tradeInventory.getOrDefault(type,List.of()));current.addAll(rows);EntityPech.tradeInventory.put(type,current);owned.put(type,rows);}
    // BETA26 never registers eldritchEye. Its tier-4 row is an empty ItemStack;
    // retain that draw in the table instead of changing the other trade probabilities.
    private static void add(List<Trade> rows,int tier,Item...items){for(Item item:items)rows.add(new Trade(tier,item==null?ItemStack.EMPTY:new ItemStack(item)));}
    public static Item item(String path){ResourceLocation key=ResourceLocation.fromNamespaceAndPath("thaumcraft",path);if(!BuiltInRegistries.ITEM.containsKey(key))throw new IllegalStateException("Missing real Pech dependency "+key);return BuiltInRegistries.ITEM.get(key);}
    private static ItemStack book(Level level,ResourceKey<Enchantment> key){ItemStack stack=new ItemStack(Items.ENCHANTED_BOOK);stack.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key),1);return stack;}
}
