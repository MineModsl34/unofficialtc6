package thaumcraft.common.lib.research.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonSyntaxException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import thaumcraft.common.lib.research.catalog.validation.ResearchCatalogNetworkAudit;

class ResearchCatalogAuditTerminalReadTest {
    @TempDir Path directory;

    @Test
    void missingEmptyOrIncompleteFinalJsonWaitsUntilCompleteWithoutAcceptingIt() throws Exception {
        Path file = directory.resolve("server-report.json");
        assertNull(ResearchCatalogNetworkAudit.readTerminalDocument(file, 1024));
        Files.writeString(file, "");
        assertNull(ResearchCatalogNetworkAudit.readTerminalDocument(file, 1024));
        Files.writeString(file, "{\"status\":\"comple");
        assertNull(ResearchCatalogNetworkAudit.readTerminalDocument(file, 1024));
        Files.writeString(file, "{\"status\":\"completed\"}");
        assertEquals("completed", ResearchCatalogNetworkAudit.readTerminalDocument(file, 1024).get("status").getAsString());
    }

    @Test
    void markerReadsRemainStrictAndCompleteWrongJsonTypesAreNotIgnored() throws Exception {
        Path file = directory.resolve("marker.json");
        Files.writeString(file, "{\"schemaVersion\":");
        assertThrows(JsonSyntaxException.class, () -> ResearchCatalogNetworkAudit.readDocument(file, 1024));
        Files.writeString(file, "[]");
        assertThrows(IllegalStateException.class, () -> ResearchCatalogNetworkAudit.readTerminalDocument(file, 1024));
    }

    @Test
    void terminalReadNeverSwallowsSizeOrInvalidUtf8Failures() throws Exception {
        Path file = directory.resolve("server-report.json");
        Files.writeString(file, "{\"status\":\"completed\"}");
        assertThrows(IllegalStateException.class, () -> ResearchCatalogNetworkAudit.readTerminalDocument(file, 4));
        Files.write(file, new byte[] {(byte) 0xC3});
        assertThrows(MalformedInputException.class, () -> ResearchCatalogNetworkAudit.readTerminalDocument(file, 1024));
    }
}
