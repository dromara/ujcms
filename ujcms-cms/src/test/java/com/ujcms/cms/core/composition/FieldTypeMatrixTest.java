package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldTypeRegistry;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import com.ujcms.cms.core.composition.fields.types.ArticleRefFieldType;
import com.ujcms.cms.core.composition.fields.types.BadgeFieldType;
import com.ujcms.cms.core.composition.fields.types.BooleanFieldType;
import com.ujcms.cms.core.composition.fields.types.ChannelRefFieldType;
import com.ujcms.cms.core.composition.fields.types.DateFieldType;
import com.ujcms.cms.core.composition.fields.types.DateTimeFieldType;
import com.ujcms.cms.core.composition.fields.types.EnumFieldType;
import com.ujcms.cms.core.composition.fields.types.FreeColorFieldType;
import com.ujcms.cms.core.composition.fields.types.HtmlFieldType;
import com.ujcms.cms.core.composition.fields.types.LinkFieldType;
import com.ujcms.cms.core.composition.fields.types.ListFieldType;
import com.ujcms.cms.core.composition.fields.types.LongTextFieldType;
import com.ujcms.cms.core.composition.fields.types.MediaFieldType;
import com.ujcms.cms.core.composition.fields.types.NumberFieldType;
import com.ujcms.cms.core.composition.fields.types.ObjectFieldType;
import com.ujcms.cms.core.composition.fields.types.TextFieldType;
import com.ujcms.cms.core.composition.fields.types.ThemeColorFieldType;
import com.ujcms.cms.core.composition.fields.types.VoteRefFieldType;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字段类型矩阵测试：17 个核心类型 + voteRef（设置专用引用，vote-micro-region §2）的合法/非法值。
 */
class FieldTypeMatrixTest {
    static FieldTypeRegistry registry() {
        HtmlSanitizeService sanitizeService = new HtmlSanitizeService("");
        return new FieldTypeRegistry(List.of(
                new TextFieldType(), new LongTextFieldType(), new HtmlFieldType(sanitizeService),
                new NumberFieldType(), new BooleanFieldType(), new DateFieldType(), new DateTimeFieldType(),
                new EnumFieldType(), new MediaFieldType(), new LinkFieldType(), new BadgeFieldType(),
                new ThemeColorFieldType(), new FreeColorFieldType(), new ArticleRefFieldType(),
                new ChannelRefFieldType(), new VoteRefFieldType(), new ListFieldType(), new ObjectFieldType()));
    }

    static final FieldValueValidator VALIDATOR = new FieldValueValidator(registry());

    static List<String> validate(Map<String, Object> def, Object value) {
        return VALIDATOR.validateValue(value, new FieldDef(def));
    }

    @Test
    void registryContainsAllMatrixTypes() {
        assertEquals(18, registry().names().size());
    }

    static Stream<Arguments> validCases() {
        return Stream.of(
                Arguments.of(Map.of("type", "text", "max", 10), "hello"),
                Arguments.of(Map.of("type", "text", "pattern", "^[a-z]+$"), "abc"),
                Arguments.of(Map.of("type", "longText", "max", 100), "line1\nline2"),
                Arguments.of(Map.of("type", "html"), "<p>hi</p>"),
                Arguments.of(Map.of("type", "number", "min", 1, "max", 10), 5),
                Arguments.of(Map.of("type", "number", "integer", true), 5),
                Arguments.of(Map.of("type", "boolean"), true),
                Arguments.of(Map.of("type", "date"), "2026-06-11"),
                Arguments.of(Map.of("type", "datetime"), "2026-06-11T08:00:00+08:00"),
                Arguments.of(Map.of("type", "enum", "values", List.of("a", "b")), "a"),
                Arguments.of(Map.of("type", "media"), Map.of("url", "/uploads/a.jpg", "alt", "a")),
                Arguments.of(Map.of("type", "media", "constraints", Map.of("minWidth", 100)),
                        Map.of("url", "http://example.com/a.jpg", "width", 200)),
                Arguments.of(Map.of("type", "link"), Map.of("url", "/news", "targetBlank", false)),
                Arguments.of(Map.of("type", "link"), Map.of("url", "https://example.com")),
                Arguments.of(Map.of("type", "link"), Map.of("url", "HTTPS://Example.com")),
                Arguments.of(Map.of("type", "link"), Map.of("url", "//cdn.example.com/a")),
                Arguments.of(Map.of("type", "link"), Map.of("url", "#")),
                Arguments.of(Map.of("type", "link"), Map.of("url", "#section-2")),
                Arguments.of(Map.of("type", "link"), Map.of("url", "mailto:hi@example.com")),
                Arguments.of(Map.of("type", "link"), Map.of("url", "tel:+8610000000")),
                // 粘贴常带首尾空白：校验按剥离后的值
                Arguments.of(Map.of("type", "link"), Map.of("url", "  https://example.com ")),
                Arguments.of(Map.of("type", "link", "allowExternal", false), Map.of("url", "#top")),
                Arguments.of(Map.of("type", "badge"), Map.of("text", "新", "bg", "#ff0000", "fg", "#fff")),
                Arguments.of(Map.of("type", "themeColor", "palette", List.of("primary", "accent")),
                        Map.of("token", "primary")),
                Arguments.of(Map.of("type", "freeColor"), "#2563eb"),
                Arguments.of(Map.of("type", "freeColor"), "#abc"),
                Arguments.of(Map.of("type", "articleRef"), 123L),
                // 雪花ID超出 JS 安全整数范围，控制台以字符串提交（保存时归一化为 Long，§10.1）
                Arguments.of(Map.of("type", "articleRef"), "1934567890123456789"),
                Arguments.of(Map.of("type", "channelRef"), 456),
                Arguments.of(Map.of("type", "channelRef"), "456"),
                Arguments.of(Map.of("type", "voteRef"), 789L),
                Arguments.of(Map.of("type", "voteRef"), 789),
                Arguments.of(Map.of("type", "voteRef"), "1934567890123456789"),
                Arguments.of(Map.of("type", "list", "of", Map.of("type", "text"), "max", 3), List.of("a", "b")),
                Arguments.of(Map.of("type", "list", "of",
                                Map.of("type", "object", "fields", Map.of("label", Map.of("type", "text")))),
                        List.of(Map.of("label", "x"))),
                Arguments.of(Map.of("type", "object", "fields", Map.of("label", Map.of("type", "text"))),
                        Map.of("label", "x"))
        );
    }

    @ParameterizedTest
    @MethodSource("validCases")
    void validValuesPass(Map<String, Object> def, Object value) {
        assertEquals(List.of(), validate(def, value));
    }

    static Stream<Arguments> invalidCases() {
        return Stream.of(
                Arguments.of(Map.of("type", "text", "max", 3), "toolong"),
                Arguments.of(Map.of("type", "text", "min", 5), "ab"),
                Arguments.of(Map.of("type", "text", "pattern", "^[a-z]+$"), "ABC"),
                Arguments.of(Map.of("type", "text"), 42),
                Arguments.of(Map.of("type", "longText", "max", 3), "toolong"),
                Arguments.of(Map.of("type", "html"), 42),
                Arguments.of(Map.of("type", "number", "max", 10), 11),
                Arguments.of(Map.of("type", "number", "min", 10), 9),
                Arguments.of(Map.of("type", "number", "integer", true), 1.5),
                Arguments.of(Map.of("type", "number"), "5"),
                Arguments.of(Map.of("type", "boolean"), "true"),
                Arguments.of(Map.of("type", "date"), "2026/06/11"),
                Arguments.of(Map.of("type", "date"), "2026-13-40"),
                Arguments.of(Map.of("type", "datetime"), "2026-06-11 08:00:00"),
                Arguments.of(Map.of("type", "enum", "values", List.of("a", "b")), "c"),
                Arguments.of(Map.of("type", "media"), Map.of("alt", "no url")),
                Arguments.of(Map.of("type", "media"), Map.of("url", "javascript:alert(1)")),
                Arguments.of(Map.of("type", "media", "constraints", Map.of("minWidth", 300)),
                        Map.of("url", "/a.jpg", "width", 200)),
                Arguments.of(Map.of("type", "media", "constraints", Map.of("maxBytes", 100)),
                        Map.of("url", "/a.jpg", "byteLength", 200)),
                Arguments.of(Map.of("type", "link"), Map.of("url", "ftp://example.com")),
                Arguments.of(Map.of("type", "link"), Map.of("url", "javascript:alert(1)")),
                Arguments.of(Map.of("type", "link"), Map.of("url", "www.example.com")),
                Arguments.of(Map.of("type", "link"), Map.of("url", "about.html")),
                Arguments.of(Map.of("type", "link"), Map.of("url", "httpfoo")),
                Arguments.of(Map.of("type", "link"), Map.of("url", "   ")),
                Arguments.of(Map.of("type", "link", "allowExternal", false), Map.of("url", "http://evil.com")),
                // 协议相对地址是外链
                Arguments.of(Map.of("type", "link", "allowExternal", false), Map.of("url", "//evil.com")),
                Arguments.of(Map.of("type", "link", "allowTargetBlank", false),
                        Map.of("url", "/a", "targetBlank", true)),
                Arguments.of(Map.of("type", "badge"), Map.of("bg", "#fff")),
                Arguments.of(Map.of("type", "badge"), Map.of("text", "新", "bg", "red")),
                Arguments.of(Map.of("type", "themeColor", "palette", List.of("primary")), Map.of("token", "other")),
                Arguments.of(Map.of("type", "themeColor"), Map.of()),
                Arguments.of(Map.of("type", "freeColor"), "blue"),
                Arguments.of(Map.of("type", "freeColor"), "#12"),
                Arguments.of(Map.of("type", "articleRef"), 0),
                Arguments.of(Map.of("type", "articleRef"), 1.5),
                Arguments.of(Map.of("type", "articleRef"), "0"),
                Arguments.of(Map.of("type", "channelRef"), "12x"),
                Arguments.of(Map.of("type", "voteRef"), 0),
                Arguments.of(Map.of("type", "voteRef"), 1.5),
                Arguments.of(Map.of("type", "voteRef"), "0"),
                Arguments.of(Map.of("type", "voteRef"), "12x"),
                Arguments.of(Map.of("type", "list", "of", Map.of("type", "text"), "max", 1), List.of("a", "b")),
                Arguments.of(Map.of("type", "list", "of", Map.of("type", "text"), "min", 2), List.of("a")),
                Arguments.of(Map.of("type", "list", "of", Map.of("type", "number")), List.of("not number")),
                Arguments.of(Map.of("type", "object", "fields", Map.of("label", Map.of("type", "text"))),
                        Map.of("unknown", "x"))
        );
    }

    @ParameterizedTest
    @MethodSource("invalidCases")
    void invalidValuesFail(Map<String, Object> def, Object value) {
        assertFalse(validate(def, value).isEmpty(), "应校验失败: " + def + " <- " + value);
    }

    @Test
    void htmlSanitizedOnSave() {
        FieldDef def = new FieldDef(Map.of("type", "html"));
        Object saved = VALIDATOR.processValueOnSave("<p>ok</p><script>alert(1)</script>", def);
        assertEquals("<p>ok</p>", saved);
    }

    @Test
    void htmlSanitizeAppliesInsideListAndObject() {
        FieldDef def = new FieldDef(Map.of("type", "list", "of",
                Map.of("type", "object", "fields", Map.of("body", Map.of("type", "html")))));
        Object saved = VALIDATOR.processValueOnSave(
                List.of(Map.of("body", "<b>x</b><script>bad()</script>")), def);
        assertEquals(List.of(Map.of("body", "<b>x</b>")), saved);
    }

    @Test
    void linkUrlTrimmedOnSave() {
        FieldDef def = new FieldDef(Map.of("type", "link", "withLabel", true));
        Object saved = VALIDATOR.processValueOnSave(
                Map.of("url", " https://example.com ", "mobileUrl", " /m ", "label", " x "), def);
        assertEquals(Map.of("url", "https://example.com", "mobileUrl", "/m", "label", " x "), saved);
    }

    @Test
    void linkLabelDroppedOnSaveUnlessWithLabel() {
        FieldDef def = new FieldDef(Map.of("type", "link"));
        Object saved = VALIDATOR.processValueOnSave(
                Map.of("url", "/more", "targetBlank", true, "label", "更多"), def);
        assertEquals(Map.of("url", "/more", "targetBlank", true), saved);
    }

    @Test
    void requiredFieldEnforcedAtGroupLevel() {
        Map<String, FieldDef> fields = Map.of(
                "headline", new FieldDef(Map.of("type", "text", "required", true)));
        List<FieldValueValidator.FieldError> errors = VALIDATOR.validate(fields, Map.of());
        assertEquals(1, errors.size());
        assertEquals("headline", errors.get(0).field());
    }

    @Test
    void undeclaredFieldRejected() {
        Map<String, FieldDef> fields = Map.of("a", new FieldDef(Map.of("type", "text")));
        List<FieldValueValidator.FieldError> errors = VALIDATOR.validate(fields, Map.of("b", "x"));
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> "b".equals(e.field())));
    }
}
