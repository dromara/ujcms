package com.ujcms.cms.core.web.directive;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.service.ArticleRelatedService;
import com.ujcms.cms.core.service.ArticleRelatedService.RelatedResult;
import com.ujcms.cms.core.service.ArticleService;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code [@ArticleRelatedList]} 指令（docs/spec/article-related/plan.md §6）：参数默认值、
 * 第二个循环变量 manualCount 可选、文章不存在时输出空列表、id 缺失报错
 */
class ArticleRelatedListDirectiveTest {
    private final ArticleService articleService = mock(ArticleService.class);
    private final ArticleRelatedService relatedService = mock(ArticleRelatedService.class);

    private String render(String template) throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);
        cfg.setSharedVariable("ArticleRelatedList", new ArticleRelatedListDirective(articleService, relatedService));
        StringTemplateLoader loader = new StringTemplateLoader();
        loader.putTemplate("t", template);
        cfg.setTemplateLoader(loader);
        Template fm = cfg.getTemplate("t");
        StringWriter out = new StringWriter();
        fm.process(new HashMap<String, Object>(), out);
        return out.toString();
    }

    private static Article article(long id) {
        Article a = new Article();
        a.setId(id);
        a.setTitle("T" + id);
        return a;
    }

    @Test
    void rendersManualCountAndDefaults() throws Exception {
        Article seed = article(1L);
        when(articleService.select(1L)).thenReturn(seed);
        when(relatedService.listForRender(eq(seed), eq(5), eq(true), isNull()))
                .thenReturn(new RelatedResult(List.of(article(2L), article(3L)), 1));
        String out = render("<@ArticleRelatedList id='1'; list, manualCount>"
                + "<#list list as a>${a.title}<#if a_index lt manualCount>*</#if>,</#list>"
                + "</@ArticleRelatedList>");
        assertEquals("T2*,T3,", out);
    }

    @Test
    void passesLimitFillAndChannel() throws Exception {
        Article seed = article(1L);
        when(articleService.select(1L)).thenReturn(seed);
        when(relatedService.listForRender(eq(seed), eq(3), eq(false), eq(9L)))
                .thenReturn(new RelatedResult(List.of(article(2L)), 1));
        String out = render("<@ArticleRelatedList id='1' limit='3' isFill='false' channelId='9'; list>"
                + "<#list list as a>${a.title}</#list></@ArticleRelatedList>");
        assertEquals("T2", out);
        verify(relatedService).listForRender(seed, 3, false, 9L);
    }

    @Test
    void unknownArticleRendersEmpty() throws Exception {
        when(articleService.select(7L)).thenReturn(null);
        String out = render("<@ArticleRelatedList id='7'; list>[${list?size}]</@ArticleRelatedList>");
        assertEquals("[0]", out);
    }

    @Test
    void idIsRequired() {
        // Directives.getLongRequired 的既有约定：缺参抛 IllegalArgumentException（不包成 TemplateException）
        assertThrows(IllegalArgumentException.class, () -> render("<@ArticleRelatedList; list>x</@ArticleRelatedList>"));
    }
}
