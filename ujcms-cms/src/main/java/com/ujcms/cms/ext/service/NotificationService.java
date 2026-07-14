package com.ujcms.cms.ext.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.listener.ArticleDeleteListener;
import com.ujcms.cms.core.listener.ChannelDeleteListener;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.ext.domain.Notification;
import com.ujcms.cms.ext.domain.generated.GeneratedNotification;
import com.ujcms.cms.ext.mapper.NotificationMapper;
import com.ujcms.cms.ext.service.args.NotificationArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 通知 Service
 *
 * @author PONY
 */
@Service
public class NotificationService implements ArticleDeleteListener, ChannelDeleteListener,
        SiteDeleteListener, UserDeleteListener {
    private final NotificationMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public NotificationService(NotificationMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    /**
     * 标题最大长度
     */
    private static final int TITLE_MAX_LENGTH = 255;

    @Transactional(rollbackFor = Exception.class)
    public void insert(Notification bean) {
        bean.setId(snowflakeSequence.nextId());
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insertBatch(List<Notification> list) {
        if (list.isEmpty()) {
            return;
        }
        list.forEach(bean -> bean.setId(snowflakeSequence.nextId()));
        mapper.insertBatch(list);
    }

    /**
     * 发送通知（带内容引用的类型）
     */
    @Transactional(rollbackFor = Exception.class)
    public void send(short type, Long userId, @Nullable Long senderId, @Nullable Long siteId,
                     @Nullable Long articleId, @Nullable Long commentId, @Nullable Long channelId, String title) {
        Notification bean = new Notification();
        bean.setType(type);
        bean.setUserId(userId);
        bean.setSenderId(senderId);
        bean.setSiteId(siteId);
        bean.setArticleId(articleId);
        bean.setCommentId(commentId);
        bean.setChannelId(channelId);
        bean.setTitle(StringUtils.substring(title, 0, TITLE_MAX_LENGTH));
        insert(bean);
    }

    /**
     * 发送通知（无内容引用的类型，如系统公告、好友通知）
     */
    @Transactional(rollbackFor = Exception.class)
    public void send(short type, Long userId, @Nullable Long senderId, String title,
                     @Nullable String text, @Nullable String url) {
        Notification bean = new Notification();
        bean.setType(type);
        bean.setUserId(userId);
        bean.setSenderId(senderId);
        bean.setTitle(StringUtils.substring(title, 0, TITLE_MAX_LENGTH));
        bean.setText(text);
        bean.setUrl(url);
        insert(bean);
    }

    /**
     * 批量发送系统公告。公告允许重复发送，不做去重
     */
    @Transactional(rollbackFor = Exception.class)
    public void sendSystem(Collection<Long> userIds, @Nullable Long senderId, String title,
                           @Nullable String text, @Nullable String url) {
        List<Notification> list = new ArrayList<>(userIds.size());
        for (Long userId : userIds) {
            Notification bean = new Notification();
            bean.setType(Notification.TYPE_SYSTEM);
            bean.setUserId(userId);
            bean.setSenderId(senderId);
            bean.setTitle(StringUtils.substring(title, 0, TITLE_MAX_LENGTH));
            bean.setText(text);
            bean.setUrl(url);
            list.add(bean);
        }
        insertBatch(list);
    }

    @Nullable
    public Notification select(Long id) {
        return mapper.select(id);
    }

    public List<Notification> selectList(NotificationArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedNotification.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<Notification> selectList(NotificationArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Notification> selectPage(NotificationArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    public long count(NotificationArgs args) {
        return PageMethod.count(() -> selectList(args));
    }

    public long countUnread(Long userId) {
        return mapper.countUnread(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markRead(List<Long> ids, Long userId) {
        if (ids.isEmpty()) {
            return;
        }
        mapper.updateRead(ids, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(Long userId) {
        mapper.updateAllRead(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> ids, Long userId) {
        if (ids.isEmpty()) {
            return;
        }
        mapper.deleteByIds(ids, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRead(Long userId) {
        mapper.deleteReadByUserId(userId);
    }

    @Override
    public void preArticleDelete(Long articleId) {
        // 评论类通知均携带文章ID，随文章一并删除
        mapper.deleteByArticleId(articleId);
    }

    @Override
    public void preChannelDelete(Long channelId) {
        mapper.deleteByChannelId(channelId);
    }

    @Override
    public void preSiteDelete(Long siteId) {
        // 无站点的通知（系统公告、好友通知）予以保留
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public void preUserDelete(Long userId) {
        mapper.deleteByUserId(userId);
        // 发送用户被删除后，通知显示为系统
        mapper.clearSenderId(userId);
    }

    @Override
    public int deleteListenerOrder() {
        // 通知依赖文章(300)和栏目(100)，须先于两者删除
        return 500;
    }
}
