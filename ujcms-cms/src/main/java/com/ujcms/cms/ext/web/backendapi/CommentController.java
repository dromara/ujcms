package com.ujcms.cms.ext.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.ValidUtils;
import com.ujcms.cms.ext.domain.Comment;
import com.ujcms.cms.ext.service.CommentService;
import com.ujcms.cms.ext.service.args.CommentArgs;
import com.ujcms.common.web.Entities;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http404Exception;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.ujcms.cms.core.support.Constants.validPage;
import static com.ujcms.cms.core.support.Constants.validPageSize;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.common.db.MyBatis.springPage;
import static com.ujcms.common.query.QueryUtils.getQueryMap;

/**
 * 评论 Controller
 *
 * @author PONY
 */
@RestController("backendCommentController")
@RequestMapping(BACKEND_API + "/ext/comment")
public class CommentController {
    private final CommentService service;

    public CommentController(CommentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('comment:list','*')")
    public Page<Comment> list(@RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize,
                              HttpServletRequest request) {
        CommentArgs args = CommentArgs.of(getQueryMap(request.getQueryString()))
                .siteId(Contexts.getCurrentSiteId());
        return springPage(service.selectPage(args, validPage(page), validPageSize(pageSize)));
    }

    @GetMapping("unreviewed-count")
    @PreAuthorize("hasAnyAuthority('comment:list','*')")
    public long unreviewedCount() {
        CommentArgs args = CommentArgs.of();
        args.siteId(Contexts.getCurrentSiteId());
        args.status(Collections.singleton(Comment.STATUS_UNREVIEWED));
        return service.count(args);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('comment:show','*')")
    public Comment show(@PathVariable Long id) {
        Comment bean = service.select(id);
        if (bean == null) {
            throw new Http404Exception(Comment.NOT_FOUND + id);
        }
        ValidUtils.dataInSite(bean.getSiteId(), Contexts.getCurrentSiteId());
        return bean;
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('comment:update','*')")
    @OperationLog(module = "comment", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody @Valid Comment bean) {
        Comment comment = service.select(bean.getId());
        if (comment == null) {
            return Responses.notFound(Comment.NOT_FOUND + bean.getId());
        }
        // 管理端编辑只能移除图片，不能新增，防止引入外部URL
        if (!comment.getImages().containsAll(bean.getImages())) {
            throw new Http400Exception("Comment images can only be removed.");
        }
        Entities.copy(bean, comment, "siteId", "articleId", "userId", "parentId", "created", "ip", "status");
        ValidUtils.dataInSite(comment.getSiteId(), Contexts.getCurrentSiteId());
        service.update(comment);
        return Responses.ok();
    }

    @PutMapping("status")
    @PreAuthorize("hasAnyAuthority('comment:updateStatus','*')")
    @OperationLog(module = "comment", operation = "updateStatus", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateStatus(@RequestBody @Valid UpdateStatusParams params) {
        Long siteId = Contexts.getCurrentSiteId();
        params.ids.stream().filter(Objects::nonNull).map(service::select)
                .filter(Objects::nonNull).forEach(bean -> {
            ValidUtils.dataInSite(bean.getSiteId(), siteId);
            service.updateStatus(bean, params.status);
        });
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('comment:delete','*')")
    @OperationLog(module = "comment", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids) {
        Long siteId = Contexts.getCurrentSiteId();
        for (Long id : ids) {
            Comment bean = service.select(id);
            if (bean == null) {
                throw new Http404Exception(Comment.NOT_FOUND + id);
            }
            ValidUtils.dataInSite(bean.getSiteId(), siteId);
            service.delete(id);
        }
        return Responses.ok();
    }

    public static class UpdateStatusParams implements Serializable {
        private static final long serialVersionUID = 1L;

        @NotNull
        private List<Long> ids;
        @NotNull
        @Min(0)
        @Max(2)
        private Short status;

        public List<Long> getIds() {
            return ids;
        }

        public void setIds(List<Long> ids) {
            this.ids = ids;
        }

        public Short getStatus() {
            return status;
        }

        public void setStatus(Short status) {
            this.status = status;
        }
    }
}
