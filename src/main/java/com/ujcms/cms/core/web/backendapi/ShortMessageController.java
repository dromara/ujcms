package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.ShortMessage;
import com.ujcms.cms.core.service.ShortMessageService;
import com.ujcms.cms.core.service.args.ShortMessageArgs;
import com.ujcms.util.web.Entities;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Responses.Body;
import com.ujcms.util.web.exception.Http404Exception;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

import static com.ujcms.cms.core.support.Constants.validPage;
import static com.ujcms.cms.core.support.Constants.validPageSize;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.util.db.MyBatis.springPage;
import static com.ujcms.util.query.QueryUtils.getQueryMap;

/**
 * 短信 Controller
 *
 * @author PONY
 */
@RestController("backendShortMessageController")
@RequestMapping(BACKEND_API + "/core/short-message")
public class ShortMessageController {
    private final ShortMessageService service;

    public ShortMessageController(ShortMessageService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('shortMessage:list','*')")
    public Page<ShortMessage> list(Integer page, Integer pageSize, HttpServletRequest request) {
        ShortMessageArgs args = ShortMessageArgs.of(getQueryMap(request.getQueryString()));
        return springPage(service.selectPage(args, validPage(page), validPageSize(pageSize)));
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('shortMessage:show','*')")
    public ShortMessage show(@PathVariable int id) {
        ShortMessage bean = service.select(id);
        if (bean == null) {
            throw new Http404Exception("ShortMessage not found. ID = " + id);
        }
        return bean;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('shortMessage:create','*')")
    @OperationLog(module = "shortMessage", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody @Valid ShortMessage bean) {
        ShortMessage shortMessage = new ShortMessage();
        Entities.copy(bean, shortMessage);
        service.insert(shortMessage);
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('shortMessage:update','*')")
    @OperationLog(module = "shortMessage", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody @Valid ShortMessage bean) {
        ShortMessage shortMessage = service.select(bean.getId());
        if (shortMessage == null) {
            return Responses.notFound("ShortMessage not found. ID = " + bean.getId());
        }
        Entities.copy(bean, shortMessage);
        service.update(shortMessage);
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('shortMessage:delete','*')")
    @OperationLog(module = "shortMessage", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids) {
        service.delete(ids);
        return Responses.ok();
    }
}