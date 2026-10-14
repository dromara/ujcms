package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.BlockService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.ext.domain.Vote;
import com.ujcms.cms.ext.service.VoteService;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面板设置解析（component-settings §3 / §4.2）。按组件（Shape v2）声明的设置结构，把面板存储的
 * 原始设置值解析为两种形态：
 *
 * <ul>
 *   <li><b>引擎值</b>（{@link Resolved#values()}）——供 {@code $settings.*} 替换进入查询引擎的
 *       <b>类型化边界</b>：缺省值补齐；{@code channelRef}/{@code articleRef} 规范化为经存在性与
 *       站点归属校验的实体ID（别名形态按站点解析）；{@code number} 按 min/max 钳制；{@code enum}
 *       做成员校验；无效值归 {@code null}（缺失语义）。比自由 where 行更严——这是操作员输入进入
 *       查询引擎的唯一通道，DSL 白名单是第二道防线。</li>
 *   <li><b>契约值</b>（{@link Resolved#contract()}）——渲染契约的 {@code settings} 块：引用展开为
 *       摘要对象（文章 {@code {id,title,url,image}}、栏目 {@code {id,alias,name,url}}；目标已删
 *       返回 {@code {id,deleted:true}} 占位），未设值以 {@code null} 显式呈现。</li>
 * </ul>
 *
 * <p>{@link #staleRefs} 是<b>失效引用的唯一判定口径</b>：健康报告（构成健康页）与种子导入
 * （theme-marketplace D24：只填空策略下修复未改动本源面板的死引用）共用，两处绝不各自演化。
 *
 * @author PONY
 */
@Component
public class SettingsResolver {
    /**
     * 设置字段类型：栏目引用
     */
    private static final String TYPE_CHANNEL_REF = RefResolver.TYPE_CHANNEL_REF;
    /**
     * 设置字段类型：文章引用
     */
    private static final String TYPE_ARTICLE_REF = RefResolver.TYPE_ARTICLE_REF;
    /**
     * 设置字段类型：区块引用（component-settings §5——共享/排期编排内容住区块；组件以
     * {@code blockRef} 设置 + {@code where: {"blockId": "$settings.…"}} 数据模板取区块项）。
     * 仅设置类型，不是条目字段类型（{@link RefResolver} 不解析它）。
     */
    public static final String TYPE_BLOCK_REF = "blockRef";
    /**
     * 设置字段类型：投票引用（vote-micro-region §2——微区域面板以 {@code voteRef} 设置指定投票，
     * 模板片段经 {@code [@Vote id=…]} 自取实体）。仅设置类型，不是条目字段类型
     * （{@link RefResolver} 不解析它，引擎值为经校验的裸ID透传）。
     */
    public static final String TYPE_VOTE_REF = "voteRef";
    /**
     * 失效引用的目标种类（{@link #staleRefs}）：栏目 / 区块 / 投票 / 文章——健康报告与控制台按此分类展示
     */
    public static final String KIND_CHANNEL = "channel";
    public static final String KIND_BLOCK = "block";
    public static final String KIND_VOTE = "vote";
    public static final String KIND_ARTICLE = "article";
    private static final String TYPE_NUMBER = "number";
    private static final String TYPE_ENUM = "enum";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_LIST = "list";
    private static final String ID = "id";
    private static final String ALIAS = "alias";
    private static final String DELETED = "deleted";

    private final ArticleService articleService;
    private final ChannelService channelService;
    private final BlockService blockService;
    private final VoteService voteService;

    public SettingsResolver(ArticleService articleService, ChannelService channelService,
                            BlockService blockService, VoteService voteService) {
        this.articleService = articleService;
        this.channelService = channelService;
        this.blockService = blockService;
        this.voteService = voteService;
    }

    /**
     * 解析一个面板的设置值。
     *
     * @param shape  组件（声明设置结构）
     * @param raw    面板存储的原始设置值（{@code settings_json_}）
     * @param siteId 站点ID（引用按站点校验归属；别名形态按站点解析）
     * @return 引擎值 + 契约值
     */
    public Resolved resolve(Shape shape, Map<String, Object> raw, Long siteId) {
        Map<String, Object> values = new LinkedHashMap<>(shape.getSettings().size());
        Map<String, Object> contract = new LinkedHashMap<>(shape.getSettings().size());
        shape.getSettings().forEach((name, def) -> {
            Object value = raw.get(name);
            if (isEmpty(value)) {
                value = def.getDefaultValue();
            }
            Entry entry = normalize(value, def, siteId);
            values.put(name, entry.engine());
            contract.put(name, entry.contract());
        });
        return new Resolved(Collections.unmodifiableMap(values), Collections.unmodifiableMap(contract));
    }

    /**
     * 失效引用清点（component-settings Phase D 健康检测；theme-marketplace D24 起与种子导入共用同一口径）：
     * 组件声明的引用型设置——{@code channelRef} / {@code blockRef} / {@code voteRef} / {@code articleRef}，
     * 以及元素为 channelRef/articleRef 的 {@code list}——有效值（未设值回退缺省值）非空、但经
     * {@link #resolve} 同一类型化边界解析后引擎值为空，即目标已删除或越站（渲染时按未设值处理）。
     * 列表设置按元素清点：任一元素失联即计入一条（引擎值静默丢弃该元素，操作员须知）。
     *
     * @param shape  组件（声明设置结构）
     * @param raw    面板存储的原始设置值
     * @param siteId 站点ID
     * @return 逐设置的失效引用（无失效为空列表；组件无引用型设置恒为空）
     */
    public List<StaleRef> staleRefs(Shape shape, Map<String, Object> raw, Long siteId) {
        List<StaleRef> result = new ArrayList<>();
        shape.getSettings().forEach((name, def) -> {
            String kind = refKind(def);
            if (kind == null) {
                return;
            }
            Object value = raw.get(name);
            if (isEmpty(value)) {
                value = def.getDefaultValue();
            }
            if (value == null) {
                return;
            }
            if (TYPE_LIST.equals(def.getType())) {
                FieldDef of = def.getFieldDef("of");
                if (of == null || !(value instanceof List<?> list)) {
                    return;
                }
                List<String> dead = new ArrayList<>();
                for (Object element : list) {
                    if (!isEmpty(element) && normalize(element, of, siteId).engine() == null) {
                        dead.add(refString(element));
                    }
                }
                if (!dead.isEmpty()) {
                    result.add(new StaleRef(name, kind, String.join(", ", dead)));
                }
                return;
            }
            if (normalize(value, def, siteId).engine() == null) {
                result.add(new StaleRef(name, kind, refString(value)));
            }
        });
        return result;
    }

    /**
     * 引用型设置的目标种类（{@link #KIND_CHANNEL} 等）；非引用型为 {@code null}。列表按元素类型判定
     * （与 {@link #normalizeList} 同口径：仅 channelRef/articleRef 元素参与规范化）。
     */
    @Nullable
    private static String refKind(FieldDef def) {
        String type = def.getType();
        if (TYPE_LIST.equals(type)) {
            FieldDef of = def.getFieldDef("of");
            if (of == null) {
                return null;
            }
            if (TYPE_CHANNEL_REF.equals(of.getType())) {
                return KIND_CHANNEL;
            }
            return TYPE_ARTICLE_REF.equals(of.getType()) ? KIND_ARTICLE : null;
        }
        return switch (type) {
            case TYPE_CHANNEL_REF -> KIND_CHANNEL;
            case TYPE_BLOCK_REF -> KIND_BLOCK;
            case TYPE_VOTE_REF -> KIND_VOTE;
            case TYPE_ARTICLE_REF -> KIND_ARTICLE;
            default -> null;
        };
    }

    /**
     * 引用值的展示串（ID / 别名），供失效引用列明。
     */
    private static String refString(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object id = map.get(ID);
            if (id != null) {
                return String.valueOf(id);
            }
            Object alias = map.get(ALIAS);
            if (alias != null) {
                return String.valueOf(alias);
            }
        }
        return String.valueOf(value);
    }

    /**
     * 按类型规范化单个设置值（引擎值 + 契约值）。
     */
    private Entry normalize(@Nullable Object value, FieldDef def, Long siteId) {
        if (value == null) {
            return Entry.empty();
        }
        return switch (def.getType()) {
            case TYPE_CHANNEL_REF -> normalizeChannelRef(value, siteId);
            case TYPE_ARTICLE_REF -> normalizeArticleRef(value, siteId);
            case TYPE_BLOCK_REF -> normalizeBlockRef(value, siteId);
            case TYPE_VOTE_REF -> normalizeVoteRef(value, siteId);
            case TYPE_NUMBER -> normalizeNumber(value, def);
            case TYPE_ENUM -> normalizeEnum(value, def);
            case TYPE_BOOLEAN -> normalizeBoolean(value);
            case TYPE_LIST -> normalizeList(value, def, siteId);
            default -> Entry.same(value);
        };
    }

    /**
     * 栏目引用：接受裸ID（数字/数字串/{@code {"id":…}}）或别名（字符串/{@code {"alias":…}}，
     * 种子缺省值形态，§4.1）。经存在性 + 站点归属校验；引擎值为栏目ID，契约值为摘要对象。
     */
    private Entry normalizeChannelRef(Object value, Long siteId) {
        Long id = refId(value);
        Channel channel;
        if (id != null) {
            channel = channelService.select(id);
            if (channel != null && !siteId.equals(channel.getSiteId())) {
                channel = null;
            }
        } else {
            String alias = refAlias(value);
            if (StringUtils.isBlank(alias)) {
                return Entry.empty();
            }
            channel = channelService.findBySiteIdAndSlug(siteId, alias);
        }
        if (channel == null) {
            // 目标不存在/越站：引擎按缺失处理；契约保留占位供操作员发现失联引用
            return new Entry(null, id != null ? stub(id) : null);
        }
        Map<String, Object> summary = new LinkedHashMap<>(4);
        summary.put(ID, String.valueOf(channel.getId()));
        summary.put(ALIAS, channel.getSlug());
        summary.put("name", channel.getName());
        summary.put("url", channel.getUrl());
        return new Entry(channel.getId(), summary);
    }

    /**
     * 区块引用：接受裸ID（数字/数字串/{@code {"id":…}}）或别名（字符串/{@code {"alias":…}}，
     * 种子缺省值形态）。经存在性 + 站点可见范围（本站 ∪ 全局共享，与控制台区块目录同口径）校验；
     * 引擎值为区块ID（数据模板 {@code where.blockId}），契约值为摘要对象 {@code {id,alias,name,title}}。
     */
    private Entry normalizeBlockRef(Object value, Long siteId) {
        Long id = refId(value);
        Block block;
        if (id != null) {
            block = blockService.select(id);
            if (block != null && !siteId.equals(block.getSiteId()) && !block.isGlobal()) {
                block = null;
            }
        } else {
            String alias = refAlias(value);
            if (StringUtils.isBlank(alias)) {
                return Entry.empty();
            }
            block = blockService.findByScopeSiteIdAndAlias(siteId, alias);
        }
        if (block == null) {
            // 目标不存在/越站：引擎按缺失处理；契约保留占位供操作员发现失联引用
            return new Entry(null, id != null ? stub(id) : null);
        }
        Map<String, Object> summary = new LinkedHashMap<>(4);
        summary.put(ID, String.valueOf(block.getId()));
        summary.put(ALIAS, block.getAlias());
        summary.put("name", block.getName());
        summary.put("title", block.getTitle());
        return new Entry(block.getId(), summary);
    }

    /**
     * 投票引用：裸ID（数字/数字串/{@code {"id":…}}，无别名形态）。经存在性 + 站点归属校验；
     * 引擎值为投票ID（透传——实体由模板片段经 {@code [@Vote id=…]} 自取），契约值为摘要对象 {@code {id,title}}。
     */
    private Entry normalizeVoteRef(Object value, Long siteId) {
        Long id = refId(value);
        if (id == null) {
            return Entry.empty();
        }
        Vote vote = voteService.select(id);
        if (vote == null || !siteId.equals(vote.getSiteId())) {
            return new Entry(null, stub(id));
        }
        Map<String, Object> summary = new LinkedHashMap<>(2);
        summary.put(ID, String.valueOf(id));
        summary.put("title", vote.getTitle());
        return new Entry(id, summary);
    }

    /**
     * 文章引用：裸ID（数字/数字串/{@code {"id":…}}）。经存在性 + 站点归属校验；
     * 引擎值为文章ID，契约值为摘要对象。
     */
    private Entry normalizeArticleRef(Object value, Long siteId) {
        Long id = refId(value);
        if (id == null) {
            return Entry.empty();
        }
        Article article = articleService.select(id);
        if (article == null || !siteId.equals(article.getSiteId())) {
            return new Entry(null, stub(id));
        }
        Map<String, Object> summary = new LinkedHashMap<>(4);
        summary.put(ID, String.valueOf(id));
        summary.put("title", article.getTitle());
        summary.put("url", article.getUrl());
        summary.put("image", article.getImage());
        return new Entry(id, summary);
    }

    /**
     * 数字：按声明的 {@code min}/{@code max} 钳制（§3 类型化边界——超界值收敛而非拒绝）。
     */
    private static Entry normalizeNumber(Object value, FieldDef def) {
        Double number = doubleVal(value);
        if (number == null) {
            return Entry.empty();
        }
        Double min = def.getDouble("min");
        Double max = def.getDouble("max");
        double clamped = number;
        if (min != null && clamped < min) {
            clamped = min;
        }
        if (max != null && clamped > max) {
            clamped = max;
        }
        Object result = clamped == Math.floor(clamped) && !Double.isInfinite(clamped)
                ? (Object) (long) clamped : (Object) clamped;
        return Entry.same(result);
    }

    /**
     * 枚举：成员校验，非成员归缺失（不静默替换为其他值）。
     */
    private static Entry normalizeEnum(Object value, FieldDef def) {
        String str = value instanceof String s ? s : String.valueOf(value);
        return def.getStringList("values").contains(str) ? Entry.same(str) : Entry.empty();
    }

    private static Entry normalizeBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return Entry.same(bool);
        }
        if (value instanceof String str) {
            return Entry.same(Boolean.parseBoolean(str.trim()));
        }
        return Entry.empty();
    }

    /**
     * 列表：元素为引用类型时逐个规范化（引擎值仅保留有效ID，供置顶/去重；契约值含占位）；
     * 其他元素类型（含 object——页面本地编排条目）原样透传。
     */
    private Entry normalizeList(Object value, FieldDef def, Long siteId) {
        if (!(value instanceof List<?> list)) {
            return Entry.empty();
        }
        FieldDef of = def.getFieldDef("of");
        String ofType = of != null ? of.getType() : "";
        if (!TYPE_CHANNEL_REF.equals(ofType) && !TYPE_ARTICLE_REF.equals(ofType)) {
            return Entry.same(value);
        }
        List<Object> engine = new ArrayList<>(list.size());
        List<Object> contract = new ArrayList<>(list.size());
        for (Object element : list) {
            if (element == null) {
                continue;
            }
            Entry entry = TYPE_CHANNEL_REF.equals(ofType)
                    ? normalizeChannelRef(element, siteId)
                    : normalizeArticleRef(element, siteId);
            if (entry.engine() != null) {
                engine.add(entry.engine());
            }
            if (entry.contract() != null) {
                contract.add(entry.contract());
            }
        }
        return new Entry(List.copyOf(engine), List.copyOf(contract));
    }

    /**
     * 已删除/越站目标的契约占位：{@code {id, deleted:true}}（与 {@link RefResolver} 的卡片引用占位同型）。
     */
    private static Map<String, Object> stub(Long id) {
        Map<String, Object> ref = new LinkedHashMap<>(2);
        ref.put(ID, String.valueOf(id));
        ref.put(DELETED, true);
        return ref;
    }

    /**
     * 从引用值中取裸ID：数字 / 数字串 / {@code {"id": …}}。别名形态返回 {@code null}。
     */
    @Nullable
    private static Long refId(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object id = map.get(ID);
            return id != null ? refId(id) : null;
        }
        if (value instanceof Number number && number.longValue() > 0
                && number.longValue() == number.doubleValue()) {
            return number.longValue();
        }
        if (value instanceof String str && str.matches("\\d{1,19}")) {
            long id = Long.parseLong(str);
            return id > 0 ? id : null;
        }
        return null;
    }

    /**
     * 从引用值中取别名：非数字字符串 / {@code {"alias": …}}（种子缺省值形态）。
     */
    @Nullable
    private static String refAlias(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.get(ALIAS) instanceof String alias ? alias : null;
        }
        return value instanceof String str ? str : null;
    }

    @Nullable
    private static Double doubleVal(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String str && StringUtils.isNotBlank(str)) {
            try {
                return Double.parseDouble(str.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean isEmpty(@Nullable Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String str) {
            return str.isBlank();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        return false;
    }

    /**
     * 解析结果：引擎值 + 契约值（键集 = 组件声明的设置名，未设值为 {@code null}）。
     */
    public record Resolved(Map<String, Object> values, Map<String, Object> contract) {
        /**
         * 空结果（组件无设置声明 / Shape 不可解析）。
         */
        public static Resolved empty() {
            return new Resolved(Map.of(), Map.of());
        }
    }

    /**
     * 一条失效引用（{@link #staleRefs}）。
     *
     * @param setting   设置名
     * @param kind      目标种类（{@link #KIND_CHANNEL} / {@link #KIND_BLOCK} / {@link #KIND_VOTE} /
     *                  {@link #KIND_ARTICLE}）
     * @param targetRef 失效的目标引用（ID 或别名；列表设置为逗号分隔的失联元素）
     */
    public record StaleRef(String setting, String kind, String targetRef) {
    }

    /**
     * 单个设置的规范化结果（引擎值 / 契约值皆可为 {@code null} = 缺失）。
     */
    private record Entry(@Nullable Object engine, @Nullable Object contract) {
        static Entry empty() {
            return new Entry(null, null);
        }

        static Entry same(Object value) {
            return new Entry(value, value);
        }
    }
}
