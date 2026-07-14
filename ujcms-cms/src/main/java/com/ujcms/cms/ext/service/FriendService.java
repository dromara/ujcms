package com.ujcms.cms.ext.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.ext.domain.Friend;
import com.ujcms.cms.ext.domain.Notification;
import com.ujcms.cms.ext.domain.generated.GeneratedFriend;
import com.ujcms.cms.ext.mapper.FriendMapper;
import com.ujcms.cms.ext.service.args.FriendArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import com.ujcms.common.social.FriendRequestCache;
import com.ujcms.common.web.exception.Http400Exception;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 好友 Service
 * <p>
 * 好友关系不变式：待通过时仅申请方一行 {@code (sender, target, 1)}；通过后双方各一行
 * {@code (A,B,0)} 与 {@code (B,A,0)}，两行在同一事务内维护。
 *
 * @author PONY
 */
@Service
public class FriendService implements UserDeleteListener {
    private final NotificationService notificationService;
    private final FriendRequestCache friendRequestCache;
    private final FriendMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public FriendService(NotificationService notificationService, FriendRequestCache friendRequestCache,
                         FriendMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.notificationService = notificationService;
        this.friendRequestCache = friendRequestCache;
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    /**
     * 每人每天最多发起的好友申请数。基于 Caffeine 缓存计数：单节点、重启清零、滚动窗口，尽力而为即可
     */
    public static final int MAX_REQUESTS_PER_DAY = 50;

    /**
     * 好友通知的跳转地址
     */
    private static final String FRIEND_SETTINGS_URL = "/settings/friend";

    /**
     * 发起好友申请。已申请或已是好友时幂等返回当前关系；对方已向我申请时视为交叉申请，直接通过
     *
     * @param sender 申请人
     * @param target 接收人。存在性、非本人、非匿名、非注销校验由调用方完成
     * @return 申请后的好友关系
     */
    @Transactional(rollbackFor = Exception.class)
    public Friend.Relation request(User sender, User target) {
        Long senderId = sender.getId();
        Long targetId = target.getId();
        Friend forward = mapper.findByUserIdAndFriendId(senderId, targetId);
        if (forward != null) {
            // 幂等：已申请或已是好友，直接返回当前关系
            return forward.getStatus() == Friend.STATUS_ACCEPTED ? Friend.Relation.ACCEPTED
                    : Friend.Relation.PENDING_SENT;
        }
        Friend reverse = mapper.findByUserIdAndFriendId(targetId, senderId);
        if (reverse != null) {
            if (reverse.getStatus() == Friend.STATUS_PENDING) {
                // 交叉申请：双方均已表达意愿，直接通过
                acceptPending(reverse);
            } else {
                // 反向行已通过而正向行缺失，理论上不会出现，补齐镜像行即可
                insertRow(senderId, targetId, Friend.STATUS_ACCEPTED);
            }
            return Friend.Relation.ACCEPTED;
        }
        String cacheKey = senderId.toString();
        int count = friendRequestCache.getCount(cacheKey);
        if (count >= MAX_REQUESTS_PER_DAY) {
            throw new Http400Exception("Friend requests exceed daily limit: " + MAX_REQUESTS_PER_DAY);
        }
        insertRow(senderId, targetId, Friend.STATUS_PENDING);
        friendRequestCache.updateCount(cacheKey, count + 1);
        notificationService.send(Notification.TYPE_FRIEND_REQUEST, targetId, senderId,
                "请求加您为好友", null, FRIEND_SETTINGS_URL);
        return Friend.Relation.PENDING_SENT;
    }

    /**
     * 通过好友申请
     *
     * @param me          当前用户ID（申请接收人）
     * @param requesterId 申请人用户ID
     * @return 是否存在申请记录。已是好友时幂等返回 {@code true}
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean accept(Long me, Long requesterId) {
        Friend row = mapper.findByUserIdAndFriendId(requesterId, me);
        if (row == null) {
            return false;
        }
        if (row.getStatus() == Friend.STATUS_ACCEPTED) {
            return true;
        }
        acceptPending(row);
        return true;
    }

    /**
     * 通过待处理申请：原行置为已通过，插入镜像行，并通知申请人
     */
    private void acceptPending(Friend pending) {
        mapper.updateStatus(pending.getId(), Friend.STATUS_ACCEPTED);
        insertRow(pending.getFriendId(), pending.getUserId(), Friend.STATUS_ACCEPTED);
        notificationService.send(Notification.TYPE_FRIEND_ACCEPTED, pending.getUserId(), pending.getFriendId(),
                "同意了您的好友申请", null, FRIEND_SETTINGS_URL);
    }

    private void insertRow(Long userId, Long friendId, short status) {
        Friend bean = new Friend();
        bean.setId(snowflakeSequence.nextId());
        bean.setUserId(userId);
        bean.setFriendId(friendId);
        bean.setStatus(status);
        mapper.insert(bean);
    }

    /**
     * 解除两个用户之间的好友关系，涵盖拒绝、取消申请、删除好友。幂等
     *
     * @param me      当前用户ID
     * @param otherId 对方用户ID
     * @return 删除条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int removeRelation(Long me, Long otherId) {
        return mapper.deletePair(me, otherId);
    }

    /**
     * 是否互为好友。私信发送的门禁原语：好友关系的双行不变式保证单向唯一键查询即可判定
     *
     * @param a 用户ID
     * @param b 用户ID
     * @return 是否互为好友
     */
    public boolean isMutual(Long a, Long b) {
        Friend row = mapper.findByUserIdAndFriendId(a, b);
        return row != null && row.getStatus() == Friend.STATUS_ACCEPTED;
    }

    /**
     * 获取当前用户与对方的好友关系
     *
     * @param me      当前用户ID
     * @param otherId 对方用户ID
     * @return 好友关系
     */
    public Friend.Relation relation(Long me, Long otherId) {
        if (me.equals(otherId)) {
            return Friend.Relation.SELF;
        }
        Friend forward = mapper.findByUserIdAndFriendId(me, otherId);
        if (forward != null) {
            return forward.getStatus() == Friend.STATUS_ACCEPTED ? Friend.Relation.ACCEPTED
                    : Friend.Relation.PENDING_SENT;
        }
        Friend reverse = mapper.findByUserIdAndFriendId(otherId, me);
        if (reverse != null) {
            return reverse.getStatus() == Friend.STATUS_PENDING ? Friend.Relation.PENDING_RECEIVED
                    : Friend.Relation.ACCEPTED;
        }
        return Friend.Relation.NONE;
    }

    @Nullable
    public Friend select(Long id) {
        return mapper.select(id);
    }

    public List<Friend> selectList(FriendArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedFriend.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<Friend> selectList(FriendArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Friend> selectPage(FriendArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    public long count(FriendArgs args) {
        return PageMethod.count(() -> selectList(args));
    }

    /**
     * 待我处理的好友申请数量（角标用）
     *
     * @param userId 用户ID
     * @return 申请数量
     */
    public long pendingCount(Long userId) {
        return count(FriendArgs.of().friendId(userId).status(Friend.STATUS_PENDING));
    }

    @Override
    public void preUserDelete(Long userId) {
        // 双向清理：本人视角行与对方视角的镜像行
        mapper.deleteByUserId(userId);
        mapper.deleteByFriendId(userId);
    }
}
