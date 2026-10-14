package com.ujcms.cms.core.composition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 快照差异摘要（加载记录 {@code diff_summary_json_}）。对比新旧快照的制品级内容散列
 * （{@link RegistrySnapshot#getSourceHashes()}），按操作员视角归类：
 * 组件（{@code shape:*}）、页面（{@code manifest:*}）、主题级声明
 * （settings / layout-variants / link-slots，按主题键归并为 themes 类）。
 *
 * <p>产出结构（空类别/空操作省略，每列表最多 {@link #MAX_NAMES} 个名称，超出置 {@code truncated}）：
 * <pre>{@code {"shapes":{"added":[…],"removed":[…],"changed":[…]},"pages":{…},"themes":{…}}}</pre>
 * 无差异为 {@code {}}（重载发生但内容未变，健康页展示「无变更」）；旧快照为空
 * （进程首次加载，无对比基线）返回 {@code null}。
 *
 * @author PONY
 */
public final class SnapshotDiff {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /**
     * 每列表最多记录的名称数（防御性上限；超出截断并置 truncated 标记）
     */
    private static final int MAX_NAMES = 20;

    private static final String CATEGORY_SHAPES = "shapes";
    private static final String CATEGORY_PAGES = "pages";
    private static final String CATEGORY_THEMES = "themes";
    private static final String OP_ADDED = "added";
    private static final String OP_REMOVED = "removed";
    private static final String OP_CHANGED = "changed";

    private SnapshotDiff() {
    }

    /**
     * 对比新旧快照，产出差异摘要 JSON。
     *
     * @param previous 被替换的快照
     * @param next     即将生效的快照
     * @return 差异摘要 JSON；旧快照为空（无对比基线）为 {@code null}
     */
    @Nullable
    public static String summarize(RegistrySnapshot previous, RegistrySnapshot next) {
        Map<String, String> before = previous.getSourceHashes();
        Map<String, String> after = next.getSourceHashes();
        if (before.isEmpty()) {
            return null;
        }
        Map<String, Map<String, Set<String>>> diff = new LinkedHashMap<>();
        after.forEach((key, hash) -> {
            String old = before.get(key);
            if (old == null) {
                collect(diff, key, OP_ADDED);
            } else if (!old.equals(hash)) {
                collect(diff, key, OP_CHANGED);
            }
        });
        before.keySet().forEach(key -> {
            if (!after.containsKey(key)) {
                collect(diff, key, OP_REMOVED);
            }
        });
        return serialize(diff);
    }

    private static void collect(Map<String, Map<String, Set<String>>> diff, String artifactKey, String op) {
        String category = artifactKey.startsWith("shape:") ? CATEGORY_SHAPES
                : artifactKey.startsWith("manifest:") ? CATEGORY_PAGES : CATEGORY_THEMES;
        // 名称取制品键最后一段：Shape/页面名，或主题级声明的主题键（主题键不含冒号）
        String name = artifactKey.substring(artifactKey.lastIndexOf(':') + 1);
        diff.computeIfAbsent(category, k -> new LinkedHashMap<>())
                .computeIfAbsent(op, k -> new LinkedHashSet<>()).add(name);
    }

    @Nullable
    private static String serialize(Map<String, Map<String, Set<String>>> diff) {
        Map<String, Object> root = new LinkedHashMap<>();
        boolean truncated = false;
        for (String category : List.of(CATEGORY_SHAPES, CATEGORY_PAGES, CATEGORY_THEMES)) {
            Map<String, Set<String>> ops = diff.get(category);
            if (ops == null) {
                continue;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            for (String op : List.of(OP_ADDED, OP_REMOVED, OP_CHANGED)) {
                Set<String> names = ops.get(op);
                if (names == null || names.isEmpty()) {
                    continue;
                }
                truncated |= names.size() > MAX_NAMES;
                out.put(op, names.stream().limit(MAX_NAMES).toList());
            }
            root.put(category, out);
        }
        if (truncated) {
            root.put("truncated", true);
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            // 摘要与审计一样尽力而为：序列化失败不应影响重载
            return null;
        }
    }
}
