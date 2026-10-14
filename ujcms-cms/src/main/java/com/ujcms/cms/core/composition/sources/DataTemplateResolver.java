package com.ujcms.cms.core.composition.sources;

import com.ujcms.cms.core.composition.Shape;
import com.ujcms.cms.core.composition.ShapeLoader;
import com.ujcms.cms.core.composition.ResolveContext;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组件数据模板求值（component-settings §4.1 / §5）。对 Shape v2 的 {@code data} 块做
 * {@code $settings.*} 替换后驱动既有查询/投影引擎，产出面板条目
 * （{@link ResolvedItem}，由 {@code PanelResolver} 转为契约条目）：
 *
 * <ul>
 *   <li><b>查询</b>（{@code from/where/orderBy/limit/missing/projection}）——替换后交
 *       {@link QuerySourceResolver#executeQuery}；{@code $ctx.*} 引用原样透传（由查询侧按同一
 *       {@code missing} 策略替换）。设置值已在 {@code SettingsResolver} 过类型化边界。</li>
 *   <li><b>手工条目</b>（{@code items} 引用 list-of-object 设置）——页面本地编排内容（§5），
 *       经 {@link SourceProjector#projectInlineItem} 按 item 结构包装。</li>
 *   <li><b>模式切换</b>（Q3）：{@code mode} 引用的 enum 设置解析值为 {@code manual} 时跳过查询、
 *       走手工条目；未声明 {@code from} 的组件恒走手工条目。</li>
 *   <li><b>编辑置顶</b>（{@code pinned} 引用 list-of-引用 设置）——置顶实体先渲染，查询填充到
 *       {@code limit} 并按来源实体ID去重（原 hybrid 来源的置顶+填充语义，改由设置驱动）。</li>
 * </ul>
 *
 * @author PONY
 */
@Component
public class DataTemplateResolver {
    /**
     * {@code mode} 设置的手工值（Q3：解析值为 manual 时跳过查询）
     */
    public static final String MODE_MANUAL = "manual";
    /**
     * 无 {@code limit} 声明时的取数上限（与查询来源默认一致）
     */
    private static final int DEFAULT_LIMIT = 20;

    private final QuerySourceResolver querySourceResolver;
    private final ArticleService articleService;
    private final ChannelService channelService;

    public DataTemplateResolver(QuerySourceResolver querySourceResolver, ArticleService articleService,
                                ChannelService channelService) {
        this.querySourceResolver = querySourceResolver;
        this.articleService = articleService;
        this.channelService = channelService;
    }

    /**
     * 求值组件数据模板。
     *
     * @param shape    组件（携带 {@code data} 模板与 item 字段结构）
     * @param settings 解析后的设置引擎值（{@code SettingsResolver.Resolved#values()}）
     * @param siteId   站点ID
     * @param ctx      解析上下文（轨道、{@code $ctx.*}、预览标记）
     * @return 条目（按序）；无 {@code data} 模板（纯设置组件）为空列表
     */
    public List<ResolvedItem> resolve(Shape shape, Map<String, Object> settings, Long siteId,
                                      ResolveContext ctx) {
        Map<String, Object> data = shape.getData();
        if (data == null) {
            return List.of();
        }
        boolean hasQuery = data.get("from") instanceof String;
        if (isManual(data, settings) || !hasQuery) {
            return inlineItems(data, settings, shape);
        }
        return query(data, settings, siteId, shape, ctx);
    }

    /**
     * 模式判定（Q3）：{@code data.mode} 引用的设置解析值为 {@code manual} → 手工条目。
     */
    private static boolean isManual(Map<String, Object> data, Map<String, Object> settings) {
        Object ref = data.get("mode");
        return ref instanceof String str && MODE_MANUAL.equals(settingValue(str, settings));
    }

    /**
     * 手工条目（§5 页面本地编排）：{@code data.items} 引用的 list-of-object 设置值 → 按 item 结构
     * 包装的条目。引用缺失或未设值 → 空。
     */
    private static List<ResolvedItem> inlineItems(Map<String, Object> data, Map<String, Object> settings, Shape shape) {
        Object ref = data.get("items");
        if (!(ref instanceof String str)) {
            return List.of();
        }
        Object value = settingValue(str, settings);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<ResolvedItem> result = new ArrayList<>(list.size());
        int index = 0;
        for (Object element : list) {
            if (element instanceof Map<?, ?> map) {
                Map<String, Object> item = new LinkedHashMap<>(map.size());
                map.forEach((k, v) -> {
                    if (k instanceof String key) {
                        item.put(key, v);
                    }
                });
                result.add(SourceProjector.projectInlineItem(index, item, shape.getFields(), shape.getName()));
            }
            index++;
        }
        return result;
    }

    /**
     * 查询路径：{@code $settings.*} 替换 → （可选）置顶装载 → 查询填充（去重、补到 limit）。
     */
    private List<ResolvedItem> query(Map<String, Object> data, Map<String, Object> settings, Long siteId,
                                     Shape shape, ResolveContext ctx) {
        String missing = strOr(data.get("missing"), QuerySourceResolver.MISSING_SKIP);
        Map<String, Object> where = substituteSettings(asMap(data.get("where")), settings, missing);
        if (where == null) {
            // missing 'empty' 策略命中：被引设置未设值 → 空结果
            return List.of();
        }
        int limit = resolveLimit(data.get("limit"), settings);
        if (limit <= 0) {
            return List.of();
        }
        Map<String, Object> spec = new LinkedHashMap<>(8);
        spec.put("from", data.get("from"));
        spec.put("where", where);
        spec.put("missing", missing);
        spec.put("limit", limit);
        Object orderBy = data.get("orderBy") instanceof String str ? scalar(str, settings) : null;
        if (orderBy != null) {
            spec.put("orderBy", orderBy);
        }
        if (data.get("projection") instanceof Map<?, ?> projection) {
            spec.put("projection", projection);
        }
        List<Long> pinnedIds = pinnedIds(data, settings);
        if (pinnedIds.isEmpty()) {
            return querySourceResolver.executeQuery(siteId, spec, shape.getName(), ctx, null, Set.of());
        }
        List<ResolvedItem> pinned = loadPinned(pinnedIds, strOr(data.get("from"), SourceProjector.FROM_ARTICLE),
                asMap(data.get("projection")), shape, ctx);
        if (pinned.size() >= limit) {
            return List.copyOf(pinned.subList(0, limit));
        }
        // 填充按置顶的来源实体ID去重（§5：置顶引用即实体ID，无需扫描引用字段）
        List<ResolvedItem> result = new ArrayList<>(pinned);
        result.addAll(querySourceResolver.executeQuery(siteId, spec, shape.getName(), ctx,
                limit - pinned.size(), new HashSet<>(pinnedIds)));
        return result;
    }

    /**
     * 置顶实体装载（§5）：按设置引用的实体ID逐个取实体并投影（与查询同一投影配置）。
     * 文章在线上路径过生命周期门槛（未发布不渲染也不占名额——其ID仍参与填充去重，
     * 发布态查询本就不会返回它们）；预览全量呈现。
     */
    private List<ResolvedItem> loadPinned(List<Long> ids, String from, Map<String, Object> projection, Shape shape,
                                          ResolveContext ctx) {
        Map<String, Object> effectiveProjection = projection.isEmpty() ? null : projection;
        List<ResolvedItem> result = new ArrayList<>(ids.size());
        for (Long id : ids) {
            if (SourceProjector.FROM_CHANNEL.equals(from)) {
                Channel channel = channelService.select(id);
                if (channel != null) {
                    result.add(SourceProjector.projectChannel(channel, effectiveProjection, shape.getFields(),
                            shape.getName()));
                }
            } else if (SourceProjector.FROM_ARTICLE.equals(from)) {
                Article article = articleService.select(id);
                if (article != null && (ctx.preview() || article.isNormal())) {
                    result.add(SourceProjector.projectArticle(article, effectiveProjection, shape.getFields(),
                            shape.getName()));
                }
            }
            // blockItem 无引用设置类型，pinned 不适用（Schema/加载期已拒）
        }
        return result;
    }

    /**
     * 把 {@code where} 中的 {@code $settings.*} 引用替换为设置引擎值；{@code $ctx.*} 引用原样保留
     * （查询侧按同一 {@code missing} 策略替换）。被引设置未设值时按 {@code missing} 策略：
     * {@code skip} 删除该过滤；{@code empty} 短路（返回 {@code null}）。
     */
    @Nullable
    private static Map<String, Object> substituteSettings(Map<String, Object> where, Map<String, Object> settings,
                                                          String missing) {
        Map<String, Object> result = new LinkedHashMap<>(where.size());
        for (Map.Entry<String, Object> entry : where.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String str && str.startsWith(ShapeLoader.SETTINGS_PREFIX)) {
                Object resolved = settingValue(str, settings);
                if (resolved != null) {
                    result.put(entry.getKey(), resolved);
                } else if (QuerySourceResolver.MISSING_EMPTY.equals(missing)) {
                    return null;
                }
                // skip 策略：丢弃该过滤（不放入 result）
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    /**
     * 标量替换（{@code orderBy}）：{@code $settings.*} → 引擎值（未设值 → {@code null}，
     * 调用方省略该键回退默认）；字面量原样。
     */
    @Nullable
    private static Object scalar(String value, Map<String, Object> settings) {
        if (value.startsWith(ShapeLoader.SETTINGS_PREFIX)) {
            return settingValue(value, settings);
        }
        return value;
    }

    /**
     * {@code limit}：字面量数字或 {@code $settings.*}（number 设置，已钳制）；不可解析回退默认。
     */
    private static int resolveLimit(@Nullable Object value, Map<String, Object> settings) {
        Object resolved = value instanceof String str ? scalar(str, settings) : value;
        if (resolved instanceof Number number) {
            return number.intValue();
        }
        return DEFAULT_LIMIT;
    }

    /**
     * 置顶实体ID（{@code data.pinned} 引用的 list 设置引擎值——{@code SettingsResolver} 已滤为有效ID）。
     */
    private static List<Long> pinnedIds(Map<String, Object> data, Map<String, Object> settings) {
        Object ref = data.get("pinned");
        if (!(ref instanceof String str)) {
            return List.of();
        }
        Object value = settingValue(str, settings);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>(list.size());
        for (Object element : list) {
            if (element instanceof Number number) {
                ids.add(number.longValue());
            }
        }
        return ids;
    }

    /**
     * 读取 {@code $settings.<name>} 引用的引擎值（{@code null} = 未设值/无该设置）。
     */
    @Nullable
    private static Object settingValue(String ref, Map<String, Object> settings) {
        return settings.get(ref.substring(ShapeLoader.SETTINGS_PREFIX.length()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(@Nullable Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }

    private static String strOr(@Nullable Object value, String defaultValue) {
        return value instanceof String str && StringUtils.isNotBlank(str) ? str : defaultValue;
    }
}
