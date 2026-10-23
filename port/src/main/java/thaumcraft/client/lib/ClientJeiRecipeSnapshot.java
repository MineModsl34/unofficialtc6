package thaumcraft.client.lib;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumcraft.Thaumcraft;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.common.lib.crafting.JeiRecipeSyncSession;
import thaumcraft.common.lib.crafting.JeiRecipeSnapshotCodec;
import thaumcraft.common.lib.network.misc.PacketSyncJeiRecipes;

/** Connection-owned detached metadata. No JEI dependency and no executable client API mutation. */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ClientJeiRecipeSnapshot {
    private static final JeiRecipeSyncSession SESSION = new JeiRecipeSyncSession();
    private static Object connection;
    private static List<RecipeHolder<CrucibleRecipe>> crucible = List.of();
    private static List<RecipeHolder<InfusionRecipe>> infusion = List.of();
    private ClientJeiRecipeSnapshot() {}
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event) {
        requireMain();
        var listener = Minecraft.getInstance().getConnection();
        if (listener != null && listener.getConnection() == event.getConnection()) begin(event.getConnection());
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        requireMain();
        if (event.getConnection() != null && SESSION.end(event.getConnection())) { connection = null; crucible = List.of(); infusion = List.of(); }
    }
    private static void begin(Object actual) {
        if (connection == actual) return;
        SESSION.begin(actual); connection = actual; crucible = List.of(); infusion = List.of();
    }
    public static void accept(PacketSyncJeiRecipes packet, IPayloadContext context) {
        var minecraft = Minecraft.getInstance(); var listener = minecraft.getConnection();
        if (!minecraft.isSameThread()) throw new IllegalStateException("JEI metadata requires client MAIN");
        if (listener == null || listener.getConnection() != context.connection()) return;
        begin(context.connection());
        try {
            List<byte[]> complete = SESSION.accept(connection, packet);
            if (complete == null) return;
            List<RecipeHolder<CrucibleRecipe>> nextCrucible = new ArrayList<>(); List<RecipeHolder<InfusionRecipe>> nextInfusion = new ArrayList<>();
            var ids = new HashSet<net.minecraft.resources.ResourceLocation>();
            for (byte[] body : complete) {
                var buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(body), listener.registryAccess());
                try {
                    var holder = JeiRecipeSnapshotCodec.decode(buffer);
                    if (buffer.isReadable() || !ids.add(holder.id())) throw new IllegalArgumentException("Invalid recipe identity/body");
                    if (holder.value() instanceof CrucibleRecipe recipe) nextCrucible.add(new RecipeHolder<>(holder.id(), recipe));
                    else if (holder.value() instanceof InfusionRecipe recipe) nextInfusion.add(new RecipeHolder<>(holder.id(), recipe));
                    else throw new IllegalArgumentException("Unsupported recipe metadata kind");
                } finally { buffer.release(); }
            }
            crucible = List.copyOf(nextCrucible); infusion = List.copyOf(nextInfusion);
        } catch (RuntimeException invalid) {
            SESSION.end(connection); connection = null; crucible = List.of(); infusion = List.of();
            Thaumcraft.log.error("Rejected server JEI recipe snapshot", invalid);
            context.disconnect(Component.literal("Invalid Thaumcraft recipe metadata."));
        }
    }
    public static boolean ready() { requireMain(); var listener = Minecraft.getInstance().getConnection(); return listener != null && listener.getConnection() == connection && SESSION.revision() > 0; }
    public static long revision() { return ready() ? SESSION.revision() : 0; }
    public static List<RecipeHolder<CrucibleRecipe>> crucible() { return ready() ? crucible : List.of(); }
    public static List<RecipeHolder<InfusionRecipe>> infusion() { return ready() ? infusion : List.of(); }
    private static void requireMain() {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("JEI metadata requires client MAIN");
    }
}
