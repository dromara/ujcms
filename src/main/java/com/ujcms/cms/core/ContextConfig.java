package com.ujcms.cms.core;

import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.service.*;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.directive.*;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.util.freemarker.*;
import no.api.freemarker.java8.Java8ObjectWrapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    private final TagService tagService;
    private final DictService dictService;
    private final BlockItemService blockItemService;
    private final MessageBoardService messageBoardService;
    private final freemarker.template.Configuration configuration;

    public ContextConfig(SiteService siteService, ChannelService channelService, ArticleLucene articleLucene,
                         ArticleService articleService, TagService tagService, DictService dictService,
                         BlockItemService blockItemService, MessageBoardService messageBoardService,
                         freemarker.template.Configuration configuration) {
        this.siteService = siteService;
        this.channelService = channelService;
        this.articleLucene = articleLucene;
        this.articleService = articleService;
        this.tagService = tagService;
        this.dictService = dictService;
        this.blockItemService = blockItemService;
        this.messageBoardService = messageBoardService;
        this.configuration = configuration;
    }

    @Override
    public void afterPropertiesSet() {
        // 支持 java.time API
        configuration.setObjectWrapper(new Java8ObjectWrapper(freemarker.template.Configuration.VERSION_2_3_31));
        // 标签
        configuration.setSharedVariable("Site", new SiteDirective(siteService));
        configuration.setSharedVariable("ChannelList", new ChannelListDirective(channelService));
        configuration.setSharedVariable("Channel", new ChannelDirective(channelService));
        configuration.setSharedVariable("ArticleList", new ArticleListDirective(articleService, channelService));
        configuration.setSharedVariable("ArticlePage", new ArticlePageDirective(articleService, channelService));
        configuration.setSharedVariable("Article", new ArticleDirective(articleService, channelService));
        configuration.setSharedVariable("ArticlePrev", new ArticlePrevDirective(articleService));
        configuration.setSharedVariable("ArticleNext", new ArticleNextDirective(articleService));
        configuration.setSharedVariable("DictList", new DictListDirective(dictService));
        configuration.setSharedVariable("BlockItemList", new BlockItemListDirective(blockItemService));
        configuration.setSharedVariable("MessageBoardList", new MessageBoardListDirective(messageBoardService));
        configuration.setSharedVariable("MessageBoardPage", new MessageBoardPageDirective(messageBoardService));
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
}
