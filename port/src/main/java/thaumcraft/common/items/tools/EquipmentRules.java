package thaumcraft.common.items.tools;

import java.util.Set;

/** Source arithmetic separated from the native world bridge. */
public final class EquipmentRules {
    /** ConfigBlocks constructors with Material.WOOD, including inherited/device materials.
     * The extra light_gray spelling is the native flattened counterpart of source silver.
     */
    private static final Set<String> SOURCE_WOOD_BLOCKS = Set.of(
        "arcane_workbench", "arcane_workbench_charger", "research_table", "centrifuge", "alembic",
        "hungry_chest", "bellows", "levitator", "arcane_ear", "arcane_ear_toggle", "vis_generator",
        "table_wood", "loot_crate_common", "loot_crate_uncommon", "loot_crate_rare",
        "log_greatwood", "log_silverwood", "plank_greatwood", "plank_silverwood",
        "stairs_greatwood", "stairs_silverwood", "slab_greatwood", "slab_silverwood",
        "slab_double_greatwood", "slab_double_silverwood", "banner_crimson_cult",
        "banner_white", "banner_orange", "banner_magenta", "banner_light_blue", "banner_yellow",
        "banner_lime", "banner_pink", "banner_gray", "banner_silver", "banner_light_gray", "banner_cyan",
        "banner_purple", "banner_blue", "banner_brown", "banner_green", "banner_red", "banner_black"
    );
    private EquipmentRules() {}
    public static boolean crusherRejectsSourceWood(String namespace, String path) {
        return "thaumcraft".equals(namespace) && SOURCE_WOOD_BLOCKS.contains(path);
    }
    public static double vacuumComponent(double current,double normalized,boolean vertical){
        return Math.max(-.25,Math.min(.25,current-normalized*.3+(vertical?.1:0)));
    }
    public static double swordLift(double velocity){
        if(velocity<0)velocity/=1.2F;
        velocity+=.08F;
        return velocity>.5?.2F:velocity;
    }
    public static double grappleDivisor(double distance){return Math.max(1E-9,distance<8?distance*(8-distance):distance)*5;}
    /** a/b loop order and even the two equivalent UP/DOWN alternate planes match TC6. */
    public static int[] shovelOffset(int orientation,int face,int yawQuarter,int a,int b){
        int x=0,y=0,z=0;
        if(orientation==1 || orientation==2 && face<=1){
            y=b;
            if(face<=1){if(yawQuarter!=0 && yawQuarter!=2)z=a;else x=a;}
            else if(face<=3)z=a;else x=a;
        } else if(orientation==2){z=b;x=a;}
        else if(face<=1){x=a;z=b;}
        else if(face<=3){x=a;y=b;}
        else{z=a;y=b;}
        return new int[]{x,y,z};
    }
    public static int[] sanitySegments(int permanent,int normal,int temporary){
        float total=permanent+normal+temporary,mod=total>100?100/total:1;
        return new int[]{(int)((100-Math.min(100,total))/100*48),(int)(temporary/100F*48*mod),(int)(normal/100F*48*mod)};
    }
}
