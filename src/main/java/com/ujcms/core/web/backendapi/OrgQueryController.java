package com.ujcms.core.web.backendapi;

import com.ujcms.core.domain.Org;
import com.ujcms.core.service.OrgQueryService;
import com.ofwise.util.query.QueryUtils;
import com.ofwise.util.web.Responses;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

import static com.ujcms.core.support.UrlConstants.BACKEND_API;

/**
 * 组织 Controller
 *
 * @author PONY
 */
@RestController("backendOrgQueryController")
@RequestMapping(BACKEND_API + "/core/org")
public class OrgQueryController {
    private OrgQueryService service;

    public OrgQueryController(OrgQueryService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermissions("org:list")
    public Object list(HttpServletRequest request) {
        Map<String, Object> queryMap = QueryUtils.getQueryMap(request.getQueryString());
        return service.selectList(queryMap);
    }

    @GetMapping("{id}")
    @RequiresPermissions("org:show")
    public Object show(@PathVariable Integer id) {
        Org bean = service.select(id);
        if (bean == null) {
            return Responses.notFound("Org not found. ID = " + id);
        }
        return bean;
    }
}