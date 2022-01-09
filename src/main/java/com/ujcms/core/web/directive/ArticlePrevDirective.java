package com.ujcms.core.web.directive;

import com.ujcms.core.domain.Article;
import com.ujcms.core.service.ArticleService;
import com.ofwise.util.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;

import java.io.IOException;
import java.util.Map;

import static com.ujcms.core.web.directive.ArticleNextDirective.query;

/**
 * 文章上一篇 标签
 *
 * @author PONY
 */
public class ArticlePrevDirective implements TemplateDirectiveModel {
    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        Freemarkers.checkLoopVars(loopVars);
        Freemarkers.checkBody(body);

        Article article = query(params, articleService::findPrev);

        loopVars[0] = env.getObjectWrapper().wrap(article);
        body.render(env.getOut());
    }

    private ArticleService articleService;

    public ArticlePrevDirective(ArticleService articleService) {
        this.articleService = articleService;
    }
}
