package com.ujcms.cms.core;

import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.BlockItemService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.DictService;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.directive.AnchorDirective;
import com.ujcms.cms.core.web.directive.ArticleDirective;
import com.ujcms.cms.core.web.directive.ArticleListDirective;
import com.ujcms.cms.core.web.directive.ArticleNextDirective;
import com.ujcms.cms.core.web.directive.ArticlePageDirective;
import com.ujcms.cms.core.web.directive.ArticlePrevDirective;
import com.ujcms.cms.core.web.directive.BlockItemListDirective;
import com.ujcms.cms.core.web.directive.ChannelDirective;
import com.ujcms.cms.core.web.directive.ChannelListDirective;
import com.ujcms.cms.core.web.directive.DictListDirective;
import com.ujcms.cms.core.web.directive.EsArticleListDirective;
import com.ujcms.cms.core.web.directive.EsArticlePageDirective;
import com.ujcms.cms.core.web.directive.SiteDirective;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.util.freemarker.AddParamMethod;
import com.ujcms.util.freemarker.FormatMethod;
import com.ujcms.util.freemarker.PagingMethod;
import com.ujcms.util.freemarker.SubstringMethod;
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
    private SiteService siteService;
    private ChannelService channelService;
    private ArticleLucene articleLucene;
    private ArticleService articleService;
    private DictService dictService;
    private BlockItemService blockItemService;
    private freemarker.template.Configuration configuration;

    public ContextConfig( SiteService siteService, ChannelService channelService, ArticleLucene articleLucene,
                         ArticleService articleService, DictService dictService,
                         BlockItemService blockItemService, freemarker.template.Configuration configuration) {
        this.siteService = siteService;
        this.channelService = channelService;
        this.articleLucene = articleLucene;
        this.articleService = articleService;
        this.dictService = dictService;
        this.blockItemService = blockItemService;
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
        configuration.setSharedVariable("Article", new ArticleDirective(articleService));
        configuration.setSharedVariable("ArticlePrev", new ArticlePrevDirective(articleService));
        configuration.setSharedVariable("ArticleNext", new ArticleNextDirective(articleService));
        configuration.setSharedVariable("DictList", new DictListDirective(dictService));
        configuration.setSharedVariable("BlockItemList", new BlockItemListDirective(blockItemService));

        configuration.setSharedVariable("EsArticleList", new EsArticleListDirective(articleLucene));
        configuration.setSharedVariable("EsArticlePage", new EsArticlePageDirective(articleLucene));

        configuration.setSharedVariable("A", new AnchorDirective());
        // 方法
        configuration.setSharedVariable("substring", new SubstringMethod());
        configuration.setSharedVariable("format", new FormatMethod());
        configuration.setSharedVariable("paging", new PagingMethod(Frontends.PAGE));
        configuration.setSharedVariable("addParam", new AddParamMethod(Frontends.PAGE));
    }

    @Bean
    public SiteResolver siteResolver(SiteService siteService, ConfigService configService) {
        return new SiteResolver(siteService, configService);
    }
}
