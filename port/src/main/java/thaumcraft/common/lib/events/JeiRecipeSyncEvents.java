package thaumcraft.common.lib.events;

import io.netty.buffer.Unpooled;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.Thaumcraft;
import thaumcraft.common.lib.crafting.CrucibleRecipes;
import thaumcraft.common.lib.crafting.InfusionRecipes;
import thaumcraft.common.lib.crafting.JeiRecipeSnapshotCodec;
import thaumcraft.common.lib.crafting.JeiRecipeRecipients;
import thaumcraft.common.lib.network.misc.PacketSyncJeiRecipes;

/** Runs after aspect-derived/API recipe publication and uses the actual executable server pool. */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class JeiRecipeSyncEvents {
    private record State(long revision, byte[] digest, List<PacketSyncJeiRecipes> packets) {}
    private static final IdentityHashMap<MinecraftServer, State> STATES = new IdentityHashMap<>();
    private JeiRecipeSyncEvents() {}
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void sync(OnDatapackSyncEvent event) {
        publish(event.getPlayerList().getServer(), event.getRelevantPlayers().toList());
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        // Includes public API map replacements and mutable source setters after login.
        if (server.getTickCount() % 20 == 0 && !server.getPlayerList().getPlayers().isEmpty()) publish(server, null);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { STATES.remove(event.getServer()); }

    private static void publish(MinecraftServer server, List<ServerPlayer> requested) {
        if (!server.isSameThread()) throw new IllegalStateException("Recipe publication requires server MAIN");
        List<RecipeHolder<?>> recipes = new ArrayList<>();
        recipes.addAll(CrucibleRecipes.all(server.overworld())); recipes.addAll(InfusionRecipes.all(server.overworld()));
        // Arbitrary add-on subclass callbacks cannot be reconstructed by native metadata.
        // Filter the effective executable pool after API shadowing, never restore a shadowed
        // datapack recipe just because its replacement has an unsupported implementation.
        recipes.removeIf(holder -> !JeiRecipeSnapshotCodec.isSupported(holder));
        recipes.sort(java.util.Comparator.comparing(holder -> holder.id().toString()));
        if (recipes.size() > PacketSyncJeiRecipes.MAX_RECIPES) throw new IllegalArgumentException("Too many API recipes");
        List<byte[]> encoded = new ArrayList<>();
        MessageDigest hash;
        try { hash = MessageDigest.getInstance("SHA-256"); } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        int totalBytes = 0;
        for (var recipe : recipes) {
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
            try {
                JeiRecipeSnapshotCodec.encode(buffer, recipe);
                if (buffer.readableBytes() > PacketSyncJeiRecipes.MAX_CHUNK_BYTES - 24) throw new IllegalArgumentException("API recipe too large: " + recipe.id());
                byte[] body = new byte[buffer.readableBytes()]; buffer.readBytes(body);
                totalBytes = Math.addExact(totalBytes, body.length);
                if (totalBytes > PacketSyncJeiRecipes.MAX_SNAPSHOT_BYTES) throw new IllegalArgumentException("API recipe catalog too large");
                hash.update(java.nio.ByteBuffer.allocate(4).putInt(body.length).array()); hash.update(body); encoded.add(body);
            } finally { buffer.release(); }
        }
        byte[] digest = hash.digest(); State previous = STATES.get(server);
        boolean changed = previous == null || !Arrays.equals(previous.digest, digest);
        State current = previous;
        if (changed) {
            long revision = previous == null ? 1 : Math.incrementExact(previous.revision);
            List<List<byte[]>> groups = new ArrayList<>(); List<byte[]> group = new ArrayList<>(); int size = 20;
            for (byte[] body : encoded) {
                if (size + body.length + 4 > PacketSyncJeiRecipes.MAX_CHUNK_BYTES) { groups.add(group); group = new ArrayList<>(); size = 20; }
                group.add(body); size += body.length + 4;
            }
            groups.add(group);
            if (groups.size() > PacketSyncJeiRecipes.MAX_CHUNKS) throw new IllegalArgumentException("Too many recipe chunks");
            List<PacketSyncJeiRecipes> packets = new ArrayList<>();
            for (int i = 0; i < groups.size(); i++) packets.add(new PacketSyncJeiRecipes(revision, i, groups.size(), groups.get(i)));
            current = new State(revision, digest, List.copyOf(packets)); STATES.put(server, current);
        }
        // OnDatapackSync runs before a logging-in player is inserted into players.
        // A first/changed snapshot must reach that explicit owner as well as existing players.
        var recipients = JeiRecipeRecipients.select(changed, server.getPlayerList().getPlayers(), requested);
        for (var player : recipients) for (var packet : current.packets) PacketDistributor.sendToPlayer(player, packet);
    }
}
