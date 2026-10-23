package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Private GameTest arenas and real registered tiles, never dummy endpoints or result injection. */
final class BulkGameplayFixtures {
    private BulkGameplayFixtures() {}
    static void floor(GameTestHelper h) {
        for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }
    static ServerPlayer player(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "TC_Bulk_Owner"));
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 1));
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        player.getAbilities().mayBuild = true;
        return player;
    }
    static <T extends BlockEntity> T tile(GameTestHelper h, BlockPos relative, Block block, Class<T> type) {
        h.setBlock(relative, block);
        var tile = h.getLevel().getBlockEntity(h.absolutePos(relative));
        h.assertTrue(type.isInstance(tile), "registered native tile for " + block + ": " + tile);
        return type.cast(tile);
    }
    static void remove(Entity entity) { if (entity != null && !entity.isRemoved()) entity.discard(); }
    static void research(ServerPlayer player) {
        var knowledge = thaumcraft.api.capabilities.ThaumcraftCapabilities.getKnowledge(player);
        for (var category : thaumcraft.api.research.ResearchCategories.researchCategories.values()) for (var entry : category.research.values()) {
            knowledge.addResearch(entry.getKey()); knowledge.setResearchStage(entry.getKey(), entry.getStages() == null ? 1 : entry.getStages().length + 1);
        }
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.getAbilities().mayBuild = true;
    }
    static ServerPlayer connectedPlayer(GameTestHelper h) {
        var profile = new GameProfile(UUID.randomUUID(), "TC_Bulk_Caster");
        var information = net.minecraft.server.level.ClientInformation.createDefault();
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, information);
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player,
            new net.minecraft.server.network.CommonListenerCookie(profile, 0, information, false, net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE));
        player.setPos(h.absolutePos(new BlockPos(2, 2, 1)).getCenter());
        return player;
    }
    static void removeConnected(ServerPlayer player) {
        thaumcraft.common.lib.capabilities.PlayerDataSync.forget(player);
        try { player.getServer().getPlayerList().remove(player); }
        finally { ((io.netty.channel.embedded.EmbeddedChannel) player.connection.getConnection().channel()).finishAndReleaseAll(); }
    }
}
