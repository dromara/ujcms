package com.ujcms.cms.ext.component;

import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.ext.domain.Comment;
import com.ujcms.cms.ext.domain.Notification;
import com.ujcms.cms.ext.service.CommentService;
import com.ujcms.cms.ext.service.FollowService;
import com.ujcms.cms.ext.service.NotificationService;
import com.ujcms.cms.ext.service.support.MentionParser;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongFunction;

/**
 * 通知分发 Service。由 {@link NotificationEventListener} 在源事务提交后异步调用，
 * 分发耗时与发布、审核操作的响应时间无关；分发异常也不会回滚源事务
 *
 * @author PONY
 */
@Component
public class NotificationFanoutService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationFanoutService.class);

    private final NotificationService notificationService;
    private final CommentService commentService;
    private final FollowService followService;
    private final UserService userService;

    public NotificationFanoutService(NotificationService notificationService, CommentService commentService,
                                     FollowService followService, UserService userService) {
        this.notificationService = notificationService;
        this.commentService = commentService;
        this.followService = followService;
        this.userService = userService;
    }

    /**
     * 评论摘要最大长度
     */
    private static final int SNIPPET_MAX_LENGTH = 100;
    /**
     * 标题最大长度
     */
    private static final int TITLE_MAX_LENGTH = 255;
    /**
     * 粉丝分发每批数量。每批一个事务，内存占用恒定
     */
    private static final int FANOUT_BATCH_SIZE = 1000;
    /**
     * 粉丝分发数量预警阈值
     */
    private static final long FANOUT_WARN_THRESHOLD = 100_000L;

    /**
     * 文章发布分发。认领标记已保证每篇文章至多进入一次。
     * 作者粉丝分发先于栏目粉丝分发，最具体者优先：同时关注作者与栏目的用户只收到作者通知
     */
    @Async
    public void articlePublished(ArticlePayload payload) {
        String title = StringUtils.substring(payload.title(), 0, TITLE_MAX_LENGTH);
        // 1. 关注用户新文章分发。作者本人已在查询中排除；收件人收入 skip 集，供更泛的栏目分发去重
        Set<Long> notifiedUserIds = new HashSet<>();
        fanoutFollowers(minUserId -> followService.listUserIdByToUserId(
                        payload.userId(), payload.userId(), minUserId, FANOUT_BATCH_SIZE),
                Collections.emptySet(),
                userId -> {
                    Notification bean = new Notification();
                    bean.setType(Notification.TYPE_FOLLOW_USER_ARTICLE);
                    bean.setUserId(userId);
                    bean.setSenderId(payload.userId());
                    bean.setSiteId(payload.siteId());
                    bean.setArticleId(payload.id());
                    bean.setTitle(title);
                    return bean;
                },
                notifiedUserIds,
                "author " + payload.userId());
        // 2. 关注栏目新文章分发。作者本人已在查询中排除，另跳过已收到作者通知的用户
        fanoutFollowers(minUserId -> followService.listUserIdByChannelId(
                        payload.channelId(), payload.userId(), minUserId, FANOUT_BATCH_SIZE),
                notifiedUserIds,
                userId -> {
                    Notification bean = new Notification();
                    bean.setType(Notification.TYPE_FOLLOW_CHANNEL_ARTICLE);
                    bean.setUserId(userId);
                    bean.setSenderId(payload.userId());
                    bean.setSiteId(payload.siteId());
                    bean.setArticleId(payload.id());
                    bean.setChannelId(payload.channelId());
                    bean.setTitle(title);
                    return bean;
                },
                null,
                "channel " + payload.channelId());
    }

    /**
     * 评论审核通过分发。认领标记已保证每条评论至多进入一次
     */
    @Async
    public void commentAudited(CommentPayload payload) {
        // 已获得更具体通知（或无需通知）的用户：评论者本人、匿名用户
        Set<Long> notifiedUserIds = new HashSet<>();
        notifiedUserIds.add(payload.userId());
        notifiedUserIds.add(User.ANONYMOUS_ID);
        String snippet = StringUtils.substring(payload.text(), 0, SNIPPET_MAX_LENGTH);
        // 1. 评论回复：通知上级评论作者
        Long parentId = payload.parentId();
        if (parentId != null) {
            Comment parent = commentService.select(parentId);
            if (parent != null && !notifiedUserIds.contains(parent.getUserId())) {
                notificationService.send(Notification.TYPE_COMMENT_REPLY, parent.getUserId(), payload.userId(),
                        payload.siteId(), payload.articleId(), payload.id(), null, snippet);
                notifiedUserIds.add(parent.getUserId());
            }
        }
        // 2. @提及：每个被提及用户一条，排除已获得更具体通知的用户
        for (Long mentionUserId : listMentionUserIds(payload)) {
            if (notifiedUserIds.add(mentionUserId)) {
                notificationService.send(Notification.TYPE_MENTION, mentionUserId, payload.userId(),
                        payload.siteId(), payload.articleId(), payload.id(), null, snippet);
            }
        }
        // 3. 关注文章新评论粉丝分发。评论作者已在查询中排除，另跳过已获得更具体通知的用户
        fanoutFollowers(minUserId -> followService.listUserIdByArticleId(
                        payload.articleId(), payload.userId(), minUserId, FANOUT_BATCH_SIZE),
                notifiedUserIds,
                userId -> {
                    Notification bean = new Notification();
                    bean.setType(Notification.TYPE_FOLLOW_ARTICLE_COMMENT);
                    bean.setUserId(userId);
                    bean.setSenderId(payload.userId());
                    bean.setSiteId(payload.siteId());
                    bean.setArticleId(payload.articleId());
                    bean.setCommentId(payload.id());
                    bean.setTitle(snippet);
                    return bean;
                },
                null,
                "article " + payload.articleId());
    }

    /**
     * 粉丝分发。按 user_id_ 升序键集分页循环，每批一个事务，内存占用恒定；
     * 中途异常只丢失剩余批次（认领标记已置位，通知尽力送达即可）
     *
     * @param loader      按用户ID下界（不含）加载一批粉丝用户ID
     * @param skipUserIds 跳过的用户ID（已获得更具体通知的用户）
     * @param creator     按用户ID构建通知
     * @param collectInto 非空时，将本次实际发出通知的收件人用户ID收入此集合（供更泛分发去重）
     * @param target      分发目标描述，用于预警日志
     */
    private void fanoutFollowers(LongFunction<List<Long>> loader, Set<Long> skipUserIds,
                                 Function<Long, Notification> creator, @Nullable Set<Long> collectInto,
                                 String target) {
        long total = 0;
        long minUserId = 0;
        List<Long> userIds;
        do {
            userIds = loader.apply(minUserId);
            if (userIds.isEmpty()) {
                break;
            }
            List<Notification> list = new ArrayList<>(userIds.size());
            for (Long userId : userIds) {
                if (!skipUserIds.contains(userId)) {
                    list.add(creator.apply(userId));
                    if (collectInto != null) {
                        collectInto.add(userId);
                    }
                }
            }
            notificationService.insertBatch(list);
            total += userIds.size();
            minUserId = userIds.get(userIds.size() - 1);
        } while (userIds.size() >= FANOUT_BATCH_SIZE);
        if (total > FANOUT_WARN_THRESHOLD) {
            logger.warn("Follower fan-out exceeds {} users ({}): {}", FANOUT_WARN_THRESHOLD, total, target);
        }
    }

    /**
     * 解析评论文本中的 @提及 用户（见 docs/spec/membership/comment-mention.md）。
     * 无提及表：认领标记保证每条评论至多解析一次，审核时按当前文本重新解析即可
     */
    private List<Long> listMentionUserIds(CommentPayload payload) {
        return MentionParser.parseUserIds(payload.text(), userService::selectByUsername);
    }

    /**
     * 文章发布事件的不可变载荷
     */
    public record ArticlePayload(Long id, Long siteId, Long channelId, Long userId, String title) {
    }

    /**
     * 评论审核通过事件的不可变载荷
     */
    public record CommentPayload(Long id, Long articleId, Long siteId, Long userId,
                                 @Nullable Long parentId, @Nullable String text) {
    }
}
