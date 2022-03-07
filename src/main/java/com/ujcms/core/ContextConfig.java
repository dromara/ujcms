package com.ujcms.core;

import com.ofwise.util.freemarker.AddParamMethod;
import com.ofwise.util.freemarker.FormatMethod;
import com.ofwise.util.freemarker.PagingMethod;
import com.ofwise.util.freemarker.SubstringMethod;
import com.ujcms.core.lucene.ArticleLucene;
import com.ujcms.core.service.ArticleService;
import com.ujcms.core.service.BlockItemService;
import com.ujcms.core.service.ChannelService;
import com.ujcms.core.service.DictService;
import com.ujcms.core.service.GlobalService;
import com.ujcms.core.service.SiteQueryService;
import com.ujcms.core.support.Frontends;
import com.ujcms.core.web.directive.AnchorDirective;
import com.ujcms.core.web.directive.ArticleDirective;
import com.ujcms.core.web.directive.ArticleListDirective;
import com.ujcms.core.web.directive.ArticleNextDirective;
import com.ujcms.core.web.directive.ArticlePageDirective;
import com.ujcms.core.web.directive.ArticlePrevDirective;
import com.ujcms.core.web.directive.BlockItemListDirective;
import com.ujcms.core.web.directive.ChannelDirective;
import com.ujcms.core.web.directive.ChannelListDirective;
import com.ujcms.core.web.directive.DictListDirective;
import com.ujcms.core.web.directive.EsArticleListDirective;
import com.ujcms.core.web.directive.EsArticlePageDirective;
import com.ujcms.core.web.support.SiteResolver;
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
@MapperScan("com.ujcms.core.mapper")
public class ContextConfig implements InitializingBean {
    private ArticleLucene articleLucene;
    private ArticleService articleService;
    private ChannelService channelService;
    private DictService dictService;
    private BlockItemService blockItemService;
    private freemarker.template.Configuration configuration;

    public ContextConfig(ArticleLucene articleLucene,
                         ArticleService articleService, ChannelService channelService, DictService dictService,
                         BlockItemService blockItemService, freemarker.template.Configuration configuration) {
        this.articleLucene = articleLucene;
        this.articleService = articleService;
        this.channelService = channelService;
        this.dictService = dictService;
        this.blockItemService = blockItemService;
        this.configuration = configuration;
    }

    @Override
    public void afterPropertiesSet() {
        // 标签
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
    public SiteResolver siteResolver(SiteQueryService siteService, GlobalService globalService) {
        return new SiteResolver(siteService, globalService);
    }
}
