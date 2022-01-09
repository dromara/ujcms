package com.ujcms.core.web.directive;

import com.ujcms.core.domain.Article;
import com.ujcms.core.service.ArticleService;
import com.ujcms.core.web.support.Directives;
import com.ofwise.util.freemarker.Freemarkers;
import com.ofwise.util.function.Function3;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 文章下一篇 标签
 *
 * @author PONY
 */
public class ArticleNextDirective implements TemplateDirectiveModel {
    private static final String ID = "id";
    private static final String PUBLISH_DATE = "publishDate";
    private static final String CHANNEL_ID = "channelId";

    @Nullable
    public static Article query(Map<String, ?> params, Function3<Integer, OffsetDateTime, Integer, Article> handle) {
        Integer id = Directives.getIntegerRequired(params, ID);
        OffsetDateTime publishDate = Directives.getOffsetDateTimeRequired(params, PUBLISH_DATE);
        Integer channelId = Directives.getIntegerRequired(params, CHANNEL_ID);
        return handle.apply(id, publishDate, channelId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        Freemarkers.checkLoopVars(loopVars);
        Freemarkers.checkBody(body);

        Article article = query(params, articleService::findNext);

        loopVars[0] = env.getObjectWrapper().wrap(article);
        body.render(env.getOut());
    }

    private ArticleService articleService;

    public ArticleNextDirective(ArticleService articleService) {
        this.articleService = articleService;
    }
}
