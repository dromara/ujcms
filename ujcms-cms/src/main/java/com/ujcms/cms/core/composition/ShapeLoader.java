package com.ujcms.cms.core.composition;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.ujcms.cms.core.composition.fields.FieldDef;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ujcms.cms.core.support.Constants.MAPPER;

/**
 * 组件（Shape v2）加载器。解析 Shape JSON、按已发布 Schema 校验、并执行文件内结构规则
 * （嵌套约束、titleField 存在性、{@code data} 模板的 {@code $settings.*} 引用与被引设置的类型匹配）。
 * 失败抛出 {@link CompositionLoadException}，错误信息携带制品来源。
 *
 * @author PONY
 */
@Component
public class ShapeLoader {
    /**
     * 结构类型：list
     */
    private static final String TYPE_LIST = "list";
    /**
     * 结构类型：object
     */
    private static final String TYPE_OBJECT = "object";
    /**
     * 默认标题字段类型
     */
    private static final String TYPE_TEXT = "text";
    /**
     * 设置引用前缀（{@code data} 模板值 {@code "$settings.channel"}）
     */
    public static final String SETTINGS_PREFIX = "$settings.";

    private final CompositionSchemas schemas;

    public ShapeLoader(CompositionSchemas schemas) {
        this.schemas = schemas;
    }

    /**
     * 加载一个 Shape。
     *
     * @param json       Shape JSON 文本
     * @param source     来源命名空间（{@link Shape#SOURCE_THEME_PREFIX} + 主题键）
     * @param sourceDesc 来源描述（文件路径/资源名，用于错误信息）
     * @return 构建的 Shape
     */
    public Shape load(String json, String source, String sourceDesc) {
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw new CompositionLoadException(sourceDesc, e);
        }
        List<String> schemaErrors = schemas.validate(CompositionSchemas.SHAPE, node);
        if (!schemaErrors.isEmpty()) {
            throw new CompositionLoadException(sourceDesc, schemaErrors);
        }
        Map<String, Object> raw = MAPPER.convertValue(node, new TypeReference<>() {
        });
        raw.remove("$schema");

        String name = (String) raw.get("name");
        String version = (String) raw.get("version");
        Map<String, FieldDef> itemFields = toFieldDefs(raw.get("item"));
        Map<String, FieldDef> settings = toFieldDefs(raw.get("settings"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = raw.get("data") instanceof Map<?, ?> map ? (Map<String, Object>) map : null;

        List<String> errors = new ArrayList<>();
        itemFields.forEach((fieldName, def) -> validateStructure("item." + fieldName, def, errors));
        settings.forEach((fieldName, def) -> validateStructure("settings." + fieldName, def, errors));
        if (data != null) {
            validateData(data, settings, errors);
        }

        String titleField = raw.get("titleField") instanceof String str ? str : null;
        if (titleField != null && !itemFields.containsKey(titleField)) {
            errors.add("titleField 引用了不存在的条目字段: " + titleField);
        }
        if (titleField == null) {
            titleField = itemFields.entrySet().stream()
                    .filter(e -> TYPE_TEXT.equals(e.getValue().getType()))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
        }
        if (!errors.isEmpty()) {
            throw new CompositionLoadException(sourceDesc, errors);
        }
        return new Shape(name, version,
                raw.get("description") instanceof String str ? str : null,
                toStringMap(raw.get("label")),
                toStringList(raw.get("tags")),
                titleField, itemFields, settings, data,
                toMapList(raw.get("migrations")),
                toMapList(raw.get("examples")),
                source, raw);
    }

    /**
     * 嵌套约束（slot-card §7.4，settings 与 item 同规则）：object 仅含非结构字段；
     * list 的元素为非结构字段或“仅含非结构字段的 object”。
     */
    private void validateStructure(String path, FieldDef def, List<String> errors) {
        String type = def.getType();
        if (TYPE_LIST.equals(type)) {
            FieldDef of = def.getFieldDef("of");
            if (of == null) {
                errors.add(path + ": list 缺少 of(元素类型)");
                return;
            }
            if (TYPE_LIST.equals(of.getType())) {
                errors.add(path + ": 不支持 list 嵌套 list");
            } else if (TYPE_OBJECT.equals(of.getType())) {
                requireNonStructuralFields(path + ".of", of, errors);
            }
        } else if (TYPE_OBJECT.equals(type)) {
            requireNonStructuralFields(path, def, errors);
        }
    }

    private void requireNonStructuralFields(String path, FieldDef objectDef, List<String> errors) {
        Map<String, FieldDef> fields = objectDef.getFieldDefs("fields");
        if (fields.isEmpty()) {
            errors.add(path + ": object 缺少 fields(子字段)");
            return;
        }
        fields.forEach((name, def) -> {
            String type = def.getType();
            if (TYPE_LIST.equals(type) || TYPE_OBJECT.equals(type)) {
                errors.add(path + "." + name + ": 不支持更深的结构嵌套(object 内仅允许非结构字段)");
            }
        });
    }

    /**
     * 数据模板校验（component-settings §4.1）：全部 {@code $settings.*} 引用须指向已声明设置，
     * {@code $ctx.*} 引用须在白名单内；{@code mode/items/pinned/limit} 的被引设置须为匹配类型
     * （enum / list / list-of-引用 / number）。加载期失败——坏模板不进注册表。
     */
    private void validateData(Map<String, Object> data, Map<String, FieldDef> settings, List<String> errors) {
        if (data.get("where") instanceof Map<?, ?> where) {
            where.forEach((key, value) -> {
                if (value instanceof String str) {
                    requireResolvableRef("data.where." + key, str, settings, errors);
                }
            });
        }
        if (data.get("orderBy") instanceof String orderBy) {
            requireResolvableRef("data.orderBy", orderBy, settings, errors);
        }
        if (data.get("limit") instanceof String limit) {
            FieldDef def = requireSettingRef("data.limit", limit, settings, errors);
            if (def != null && !"number".equals(def.getType())) {
                errors.add("data.limit 引用的设置须为 number 类型: " + limit);
            }
        }
        if (data.get("mode") instanceof String mode) {
            FieldDef def = requireSettingRef("data.mode", mode, settings, errors);
            if (def != null && !"enum".equals(def.getType())) {
                errors.add("data.mode 引用的设置须为 enum 类型: " + mode);
            }
        }
        if (data.get("items") instanceof String items) {
            FieldDef def = requireSettingRef("data.items", items, settings, errors);
            if (def != null && !TYPE_LIST.equals(def.getType())) {
                errors.add("data.items 引用的设置须为 list 类型: " + items);
            }
        }
        if (data.get("pinned") instanceof String pinned) {
            FieldDef def = requireSettingRef("data.pinned", pinned, settings, errors);
            if (def != null) {
                FieldDef of = TYPE_LIST.equals(def.getType()) ? def.getFieldDef("of") : null;
                String ofType = of != null ? of.getType() : "";
                if (!"articleRef".equals(ofType) && !"channelRef".equals(ofType)) {
                    errors.add("data.pinned 引用的设置须为 list-of-articleRef/channelRef: " + pinned);
                }
            }
        }
    }

    /**
     * 校验字符串值中的引用可解析：{@code $settings.*} 须为已声明设置；{@code $ctx.*} 须在白名单内。
     * 字面量放行。
     */
    private static void requireResolvableRef(String path, String value, Map<String, FieldDef> settings,
                                             List<String> errors) {
        if (value.startsWith(SETTINGS_PREFIX)) {
            requireSettingRef(path, value, settings, errors);
        } else if (value.startsWith(CtxParams.PREFIX)
                && !CtxParams.isAllowed(value.substring(CtxParams.PREFIX.length()))) {
            errors.add(path + " 引用了白名单外的上下文参数: " + value);
        }
    }

    /**
     * 解析 {@code $settings.<name>} 引用为设置定义；未声明记错误并返回 {@code null}。
     */
    @Nullable
    private static FieldDef requireSettingRef(String path, String ref, Map<String, FieldDef> settings,
                                              List<String> errors) {
        String name = ref.startsWith(SETTINGS_PREFIX) ? ref.substring(SETTINGS_PREFIX.length()) : "";
        FieldDef def = settings.get(name);
        if (def == null) {
            errors.add(path + " 引用了未声明的设置: " + ref);
        }
        return def;
    }

    static Map<String, FieldDef> toFieldDefs(Object value) {
        Map<String, FieldDef> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (k instanceof String key && v instanceof Map<?, ?> defMap) {
                    Map<String, Object> def = new LinkedHashMap<>(defMap.size());
                    defMap.forEach((dk, dv) -> {
                        if (dk instanceof String s) {
                            def.put(s, dv);
                        }
                    });
                    result.put(key, new FieldDef(def));
                }
            });
        }
        return result;
    }

    static Map<String, String> toStringMap(Object value) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (k instanceof String key && v instanceof String str) {
                    result.put(key, str);
                }
            });
        }
        return result;
    }

    static List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> toMapList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
        }
        return result;
    }
}
