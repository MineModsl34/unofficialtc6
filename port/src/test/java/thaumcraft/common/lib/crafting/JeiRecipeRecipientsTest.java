package thaumcraft.common.lib.crafting;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JeiRecipeRecipientsTest {
    @Test void firstLoginReceivesChangedCatalogBeforeOnlineInsertion() {
        Object joining = new Object();
        assertEquals(List.of(joining), JeiRecipeRecipients.select(true, List.of(), List.of(joining)));
    }
    @Test void changedCatalogReachesOnlineAndJoiningPlayersOnceByIdentity() {
        String online = new String("same"), joining = new String("same");
        var recipients = JeiRecipeRecipients.select(true, List.of(online), List.of(online, joining));
        assertEquals(2, recipients.size()); assertSame(online, recipients.get(0)); assertSame(joining, recipients.get(1));
    }
    @Test void unchangedPollIsQuietAndAnUnchangedLoginStillReceivesTheCachedCatalog() {
        Object online = new Object(), joining = new Object();
        assertTrue(JeiRecipeRecipients.select(false, List.of(online), null).isEmpty());
        assertEquals(List.of(joining), JeiRecipeRecipients.select(false, List.of(online), List.of(joining)));
    }
}
