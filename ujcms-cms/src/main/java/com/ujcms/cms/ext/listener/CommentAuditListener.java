package com.ujcms.cms.ext.listener;

import com.ujcms.cms.ext.domain.Comment;

/**
 * 评论审核监听器
 *
 * @author PONY
 */
public interface CommentAuditListener {
    /**
     * 评论处于已审核状态的保存后回调（同一事务内；重复回调由实现方负责幂等）
     *
     * @param comment 评论对象
     */
    void afterCommentAudited(Comment comment);
}
