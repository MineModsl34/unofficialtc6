package thaumcraft.client.lib;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.client.fx.particles.FXToolParticle;
import thaumcraft.common.lib.enchantment.ToolMiningRules;
import thaumcraft.common.lib.events.ToolEvents;
import thaumcraft.common.lib.network.fx.PacketFXScanSource;
import thaumcraft.common.lib.network.fx.PacketFXSlash;

public final class ToolClientPackets {
    private ToolClientPackets() {}
    public static void slash(PacketFXSlash packet) {
        var world = Minecraft.getInstance().level; if (world == null) return;
        var source = world.getEntity(packet.source()); var target = world.getEntity(packet.target());
        if (source != null && target != null) Minecraft.getInstance().particleEngine.add(FXToolParticle.slash(world,
            source.getX(), source.getBoundingBox().minY + source.getBbHeight()/2F, source.getZ(),
            target.getX(), target.getBoundingBox().minY + target.getBbHeight()/2F, target.getZ()));
    }
    public static void scan(PacketFXScanSource packet) {
        var world = Minecraft.getInstance().level; if (world == null) return;
        int range = ToolMiningRules.soundingRange(packet.rank()); var positions = new LinkedHashSet<BlockPos>();
        for (int x = -range; x <= range; x++) for (int y = -range; y <= range; y++) for (int z = -range; z <= range; z++) {
            BlockPos pos = packet.pos().offset(x, y, z);
            if (world.hasChunkAt(pos) && ToolEvents.isOre(world.getBlockState(pos))) positions.add(pos);
        }
        while (!positions.isEmpty()) {
            BlockPos start = positions.iterator().next(); positions.remove(start); BlockState state = world.getBlockState(start);
            var group = new ArrayList<BlockPos>(); var pending = new ArrayDeque<BlockPos>(); group.add(start); pending.add(start);
            while (!pending.isEmpty()) {
                BlockPos cursor = pending.removeFirst();
                for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                    BlockPos next = cursor.offset(x, y, z);
                    if (positions.contains(next) && world.getBlockState(next).equals(state)) { positions.remove(next); pending.add(next); group.add(next); }
                }
            }
            double x = 0, y = 0, z = 0;
            for (BlockPos pos : group) { x += pos.getX() + .5; y += pos.getY() + .5; z += pos.getZ() + .5; }
            x /= group.size(); y /= group.size(); z /= group.size();
            int delay = (int)(Math.sqrt(packet.pos().distToCenterSqr(x, y, z)) * 3);
            Minecraft.getInstance().particleEngine.add(FXToolParticle.scan(world, x, y, z, color(state), delay));
        }
    }
    private static int color(BlockState state) {
        String[] names = {"iron", "coal", "redstone", "gold", "lapis", "diamond", "emerald", "quartz", "silver", "tin", "copper", "amber", "cinnabar"};
        int[] colors = {14200723,1052688,16711680,16576075,1328572,6155509,1564002,15064789,14342653,15724539,16620629,16626469,10159368};
        for (int index = 0; index < names.length; index++) if (state.is(TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores/" + names[index])))) return colors[index];
        return 12632256;
    }
}
