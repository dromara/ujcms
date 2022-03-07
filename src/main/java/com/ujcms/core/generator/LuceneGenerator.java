package com.ujcms.core.generator;

import com.ujcms.core.domain.Task;
import com.ujcms.core.lucene.ArticleLucene;
import com.ujcms.core.lucene.domain.EsArticle;
import com.ujcms.core.service.ArticleService;
import com.ujcms.core.service.TaskService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Lucene 生成器
 *
 * @author PONY
 */
@Component
public class LuceneGenerator extends AbstractGenerator {
    private ArticleLucene articleLucene;

    public LuceneGenerator(ArticleLucene articleLucene, ArticleService articleService,
                           TaskService taskService, @Qualifier("generator") ThreadPoolTaskExecutor executor) {
        super(articleService, taskService, executor);
        this.articleLucene = articleLucene;
    }

    public void reindex(Integer taskSiteId, Integer taskUserId, String taskName) {
        reindex(taskSiteId, taskUserId, taskName, null);
    }

    public void reindex(Integer taskSiteId, Integer taskUserId, String taskName, @Nullable Integer siteId) {
        execute(taskSiteId, taskUserId, taskName, Task.TYPE_LUCENE, false, (taskId) -> doReindex(taskId, siteId));
    }

    private void doReindex(Integer taskId, @Nullable Integer siteId) {
        if (siteId != null) {
            articleLucene.deleteBySiteId(siteId);
        } else {
            articleLucene.deleteAll();
        }
        handleArticle(taskId, siteId, (article) -> articleLucene.save(EsArticle.of(article)));
    }
}
