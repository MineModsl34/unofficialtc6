package thaumcraft.client.gui.model;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

/** Bounded TC6 parsePages: native 140px wrapping is supplied by the Screen. Images, lines
 * and dividers enter the source pending-image FIFO and are inserted after each text operation
 * only when they fit. Invalid/unsafe IMG remains a readable notice. Modern Font is adapted.
 */
public final class ResearchTextLayout {
    public static final int PAGE_WIDTH = 140;
    public static final int FIRST_PAGE_HEIGHT = 182;
    public static final int PAGE_HEIGHT = 210;
    public static final int MAX_TEXT_CHARS = 131_072;
    public static final int MAX_ELEMENTS = 8192;
    public static final int MAX_PAGES = 512;

    private ResearchTextLayout() {}
    public enum TokenKind { TEXT, PARAGRAPH, PAGE, LINE, DIVIDER, IMAGE, IMAGE_DEFERRED }
    public record Token(TokenKind kind, String value) {
        public Token { Objects.requireNonNull(kind); Objects.requireNonNull(value); }
    }
    public record Parsed(List<Token> tokens, int deferredImages, boolean unknownMarkup) {
        public Parsed { tokens = List.copyOf(tokens); }
    }
    public enum Kind { TEXT, BLANK, PAGE, LINE, DIVIDER, IMAGE, IMAGE_DEFERRED }
    /** paragraphEnd uses source integer truncation for pagination and drawing separately. */
    public record Element<T>(Kind kind, T value, int height, boolean paragraphEnd, ResearchPageImage image) {
        public Element(Kind kind, T value, int height, boolean paragraphEnd) { this(kind, value, height, paragraphEnd, null); }
        public Element {
            Objects.requireNonNull(kind);
            if (height < 0 || (kind != Kind.PAGE && height == 0)) throw new IllegalArgumentException("Invalid book element height");
            if (kind == Kind.PAGE && (height != 0 || paragraphEnd)) throw new IllegalArgumentException("Page break has no height");
            if ((kind == Kind.IMAGE) != (image != null)) throw new IllegalArgumentException("Image element requires its descriptor");
        }
        public int drawingAdvance() { return paragraphEnd ? height + (int) (height * .66D) : height; }
    }
    public record Page<T>(List<Element<T>> elements) {
        public Page { elements = List.copyOf(elements); }
    }

    public static Parsed parse(String text) {
        Objects.requireNonNull(text);
        if (text.length() > MAX_TEXT_CHARS) throw new IllegalArgumentException("Research text exceeds bounded page prefix");
        List<Token> result = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        int deferred = 0;
        boolean unknown = false;
        for (int offset = 0; offset < text.length();) {
            if (text.startsWith("<IMG>", offset)) {
                flush(result, plain);
                int close = text.indexOf("</IMG>", offset + 5);
                if (close < 0) {
                    add(result, new Token(TokenKind.IMAGE_DEFERRED, text.substring(offset + 5)));
                    deferred++; unknown = true; break;
                }
                String raw = text.substring(offset + 5, close);
                boolean valid = ResearchPageImage.parse(raw) != null;
                add(result, new Token(valid ? TokenKind.IMAGE : TokenKind.IMAGE_DEFERRED, raw));
                if (!valid) deferred++;
                offset = close + 6; continue;
            }
            if (text.charAt(offset) == '<') {
                // Supported markers are at most7 chars. Never scan the entire remaining text
                // once for every '<' in malformed input (quadratic unclosed-tag behavior).
                int end = -1;
                for (int at = offset + 1; at < Math.min(text.length(), offset + 8); at++) {
                    if (text.charAt(at) == '>') { end = at; break; }
                }
                if (end >= 0) {
                    String marker = text.substring(offset, end + 1);
                    TokenKind kind = switch (marker) {
                        case "<BR>", "<BR/>" -> TokenKind.PARAGRAPH;
                        case "<PAGE>", "<PAGE/>" -> TokenKind.PAGE;
                        case "<LINE>", "<LINE/>" -> TokenKind.LINE;
                        case "<DIV>", "<DIV/>" -> TokenKind.DIVIDER;
                        default -> null;
                    };
                    if (kind != null) {
                        flush(result, plain); add(result, new Token(kind, "")); offset = end + 1; continue;
                    }
                    if (marker.length() > 2 && (Character.isLetter(marker.charAt(1)) || marker.charAt(1) == '/')) unknown = true;
                }
                else if (offset + 1 < text.length() && (Character.isLetter(text.charAt(offset + 1)) || text.charAt(offset + 1) == '/')) unknown = true;
            }
            plain.append(text.charAt(offset++));
        }
        flush(result, plain);
        return new Parsed(result, deferred, unknown);
    }

    /** Literal source image queue: images can move AFTER later text rather than immediately
     * forcing a page break. PAGE keeps pending images; the final drain gets a new 210px budget.
     * Explicit PAGE keeps even an empty preceding page. Limits prevent corrupt infinite loops.
     */
    public static <T> List<Page<T>> paginate(List<Element<T>> input, int firstHeight, int otherHeight, int lineHeight) {
        if (input.size() > MAX_ELEMENTS || firstHeight < lineHeight || otherHeight < lineHeight || lineHeight < 1)
            throw new IllegalArgumentException("Invalid or unbounded book layout");
        List<Page<T>> pages = new ArrayList<>();
        List<Element<T>> current = new ArrayList<>();
        ArrayDeque<Element<T>> pending = new ArrayDeque<>();
        int remaining = firstHeight;
        for (Element<T> element : input) {
            Objects.requireNonNull(element);
            if (element.kind() == Kind.PAGE) {
                appendPage(pages, current); current = new ArrayList<>(); remaining = otherHeight;
            } else {
                if (element.height() > otherHeight) throw new IllegalArgumentException("Book element cannot fit a page");
                boolean image = element.kind() == Kind.LINE || element.kind() == Kind.DIVIDER || element.kind() == Kind.IMAGE;
                if (image) pending.addLast(element);
                else {
                    current.add(element);
                    remaining -= element.height();
                    if (element.paragraphEnd()) remaining = (int) (remaining - element.height() * .66D);
                }
            }
            while (!pending.isEmpty() && remaining >= pending.getFirst().height()) {
                var image = pending.removeFirst(); current.add(image); remaining -= image.height();
            }
            if (remaining < lineHeight && !current.isEmpty()) {
                appendPage(pages, current); current = new ArrayList<>(); remaining = otherHeight;
            }
        }
        if (!current.isEmpty()) appendPage(pages, current);
        current = new ArrayList<>(); remaining = otherHeight;
        while (!pending.isEmpty()) {
            var image = pending.getFirst();
            if (remaining < image.height()) {
                appendPage(pages, current); current = new ArrayList<>(); remaining = otherHeight;
            } else { pending.removeFirst(); current.add(image); remaining -= image.height(); }
        }
        if (!current.isEmpty()) appendPage(pages, current);
        return List.copyOf(pages);
    }

    private static <T> void appendPage(List<Page<T>> pages, List<Element<T>> current) {
        if (pages.size() >= MAX_PAGES) throw new IllegalArgumentException("Research layout exceeds bounded page count");
        pages.add(new Page<>(current));
    }
    private static void add(List<Token> tokens, Token token) {
        if (tokens.size() >= MAX_ELEMENTS) throw new IllegalArgumentException("Research layout exceeds bounded token count");
        tokens.add(token);
    }
    private static void flush(List<Token> result, StringBuilder plain) {
        if (!plain.isEmpty()) { add(result, new Token(TokenKind.TEXT, plain.toString())); plain.setLength(0); }
    }
}
