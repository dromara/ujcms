package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ujcms.cms.core.support.Constants;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UserExt.Preferences}：控制台偏好的 JSON 往返、缺省值（外观两项为空 = 跟随 {@link Config.Console}，D7）、
 * 未知字段容错与 Bean 校验（docs/spec/console-shell/plan.md）。
 */
class UserExtPreferencesTest {
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
    void blankColumnFollowsTheInstallationDefaults() {
        UserExt ext = new UserExt();
        assertNull(ext.getPreferencesJson());
        UserExt.Preferences preferences = ext.getPreferences();
        // 外观两项为空：控制台按 Config.Console 的默认外观绘制；服务端不合成
        assertNull(preferences.getSidebarTheme());
        assertNull(preferences.getThemeColor());
        assertTrue(preferences.getHome().getHidden().isEmpty());
        assertTrue(preferences.getHome().getShortcuts().isEmpty());
        // 缺省对象不落列：列仍为空，直到显式保存
        assertNull(ext.getPreferencesJson());
    }

    @Test
    void roundTripThroughTheColumn() {
        UserExt.Preferences preferences = new UserExt.Preferences();
        preferences.setSidebarTheme(Config.Console.SIDEBAR_DARK);
        preferences.setThemeColor("#db2777");
        preferences.getHome().setHidden(List.of("traffic"));
        preferences.getHome().setShortcuts(List.of("ArticleList", "ChannelList"));

        UserExt ext = new UserExt();
        ext.setPreferences(preferences);
        String json = ext.getPreferencesJson();

        UserExt loaded = new UserExt();
        loaded.setPreferencesJson(json);
        UserExt.Preferences read = loaded.getPreferences();
        assertEquals("dark", read.getSidebarTheme());
        assertEquals("#db2777", read.getThemeColor());
        assertEquals(List.of("traffic"), read.getHome().getHidden());
        assertEquals(List.of("ArticleList", "ChannelList"), read.getHome().getShortcuts());
    }

    @Test
    void unknownFieldsAreIgnored() throws JsonProcessingException {
        String json = "{\"sidebarTheme\":\"dark\",\"future\":1,\"home\":{\"shortcuts\":[\"UserList\"],\"later\":true}}";
        UserExt.Preferences read = Constants.MAPPER.readValue(json, UserExt.Preferences.class);
        assertEquals("dark", read.getSidebarTheme());
        assertNull(read.getThemeColor());
        assertEquals(List.of("UserList"), read.getHome().getShortcuts());
    }

    @Test
    void explicitNullResetsAnAppearanceChoice() throws JsonProcessingException {
        // “恢复默认”：控制台把两项写成 null；读回仍为空（Jackson 不把 null 当缺省值补回）
        String json = "{\"sidebarTheme\":null,\"themeColor\":null,\"home\":{\"shortcuts\":[\"UserList\"]}}";
        UserExt.Preferences read = Constants.MAPPER.readValue(json, UserExt.Preferences.class);
        assertNull(read.getSidebarTheme());
        assertNull(read.getThemeColor());
        assertTrue(validator.validate(read).isEmpty());
    }

    @Test
    void validationRejectsOutOfRangeValues() {
        assertTrue(validator.validate(new UserExt.Preferences()).isEmpty());

        UserExt.Preferences theme = new UserExt.Preferences();
        theme.setSidebarTheme("blue");
        assertFalse(validator.validate(theme).isEmpty());

        UserExt.Preferences color = new UserExt.Preferences();
        color.setThemeColor("red");
        assertFalse(validator.validate(color).isEmpty());

        UserExt.Preferences tooMany = new UserExt.Preferences();
        tooMany.getHome().setShortcuts(Collections.nCopies(UserExt.Preferences.Home.SHORTCUTS_MAX + 1, "ArticleList"));
        assertFalse(validator.validate(tooMany).isEmpty());

        UserExt.Preferences tooLong = new UserExt.Preferences();
        tooLong.getHome().setHidden(List.of("x".repeat(65)));
        assertFalse(validator.validate(tooLong).isEmpty());

        UserExt.Preferences atCap = new UserExt.Preferences();
        atCap.getHome().setShortcuts(Collections.nCopies(UserExt.Preferences.Home.SHORTCUTS_MAX, "ArticleList"));
        assertTrue(validator.validate(atCap).isEmpty());
    }
}
