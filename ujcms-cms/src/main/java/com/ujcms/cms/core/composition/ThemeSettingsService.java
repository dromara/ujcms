package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldTypeRegistry;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import com.ujcms.cms.core.composition.fields.FieldValueValidator.FieldError;
import com.ujcms.cms.core.domain.ThemeState;
import com.ujcms.cms.core.mapper.ThemeStateMapper;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.web.exception.Http400Exception;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主题设置服务（§8.2 / §9.2）。读取期合并配置（声明默认值 ⊕ {@code ujcms_theme_state.settings_json_}）、
 * 按设置结构校验保存、调色板 {@code themeColor} 令牌解析。设置<b>结构</b>是文件事实（在
 * {@code theme.manifest.json#settings}，随清单热重载，见 {@link RegistrySnapshot#getThemeSettings}）；
 * 此处只管理操作员设置的<b>值</b>，每 {@code (站点, 主题)} 一行（桌面 / 移动各自一行，按需懒创建，§6.3）。
 *
 * <p>保存即作废 {@link PanelResolveCache}（Phase 4 偏差 #7 的主题设置钩子）——显式作废保证后台
 * 编辑即时可见、{@code themeColor} resolvedValue 下次请求即重解析。
 *
 * @author PONY
 */
@Service
public class ThemeSettingsService {
    private final ManifestService manifestService;
    private final ThemeStateMapper mapper;
    private final CompositionCacheSync cacheSync;
    private final FieldValueValidator fieldValueValidator;
    private final SnowflakeSequence snowflakeSequence;

    public ThemeSettingsService(ManifestService manifestService, ThemeStateMapper mapper,
                                CompositionCacheSync cacheSync, FieldTypeRegistry fieldTypeRegistry,
                                SnowflakeSequence snowflakeSequence) {
        this.manifestService = manifestService;
        this.mapper = mapper;
        this.cacheSync = cacheSync;
        this.fieldValueValidator = new FieldValueValidator(fieldTypeRegistry);
        this.snowflakeSequence = snowflakeSequence;
    }

    /**
     * 主题设置结构（{@code theme.manifest.json#settings}，§8.2）。
     *
     * @param themeKey 规范化主题键（{@code siteId/themeName}）
     */
    public ThemeSettingsSchema schema(String themeKey) {
        return manifestService.getSnapshot().getThemeSettings(themeKey);
    }

    /**
     * 合并配置（分组 → 字段 → 值）：声明默认值之上叠加操作员存值。仅含设置结构声明的字段。
     *
     * @param siteId    站点ID
     * @param themeName 主题名（不含 {@code siteId/} 前缀的目录名）
     */
    public Map<String, Map<String, Object>> mergedConfig(Long siteId, String themeName) {
        ThemeSettingsSchema schema = schema(themeKey(siteId, themeName));
        if (schema.isEmpty()) {
            return new LinkedHashMap<>(0);
        }
        return merge(schema, storedValues(siteId, themeName));
    }

    /**
     * 按设置结构合并一个作用域的值：声明默认值之上叠加该作用域存值。仅含结构声明的分组/字段。
     * 全局 / 页面 / 变体三个作用域共用（§4.2 同语法）。
     */
    private static Map<String, Map<String, Object>> merge(ThemeSettingsSchema schema,
                                                          Map<String, Map<String, Object>> stored) {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>(schema.getGroups().size());
        for (ThemeSettingsSchema.Group group : schema.getGroups()) {
            Map<String, Object> storedGroup = stored.getOrDefault(group.getName(), Map.of());
            Map<String, Object> values = new LinkedHashMap<>(group.getFields().size());
            group.getFields().forEach((fieldName, def) -> {
                Object value = storedGroup.containsKey(fieldName) ? storedGroup.get(fieldName) : def.getDefaultValue();
                if (value != null) {
                    values.put(fieldName, value);
                }
            });
            merged.put(group.getName(), values);
        }
        return merged;
    }

    /**
     * 合并配置（按主题键，{@code siteId/themeName}）。无主题键 → 空。
     */
    public Map<String, Map<String, Object>> mergedConfigByThemeKey(@Nullable String themeKey) {
        SiteTheme st = SiteTheme.parse(themeKey);
        return st == null ? new LinkedHashMap<>(0) : mergedConfig(st.siteId(), st.themeName());
    }

    /**
     * 调色板（{@code palette} 分组的合并值，令牌名 → 颜色值；§7.2）。
     *
     * @param themeKey 规范化主题键（{@code siteId/themeName}）；{@code null}/无 palette → 空
     */
    public Map<String, Object> paletteByThemeKey(@Nullable String themeKey) {
        SiteTheme st = SiteTheme.parse(themeKey);
        if (st == null) {
            return Map.of();
        }
        Map<String, Object> palette = mergedConfig(st.siteId(), st.themeName())
                .get(ThemeSettingsSchema.PALETTE_GROUP);
        return palette != null ? palette : Map.of();
    }

    /**
     * 模板轨道的 {@code theme} 模型（{@code theme.config.<group>.<name>}，§8.2）。
     *
     * @param siteId    站点ID
     * @param themeName 当前活动主题名（{@code site.getTheme()} / {@code mobileTheme}，R2 起为裸主题名）
     */
    public Map<String, Object> themeModel(Long siteId, String themeName) {
        Map<String, Object> model = new LinkedHashMap<>(2);
        model.put("config", mergedConfig(siteId, themeName));
        return model;
    }

    /**
     * 页面作用域设置结构（manifest 2.0 §4.2）：主题页面清单的 {@code settings} 块。
     * 页面不存在或未声明 → {@link ThemeSettingsSchema#EMPTY}。
     *
     * @param themeKey 规范化主题键（{@code siteId/themeName}）
     * @param pageName 页面名（{@code home}、{@code channel}…）
     */
    public ThemeSettingsSchema pageSchema(String themeKey, String pageName) {
        PageManifest manifest = manifestService.getSnapshot().getThemeManifest(themeKey, pageName);
        return manifest != null ? manifest.getSettings() : ThemeSettingsSchema.EMPTY;
    }

    /**
     * 变体作用域设置结构（§4.1/§4.2）：主题清单 {@code layoutVariants} 中该变体的 {@code settings} 块。
     * 变体不存在或未声明 → {@link ThemeSettingsSchema#EMPTY}。
     *
     * @param themeKey   规范化主题键（{@code siteId/themeName}）
     * @param variantKey 变体作用域键（{@code <surface>:<value>}，R3 存值键）
     */
    public ThemeSettingsSchema variantSchema(String themeKey, String variantKey) {
        int colon = variantKey.indexOf(':');
        if (colon <= 0 || colon == variantKey.length() - 1) {
            return ThemeSettingsSchema.EMPTY;
        }
        String surface = variantKey.substring(0, colon);
        String value = variantKey.substring(colon + 1);
        return manifestService.getSnapshot().getThemeLayoutVariants(themeKey).forSurface(surface).stream()
                .filter(item -> value.equals(item.getValue()))
                .map(LayoutVariantCatalog.Item::getSettings)
                .findFirst().orElse(ThemeSettingsSchema.EMPTY);
    }

    /**
     * 保存设置值（按各作用域设置结构校验 → upsert → 作废解析缓存）。仅接受结构声明的作用域/分组/字段
     * （未声明的页面/变体作用域键丢弃，与未声明字段一致——弃警不拒，丢弃项在返回值中列明，H5）；
     * 校验不通过抛 400（字段级）。三个作用域各为全量替换（提交体即该作用域的完整存值）。
     *
     * @param siteId    站点ID
     * @param themeName 主题名（目录名）
     * @param global    全局作用域：分组 → 字段 → 值（{@code null} 视为空）
     * @param pages     页面作用域：页面名 → 分组 → 字段 → 值（{@code null} 视为空）
     * @param variants  变体作用域：{@code surface:value} → 分组 → 字段 → 值（{@code null} 视为空）
     * @return 被丢弃的未声明键（点路径，如 {@code palette.badField}、{@code pages.undeclared}）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> save(Long siteId, String themeName, @Nullable Map<String, Map<String, Object>> global,
                             @Nullable Map<String, Map<String, Map<String, Object>>> pages,
                             @Nullable Map<String, Map<String, Map<String, Object>>> variants) {
        String themeKey = themeKey(siteId, themeName);
        ThemeSettingsSchema schema = schema(themeKey);
        boolean hasScopedSchema = hasPageOrVariantSettings(themeKey);
        if (schema.isEmpty() && !hasScopedSchema) {
            throw new Http400Exception("主题无设置结构: " + themeKey);
        }
        List<String> errors = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        Map<String, Map<String, Object>> acceptedGlobal = acceptScope(schema, global, "", errors, dropped);
        Map<String, Map<String, Map<String, Object>>> acceptedPages = new LinkedHashMap<>();
        if (pages != null) {
            pages.forEach((pageName, values) -> {
                ThemeSettingsSchema pageSchema = pageSchema(themeKey, pageName);
                if (pageSchema.isEmpty()) {
                    if (values != null && !values.isEmpty()) {
                        dropped.add("pages." + pageName);
                    }
                    return;
                }
                Map<String, Map<String, Object>> accepted =
                        acceptScope(pageSchema, values, "pages." + pageName + ".", errors, dropped);
                if (!accepted.isEmpty()) {
                    acceptedPages.put(pageName, accepted);
                }
            });
        }
        Map<String, Map<String, Map<String, Object>>> acceptedVariants = new LinkedHashMap<>();
        if (variants != null) {
            variants.forEach((variantKey, values) -> {
                ThemeSettingsSchema variantSchema = variantSchema(themeKey, variantKey);
                if (variantSchema.isEmpty()) {
                    if (values != null && !values.isEmpty()) {
                        dropped.add("variants." + variantKey);
                    }
                    return;
                }
                Map<String, Map<String, Object>> accepted =
                        acceptScope(variantSchema, values, "variants." + variantKey + ".", errors, dropped);
                if (!accepted.isEmpty()) {
                    acceptedVariants.put(variantKey, accepted);
                }
            });
        }
        if (!errors.isEmpty()) {
            throw new Http400Exception(String.join("; ", errors));
        }
        ThemeState state = getOrCreate(siteId, themeName);
        state.getSettings().setGlobal(acceptedGlobal);
        state.getSettings().setPages(acceptedPages);
        state.getSettings().setVariants(acceptedVariants);
        state.setModified(OffsetDateTime.now());
        state.disassemble();
        mapper.update(state);
        cacheSync.invalidateAll("theme-settings");
        return dropped;
    }

    /**
     * 主题是否声明了任一页面/变体作用域设置结构（全局无 settings 但页面/变体有时，保存仍可进行）。
     */
    private boolean hasPageOrVariantSettings(String themeKey) {
        RegistrySnapshot snapshot = manifestService.getSnapshot();
        boolean pageDeclared = snapshot.getThemeManifests().getOrDefault(themeKey, Map.of()).values().stream()
                .anyMatch(manifest -> !manifest.getSettings().isEmpty());
        if (pageDeclared) {
            return true;
        }
        return snapshot.getThemeLayoutVariants(themeKey).getBySurface().values().stream()
                .flatMap(List::stream).anyMatch(item -> !item.getSettings().isEmpty());
    }

    /**
     * 校验并筛取一个作用域的提交值：仅保留结构声明的分组/字段，字段值经字段类型校验。
     * 校验错误累积进 {@code errors}（带作用域前缀），由调用方统一抛 400；被丢弃的未声明
     * 分组/字段键累积进 {@code dropped}（带作用域前缀的点路径，H5——{@code null} 值为
     * 「清除该字段」语义，不计入）。
     *
     * @param scopePrefix 错误信息的作用域前缀（如 {@code pages.channel.}；全局为空串）
     */
    private Map<String, Map<String, Object>> acceptScope(ThemeSettingsSchema schema,
                                                         @Nullable Map<String, Map<String, Object>> values,
                                                         String scopePrefix, List<String> errors,
                                                         List<String> dropped) {
        Map<String, Map<String, Object>> accepted = new LinkedHashMap<>();
        for (ThemeSettingsSchema.Group group : schema.getGroups()) {
            Map<String, Object> submitted = values != null ? values.get(group.getName()) : null;
            if (submitted == null || submitted.isEmpty()) {
                continue;
            }
            Map<String, Object> declared = new LinkedHashMap<>();
            Map<String, FieldDef> fields = group.getFields();
            submitted.forEach((field, value) -> {
                if (fields.containsKey(field)) {
                    if (value != null) {
                        declared.put(field, value);
                    }
                } else {
                    dropped.add(scopePrefix + group.getName() + "." + field);
                }
            });
            for (FieldError error : fieldValueValidator.validate(fields, declared)) {
                errors.add(scopePrefix + group.getName() + "." + error.field() + ": " + error.message());
            }
            if (!declared.isEmpty()) {
                accepted.put(group.getName(), declared);
            }
        }
        if (values != null) {
            values.forEach((groupName, submitted) -> {
                if (schema.group(groupName) == null && submitted != null && !submitted.isEmpty()) {
                    dropped.add(scopePrefix + groupName);
                }
            });
        }
        return accepted;
    }

    /**
     * 取主题状态行（无则 {@code null}）。
     */
    @Nullable
    public ThemeState findState(Long siteId, String themeName) {
        return mapper.selectByUnique(siteId, themeName);
    }

    /**
     * 取或懒创建主题状态行（首次种子导入 / 首次设置保存，§6.3）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ThemeState getOrCreate(Long siteId, String themeName) {
        ThemeState state = mapper.selectByUnique(siteId, themeName);
        if (state != null) {
            return state;
        }
        state = new ThemeState();
        state.setId(snowflakeSequence.nextId());
        state.setSiteId(siteId);
        state.setThemeName(themeName);
        state.setSeedImported(false);
        OffsetDateTime now = OffsetDateTime.now();
        state.setCreated(now);
        state.setModified(now);
        mapper.insert(state);
        return state;
    }

    /**
     * 标记种子已导入（{@code seed_imported_ = true}，懒建行）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markSeedImported(Long siteId, String themeName) {
        ThemeState state = getOrCreate(siteId, themeName);
        if (!Boolean.TRUE.equals(state.getSeedImported())) {
            state.setSeedImported(true);
            state.setModified(OffsetDateTime.now());
            state.disassemble();
            mapper.update(state);
        }
    }

    /**
     * 记录主题版本（来自 {@code theme.manifest.json}，懒建行）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordVersion(Long siteId, String themeName, @Nullable String version) {
        ThemeState state = getOrCreate(siteId, themeName);
        state.setVersion(version);
        state.setModified(OffsetDateTime.now());
        state.disassemble();
        mapper.update(state);
    }

    /**
     * 结构化配置（R3；{@code GET /api/theme-config} 返回体）：三个作用域均为按结构合并的值
     * （声明默认值 ⊕ 存值，仅含结构声明的作用域/分组/字段，§4.2）。{@code pages} 含主题声明了
     * settings 的每个页面；{@code variants} 含声明了 settings 的每个变体（键 {@code surface:value}）。
     */
    public Map<String, Object> structuredConfig(Long siteId, String themeName) {
        Map<String, Object> out = new LinkedHashMap<>(3);
        out.put("global", mergedConfig(siteId, themeName));
        out.put("pages", mergedPageConfigs(siteId, themeName));
        out.put("variants", mergedVariantConfigs(siteId, themeName));
        return out;
    }

    /**
     * 页面作用域合并值全集（页面名 → 分组 → 字段 → 值）：主题声明了 settings 的每个页面清单，
     * 声明默认值 ⊕ {@code pages.<pageName>} 存值。
     */
    public Map<String, Map<String, Map<String, Object>>> mergedPageConfigs(Long siteId, String themeName) {
        String themeKey = themeKey(siteId, themeName);
        ThemeState.SettingValues stored = storedSettings(siteId, themeName);
        Map<String, Map<String, Map<String, Object>>> pages = new LinkedHashMap<>();
        manifestService.getSnapshot().getThemeManifests().getOrDefault(themeKey, Map.of())
                .forEach((pageName, manifest) -> {
                    if (!manifest.getSettings().isEmpty()) {
                        pages.put(pageName, merge(manifest.getSettings(),
                                stored.getPages().getOrDefault(pageName, Map.of())));
                    }
                });
        return pages;
    }

    /**
     * 变体作用域合并值全集（{@code surface:value} → 分组 → 字段 → 值）：主题清单声明了 settings 的
     * 每个布局变体，声明默认值 ⊕ {@code variants.<surface>:<value>} 存值。
     */
    public Map<String, Map<String, Map<String, Object>>> mergedVariantConfigs(Long siteId, String themeName) {
        String themeKey = themeKey(siteId, themeName);
        ThemeState.SettingValues stored = storedSettings(siteId, themeName);
        Map<String, Map<String, Map<String, Object>>> variants = new LinkedHashMap<>();
        manifestService.getSnapshot().getThemeLayoutVariants(themeKey).getBySurface()
                .forEach((surface, items) -> items.forEach(item -> {
                    if (!item.getSettings().isEmpty()) {
                        String variantKey = surface + ":" + item.getValue();
                        variants.put(variantKey, merge(item.getSettings(),
                                stored.getVariants().getOrDefault(variantKey, Map.of())));
                    }
                }));
        return variants;
    }

    /**
     * 模板轨道的 {@code pageConfig} 模型（§4.2）：匹配页面的页面作用域合并值 ⊕ 活动变体的变体作用域
     * 合并值（同名分组按字段叠加，变体胜出）。由前台渲染支撑（{@code FrontendInterceptor}）按
     * 渲染上下文（模板名 → 页面清单，控制器写入的活动变体键）解析后注入模型。
     *
     * @param siteId     站点ID
     * @param themeName  当前活动主题名（裸主题名）
     * @param pageName   匹配的页面名（无页面清单匹配 → {@code null}）
     * @param variantKey 活动变体作用域键（{@code surface:value}；无 → {@code null}）
     */
    public Map<String, Map<String, Object>> pageConfig(Long siteId, String themeName,
                                                       @Nullable String pageName, @Nullable String variantKey) {
        String themeKey = themeKey(siteId, themeName);
        ThemeState.SettingValues stored = storedSettings(siteId, themeName);
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (pageName != null) {
            ThemeSettingsSchema pageSchema = pageSchema(themeKey, pageName);
            if (!pageSchema.isEmpty()) {
                result.putAll(merge(pageSchema, stored.getPages().getOrDefault(pageName, Map.of())));
            }
        }
        if (variantKey != null) {
            ThemeSettingsSchema variantSchema = variantSchema(themeKey, variantKey);
            if (!variantSchema.isEmpty()) {
                merge(variantSchema, stored.getVariants().getOrDefault(variantKey, Map.of()))
                        .forEach((group, values) ->
                                result.merge(group, values, (pageValues, variantValues) -> {
                                    Map<String, Object> combined = new LinkedHashMap<>(pageValues);
                                    combined.putAll(variantValues);
                                    return combined;
                                }));
            }
        }
        return result;
    }

    private Map<String, Map<String, Object>> storedValues(Long siteId, String themeName) {
        return storedSettings(siteId, themeName).getGlobal();
    }

    private ThemeState.SettingValues storedSettings(Long siteId, String themeName) {
        ThemeState state = mapper.selectByUnique(siteId, themeName);
        return state != null ? state.getSettings() : new ThemeState.SettingValues();
    }

    /**
     * 规范化主题键（{@code siteId/themeName}），与 {@link ThemeDirScanner} / 快照键一致。
     */
    public static String themeKey(Long siteId, String themeName) {
        return siteId + "/" + themeName;
    }

    /**
     * 拆解主题键 {@code siteId/themeName}（前导斜杠容忍）。无法解析时为 {@code null}。
     */
    private record SiteTheme(Long siteId, String themeName) {
        @Nullable
        static SiteTheme parse(@Nullable String themeKey) {
            if (themeKey == null || themeKey.isBlank()) {
                return null;
            }
            String normalized = themeKey.startsWith("/") ? themeKey.substring(1) : themeKey;
            int slash = normalized.indexOf('/');
            if (slash <= 0 || slash == normalized.length() - 1) {
                return null;
            }
            try {
                return new SiteTheme(Long.parseLong(normalized.substring(0, slash)), normalized.substring(slash + 1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
