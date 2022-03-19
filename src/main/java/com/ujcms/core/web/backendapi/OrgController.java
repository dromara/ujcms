package com.ujcms.core.web.backendapi;

import com.ofwise.util.query.QueryUtils;
import com.ofwise.util.web.Entities;
import com.ofwise.util.web.Responses;
import com.ofwise.util.web.Responses.Body;
import com.ujcms.core.domain.Org;
import com.ujcms.core.service.OrgService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.ujcms.core.support.UrlConstants.BACKEND_API;

/**
 * 组织 Controller
 *
 * @author PONY
 */
@RestController("backendOrgController")
@RequestMapping(BACKEND_API + "/core/org")
public class OrgController {
    private OrgService service;

    public OrgController(OrgService service) {
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

    @PostMapping
    @RequiresPermissions("org:create")
    public ResponseEntity<Body> create(@RequestBody Org bean) {
        service.insert(bean);
        return Responses.ok();
    }

    @PutMapping
    @RequiresPermissions("org:update")
    public ResponseEntity<Body> update(@RequestBody Org bean) {
        Org org = service.select(bean.getId());
        if (org == null) {
            return Responses.notFound("Org not found. ID = " + bean.getId());
        }
        Entities.copy(bean, org, "parentId", "order");
        service.update(org, bean.getParentId());
        return Responses.ok();
    }

    @PutMapping("order")
    @RequiresPermissions("org:update")
    public ResponseEntity<Body> updateOrder(@RequestBody Integer[] ids) {
        List<Org> list = new ArrayList<>();
        for (Integer id : ids) {
            Org bean = service.select(id);
            if (bean == null) {
                return Responses.notFound("Org not found. ID = " + id);
            }
            list.add(bean);
        }
        service.updateOrder(list);
        return Responses.ok();
    }

    @DeleteMapping
    @RequiresPermissions("org:delete")
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids) {
        service.delete(ids);
        return Responses.ok();
    }
}