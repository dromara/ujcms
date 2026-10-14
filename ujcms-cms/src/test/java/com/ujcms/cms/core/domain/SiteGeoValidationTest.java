package com.ujcms.cms.core.domain;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Site.Geo} Bean 校验：AI 爬虫 User-agent 项限定 product token 字符集（一个换行就能伪造出第二条指令），
 * robots.txt 自定义内容长度与控制台上限一致。
 */
class SiteGeoValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static boolean isAgentValid(String agent) {
        Site.Geo geo = new Site.Geo();
        geo.setDisallowedAiAgents(List.of(agent));
        return validator.validate(geo).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GPTBot", "Claude-User", "meta-externalagent", "OAI-SearchBot", "Applebot-Extended", "a.b_c"})
    void agentAcceptsProductTokens(String agent) {
        assertTrue(isAgentValid(agent), agent);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "GPT Bot", "GPTBot\nDisallow: /", "bot:", "中文", "a/b"})
    void agentRejectsAnythingThatCouldBreakTheFile(String agent) {
        assertFalse(isAgentValid(agent), agent);
    }

    @Test
    void customRobotsContentIsCapped() {
        Site.Geo geo = new Site.Geo();
        geo.setRobotsCustom("x".repeat(30000));
        assertTrue(validator.validate(geo).isEmpty());
        geo.setRobotsCustom("x".repeat(30001));
        assertFalse(validator.validate(geo).isEmpty());
    }
}
