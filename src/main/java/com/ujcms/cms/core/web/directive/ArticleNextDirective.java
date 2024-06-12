package com.ujcms.cms.core.web.directive;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.commons.freemarker.Freemarkers;
import com.ujcms.commons.function.Function3;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.util.Map;

/**
 * 文章下一篇 标签
 *
 * @author PONY
 */
public class ArticleNextDirective implements TemplateDirectiveModel {
    /**
     * 文章ID
     */
    private static final String ID = "id";
    /**
     * 文章排序值
     */
    private static final String ORDER = "order";
    /**
     * 文章栏目ID
     */
    private static final String CHANNEL_ID = "channelId";
    /**
     * 是否倒序
     */
    public static final String IS_DESC = "isDesc";

    @Nullable
    public static Article query(Map<String, ?> params, Function3<Long, Long, Long, Article> handle) {
        Long id = Directives.getLongRequired(params, ID);
        Long order = Directives.getLongRequired(params, ORDER);
        Long channelId = Directives.getLongRequired(params, CHANNEL_ID);
        return handle.apply(id, order, channelId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);

        boolean isDesc = Directives.getBoolean(params, IS_DESC, true);
        Article article = query(params, isDesc ? articleService::findNext : articleService::findPrev);

        loopVars[0] = env.getObjectWrapper().wrap(article);
        body.render(env.getOut());
    }

    private final ArticleService articleService;

    public ArticleNextDirective(ArticleService articleService) {
        this.articleService = articleService;
    }
}
