package com.ujcms.core.web.directive;

import com.ofwise.util.freemarker.Freemarkers;
import com.ofwise.util.query.OffsetLimitRequest;
import com.ujcms.core.lucene.ArticleLucene;
import com.ujcms.core.lucene.domain.EsArticle;
import com.ujcms.core.support.Frontends;
import com.ujcms.core.web.support.Directives;
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

import static com.ujcms.core.web.directive.ArticleListDirective.*;

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
        Integer siteId = Directives.getInteger(params, SITE_ID);
        // 不获取所有站点，则给默认站点ID
        if (siteId == null && !Directives.getBoolean(params, IS_ALL_SITE, false)) {
            siteId = defaultSiteId;
        }
        Boolean isIncludeSubSite = Directives.getBoolean(params, IS_INCLUDE_SUB_SITE, false);

        Integer channelId = Directives.getInteger(params, CHANNEL_ID);
        Boolean isIncludeSubChannel = Directives.getBoolean(params, IS_INCLUDE_SUB_CHANNEL, true);

        String q = Directives.getString(params, Q);
        Boolean isIncludeBody = Directives.getBoolean(params, IS_INCLUDE_BODY, true);
        boolean isOperatorAnd = Directives.getBoolean(params, IS_OPERATOR_AND, false);
        OffsetDateTime beginPublishDate = Directives.getOffsetDateTime(params, BEGIN_PUBLISH_DATE);
        OffsetDateTime endPublishDate = Directives.getOffsetDateTime(params, END_PUBLISH_DATE);
        Boolean isWithImage = Directives.getBoolean(params, IS_WITH_IMAGE);
        Collection<Integer> excludeIds = Directives.getIntegers(params, EXCLUDE_ID);

        return articleLucene.findAll(q, isIncludeBody, isOperatorAnd,
                siteId, isIncludeSubSite, channelId, isIncludeSubChannel,
                beginPublishDate, endPublishDate, isWithImage, excludeIds,
                Directives.getString(params, K1), Directives.getString(params, K2),
                Directives.getString(params, K3), Directives.getString(params, K4),
                Directives.getString(params, K5), Directives.getString(params, K6),
                Directives.getString(params, K7), Directives.getString(params, K8),
                Directives.getString(params, S1), Directives.getString(params, S2),
                Directives.getString(params, S3), Directives.getString(params, S4),
                Directives.getString(params, S5), Directives.getString(params, S6),
                Directives.getBoolean(params, B1), Directives.getBoolean(params, B2),
                Directives.getBoolean(params, B3), Directives.getBoolean(params, B4),
                null, pageable);
    }

    protected void doExecute(Environment env, Map<String, TemplateModel> params, TemplateModel[] loopVars,
                             TemplateDirectiveBody body, boolean isPage) throws TemplateException, IOException {
        Freemarkers.checkLoopVars(loopVars);
        Freemarkers.checkBody(body);

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

    private ArticleLucene articleLucene;

    public EsArticleListDirective(ArticleLucene articleLucene) {
        this.articleLucene = articleLucene;
    }

}
