package thaumcraft.client.gui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import thaumcraft.client.gui.model.ResearchTextLayout.Element;
import thaumcraft.client.gui.model.ResearchTextLayout.Kind;
import thaumcraft.client.gui.model.ResearchTextLayout.TokenKind;

class ResearchTextLayoutTest {
    @Test void sourceMarkersAndSlashVariantsPreserveOrderAndRawLocalizedText() {
        var parsed = ResearchTextLayout.parse("§oOne<BR>two<BR/>three<LINE>four<LINE/><DIV>five<DIV/><PAGE>six<PAGE/>");
        assertEquals(List.of(TokenKind.TEXT, TokenKind.PARAGRAPH, TokenKind.TEXT, TokenKind.PARAGRAPH,
            TokenKind.TEXT, TokenKind.LINE, TokenKind.TEXT, TokenKind.LINE, TokenKind.DIVIDER,
            TokenKind.TEXT, TokenKind.DIVIDER, TokenKind.PAGE, TokenKind.TEXT, TokenKind.PAGE), parsed.tokens().stream().map(token -> token.kind()).toList());
        assertEquals("§oOne", parsed.tokens().getFirst().value());
        assertEquals("six", parsed.tokens().get(12).value());
        assertEquals(0, parsed.deferredImages()); assertFalse(parsed.unknownMarkup());
    }
    @Test void imagesAreExplicitlyDeferredWithoutInventedTextureParsingOrFetch() {
        var parsed = ResearchTextLayout.parse("left<IMG>unknown:outside.png:0:0:999999:999999:Infinity</IMG>right");
        assertEquals(List.of(TokenKind.TEXT, TokenKind.IMAGE_DEFERRED, TokenKind.TEXT), parsed.tokens().stream().map(token -> token.kind()).toList());
        assertEquals(1, parsed.deferredImages()); assertFalse(parsed.unknownMarkup());
        assertEquals("unknown:outside.png:0:0:999999:999999:Infinity", parsed.tokens().get(1).value());
    }
    @Test void malformedImageAndUnknownMarkupAreExplicitRatherThanSilentlyTranslated() {
        var broken = ResearchTextLayout.parse("before<IMG>unfinished");
        assertEquals(1, broken.deferredImages()); assertTrue(broken.unknownMarkup());
        var unknown = ResearchTextLayout.parse("<COLOR>text</COLOR><br>");
        assertTrue(unknown.unknownMarkup());
        assertEquals("<COLOR>text</COLOR><br>", unknown.tokens().getFirst().value());
    }
    @Test void parserIsBoundedAndReturnedTokensCannotAliasCallerStorage() {
        assertThrows(IllegalArgumentException.class, () -> ResearchTextLayout.parse("a".repeat(ResearchTextLayout.MAX_TEXT_CHARS + 1)));
        assertThrows(IllegalArgumentException.class, () -> ResearchTextLayout.parse("<BR>".repeat(ResearchTextLayout.MAX_ELEMENTS + 1)));
        var parsed = ResearchTextLayout.parse("a");
        assertThrows(UnsupportedOperationException.class, () -> parsed.tokens().clear());
        assertEquals(List.of(), ResearchTextLayout.parse("").tokens());
        assertEquals("<".repeat(ResearchTextLayout.MAX_TEXT_CHARS),
            ResearchTextLayout.parse("<".repeat(ResearchTextLayout.MAX_TEXT_CHARS)).tokens().getFirst().value());
    }
    @Test void twoSourcePageBudgetsAndTextOverflowProduceOrderedPagesWithoutTrailingEmpty() {
        List<Element<String>> elements = new ArrayList<>();
        for (int i = 0; i < 4; i++) elements.add(text("line" + i, 9, false));
        var pages = ResearchTextLayout.paginate(elements, 18, 27, 9);
        assertEquals(2, pages.size());
        assertEquals(List.of("line0", "line1"), pages.get(0).elements().stream().map(Element::value).toList());
        assertEquals(List.of("line2", "line3"), pages.get(1).elements().stream().map(Element::value).toList());
    }
    @Test void explicitSourcePageKeepsEmptyPrecedingPageButNotAnUnusedTrailingPage() {
        Element<String> page = new Element<>(Kind.PAGE, null, 0, false);
        var pages = ResearchTextLayout.paginate(List.of(page, text("next", 9, false), page), 182, 210, 9);
        assertEquals(2, pages.size()); assertTrue(pages.getFirst().elements().isEmpty());
        assertEquals("next", pages.getLast().elements().getFirst().value());
        assertTrue(ResearchTextLayout.paginate(List.<Element<String>>of(), 182, 210, 9).isEmpty());
    }
    @Test void paragraphUsesOriginalDifferentDrawingAndBudgetIntegerRounding() {
        Element<String> paragraph = text("p", 9, true);
        assertEquals(14, paragraph.drawingAdvance()); // y + (int)(9*.66)
        var pages = ResearchTextLayout.paginate(List.of(paragraph, text("next", 9, false)), 23, 210, 9);
        assertEquals(2, pages.size()); // remaining23-9=14 -> (int)(14-5.94)=8, below line9.
        assertEquals("p", pages.getFirst().elements().getFirst().value());
        assertEquals("next", pages.getLast().elements().getFirst().value());
    }
    @Test void sourceImageFifoCanMoveAfterLaterTextRatherThanForceImmediateOverflow() {
        Element<String> divider = new Element<>(Kind.DIVIDER, null, 8, false);
        var pages = ResearchTextLayout.paginate(List.of(text("one", 9, false), divider, text("two", 9, false)), 16, 210, 1);
        assertEquals(2, pages.size());
        assertEquals(List.of(text("one", 9, false), text("two", 9, false)), pages.getFirst().elements());
        assertEquals(List.of(divider), pages.getLast().elements());
    }
    @Test void validIllustrationCarriesSourceDescriptorAndPendingQueueSurvivesExplicitPage() {
        String raw = "thaumcraft:textures/gui/research1.png:0:0:256:128:0.5";
        var parsed = ResearchTextLayout.parse("before<IMG>" + raw + "</IMG>after");
        assertEquals(TokenKind.IMAGE, parsed.tokens().get(1).kind()); assertEquals(0, parsed.deferredImages());
        var descriptor = ResearchPageImage.parse(raw);
        Element<String> image = new Element<>(Kind.IMAGE, null, descriptor.drawnHeight() + 2, false, descriptor);
        var pages = ResearchTextLayout.paginate(List.of(text("first", 9, false), image,
            new Element<>(Kind.PAGE, null, 0, false), text("next", 9, false)), 20, 210, 9);
        assertEquals(List.of(text("first", 9, false)), pages.getFirst().elements());
        assertEquals(List.of(image, text("next", 9, false)), pages.getLast().elements());
    }
    @Test void layoutCopiesInputsAndRefusesUnboundedOrImpossibleLayouts() {
        List<Element<String>> input = new ArrayList<>(List.of(text("one", 9, false)));
        var pages = ResearchTextLayout.paginate(input, 182, 210, 9); input.clear();
        assertEquals(1, pages.getFirst().elements().size());
        assertThrows(UnsupportedOperationException.class, () -> pages.clear());
        assertThrows(UnsupportedOperationException.class, () -> pages.getFirst().elements().clear());
        assertThrows(IllegalArgumentException.class, () -> ResearchTextLayout.paginate(List.of(text("too tall", 211, false)), 182, 210, 9));
        assertThrows(IllegalArgumentException.class, () -> ResearchTextLayout.paginate(List.<Element<String>>of(), 1, 210, 9));
        List<Element<String>> tooManyPages = new ArrayList<>();
        for (int i = 0; i <= ResearchTextLayout.MAX_PAGES; i++) tooManyPages.add(new Element<>(Kind.PAGE, null, 0, false));
        assertThrows(IllegalArgumentException.class, () -> ResearchTextLayout.paginate(tooManyPages, 182, 210, 9));
    }
    private static Element<String> text(String value, int height, boolean paragraph) { return new Element<>(Kind.TEXT, value, height, paragraph); }
}
