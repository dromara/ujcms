package com.ujcms.cms.core;

import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.service.*;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.web.directive.*;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.commons.freemarker.*;
import com.ujcms.commons.ip.IpSeeker;
import no.api.freemarker.java8.Java8ObjectWrapper;
import org.apache.commons.io.IOUtils;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    private final SiteService siteService;
    private final ChannelService channelService;
    private final ArticleLucene articleLucene;
    private final ArticleService articleService;
    private final ModelService modelService;
    private final TagService tagService;
    private final DictService dictService;
    private final BlockItemService blockItemService;
    private final freemarker.template.Configuration configuration;
    private final Props props;
    private final ResourceLoader resourceLoader;

    public ContextConfig(SiteService siteService, ChannelService channelService, ArticleLucene articleLucene,
                         ArticleService articleService, ModelService modelService, TagService tagService,
                         DictService dictService, BlockItemService blockItemService,
                         freemarker.template.Configuration configuration, Props props, ResourceLoader resourceLoader) {
        this.siteService = siteService;
        this.channelService = channelService;
        this.articleLucene = articleLucene;
        this.articleService = articleService;
        this.modelService = modelService;
        this.tagService = tagService;
        this.dictService = dictService;
        this.blockItemService = blockItemService;
        this.configuration = configuration;
        this.props = props;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void afterPropertiesSet() {
        // 支持 java.time API
        configuration.setObjectWrapper(new Java8ObjectWrapper(freemarker.template.Configuration.VERSION_2_3_31));
        // 标签
        configuration.setSharedVariable("SiteList", new SiteListDirective(siteService));
        configuration.setSharedVariable("Site", new SiteDirective(siteService));
        configuration.setSharedVariable("ChannelList", new ChannelListDirective(channelService));
        configuration.setSharedVariable("Channel", new ChannelDirective(channelService));
        configuration.setSharedVariable("ArticleList", new ArticleListDirective(articleService, channelService));
        configuration.setSharedVariable("ArticlePage", new ArticlePageDirective(articleService, channelService));
        configuration.setSharedVariable("Article", new ArticleDirective(articleService));
        configuration.setSharedVariable("ArticlePrev", new ArticlePrevDirective(articleService));
        configuration.setSharedVariable("ArticleNext", new ArticleNextDirective(articleService));
        configuration.setSharedVariable("ModelList", new ModelDirective(modelService));
        configuration.setSharedVariable("Model", new ArticleNextDirective(articleService));
        configuration.setSharedVariable("DictList", new DictListDirective(dictService));
        configuration.setSharedVariable("BlockItemList", new BlockItemListDirective(blockItemService));
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
    public SiteResolver siteResolver(SiteService siteService, ConfigService configService) {
        return new SiteResolver(siteService, configService);
    }

    @Bean
    public IpSeeker ipSeeker() {
        try (InputStream is = resourceLoader.getResource(props.getIp2regionPath()).getInputStream()) {
            byte[] buff = IOUtils.toByteArray(is);
            return new IpSeeker(buff);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
