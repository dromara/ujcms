package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.ThemeState;
import com.ujcms.cms.core.mapper.ThemeStateMapper;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.web.exception.Http400Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 主题设置服务测试：合并配置（默认值 ⊕ 存值）、保存校验 + 缓存作废、调色板令牌解析、设置结构热重载。
 */
class ThemeSettingsServiceTest {
    private static final Long SITE = 1L;
    private static final String THEME = "liquid-default";
    private static final String THEME_KEY = "1/liquid-default";

    private ManifestService manifestService;
    private ThemeStateMapper mapper;
    private CompositionCacheSync cacheSync;
    private ThemeSettingsService service;

    private static final String MANIFEST_JSON = """
            { "name": "Demo Default", "version": "1.1.0", "templateSpecVersion": "2.0",
              "settings": { "groups": [
                { "name": "palette", "label": { "zh-CN": "配色" }, "fields": {
                    "primary": { "type": "freeColor", "default": "#2563eb" },
                    "accent":  { "type": "freeColor", "default": "#ef4444" } } },
                { "name": "layout", "fields": {
                    "nav": { "type": "enum", "values": ["single", "double"], "default": "single" } } }
              ] } }
            """;

    private RegistrySnapshot snapshotWithSettings() {
        ThemeManifestLoader loader = new ThemeManifestLoader(new CompositionSchemas());
        ThemeManifestLoader.ParsedManifest parsed = loader.load(MANIFEST_JSON, "theme.manifest.json");
        return RegistrySnapshot.builder()
                .addThemeSettings(THEME_KEY, parsed.settings(), parsed.settingsJson())
                .build();
    }

    @BeforeEach
    void setUp() {
        manifestService = mock(ManifestService.class);
        mapper = mock(ThemeStateMapper.class);
        cacheSync = mock(CompositionCacheSync.class);
        when(manifestService.getSnapshot()).thenReturn(snapshotWithSettings());
        service = new ThemeSettingsService(manifestService, mapper, cacheSync,
                FieldTypeMatrixTest.registry(), new SnowflakeSequence(0, 0));
    }

    @Test
    void mergedConfigUsesDeclaredDefaultsWhenNoStoredValues() {
        when(mapper.selectByUnique(SITE, THEME)).thenReturn(null);
        Map<String, Map<String, Object>> config = service.mergedConfig(SITE, THEME);
        assertEquals("#2563eb", config.get("palette").get("primary"));
        assertEquals("#ef4444", config.get("palette").get("accent"));
        assertEquals("single", config.get("layout").get("nav"));
    }

    @Test
    void mergedConfigOverlaysStoredValuesOverDefaults() {
        ThemeState state = new ThemeState();
        Map<String, Map<String, Object>> stored = new LinkedHashMap<>();
        stored.put("palette", new LinkedHashMap<>(Map.of("primary", "#000000")));
        state.setSettings(globalValues(stored));
        when(mapper.selectByUnique(SITE, THEME)).thenReturn(state);

        Map<String, Map<String, Object>> config = service.mergedConfig(SITE, THEME);
        assertEquals("#000000", config.get("palette").get("primary"));
        // 未覆盖字段仍取默认
        assertEquals("#ef4444", config.get("palette").get("accent"));
    }

    @Test
    void paletteResolvesByThemeKey() {
        when(mapper.selectByUnique(SITE, THEME)).thenReturn(null);
        Map<String, Object> palette = service.paletteByThemeKey(THEME_KEY);
        assertEquals("#2563eb", palette.get("primary"));
        // 无主题键 → 空
        assertTrue(service.paletteByThemeKey(null).isEmpty());
    }

    @Test
    void saveValidatesAgainstSchemaAndInvalidatesCache() {
        when(mapper.selectByUnique(SITE, THEME)).thenReturn(existingState());
        Map<String, Map<String, Object>> values = new LinkedHashMap<>();
        values.put("palette", new LinkedHashMap<>(Map.of("primary", "#123456")));
        assertTrue(service.save(SITE, THEME, values, null, null).isEmpty());
        verify(mapper).update(any(ThemeState.class));
        verify(cacheSync).invalidateAll("theme-settings");
    }

    @Test
    void saveReportsDroppedUndeclaredGroupsAndFields() {
        // 未声明分组/字段弃警不拒（H5）：照常落库已声明值，丢弃键以点路径返回
        when(mapper.selectByUnique(SITE, THEME)).thenReturn(existingState());
        Map<String, Map<String, Object>> values = new LinkedHashMap<>();
        values.put("palette", new LinkedHashMap<>(Map.of("primary", "#123456", "ghostField", "x")));
        values.put("ghostGroup", new LinkedHashMap<>(Map.of("y", "z")));
        java.util.List<String> dropped = service.save(SITE, THEME, values, null, null);
        assertTrue(dropped.contains("palette.ghostField"));
        assertTrue(dropped.contains("ghostGroup"));
        assertEquals(2, dropped.size());
        verify(mapper).update(any(ThemeState.class));
    }

    @Test
    void saveRejectsInvalidEnumValue() {
        when(mapper.selectByUnique(SITE, THEME)).thenReturn(existingState());
        Map<String, Map<String, Object>> values = new LinkedHashMap<>();
        values.put("layout", new LinkedHashMap<>(Map.of("nav", "triple")));
        assertThrows(Http400Exception.class, () -> service.save(SITE, THEME, values, null, null));
        verify(cacheSync, org.mockito.Mockito.never()).invalidateAll("theme-settings");
    }

    @Test
    void themeModelExposesConfigForActivePath() {
        when(mapper.selectByUnique(SITE, THEME)).thenReturn(null);
        // active theme stores the bare theme name (R2)
        Map<String, Object> model = service.themeModel(SITE, THEME);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> config = (Map<String, Map<String, Object>>) model.get("config");
        assertEquals("#2563eb", config.get("palette").get("primary"));
    }

    @Test
    void hotReloadPicksUpSchemaChangeFromNewSnapshot() {
        when(mapper.selectByUnique(SITE, THEME)).thenReturn(null);
        assertEquals("#2563eb", service.mergedConfig(SITE, THEME).get("palette").get("primary"));
        // 模拟清单编辑后注册表热重载：新快照里默认色变化
        String edited = MANIFEST_JSON.replace("#2563eb", "#0ea5e9");
        ThemeManifestLoader loader = new ThemeManifestLoader(new CompositionSchemas());
        ThemeManifestLoader.ParsedManifest parsed = loader.load(edited, "theme.manifest.json");
        when(manifestService.getSnapshot()).thenReturn(RegistrySnapshot.builder()
                .addThemeSettings(THEME_KEY, parsed.settings(), parsed.settingsJson()).build());
        assertEquals("#0ea5e9", service.mergedConfig(SITE, THEME).get("palette").get("primary"));
    }

    @Test
    void mergedConfigEmptyForThemeWithoutSettings() {
        when(manifestService.getSnapshot()).thenReturn(RegistrySnapshot.empty());
        assertTrue(service.mergedConfig(SITE, THEME).isEmpty());
        assertNull(service.findState(SITE, THEME));
    }

    private ThemeState existingState() {
        ThemeState state = new ThemeState();
        state.setId(99L);
        state.setSiteId(SITE);
        state.setThemeName(THEME);
        return state;
    }

    /** R3 结构化设置值：仅设 global 作用域（分组 → 字段 → 值）。 */
    private static ThemeState.SettingValues globalValues(Map<String, Map<String, Object>> global) {
        ThemeState.SettingValues values = new ThemeState.SettingValues();
        values.setGlobal(global);
        return values;
    }

    @Test
    void settingsJsonRoundTripsR3Structure() {
        // R3 存储结构 {global, pages, variants}：写读一致，且保存 global 不动 pages/variants
        ThemeState state = new ThemeState();
        state.setId(1L);
        state.setSettingsJson("""
                { "global": { "palette": { "primary": "#000000" } },
                  "pages": { "channel": { "listing": { "columns": "2" } } },
                  "variants": { "channel:grid": { "listing": { "gap": "8" } } } }
                """);
        ThemeState.SettingValues values = state.getSettings();
        assertEquals("#000000", values.getGlobal().get("palette").get("primary"));
        assertEquals("2", values.getPages().get("channel").get("listing").get("columns"));
        assertEquals("8", values.getVariants().get("channel:grid").get("listing").get("gap"));

        values.setGlobal(new LinkedHashMap<>(Map.of("palette", new LinkedHashMap<>(Map.of("primary", "#ffffff")))));
        state.disassemble();
        ThemeState reloaded = new ThemeState();
        reloaded.setSettingsJson(state.getSettingsJson());
        assertEquals("#ffffff", reloaded.getSettings().getGlobal().get("palette").get("primary"));
        assertEquals("2", reloaded.getSettings().getPages().get("channel").get("listing").get("columns"));
    }

    // ---- 页面 / 变体作用域（manifest 2.0 §4.1/§4.2）：结构声明 + 按作用域合并 ----

    /** 带页面 settings 与变体 settings 声明的快照（channel 页面清单 + channel:grid 变体）。 */
    private RegistrySnapshot snapshotWithScopedSettings() {
        ThemeManifestLoader themeLoader = new ThemeManifestLoader(new CompositionSchemas());
        ThemeManifestLoader.ParsedManifest parsed = themeLoader.load("""
                { "name": "Demo Default",
                  "settings": { "groups": [
                    { "name": "palette", "fields": { "primary": { "type": "freeColor", "default": "#2563eb" } } } ] },
                  "layoutVariants": { "channel": [
                    { "value": "grid", "label": { "zh-CN": "网格" }, "template": "channel_grid",
                      "settings": { "groups": [
                        { "name": "grid", "fields": {
                            "gap": { "type": "enum", "values": ["4", "8"], "default": "4" } } } ] } } ] } }
                """, "theme.manifest.json");
        ManifestLoader manifestLoader = new ManifestLoader(new CompositionSchemas());
        PageManifest channelPage = manifestLoader.load("""
                { "name": "channel", "label": { "zh-CN": "栏目页" }, "template": "channel",
                  "settings": { "groups": [
                    { "name": "listing", "fields": {
                        "columns":     { "type": "enum", "values": ["1", "2", "3"], "default": "2" },
                        "showSummary": { "type": "boolean", "default": true } } } ] } }
                """, "channel.manifest.json");
        return RegistrySnapshot.builder()
                .addThemeSettings(THEME_KEY, parsed.settings(), parsed.settingsJson())
                .addThemeLayoutVariants(THEME_KEY, parsed.layoutVariants(), parsed.layoutVariantsJson())
                .addThemeManifest(THEME_KEY, channelPage, "{}", "channel.manifest.json")
                .build();
    }

    @Test
    void structuredConfigMergesAllScopesAgainstDeclaredSchemas() {
        when(manifestService.getSnapshot()).thenReturn(snapshotWithScopedSettings());
        ThemeState state = existingState();
        state.setSettingsJson("""
                { "global": { "palette": { "primary": "#000000" } },
                  "pages": { "channel": { "listing": { "columns": "3" } },
                             "undeclared": { "x": { "y": "z" } } },
                  "variants": { "channel:grid": { "grid": { "gap": "8" } } } }
                """);
        when(mapper.selectByUnique(SITE, THEME)).thenReturn(state);
        Map<String, Object> config = service.structuredConfig(SITE, THEME);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> global = (Map<String, Map<String, Object>>) config.get("global");
        assertEquals("#000000", global.get("palette").get("primary"));
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Map<String, Object>>> pages =
                (Map<String, Map<String, Map<String, Object>>>) config.get("pages");
        // 页面作用域按声明结构合并：存值覆盖 + 默认值兜底；未声明的存值作用域不再透传
        assertEquals("3", pages.get("channel").get("listing").get("columns"));
        assertEquals(true, pages.get("channel").get("listing").get("showSummary"));
        assertTrue(!pages.containsKey("undeclared"));
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Map<String, Object>>> variants =
                (Map<String, Map<String, Map<String, Object>>>) config.get("variants");
        assertEquals("8", variants.get("channel:grid").get("grid").get("gap"));
    }

    @Test
    void pageConfigOverlaysVariantScopeOverPageScope() {
        when(manifestService.getSnapshot()).thenReturn(snapshotWithScopedSettings());
        when(mapper.selectByUnique(SITE, THEME)).thenReturn(null);
        // 页面 + 变体：两个作用域的分组并集，声明默认值兜底
        Map<String, Map<String, Object>> config = service.pageConfig(SITE, THEME, "channel", "channel:grid");
        assertEquals("2", config.get("listing").get("columns"));
        assertEquals("4", config.get("grid").get("gap"));
        // 仅页面（无活动变体）
        assertTrue(!service.pageConfig(SITE, THEME, "channel", null).containsKey("grid"));
        // 无匹配页面无变体 → 空
        assertTrue(service.pageConfig(SITE, THEME, null, null).isEmpty());
    }

    @Test
    void saveScopedValuesValidatesEachScopeAndDropsUndeclared() {
        when(manifestService.getSnapshot()).thenReturn(snapshotWithScopedSettings());
        when(mapper.selectByUnique(SITE, THEME)).thenReturn(existingState());
        Map<String, Map<String, Map<String, Object>>> pages = new LinkedHashMap<>();
        pages.put("channel", new LinkedHashMap<>(Map.of("listing", new LinkedHashMap<>(Map.of("columns", "1")))));
        pages.put("undeclared", new LinkedHashMap<>(Map.of("x", new LinkedHashMap<>(Map.of("y", "z")))));
        Map<String, Map<String, Map<String, Object>>> variants = new LinkedHashMap<>();
        variants.put("channel:grid", new LinkedHashMap<>(Map.of("grid", new LinkedHashMap<>(Map.of("gap", "8")))));
        java.util.List<String> dropped = service.save(SITE, THEME, null, pages, variants);
        // 未声明的页面作用域键整体丢弃并回报（H5）
        assertEquals(java.util.List.of("pages.undeclared"), dropped);
        org.mockito.ArgumentCaptor<ThemeState> captor = org.mockito.ArgumentCaptor.forClass(ThemeState.class);
        verify(mapper).update(captor.capture());
        ThemeState.SettingValues saved = captor.getValue().getSettings();
        assertEquals("1", saved.getPages().get("channel").get("listing").get("columns"));
        assertTrue(!saved.getPages().containsKey("undeclared"));
        assertEquals("8", saved.getVariants().get("channel:grid").get("grid").get("gap"));
    }

    @Test
    void saveScopedValuesRejectsInvalidVariantValue() {
        when(manifestService.getSnapshot()).thenReturn(snapshotWithScopedSettings());
        when(mapper.selectByUnique(SITE, THEME)).thenReturn(existingState());
        Map<String, Map<String, Map<String, Object>>> variants = new LinkedHashMap<>();
        variants.put("channel:grid", new LinkedHashMap<>(Map.of("grid", new LinkedHashMap<>(Map.of("gap", "16")))));
        assertThrows(Http400Exception.class, () -> service.save(SITE, THEME, null, null, variants));
        verify(cacheSync, org.mockito.Mockito.never()).invalidateAll("theme-settings");
    }

}
