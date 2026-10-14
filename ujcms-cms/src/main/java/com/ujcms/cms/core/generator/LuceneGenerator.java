package com.ujcms.cms.core.generator;

import com.ujcms.cms.core.domain.Task;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.TaskService;
import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.lucene.domain.EsArticle;
import org.springframework.beans.factory.annotation.Qualifier;
import org.jspecify.annotations.Nullable;
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
                           TaskService taskService, @Qualifier("generatorTaskExecutor") ThreadPoolTaskExecutor executor) {
        super(articleService, taskService, executor);
        this.articleLucene = articleLucene;
    }

    public void reindex(Long taskSiteId, Long taskUserId, String taskName) {
        reindex(taskSiteId, taskUserId, taskName, null);
    }

    public void reindex(Long taskSiteId, Long taskUserId, String taskName, @Nullable Long siteId) {
        execute(taskSiteId, taskUserId, taskName, Task.TYPE_LUCENE, false, taskId -> doReindex(taskId, siteId));
    }

    /**
     * 更新某栏目（含子栏目）下文章的索引。栏目合并后文章改属目标栏目，索引里的栏目信息随之过期。
     * 逐篇覆盖写入、不清空索引：更新期间检索不受影响；完成后任务记录即删除（与栏目关联静态页任务一致）
     *
     * @param channelId 栏目ID
     */
    public void reindexChannel(Long taskSiteId, Long taskUserId, String taskName, Long channelId) {
        execute(taskSiteId, taskUserId, taskName, Task.TYPE_LUCENE, true, taskId ->
                handleArticle(taskId, null, channelId, article -> articleLucene.update(EsArticle.of(article))));
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
