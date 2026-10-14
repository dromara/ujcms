package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.Topic;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.LayoutVariantService;
import com.ujcms.cms.core.service.TopicService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.core.service.args.ChannelArgs;
import com.ujcms.cms.core.service.args.TopicArgs;
import com.ujcms.cms.core.support.Commercial;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.cms.ext.domain.FormType;
import com.ujcms.cms.ext.service.FormService;
import com.ujcms.cms.ext.service.FormTypeService;
import com.ujcms.cms.ext.service.args.FormArgs;
import com.ujcms.cms.ext.service.args.FormTypeArgs;
import com.ujcms.common.query.QueryParser;
import com.ujcms.common.web.UrlBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

/**
 * 代表性预览地址解析器（preview-url-resolver §4.3）。给定（站点, 页面清单, [变体], [栏目]），
 * 产出能渲染该页面的<b>具体可访问 URL</b> 候选列表——构建器默认路径 / 路径选择器 / 范围自动寻址的
 * 数据源。三类页面（§3）：
 *
 * <ul>
 * <li><b>固定路由页</b>：页面清单 {@code route}（权威来源）∪ 内置固定路由表（无清单的系统模板）。
 * 恰一个候选。参数化路由（含 {@code [param]}）
 * 不是固定路由。</li>
 * <li><b>实体承载页</b>（栏目/文章/专题）：在 Java 中遍历站点实体、经 {@link LayoutVariantService}
 * 共享 helper 计算<b>有效模板</b>后按清单绑定过滤（D1——文章级/栏目级级联与 NULL 继承使 SQL 反查
 * 不可靠）；变体按有效模板<b>反查</b>（template-track-variants D2）。候选 URL 恒为
 * 动态 URL（D2）。</li>
 * <li><b>实例承载页</b>（FormType 每实例模板列）：{@link #resolveByTemplate} 反查产出（P3 模板列表
 * 预览按钮），页面名解析不产出。</li>
 * </ul>
 *
 * <p>排序与边界：栏目/专题按树序（{@code order,id}），文章按发布时间倒序且限定在候选栏目内
 * （一次有界查询）；{@code limit} 默认 20。无新表、无快照/栏目缓存之外的缓存（Q1：先测量）。
 *
 * @author PONY
 */
@Component
public class PreviewUrlResolver {
    /**
     * 候选类别：固定路由页
     */
    public static final String KIND_FIXED = "fixed";
    /**
     * 候选类别：表单页（FormType 每实例模板列反查，§3.3）
     */
    public static final String KIND_FORM = "form";
    /**
     * 空态原因：页面清单不存在（活动主题未声明此页面）
     */
    public static final String EMPTY_NO_MANIFEST = "no-manifest";
    /**
     * 空态原因：实体承载页无任何候选内容（新站点未导入演示数据等，§6）
     */
    public static final String EMPTY_NO_CONTENT = "no-content";

    /**
     * 内置固定路由表（§3.1 来源 2）：无页面清单的系统模板 → 固定路由。控制器仍是路由的
     * 唯一真相，本表随 {@code PreviewUrlResolverTest} 对 {@link UrlConstants} 断言（有常量者）。
     * {@code sys_survey} 为 T2 商业模板——表是静态元数据，未部署该层级时对应模板不会出现在清单绑定里。
     */
    /**
     * 仅商业版 T2 前台控制器提供的固定路由模板（表单类型页、问卷页）
     */
    private static final Set<String> TIER2_ROUTES = Set.of("sys_form_type", "sys_survey", "topics");

    private static final Map<String, String> FIXED_ROUTES = Map.ofEntries(
            Map.entry("sys_search", "/search"),
            Map.entry("sys_tag", "/tag"),
            Map.entry("sys_vote", "/vote"),
            Map.entry("sys_form_type", "/form-type"),
            Map.entry("sys_message_board", "/message-board"),
            Map.entry("sys_example", "/example"),
            Map.entry("sys_survey", "/survey"),
            Map.entry("topics", UrlConstants.TOPIC),
            Map.entry("mem_login", UrlConstants.LOGIN_URL),
            Map.entry("mem_register", "/register"),
            Map.entry("mem_password_reset", "/password-reset"));

    /**
     * 文章候选的超采样系数：先按候选栏目取回 {@code limit × N} 篇再逐篇算有效模板过滤
     * （文章级显式选择可能指向清单之外）。
     */
    private static final int ARTICLE_OVERFETCH = 3;
    /**
     * 文章查询窗口下限：变体/模板过滤在 Java 侧逐篇进行，小 {@code limit}（范围自动寻址传 1）
     * 配合稀有变体时按 {@code limit × 3} 取数会整窗错过——窗口不随 limit 缩小到失真。
     */
    private static final int ARTICLE_MIN_WINDOW = 60;

    private final ManifestService manifestService;
    private final LayoutVariantService layoutVariantService;
    private final ChannelService channelService;
    private final ArticleService articleService;
    private final TopicService topicService;
    private final FormTypeService formTypeService;
    private final FormService formService;
    /**
     * 当前商业版级别（可注入以便测试）：表单类型页/表单页、问卷页与专题页由 T2 前台控制器渲染，低级别不给出会 404 的预览地址
     */
    private final IntSupplier commercialTier;

    @Autowired
    public PreviewUrlResolver(ManifestService manifestService, LayoutVariantService layoutVariantService,
                              ChannelService channelService, ArticleService articleService,
                              TopicService topicService, FormTypeService formTypeService,
                              FormService formService) {
        this(manifestService, layoutVariantService, channelService, articleService, topicService, formTypeService,
                formService, Commercial::getTier);
    }

    PreviewUrlResolver(ManifestService manifestService, LayoutVariantService layoutVariantService,
                       ChannelService channelService, ArticleService articleService,
                       TopicService topicService, FormTypeService formTypeService,
                       FormService formService, IntSupplier commercialTier) {
        this.commercialTier = commercialTier;
        this.manifestService = manifestService;
        this.layoutVariantService = layoutVariantService;
        this.channelService = channelService;
        this.articleService = articleService;
        this.topicService = topicService;
        this.formTypeService = formTypeService;
        this.formService = formService;
    }

    /**
     * 解析候选预览地址。
     *
     * @param site      站点
     * @param page      页面名（{@code home}、{@code channel}…）
     * @param variant   变体过滤（实体解析出的变体值须等于该值；{@code null} 不过滤）
     * @param channelId 栏目过滤（栏目候选精确匹配；文章候选限于该栏目；{@code null} 不过滤）
     * @param q         名称/标题子串过滤（{@code null} 不过滤）
     * @param limit     候选数上限
     */
    public Result resolve(Site site, String page, @Nullable String variant,
                          @Nullable Long channelId, @Nullable String q, int limit) {
        RegistrySnapshot snapshot = manifestService.getSnapshot();
        PageManifest manifest = themeManifest(snapshot, site, page);
        if (manifest == null) {
            return new Result(List.of(), EMPTY_NO_MANIFEST);
        }
        List<Candidate> fixed = fixedCandidates(site, manifest);
        if (!fixed.isEmpty()) {
            return new Result(fixed, null);
        }
        List<Candidate> candidates = new ArrayList<>();
        Set<String> templates = new LinkedHashSet<>(manifest.getTemplates());
        // 栏目列表恒需（栏目候选 + 文章候选栏目级联）
        List<Channel> channels = channelService.selectList(ChannelArgs.of().siteId(site.getId()));
        collectChannels(candidates, site, manifest, templates, channels, variant, channelId, q, limit);
        collectArticles(candidates, site, manifest, templates, channels, variant, channelId, q, limit);
        collectTopics(candidates, site, manifest, templates, variant, q, limit);
        return new Result(candidates, candidates.isEmpty() ? EMPTY_NO_CONTENT : null);
    }

    /**
     * 按模板名解析候选预览地址（P3 模板列表预览按钮）。
     * 三来源按序：
     *
     * <ol>
     * <li><b>活动主题清单正向绑定</b>：{@code route} 固定候选；无 {@code route} 时按<b>单模板</b>
     * 过滤集走实体解析（与页面名解析同一渲染同径口径——绑定同页的其它变体模板不混入）。</li>
     * <li><b>内置固定路由表</b>（§3.1 来源 2）：无清单绑定的系统模板（{@code sys_*} / {@code mem_*}
     * / {@code topics}）。</li>
     * <li><b>FormType 反查</b>（§3.3）：{@code list_template_} / {@code item_template_} 为真每实例列
     * ——无变体间接与 NULL 继承，平面列查询方向正确（D1 的唯一例外）。</li>
     * </ol>
     */
    public Result resolveByTemplate(Site site, String template, int limit) {
        RegistrySnapshot snapshot = manifestService.getSnapshot();
        List<Candidate> candidates = new ArrayList<>();
        PageManifest manifest = themeManifestByTemplate(snapshot, site, template);
        if (manifest != null) {
            String route = manifest.getRoute();
            if (route != null && !route.contains("[")) {
                candidates.add(new Candidate(KIND_FIXED, manifestLabel(manifest), sitePath(site, route),
                        null, template, null));
            } else {
                Set<String> templates = Set.of(template);
                List<Channel> channels = channelService.selectList(ChannelArgs.of().siteId(site.getId()));
                collectChannels(candidates, site, manifest, templates, channels, null, null, null, limit);
                collectArticles(candidates, site, manifest, templates, channels, null, null, null, limit);
                collectTopics(candidates, site, manifest, templates, null, null, limit);
            }
        }
        if (candidates.isEmpty()) {
            String builtIn = FIXED_ROUTES.get(template);
            // 表单类型页与问卷页由商业版 T2 前台控制器渲染，社区版/T1 无此路由，不给出会 404 的预览地址
            if (builtIn != null && (commercialTier.getAsInt() >= 2 || !TIER2_ROUTES.contains(template))) {
                candidates.add(new Candidate(KIND_FIXED, template, sitePath(site, builtIn), null, template, null));
            }
        }
        collectForms(candidates, site, template, limit);
        return new Result(candidates, candidates.isEmpty() ? EMPTY_NO_CONTENT : null);
    }

    /**
     * FormType 反查候选（§3.3）。渲染同径 {@code FormController}：仅 {@code viewable} 类型；
     * {@code /form-item/{id}} 以该类型最新表单数据行为代表（无数据行则无候选）。
     */
    private void collectForms(List<Candidate> out, Site site, String template, int limit) {
        // 表单页面由商业版 T2 渲染（FormT2Controller），低级别无候选
        if (out.size() >= limit || commercialTier.getAsInt() < 2) {
            return;
        }
        List<FormType> types = formTypeService.selectList(FormTypeArgs.of().siteId(site.getId()).viewable(true));
        for (FormType type : types) {
            if (out.size() >= limit) {
                return;
            }
            if (template.equals(type.getListTemplate())) {
                out.add(new Candidate(KIND_FORM, type.getName(), sitePath(site, "/form-list/" + type.getId()),
                        String.valueOf(type.getId()), template, null));
            }
        }
        for (FormType type : types) {
            if (out.size() >= limit) {
                return;
            }
            if (template.equals(type.getItemTemplate())) {
                formService.selectList(FormArgs.of().siteId(site.getId()).typeId(type.getId()), 0, 1).stream()
                        .findFirst().ifPresent(form -> out.add(new Candidate(KIND_FORM, type.getName(),
                                sitePath(site, "/form-item/" + form.getId()), String.valueOf(form.getId()),
                                template, null)));
            }
        }
    }

    /**
     * 固定路由候选（§3.1 两来源合并）：清单 {@code route}（参数化路由除外）∪ 内置表。
     */
    private List<Candidate> fixedCandidates(Site site, PageManifest manifest) {
        List<Candidate> fixed = new ArrayList<>(2);
        String route = manifest.getRoute();
        if (route != null && !route.contains("[")) {
            fixed.add(new Candidate(KIND_FIXED, manifestLabel(manifest), sitePath(site, route), null, null, null));
        } else {
            for (String template : manifest.getTemplates()) {
                String builtIn = FIXED_ROUTES.get(template);
                // 表单类型页/问卷页/专题列表页由商业版 T2 前台控制器渲染，低级别不给出会 404 的预览地址（与 resolveByTemplate 同口径）
                if (builtIn != null && (commercialTier.getAsInt() >= 2 || !TIER2_ROUTES.contains(template))) {
                    fixed.add(new Candidate(KIND_FIXED, manifestLabel(manifest), sitePath(site, builtIn),
                            null, template, null));
                }
            }
        }
        return fixed;
    }

    private void collectChannels(List<Candidate> out, Site site, PageManifest manifest,
                                 Set<String> templates, List<Channel> channels, @Nullable String variant,
                                 @Nullable Long channelId, @Nullable String q, int limit) {
        for (Channel channel : channels) {
            if (out.size() >= limit) {
                return;
            }
            short type = channel.getType();
            // 转向类型栏目不渲染栏目模板（控制器直接重定向），不作候选
            if (type == Channel.TYPE_LINK || type == Channel.TYPE_LINK_CHILD) {
                continue;
            }
            if (channelId != null && !channelId.equals(channel.getId())) {
                continue;
            }
            if (!matchesQuery(q, channel.getName())) {
                continue;
            }
            LayoutVariantService.EffectiveTemplate effective =
                    layoutVariantService.effectiveChannelTemplate(site, channel);
            if (!templates.contains(effective.template())) {
                continue;
            }
            // 渲染同径（renderer parity）：变体 = 有效模板反查结果（template-track-variants D2）
            // ——凡有效模板被主题变体绑定的实体都有变体值，未绑定模板的实体不匹配任何变体值
            String resolvedVariant = effective.variant() != null ? effective.variant().getValue() : null;
            if (variant != null && !variant.equals(resolvedVariant)) {
                continue;
            }
            String template = effective.template();
            out.add(new Candidate(LayoutVariantCatalog.SURFACE_CHANNEL, channel.getName(),
                    siteRelative(channel.getDynamicUrl()), String.valueOf(channel.getId()),
                    template, resolvedVariant));
        }
    }

    private void collectArticles(List<Candidate> out, Site site, PageManifest manifest,
                                 Set<String> templates, List<Channel> channels, @Nullable String variant,
                                 @Nullable Long channelId, @Nullable String q, int limit) {
        if (out.size() >= limit) {
            return;
        }
        // 候选栏目（§3.2 排序与边界）：栏目级级联（channel.articleTemplate > 默认 article）
        // 落在清单绑定内的栏目——继承的文章都会命中；文章级显式选择在逐篇过滤时兜底
        List<Long> ids = channels.stream()
                .filter(ch -> templates.contains(channelLevelArticleTemplate(ch)))
                .map(Channel::getId)
                .toList();
        // 变体过滤时进一步收窄到栏目级变体命中的栏目（继承文章密集命中，取数窗口内必有代表）；
        // 变体按栏目级有效模板反查（template-track-variants D2，与逐篇过滤同径）；
        // 无栏目级命中则保持原候选——文章级模板覆盖仍可能命中，靠窗口逐篇过滤兜底
        if (variant != null) {
            Set<Long> variantIds = channels.stream()
                    .filter(ch -> {
                        LayoutVariantCatalog.Item item = layoutVariantService.resolveThemeVariantByTemplate(
                                site, LayoutVariantCatalog.SURFACE_ARTICLE, channelLevelArticleTemplate(ch));
                        return item != null && variant.equals(item.getValue());
                    })
                    .map(Channel::getId)
                    .collect(Collectors.toSet());
            List<Long> narrowed = ids.stream().filter(variantIds::contains).toList();
            if (!narrowed.isEmpty()) {
                ids = narrowed;
            }
        }
        if (channelId != null) {
            ids = ids.contains(channelId) ? List.of(channelId) : List.of();
        }
        if (ids.isEmpty()) {
            return;
        }
        // 全站栏目皆候选时省去 IN 限定
        List<Long> candidateChannelIds = ids.size() == channels.size() ? List.of() : ids;
        ArticleArgs args = ArticleArgs.of().siteId(site.getId());
        if (!candidateChannelIds.isEmpty()) {
            args.inChannelIds(candidateChannelIds);
        }
        if (StringUtils.isNotBlank(q)) {
            args.containsTitle(q);
        }
        args.status(List.of(Article.STATUS_PUBLISHED));
        args.getQueryMap().put(QueryParser.ORDER_BY, "publishDate_desc");
        List<Article> articles = articleService.selectList(args, 0,
                Math.max(Math.max(limit, 1) * ARTICLE_OVERFETCH, ARTICLE_MIN_WINDOW));
        for (Article article : articles) {
            if (out.size() >= limit) {
                return;
            }
            // 转向文章不渲染文章模板（控制器直接重定向），不作候选
            if (StringUtils.isNotBlank(article.getLinkUrl())) {
                continue;
            }
            LayoutVariantService.EffectiveTemplate effective =
                    layoutVariantService.effectiveArticleTemplate(site, article, article.getChannel());
            if (!templates.contains(effective.template())) {
                continue;
            }
            String resolvedVariant = effective.variant() != null ? effective.variant().getValue() : null;
            if (variant != null && !variant.equals(resolvedVariant)) {
                continue;
            }
            String template = effective.template();
            out.add(new Candidate(LayoutVariantCatalog.SURFACE_ARTICLE, article.getTitle(),
                    siteRelative(article.getDynamicUrl()), String.valueOf(article.getId()),
                    template, resolvedVariant));
        }
    }

    private void collectTopics(List<Candidate> out, Site site, PageManifest manifest,
                               Set<String> templates, @Nullable String variant, @Nullable String q, int limit) {
        // 专题页由商业版 T2 前台控制器渲染（TopicT2Controller），低级别无候选
        if (out.size() >= limit || commercialTier.getAsInt() < 2) {
            return;
        }
        List<Topic> topics = topicService.selectList(TopicArgs.of().siteId(site.getId()));
        for (Topic topic : topics) {
            if (out.size() >= limit) {
                return;
            }
            if (!matchesQuery(q, topic.getTitle())) {
                continue;
            }
            LayoutVariantService.EffectiveTemplate effective =
                    layoutVariantService.effectiveTopicTemplate(site, topic);
            if (!templates.contains(effective.template())) {
                continue;
            }
            String resolvedVariant = effective.variant() != null ? effective.variant().getValue() : null;
            if (variant != null && !variant.equals(resolvedVariant)) {
                continue;
            }
            String template = effective.template();
            out.add(new Candidate(LayoutVariantCatalog.SURFACE_TOPIC, topic.getTitle(),
                    siteRelative(topic.getDynamicUrl()), String.valueOf(topic.getId()),
                    template, resolvedVariant));
        }
    }

    /**
     * 栏目级文章模板级联（继承文章会得到的模板）：{@code channel.articleTemplate} 显式 &gt;
     * 默认 {@code article}（template-track-variants D1；布局列已随 SSR 轨道整体移除）。与
     * {@link LayoutVariantService#effectiveArticleTemplate} 的文章级规则同构，仅缺文章级选择。
     */
    private static String channelLevelArticleTemplate(Channel channel) {
        String template = channel.getArticleTemplate();
        return StringUtils.isNotBlank(template) ? template : Article.TEMPLATE_PREFIX;
    }

    /**
     * 活动主题（桌面优先，再移动）的页面清单。
     */
    @Nullable
    private static PageManifest themeManifest(RegistrySnapshot snapshot, Site site, String page) {
        Set<String> keys = new LinkedHashSet<>(2);
        keys.add(site.getThemeKey());
        keys.add(site.getMobileThemeKey());
        for (String themeKey : keys) {
            PageManifest manifest = snapshot.getThemeManifest(themeKey, page);
            if (manifest != null) {
                return manifest;
            }
        }
        return null;
    }

    /**
     * 活动主题（桌面优先，再移动）内绑定该模板的页面清单（正向绑定 {@code themeTemplateBindings}）。
     */
    @Nullable
    private static PageManifest themeManifestByTemplate(RegistrySnapshot snapshot, Site site, String template) {
        Set<String> keys = new LinkedHashSet<>(2);
        keys.add(site.getThemeKey());
        keys.add(site.getMobileThemeKey());
        for (String themeKey : keys) {
            PageManifest manifest = snapshot.resolveThemeManifestByTemplate(themeKey, template);
            if (manifest != null) {
                return manifest;
            }
        }
        return null;
    }

    private static String manifestLabel(PageManifest manifest) {
        Map<String, String> label = manifest.getLabel();
        String zh = label.get("zh-CN");
        if (StringUtils.isNotBlank(zh)) {
            return zh;
        }
        String en = label.get("en");
        return StringUtils.isNotBlank(en) ? en : manifest.getName();
    }

    private static boolean matchesQuery(@Nullable String q, @Nullable String name) {
        return StringUtils.isBlank(q) || Strings.CI.contains(name, q);
    }

    private static String sitePath(Site site, String route) {
        // 根路由不追加：避免子目录站点产出带尾斜杠的 /sub/（getDynamicUrl 已是 / 或 /sub）
        if (UrlBuilder.SLASH.equals(route)) {
            return siteRelative(site.getDynamicUrl());
        }
        return siteRelative(UrlBuilder.of(site.getDynamicUrl()).appendPath(route).toString());
    }

    /**
     * 归一化为站点相对路径：预览 iframe 的地址由控制台补预览基址（代理/同源），多域名站点的
     * {@code getDynamicUrl()} 携带协议与域名时剥去源部分只留路径。
     */
    private static String siteRelative(String url) {
        String path = url;
        int schemeIdx = path.indexOf("//");
        if (schemeIdx == 0 || (schemeIdx > 0 && path.startsWith("http") && path.charAt(schemeIdx - 1) == ':')) {
            int slash = path.indexOf('/', schemeIdx + 2);
            path = slash >= 0 ? path.substring(slash) : "/";
        }
        return path.isEmpty() ? "/" : path;
    }

    /**
     * 解析结果（{@code GET /api/backend/core/composition/preview-urls} 响应体；健康报告同型的
     * 构成域结果对象，控制器直接返回）。
     *
     * @param candidates  候选列表（固定路由恰一项；实体候选按 栏目 → 文章 → 专题、树序/发布倒序）
     * @param emptyReason 空态原因：{@code null}（有候选）/ {@link #EMPTY_NO_CONTENT} /
     *                    {@link #EMPTY_NO_MANIFEST}
     */
    public record Result(List<Candidate> candidates, @Nullable String emptyReason) {
    }

    /**
     * 候选预览地址
     *
     * @param kind     {@code fixed} / {@code channel} / {@code article} / {@code topic} / {@code form}
     * @param label    显示名（实体名称/标题；固定路由取页面标签；表单取类型名称）
     * @param url      站点相对动态 URL（D2；控制台补预览基址）
     * @param entityId 实体ID（字符串——雪花ID超过 JS 安全整数；固定路由为 {@code null}）
     * @param template 有效模板名（实体候选；固定路由可为 {@code null}）
     * @param variant  解析出的变体值（未命中变体 → {@code null}）
     */
    public record Candidate(String kind, String label, String url, @Nullable String entityId,
                            @Nullable String template, @Nullable String variant) {
    }
}
