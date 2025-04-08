package com.ujcms.cms.core.web.directive;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.lucene.ArticleLuceneArgs;
import com.ujcms.cms.core.lucene.domain.EsArticle;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.commons.freemarker.Freemarkers;
import com.ujcms.commons.query.OffsetLimitRequest;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
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
    public static final String IS_INCLUDE_DISABLED = "isIncludeDisabled";
    public static final String FRAGMENT_SIZE = "fragmentSize";
    public static final String DATE_EXP_SCALE = "dateExpScale";
    public static final String DATE_EXP_OFFSET = "dateExpOffset";
    public static final String DATE_EXP_DECAY = "dateExpDecay";
    public static final String DATE_EXP_BOOST = "dateExpBoost";
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

    public static Page<EsArticle> query(Map<String, ?> params, Long defaultSiteId,
                                        Pageable pageable, ArticleLucene articleLucene) {
        Long siteId = getLong(params, SITE_ID);
        // 不获取所有站点，则给默认站点ID
        if (siteId == null && !getBoolean(params, IS_ALL_SITE, false)) {
            siteId = defaultSiteId;
        }
        Boolean isIncludeSubSite = getBoolean(params, IS_INCLUDE_SUB_SITE, false);

        Collection<Long> channelIds = getLongs(params, CHANNEL_ID);
        Boolean isIncludeSubChannel = getBoolean(params, IS_INCLUDE_SUB_CHANNEL, true);

        String q = getString(params, Q);
        Integer fragmentSize = getInteger(params, FRAGMENT_SIZE, 100);
        Boolean isIncludeBody = getBoolean(params, IS_INCLUDE_BODY, true);
        Boolean isOperatorAnd = getBoolean(params, IS_OPERATOR_AND, false);
        OffsetDateTime beginPublishDate = getOffsetDateTime(params, BEGIN_PUBLISH_DATE);
        OffsetDateTime endPublishDate = getOffsetDateTime(params, END_PUBLISH_DATE);
        Boolean isWithImage = getBoolean(params, IS_WITH_IMAGE);
        Collection<Long> excludeIds = getLongs(params, EXCLUDE_ID);
        Collection<Integer> status = getIntegers(params, STATUS);
        if (status == null) {
            status = Collections.singletonList((int) Article.STATUS_PUBLISHED);
        }

        Integer dateExpScale = getInteger(params, DATE_EXP_SCALE, 0);
        Integer dateExpOffset = getInteger(params, DATE_EXP_OFFSET, 0);
        Double dateExpDecay = getDouble(params, DATE_EXP_DECAY, 0.5d);
        Float dateExpBoost = getFloat(params, DATE_EXP_BOOST, 3f);

        ArticleLuceneArgs args = new ArticleLuceneArgs();
        args.setTitle(q);
        if (Boolean.TRUE.equals(isIncludeBody)) {
            args.setBody(q);
        }
        if (Boolean.FALSE.equals(getBoolean(params, IS_INCLUDE_DISABLED, false))) {
            args.setEnabled(true);
        }
        args.setOperatorAnd(Boolean.TRUE.equals(isOperatorAnd));
        args.setFragmentSize(fragmentSize);
        args.setSiteId(siteId);
        args.setIncludeSubSite(Boolean.TRUE.equals(isIncludeSubSite));
        args.setChannelIds(channelIds);
        args.setIncludeSubChannel(Boolean.TRUE.equals(isIncludeSubChannel));
        args.setBeginPublishDate(beginPublishDate);
        args.setEndPublishDate(endPublishDate);
        args.setWithImage(isWithImage);
        args.setExcludeIds(excludeIds);
        args.setStatus(status);
        args.setDateExpScale(dateExpScale);
        args.setDateExpOffset(dateExpOffset);
        args.setDateExpDecay(dateExpDecay);
        args.setDateExpBoost(dateExpBoost);
        args.setK1(getString(params, K1));
        args.setK2(getString(params, K2));
        args.setK3(getString(params, K3));
        args.setK4(getString(params, K4));
        args.setK5(getString(params, K5));
        args.setK6(getString(params, K6));
        args.setK7(getString(params, K7));
        args.setK8(getString(params, K8));
        args.setS1(getString(params, S1));
        args.setS2(getString(params, S2));
        args.setS3(getString(params, S3));
        args.setS4(getString(params, S4));
        args.setS5(getString(params, S5));
        args.setS6(getString(params, S6));
        args.setB1(getBoolean(params, B1));
        args.setB2(getBoolean(params, B2));
        args.setB3(getBoolean(params, B3));
        args.setB4(getBoolean(params, B4));
        return articleLucene.findAll(args, null, pageable);
    }

    protected void doExecute(Environment env, Map<String, TemplateModel> params, TemplateModel[] loopVars,
                             TemplateDirectiveBody body, boolean isPage) throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);

        Long defaultSiteId = Frontends.getSiteId(env);
        Sort sort = Directives.getSort(params);
        if (isPage) {
            // spring-data 的 page 从 0 开始
            int page = Directives.getPage(params, env) - 1;
            int pageSize = Directives.getPageSize(params, env);
            Pageable pageable = PageRequest.of(page, pageSize, sort);
            Page<EsArticle> pagedList = query(params, defaultSiteId, pageable, articleLucene);
            Directives.setTotalPages(pagedList.getTotalPages());
            loopVars[0] = env.getObjectWrapper().wrap(pagedList);
        } else {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            Pageable pageable = OffsetLimitRequest.of(offset, limit, sort);
            Page<EsArticle> pagedList = query(params, defaultSiteId, pageable, articleLucene);
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
