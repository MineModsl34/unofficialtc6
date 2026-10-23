package thaumcraft.client.lib.warp;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SourceSonicMeshTest {
    @Test void originalHemisphereHasNinetyOneVerticesAndOneHundredSixtyFacesWithSourceUvInset() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/thaumcraft/models/obj/hemis.obj")) {
            assertNotNull(stream);
            var mesh = SourceSonicMesh.read(new InputStreamReader(stream, StandardCharsets.UTF_8));
            assertEquals(91, mesh.vertexCount()); assertEquals(160, mesh.triangles().size());
            var first = mesh.triangles().getFirst();
            assertEquals(0, first.a().x()); assertEquals(2, first.a().z());
            assertEquals(0.5005F, first.a().u(), 0.00001);
            assertEquals(0.4995F, first.a().v(), 0.00001);
            for (var triangle : mesh.triangles()) {
                assertEquals(1, Math.sqrt(triangle.nx()*triangle.nx()+triangle.ny()*triangle.ny()+triangle.nz()*triangle.nz()), 0.00001);
            }
        }
    }
    @Test void sourceRippleFramesAdvanceOneThroughFifteenAndClamp() {
        assertEquals(1, SourceSonicMesh.frame(0, 0, 10));
        assertEquals(8, SourceSonicMesh.frame(5, 0, 10));
        assertEquals(15, SourceSonicMesh.frame(10, 0, 10));
        assertEquals(15, SourceSonicMesh.frame(20, 0.5F, 10));
    }
    @Test void nonFiniteOrNonSourceTopologyFailsExplicitly() {
        assertThrows(java.io.IOException.class, () -> SourceSonicMesh.read(new StringReader("v NaN 0 0")));
        assertThrows(java.io.IOException.class, () -> SourceSonicMesh.read(new StringReader("v 0 0 0")));
    }
}
