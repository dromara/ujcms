package com.ujcms.cms.core.composition.sources;

import com.ujcms.cms.core.composition.CtxParams;
import com.ujcms.cms.core.composition.ManifestService;
import com.ujcms.cms.core.composition.Shape;
import com.ujcms.cms.core.composition.ResolveContext;
import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Tag;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.BlockItemService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.TagService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.core.service.args.BlockItemArgs;
import com.ujcms.cms.core.service.args.ChannelArgs;
import com.ujcms.common.query.QueryParser;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查询引擎（§14.2 / component-settings §4.1）。从文章 / 栏目 / 区块项按 {@code where} 精确匹配 +
 * 单字段排序 + 限量取数，经 {@link SourceProjector} 投影为组件的 item 结构。{@code where} 值可引用
 * {@code $ctx.*} 渲染上下文参数（白名单，§14.2），使单个面板产出按频道而异的内容。
 *
 * <p>范围（§3.9 / §21 Q1）：精确匹配过滤 + 单字段排序，无 Lucene 全文检索。
 * DSL 详见 {@code docs/spec/slot-card-redesign/query-dsl.md}（作者侧，组件 {@code data} 模板）。
 *
 * <p>{@link #executeQuery} 由 {@link DataTemplateResolver} 驱动（$settings 替换后的查询规格 +
 * 置顶填充的去重排除）。
 *
 * @author PONY
 */
@Component
public class QuerySourceResolver {
    /**
     * 默认取数上限（组件 {@code data.limit} 未声明时）
     */
    private static final int DEFAULT_LIMIT = 20;

    /**
     * 缺失键策略：跳过该过滤（默认）
     */
    static final String MISSING_SKIP = "skip";
    /**
     * 缺失键策略：返回空结果
     */
    static final String MISSING_EMPTY = "empty";

    /**
     * 各来源实体允许的排序字段 → 查询令牌列（防注入 + 单字段约束，§3.9）。
     * 文章的浏览量统计列在 {@code ArticleExt} 上，令牌用查询管线的 {@code @articleExt-<col>}
     * 语法（与模板 {@code [@ArticleList orderBy='@articleExt-weekViews_desc']} 同机制，
     * component-settings §12.3 #1——「本周最热」等排序由此表达）。
     */
    private static final Map<String, Map<String, String>> ORDER_FIELDS = Map.of(
            SourceProjector.FROM_ARTICLE, Map.ofEntries(
                    Map.entry("publishdate", "publishDate"), Map.entry("publishedat", "publishDate"),
                    Map.entry("id", "id"), Map.entry("order", "order"), Map.entry("sticky", "sticky"),
                    Map.entry("views", "@articleExt-views"), Map.entry("dayviews", "@articleExt-dayViews"),
                    Map.entry("weekviews", "@articleExt-weekViews"),
                    Map.entry("monthviews", "@articleExt-monthViews"),
                    Map.entry("yearviews", "@articleExt-yearViews")),
            SourceProjector.FROM_CHANNEL, Map.of("order", "order", "id", "id", "name", "name"),
            SourceProjector.FROM_BLOCK_ITEM, Map.of("order", "order", "id", "id"));
    private static final Map<String, String> DEFAULT_ORDER = Map.of(
            SourceProjector.FROM_ARTICLE, "publishDate_desc",
            SourceProjector.FROM_CHANNEL, "order",
            SourceProjector.FROM_BLOCK_ITEM, "order");

    private final ArticleService articleService;
    private final ChannelService channelService;
    private final BlockItemService blockItemService;
    private final TagService tagService;
    private final ManifestService manifestService;

    public QuerySourceResolver(ArticleService articleService, ChannelService channelService,
                               BlockItemService blockItemService, TagService tagService,
                               ManifestService manifestService) {
        this.articleService = articleService;
        this.channelService = channelService;
        this.blockItemService = blockItemService;
        this.tagService = tagService;
        this.manifestService = manifestService;
    }

    /**
     * 执行查询并投影为条目（{@link DataTemplateResolver} 驱动）。
     *
     * @param siteId           站点ID
     * @param spec             查询配置（{@code from}/{@code where}/{@code orderBy}/{@code limit}/
     *                         {@code projection}/{@code missing}）
     * @param shapeName        目标组件名（投影所按的 item 字段结构）
     * @param ctx              解析上下文（{@code $ctx.*} 替换 + 主题命名空间）
     * @param limitOverride    取数上限覆盖（置顶填充时按剩余位计算；{@code null} 用 spec/默认）
     * @param excludeSourceIds 排除的来源实体ID（置顶去重，§5）
     * @return 投影后的条目（按查询顺序）
     */
    public List<ResolvedItem> executeQuery(Long siteId, Map<String, Object> spec, String shapeName,
                                           ResolveContext ctx,
                                           @Nullable Integer limitOverride, Set<Long> excludeSourceIds) {
        String from = strOr(spec.get("from"), SourceProjector.FROM_ARTICLE);
        Map<String, Object> where = substituteCtx(asMap(spec.get("where")), ctx, strOr(spec.get("missing"),
                MISSING_SKIP));
        if (where == null) {
            // missing 'empty' 策略命中：$ctx 键不在当前渲染上下文 → 返回空
            return List.of();
        }
        int limit = resolveLimit(spec, limitOverride);
        if (limit <= 0) {
            return List.of();
        }
        Map<String, Object> projection = asMap(spec.get("projection"));
        Map<String, FieldDef> fields = shapeFields(shapeName, ctx);
        String orderToken = orderToken(from, strOr(spec.get("orderBy"), ""));
        return switch (from) {
            case SourceProjector.FROM_CHANNEL ->
                    queryChannels(siteId, where, orderToken, limit, projection, fields, shapeName, excludeSourceIds);
            case SourceProjector.FROM_BLOCK_ITEM ->
                    queryBlockItems(siteId, where, orderToken, limit, projection, fields, shapeName, excludeSourceIds);
            default ->
                    queryArticles(siteId, where, orderToken, limit, projection, fields, shapeName, excludeSourceIds);
        };
    }

    private List<ResolvedItem> queryArticles(Long siteId, Map<String, Object> where, String orderToken, int limit,
                                     @Nullable Map<String, Object> projection, Map<String, FieldDef> fields,
                                     String shapeName, Set<Long> excludeSourceIds) {
        ArticleArgs args = ArticleArgs.of();
        args.siteId(siteId);
        boolean includeSub = boolOr(where.get("isIncludeSubChannel"), false);
        Long channelId = longVal(where.get("channelId"));
        if (channelId == null && where.get("channel") != null) {
            // channel 键：数字为栏目ID（channelRef 设置的类型化边界产出，component-settings §3），
            // 字符串为别名——与 tag 键的双形态同型
            channelId = longVal(where.get("channel"));
        }
        if (channelId == null && where.get("channel") != null) {
            String alias = strVal(where.get("channel"));
            if (StringUtils.isNotBlank(alias)) {
                Channel channel = channelService.findBySiteIdAndSlug(siteId, alias);
                if (channel == null) {
                    return List.of();
                }
                channelId = channel.getId();
            }
        }
        if (channelId != null) {
            if (includeSub) {
                args.channelAncestorId(channelId);
            } else {
                args.channelId(channelId);
            }
        }
        Long tagId = resolveTagId(siteId, where);
        if (tagId != null) {
            args.tagId(tagId);
        }
        if (!excludeSourceIds.isEmpty()) {
            args.excludeIds(excludeSourceIds);
        }
        args.status(List.of(Article.STATUS_PUBLISHED));
        args.getQueryMap().put(QueryParser.ORDER_BY, orderToken);
        return articleService.selectList(args, 0, limit).stream()
                .map(article -> SourceProjector.projectArticle(article, projection, fields, shapeName))
                .toList();
    }

    private List<ResolvedItem> queryChannels(Long siteId, Map<String, Object> where, String orderToken, int limit,
                                     @Nullable Map<String, Object> projection, Map<String, FieldDef> fields,
                                     String shapeName, Set<Long> excludeSourceIds) {
        ChannelArgs args = ChannelArgs.of();
        args.siteId(siteId);
        Long parentId = longVal(where.get("parentId"));
        if (parentId == null && where.get("parent") != null) {
            // parent 键与 channel 键同型：数字为栏目ID（channelRef 设置产出），字符串为别名
            parentId = longVal(where.get("parent"));
        }
        if (parentId == null && where.get("parent") != null) {
            String alias = strVal(where.get("parent"));
            if (StringUtils.isNotBlank(alias)) {
                Channel parent = channelService.findBySiteIdAndSlug(siteId, alias);
                if (parent == null) {
                    return List.of();
                }
                parentId = parent.getId();
            }
        }
        if (parentId != null) {
            args.parentId(parentId);
        } else if (boolOr(where.get("rootOnly"), false)) {
            args.parentIdIsNull();
        }
        if (where.get("isNav") != null) {
            args.isNav(boolOr(where.get("isNav"), false));
        }
        if (where.get("isReal") != null) {
            args.isReal(boolOr(where.get("isReal"), false));
        }
        args.getQueryMap().put(QueryParser.ORDER_BY, orderToken);
        return channelService.selectList(args, 0, limit + excludeSourceIds.size()).stream()
                .filter(channel -> !excludeSourceIds.contains(channel.getId()))
                .limit(limit)
                .map(channel -> SourceProjector.projectChannel(channel, projection, fields, shapeName))
                .toList();
    }

    private List<ResolvedItem> queryBlockItems(Long siteId, Map<String, Object> where, String orderToken, int limit,
                                       @Nullable Map<String, Object> projection, Map<String, FieldDef> fields,
                                       String shapeName, Set<Long> excludeSourceIds) {
        BlockItemArgs args = BlockItemArgs.of();
        args.siteId(siteId);
        Long blockId = longVal(where.get("blockId"));
        if (blockId != null) {
            args.blockId(blockId);
        } else {
            String alias = strVal(where.get("block"));
            if (StringUtils.isNotBlank(alias)) {
                args.blockAlias(alias);
            }
        }
        // 区块项遵循其自身的发布窗口（V11.1 isActive 语义，§14.4）——这是区块的门槛，而非卡片生命周期
        args.enabled(true).activeAt(OffsetDateTime.now());
        args.getQueryMap().put(QueryParser.ORDER_BY, orderToken);
        return blockItemService.selectList(args, 0, limit + excludeSourceIds.size()).stream()
                .filter(item -> !excludeSourceIds.contains(item.getId()))
                .limit(limit)
                .map(item -> SourceProjector.projectBlockItem(item, projection, fields, shapeName))
                .toList();
    }

    /**
     * 把 {@code where} 中的 {@code $ctx.*} 引用替换为当前渲染上下文的值（白名单内）。
     * 上下文缺失该键时按 {@code missing} 策略：{@code skip} 删除该过滤；{@code empty} 短路返回（{@code null}）。
     *
     * @return 替换后的 {@code where}；缺失键命中 {@code empty} 策略时返回 {@code null}
     */
    @Nullable
    private static Map<String, Object> substituteCtx(Map<String, Object> where, ResolveContext ctx,
                                                     String missing) {
        Map<String, Object> result = new LinkedHashMap<>(where.size());
        for (Map.Entry<String, Object> entry : where.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String str && str.startsWith(CtxParams.PREFIX)) {
                String key = str.substring(CtxParams.PREFIX.length());
                Object ctxValue = CtxParams.isAllowed(key) ? ctx.ctx(key) : null;
                if (ctxValue != null) {
                    result.put(entry.getKey(), ctxValue);
                } else if (MISSING_EMPTY.equals(missing)) {
                    return null;
                }
                // skip 策略：丢弃该过滤（不放入 result）
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    @Nullable
    private Long resolveTagId(Long siteId, Map<String, Object> where) {
        Long tagId = longVal(where.get("tagId"));
        if (tagId != null) {
            return tagId;
        }
        Object tag = where.get("tag");
        if (tag == null) {
            return null;
        }
        Long numeric = longVal(tag);
        if (numeric != null) {
            return numeric;
        }
        String name = strVal(tag);
        if (StringUtils.isBlank(name)) {
            return null;
        }
        Tag found = tagService.selectByName(siteId, name);
        return found != null ? found.getId() : -1L; // 标签名不存在 → 用不可能的ID，确保返回空
    }

    /**
     * 解析组件的 item 字段定义（投影按类型包装），按命名空间链。
     */
    public Map<String, FieldDef> shapeFields(String shapeName, ResolveContext ctx) {
        Shape shape = manifestService.getSnapshot().resolveShape(ctx.themeKey(), shapeName);
        return shape != null ? shape.getFields() : Map.of();
    }

    private static int resolveLimit(Map<String, Object> spec, @Nullable Integer limitOverride) {
        if (limitOverride != null) {
            return limitOverride;
        }
        Long specLimit = longVal(spec.get("limit"));
        if (specLimit != null) {
            return specLimit.intValue();
        }
        return DEFAULT_LIMIT;
    }

    /**
     * 把 DSL 排序（{@code "publishedAt desc"}）翻译为查询令牌（{@code "publishDate_desc"}）。
     * 字段经白名单校验（防注入）；非法/空字段回退到该来源的默认排序。
     */
    static String orderToken(String from, String orderByDsl) {
        Map<String, String> allowed = ORDER_FIELDS.getOrDefault(from, Map.of());
        String fallback = DEFAULT_ORDER.getOrDefault(from, "id");
        if (StringUtils.isBlank(orderByDsl)) {
            return fallback;
        }
        String[] parts = orderByDsl.trim().split("\\s+");
        String column = allowed.get(parts[0].toLowerCase());
        if (column == null) {
            return fallback;
        }
        boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1]);
        return desc ? column + "_desc" : column;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(@Nullable Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }

    private static String strOr(@Nullable Object value, String defaultValue) {
        String str = strVal(value);
        return StringUtils.isNotBlank(str) ? str : defaultValue;
    }

    @Nullable
    private static String strVal(@Nullable Object value) {
        return value != null ? value.toString() : null;
    }

    @Nullable
    private static Long longVal(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str && StringUtils.isNotBlank(str)) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean boolOr(@Nullable Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str) {
            return Boolean.parseBoolean(str.trim());
        }
        return defaultValue;
    }
}
