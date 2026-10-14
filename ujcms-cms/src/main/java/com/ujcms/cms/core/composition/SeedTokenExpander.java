package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.mapper.BlockMapper;
import com.ujcms.cms.core.mapper.ChannelMapper;
import com.ujcms.cms.ext.domain.Vote;
import com.ujcms.cms.ext.mapper.VoteMapper;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 种子令牌展开器（theme-seed-export R1）：{@link ThemeSeedImporter} 面板设置展开的抽取形——
 * 递归遍历设置值（字符串/映射/列表），逐串按 <b>{@code {{asset:}}} → {@code {{theme}}} →
 * 内容引用令牌</b> 的顺序展开（与导入语义逐字一致，字段无关）。两个消费方共用同一遍历，
 * 绝不三写：
 * <ul>
 *   <li><b>严格模式</b>（导入，{@code lenient=false}）：引用令牌不可解析抛
 *       {@link SeedTokenException}——{@link ThemeSeedImporter} 捕获后行级降级（新区域整块跳过、
 *       刷新面板跳过，失败明细走 R2 通道），异常绝不逃逸为整次导入失败（2026-07-27 前是
 *       整次回滚 + HTTP 500，白板站点仅布局激活即触发）；</li>
 *   <li><b>宽容模式</b>（修复，{@code lenient=true}）：不可解析的引用令牌原样保留，
 *       调用方以 {@link #scanTokens} 对比前后清点跳过明细——修复动作绝不因个别悬空令牌整体失败。</li>
 * </ul>
 * {@link #scanTokens} 供构成健康检测（§11.5 未展开令牌规则）与修复的跳过清点使用：
 * 导入对<b>一切</b>字符串无条件展开令牌，故活数据中残留的令牌字面量必为 D9 之前导入的遗留。
 *
 * <p><b>别名映射遗留形</b>（D20，E4a 修订 D24）：令牌文法（A2）之前的手写种子以
 * {@code {"alias": "<x>"}} 对象承载栏目/区块引用——导入历来原样放行，渲染端迟绑可用但导出
 * 无从令牌化。该形的检测与修复走<b>字段定义驱动</b>分派（{@link #scanAliasRefs}/
 * {@link #expandAliasRefs}，channelRef/blockRef/list/object 递归）——绝不做字段无关识别，
 * 无辜的 {@code alias} 键不误报。
 *
 * @author PONY
 */
public class SeedTokenExpander {

    /**
     * 内容引用令牌（theme-marketplace §5.2，Phase A2；E4a / D24 增 block 种类；
     * E4b / D30 增 vote 种类）：{@code {{channel:<alias>}}} / {@code {{article:<originId>}}} /
     * {@code {{block:<alias>}}} / {@code {{vote:<originId>}}}——
     * channelRef/articleRef/blockRef/voteRef 值展开为字符串ID
     * （{@code FieldValues.asRefId} 接受字符串编码ID，字段类型无需改动）。
     */
    public static final Pattern REF_TOKEN = Pattern.compile("\\{\\{(channel|article|block|vote):([^}]+)}}");
    public static final String THEME_PLACEHOLDER = "{{theme}}";
    /**
     * 全部种子令牌字面量（检测口径）：{@code {{channel:}}}/{@code {{article:}}}/{@code {{block:}}}/
     * {@code {{vote:}}}/{@code {{asset:}}}/{@code {{theme}}}
     */
    private static final Pattern LITERAL_TOKEN =
            Pattern.compile("\\{\\{(?:(?:channel|article|block|vote|asset):[^}]+|theme)}}");
    private static final String REF_KIND_CHANNEL = "channel";
    private static final String REF_KIND_BLOCK = "block";
    private static final String REF_KIND_VOTE = "vote";

    private final ChannelMapper channelMapper;
    private final ArticleMapper articleMapper;
    private final BlockMapper blockMapper;
    private final VoteMapper voteMapper;

    public SeedTokenExpander(ChannelMapper channelMapper, ArticleMapper articleMapper,
                             BlockMapper blockMapper, VoteMapper voteMapper) {
        this.channelMapper = channelMapper;
        this.articleMapper = articleMapper;
        this.blockMapper = blockMapper;
        this.voteMapper = voteMapper;
    }

    /**
     * 展开整块面板设置（导入 {@code buildPanelPlacement} / 修复动作共用入口）。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> expandData(Map<String, Object> data, ExpandContext ctx) {
        return (Map<String, Object>) expandValue(data, ctx);
    }

    @Nullable
    private Object expandValue(@Nullable Object value, ExpandContext ctx) {
        if (value instanceof String str) {
            // {{asset:}} 先展开（内容媒体，确定性上传路径），再展开 {{theme}} 与内容引用令牌
            str = String.valueOf(ctx.assets().expand(str));
            if (str.contains(THEME_PLACEHOLDER)) {
                str = str.replace(THEME_PLACEHOLDER, ctx.assetPrefix());
            }
            return expandRefTokens(str, ctx);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>(map.size());
            map.forEach((k, v) -> out.put(String.valueOf(k), expandValue(v, ctx)));
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            list.forEach(item -> out.add(expandValue(item, ctx)));
            return out;
        }
        return value;
    }

    /**
     * 展开内容引用令牌（§5.2）：{@code {{channel:<alias>}}} 按站点内别名（种子或既有栏目均可）、
     * {@code {{block:<alias>}}} 按站点可见别名（本站 ∪ 全局，本站行胜出——E4a / D24）、
     * {@code {{article:<originId>}}} / {@code {{vote:<originId>}}} 按来源标记解析为字符串ID
     * （E4b / D30——投票无别名，与文章同为来源绑定）。不可解析时严格模式抛
     * {@link SeedTokenException}，宽容模式原样保留（来源标记缺失的操作员面板同理——
     * 文章/投票令牌无从解析，保留待人工处置）。
     */
    private String expandRefTokens(String value, ExpandContext ctx) {
        if (!value.contains("{{")) {
            return value;
        }
        Matcher matcher = REF_TOKEN.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String kind = matcher.group(1);
            String ref = matcher.group(2).trim();
            Long id;
            if (REF_KIND_CHANNEL.equals(kind)) {
                id = channelMapper.findBySiteIdAndSlug(ctx.siteId(), ref).stream()
                        .findFirst().map(Channel::getId).orElse(null);
            } else if (REF_KIND_BLOCK.equals(kind)) {
                id = blockMapper.listByScopeSiteIdAndAlias(ctx.siteId(), ref).stream()
                        .findFirst().map(Block::getId).orElse(null);
            } else if (REF_KIND_VOTE.equals(kind)) {
                Vote vote = ctx.origin() != null
                        ? voteMapper.selectByOrigin(ctx.siteId(), ctx.origin(), ref) : null;
                id = vote != null ? vote.getId() : null;
            } else {
                Article article = ctx.origin() != null
                        ? articleMapper.selectByOrigin(ctx.siteId(), ctx.origin(), ref) : null;
                id = article != null ? article.getId() : null;
            }
            if (id == null) {
                if (!ctx.lenient()) {
                    throw new SeedTokenException("{{" + kind + ":" + ref + "}}");
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            matcher.appendReplacement(sb, String.valueOf(id));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 递归清点值内的种子令牌字面量：每个命中回调一次 {@code (路径, 令牌)}
     * （路径形如 {@code items[0].image.url}；同一字符串含多个令牌则逐个回调）。
     */
    public static void scanTokens(@Nullable Object value, String path, BiConsumer<String, String> consumer) {
        if (value instanceof String str) {
            if (!str.contains("{{")) {
                return;
            }
            Matcher matcher = LITERAL_TOKEN.matcher(str);
            while (matcher.find()) {
                consumer.accept(path, matcher.group());
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> scanTokens(v, path.isEmpty() ? String.valueOf(k) : path + "." + k, consumer));
            return;
        }
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                scanTokens(list.get(i), path + "[" + i + "]", consumer);
            }
        }
    }

    /**
     * 值内全部种子令牌字面量（{@code 路径: 令牌} 展示形，遍历序）。
     */
    public static List<String> listTokens(@Nullable Object value) {
        List<String> tokens = new ArrayList<>();
        scanTokens(value, "", (path, token) -> tokens.add(path.isEmpty() ? token : path + ": " + token));
        return tokens;
    }

    // ----- 别名映射遗留形（D20：令牌文法之前的手写种子形）-----

    private static final String ALIAS_KEY = "alias";

    /**
     * 值是否为别名映射遗留形：仅含 {@code alias} 一键、值为非空字符串的对象。仅在 channelRef
     * 字段定义语境下调用（字段无关识别会误报无辜的 alias 键）。
     */
    public static boolean isAliasRefMap(@Nullable Object value) {
        return value instanceof Map<?, ?> map && map.size() == 1
                && map.get(ALIAS_KEY) instanceof String alias && !alias.isBlank();
    }

    /**
     * 按 Shape 设置字段定义清点别名映射遗留形：每个命中回调一次 {@code (路径, 展示形)}。
     */
    public static void scanAliasRefs(Map<String, Object> settings, Map<String, FieldDef> fieldDefs,
                                     BiConsumer<String, String> consumer) {
        settings.forEach((key, value) -> {
            FieldDef def = fieldDefs.get(key);
            if (def != null) {
                walkAliasRefs(value, def, key, null, null, consumer);
            }
        });
    }

    /**
     * 别名映射遗留形清单（{@code 路径: 展示形}，遍历序）。
     */
    public static List<String> listAliasRefs(Map<String, Object> settings, Map<String, FieldDef> fieldDefs) {
        List<String> hits = new ArrayList<>();
        scanAliasRefs(settings, fieldDefs, (path, display) -> hits.add(path + ": " + display));
        return hits;
    }

    /**
     * 展开别名映射遗留形（恒宽容——别名不可解析原样保留，与令牌宽容模式同语义）。
     * channelRef 按站内别名、blockRef 按站点可见别名（E4a / D24）解析为字符串ID。
     */
    public Map<String, Object> expandAliasRefs(Map<String, Object> settings, Map<String, FieldDef> fieldDefs,
                                               Long siteId) {
        Map<String, Object> out = new LinkedHashMap<>(settings.size());
        settings.forEach((key, value) -> {
            FieldDef def = fieldDefs.get(key);
            out.put(key, def != null
                    ? walkAliasRefs(value, def, key, alias -> channelIdByAlias(siteId, alias),
                            alias -> blockIdByAlias(siteId, alias), (p, t) -> { })
                    : value);
        });
        return out;
    }

    @Nullable
    private Long channelIdByAlias(Long siteId, String alias) {
        return channelMapper.findBySiteIdAndSlug(siteId, alias).stream()
                .findFirst().map(Channel::getId).orElse(null);
    }

    @Nullable
    private Long blockIdByAlias(Long siteId, String alias) {
        return blockMapper.listByScopeSiteIdAndAlias(siteId, alias).stream()
                .findFirst().map(Block::getId).orElse(null);
    }

    /**
     * 字段定义驱动的别名映射遍历（channelRef/blockRef 命中 + list/object 递归；其余类型不承载）。
     * {@code resolver} 为空即纯扫描；解析成功替换为字符串ID（与令牌展开产物同形）。
     */
    @Nullable
    private static Object walkAliasRefs(@Nullable Object value, FieldDef def, String path,
                                        @Nullable Function<String, Long> channelResolver,
                                        @Nullable Function<String, Long> blockResolver,
                                        BiConsumer<String, String> onHit) {
        if (value == null) {
            return null;
        }
        switch (SeedExportFieldRules.shapeRule(def.getType())) {
            case CHANNEL_REF -> {
                return resolveAliasRefMap(value, path, channelResolver, onHit);
            }
            case BLOCK_REF -> {
                return resolveAliasRefMap(value, path, blockResolver, onHit);
            }
            case LIST -> {
                FieldDef of = def.getFieldDef("of");
                if (!(value instanceof List<?> list) || of == null) {
                    return value;
                }
                List<Object> out = new ArrayList<>(list.size());
                for (int i = 0; i < list.size(); i++) {
                    out.add(walkAliasRefs(list.get(i), of, path + "[" + i + "]", channelResolver,
                            blockResolver, onHit));
                }
                return out;
            }
            case OBJECT -> {
                if (!(value instanceof Map<?, ?> map)) {
                    return value;
                }
                Map<String, FieldDef> fields = def.getFieldDefs("fields");
                Map<String, Object> out = new LinkedHashMap<>(map.size());
                map.forEach((k, v) -> {
                    String key = String.valueOf(k);
                    FieldDef field = fields.get(key);
                    out.put(key, field != null
                            ? walkAliasRefs(v, field, path + "." + key, channelResolver, blockResolver, onHit)
                            : v);
                });
                return out;
            }
            default -> {
                return value;
            }
        }
    }

    /**
     * 别名映射的命中回调 + 可选解析（引用类字段共用形）。
     */
    private static Object resolveAliasRefMap(Object value, String path,
                                             @Nullable Function<String, Long> resolver,
                                             BiConsumer<String, String> onHit) {
        if (isAliasRefMap(value)) {
            String alias = String.valueOf(((Map<?, ?>) value).get(ALIAS_KEY));
            onHit.accept(path, aliasDisplay(alias));
            if (resolver != null) {
                Long id = resolver.apply(alias);
                if (id != null) {
                    return String.valueOf(id);
                }
            }
        }
        return value;
    }

    /**
     * 别名映射的展示形（健康发现/跳过明细用）
     */
    public static String aliasDisplay(String alias) {
        return "{\"alias\": \"" + alias + "\"}";
    }

    /**
     * 单次展开的参数集
     *
     * @param assetPrefix 主题资源 URL 前缀（{@code <templateStorageUrl>/<siteId>/<themeName>}，{{theme}} 展开）
     * @param assets      资源令牌展开会话（{@code {{asset:}}}，{@link SeedAssetCopier}）
     * @param siteId      站点ID（{{channel:}} 别名解析域）
     * @param origin      来源标记（{{article:}} originId 解析域；操作员面板为 null——文章令牌不可解析）
     * @param lenient     宽容模式：不可解析的引用令牌原样保留（修复）；否则抛异常（导入）
     */
    public record ExpandContext(String assetPrefix, SeedAssetCopier.Session assets, Long siteId,
                                @Nullable String origin, boolean lenient) {
    }

    /**
     * 内容引用令牌不可解析（严格模式）
     */
    public static class SeedTokenException extends RuntimeException {
        public SeedTokenException(String token) {
            super(token);
        }
    }
}
