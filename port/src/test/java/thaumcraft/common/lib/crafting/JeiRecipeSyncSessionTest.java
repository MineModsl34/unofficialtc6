package thaumcraft.common.lib.crafting;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import thaumcraft.common.lib.network.misc.PacketSyncJeiRecipes;

/** Opaque byte assembly only; no RecipeHolder codec or item registry bootstrap. */
class JeiRecipeSyncSessionTest {
    @Test void unownedOrWrongConnectionCannotPublishData() {
        var session = new JeiRecipeSyncSession();
        var packet = chunk(1, 0, 1, 1);
        assertNull(session.accept(null, packet));
        assertFalse(session.end(null));
        Object first = new String("equal"), second = new String("equal");
        session.begin(first);
        assertNull(session.accept(second, packet));
        assertFalse(session.end(second));
        assertEquals(0, session.revision());
        assertNotNull(session.accept(first, packet));
        assertEquals(1, session.revision());
    }
    @Test void completeChunksAreConcatenatedByIndexAndPartialReloadNeverPublishes() {
        var session = new JeiRecipeSyncSession(); Object owner = new Object(); session.begin(owner);
        assertNotNull(session.accept(owner, chunk(1, 0, 1, 1)));
        assertNull(session.accept(owner, chunk(2, 2, 3, 4)));
        assertNull(session.accept(owner, chunk(2, 0, 3, 2)));
        assertEquals(1, session.revision());
        var complete = session.accept(owner, chunk(2, 1, 3, 3));
        assertEquals(3, complete.size());
        for (int index = 0; index < 3; index++) assertArrayEquals(new byte[]{(byte) (index + 2)}, complete.get(index));
        assertEquals(2, session.revision());
        assertNull(session.accept(owner, chunk(1, 0, 1, 1)));
        assertNull(session.accept(owner, chunk(2, 1, 3, 3)));
    }
    @Test void newerIncompleteRevisionSupersedesPendingAndCannotRollBack() {
        var session = new JeiRecipeSyncSession(); Object owner = new Object(); session.begin(owner);
        assertNull(session.accept(owner, chunk(2, 0, 2, 2)));
        assertNull(session.accept(owner, chunk(3, 1, 2, 4)));
        assertNull(session.accept(owner, chunk(2, 1, 2, 99)));
        var complete = session.accept(owner, chunk(3, 0, 2, 3));
        assertArrayEquals(new byte[]{3}, complete.get(0));
        assertArrayEquals(new byte[]{4}, complete.get(1));
        assertEquals(3, session.revision());
    }
    @Test void repeatedAndInconsistentPendingChunksFailWithoutReplacingAcceptedRevision() {
        var session = new JeiRecipeSyncSession(); Object owner = new Object(); session.begin(owner);
        session.accept(owner, chunk(1, 0, 1, 1));
        session.accept(owner, chunk(2, 0, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> session.accept(owner, chunk(2, 0, 2, 2)));
        assertThrows(IllegalArgumentException.class, () -> session.accept(owner, chunk(2, 1, 3, 3)));
        assertEquals(1, session.revision());
        assertNotNull(session.accept(owner, chunk(3, 0, 1, 3)));
        assertEquals(3, session.revision());
    }
    @Test void sameOwnerLoginIsIdempotentAndDelayedOldLogoutDoesNotClearNewOwner() {
        var session = new JeiRecipeSyncSession(); Object first = new Object(), second = new Object();
        session.begin(first); session.accept(first, chunk(9, 0, 1, 9)); session.begin(first);
        assertEquals(9, session.revision());
        session.begin(second); assertEquals(0, session.revision());
        assertNull(session.accept(first, chunk(10, 0, 1, 10)));
        assertFalse(session.end(first));
        assertNotNull(session.accept(second, chunk(1, 0, 1, 1)));
        assertEquals(1, session.revision());
        assertTrue(session.end(second)); assertEquals(0, session.revision());
    }
    @Test void emptyAcceptedCatalogAndByteArraysRemainDetachedFromPacketExports() {
        var session = new JeiRecipeSyncSession(); Object owner = new Object(); session.begin(owner);
        assertTrue(session.accept(owner, new PacketSyncJeiRecipes(1, 0, 1, List.of())).isEmpty());
        var packet = chunk(2, 0, 1, 7);
        var complete = session.accept(owner, packet);
        complete.get(0)[0] = 9;
        assertArrayEquals(new byte[]{7}, packet.recipes().get(0));
        assertThrows(UnsupportedOperationException.class, () -> complete.clear());
    }
    @Test void aggregateRecipeCountCannotExceedBoundAcrossIndividuallyValidChunks() {
        var session = new JeiRecipeSyncSession(); Object owner = new Object(); session.begin(owner);
        var bodies = Collections.nCopies(PacketSyncJeiRecipes.MAX_RECIPES, new byte[]{1});
        assertNull(session.accept(owner, new PacketSyncJeiRecipes(1, 0, 2, bodies)));
        assertThrows(IllegalArgumentException.class, () -> session.accept(owner, chunk(1, 1, 2, 2)));
        assertEquals(0, session.revision());
    }
    @Test void aggregateSnapshotBytesCannotExceedBoundAcrossIndividuallyValidChunks() {
        var session = new JeiRecipeSyncSession(); Object owner = new Object(); session.begin(owner);
        session.accept(owner, chunk(1, 0, 1, 1));
        int bodySize = PacketSyncJeiRecipes.MAX_CHUNK_BYTES - 24;
        int chunks = PacketSyncJeiRecipes.MAX_SNAPSHOT_BYTES / bodySize + 1;
        byte[] body = new byte[bodySize];
        for (int index = 0; index < chunks - 1; index++)
            assertNull(session.accept(owner, new PacketSyncJeiRecipes(2, index, chunks, List.of(body))));
        assertThrows(IllegalArgumentException.class, () -> session.accept(owner,
            new PacketSyncJeiRecipes(2, chunks - 1, chunks, List.of(body))));
        assertEquals(1, session.revision());
    }
    private static PacketSyncJeiRecipes chunk(long revision, int index, int chunks, int body) {
        return new PacketSyncJeiRecipes(revision, index, chunks, List.of(new byte[]{(byte) body}));
    }
}
