package com.ujcms.cms.core.service;

import com.ujcms.cms.core.component.TemplateService;
import com.ujcms.cms.core.composition.LayoutVariantCatalog;
import com.ujcms.cms.core.composition.ManifestService;
import com.ujcms.cms.core.composition.RegistrySnapshot;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.Topic;
import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.mapper.ChannelMapper;
import com.ujcms.cms.core.mapper.TopicMapper;
import com.ujcms.cms.core.support.Contexts;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 布局变体 Service（theme-marketplace R1 统一注册表；template-track-variants 轨道拆分重置）。
 * 变体目录是 {@link RegistrySnapshot} 内的注册表概念，由活动主题清单声明——<b>模板元数据</b>
 * （标签 + 设置结构，经 {@code template} 绑定挂在模板上）。有效模板即页面类型身份；活动变体按
 * 模板<b>反查</b>（{@link #resolveThemeVariantByTemplate}），template-track-variants D1/D2。
 *
 * @author PONY
 */
@Service
public class LayoutVariantService {
    private static final Logger logger = LoggerFactory.getLogger(LayoutVariantService.class);

    private final ManifestService manifestService;
    private final TemplateService templateService;
    private final ChannelMapper channelMapper;
    private final ArticleMapper articleMapper;
    private final TopicMapper topicMapper;

    public LayoutVariantService(ManifestService manifestService, TemplateService templateService,
                                ChannelMapper channelMapper, ArticleMapper articleMapper, TopicMapper topicMapper) {
        this.manifestService = manifestService;
        this.templateService = templateService;
        this.channelMapper = channelMapper;
        this.articleMapper = articleMapper;
        this.topicMapper = topicMapper;
    }

    /**
     * 模板轨道目录（活动主题清单声明，桌面 + 移动去重合并，含禁用项，按声明顺序；
     * 同 value 桌面主题胜出）。构建器模板轨道变体切换器的选项与标签来源（template-track-variants D5）。
     *
     * @param site 站点（活动主题据此解析）
     */
    public List<LayoutVariantCatalog.Item> listThemeBySurface(Site site, String surface) {
        RegistrySnapshot snapshot = manifestService.getSnapshot();
        Map<String, LayoutVariantCatalog.Item> byValue = new LinkedHashMap<>();
        for (String themeKey : activeThemeKeys(site)) {
            snapshot.getThemeLayoutVariants(themeKey).forSurface(surface)
                    .forEach(item -> byValue.putIfAbsent(item.getValue(), item));
        }
        return new ArrayList<>(byValue.values());
    }

    /**
     * 模板轨道活动变体反查（template-track-variants D2）：当前请求主题（移动端取移动主题，与
     * {@code Site.assembleTemplate} 一致）声明目录中 {@code template} 绑定等于给定有效模板的首个
     * <b>启用</b>变体。多变体绑定同一模板时先声明者胜出（{@code SnapshotValidator} 加载期告警）。
     *
     * @param site     站点
     * @param surface  {@code channel} / {@code article} / {@code topic}
     * @param template 有效的未组装模板名（如 {@code channel}、{@code channel_video}）
     * @return 命中的启用变体；无 → {@code null}（未绑定变体的模板没有变体上下文）
     */
    public LayoutVariantCatalog.@Nullable Item resolveThemeVariantByTemplate(Site site, String surface,
                                                                             @Nullable String template) {
        if (StringUtils.isBlank(template)) {
            return null;
        }
        String themeKey = Contexts.isMobile() ? site.getMobileThemeKey() : site.getThemeKey();
        return manifestService.getSnapshot().getThemeLayoutVariants(themeKey).forSurface(surface).stream()
                .filter(item -> item.isEnabled() && template.equals(item.getTemplate()))
                .findFirst().orElse(null);
    }

    /**
     * 指定主题声明目录中 {@code value} 命中的启用变体所绑定的模板（种子导入物化用，
     * template-track-variants D8——渲染期不再有布局列中间层，种子的变体值经安装主题的绑定
     * 落到显式 {@code *_template_} 列）。主题键显式传入：导入可发生在「先装不激活」时，
     * 不能依赖请求主题上下文。
     */
    @Nullable
    public String themeBoundTemplate(String themeKey, String surface, @Nullable String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return manifestService.getSnapshot().getThemeLayoutVariants(themeKey).forSurface(surface).stream()
                .filter(item -> item.isEnabled() && value.equals(item.getValue()))
                .findFirst().map(LayoutVariantCatalog.Item::getTemplate)
                .filter(StringUtils::isNotBlank).orElse(null);
    }

    /**
     * 指定主题声明目录中 {@code template} 绑定命中的启用变体值（种子导出反查用，D8；
     * 多变体绑定同一模板时先声明者胜出，与 {@link #resolveThemeVariantByTemplate} 同规）。
     */
    @Nullable
    public String themeVariantValueByTemplate(String themeKey, String surface, @Nullable String template) {
        if (StringUtils.isBlank(template)) {
            return null;
        }
        return manifestService.getSnapshot().getThemeLayoutVariants(themeKey).forSurface(surface).stream()
                .filter(item -> item.isEnabled() && template.equals(item.getTemplate()))
                .findFirst().map(LayoutVariantCatalog.Item::getValue).orElse(null);
    }

    /**
     * 有效模板解析结果（preview-url-resolver §4.2 / template-track-variants §4）：模板轨道的
     * 「显式模板选择 &gt; 默认」两级规则 + 变体反查收口于此——前台控制器与
     * {@code PreviewUrlResolver} 共用，二者永不漂移（D6）。
     *
     * @param template 有效的未组装模板名（如 {@code channel}、{@code channel_video}）
     * @param variant  该模板反查命中的启用主题变体（渲染模型 {@code layoutVariant} 用；未命中 → {@code null}）
     */
    public record EffectiveTemplate(String template, LayoutVariantCatalog.@Nullable Item variant) {
    }

    /**
     * 栏目页有效模板：显式 {@code channel_template_} &gt; 默认 {@code channel}；变体按模板反查。
     * （template-track-variants D1；布局列已随 SSR 轨道整体移除）。
     * 显式选择在当前主题中无模板文件时回退默认并 WARN（Q2）。
     */
    public EffectiveTemplate effectiveChannelTemplate(Site site, Channel channel) {
        String templateName = fallbackIfOrphaned(site, LayoutVariantCatalog.SURFACE_CHANNEL,
                channel.getTemplateName(), Channel.TEMPLATE_PREFIX);
        return new EffectiveTemplate(templateName,
                resolveThemeVariantByTemplate(site, LayoutVariantCatalog.SURFACE_CHANNEL, templateName));
    }

    /**
     * 文章页有效模板：显式选择（文章级 &gt; 栏目级 {@code article_template_}）&gt; 默认 {@code article}；
     * 变体按模板反查（布局列已随 SSR 轨道整体移除）。
     *
     * @param channel 文章所属栏目（控制器单独加载的实例；{@code null} 时按实体自身级联解析）
     */
    public EffectiveTemplate effectiveArticleTemplate(Site site, Article article, @Nullable Channel channel) {
        String templateName;
        if (channel != null) {
            templateName = article.getArticleTemplate();
            if (StringUtils.isBlank(templateName)) {
                templateName = channel.getArticleTemplate();
            }
            if (StringUtils.isBlank(templateName)) {
                templateName = Article.TEMPLATE_PREFIX;
            }
        } else {
            templateName = article.getTemplateName();
        }
        templateName = fallbackIfOrphaned(site, LayoutVariantCatalog.SURFACE_ARTICLE,
                templateName, Article.TEMPLATE_PREFIX);
        return new EffectiveTemplate(templateName,
                resolveThemeVariantByTemplate(site, LayoutVariantCatalog.SURFACE_ARTICLE, templateName));
    }

    /**
     * 专题页有效模板：显式 {@code topic_template_} &gt; 默认 {@code topic}；变体按模板反查。
     * （布局列已随 SSR 轨道整体移除）。
     * 显式选择在当前主题中无模板文件时回退默认并 WARN（Q2）。
     */
    public EffectiveTemplate effectiveTopicTemplate(Site site, Topic topic) {
        String templateName = fallbackIfOrphaned(site, LayoutVariantCatalog.SURFACE_TOPIC,
                topic.getTemplateName(), Topic.TEMPLATE_PREFIX);
        return new EffectiveTemplate(templateName,
                resolveThemeVariantByTemplate(site, LayoutVariantCatalog.SURFACE_TOPIC, templateName));
    }

    /**
     * 孤立显式模板回退（template-track-variants Q2）：显式 {@code *_template_} 选择在当前请求主题
     * （移动端取移动主题，与 {@code Site.assembleTemplate} 一致）的 {@code templates/} 顶层无对应
     * 文件时，回退该 surface 的默认模板并 WARN——主题切换后的失配选择降级为默认页渲染，而非
     * FreeMarker 模板缺失 500（与 SSR 轨道未知值回退 {@code default} 同规）。变体反查随回退后的
     * 模板走，渲染与预览寻址口径一致。无法校验（无 Config 上下文 / 存储不可读 / {@code templates/}
     * 目录缺失）→ 信任显式选择。选择列永不被改写，切回原主题即恢复。
     */
    private String fallbackIfOrphaned(Site site, String surface, String templateName, String defaultTemplate) {
        if (defaultTemplate.equals(templateName)) {
            return templateName;
        }
        Config config = site.getConfig();
        if (config == null) {
            return templateName;
        }
        String themeKey = Contexts.isMobile() ? site.getMobileThemeKey() : site.getThemeKey();
        String themePath = config.getTemplateStorage().getPath() + "/" + themeKey;
        if (templateService.templateExists(themePath, templateName)) {
            return templateName;
        }
        logger.warn("[site:{}] {} 显式模板 {} 在主题 {} 中不存在——回退默认模板 {}（template-track-variants Q2）",
                site.getId(), surface, templateName, themeKey, defaultTemplate);
        return defaultTemplate;
    }

    /**
     * 孤立模板选择计数（{@code ThemeImpactReport.orphanedTemplateSelections}，
     * template-track-variants D7）：站内显式模板选择（栏目 {@code channel_template_}/
     * {@code article_template_}、文章 {@code article_template_}、专题 {@code topic_template_}）中，
     * 不在给定模板名集合（切换后生效主题对的模板文件并集）内的<b>选择数</b>。
     * 某 surface 的集合为空（主题无该前缀模板/扫描失败）→ 该 surface 不计，避免整面误报。
     * 只计数不改写——选择列永不被切换重写，切回主题即恢复。
     */
    public int countOrphanedTemplateSelections(Long siteId, Set<String> channelTemplates,
                                               Set<String> articleTemplates, Set<String> topicTemplates) {
        int total = 0;
        if (!channelTemplates.isEmpty()) {
            total += channelMapper.countChannelTemplateNotIn(siteId, channelTemplates);
        }
        if (!articleTemplates.isEmpty()) {
            total += channelMapper.countArticleTemplateNotIn(siteId, articleTemplates);
            total += articleMapper.countArticleTemplateNotIn(siteId, articleTemplates);
        }
        if (!topicTemplates.isEmpty()) {
            total += topicMapper.countTopicTemplateNotIn(siteId, topicTemplates);
        }
        return total;
    }

    /**
     * 站点的活动主题键（桌面 + 移动，去重；{@code siteId/themeName}）。
     */
    private static List<String> activeThemeKeys(Site site) {
        Set<String> keys = new LinkedHashSet<>(2);
        keys.add(site.getThemeKey());
        keys.add(site.getMobileThemeKey());
        return new ArrayList<>(keys);
    }
}
