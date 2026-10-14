package com.ujcms.cms.ext.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.component.WordScreeningService;
import com.ujcms.cms.core.component.WordScreeningService.WordHit;
import com.ujcms.cms.core.component.WordScreeningService.WordType;
import com.ujcms.cms.core.listener.ArticleDeleteListener;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.core.service.AttachmentService;
import com.ujcms.cms.ext.component.UgcSubmitDispatcher;
import com.ujcms.cms.ext.domain.Comment;
import com.ujcms.cms.ext.domain.generated.GeneratedComment;
import com.ujcms.cms.ext.listener.CommentAuditListener;
import com.ujcms.cms.ext.listener.UgcSubmitListener.UgcSubmission;
import com.ujcms.cms.ext.mapper.CommentMapper;
import com.ujcms.cms.ext.service.args.CommentArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 评论 Service
 *
 * @author PONY
 */
@Service
public class CommentService implements ArticleDeleteListener, SiteDeleteListener, UserDeleteListener {
    private final AttachmentService attachmentService;
    private final CommentMapper mapper;
    private final SnowflakeSequence snowflakeSequence;
    private final WordScreeningService wordScreeningService;
    private final UgcSubmitDispatcher ugcSubmitDispatcher;

    public CommentService(AttachmentService attachmentService, CommentMapper mapper,
                          SnowflakeSequence snowflakeSequence, WordScreeningService wordScreeningService,
                          UgcSubmitDispatcher ugcSubmitDispatcher) {
        this.attachmentService = attachmentService;
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
        this.wordScreeningService = wordScreeningService;
        this.ugcSubmitDispatcher = ugcSubmitDispatcher;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Comment bean, Long userId, String ip) {
        bean.setId(snowflakeSequence.nextId());
        bean.setUserId(userId);
        bean.setIp(ip);
        // 免费词库门禁（docs/spec/ai/screening.md §4.2）：命中敏感词强制待审核，不报错——
        // 报错会向提交者泄露词库；易错词属编辑校对范畴，UGC不适用
        List<WordHit> dictHits = wordScreeningService.screen(bean.getText()).stream()
                .filter(hit -> hit.type() == WordType.SENSITIVE).toList();
        if (!dictHits.isEmpty()) {
            bean.setStatus(Comment.STATUS_UNREVIEWED);
        }
        mapper.insert(bean);
        attachmentService.insertRefer(GeneratedComment.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
        // 仅已审核（可见）评论计入文章评论数，与前台只展示已审核评论的口径一致；待审核评论待审核通过时再计入
        if (bean.getStatus() == Comment.STATUS_AUDITED) {
            mapper.addArticleComments(bean.getArticleId(), 1);
            auditListeners.forEach(it -> it.afterCommentAudited(bean));
        }
        ugcSubmitDispatcher.dispatch(new UgcSubmission(UgcSubmission.TYPE_COMMENT, bean.getId(), bean.getSiteId(),
                userId, bean.getText(), dictHits, bean.getStatus()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Comment bean) {
        mapper.update(bean);
        attachmentService.updateRefer(GeneratedComment.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Comment bean, Short status) {
        short oldStatus = bean.getStatus();
        bean.setStatus(status);
        mapper.update(bean);
        // 维护文章评论数：进入已审核 +1，离开已审核 -1，使计数始终等于可见（已审核）评论数
        if (status == Comment.STATUS_AUDITED && oldStatus != Comment.STATUS_AUDITED) {
            mapper.addArticleComments(bean.getArticleId(), 1);
        } else if (status != Comment.STATUS_AUDITED && oldStatus == Comment.STATUS_AUDITED) {
            mapper.addArticleComments(bean.getArticleId(), -1);
        }
        if (bean.getStatus() == Comment.STATUS_AUDITED) {
            auditListeners.forEach(it -> it.afterCommentAudited(bean));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        Comment bean = mapper.select(id);
        if (bean == null) {
            return 0;
        }
        // 删除附件引用（含子评论），附件回归未使用状态，以便清理
        selectList(CommentArgs.of().parentId(id)).forEach(child ->
                attachmentService.deleteRefer(GeneratedComment.TABLE_NAME, child.getId()));
        attachmentService.deleteRefer(GeneratedComment.TABLE_NAME, id);
        // 仅按已审核（可见）评论回退文章评论数，与 insert/updateStatus 的计数口径一致（须在删除前统计）
        long auditedCount = count(CommentArgs.of().parentId(id).status(Collections.singleton(Comment.STATUS_AUDITED)));
        if (bean.getStatus() == Comment.STATUS_AUDITED) {
            auditedCount++;
        }
        int count = mapper.deleteByParentId(id);
        count += mapper.delete(id);
        if (auditedCount > 0) {
            mapper.addArticleComments(bean.getArticleId(), (int) -auditedCount);
        }
        return count;
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public Comment select(Long id) {
        return mapper.select(id);
    }

    public List<Comment> selectList(CommentArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedComment.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<Comment> selectList(CommentArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Comment> selectPage(CommentArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    public long count(CommentArgs args) {
        return PageMethod.count(() -> selectList(args));
    }

    @Override
    public void preArticleDelete(Long articleId) {
        selectList(CommentArgs.of().articleId(articleId)).forEach(comment ->
                attachmentService.deleteRefer(GeneratedComment.TABLE_NAME, comment.getId()));
        mapper.deleteByArticleId(articleId);
    }

    @Override
    public void preUserDelete(Long userId) {
        // 逐条删除，以便维护文章评论次数
        selectList(CommentArgs.of().userId(userId)).forEach(comment -> delete(comment.getId()));
    }

    @Override
    public void preSiteDelete(Long siteId) {
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        // 评论依赖文章(300)，须先于文章删除
        return 500;
    }

    private List<CommentAuditListener> auditListeners = Collections.emptyList();

    @Lazy
    @Autowired(required = false)
    public void setAuditListeners(List<CommentAuditListener> auditListeners) {
        this.auditListeners = auditListeners;
    }
}
