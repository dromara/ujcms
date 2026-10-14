package com.ujcms.cms.core.web.directive;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * 文章列表标签的状态条件（docs/spec/article-management/plan.md Phase 2.5）：不给状态、给了空值都是“已发布”。
 * 空值以前被当成“不限状态”——模板里一个空变量，草稿、回收站里的文章就都列出来了。
 */
class ArticleListDirectiveStatusTest {
    private static Collection<?> status(Map<String, String> params) {
        ArticleArgs args = ArticleArgs.of();
        ArticleListDirective.assemble(args, params, 1L, mock(ChannelService.class));
        return (Collection<?>) args.getQueryMap().get("In_status_Short");
    }

    @Test
    void anAbsentOrEmptyStatusMeansPublished() {
        assertEquals(List.of(Article.STATUS_PUBLISHED), List.copyOf(status(new HashMap<>())));
        Map<String, String> empty = new HashMap<>();
        empty.put("status", "");
        assertEquals(List.of(Article.STATUS_PUBLISHED), List.copyOf(status(empty)));
    }

    @Test
    void anExplicitStatusIsKept() {
        Map<String, String> params = new HashMap<>();
        params.put("status", "0,1");
        assertEquals(List.of(Article.STATUS_PUBLISHED, Article.STATUS_ARCHIVED), List.copyOf(status(params)));
    }
}
