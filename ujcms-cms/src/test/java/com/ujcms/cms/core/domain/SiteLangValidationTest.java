package com.ujcms.cms.core.domain;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多语言寻址（multilingual P1）Bean 校验测试：
 * <ul>
 * <li>{@code Site.getLang()} 的 BCP-47 {@code @Pattern}（addressing-core.md R5）</li>
 * <li>{@code Site.getSubDir()} 保留字扩展 api|frontend|m（addressing-core.md §1.1）</li>
 * </ul>
 */
class SiteLangValidationTest {
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

    private boolean isLangValid(String lang) {
        Site site = new Site();
        site.setLang(lang);
        return validator.validateProperty(site, "lang").isEmpty();
    }

    private boolean isSubDirValid(String subDir) {
        Site site = new Site();
        site.setSubDir(subDir);
        return validator.validateProperty(site, "subDir").isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh-CN", "en", "zh-Hans-CN", "en-US", "pt-BR", "sr-Latn-RS", "es-419"})
    void langAcceptsBcp47Tags(String lang) {
        assertTrue(isLangValid(lang), lang);
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh_CN", "1en", "a", "", "-en", "en-", "zh-CN-", "abcdefghi", "zh CN", "zh--CN"})
    void langRejectsMalformedTags(String lang) {
        assertFalse(isLangValid(lang), lang);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "site-a", "site-a-en", "en2", "m2", "apis"})
    void subDirAcceptsRegularSegments(String subDir) {
        assertTrue(isSubDirValid(subDir), subDir);
    }

    /**
     * 原有保留字 + P1 新增：api/frontend（REST 前缀）、m（移动端静态页目录）。大小写不敏感
     */
    @ParameterizedTest
    @ValueSource(strings = {"uploads", "themes", "WEB-INF", "web-inf", "cp", "api", "API", "frontend", "Frontend", "m", "M"})
    void subDirRejectsReservedWords(String subDir) {
        assertFalse(isSubDirValid(subDir), subDir);
    }
}
