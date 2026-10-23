package thaumcraft.common.tiles.devices;

/** Side-effect-free source arithmetic shared by runtime and bounded unit tests. */
public final class DeviceRules {
    public static final int[][] PATTERNS = {{0,1,2,3,4,5,6,7,8},{0},{0,1},{0,3},{0,1,3,4},{0,1,2},{0,3,6},{0,1,2,3,4,5},{0,1,3,4,6,7},{0,1,2,3,5,6,7,8}};
    private DeviceRules() {}
    public static int[] urnOffset(int index) { return new int[]{index / 5 % 5 - 2,index / 25 % 3 - 1,index % 5 - 2}; }
    public static int condenserInterval(float latticeCount) { return latticeCount <= 0 ? 0 : Math.max(5,Math.round(600 - Math.min(40,latticeCount) * 15)); }
    public static int condenserCost(int latticeBlocks) { return (int)(4 + Math.sqrt(latticeBlocks)); }
    public static int compare(float ratio) { return (int)(ratio * 14) + (ratio > 0 ? 1 : 0); }
    public static float urnVisRequest(int amount) { return Math.min((1000 - amount) / 1000F,.1F); }
    /** Finite OreDictionary bridge, not arbitrary tag equality. Broad modern mineral groups
     * contain different source materials. Old logWood/plankWood/wool wildcard groups do not.
     * Leaf material tags and the listed old wildcard families remain extensible to addons. */
    public static boolean baseOreTag(String namespace,String path) {
        if (namespace.equals("minecraft")) return switch (path) {
            case "logs", "planks", "wooden_slabs", "wooden_stairs", "wooden_fences", "wooden_doors", "saplings", "leaves", "wool" -> true;
            default -> false;
        };
        if (!namespace.equals("c")) return false;
        if (java.util.Set.of("logs", "planks", "wool", "chests", "glass_blocks", "glass_panes", "sands", "sandstone_blocks", "slimeballs", "ender_pearls", "bones", "gunpowders", "strings", "nether_stars", "leathers", "feathers", "eggs").contains(path)) return true;
        int slash = path.indexOf('/');
        return slash > 0 && slash < path.length() - 1 && path.indexOf('/',slash + 1) < 0
            && java.util.Set.of("ingots", "nuggets", "ores", "gems", "dusts", "storage_blocks", "raw_materials", "rods", "plates", "gears", "wires", "bricks", "dyes").contains(path.substring(0,slash));
    }
}
