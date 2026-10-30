package com.ujcms.cms.ext.component;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.listener.ArticlePublishListener;
import com.ujcms.cms.core.listener.ArticleRejectListener;
import com.ujcms.cms.core.domain.User;
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
public class NotificationEventListener implements ArticlePublishListener, ArticleRejectListener,
        CommentAuditListener {
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
                article.getChannelId(), article.getUserId(), article.getTitle(), article.getInputType());
        dispatchAfterCommit(() -> fanoutService.articlePublished(payload));
    }

    /**
     * 投稿被退回：通知作者（docs/spec/member-contribute/plan.md D6）。退回是一次人为动作，每次都通知，不用认领标记；
     * 只限投稿——编辑自己的文章在审批流程里被退回，走流程任务的意见
     */
    @Override
    public void afterArticleReject(Article article) {
        if (article.getInputType() != Article.INPUT_TYPE_CONTRIBUTE
                || User.ANONYMOUS_ID.equals(article.getUserId())) {
            return;
        }
        String reason = article.getExt().getRejectReason();
        String url = article.getSite().getDy() + "/settings/contribute";
        Long userId = article.getUserId();
        String title = article.getTitle();
        dispatchAfterCommit(() -> fanoutService.contributeRejected(userId, title, reason, url));
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
