package com.ujcms.cms.core;

import com.ujcms.cms.core.component.LangAlternateService;
import com.ujcms.cms.core.composition.RegionTemplateSupport;
import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.security.TemplateMemberAccessPolicy;
import com.ujcms.cms.core.service.*;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.TemplateErrorHandler;
import com.ujcms.cms.core.web.directive.*;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.common.freemarker.*;
import com.ujcms.common.ip.IpSeeker;
import freemarker.core.TemplateClassResolver;
import no.api.freemarker.java8.Java8ObjectWrapper;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;

/**
 * ContextConfig
 *
 * @author PONY
 */
@Configuration
@MapperScan("com.ujcms.cms.core.mapper")
public class ContextConfig implements InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(ContextConfig.class);

    private final SiteService siteService;
    private final ChannelService channelService;
    private final ArticleLucene articleLucene;
    private final ArticleService articleService;
    private final ArticleRelatedService articleRelatedService;
    private final ModelService modelService;
    private final TagService tagService;
    private final DictService dictService;
    private final BlockItemService blockItemService;
    private final BlockService blockService;
    private final RegionTemplateSupport regionTemplateSupport;
    private final LangAlternateService langAlternateService;
    private final freemarker.template.Configuration configuration;
    private final Props props;
    private final ResourceLoader resourceLoader;
    private final TemplateErrorHandler templateErrorHandler;

    public ContextConfig(SiteService siteService, ChannelService channelService,
                         ArticleLucene articleLucene, ArticleService articleService,
                         ArticleRelatedService articleRelatedService, ModelService modelService,
                         TagService tagService, DictService dictService, BlockItemService blockItemService,
                         BlockService blockService, RegionTemplateSupport regionTemplateSupport,
                         LangAlternateService langAlternateService,
                         freemarker.template.Configuration configuration, Props props,
                         ResourceLoader resourceLoader, TemplateErrorHandler templateErrorHandler) {
        this.siteService = siteService;
        this.channelService = channelService;
        this.articleLucene = articleLucene;
        this.articleService = articleService;
        this.articleRelatedService = articleRelatedService;
        this.modelService = modelService;
        this.tagService = tagService;
        this.dictService = dictService;
        this.blockItemService = blockItemService;
        this.blockService = blockService;
        this.regionTemplateSupport = regionTemplateSupport;
        this.langAlternateService = langAlternateService;
        this.configuration = configuration;
        this.props = props;
        this.resourceLoader = resourceLoader;
        this.templateErrorHandler = templateErrorHandler;
    }

    @Override
    public void afterPropertiesSet() {
        // 支持 java.time API
        Java8ObjectWrapper objectWrapper =
                new Java8ObjectWrapper(freemarker.template.Configuration.VERSION_2_3_31);
        // 收敛模板可达面（design-review-2026-07 T5）：模板拿到的是领域实体本身，BeansWrapper 会暴露每个
        // public getter，而 @JsonIgnore 只管 Jackson。存储/短信/邮件凭据与用户口令散列、PII 盲索引由
        // Sm4StringTypeHandler 解密后原文挂在实体上，此处从对象包装器摘除。必须在交给 Configuration 前设置。
        // 叠加 SecureMemberAccessPolicy：禁止模板访问 ServletContext、Spring 容器、ClassLoader、
        // InstanceManager 等能力型类型，防止后台模板编辑（SSTI）经实时引用提权为远程命令执行（RCE）
        objectWrapper.setMemberAccessPolicy(new TemplateMemberAccessPolicy(
                new SecureMemberAccessPolicy(freemarker.template.Configuration.VERSION_2_3_31)));
        configuration.setObjectWrapper(objectWrapper);
        // 禁止模板通过 ?new 创建类实例，防止远程代码执行（RCE）。主题可通过 ZIP 安装、可在线编辑，
        // 沙箱不能只依赖 yaml 配置（new_builtin_class_resolver），此处用代码固定，yaml 仅作后备说明
        configuration.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
        // 模板错误处理由代码固定、按查看者身份决定呈现（TemplateErrorHandler，docs/spec/template-error-disclosure D1），
        // 不再随 profile 在 rethrow 与 SimpleTemplateExceptionHandler 之间切换；yaml 里的
        // template_exception_handler / log_template_exceptions 不再配置。FreeMarker 自带的整段堆栈日志关闭：
        // 记录器已记一条带错误编号的日志，重新抛出的错误 Tomcat 还会记一次完整堆栈
        configuration.setTemplateExceptionHandler(templateErrorHandler);
        configuration.setLogTemplateExceptions(false);
        // 标签
        configuration.setSharedVariable("SiteList", new SiteListDirective(siteService));
        configuration.setSharedVariable("Site", new SiteDirective(siteService));
        // 语言备选（multilingual P3）：切换器与 hreflang 块。指令读取数据模型的 article/channel/isHome，
        // 动态渲染与静态页生成两条管线下行为一致
        configuration.setSharedVariable("LangSiteList", new LangSiteListDirective(siteService, langAlternateService));
        configuration.setSharedVariable("ChannelList", new ChannelListDirective(channelService));
        configuration.setSharedVariable("Channel", new ChannelDirective(channelService));
        // 专题标签（TopicList/TopicPage/Topic/ArticleTopicList）2026-09-15 起归属商业版 T2，由 ContextT2Config 注册
        configuration.setSharedVariable("ArticleList", new ArticleListDirective(articleService, channelService));
        configuration.setSharedVariable("ArticlePage", new ArticlePageDirective(articleService, channelService));
        configuration.setSharedVariable("Article", new ArticleDirective(articleService));
        configuration.setSharedVariable("ArticlePrev", new ArticlePrevDirective(articleService));
        configuration.setSharedVariable("ArticleNext", new ArticleNextDirective(articleService));
        // 相关文章：手选优先、自动补齐（docs/spec/article-related/plan.md §6）
        configuration.setSharedVariable("ArticleRelatedList", new ArticleRelatedListDirective(articleService, articleRelatedService));
        configuration.setSharedVariable("ModelList", new ModelListDirective(modelService));
        configuration.setSharedVariable("Model", new ModelDirective(modelService));
        configuration.setSharedVariable("DictList", new DictListDirective(dictService));
        configuration.setSharedVariable("BlockItemList", new BlockItemListDirective(blockItemService));
        configuration.setSharedVariable("BlockList", new BlockListDirective(blockService));
        // 插槽（页面构成）：与 BlockItemList 并存——同一页面可同时使用两层机制（§15.2）
        // 区域（页面区域，模板轨道渲染器，page-regions §7.4）：自身铺出行→列→面板栅格，面板分派给 snippets/panels/<Shape>
        configuration.setSharedVariable("region", new RegionDirective(regionTemplateSupport));
        configuration.setSharedVariable("TagList", new TagListDirective(tagService));
        configuration.setSharedVariable("TagPage", new TagPageDirective(tagService));

        configuration.setSharedVariable("EsArticleList", new EsArticleListDirective(articleLucene));
        configuration.setSharedVariable("EsArticlePage", new EsArticlePageDirective(articleLucene));

        configuration.setSharedVariable("A", new AnchorDirective());
        // 方法
        configuration.setSharedVariable("substring", new SubstringMethod());
        configuration.setSharedVariable("bbcode", new BbCodeMethod());
        configuration.setSharedVariable("format", new FormatMethod());
        configuration.setSharedVariable("paging", new PagingMethod(Frontends.PAGE));
        configuration.setSharedVariable("addParam", new AddParamMethod(Frontends.PAGE));
    }


    @Bean
    public SiteResolver siteResolver(SiteService siteService, ConfigService configService,
                                     ThemeInstallService themeInstallService) {
        return new SiteResolver(siteService, configService, themeInstallService);
    }

    /**
     * IP 归属地查找。IPv4 地址库必需（缺失或非法则启动失败）；IPv6 地址库可选（{@code ujcms.ip2region-v6-path}），
     * 不存在时仅记录一条 INFO，不可用时由 {@link IpSeeker} 记录警告并退化为仅 IPv4——地址库问题不应阻止系统启动
     */
    @Bean
    public IpSeeker ipSeeker() {
        Resource v4 = resourceLoader.getResource(props.getIp2regionPath());
        Resource v6 = resourceLoader.getResource(props.getIp2regionV6Path());
        try (InputStream v4Input = v4.getInputStream()) {
            if (!v6.isReadable()) {
                logger.info("IPv6 ip2region database not found ({}), IPv6 lookups return UNKNOWN",
                        props.getIp2regionV6Path());
                return new IpSeeker(v4Input, null);
            }
            try (InputStream v6Input = v6.getInputStream()) {
                return new IpSeeker(v4Input, v6Input);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
