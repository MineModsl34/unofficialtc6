package thaumcraft.client.lib;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.client.gui.model.ResearchPageProjection;
import thaumcraft.client.gui.model.ResearchReadRetry;
import thaumcraft.common.lib.network.playerdata.PacketSyncProgressToServer;
import thaumcraft.common.lib.network.playerdata.PacketResearchReadToServer;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.common.lib.research.catalog.ResearchClientView;

/** Client MAIN facade for the browser/page and checked actual progression sends.
 * Readiness comes only from an actual owner snapshot applied on the current connection.
 * Integrated catalogs use the accepted SESSION echo, never a direct server revision shortcut.
 * No local grants, flag clearing, requirement payment, optimistic progression or custom ACK.
 */
public final class ClientResearchPresentation {
    private static final ResearchClientView OWNER = new ResearchClientView();
    private static final ResearchReadRetry READ_RETRY = new ResearchReadRetry();

    private ClientResearchPresentation() {}

    static void begin(Connection connection) {
        requireMain();
        OWNER.begin(connection);
        READ_RETRY.reset();
    }

    static void end(Connection connection) {
        requireMain();
        if (OWNER.end(connection)) READ_RETRY.reset();
    }

    /** Called AFTER the actual knowledge handler applied and cleared source POPUP flags. */
    static void knowledgeApplied(Connection connection, CompoundTag receivedSnapshot) {
        requireMain();
        var minecraft = Minecraft.getInstance();
        var listener = minecraft.getConnection();
        if (listener == null || listener.getConnection() != connection || minecraft.player == null) return;
        OWNER.knowledgeApplied(connection, minecraft.player, ThaumcraftCapabilities.getKnowledge(minecraft.player), receivedSnapshot);
    }

    public static ResearchClientView.Snapshot view() {
        requireMain();
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return null;
        return OWNER.capture(ClientResearchCatalogHandlers.acceptedViewForCurrentConnection(), minecraft.player);
    }

    /** Preparation is read-only; submit must revalidate immediately before actual dispatch. */
    public static Prepared prepare(String key) {
        var snapshot = view();
        if (snapshot == null) return null;
        var packet = snapshot.prepare(key);
        return packet == null ? null : new Prepared(snapshot, packet);
    }

    /** Source stage/text/addendum descriptors only; no translation, markup or recipe rendering. */
    public static ResearchPageProjection.View page(String key) {
        var snapshot = view();
        if (snapshot == null) return ResearchPageProjection.project(null, -1, research -> false);
        return ResearchPageProjection.project(snapshot.spec().entriesByKey().get(key), snapshot.storedStage(key), snapshot::knowsStrict);
    }

    /** Dispatches the existing bounded C2S only; no optimistic writes or invented ACK. */
    public static boolean submit(Prepared request) {
        requireMain();
        if (!isCurrent(request)) return false;
        PacketDistributor.sendToServer(request.packet());
        return true;
    }

    /** Reading may retry a silently rate-limited acknowledgement; only real S2C changes flags. */
    public static boolean clearReadFlags(String key) {
        var snapshot = view();
        if (snapshot == null) { READ_RETRY.reset(); return false; }
        if (!(snapshot.hasResearchFlag(key, EnumResearchFlag.PAGE)
            || snapshot.hasResearchFlag(key, EnumResearchFlag.RESEARCH))) return false;
        var availability = snapshot.availability(key);
        if (!availability.known() || !availability.visible()
            || !availability.categoryAccessible()) return false;
        var player = Minecraft.getInstance().player;
        if (player == null || !READ_RETRY.canSend(player, player.tickCount)) return false;
        var reference = new PacketSyncProgressToServer(key, snapshot.storedStage(key), snapshot.digest());
        PacketDistributor.sendToServer(new PacketResearchReadToServer(reference, snapshot.readStateDigest()));
        READ_RETRY.sent(player, player.tickCount);
        return true;
    }

    /** Rechecks the exact accepted catalog, received owner epoch and live exact stage. */
    public static boolean isCurrent(Prepared request) {
        requireMain();
        if (request == null) return false;
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) return false;
        return OWNER.isCurrent(request.snapshot(), ClientResearchCatalogHandlers.acceptedViewForCurrentConnection(), minecraft.player)
            && request.packet().equals(request.snapshot().prepare(request.packet().researchKey()))
            && request.packet().expectedStage() == ThaumcraftCapabilities.getKnowledge(minecraft.player)
                .getResearchStage(request.packet().researchKey());
    }

    public record Prepared(ResearchClientView.Snapshot snapshot, PacketSyncProgressToServer packet) {
        public Prepared {
            Objects.requireNonNull(snapshot, "accepted owner snapshot");
            Objects.requireNonNull(packet, "prepared request");
        }
    }

    private static void requireMain() {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Research presentation requires client MAIN");
    }
}
