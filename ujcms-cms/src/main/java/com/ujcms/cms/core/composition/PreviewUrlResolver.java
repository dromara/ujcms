package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.Topic;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.ConfigService;
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
import org.springframework.beans.factory.ObjectProvider;
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
 * 数据源。四类页面（§3）：
 *
 * <ul>
 * <li><b>固定路由页</b>：页面清单 {@code route}（权威来源）∪ 内置固定路由表（无清单的系统模板，含层级
 * 与功能开关门禁）。恰一个候选。参数化路由（含 {@code [param]}）不是固定路由。</li>
 * <li><b>实体承载页</b>（栏目/文章/专题）：在 Java 中遍历站点实体、经 {@link LayoutVariantService}
 * 共享 helper 计算<b>有效模板</b>后按清单绑定过滤（D1——文章级/栏目级级联与 NULL 继承使 SQL 反查
 * 不可靠）；变体按有效模板<b>反查</b>（template-track-variants D2）。候选 URL 恒为
 * 动态 URL（D2）。</li>
 * <li><b>实例承载页</b>（标签/投票/留言/会员主页/问卷/信件/访谈/表单提交…）：{@link TemplatePreviewSource}
 * 来源按模板名给出代表记录（P4）；FormType 每实例模板列由 {@link #resolveByTemplate} 反查（§3.3）。</li>
 * </ul>
 *
 * <p>按模板名解析（模板列表的预览按钮）无候选时给出<b>原因码</b>与<b>主体</b>（{@link Result#emptyReason()}
 * / {@link Result#emptySubject()}），控制台据此告诉操作员是缺内容、缺层级、功能未启用还是没有页面渲染该模板。
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
     * 候选类别：表单页（FormType 每实例模板列反查，§3.3；表单提交页同类）
     */
    public static final String KIND_FORM = "form";
    /**
     * 空态原因：页面清单不存在（活动主题未声明此页面）
     */
    public static final String EMPTY_NO_MANIFEST = "no-manifest";
    /**
     * 空态原因：页面可渲染，但站点尚无使用该模板的内容/记录（新站点未导入演示数据、无栏目选用该模板、
     * 表单类型无数据行等，§6）；{@link Result#emptySubject()} 给出缺的是哪类主体
     */
    public static final String EMPTY_NO_CONTENT = "no-content";
    /**
     * 空态原因：模板对应页面由更高商业层级的前台控制器渲染，当前部署没有该路由
     */
    public static final String EMPTY_TIER_LOCKED = "tier-locked";
    /**
     * 空态原因：模板对应功能未启用（注册 / 留言板 / 依申请公开 / 领导信箱 / 在线访谈），路由会 403 / 404；
     * {@link Result#emptySubject()} 给出是哪项功能
     */
    public static final String EMPTY_DISABLED = "disabled";
    /**
     * 空态原因：没有任何页面渲染该模板——既无清单绑定、不在固定路由表、无实例来源认领，也没有表单类型引用
     */
    public static final String EMPTY_UNSUPPORTED = "unsupported";

    /**
     * 主体词表（{@link Result#emptySubject()} 与实例候选的 {@link Candidate#kind()} 同词表）：
     * 栏目/文章/专题沿用 {@link LayoutVariantCatalog#SURFACE_CHANNEL} 等
     */
    public static final String SUBJECT_FORM = KIND_FORM;
    public static final String SUBJECT_TAG = "tag";
    public static final String SUBJECT_VOTE = "vote";
    public static final String SUBJECT_SURVEY = "survey";
    public static final String SUBJECT_MESSAGE_BOARD = "messageBoard";
    public static final String SUBJECT_MAILBOX = "mailbox";
    public static final String SUBJECT_INTERVIEW = "interview";
    public static final String SUBJECT_USER = "user";
    public static final String SUBJECT_REGISTER = "register";
    public static final String SUBJECT_DISCLOSURE = "disclosure";

    /**
     * 固定路由门禁：与渲染该页面的前台控制器同径（控制器 403 / 404 / 401 的条件在此表达为
     * 功能未启用 / 需会员登录）
     */
    private enum Gate {
        /** 无门禁 */
        NONE,
        /** 全局设置「注册」开关（{@code MemberController#register} 未启用 404） */
        REGISTER,
        /** 需会员登录（{@code SettingController} 全部 {@code isAuthenticated()}） */
        MEMBER,
        /** 站点留言板开关 */
        MESSAGE_BOARD,
        /** 站点留言板开关 + 发表需登录开关 */
        MESSAGE_BOARD_CREATE,
        /** 站点依申请公开开关 */
        DISCLOSURE,
        /** 站点依申请公开开关 + 申请需登录开关 */
        DISCLOSURE_CREATE,
        /** 站点领导信箱开关 */
        MAILBOX,
        /** 站点领导信箱开关 + 写信需登录开关 */
        MAILBOX_CREATE,
        /** 站点在线访谈开关 */
        INTERVIEW
    }

    /**
     * 固定路由条目
     *
     * @param path   路由（站点相对；{@code global} 时为部署相对——{@code subDir=false} 的全局控制器）
     * @param tier   渲染该路由的前台控制器所需最低商业层级（0 = 核心）
     * @param gate   门禁
     * @param global 是否全局路由（不随站点子目录生成孪生路由）
     */
    private record FixedRoute(String path, int tier, Gate gate, boolean global) {
        static FixedRoute core(String path) {
            return new FixedRoute(path, 0, Gate.NONE, false);
        }

        static FixedRoute core(String path, Gate gate) {
            return new FixedRoute(path, 0, gate, false);
        }

        static FixedRoute tier(int tier, String path, Gate gate) {
            return new FixedRoute(path, tier, gate, false);
        }
    }

    /**
     * 内置固定路由表（§3.1 来源 2）：无页面清单的系统模板 → 固定路由。控制器仍是路由的唯一真相，
     * 本表随 {@code PreviewUrlResolverTest} 对 {@link UrlConstants} 断言（有常量者）。层级与门禁与渲染
     * 该路由的控制器同径：层级不足不给出会 404 的地址（T2：表单类型页、问卷页、专题列表页、第三方注册页；
     * T4：依申请公开、领导信箱、在线访谈），功能未启用不给出会 403 / 404 的地址。
     * {@code sys_example*} 的前台控制器已停用，故不在表中（答 {@code unsupported}）。
     */
    private static final Map<String, FixedRoute> FIXED_ROUTES = Map.ofEntries(
            // ---- 核心前台控制器 ----
            Map.entry("sys_search", FixedRoute.core("/search")),
            Map.entry("sys_tag", FixedRoute.core("/tag")),
            Map.entry("sys_vote", FixedRoute.core("/vote")),
            Map.entry("sys_rss", FixedRoute.core("/rss")),
            Map.entry("sys_message_board", FixedRoute.core("/message-board", Gate.MESSAGE_BOARD)),
            Map.entry("sys_message_board_form", FixedRoute.core("/message-board/create", Gate.MESSAGE_BOARD_CREATE)),
            Map.entry("mem_login", FixedRoute.core(UrlConstants.LOGIN_URL)),
            Map.entry("mem_register", FixedRoute.core("/register", Gate.REGISTER)),
            Map.entry("mem_password_reset", FixedRoute.core("/password-reset")),
            Map.entry("mem_settings_home", FixedRoute.core("/settings", Gate.MEMBER)),
            Map.entry("mem_settings_profile", FixedRoute.core("/settings/profile", Gate.MEMBER)),
            Map.entry("mem_settings_avatar", FixedRoute.core("/settings/avatar", Gate.MEMBER)),
            Map.entry("mem_settings_account", FixedRoute.core("/settings/account", Gate.MEMBER)),
            Map.entry("mem_settings_comment", FixedRoute.core("/settings/comment", Gate.MEMBER)),
            Map.entry("mem_settings_favorite", FixedRoute.core("/settings/favorite", Gate.MEMBER)),
            Map.entry("mem_settings_history", FixedRoute.core("/settings/history", Gate.MEMBER)),
            Map.entry("mem_settings_follow", FixedRoute.core("/settings/follow", Gate.MEMBER)),
            Map.entry("mem_settings_friend", FixedRoute.core("/settings/friend", Gate.MEMBER)),
            Map.entry("mem_settings_message", FixedRoute.core("/settings/message", Gate.MEMBER)),
            Map.entry("mem_settings_notification", FixedRoute.core("/settings/notification", Gate.MEMBER)),
            // ---- 商业版 T2 前台控制器 ----
            Map.entry("sys_form_type", FixedRoute.tier(2, "/form-type", Gate.NONE)),
            Map.entry("sys_survey", FixedRoute.tier(2, "/survey", Gate.NONE)),
            Map.entry("topics", FixedRoute.tier(2, UrlConstants.TOPIC, Gate.NONE)),
            // 第三方登录注册页：全局流程控制器（subDir=false），不随子目录站点生成孪生路由
            Map.entry("mem_oauth_register", new FixedRoute("/oauth/register", 2, Gate.REGISTER, true)),
            // ---- 商业版 T4 前台控制器 ----
            Map.entry("sys_disclosure", FixedRoute.tier(4, "/disclosure", Gate.DISCLOSURE)),
            Map.entry("sys_disclosure_form", FixedRoute.tier(4, "/disclosure/create", Gate.DISCLOSURE_CREATE)),
            Map.entry("sys_disclosure_query", FixedRoute.tier(4, "/disclosure/query", Gate.DISCLOSURE)),
            Map.entry("sys_mailbox", FixedRoute.tier(4, "/mailbox", Gate.MAILBOX)),
            Map.entry("sys_mailbox_form", FixedRoute.tier(4, "/mailbox/create", Gate.MAILBOX_CREATE)),
            Map.entry("sys_mailbox_query", FixedRoute.tier(4, "/mailbox/query", Gate.MAILBOX)),
            Map.entry("sys_interview", FixedRoute.tier(4, "/interview", Gate.INTERVIEW)));

    /**
     * 商业版实例页来源的静态层级表：对应 {@link TemplatePreviewSource} Bean 未加载（部署层级不足）时，
     * 据此答 {@code tier-locked} 而非 {@code unsupported}。Bean 已加载时以来源为准，本表不参与。
     */
    private static final Map<String, Integer> SOURCE_TIERS = Map.of(
            "sys_survey_show", 2,
            "sys_survey_result", 2,
            "form_submit", 2,
            "sys_mailbox_item", 4,
            "sys_interview_item", 4);

    /**
     * 表单类型模板的命名约定前缀（{@code form_list*} / {@code form_item*}）：仅用于空态原因的层级/主体判定，
     * 反查本身仍按表单类型的模板列精确匹配
     */
    private static final String FORM_TEMPLATE_PREFIX = "form_";

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
    private final ConfigService configService;
    /**
     * 实例页来源（核心内置 + 已加载商业层级注册的）
     */
    private final List<TemplatePreviewSource> sources;
    /**
     * 当前商业版级别（可注入以便测试）：表单类型页/表单页、问卷页与专题页由 T2 前台控制器渲染，
     * 政务页面由 T4 渲染，低级别不给出会 404 的预览地址
     */
    private final IntSupplier commercialTier;

    @Autowired
    public PreviewUrlResolver(ManifestService manifestService, LayoutVariantService layoutVariantService,
                              ChannelService channelService, ArticleService articleService,
                              TopicService topicService, FormTypeService formTypeService,
                              FormService formService, ConfigService configService,
                              ObjectProvider<TemplatePreviewSource> sources) {
        this(manifestService, layoutVariantService, channelService, articleService, topicService, formTypeService,
                formService, configService, sources.orderedStream().toList(), Commercial::getTier);
    }

    PreviewUrlResolver(ManifestService manifestService, LayoutVariantService layoutVariantService,
                       ChannelService channelService, ArticleService articleService,
                       TopicService topicService, FormTypeService formTypeService,
                       FormService formService, ConfigService configService,
                       List<TemplatePreviewSource> sources, IntSupplier commercialTier) {
        this.commercialTier = commercialTier;
        this.manifestService = manifestService;
        this.layoutVariantService = layoutVariantService;
        this.channelService = channelService;
        this.articleService = articleService;
        this.topicService = topicService;
        this.formTypeService = formTypeService;
        this.formService = formService;
        this.configService = configService;
        this.sources = List.copyOf(sources);
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
        List<Candidate> fixed = fixedCandidates(site, manifest, limit);
        if (!fixed.isEmpty()) {
            return new Result(fixed, null);
        }
        List<Candidate> candidates = new ArrayList<>();
        Set<String> templates = new LinkedHashSet<>(manifest.getTemplates());
        // 栏目列表恒需（栏目候选 + 文章候选栏目级联）
        List<Channel> channels = channelService.selectList(ChannelArgs.of().siteId(site.getId()));
        collectChannels(candidates, site, templates, channels, variant, channelId, q, limit);
        collectArticles(candidates, site, templates, channels, variant, channelId, q, limit);
        collectTopics(candidates, site, templates, variant, q, limit);
        return new Result(candidates, candidates.isEmpty() ? EMPTY_NO_CONTENT : null);
    }

    /**
     * 按模板名解析候选预览地址（P3 模板列表预览按钮；P4 加入原因码）。
     * 四来源按序，首个有候选者即为结果；全部落空时取<b>首个认领该模板的来源</b>给出的原因，无人认领答
     * {@link #EMPTY_UNSUPPORTED}：
     *
     * <ol>
     * <li><b>活动主题清单正向绑定</b>：{@code route} 固定候选；无 {@code route} 时按<b>单模板</b>
     * 过滤集走实体解析（与页面名解析同一渲染同径口径——绑定同页的其它变体模板不混入）。实体面模板
     * （{@code channel*} / {@code article*} / {@code topic*}）无代表实体 → {@code no-content}。</li>
     * <li><b>内置固定路由表</b>（§3.1 来源 2）：无清单绑定的系统模板（{@code sys_*} / {@code mem_*}
     * / {@code topics}），含层级与功能开关门禁，需会员登录的页面以 {@link Candidate#loginRequired()}
     * 标出（控制台走预览令牌身份打开）。</li>
     * <li><b>实例页来源</b>（{@link TemplatePreviewSource}，§3.4）：标签/投票/留言/会员主页/问卷/信件/
     * 访谈/表单提交页的代表记录；来源未加载的商业模板由静态层级表答 {@code tier-locked}。</li>
     * <li><b>FormType 反查</b>（§3.3）：{@code list_template_} / {@code item_template_} 为真每实例列
     * ——无变体间接与 NULL 继承，平面列查询方向正确（D1 的唯一例外）。</li>
     * </ol>
     */
    public Result resolveByTemplate(Site site, String template, int limit) {
        RegistrySnapshot snapshot = manifestService.getSnapshot();
        Probe probe = new Probe();
        int tier = commercialTier.getAsInt();
        // ---- 来源 1：活动主题清单正向绑定 ----
        PageManifest manifest = themeManifestByTemplate(snapshot, site, template);
        if (manifest != null) {
            String route = manifest.getRoute();
            if (route != null && !route.contains("[")) {
                probe.candidates.add(new Candidate(KIND_FIXED, manifestLabel(manifest), sitePath(site, route),
                        null, template, null));
                return probe.result();
            }
            Set<String> templates = Set.of(template);
            List<Channel> channels = channelService.selectList(ChannelArgs.of().siteId(site.getId()));
            collectChannels(probe.candidates, site, templates, channels, null, null, null, limit);
            collectArticles(probe.candidates, site, templates, channels, null, null, null, limit);
            collectTopics(probe.candidates, site, templates, null, null, limit);
            if (probe.hit()) {
                return probe.result();
            }
            // 实体面模板无代表实体 → 需内容（专题在低层级是层级问题）；其余绑定模板交由后续来源判定
            String surface = entitySurface(template);
            if (LayoutVariantCatalog.SURFACE_TOPIC.equals(surface) && tier < 2) {
                probe.miss(EMPTY_TIER_LOCKED, null);
            } else if (surface != null) {
                probe.miss(EMPTY_NO_CONTENT, surface);
            }
        }
        // ---- 来源 2：内置固定路由表（层级 + 门禁） ----
        FixedRoute fixed = FIXED_ROUTES.get(template);
        if (fixed != null) {
            fixedCandidate(probe, site, template, template, fixed);
            if (probe.hit()) {
                return probe.result();
            }
        }
        // ---- 来源 3：实例页来源 ----
        boolean claimed = false;
        for (TemplatePreviewSource source : sources) {
            if (!source.templates().contains(template)) {
                continue;
            }
            claimed = true;
            Result found = source.find(site, template, limit);
            if (!found.candidates().isEmpty()) {
                probe.candidates.addAll(found.candidates().subList(0, Math.min(limit, found.candidates().size())));
                return probe.result();
            }
            probe.miss(found.emptyReason() != null ? found.emptyReason() : EMPTY_NO_CONTENT, found.emptySubject());
        }
        if (!claimed) {
            Integer requiredTier = SOURCE_TIERS.get(template);
            if (requiredTier != null && tier < requiredTier) {
                probe.miss(EMPTY_TIER_LOCKED, null);
            }
        }
        // ---- 来源 4：FormType 每实例模板列反查（T2 前台控制器渲染） ----
        boolean formNamed = template.startsWith(FORM_TEMPLATE_PREFIX);
        if (tier < 2) {
            if (formNamed) {
                probe.miss(EMPTY_TIER_LOCKED, null);
            }
            return probe.result();
        }
        collectForms(probe.candidates, site, template, limit);
        if (!probe.hit() && formNamed) {
            probe.miss(EMPTY_NO_CONTENT, SUBJECT_FORM);
        }
        return probe.result();
    }

    /**
     * FormType 反查候选（§3.3）。渲染同径 {@code FormT2Controller}：仅 {@code viewable} 类型；
     * {@code /form-item/{id}} 以该类型最新表单数据行为代表（无数据行则无候选）。调用方已按 T2 门禁。
     */
    private void collectForms(List<Candidate> out, Site site, String template, int limit) {
        if (out.size() >= limit) {
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
     * 页面名解析的固定候选（§3.1 两来源合并）：清单 {@code route}（参数化路由除外）∪ 内置表 ∪ 实例页来源
     * （绑定在清单里的实例页模板，如系统页清单的 {@code sys_tag_item} / {@code mem_user}，每个模板只取一条
     * 代表——标签/投票实例不应挤掉同清单里排在后面的固定页）。
     * 层级不足 / 功能未启用的条目跳过（页面名解析的空态仍是 {@code no-content}）。
     */
    private List<Candidate> fixedCandidates(Site site, PageManifest manifest, int limit) {
        List<Candidate> fixed = new ArrayList<>(2);
        String route = manifest.getRoute();
        if (route != null && !route.contains("[")) {
            fixed.add(new Candidate(KIND_FIXED, manifestLabel(manifest), sitePath(site, route), null, null, null));
            return fixed;
        }
        Probe probe = new Probe();
        for (String template : manifest.getTemplates()) {
            if (probe.candidates.size() >= limit) {
                break;
            }
            FixedRoute entry = FIXED_ROUTES.get(template);
            if (entry != null) {
                fixedCandidate(probe, site, template, manifestLabel(manifest), entry);
                continue;
            }
            for (TemplatePreviewSource source : sources) {
                if (source.templates().contains(template)) {
                    probe.candidates.addAll(source.find(site, template, 1).candidates());
                }
            }
        }
        fixed.addAll(probe.candidates);
        return fixed;
    }

    /**
     * 固定路由条目 → 候选或空态原因（层级不足 → {@code tier-locked}；功能未启用 → {@code disabled}）。
     */
    private void fixedCandidate(Probe probe, Site site, String template, String label, FixedRoute entry) {
        if (commercialTier.getAsInt() < entry.tier()) {
            probe.miss(EMPTY_TIER_LOCKED, null);
            return;
        }
        Access access = checkGate(site, entry.gate());
        if (!access.allowed()) {
            probe.miss(access.reason(), access.subject());
            return;
        }
        String url = entry.global() ? globalPath(site, entry.path()) : sitePath(site, entry.path());
        probe.candidates.add(new Candidate(KIND_FIXED, label, url, null, template, null, access.loginRequired()));
    }

    /**
     * 门禁判定（与前台控制器同径）
     */
    private Access checkGate(Site site, Gate gate) {
        return switch (gate) {
            case NONE -> Access.PUBLIC;
            case MEMBER -> Access.MEMBER;
            case REGISTER -> configService.getUnique().getRegister().isEnabled()
                    ? Access.PUBLIC : Access.disabled(SUBJECT_REGISTER);
            case MESSAGE_BOARD -> site.getMessageBoard().isEnabled()
                    ? Access.PUBLIC : Access.disabled(SUBJECT_MESSAGE_BOARD);
            case MESSAGE_BOARD_CREATE -> {
                Site.MessageBoard settings = site.getMessageBoard();
                yield !settings.isEnabled() ? Access.disabled(SUBJECT_MESSAGE_BOARD)
                        : settings.isLoginRequired() ? Access.MEMBER : Access.PUBLIC;
            }
            case DISCLOSURE -> site.getDisclosure().isEnabled()
                    ? Access.PUBLIC : Access.disabled(SUBJECT_DISCLOSURE);
            case DISCLOSURE_CREATE -> {
                Site.Disclosure settings = site.getDisclosure();
                yield !settings.isEnabled() ? Access.disabled(SUBJECT_DISCLOSURE)
                        : settings.isLoginRequired() ? Access.MEMBER : Access.PUBLIC;
            }
            case MAILBOX -> site.getMailbox().isEnabled()
                    ? Access.PUBLIC : Access.disabled(SUBJECT_MAILBOX);
            case MAILBOX_CREATE -> {
                Site.Mailbox settings = site.getMailbox();
                yield !settings.isEnabled() ? Access.disabled(SUBJECT_MAILBOX)
                        : settings.isLoginRequired() ? Access.MEMBER : Access.PUBLIC;
            }
            case INTERVIEW -> site.getInterview().isEnabled()
                    ? Access.PUBLIC : Access.disabled(SUBJECT_INTERVIEW);
        };
    }

    private void collectChannels(List<Candidate> out, Site site, Set<String> templates, List<Channel> channels,
                                 @Nullable String variant, @Nullable Long channelId, @Nullable String q,
                                 int limit) {
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

    private void collectArticles(List<Candidate> out, Site site, Set<String> templates, List<Channel> channels,
                                 @Nullable String variant, @Nullable Long channelId, @Nullable String q,
                                 int limit) {
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

    private void collectTopics(List<Candidate> out, Site site, Set<String> templates,
                               @Nullable String variant, @Nullable String q, int limit) {
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
     * 实体面模板的主体（按模板命名约定前缀；栏目/文章/专题的模板选择器即按此前缀列出模板）
     */
    @Nullable
    private static String entitySurface(String template) {
        if (template.startsWith(Channel.TEMPLATE_PREFIX)) {
            return LayoutVariantCatalog.SURFACE_CHANNEL;
        }
        if (template.startsWith(Article.TEMPLATE_PREFIX)) {
            return LayoutVariantCatalog.SURFACE_ARTICLE;
        }
        if (template.startsWith(Topic.TEMPLATE_PREFIX)) {
            return LayoutVariantCatalog.SURFACE_TOPIC;
        }
        return null;
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

    /**
     * 站点相对路径：站点动态前缀（部署路径 + 子目录）拼固定路由。供 {@link TemplatePreviewSource} 实现复用。
     */
    public static String sitePath(Site site, String route) {
        // 根路由不追加：避免子目录站点产出带尾斜杠的 /sub/（getDynamicUrl 已是 / 或 /sub）
        if (UrlBuilder.SLASH.equals(route)) {
            return siteRelative(site.getDynamicUrl());
        }
        return siteRelative(UrlBuilder.of(site.getDynamicUrl()).appendPath(route).toString());
    }

    /**
     * 全局路由（{@code subDir=false} 的控制器）：部署路径拼路由，不带站点子目录——子目录站点不生成这类
     * 路由的孪生映射，带子目录会 404。
     */
    private static String globalPath(Site site, String route) {
        String base = siteRelative(site.getDynamicUrl());
        String subDir = site.getSubDir();
        if (StringUtils.isNotBlank(subDir) && base.endsWith(UrlBuilder.SLASH + subDir)) {
            base = base.substring(0, base.length() - subDir.length() - 1);
        }
        return siteRelative(UrlBuilder.of(base.isEmpty() ? UrlBuilder.SLASH : base).appendPath(route).toString());
    }

    /**
     * 归一化为站点相对路径：预览 iframe 的地址由控制台补预览基址（代理/同源），多域名站点的
     * {@code getDynamicUrl()} 携带协议与域名时剥去源部分只留路径。
     */
    public static String siteRelative(String url) {
        String path = url;
        int schemeIdx = path.indexOf("//");
        if (schemeIdx == 0 || (schemeIdx > 0 && path.startsWith("http") && path.charAt(schemeIdx - 1) == ':')) {
            int slash = path.indexOf('/', schemeIdx + 2);
            path = slash >= 0 ? path.substring(slash) : "/";
        }
        return path.isEmpty() ? "/" : path;
    }

    /**
     * 固定路由门禁结果
     *
     * @param allowed       是否可给出地址
     * @param loginRequired 可给出但需会员登录（控制台以预览令牌身份打开）
     * @param reason        不可给出的原因（{@link #EMPTY_DISABLED}）
     * @param subject       不可给出的主体（未启用的功能）
     */
    private record Access(boolean allowed, boolean loginRequired, @Nullable String reason,
                          @Nullable String subject) {
        static final Access PUBLIC = new Access(true, false, null, null);
        static final Access MEMBER = new Access(true, true, null, null);

        static Access disabled(String subject) {
            return new Access(false, false, EMPTY_DISABLED, subject);
        }
    }

    /**
     * 按模板名解析的累积器：候选 + 首个认领来源给出的空态原因
     */
    private static final class Probe {
        private final List<Candidate> candidates = new ArrayList<>();
        @Nullable
        private String reason;
        @Nullable
        private String subject;

        void miss(String reason, @Nullable String subject) {
            if (this.reason == null) {
                this.reason = reason;
                this.subject = subject;
            }
        }

        boolean hit() {
            return !candidates.isEmpty();
        }

        Result result() {
            if (hit()) {
                return new Result(List.copyOf(candidates), null, null);
            }
            return new Result(List.of(), reason != null ? reason : EMPTY_UNSUPPORTED, subject);
        }
    }

    /**
     * 解析结果（{@code GET /api/backend/core/composition/preview-urls} 响应体；健康报告同型的
     * 构成域结果对象，控制器直接返回）。
     *
     * @param candidates   候选列表（固定路由恰一项；实体候选按 栏目 → 文章 → 专题、树序/发布倒序）
     * @param emptyReason  空态原因：{@code null}（有候选）/ {@link #EMPTY_NO_CONTENT} /
     *                     {@link #EMPTY_NO_MANIFEST} / {@link #EMPTY_TIER_LOCKED} / {@link #EMPTY_DISABLED}
     *                     / {@link #EMPTY_UNSUPPORTED}（后三者仅按模板名解析给出）
     * @param emptySubject 空态主体：{@code no-content} 时缺的是哪类内容（{@code channel} / {@code article}
     *                     / {@code topic} / {@code form} / {@code tag} …），{@code disabled} 时未启用的是哪项
     *                     功能（{@code register} / {@code messageBoard} / {@code disclosure} / {@code mailbox}
     *                     / {@code interview}）；其余 {@code null}
     */
    public record Result(List<Candidate> candidates, @Nullable String emptyReason, @Nullable String emptySubject) {
        public Result(List<Candidate> candidates, @Nullable String emptyReason) {
            this(candidates, emptyReason, null);
        }
    }

    /**
     * 候选预览地址
     *
     * @param kind          {@code fixed} / {@code channel} / {@code article} / {@code topic} / {@code form}
     *                      / {@code tag} / {@code vote} / {@code messageBoard} / {@code user} / {@code survey}
     *                      / {@code mailbox} / {@code interview}
     * @param label         显示名（实体名称/标题；固定路由取页面标签；表单取类型名称）
     * @param url           站点相对动态 URL（D2；控制台补预览基址）
     * @param entityId      实体ID（字符串——雪花ID超过 JS 安全整数；固定路由为 {@code null}）
     * @param template      有效模板名（实体候选；固定路由可为 {@code null}）
     * @param variant       解析出的变体值（未命中变体 → {@code null}）
     * @param loginRequired 页面需会员登录（{@code isAuthenticated()} 门禁）——匿名打开会被送去登录页，
     *                      控制台应以预览令牌身份（{@code _preview=draft&_ptoken=} 或 {@code _theme=&_ptoken=}）打开
     */
    public record Candidate(String kind, String label, String url, @Nullable String entityId,
                            @Nullable String template, @Nullable String variant, boolean loginRequired) {
        public Candidate(String kind, String label, String url, @Nullable String entityId,
                         @Nullable String template, @Nullable String variant) {
            this(kind, label, url, entityId, template, variant, false);
        }
    }
}
