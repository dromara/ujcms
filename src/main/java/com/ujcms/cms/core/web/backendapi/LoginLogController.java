package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.LoginLog;
import com.ujcms.cms.core.service.LoginLogService;
import com.ujcms.cms.core.service.args.LoginLogArgs;
import com.ujcms.commons.web.Entities;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Responses.Body;
import com.ujcms.commons.web.exception.Http404Exception;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

import static com.ujcms.cms.core.support.Constants.validPage;
import static com.ujcms.cms.core.support.Constants.validPageSize;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.commons.db.MyBatis.springPage;
import static com.ujcms.commons.query.QueryUtils.getQueryMap;

/**
 * 登录日志 Controller
 *
 * @author PONY
 */
@RestController("backendLoginLogController")
@RequestMapping(BACKEND_API + "/core/login-log")
public class LoginLogController {
    private final LoginLogService service;

    public LoginLogController(LoginLogService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('loginLog:list','*')")
    public Page<LoginLog> list(@RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize,
                               HttpServletRequest request) {
        LoginLogArgs args = LoginLogArgs.of(getQueryMap(request.getQueryString()));
        return springPage(service.selectPage(args, validPage(page), validPageSize(pageSize)));
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('loginLog:show','*')")
    public LoginLog show(@PathVariable("id") int id) {
        LoginLog bean = service.select(id);
        if (bean == null) {
            throw new Http404Exception("LoginLog not found. ID = " + id);
        }
        return bean;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('loginLog:create','*')")
    @OperationLog(module = "loginLog", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody @Valid LoginLog bean) {
        LoginLog loginLog = new LoginLog();
        Entities.copy(bean, loginLog);
        service.insert(loginLog);
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('loginLog:update','*')")
    @OperationLog(module = "loginLog", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody @Valid LoginLog bean) {
        LoginLog loginLog = service.select(bean.getId());
        if (loginLog == null) {
            return Responses.notFound("LoginLog not found. ID = " + bean.getId());
        }
        Entities.copy(bean, loginLog);
        service.update(loginLog);
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('loginLog:delete','*')")
    @OperationLog(module = "loginLog", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids) {
        service.delete(ids);
        return Responses.ok();
    }
}