package thaumcraft.api.research;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;

/** Native block tags replace the removed Material enum. Tags are rebound on datapack reload. */
public class ScanMaterial implements IScanThing {
    String research;
    TagKey<Block>[] materials;
    public ScanMaterial(TagKey<Block> material) { this("!" + material.location(), material); }
    @SafeVarargs public ScanMaterial(String research, TagKey<Block>... materials) {
        this.research = research; this.materials = materials;
    }
    @Override public boolean checkThing(Player player, Object object) {
        if (object instanceof BlockPos pos) for (TagKey<Block> material : materials)
            if (player.level().getBlockState(pos).is(material)) return true;
        return false;
    }
    @Override public String getResearchKey(Player player, Object object) { return research; }
}
