package com.examprep.document.parser;

import java.util.regex.Pattern;

/**
 * Shared text clean-up so chunk boundaries and citations stay predictable.
 */
final class TextNormaliser {

    private static final Pattern WINDOWS_NEWLINES = Pattern.compile("\r\n?");
    private static final Pattern EXCESS_BLANK_LINES = Pattern.compile("\n{3,}");
    private static final Pattern TRAILING_SPACES = Pattern.compile("[ \t]+\n");
    private static final Pattern HORIZONTAL_SPACE = Pattern.compile("[ \t\u00A0]{2,}");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");

    private TextNormaliser() {
    }

    static String normalise(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = WINDOWS_NEWLINES.matcher(raw).replaceAll("\n");
        text = CONTROL_CHARS.matcher(text).replaceAll("");
        text = HORIZONTAL_SPACE.matcher(text).replaceAll(" ");
        text = TRAILING_SPACES.matcher(text).replaceAll("\n");
        text = EXCESS_BLANK_LINES.matcher(text).replaceAll("\n\n");
        return text.strip();
    }
}
