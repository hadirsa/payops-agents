package io.github.hadirsa.payops.dispute.domain.privacy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PanRedactorTest {

    @Test
    void masksPlainGroupedAndHyphenatedCardNumbers() {
        assertEquals("card [REDACTED-CARD-NUMBER] was charged", PanRedactor.redact("card 4242424242424242 was charged"));
        assertEquals("card [REDACTED-CARD-NUMBER] was charged", PanRedactor.redact("card 4242 4242 4242 4242 was charged"));
        assertEquals("card [REDACTED-CARD-NUMBER] was charged", PanRedactor.redact("card 4242-4242-4242-4242 was charged"));
    }

    @Test
    void masksShorterAndLongerCardLengths() {
        assertEquals(PanRedactor.MASK, PanRedactor.redact("4000056655665556".substring(0, 13)));
        assertEquals(PanRedactor.MASK, PanRedactor.redact("6011111111111117890"));
    }

    @Test
    void leavesShortNumbersAndDisputeIdsAlone() {
        var text = "Dispute dp_1234567890123456 for order 98213, ending 4242, $240.00";
        assertEquals(text, PanRedactor.redact(text));
    }

    @Test
    void handlesNullAndEmpty() {
        assertNull(PanRedactor.redact(null));
        assertEquals("", PanRedactor.redact(""));
    }
}
