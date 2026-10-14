package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.fields.FieldDef;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主题设置结构（§8.2）。解析自 {@code theme.manifest.json#settings}：分组（{@code groups}）→
 * 字段（{@code fields}，复用 §7 字段类型词汇）。结构是文件事实，经注册表管道装载、随主题清单热重载；
 * 操作员设置的<b>值</b>另存于 {@code ujcms_theme_state.settings_json_}，读取期合并在声明默认值之上。
 *
 * <p>调色板令牌按约定解析：{@code palette} 分组内字段名即 {@code themeColor} 字段可引用的令牌名（§7.2）。
 *
 * @author PONY
 */
public final class ThemeSettingsSchema {
    /**
     * 调色板分组名（{@code themeColor} 令牌按约定解析其字段，§8.2）
     */
    public static final String PALETTE_GROUP = "palette";

    /**
     * 空设置结构（无清单、无 settings 块的主题）
     */
    public static final ThemeSettingsSchema EMPTY = new ThemeSettingsSchema(List.of());

    private final List<Group> groups;
    private final Map<String, Group> byName;

    private ThemeSettingsSchema(List<Group> groups) {
        this.groups = List.copyOf(groups);
        Map<String, Group> map = new LinkedHashMap<>(groups.size());
        groups.forEach(g -> map.put(g.getName(), g));
        this.byName = Collections.unmodifiableMap(map);
    }

    /**
     * 解析 {@code settings} 节点（清单中的 {@code settings} 对象，{@code null}/缺失 → 空结构）。
     *
     * @param settingsNode 清单 {@code settings} 节点（{@code {"groups": [...]}}）
     * @return 设置结构
     */
    @SuppressWarnings("unchecked")
    public static ThemeSettingsSchema parse(@Nullable Object settingsNode) {
        if (!(settingsNode instanceof Map<?, ?> settings)) {
            return EMPTY;
        }
        Object groupsNode = settings.get("groups");
        if (!(groupsNode instanceof List<?> groupList)) {
            return EMPTY;
        }
        List<Group> groups = new ArrayList<>(groupList.size());
        for (Object item : groupList) {
            if (!(item instanceof Map<?, ?> groupMap)) {
                continue;
            }
            String name = groupMap.get("name") instanceof String s ? s : null;
            if (name == null || name.isBlank()) {
                continue;
            }
            Map<String, String> label = ShapeLoader.toStringMap(groupMap.get("label"));
            Map<String, FieldDef> fields = ShapeLoader.toFieldDefs(groupMap.get("fields"));
            Map<String, Object> raw = new LinkedHashMap<>((Map<String, Object>) groupMap);
            groups.add(new Group(name, label, fields, raw));
        }
        return new ThemeSettingsSchema(groups);
    }

    public List<Group> getGroups() {
        return groups;
    }

    public boolean isEmpty() {
        return groups.isEmpty();
    }

    @Nullable
    public Group group(String name) {
        return byName.get(name);
    }

    /**
     * 声明默认值（分组名 → 字段名 → 默认值；仅含声明了 {@code default} 的字段）。
     */
    public Map<String, Map<String, Object>> defaults() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>(groups.size());
        for (Group group : groups) {
            Map<String, Object> groupDefaults = new LinkedHashMap<>();
            group.getFields().forEach((fieldName, def) -> {
                Object dv = def.getDefaultValue();
                if (dv != null) {
                    groupDefaults.put(fieldName, dv);
                }
            });
            if (!groupDefaults.isEmpty()) {
                result.put(group.getName(), groupDefaults);
            }
        }
        return result;
    }

    /**
     * 分组原始定义列表（控制台据此用字段输入组件生成设置表单）。
     */
    public List<Map<String, Object>> rawGroups() {
        return groups.stream().map(Group::getRaw).toList();
    }

    /**
     * 设置分组
     */
    public static final class Group {
        private final String name;
        private final Map<String, String> label;
        private final Map<String, FieldDef> fields;
        private final Map<String, Object> raw;

        Group(String name, Map<String, String> label, Map<String, FieldDef> fields, Map<String, Object> raw) {
            this.name = name;
            this.label = Collections.unmodifiableMap(new LinkedHashMap<>(label));
            this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
            this.raw = Collections.unmodifiableMap(new LinkedHashMap<>(raw));
        }

        public String getName() {
            return name;
        }

        public Map<String, String> getLabel() {
            return label;
        }

        public Map<String, FieldDef> getFields() {
            return fields;
        }

        public Map<String, Object> getRaw() {
            return raw;
        }
    }
}
