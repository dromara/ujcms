package com.ujcms.cms.ext.component;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.listener.ArticlePublishListener;
import com.ujcms.cms.ext.component.NotificationFanoutService.ArticlePayload;
import com.ujcms.cms.ext.component.NotificationFanoutService.CommentPayload;
import com.ujcms.cms.ext.domain.Comment;
import com.ujcms.cms.ext.listener.CommentAuditListener;
import com.ujcms.cms.ext.mapper.NotificationMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 通知事件监听器。在发布、审核事务内同步执行：先读后改原子认领源数据分发标记（精确一次），
 * 认领成功后于事务提交后异步分发（分发插入的通知行携带文章外键，须待源数据提交可见后进行）
 *
 * @author PONY
 */
@Component
public class NotificationEventListener implements ArticlePublishListener, CommentAuditListener {
    private final NotificationMapper mapper;
    private final NotificationFanoutService fanoutService;

    public NotificationEventListener(NotificationMapper mapper, NotificationFanoutService fanoutService) {
        this.mapper = mapper;
        this.fanoutService = fanoutService;
    }

    private static final String NOTIFIED = "1";

    @Override
    public void afterArticlePublish(Article article) {
        // 便宜的前置读取，让重复保存无需行锁即可返回
        if (NOTIFIED.equals(mapper.findArticlePublishNotified(article.getId()))) {
            return;
        }
        // 原子认领。0 行表示已被认领；同一事务内认领，回滚时自动解除
        if (mapper.claimArticlePublishNotified(article.getId()) == 0) {
            return;
        }
        ArticlePayload payload = new ArticlePayload(article.getId(), article.getSiteId(),
                article.getChannelId(), article.getUserId(), article.getTitle());
        dispatchAfterCommit(() -> fanoutService.articlePublished(payload));
    }

    @Override
    public void afterCommentAudited(Comment comment) {
        if (NOTIFIED.equals(mapper.findCommentNotified(comment.getId()))) {
            return;
        }
        if (mapper.claimCommentNotified(comment.getId()) == 0) {
            return;
        }
        CommentPayload payload = new CommentPayload(comment.getId(), comment.getArticleId(),
                comment.getSiteId(), comment.getUserId(), comment.getParentId(), comment.getText());
        dispatchAfterCommit(() -> fanoutService.commentAudited(payload));
    }

    private void dispatchAfterCommit(Runnable fanout) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fanout.run();
                }
            });
        } else {
            fanout.run();
        }
    }
}
