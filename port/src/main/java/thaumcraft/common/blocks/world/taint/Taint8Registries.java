package thaumcraft.common.blocks.world.taint;
import java.util.*;
import net.minecraft.world.level.block.Block;
import thaumcraft.api.blocks.BlocksTC;
/** Root-owned ConfigBlocks consumes this factory; block items remain its ordinary native BlockItems. */
public final class Taint8Registries {
    private Taint8Registries(){}
    public static Map<String,Block> createBlocks(){var map=new LinkedHashMap<String,Block>();map.put("taint_crust",BlocksTC.taintCrust=new BlockTaint(BlockTaint.Kind.CRUST));map.put("taint_soil",BlocksTC.taintSoil=new BlockTaint(BlockTaint.Kind.SOIL));map.put("taint_rock",BlocksTC.taintRock=new BlockTaint(BlockTaint.Kind.ROCK));map.put("taint_geyser",BlocksTC.taintGeyser=new BlockTaint(BlockTaint.Kind.GEYSER));map.put("taint_log",BlocksTC.taintLog=new BlockTaintLog());map.put("taint_feature",BlocksTC.taintFeature=new BlockTaintFeature());return map;}
}
