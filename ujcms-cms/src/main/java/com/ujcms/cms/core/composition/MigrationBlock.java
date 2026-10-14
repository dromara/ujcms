package com.ujcms.cms.core.composition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 迁移块（§13.2）。Shape JSON {@code migrations[]} 中单个块的类型化视图：{@code from} 范围 +
 * 声明式动词（{@code rename}/{@code default}/{@code remove}/{@code transform}）。
 *
 * <p>读取期投影（{@link ShapeMigrationService#project}）按 {@code from} 升序套用匹配块；
 * 兼容性分级（{@link ShapeCompatibilityClassifier}）与重叠校验（{@link SnapshotValidator}）共用此解析。
 *
 * @param from      适用的旧版本范围
 * @param rename    旧字段名 → 新字段名
 * @param defaults  字段名 → 默认值（旧卡片缺该字段时补入）
 * @param remove    需移除的字段名
 * @param transform 字段名 → 变换规格（声明式 DSL 占位，复杂情形交由 {@code @ShapeMigration} 处理器）
 * @author PONY
 */
public record MigrationBlock(Semver.Range from, Map<String, String> rename, Map<String, Object> defaults,
                             List<String> remove, Map<String, Object> transform) {
    public MigrationBlock {
        rename = Collections.unmodifiableMap(new LinkedHashMap<>(rename));
        defaults = Collections.unmodifiableMap(new LinkedHashMap<>(defaults));
        remove = List.copyOf(remove);
        transform = Collections.unmodifiableMap(new LinkedHashMap<>(transform));
    }

    /**
     * 解析 Shape 的全部迁移块并按 {@code from} 范围下界升序排序（多块匹配时的套用次序，§13.2）。
     *
     * @param migrations Shape 的 {@code migrations} 原始列表
     * @return 排序后的迁移块列表
     */
    public static List<MigrationBlock> parse(List<Map<String, Object>> migrations) {
        List<MigrationBlock> blocks = new ArrayList<>(migrations.size());
        for (Map<String, Object> raw : migrations) {
            blocks.add(parseOne(raw));
        }
        blocks.sort(Comparator.comparing(block -> block.from().lowerBound()));
        return blocks;
    }

    private static MigrationBlock parseOne(Map<String, Object> raw) {
        Semver.Range from = Semver.Range.parse(stringOf(raw.get("from")));
        return new MigrationBlock(from, stringMap(raw.get("rename")), objectMap(raw.get("default")),
                stringList(raw.get("remove")), objectMap(raw.get("transform")));
    }

    /**
     * 本块在任意动词中触及的字段名集合（重叠冲突检测用）。{@code rename} 取旧字段名（被改名的源）。
     */
    public java.util.Set<String> affectedFields() {
        java.util.Set<String> fields = new java.util.LinkedHashSet<>();
        fields.addAll(rename.keySet());
        fields.addAll(defaults.keySet());
        fields.addAll(remove);
        fields.addAll(transform.keySet());
        return fields;
    }

    private static String stringOf(Object value) {
        return value instanceof String str ? str : "";
    }

    private static Map<String, String> stringMap(Object value) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (k instanceof String key && v instanceof String val) {
                    result.put(key, val);
                }
            });
        }
        return result;
    }

    private static Map<String, Object> objectMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (k instanceof String key) {
                    result.put(key, v);
                }
            });
        }
        return result;
    }

    private static List<String> stringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof String str) {
                    result.add(str);
                }
            }
        }
        return result;
    }
}
