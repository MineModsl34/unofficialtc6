package thaumcraft.common.lib.validation;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

/** Finite native caster route; every authored focus enters the ordinary editor. */
public final class TcpCasterScenario {
    public enum Kind { STORAGE, RESEARCH, CRAFT, USE_BLOCK, OUTPUT_PICKUP, SCAN_ITEM, WAIT_MARKER,
        FOCAL_DRAFT, FOCAL_CRAFT, FOCAL_PICKUP, POUCH_INSERT, POUCH_EXTRACT, FOCUS_SELECT,
        FOCUS_REMOVE, EQUIP_GOGGLES, CAST_TARGET, CHECKPOINT }
    public record Ingredient(int slot, String item, int count, String aspect) {}
    public record Action(String id, Kind kind, BlockPos position, String item, int amount,
                         String research, int stage, List<Ingredient> ingredients) {}
    public static final BlockPos CRAFT = TcpAlchemyScenario.CRAFT, WORKBENCH = TcpAlchemyScenario.WORKBENCH;
    public static final BlockPos CRUCIBLE = TcpAlchemyScenario.CRUCIBLE, STORAGE = TcpAlchemyScenario.STORAGE;
    public static final BlockPos STORAGE_PARTNER = TcpAlchemyScenario.STORAGE_PARTNER;
    public static final BlockPos MATERIAL_CHEST = STORAGE, MATERIAL_CHEST_PARTNER = STORAGE_PARTNER;
    public static final BlockPos FOCAL = new BlockPos(264,65,16), TARGET = new BlockPos(268,65,16);
    public static final BlockPos TARGET_DISCOUNTED = TARGET.east(4);
    public static final BlockPos SCAN_ITEM = new BlockPos(262,66,20), LOW = new BlockPos(258,5,32);
    public static final BlockPos HIGH = new BlockPos(258,166,32), FIRE = new BlockPos(258,65,36);
    public static final String FOCUS_NAME = "TCP Touch Fire";
    public static final List<String> PRIMALS = TcpAlchemyScenario.PRIMALS;
    private TcpCasterScenario() {}
    private static Ingredient item(int slot,String id,int count) { return new Ingredient(slot,id.contains(":")?id:"minecraft:"+id,count,""); }
    private static Ingredient crystal(int slot,String aspect,int count) { return new Ingredient(slot,"thaumcraft:crystal_essence",count,aspect); }
    private static Action a(String id,Kind kind,BlockPos pos,String item,int amount,String research,int before,Ingredient... ingredients) {
        return new Action(id,kind,pos,item,amount,research,before,List.of(ingredients));
    }
    private static Action r(String key,int before) { return a("research-"+key.toLowerCase(java.util.Locale.ROOT)+"-"+before,Kind.RESEARCH,WORKBENCH,"",0,key,before); }
    public static int resultingStage(Action action) {
        if(action.stage()<0)return action.research().equals("BASEARTIFICE")?2:1;
        return switch(action.research()) { case "UNLOCKAUROMANCY","BASEAUROMANCY" -> action.stage()==1?2:4;
            case "FOCUSPOUCH","UNLOCKARTIFICE" -> 3; default -> action.stage()+1; };
    }
    public static CompoundTag focusGraph() {
        try { return TagParser.parseTag("{nodes:[{id:0,parent:-1,x:0,y:0,children:[I;1],key:'ROOT'},"
            +"{id:1,parent:0,x:0,y:1,children:[I;2],key:'thaumcraft.TOUCH'},"
            +"{id:2,parent:1,x:0,y:2,children:[I;],key:'thaumcraft.FIRE','setting.power':1,'setting.duration':0}]}"); }
        catch(Exception invalid) { throw new IllegalStateException(invalid); }
    }
    public static List<Action> generateActions() {
        ArrayList<Action> v=new ArrayList<>();
        v.add(a("store-inherited-crystals",Kind.STORAGE,STORAGE,"thaumcraft:crystal_essence",8,"",-1));
        v.add(a("scan-real-piston",Kind.SCAN_ITEM,SCAN_ITEM,"minecraft:piston",1,"!machina",-1));
        v.add(r("UNLOCKAUROMANCY",-1));
        v.add(a("native-deep-discovery",Kind.WAIT_MARKER,LOW,"",1,"m_deepdown",-1));
        v.add(a("native-high-discovery",Kind.WAIT_MARKER,HIGH,"",1,"m_uphigh",-1));
        v.add(r("UNLOCKAUROMANCY",1));
        for(int i=0;i<2;i++) v.add(a("craft-native-resonator-"+i,Kind.CRAFT,WORKBENCH,"thaumcraft:vis_resonator",1,"",-1,
            item(0,"thaumcraft:plate_iron",1),item(1,"quartz",1),crystal(9,"aer",1),crystal(11,"aqua",1)));
        v.add(a("craft-native-caster",Kind.CRAFT,WORKBENCH,"thaumcraft:caster_basic",1,"",-1,
            item(0,"iron_ingot",1),item(1,"iron_ingot",1),item(2,"iron_ingot",1),item(3,"leather",1),
            item(4,"thaumcraft:vis_resonator",1),item(5,"leather",1),item(6,"leather",1),
            item(7,"thaumcraft:thaumometer",1),item(8,"leather",1),crystal(9,"aer",1),crystal(10,"ignis",1),
            crystal(11,"aqua",1),crystal(12,"terra",1),crystal(13,"ordo",1),crystal(14,"perditio",1)));
        v.add(r("UNLOCKAUROMANCY",2)); v.add(r("BASEAUROMANCY",-1));
        for(String aspect:List.of("vitreus","praecantatio","auram")) {
            int count=aspect.equals("vitreus")?20:aspect.equals("praecantatio")?10:5;
            v.add(a("dissolve-focus-"+aspect,Kind.USE_BLOCK,CRUCIBLE,"thaumcraft:crystal_essence",count,aspect,-1,crystal(-1,aspect,count)));
        }
        v.add(a("crucible-native-basic-focus",Kind.USE_BLOCK,CRUCIBLE,"thaumcraft:crystal_essence",1,"ordo",-1,crystal(-1,"ordo",1)));
        v.add(a("pickup-native-basic-focus",Kind.OUTPUT_PICKUP,CRUCIBLE,"thaumcraft:focus_1",1,"",-1));
        v.add(r("BASEAUROMANCY",1));
        v.add(a("craft-native-focal-manipulator",Kind.CRAFT,WORKBENCH,"thaumcraft:wand_workbench",1,"",-1,
            item(0,"thaumcraft:plate_iron",1),item(1,"thaumcraft:slab_arcane_stone",1),item(2,"thaumcraft:plate_iron",1),
            item(3,"thaumcraft:stone_arcane",1),item(4,"thaumcraft:vis_resonator",1),item(5,"thaumcraft:stone_arcane",1),
            item(6,"gold_ingot",1),item(7,"thaumcraft:table_stone",1),item(8,"gold_ingot",1),crystal(11,"aqua",1),crystal(12,"terra",1)));
        v.add(a("place-native-focal-manipulator",Kind.USE_BLOCK,FOCAL.below(),"thaumcraft:wand_workbench",1,"",-1));
        v.add(a("native-fire-discovery",Kind.WAIT_MARKER,FIRE,"",1,"f_onfire",-1)); v.add(r("BASEAUROMANCY",2));
        v.add(r("FOCUSPOUCH",-1)); v.add(r("FOCUSPOUCH",1));
        v.add(r("UNLOCKARTIFICE",-1)); v.add(r("UNLOCKARTIFICE",1)); v.add(r("BASEARTIFICE",-1));
        v.add(a("craft-native-girdle",Kind.CRAFT,CRAFT,"thaumcraft:girdle_mundane",1,"",-1,
            item(1,"leather",1),item(3,"leather",1),item(5,"leather",1),item(7,"thaumcraft:ingot_brass",1)));
        v.add(a("craft-native-focus-pouch",Kind.CRAFT,WORKBENCH,"thaumcraft:focus_pouch",1,"",-1,
            item(0,"leather",1),item(1,"gold_ingot",1),item(2,"leather",1),item(3,"leather",1),
            item(4,"thaumcraft:girdle_mundane",1),item(5,"leather",1),item(6,"leather",1),item(7,"leather",1),item(8,"leather",1)));
        for(int i=0;i<2;i++) v.add(a("craft-native-thaumometer-"+i,Kind.CRAFT,WORKBENCH,"thaumcraft:thaumometer",1,"",-1,
            item(1,"gold_ingot",1),item(3,"gold_ingot",1),item(4,"glass_pane",1),item(5,"gold_ingot",1),item(7,"gold_ingot",1),
            crystal(9,"aer",1),crystal(10,"ignis",1),crystal(11,"aqua",1),crystal(12,"terra",1),crystal(13,"ordo",1),crystal(14,"perditio",1)));
        v.add(a("craft-native-goggles",Kind.CRAFT,WORKBENCH,"thaumcraft:goggles",1,"",-1,
            item(0,"leather",1),item(1,"thaumcraft:ingot_brass",1),item(2,"leather",1),item(3,"leather",1),
            item(5,"leather",1),item(6,"thaumcraft:thaumometer",1),item(7,"thaumcraft:ingot_brass",1),item(8,"thaumcraft:thaumometer",1)));
        v.add(a("gui-native-focal-draft",Kind.FOCAL_DRAFT,FOCAL,"thaumcraft:focus_1",1,"",-1));
        v.add(a("gui-native-focal-craft",Kind.FOCAL_CRAFT,FOCAL,"thaumcraft:focus_1",1,"",-1,crystal(-1,"aversio",1),crystal(-1,"ignis",1)));
        v.add(a("gui-native-focal-pickup",Kind.FOCAL_PICKUP,FOCAL,"thaumcraft:focus_1",1,"",-1));
        v.add(a("native-pouch-insert",Kind.POUCH_INSERT,FOCAL,"thaumcraft:focus_pouch",1,"",-1));
        v.add(a("native-focus-select",Kind.FOCUS_SELECT,FOCAL,"thaumcraft:caster_basic",1,"",-1));
        v.add(a("native-cast-without-goggles",Kind.CAST_TARGET,TARGET,"thaumcraft:caster_basic",1,"plain",-1));
        v.add(a("native-focus-remove",Kind.FOCUS_REMOVE,FOCAL,"thaumcraft:caster_basic",1,"",-1));
        v.add(a("native-pouch-extract",Kind.POUCH_EXTRACT,FOCAL,"thaumcraft:focus_pouch",1,"",-1));
        v.add(a("native-pouch-reinsert",Kind.POUCH_INSERT,FOCAL,"thaumcraft:focus_pouch",1,"",-1));
        v.add(a("native-focus-reselect",Kind.FOCUS_SELECT,FOCAL,"thaumcraft:caster_basic",1,"",-1));
        v.add(a("native-equip-goggles",Kind.EQUIP_GOGGLES,FOCAL,"thaumcraft:goggles",1,"",-1));
        v.add(a("native-cast-with-goggles",Kind.CAST_TARGET,TARGET_DISCOUNTED,"thaumcraft:caster_basic",1,"discounted",-1));
        v.add(a("save-native-casters",Kind.CHECKPOINT,FOCAL,"",0,"",-1));
        return List.copyOf(v);
    }
    public static List<BlockPos> roofPositions() {
        ArrayList<BlockPos> v=new ArrayList<>();
        for(BlockPos target:List.of(TARGET,TARGET_DISCOUNTED))for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)
            v.add(new BlockPos(target.getX()+x,69,target.getZ()+z));
        return List.copyOf(v);
    }
    public static List<BlockPos> newPositions() { ArrayList<BlockPos> v=new ArrayList<>(List.of(FOCAL,FOCAL.above(),FIRE,LOW.below(),HIGH.below()));v.addAll(roofPositions());return List.copyOf(v); }
    public static List<BlockPos> machinePositions() { ArrayList<BlockPos> v=new ArrayList<>(TcpAlchemyScenario.machinePositions());v.addAll(newPositions());return List.copyOf(v); }
}
