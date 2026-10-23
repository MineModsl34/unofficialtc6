package thaumcraft.api.research;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import thaumcraft.common.lib.research.LegacyScanIdentity;

public class ScanBlock implements IScanThing {
    String research;
    Block[] blocks;

    public ScanBlock(Block block) {
        this(LegacyScanIdentity.blockResearchKey(block), block);
    }

    public ScanBlock(String research, Block... blocks) {
        this.research = research;
        this.blocks = blocks;
        // This constructor appends item handlers BEFORE the caller appends the block handler.
        for (Block block : blocks) ScanningManager.addScannableThing(new ScanItem(research, new ItemStack(block)));
    }

    @Override
    public boolean checkThing(Player player, Object object) {
        if (object instanceof BlockPos pos) {
            for (Block block : blocks) {
                if (player.level().getBlockState(pos).getBlock() == block) return true;
            }
        }
        return false;
    }

    @Override
    public String getResearchKey(Player player, Object object) { return research; }
}
