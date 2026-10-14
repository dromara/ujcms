package com.ujcms.cms.core.composition.sources;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.BlockItem;
import com.ujcms.cms.core.domain.Channel;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 来源投影引擎（§14.2 / §14.4）。把来源实体（文章 / 栏目 / 区块项）的字段投影为某一 Shape 的字段，
 * 产出一条 {@link ResolvedItem}（{@code id} = 来源实体ID）。
 *
 * <p><b>投影值语法</b>：投影/约定的每个值是一个字符串表达式：
 * <ul>
 *   <li>{@code $.title} —— 整串即一个路径，解析为实体字段值（可为非字符串，如 media 对象）；</li>
 *   <li>{@code $.customs.titleColor} —— 嵌套路径，解析区块项 Model 自定义字段（§14.4）；</li>
 *   <li>{@code /article/$.id} —— 含字面量的模板，对其中每个 {@code $.路径} 做字符串插值，产出字符串。</li>
 * </ul>
 *
 * <p><b>按字段类型包装</b>：解析所得原始值按目标 Shape 字段类型包装，使查询/区块投影出来的条目与
 * 手工条目走同一套模板代码——例如 {@code link}/{@code media} 字段把字符串 URL 包成
 * {@code {"url": ...}}，{@code themeColor} 包成 {@code {"token": ...}}。
 *
 * <p><b>省略投影时的约定</b>：未声明 {@code projection} 时，按 Shape 字段名套用每实体的默认约定映射
 * （如文章的 {@code headline}/{@code title} ← 标题、{@code image} ← 标题图、{@code link} ← 文章URL）。
 *
 * @author PONY
 */
public final class SourceProjector {
    /**
     * 来源实体：文章
     */
    public static final String FROM_ARTICLE = "article";
    /**
     * 来源实体：栏目
     */
    public static final String FROM_CHANNEL = "channel";
    /**
     * 来源实体：区块项
     */
    public static final String FROM_BLOCK_ITEM = "blockItem";

    /**
     * 区块项自定义字段视图键（{@code $.customs.<code>}）
     */
    public static final String CUSTOMS = "customs";

    /**
     * 整串路径：{@code $.headline}、{@code $.customs.titleColor}
     */
    private static final Pattern WHOLE_PATH = Pattern.compile("^\\$\\.([A-Za-z0-9_.]+)$");
    /**
     * 模板内嵌路径：{@code /article/$.id}
     */
    private static final Pattern EMBEDDED_PATH = Pattern.compile("\\$\\.([A-Za-z0-9_.]+)");

    /**
     * 按目标 Shape 字段名（小写）套用的默认约定映射，按来源实体区分
     */
    private static final Map<String, String> ARTICLE_CONVENTION = Map.ofEntries(
            Map.entry("title", "$.title"), Map.entry("headline", "$.title"), Map.entry("label", "$.title"),
            Map.entry("name", "$.title"), Map.entry("subhead", "$.description"), Map.entry("subtitle", "$.description"),
            Map.entry("description", "$.description"), Map.entry("summary", "$.description"),
            Map.entry("body", "$.description"), Map.entry("image", "$.image"), Map.entry("icon", "$.image"),
            Map.entry("cover", "$.image"), Map.entry("photo", "$.image"), Map.entry("picture", "$.image"),
            Map.entry("link", "$.url"), Map.entry("url", "$.url"), Map.entry("date", "$.publishDate"),
            Map.entry("publishdate", "$.publishDate"), Map.entry("publishedat", "$.publishDate"),
            Map.entry("duration", "$.videoTime"), Map.entry("videotime", "$.videoTime"));
    private static final Map<String, String> CHANNEL_CONVENTION = Map.ofEntries(
            Map.entry("title", "$.name"), Map.entry("headline", "$.name"), Map.entry("label", "$.name"),
            Map.entry("name", "$.name"), Map.entry("subhead", "$.description"), Map.entry("subtitle", "$.description"),
            Map.entry("description", "$.description"), Map.entry("image", "$.image"), Map.entry("icon", "$.image"),
            Map.entry("link", "$.url"), Map.entry("url", "$.url"));
    private static final Map<String, String> BLOCK_ITEM_CONVENTION = Map.ofEntries(
            Map.entry("title", "$.title"), Map.entry("headline", "$.title"), Map.entry("label", "$.title"),
            Map.entry("name", "$.title"), Map.entry("subhead", "$.subtitle"), Map.entry("subtitle", "$.subtitle"),
            Map.entry("description", "$.description"), Map.entry("summary", "$.description"),
            Map.entry("image", "$.image"), Map.entry("icon", "$.image"), Map.entry("cover", "$.image"),
            Map.entry("mobileimage", "$.mobileImage"), Map.entry("video", "$.video"),
            Map.entry("link", "$.url"), Map.entry("url", "$.url"));

    private SourceProjector() {
    }

    /**
     * 文章 → 条目
     */
    public static ResolvedItem projectArticle(Article article, @Nullable Map<String, Object> projection,
                                      Map<String, FieldDef> shapeFields, String shapeName) {
        return project(article.getId(), FROM_ARTICLE, articleView(article), projection, shapeFields, shapeName);
    }

    /**
     * 栏目 → 条目
     */
    public static ResolvedItem projectChannel(Channel channel, @Nullable Map<String, Object> projection,
                                      Map<String, FieldDef> shapeFields, String shapeName) {
        return project(channel.getId(), FROM_CHANNEL, channelView(channel), projection, shapeFields, shapeName);
    }

    /**
     * 区块项 → 条目（含 {@code $.customs.<code>} 投影，§14.4）
     */
    public static ResolvedItem projectBlockItem(BlockItem item, @Nullable Map<String, Object> projection,
                                        Map<String, FieldDef> shapeFields, String shapeName) {
        return project(item.getId(), FROM_BLOCK_ITEM, blockItemView(item), projection, shapeFields, shapeName);
    }

    /**
     * 手工条目（component-settings §5 页面本地编排）。条目来自面板设置的 list-of-object 值：
     * 仅保留 item 声明的字段并按类型包装（与查询投影一致的访问形态）；{@code id} 为条目在列表中的
     * 序号（非实体ID，契约条目不输出）。
     */
    public static ResolvedItem projectInlineItem(int index, Map<String, Object> item, Map<String, FieldDef> shapeFields,
                                         String shapeName) {
        Map<String, Object> data = new LinkedHashMap<>(shapeFields.size());
        shapeFields.forEach((fieldName, def) -> {
            Object value = item.get(fieldName);
            if (value == null) {
                return;
            }
            data.put(fieldName, wrap(def, value));
        });
        return new ResolvedItem((long) index, shapeName, data);
    }

    /**
     * 把来源实体的字段视图投影为条目（{@code projectArticle/Channel/BlockItem} 的底层方法；
     * 视图为 {@code 字段名 → 值} 的 Map，{@code customs} 键下为区块项自定义字段）。
     *
     * @param id          来源实体ID
     * @param fromKind    来源实体类型（{@link #FROM_ARTICLE} 等，省略投影时选用约定映射）
     * @param view        实体字段视图
     * @param projection  投影配置（{@code null}/空 → 套用约定）
     * @param shapeFields 目标 Shape 字段定义（按类型包装）
     * @param shapeName   目标 Shape 名
     * @return 投影条目
     */
    public static ResolvedItem project(Long id, String fromKind, Map<String, Object> view,
                               @Nullable Map<String, Object> projection, Map<String, FieldDef> shapeFields,
                               String shapeName) {
        Map<String, String> spec = projection != null && !projection.isEmpty()
                ? toExprMap(projection)
                : convention(fromKind, shapeFields);
        Map<String, Object> data = new LinkedHashMap<>(spec.size());
        spec.forEach((targetField, expr) -> {
            Object resolved = resolveExpr(expr, view);
            if (resolved == null) {
                return;
            }
            data.put(targetField, wrap(shapeFields.get(targetField), resolved));
        });
        return new ResolvedItem(id, shapeName, data);
    }

    public static Map<String, Object> articleView(Article article) {
        Map<String, Object> view = new LinkedHashMap<>(9);
        view.put("id", article.getId());
        view.put("title", article.getTitle());
        view.put("description", article.getDescription());
        view.put("image", article.getImage());
        view.put("url", article.getUrl());
        view.put("publishDate", article.getPublishDate());
        view.put("channelId", article.getChannelId());
        // 视频时长（component-settings §12.3 #2）：精彩视频类组件的时长角标
        view.put("videoTime", article.getVideoTime());
        return view;
    }

    static Map<String, Object> channelView(Channel channel) {
        Map<String, Object> view = new LinkedHashMap<>(8);
        view.put("id", channel.getId());
        view.put("name", channel.getName());
        view.put("title", channel.getTitle());
        view.put("alias", channel.getSlug());
        view.put("description", channel.getDescription());
        view.put("image", channel.getImage());
        view.put("url", channel.getUrl());
        return view;
    }

    static Map<String, Object> blockItemView(BlockItem item) {
        Map<String, Object> view = new LinkedHashMap<>(12);
        view.put("id", item.getId());
        view.put("title", item.getTitle());
        view.put("subtitle", item.getSubtitle());
        view.put("description", item.getDescription());
        view.put("image", item.getImage());
        view.put("mobileImage", item.getMobileImage());
        view.put("video", item.getVideo());
        view.put("linkUrl", item.getLinkUrl());
        view.put("url", item.getUrl());
        view.put("targetBlank", item.getTargetBlank());
        view.put("articleId", item.getArticleId());
        view.put(CUSTOMS, item.getCustoms());
        return view;
    }

    private static Map<String, String> convention(String fromKind, Map<String, FieldDef> shapeFields) {
        Map<String, String> table = switch (fromKind) {
            case FROM_CHANNEL -> CHANNEL_CONVENTION;
            case FROM_BLOCK_ITEM -> BLOCK_ITEM_CONVENTION;
            default -> ARTICLE_CONVENTION;
        };
        Map<String, String> spec = new LinkedHashMap<>(shapeFields.size());
        shapeFields.forEach((fieldName, def) -> {
            String expr = table.get(fieldName.toLowerCase());
            if (expr != null) {
                spec.put(fieldName, expr);
            }
        });
        return spec;
    }

    private static Map<String, String> toExprMap(Map<String, Object> projection) {
        Map<String, String> spec = new LinkedHashMap<>(projection.size());
        projection.forEach((field, value) -> {
            if (value instanceof String expr) {
                spec.put(field, expr);
            }
        });
        return spec;
    }

    /**
     * 解析投影表达式：整串路径返回实体字段原值；含字面量的模板做字符串插值。
     */
    @Nullable
    private static Object resolveExpr(String expr, Map<String, Object> view) {
        Matcher whole = WHOLE_PATH.matcher(expr);
        if (whole.matches()) {
            return resolvePath(view, whole.group(1));
        }
        Matcher embedded = EMBEDDED_PATH.matcher(expr);
        StringBuilder buff = new StringBuilder();
        while (embedded.find()) {
            Object value = resolvePath(view, embedded.group(1));
            embedded.appendReplacement(buff, Matcher.quoteReplacement(value != null ? value.toString() : ""));
        }
        embedded.appendTail(buff);
        return buff.toString();
    }

    @Nullable
    private static Object resolvePath(Map<String, Object> view, String path) {
        String[] segments = path.split("\\.");
        Object current = view;
        for (String segment : segments) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * 按目标字段类型包装原始值，使投影条目与手工条目在模板里访问形态一致。
     */
    private static Object wrap(@Nullable FieldDef def, Object raw) {
        if (def == null) {
            return raw;
        }
        return switch (def.getType()) {
            case "link", "media" -> raw instanceof Map ? raw : mapOf("url", String.valueOf(raw));
            case "themeColor" -> raw instanceof Map ? raw : mapOf("token", String.valueOf(raw));
            case "badge" -> raw instanceof Map ? raw : mapOf("text", String.valueOf(raw));
            case "text", "longText", "html", "enum", "date", "datetime" ->
                    raw instanceof String ? raw : String.valueOf(raw);
            case "boolean" -> raw instanceof Boolean ? raw : Boolean.parseBoolean(String.valueOf(raw));
            default -> raw;
        };
    }

    private static Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>(2);
        map.put(key, value);
        return map;
    }

}
