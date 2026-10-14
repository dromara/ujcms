package com.ujcms.cms.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Verifies mobile masking used for the "code sent to …" hint: enough digits stay visible for the
 * owner to recognize (and to notice a wrong number on file), never the full number.
 */
class PiiMaskerTest {
    @Test
    void mainlandMobileKeepsFirstThreeAndLastFour() {
        assertEquals("138****8000", PiiMasker.maskMobile("13800138000"));
    }

    @Test
    void separatorsDoNotShiftTheVisibleDigits() {
        assertEquals("138****8000", PiiMasker.maskMobile("138-0013 8000"));
    }

    @Test
    void countryCodeNumberKeepsPrefixAndLastFour() {
        assertEquals("+86*******8000", PiiMasker.maskMobile("+86 138 0013 8000"));
    }

    @Test
    void shortestFullyMaskedLengthStillHidesAtLeastOneDigit() {
        assertEquals("123*5678", PiiMasker.maskMobile("12345678"));
    }

    @Test
    void shortNumberKeepsOnlyLastTwo() {
        assertEquals("*****67", PiiMasker.maskMobile("1234567"));
        assertEquals("***45", PiiMasker.maskMobile("12345"));
    }

    @Test
    void veryShortNumberIsFullyMasked() {
        assertEquals("****", PiiMasker.maskMobile("1234"));
    }

    @Test
    void blankReturnsEmpty() {
        assertEquals("", PiiMasker.maskMobile(null));
        assertEquals("", PiiMasker.maskMobile("  "));
        assertEquals("", PiiMasker.maskMobile("--"));
    }
}
