package com.ujcms.cms.ext.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.ValidUtils;
import com.ujcms.cms.ext.domain.MessageBoardType;
import com.ujcms.cms.ext.service.MessageBoardTypeService;
import com.ujcms.cms.ext.service.args.MessageBoardTypeArgs;
import com.ujcms.commons.db.order.MoveOrderParams;
import com.ujcms.commons.web.Entities;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Responses.Body;
import com.ujcms.commons.web.exception.Http400Exception;
import com.ujcms.commons.web.exception.Http404Exception;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;
import java.util.Optional;

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.commons.query.QueryUtils.getQueryMap;

/**
 * 留言类型 Controller
 *
 * @author PONY
 */
@RestController("backendMessageBoardTypeController")
@RequestMapping(BACKEND_API + "/ext/message-board-type")
public class MessageBoardTypeController {
    private final MessageBoardTypeService service;

    public MessageBoardTypeController(MessageBoardTypeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('messageBoardType:list','*')")
    public List<MessageBoardType> list(HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        MessageBoardTypeArgs args = MessageBoardTypeArgs.of(getQueryMap(request.getQueryString()));
        args.siteId(site.getId());
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('messageBoardType:show','*')")
    public MessageBoardType show(@PathVariable("id") Long id) {
        Site site = Contexts.getCurrentSite();
        MessageBoardType messageBoardType = Optional.ofNullable(service.select(id))
                .orElseThrow(() -> new Http404Exception(NOT_FOUND + id));
        ValidUtils.dataInSite(messageBoardType.getSiteId(), site.getId());
        return messageBoardType;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('messageBoardType:create','*')")
    @OperationLog(module = "messageBoardType", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody @Valid MessageBoardType bean) {
        Site site = Contexts.getCurrentSite();
        MessageBoardType messageBoardType = new MessageBoardType();
        Entities.copy(bean, messageBoardType);
        messageBoardType.setSiteId(site.getId());
        service.insert(messageBoardType);
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('messageBoardType:update','*')")
    @OperationLog(module = "messageBoardType", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody @Valid MessageBoardType bean) {
        Site site = Contexts.getCurrentSite();
        MessageBoardType messageBoardType = Optional.ofNullable(service.select(bean.getId()))
                .orElseThrow(() -> new Http400Exception(NOT_FOUND + bean.getId()));
        Entities.copy(bean, messageBoardType, "siteId");
        ValidUtils.dataInSite(messageBoardType.getSiteId(), site.getId());
        service.update(messageBoardType);
        return Responses.ok();
    }

    @PostMapping("update-order")
    @PreAuthorize("hasAnyAuthority('messageBoardType:update','*')")
    @OperationLog(module = "messageBoardType", operation = "updateOrder", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateOrder(@RequestBody @Valid MoveOrderParams params) {
        Site site = Contexts.getCurrentSite();
        MessageBoardType fromBean = Optional.ofNullable(service.select(params.getFromId()))
                .orElseThrow(() -> new Http400Exception(NOT_FOUND + params.getFromId()));
        MessageBoardType toBean = Optional.ofNullable(service.select(params.getToId()))
                .orElseThrow(() -> new Http400Exception(NOT_FOUND + params.getToId()));
        ValidUtils.dataInSite(fromBean.getSiteId(), site.getId());
        ValidUtils.dataInSite(toBean.getSiteId(), site.getId());
        service.moveOrder(fromBean.getId(), toBean.getId());
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('messageBoardType:delete','*')")
    @OperationLog(module = "messageBoardType", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids) {
        Site site = Contexts.getCurrentSite();
        for (Long id : ids) {
            MessageBoardType bean = Optional.ofNullable(service.select(id))
                    .orElseThrow(() -> new Http400Exception(NOT_FOUND + id));
            ValidUtils.dataInSite(bean.getSiteId(), site.getId());
            service.delete(bean.getId());
        }
        return Responses.ok();
    }

    private static final String NOT_FOUND = "MessageBoardType not found. ID = ";
}