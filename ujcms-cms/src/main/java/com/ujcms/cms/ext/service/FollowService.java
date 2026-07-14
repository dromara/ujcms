package com.ujcms.cms.ext.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.listener.ArticleDeleteListener;
import com.ujcms.cms.core.listener.ChannelDeleteListener;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.ext.domain.Follow;
import com.ujcms.cms.ext.domain.Notification;
import com.ujcms.cms.ext.domain.generated.GeneratedFollow;
import com.ujcms.cms.ext.mapper.FollowMapper;
import com.ujcms.cms.ext.service.args.FollowArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 关注 Service
 *
 * @author PONY
 */
@Service
public class FollowService implements ArticleDeleteListener, ChannelDeleteListener,
        SiteDeleteListener, UserDeleteListener {
    /**
     * 关注关系：未关注
     */
    public static final String RELATION_NONE = "none";
    /**
     * 关注关系：已关注
     */
    public static final String RELATION_FOLLOWING = "following";
    /**
     * 关注关系：本人
     */
    public static final String RELATION_SELF = "self";

    private final NotificationService notificationService;
    private final UserService userService;
    private final FollowMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public FollowService(NotificationService notificationService, UserService userService,
                         FollowMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.notificationService = notificationService;
        this.userService = userService;
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Follow bean, Long userId) {
        boolean articleRef = bean.getType() == Follow.TYPE_ARTICLE
                && bean.getArticleId() != null && bean.getChannelId() == null && bean.getToUserId() == null;
        boolean channelRef = bean.getType() == Follow.TYPE_CHANNEL
                && bean.getChannelId() != null && bean.getArticleId() == null && bean.getToUserId() == null;
        boolean userRef = bean.getType() == Follow.TYPE_USER
                && bean.getToUserId() != null && bean.getArticleId() == null && bean.getChannelId() == null;
        if (!articleRef && !channelRef && !userRef) {
            throw new IllegalArgumentException("Follow type and target mismatch. type: " + bean.getType());
        }
        if (userRef && bean.getToUserId().equals(userId)) {
            throw new IllegalArgumentException("Cannot follow yourself. userId: " + userId);
        }
        bean.setId(snowflakeSequence.nextId());
        bean.setUserId(userId);
        mapper.insert(bean);
        if (userRef) {
            // 新增粉丝：同步单条通知被关注用户。与好友申请/通过通知同一事务发送，唯一键兜底回滚时通知一并回滚
            notificationService.send(Notification.TYPE_FOLLOW_USER, bean.getToUserId(), userId,
                    followerName(userId), null, "/users/" + userId);
        }
    }

    /**
     * 粉丝显示名：优先昵称，其次用户名
     */
    private String followerName(Long userId) {
        User follower = userService.select(userId);
        if (follower == null) {
            return String.valueOf(userId);
        }
        return StringUtils.isNotBlank(follower.getNickname()) ? follower.getNickname() : follower.getUsername();
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        return mapper.delete(id);
    }

    @Nullable
    public Follow select(Long id) {
        return mapper.select(id);
    }

    @Nullable
    public Follow findByArticleIdAndUserId(Long articleId, Long userId) {
        return mapper.findByArticleIdAndUserId(articleId, userId);
    }

    @Nullable
    public Follow findByChannelIdAndUserId(Long channelId, Long userId) {
        return mapper.findByChannelIdAndUserId(channelId, userId);
    }

    @Nullable
    public Follow findByToUserIdAndUserId(Long toUserId, Long userId) {
        return mapper.findByToUserIdAndUserId(toUserId, userId);
    }

    /**
     * 获取关注文章的用户ID列表。供通知分发使用，按用户ID升序键集分页
     *
     * @param articleId     文章ID
     * @param excludeUserId 排除的用户ID（评论作者）
     * @param minUserId     用户ID下界（不含）。首次传 {@code 0}，后续传上一批最后一个用户ID
     * @param limit         每批数量
     * @return 用户ID列表
     */
    public List<Long> listUserIdByArticleId(Long articleId, Long excludeUserId, Long minUserId, int limit) {
        return PageMethod.offsetPage(0, limit, false).doSelectPage(() ->
                mapper.listUserIdByArticleId(articleId, excludeUserId, minUserId));
    }

    /**
     * 获取关注栏目的用户ID列表。供通知分发使用，按用户ID升序键集分页
     *
     * @param channelId     栏目ID
     * @param excludeUserId 排除的用户ID（文章作者）
     * @param minUserId     用户ID下界（不含）。首次传 {@code 0}，后续传上一批最后一个用户ID
     * @param limit         每批数量
     * @return 用户ID列表
     */
    public List<Long> listUserIdByChannelId(Long channelId, Long excludeUserId, Long minUserId, int limit) {
        return PageMethod.offsetPage(0, limit, false).doSelectPage(() ->
                mapper.listUserIdByChannelId(channelId, excludeUserId, minUserId));
    }

    /**
     * 获取关注某用户的粉丝用户ID列表。供文章发布通知分发使用，按用户ID升序键集分页
     *
     * @param toUserId      被关注用户ID（文章作者）
     * @param excludeUserId 排除的用户ID（作者本人）
     * @param minUserId     用户ID下界（不含）。首次传 {@code 0}，后续传上一批最后一个用户ID
     * @param limit         每批数量
     * @return 用户ID列表
     */
    public List<Long> listUserIdByToUserId(Long toUserId, Long excludeUserId, Long minUserId, int limit) {
        return PageMethod.offsetPage(0, limit, false).doSelectPage(() ->
                mapper.listUserIdByToUserId(toUserId, excludeUserId, minUserId));
    }

    /**
     * 我关注的用户数量（关注数）
     */
    public long countFollowing(Long userId) {
        return mapper.countByUserIdAndType(userId, Follow.TYPE_USER);
    }

    /**
     * 关注我的用户数量（粉丝数）
     */
    public long countFollowers(Long userId) {
        return mapper.countByToUserId(userId);
    }

    /**
     * 当前用户与目标用户的关注关系
     *
     * @param userId   当前用户ID
     * @param toUserId 目标用户ID
     * @return {@code none / following / self}
     */
    public String relation(Long userId, Long toUserId) {
        if (userId.equals(toUserId)) {
            return RELATION_SELF;
        }
        return mapper.findByToUserIdAndUserId(toUserId, userId) != null ? RELATION_FOLLOWING : RELATION_NONE;
    }

    public List<Follow> selectList(FollowArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedFollow.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<Follow> selectList(FollowArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Follow> selectPage(FollowArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    public long count(FollowArgs args) {
        return PageMethod.count(() -> selectList(args));
    }

    @Override
    public void preArticleDelete(Long articleId) {
        mapper.deleteByArticleId(articleId);
    }

    @Override
    public void preChannelDelete(Long channelId) {
        mapper.deleteByChannelId(channelId);
    }

    @Override
    public void preSiteDelete(Long siteId) {
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public void preUserDelete(Long userId) {
        // 双向清理：我发起的关注，以及关注我的行。满足 fk_follow_user 与 fk_follow_to_user 的 RESTRICT
        mapper.deleteByUserId(userId);
        mapper.deleteByToUserId(userId);
    }

    @Override
    public int deleteListenerOrder() {
        // 关注依赖文章(300)和栏目(100)，须先于两者删除
        return 500;
    }
}
