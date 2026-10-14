package com.ujcms.cms.core.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * A still-valid verification code is a usable second login factor, so it must never reach the
 * console: the short-message log is an ordinary "view logs" permission. The code is only serialized
 * once it can no longer pass validation — used, burned, or older than the longest configurable
 * lifetime — which keeps the log useful for troubleshooting.
 */
class ShortMessageJsonTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private JsonNode json(ShortMessage message) {
        return mapper.valueToTree(message);
    }

    private static ShortMessage message() {
        return new ShortMessage(ShortMessage.TYPE_MOBILE, "13800138000", "123456", "127.0.0.1",
                ShortMessage.USAGE_MFA_LOGIN);
    }

    @Test
    void unusedCodeIsNotSerialized() {
        JsonNode json = json(message());

        assertFalse(json.has("code"), "a live code must not reach the console");
        assertFalse(json.has("displayCode"), "the helper getter must not leak under another name");
        assertEquals("13800138000", json.get("receiver").asText());
    }

    @Test
    void usedOrBurnedCodeIsSerialized() {
        for (short status : new short[]{ShortMessage.STATUS_CORRECT, ShortMessage.STATUS_WRONG,
                ShortMessage.STATUS_EXPIRED, ShortMessage.STATUS_EXCEEDED}) {
            ShortMessage message = message();
            message.setStatus(status);

            assertEquals("123456", json(message).get("code").asText(), "status " + status);
        }
    }

    @Test
    void unusedCodePastTheLongestLifetimeIsSerialized() {
        ShortMessage message = message();
        message.setSendDate(OffsetDateTime.now().minusMinutes(ShortMessage.MAX_EXPIRES_MINUTES + 1L));

        assertEquals("123456", json(message).get("code").asText());
    }

    @Test
    void codeStillReadableInsideTheApplication() {
        // 校验逻辑仍需读取验证码：只影响 JSON 输出
        assertEquals("123456", message().getCode());
        assertFalse(message().isWrong("13800138000", "123456"));
    }
}
