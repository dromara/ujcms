package com.ujcms.cms.core.composition.fields;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 字段类型注册表。收集容器内全部 {@link FieldType} Bean（含商业模块贡献的字段类型）。
 *
 * @author PONY
 */
@Component
public class FieldTypeRegistry {
    private final Map<String, FieldType> types;

    public FieldTypeRegistry(List<FieldType> fieldTypes) {
        Map<String, FieldType> map = new LinkedHashMap<>(fieldTypes.size());
        for (FieldType type : fieldTypes) {
            FieldType existed = map.put(type.name(), type);
            if (existed != null) {
                throw new IllegalStateException("Duplicate field type: " + type.name());
            }
        }
        this.types = Collections.unmodifiableMap(map);
    }

    @Nullable
    public FieldType get(String name) {
        return types.get(name);
    }

    public boolean contains(String name) {
        return types.containsKey(name);
    }

    public Set<String> names() {
        return types.keySet();
    }
}
