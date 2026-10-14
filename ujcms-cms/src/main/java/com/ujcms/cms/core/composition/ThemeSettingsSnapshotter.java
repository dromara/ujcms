package com.ujcms.cms.core.composition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ujcms.cms.core.domain.ThemeState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.ujcms.cms.core.support.Constants.MAPPER;

/**
 * 设置快照器（theme-seed-export §7.4 / D21）：把 {@code theme_state.settings_json_} 的三作用域
 * <b>已存值</b>写入发射清单<b>已声明</b>字段的 {@code default}——「把我配置的外观随包发行」。
 * <ul>
 *   <li>全局作用域 → {@code theme.manifest.json#settings}；变体作用域 →
 *       {@code theme.manifest.json#layoutVariants.<surface>[].settings}（{@link #applyThemeManifest}）；
 *       页面作用域 → {@code pages/<name>.manifest.json#settings}（{@link #applyPageManifest}，
 *       页面清单是独立文件——扫描器事实）。</li>
 *   <li>只写清单声明的分组/字段：未声明的已存键丢弃 + 计数（镜像设置保存的 H5 口径，弃警不拒），
 *       无存值的字段保持作者默认值（读取期合并本就回退清单默认，无需移除语义）。</li>
 *   <li>值<b>原样</b>写入，绝不令牌化/反向资源遍历——清单默认值是读取期合并的文件事实，
 *       导入与渲染都无令牌展开机制（D9 只覆盖面板设置）；指向本站存储的 URL 值与站内ID引用值
 *       计 warning 级可移植性提示（同站可用，跨站为源站指向/悬挂引用，镜像 D19 的软降级）。</li>
 *   <li>确定性（D4）：只改已声明字段节点的 {@code default} 属性，键序保持清单作者原序
 *       （清单是操作员文档，不做字典序重排）；同一存值两次快照字节一致。</li>
 * </ul>
 * 一次导出/预览一实例（累积字段数/丢弃键/涉及清单/提示，{@link #report} 收束）。
 *
 * @author PONY
 */
public final class ThemeSettingsSnapshotter {

    /**
     * 主题清单文件名（报告 {@code manifests} 项）
     */
    public static final String THEME_MANIFEST = "theme.manifest.json";

    private static final String SETTINGS = "settings";
    private static final String GROUPS = "groups";
    private static final String FIELDS = "fields";
    private static final String DEFAULT = "default";

    private final ThemeState.SettingValues stored;
    /**
     * 上传存储展示前缀（无尾斜杠；本站上传 URL 识别，§7.4 可移植性提示）
     */
    private final String uploadPrefix;
    /**
     * 主题目录 URL 前缀（{@code <templateStorageUrl>/<siteId>/<theme>}，无尾斜杠）
     */
    private final String themePrefix;

    private int fields;
    private final List<String> dropped = new ArrayList<>();
    private final List<String> manifests = new ArrayList<>();
    private final List<ExportPlan.RefIssue> issues = new ArrayList<>();
    private final Set<String> appliedPages = new LinkedHashSet<>();

    public ThemeSettingsSnapshotter(ThemeState.SettingValues stored, String uploadPrefix, String themePrefix) {
        this.stored = stored;
        this.uploadPrefix = stripTrailingSlash(uploadPrefix);
        this.themePrefix = stripTrailingSlash(themePrefix);
    }

    /**
     * 应用全局 + 变体作用域到主题清单（就地改写传入节点）。
     *
     * @return 是否有 {@code default} 语义变化（与既有默认值相同的写入不算变化）
     */
    public boolean applyThemeManifest(ObjectNode manifest) {
        int before = fields;
        boolean changed = applyScope(manifest.get(SETTINGS), stored.getGlobal(), "");
        for (Map.Entry<String, Map<String, Map<String, Object>>> entry : stored.getVariants().entrySet()) {
            String variantKey = entry.getKey();
            Map<String, Map<String, Object>> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                continue;
            }
            JsonNode settingsNode = variantSettingsNode(manifest, variantKey);
            if (settingsNode == null) {
                dropped.add("variants." + variantKey);
                continue;
            }
            changed |= applyScope(settingsNode, values, "variants." + variantKey + ".");
        }
        if (fields > before) {
            manifests.add(THEME_MANIFEST);
        }
        return changed;
    }

    /**
     * 应用一个页面作用域到页面清单（就地改写传入节点；页面名取清单 {@code name} 字段——
     * 扫描器同口径，与文件名无关）。无该页面存值时不做任何事。
     *
     * @param relpath 页面清单相对路径（{@code pages/<file>}，报告 {@code manifests} 项）
     * @return 是否有 {@code default} 语义变化
     */
    public boolean applyPageManifest(String relpath, ObjectNode pageManifest) {
        String pageName = pageManifest.path("name").asText("");
        Map<String, Map<String, Object>> values = pageName.isEmpty() ? null : stored.getPages().get(pageName);
        if (values == null || values.isEmpty()) {
            return false;
        }
        appliedPages.add(pageName);
        if (groupsOf(pageManifest.get(SETTINGS)) == null) {
            // 页面清单未声明 settings：整个作用域丢弃（镜像保存侧 pages.<name> 丢弃口径）
            dropped.add("pages." + pageName);
            return false;
        }
        int before = fields;
        boolean changed = applyScope(pageManifest.get(SETTINGS), values, "pages." + pageName + ".");
        if (fields > before) {
            manifests.add(relpath);
        }
        return changed;
    }

    /**
     * 收束报告：有存值但从未遇到对应页面清单的页面作用域此刻落为丢弃。
     */
    public ExportPlan.SettingsSnapshot report() {
        for (Map.Entry<String, Map<String, Map<String, Object>>> entry : stored.getPages().entrySet()) {
            if (!appliedPages.contains(entry.getKey())
                    && entry.getValue() != null && !entry.getValue().isEmpty()) {
                dropped.add("pages." + entry.getKey());
            }
        }
        return new ExportPlan.SettingsSnapshot(fields, List.copyOf(dropped), List.copyOf(manifests),
                List.copyOf(issues));
    }

    // ----- 作用域应用 -----

    /**
     * 把一个作用域的存值写入一个设置结构节点（{@code {"groups":[…]}}）：仅声明的分组/字段，
     * 未声明即丢弃（带作用域前缀的点路径）。
     */
    private boolean applyScope(@Nullable JsonNode settingsNode, Map<String, Map<String, Object>> values,
                               String prefix) {
        boolean changed = false;
        ArrayNode groups = groupsOf(settingsNode);
        for (Map.Entry<String, Map<String, Object>> groupEntry : values.entrySet()) {
            String groupName = groupEntry.getKey();
            Map<String, Object> groupValues = groupEntry.getValue();
            if (groupValues == null || groupValues.isEmpty()) {
                continue;
            }
            ObjectNode fieldDefs = fieldDefsOf(groups, groupName);
            if (fieldDefs == null) {
                dropped.add(prefix + groupName);
                continue;
            }
            for (Map.Entry<String, Object> fieldEntry : groupValues.entrySet()) {
                String fieldName = fieldEntry.getKey();
                Object value = fieldEntry.getValue();
                if (value == null) {
                    // 保存侧不存 null（「清除该字段」语义）——遇到即视同无存值
                    continue;
                }
                if (!(fieldDefs.get(fieldName) instanceof ObjectNode fieldDef)) {
                    dropped.add(prefix + groupName + "." + fieldName);
                    continue;
                }
                JsonNode newDefault = MAPPER.valueToTree(value);
                changed |= !newDefault.equals(fieldDef.get(DEFAULT));
                fieldDef.set(DEFAULT, newDefault);
                fields++;
                scanValue(value, fieldDef, prefix + groupName + "." + fieldName);
            }
        }
        return changed;
    }

    @Nullable
    private static ArrayNode groupsOf(@Nullable JsonNode settingsNode) {
        return settingsNode instanceof ObjectNode node && node.get(GROUPS) instanceof ArrayNode array
                ? array : null;
    }

    /**
     * 声明分组的字段定义映射（{@code fields} 是按字段名键控的<b>映射</b>，非数组）。未声明 → {@code null}。
     */
    @Nullable
    private static ObjectNode fieldDefsOf(@Nullable ArrayNode groups, String groupName) {
        if (groups == null) {
            return null;
        }
        for (JsonNode group : groups) {
            if (group instanceof ObjectNode node && groupName.equals(node.path("name").asText())
                    && node.get(FIELDS) instanceof ObjectNode fieldDefs) {
                return fieldDefs;
            }
        }
        return null;
    }

    /**
     * 变体作用域（{@code surface:value}）在主题清单 {@code layoutVariants} 中声明的 {@code settings}
     * 节点。变体不存在或未声明 settings → {@code null}。
     */
    @Nullable
    private static JsonNode variantSettingsNode(ObjectNode manifest, String variantKey) {
        int colon = variantKey.indexOf(':');
        if (colon <= 0 || colon == variantKey.length() - 1) {
            return null;
        }
        String surface = variantKey.substring(0, colon);
        String value = variantKey.substring(colon + 1);
        if (!(manifest.get("layoutVariants") instanceof ObjectNode variants)
                || !(variants.get(surface) instanceof ArrayNode items)) {
            return null;
        }
        for (JsonNode item : items) {
            if (item instanceof ObjectNode node && value.equals(node.path("value").asText())) {
                return groupsOf(node.get(SETTINGS)) != null ? node.get(SETTINGS) : null;
            }
        }
        return null;
    }

    // ----- 可移植性提示（§7.4：原样快照 + warning，绝不改写值）-----

    /**
     * 按声明字段类型扫描写入值：媒体/链接/富文本指向本站存储、引用类携带站内ID → warning 提示。
     * 主题设置词汇即 Shape 字段词汇（§5.1 注册表已断言完备）；清单手写的未知类型按 NONE 宽容处理。
     */
    private void scanValue(@Nullable Object value, ObjectNode fieldDef, String path) {
        if (value == null) {
            return;
        }
        String type = fieldDef.path("type").asText("");
        switch (SeedExportFieldRules.shapeRuleLenient(type)) {
            case NONE -> {
                // 纯标量：无可移植性问题
            }
            case HTML -> {
                if (value instanceof String str
                        && (str.contains(uploadPrefix + "/") || str.contains(themePrefix + "/"))) {
                    addUrlIssue(path, path);
                }
            }
            case MEDIA -> scanUrlKeys(value, path, "url");
            case LINK -> scanUrlKeys(value, path, "url", "mobileUrl");
            case CHANNEL_REF, ARTICLE_REF, BLOCK_REF, VOTE_REF -> {
                // 站内数字ID才提示；迟绑形（channelRef/blockRef 的别名对象）本就可移植，不提示
                if (SeedExportValueRewriter.asRefId(value) != null) {
                    issues.add(new ExportPlan.RefIssue("settings", String.valueOf(value),
                            type + " 设置值按站内ID原样快照（同站可用；跨站安装为悬挂引用，"
                                    + "清单默认值无令牌展开机制）：" + path));
                }
            }
            case LIST -> {
                if (value instanceof List<?> list && fieldDef.get("of") instanceof ObjectNode of) {
                    for (int i = 0; i < list.size(); i++) {
                        scanValue(list.get(i), of, path + "[" + i + "]");
                    }
                }
            }
            case OBJECT -> {
                if (value instanceof Map<?, ?> map && fieldDef.get(FIELDS) instanceof ObjectNode sub) {
                    map.forEach((k, v) -> {
                        if (sub.get(String.valueOf(k)) instanceof ObjectNode subDef) {
                            scanValue(v, subDef, path + "." + k);
                        }
                    });
                }
            }
        }
    }

    private void scanUrlKeys(Object value, String path, String... urlKeys) {
        if (!(value instanceof Map<?, ?> map)) {
            return;
        }
        for (String urlKey : urlKeys) {
            if (map.get(urlKey) instanceof String url
                    && (url.startsWith(uploadPrefix + "/") || url.startsWith(themePrefix + "/"))) {
                addUrlIssue(url, path + "." + urlKey);
            }
        }
    }

    private void addUrlIssue(String ref, String path) {
        issues.add(new ExportPlan.RefIssue("settings", ref,
                "设置值指向本站存储资源，按原样快照入清单默认值（同站可用；跨站安装将指向源站或失效——"
                        + "清单默认值无令牌展开机制，不随包携带资源）：" + path));
    }

    private static String stripTrailingSlash(String prefix) {
        return prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }
}
