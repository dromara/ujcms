package com.ujcms.cms.core.web.directive;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.service.ArticleRelatedService;
import com.ujcms.cms.core.service.ArticleRelatedService.RelatedResult;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.common.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static com.ujcms.cms.core.web.support.Directives.*;

/**
 * 相关文章列表 标签（docs/spec/article-related/plan.md §6）。手选相关文章优先，不足 {@code limit} 时自动补齐
 * （标题相似 / 共同标签 / 同栏目）；无手选数据时输出与过去 {@code [@EsArticleList q=article.title]} 同类的自动结果。
 *
 * <pre>
 * [@ArticleRelatedList id=article.id limit='5'; list, manualCount]
 *   [#list list as bean]
 *   &lt;a href="${bean.url}"&gt;${bean.title}&lt;/a&gt;[#if bean_index lt manualCount] 编辑推荐[/#if]
 *   [/#list]
 * [/@ArticleRelatedList]
 * </pre>
 *
 * 参数：{@code id} 文章ID（必填）；{@code limit} 条数（默认 5）；{@code isFill} 是否自动补齐（默认 true）；
 * {@code channelId} 补齐时限定栏目（含子栏目）。第二个循环变量 {@code manualCount} 可省略。
 *
 * @author PONY
 */
public class ArticleRelatedListDirective implements TemplateDirectiveModel {
    public static final String ID = "id";
    public static final String IS_FILL = "isFill";
    public static final String CHANNEL_ID = "channelId";
    public static final int DEFAULT_LIMIT = 5;

    private final ArticleService articleService;
    private final ArticleRelatedService relatedService;

    public ArticleRelatedListDirective(ArticleService articleService, ArticleRelatedService relatedService) {
        this.articleService = articleService;
        this.relatedService = relatedService;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);

        Long id = getLongRequired(params, ID);
        int limit = getInteger(params, LIMIT, DEFAULT_LIMIT);
        boolean fill = Boolean.TRUE.equals(getBoolean(params, IS_FILL, true));
        Long channelId = getLong(params, CHANNEL_ID);

        Article article = articleService.select(id);
        RelatedResult result = article == null
                ? new RelatedResult(Collections.emptyList(), 0)
                : relatedService.listForRender(article, limit, fill, channelId);
        loopVars[0] = env.getObjectWrapper().wrap(result.list());
        if (loopVars.length > 1) {
            loopVars[1] = env.getObjectWrapper().wrap(result.manualCount());
        }
        body.render(env.getOut());
    }
}
