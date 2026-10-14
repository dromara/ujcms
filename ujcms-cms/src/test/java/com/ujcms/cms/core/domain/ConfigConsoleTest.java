package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ujcms.cms.core.support.Constants;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Config.Console}：控制台默认外观的 JSON 往返、缺省值、未知字段容错、原始列与缓存的一致性、Bean 校验
 * （docs/spec/console-shell/plan.md D7）。
 */
class ConfigConsoleTest {
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

    @Test
    void blankColumnYieldsTheCodeDefaults() {
        Config config = new Config();
        assertNull(config.getConsoleSettings());
        Config.Console console = config.getConsole();
        assertEquals(Config.Console.SIDEBAR_DARK, console.getSidebarTheme());
        assertEquals(Config.Console.DEFAULT_THEME_COLOR, console.getThemeColor());
        // 缺省对象不落列
        assertNull(config.getConsoleSettings());
    }

    @Test
    void roundTripThroughTheColumn() {
        Config.Console console = new Config.Console();
        console.setSidebarTheme(Config.Console.SIDEBAR_DARK);
        console.setThemeColor("#db2777");

        Config config = new Config();
        config.setConsole(console);
        String json = config.getConsoleSettings();

        Config loaded = new Config();
        loaded.setConsoleSettings(json);
        assertEquals("dark", loaded.getConsole().getSidebarTheme());
        assertEquals("#db2777", loaded.getConsole().getThemeColor());
    }

    @Test
    void settingTheRawColumnDropsTheCachedObject() {
        // “基础设置”整行保存走 Entities.copy → setConsoleSettings：缓存必须随之失效
        Config config = new Config();
        assertEquals("dark", config.getConsole().getSidebarTheme());
        config.setConsoleSettings("{\"sidebarTheme\":\"light\",\"themeColor\":\"#16a34a\"}");
        assertEquals("light", config.getConsole().getSidebarTheme());
        assertEquals("#16a34a", config.getConsole().getThemeColor());
    }

    @Test
    void entityJsonCarriesTheTypedObjectNotTheRawColumn() throws JsonProcessingException {
        // GET /backend/core/config（基础设置表单的数据源）须带 console；原始列 consoleSettings 由类级 @JsonIgnoreProperties 隐藏
        Config config = new Config();
        config.setConsoleSettings("{\"sidebarTheme\":\"dark\",\"themeColor\":\"#db2777\"}");
        var json = Constants.MAPPER.readTree(Constants.MAPPER.writeValueAsString(config));
        assertTrue(json.has("console"), json.toString());
        assertEquals("dark", json.get("console").get("sidebarTheme").asText());
        assertFalse(json.has("consoleSettings"));
    }

    @Test
    void unknownFieldsAreIgnored() throws JsonProcessingException {
        Config.Console read = Constants.MAPPER.readValue("{\"sidebarTheme\":\"dark\",\"future\":1}", Config.Console.class);
        assertEquals("dark", read.getSidebarTheme());
        assertEquals(Config.Console.DEFAULT_THEME_COLOR, read.getThemeColor());
    }

    @Test
    void validationRejectsOutOfRangeValues() {
        assertTrue(validator.validate(new Config.Console()).isEmpty());

        Config.Console theme = new Config.Console();
        theme.setSidebarTheme("blue");
        assertFalse(validator.validate(theme).isEmpty());

        Config.Console color = new Config.Console();
        color.setThemeColor("red");
        assertFalse(validator.validate(color).isEmpty());

        // 嵌在 Config 里经 getter 上的 @Valid 级联校验（updateBase 的 @Valid Config 即走此路）
        Config config = new Config();
        config.setConsoleSettings("{\"sidebarTheme\":\"blue\",\"themeColor\":\"#16a34a\"}");
        assertTrue(validator.validate(config).stream().anyMatch(v -> v.getPropertyPath().toString().startsWith("console")));
    }
}
