package com.ujcms.cms.core.web.backendapi;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.OperationLog;
import com.ujcms.cms.core.service.OperationLogService;
import com.ujcms.cms.core.service.args.OperationLogArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.ValidUtils;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Responses.Body;
import com.ujcms.commons.web.Views;
import com.ujcms.commons.web.exception.Http400Exception;
import com.ujcms.commons.web.exception.Http404Exception;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static com.ujcms.cms.core.support.Constants.validPage;
import static com.ujcms.cms.core.support.Constants.validPageSize;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.commons.db.MyBatis.springPage;
import static com.ujcms.commons.query.QueryUtils.getQueryMap;

/**
 * 操作日志接口
 *
 * @author PONY
 */
@RestController("backendOperationLogController")
@RequestMapping(BACKEND_API + "/core/operation-log")
public class OperationLogController {
    private final OperationLogService service;

    public OperationLogController(OperationLogService service) {
        this.service = service;
    }

    @GetMapping
    @JsonView(Views.List.class)
    @PreAuthorize("hasAnyAuthority('operationLog:list','*')")
    public Page<OperationLog> list(@RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize,
                                   HttpServletRequest request) {
        OperationLogArgs args = OperationLogArgs.of(getQueryMap(request.getQueryString()))
                .siteId(Contexts.getCurrentSiteId());
        return springPage(service.selectPage(args, validPage(page), validPageSize(pageSize)));
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('operationLog:show','*')")
    public OperationLog show(@PathVariable("id") long id) {
        OperationLog bean = service.select(id);
        if (bean == null) {
            throw new Http404Exception("OperationLog not found. ID = " + id);
        }
        return bean;
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('operationLog:delete','*')")
    @com.ujcms.cms.core.aop.annotations.OperationLog(module = "operationLog", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids) {
        Integer siteId = Contexts.getCurrentSiteId();
        for (Long id : ids) {
            OperationLog bean = service.select(id);
            if (bean == null) {
                throw new Http400Exception("OperationLog not found. ID = " + id);
            }
            ValidUtils.dataInSite(bean.getSiteId(), siteId);
            service.delete(id);
        }
        return Responses.ok();
    }
}