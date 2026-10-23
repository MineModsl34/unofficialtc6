package thaumcraft.client.gui.model;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ResearchPageImageTest {
    @Test void sourceSevenFieldsAndFloatTruncationAreRetained() {
        var image = ResearchPageImage.parse("thaumcraft:textures/gui/research3.png:16:32:255:129:0.5");
        assertNotNull(image); assertEquals(16, image.x()); assertEquals(32, image.y());
        assertEquals(127, image.drawnWidth()); assertEquals(64, image.drawnHeight());
        assertEquals("thaumcraft:textures/gui/research3.png", image.texture().toString());
    }
    @Test void rejectsSourceNonfittingAndUnsafeModernGeometryWithoutTextureFetch() {
        for (String raw : new String[]{null, "missing", "minecraft:outside:0:0:16:16:1", "thaumcraft:textures/a.jpg:0:0:16:16:1",
            "thaumcraft:textures/a.png:0:0:141:10:1", "thaumcraft:textures/a.png:0:0:140:209:1",
            "thaumcraft:textures/a.png:0:0:140:208:NaN", "thaumcraft:textures/a.png:0:0:140:208:Infinity",
            "thaumcraft:textures/a.png:0:0:140:208:0", "thaumcraft:textures/a.png:-1:0:140:208:1",
            "thaumcraft:textures/a.png:0:0:-140:208:1", "thaumcraft:textures/a.png:0:0:2147483647:208:1",
            "thaumcraft:textures/a.png:0:0:140:208:1:extra"}) assertNull(ResearchPageImage.parse(raw), String.valueOf(raw));
        assertNotNull(ResearchPageImage.parse("thaumcraft:textures/a.png:0:0:140:208:1"));
    }
    @Test void originalEnglishAndRussianImageDescriptorsFitAndReferenceImportedPng() throws Exception {
        int checked = 0;
        for (String language : new String[]{"en_us", "ru_ru"}) {
            try (var stream = getClass().getResourceAsStream("/assets/thaumcraft/lang/" + language + ".json")) {
                assertNotNull(stream);
                var translations = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                for (var translation : translations.entrySet()) {
                    var matches = Pattern.compile("<IMG>(.*?)</IMG>").matcher(translation.getValue().getAsString());
                    while (matches.find()) {
                        var image = ResearchPageImage.parse(matches.group(1));
                        assertNotNull(image, language + " " + translation.getKey());
                        String path = image.texture().getPath();
                        if (image.texture().getNamespace().equals("thaumcraft") && path.startsWith("textures/items/"))
                            path = "textures/item/" + path.substring("textures/items/".length());
                        assertNotNull(getClass().getResource("/assets/" + image.texture().getNamespace() + "/" + path));
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked > 5, "Verify actual source illustration corpus, not only artificial descriptors");
    }
}
