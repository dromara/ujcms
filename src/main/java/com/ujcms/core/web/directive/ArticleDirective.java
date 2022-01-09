package com.ujcms.core.web.directive;

import com.ujcms.core.domain.Article;
import com.ujcms.core.service.ArticleService;
import com.ujcms.core.web.support.Directives;
import com.ofwise.util.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;

import java.io.IOException;
import java.util.Map;

/**
 * 文章 标签
 *
 * @author PONY
 */
public class ArticleDirective implements TemplateDirectiveModel {
    private static final String ID = "id";

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        Freemarkers.checkLoopVars(loopVars);
        Freemarkers.checkBody(body);
        Integer id = Directives.getIntegerRequired(params, ID);

        Article article = articleService.select(id);
        loopVars[0] = env.getObjectWrapper().wrap(article);
        body.render(env.getOut());
    }

    private ArticleService articleService;

    public ArticleDirective(ArticleService articleService) {
        this.articleService = articleService;
    }
}
