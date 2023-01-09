package com.ujcms.cms.core.web.directive;

import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.lucene.domain.EsArticle;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.util.freemarker.Freemarkers;
import com.ujcms.util.query.OffsetLimitRequest;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;

import static com.ujcms.cms.core.web.directive.ArticleListDirective.*;
import static com.ujcms.cms.core.web.support.Directives.*;

/**
 * 文章列表(全文检索) 标签
 *
 * @author PONY
 */
public class EsArticleListDirective implements TemplateDirectiveModel {
    public static final String Q = "q";
    /**
     * 是否每个分词都要匹配。默认：false
     */
    public static final String IS_OPERATOR_AND = "isOperatorAnd";
    public static final String IS_INCLUDE_BODY = "isIncludeBody";
    public static final String K1 = "k1";
    public static final String K2 = "k2";
    public static final String K3 = "k3";
    public static final String K4 = "k4";
    public static final String K5 = "k5";
    public static final String K6 = "k6";
    public static final String K7 = "k7";
    public static final String K8 = "k8";
    public static final String S1 = "s1";
    public static final String S2 = "s2";
    public static final String S3 = "s3";
    public static final String S4 = "s4";
    public static final String S5 = "s5";
    public static final String S6 = "s6";
    public static final String B1 = "b1";
    public static final String B2 = "b2";
    public static final String B3 = "b3";
    public static final String B4 = "b4";

    public static Page<EsArticle> query(Map<String, ?> params, Integer defaultSiteId,
                                        Pageable pageable, ArticleLucene articleLucene) {
        Integer siteId = getInteger(params, SITE_ID);
        // 不获取所有站点，则给默认站点ID
        if (siteId == null && !getBoolean(params, IS_ALL_SITE, false)) {
            siteId = defaultSiteId;
        }
        Boolean isIncludeSubSite = getBoolean(params, IS_INCLUDE_SUB_SITE, false);

        Integer channelId = getInteger(params, CHANNEL_ID);
        Boolean isIncludeSubChannel = getBoolean(params, IS_INCLUDE_SUB_CHANNEL, true);

        String q = getString(params, Q);
        Boolean isIncludeBody = getBoolean(params, IS_INCLUDE_BODY, true);
        Boolean isOperatorAnd = getBoolean(params, IS_OPERATOR_AND, false);
        OffsetDateTime beginPublishDate = getOffsetDateTime(params, BEGIN_PUBLISH_DATE);
        OffsetDateTime endPublishDate = getOffsetDateTime(params, END_PUBLISH_DATE);
        Boolean isWithImage = getBoolean(params, IS_WITH_IMAGE);
        Collection<Integer> excludeIds = getIntegers(params, EXCLUDE_ID);

        return articleLucene.findAll(q, isIncludeBody, isOperatorAnd,
                siteId, isIncludeSubSite, channelId, isIncludeSubChannel,
                beginPublishDate, endPublishDate, isWithImage, excludeIds,
                getString(params, K1), getString(params, K2),
                getString(params, K3), getString(params, K4),
                getString(params, K5), getString(params, K6),
                getString(params, K7), getString(params, K8),
                getString(params, S1), getString(params, S2),
                getString(params, S3), getString(params, S4),
                getString(params, S5), getString(params, S6),
                getBoolean(params, B1), getBoolean(params, B2),
                getBoolean(params, B3), getBoolean(params, B4),
                null, pageable);
    }

    protected void doExecute(Environment env, Map<String, TemplateModel> params, TemplateModel[] loopVars,
                             TemplateDirectiveBody body, boolean isPage) throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);

        Integer defaultSiteId = Frontends.getSiteId(env);

        if (isPage) {
            // spring-data 的 page 从 0 开始
            int page = Directives.getPage(params, env) - 1;
            int pageSize = Directives.getPageSize(params, env);
            Page<EsArticle> pagedList = query(params, defaultSiteId, PageRequest.of(page, pageSize), articleLucene);
            Directives.setTotalPages(pagedList.getTotalPages());
            loopVars[0] = env.getObjectWrapper().wrap(pagedList);
        } else {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            Page<EsArticle> pagedList = query(params, defaultSiteId, OffsetLimitRequest.of(offset, limit), articleLucene);
            loopVars[0] = env.getObjectWrapper().wrap(pagedList.getContent());
        }
        body.render(env.getOut());
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        doExecute(env, params, loopVars, body, false);
    }

    private final ArticleLucene articleLucene;

    public EsArticleListDirective(ArticleLucene articleLucene) {
        this.articleLucene = articleLucene;
    }

}
