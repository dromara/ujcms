package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.fields.FieldDef;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组件（Shape v2，component-settings §4.1）。开发者/主题作者在代码或主题包中以 JSON 声明：
 * 实例设置结构（{@code settings}）、可选数据模板（{@code data}）与条目字段结构（{@code item}）。
 * 面板即组件实例——操作员按 settings 填表单，服务端以 {@code $settings.*} 替换执行 data 模板。
 * 不可变；由 {@link ShapeLoader} 构建。
 *
 * @author PONY
 */
public final class Shape {
    /**
     * 主题来源前缀（{@code theme:<siteId>/<themeName>}）。Shape 只存在于主题命名空间
     * （theme-owned-shapes D15：核心命名空间已退役，声明随渲染它的主题一起分发）。
     */
    public static final String SOURCE_THEME_PREFIX = "theme:";

    private final String name;
    private final String version;
    @Nullable
    private final String description;
    private final Map<String, String> label;
    private final List<String> tags;
    @Nullable
    private final String titleField;
    private final Map<String, FieldDef> fields;
    private final Map<String, FieldDef> settings;
    @Nullable
    private final Map<String, Object> data;
    private final List<Map<String, Object>> migrations;
    private final List<Map<String, Object>> examples;
    private final String source;
    private final Map<String, Object> raw;

    public Shape(String name, String version, @Nullable String description, Map<String, String> label,
                 List<String> tags, @Nullable String titleField, Map<String, FieldDef> fields,
                 List<Map<String, Object>> migrations, List<Map<String, Object>> examples,
                 String source, Map<String, Object> raw) {
        this(name, version, description, label, tags, titleField, fields, Map.of(), null,
                migrations, examples, source, raw);
    }

    public Shape(String name, String version, @Nullable String description, Map<String, String> label,
                 List<String> tags, @Nullable String titleField, Map<String, FieldDef> fields,
                 Map<String, FieldDef> settings, @Nullable Map<String, Object> data,
                 List<Map<String, Object>> migrations, List<Map<String, Object>> examples,
                 String source, Map<String, Object> raw) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.label = Collections.unmodifiableMap(new LinkedHashMap<>(label));
        this.tags = List.copyOf(tags);
        this.titleField = titleField;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        this.settings = Collections.unmodifiableMap(new LinkedHashMap<>(settings));
        this.data = data != null ? Collections.unmodifiableMap(new LinkedHashMap<>(data)) : null;
        this.migrations = List.copyOf(migrations);
        this.examples = List.copyOf(examples);
        this.source = source;
        this.raw = Collections.unmodifiableMap(new LinkedHashMap<>(raw));
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public Map<String, String> getLabel() {
        return label;
    }

    public List<String> getTags() {
        return tags;
    }

    /**
     * 后台列表标题字段：声明的 {@code titleField}，缺省为 {@code item} 中第一个 {@code text} 字段；
     * 无 text 字段时为 {@code null}（卡片 {@code name_} 回退到 Shape 名称）。
     */
    @Nullable
    public String getTitleField() {
        return titleField;
    }

    /**
     * 条目字段结构（JSON 的 {@code item} 块；v1 的 {@code fields}）。投影类型化、渲染器类型生成
     * 与手工条目共用；纯设置组件为空 Map。
     */
    public Map<String, FieldDef> getFields() {
        return fields;
    }

    /**
     * 实例设置结构（component-settings §4.1）。未声明为空 Map。
     */
    public Map<String, FieldDef> getSettings() {
        return settings;
    }

    /**
     * 数据模板（component-settings §4.1：{@code from/where/orderBy/limit/missing/projection}
     * + {@code mode/items/pinned} 设置引用）。未声明为 {@code null}（纯设置组件）。
     */
    @Nullable
    public Map<String, Object> getData() {
        return data;
    }

    public List<Map<String, Object>> getMigrations() {
        return migrations;
    }

    public List<Map<String, Object>> getExamples() {
        return examples;
    }

    public String getSource() {
        return source;
    }

    /**
     * 原始定义（目录接口原样返回，避免重复建模）
     */
    public Map<String, Object> getRaw() {
        return raw;
    }

    /**
     * 调色板呈现元数据（visual-builder §7.1 可选块）：icon / group / order / thumbnail
     * （thumbnail 按命名空间解析——{@code RegionService.resolveThumbnail}，P6/D11）。未声明时为空 Map。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPresentation() {
        Object value = raw.get("presentation");
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    /**
     * 从卡片数据中取标题（用于 {@code name_} 冗余）。无标题字段或值非字符串时返回空串。
     */
    public String resolveTitle(Map<String, Object> data) {
        if (titleField == null) {
            return "";
        }
        Object value = data.get(titleField);
        return value instanceof String str ? str : "";
    }
}
