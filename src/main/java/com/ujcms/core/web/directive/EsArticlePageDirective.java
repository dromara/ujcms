package com.ujcms.core.web.directive;

import com.ujcms.core.lucene.ArticleLucene;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;

import java.io.IOException;
import java.util.Map;

/**
 * 文章分页(全文检索) 标签
 *
 * @author PONY
 */
public class EsArticlePageDirective extends EsArticleListDirective {
    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        doExecute(env, params, loopVars, body, true);
    }

    public EsArticlePageDirective(ArticleLucene articleLucene) {
        super(articleLucene);
    }
}
