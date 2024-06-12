package com.ujcms.cms.ext.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.ValidUtils;
import com.ujcms.cms.ext.domain.MessageBoard;
import com.ujcms.cms.ext.service.MessageBoardService;
import com.ujcms.cms.ext.service.args.MessageBoardArgs;
import com.ujcms.commons.web.Entities;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Responses.Body;
import com.ujcms.commons.web.Servlets;
import com.ujcms.commons.web.exception.Http400Exception;
import com.ujcms.commons.web.exception.Http404Exception;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.ujcms.cms.core.support.Constants.validPage;
import static com.ujcms.cms.core.support.Constants.validPageSize;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.commons.db.MyBatis.springPage;
import static com.ujcms.commons.query.QueryUtils.getQueryMap;

/**
 * 留言板 Controller
 *
 * @author PONY
 */
@RestController("backendMessageBoardController")
@RequestMapping(BACKEND_API + "/ext/message-board")
public class MessageBoardController {
    private final MessageBoardService service;

    public MessageBoardController(MessageBoardService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('messageBoard:list','*')")
    public Page<MessageBoard> list(@RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize,
                                   HttpServletRequest request) {
        MessageBoardArgs args = MessageBoardArgs.of(getQueryMap(request.getQueryString()))
                .siteId(Contexts.getCurrentSiteId());
        return springPage(service.selectPage(args, validPage(page), validPageSize(pageSize)));
    }

    @GetMapping("unreviewed-count")
    @PreAuthorize("hasAnyAuthority('messageBoard:list','*')")
    public long unreviewedCount() {
        MessageBoardArgs args = MessageBoardArgs.of();
        args.siteId(Contexts.getCurrentSiteId());
        args.status(Collections.singleton(MessageBoard.STATUS_UNREVIEWED));
        return service.count(args);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('messageBoard:show','*')")
    public MessageBoard show(@PathVariable Long id) {
        MessageBoard bean = service.select(id);
        if (bean == null) {
            throw new Http404Exception("MessageBoard not found. ID = " + id);
        }
        ValidUtils.dataInSite(bean.getSiteId(), Contexts.getCurrentSiteId());
        return bean;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('messageBoard:create','*')")
    @OperationLog(module = "messageBoard", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody @Valid MessageBoard bean, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        MessageBoard messageBoard = new MessageBoard();
        Entities.copy(bean, messageBoard,
                "siteId", "replied", "status", "userId", "created", "replyUserId", "replyDate", "ip", "recommended");
        messageBoard.setSiteId(site.getId());
        String ip = Servlets.getRemoteAddr(request);
        service.insert(messageBoard, user.getId(), ip);
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('messageBoard:update','*')")
    @OperationLog(module = "messageBoard", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody @Valid MessageBoard bean) {
        User user = Contexts.getCurrentUser();
        MessageBoard messageBoard = service.select(bean.getId());
        if (messageBoard == null) {
            return Responses.notFound("MessageBoard not found. ID = " + bean.getId());
        }
        if (!StringUtils.equals(bean.getReplyText(), messageBoard.getReplyText())) {
            messageBoard.setReplyUserId(user.getId());
            messageBoard.setReplyDate(OffsetDateTime.now());
        }
        Entities.copy(bean, messageBoard,
                "siteId", "replied", "status", "userId", "created", "replyUserId", "replyDate", "ip");
        ValidUtils.dataInSite(messageBoard.getSiteId(), Contexts.getCurrentSiteId());
        service.update(messageBoard);
        return Responses.ok();
    }

    @PutMapping("status")
    @PreAuthorize("hasAnyAuthority('messageBoard:updateStatus','*')")
    @OperationLog(module = "messageBoard", operation = "updateStatus", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateStatus(@RequestBody @Valid UpdateStatusParams params) {
        Long siteId = Contexts.getCurrentSiteId();
        params.ids.stream().filter(Objects::nonNull).map(service::select)
                .filter(Objects::nonNull).forEach(bean -> {
            ValidUtils.dataInSite(bean.getSiteId(), siteId);
            bean.setStatus(params.status);
            service.update(bean);
        });
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('messageBoard:delete','*')")
    @OperationLog(module = "messageBoard", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids) {
        Long siteId = Contexts.getCurrentSiteId();
        for (Long id : ids) {
            MessageBoard bean = service.select(id);
            if (bean == null) {
                throw new Http400Exception(MessageBoard.NOT_FOUND + id);
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