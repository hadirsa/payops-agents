package io.github.hadirsa.payops.dispute.domain.privacy;

import java.util.regex.Pattern;

/**
 * Masks anything that looks like a card number (13 to 19 digits, optionally grouped with spaces or
 * hyphens) before text reaches a prompt. It errs on the side of redacting.
 */
public final class PanRedactor {

    static final String MASK = "[REDACTED-CARD-NUMBER]";
    private static final Pattern PAN = Pattern.compile("(?<![\\w])(?:\\d[ -]?){12,18}\\d(?![\\w])");

    private PanRedactor() {}

    public static String redact(String text) {
        return text == null ? null : PAN.matcher(text).replaceAll(MASK);
    }
}
