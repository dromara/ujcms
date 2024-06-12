package com.ujcms.cms.core.generator;

import com.ujcms.cms.core.domain.Task;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.TaskService;
import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.lucene.domain.EsArticle;
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
    private final ArticleLucene articleLucene;

    public LuceneGenerator(ArticleLucene articleLucene, ArticleService articleService,
                           TaskService taskService, @Qualifier("generator") ThreadPoolTaskExecutor executor) {
        super(articleService, taskService, executor);
        this.articleLucene = articleLucene;
    }

    public void reindex(Long taskSiteId, Long taskUserId, String taskName) {
        reindex(taskSiteId, taskUserId, taskName, null);
    }

    public void reindex(Long taskSiteId, Long taskUserId, String taskName, @Nullable Long siteId) {
        execute(taskSiteId, taskUserId, taskName, Task.TYPE_LUCENE, false, taskId -> doReindex(taskId, siteId));
    }

    private void doReindex(Long taskId, @Nullable Long siteId) {
        if (siteId != null) {
            articleLucene.deleteBySiteId(siteId);
        } else {
            articleLucene.deleteAll();
        }
        handleArticle(taskId, siteId, article -> articleLucene.save(EsArticle.of(article)));
    }
}
