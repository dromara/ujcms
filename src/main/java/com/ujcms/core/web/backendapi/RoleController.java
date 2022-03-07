package com.ujcms.core.web.backendapi;

import com.ujcms.core.domain.Role;
import com.ujcms.core.service.RoleService;
import com.ofwise.util.query.QueryUtils;
import com.ofwise.util.web.Entities;
import com.ofwise.util.web.Responses;
import com.ofwise.util.web.Responses.Body;
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
 * 角色 Controller
 *
 * @author PONY
 */
@RestController("backendRoleController")
@RequestMapping(BACKEND_API + "/core/role")
public class RoleController {
    private RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermissions("role:list")
    public Object list(HttpServletRequest request) {
        Map<String, Object> queryMap = QueryUtils.getQueryMap(request.getQueryString());
        return service.selectList(queryMap);
    }

    @GetMapping("{id}")
    @RequiresPermissions("role:show")
    public Object show(@PathVariable Integer id) {
        Role bean = service.select(id);
        if (bean == null) {
            return Responses.notFound("Role not found. ID = " + id);
        }
        return bean;
    }

    @PostMapping
    @RequiresPermissions("role:create")
    public ResponseEntity<Body> create(@RequestBody Role bean) {
        Entities.emptyToNull(bean);
        service.insert(bean);
        return Responses.ok();
    }

    @PutMapping
    @RequiresPermissions("role:update")
    public ResponseEntity<Body> update(@RequestBody Role bean) {
        Role role = service.select(bean.getId());
        if (role == null) {
            return Responses.notFound("Role not found. ID = " + bean.getId());
        }
        Entities.copy(bean, role);
        service.update(role);
        return Responses.ok();
    }

    @PutMapping("order")
    @RequiresPermissions("role:update")
    public ResponseEntity<Body> updateOrder(@RequestBody Integer[] ids) {
        List<Role> list = new ArrayList<>();
        for (Integer id : ids) {
            Role bean = service.select(id);
            if (bean == null) {
                return Responses.notFound("Role not found. ID = " + id);
            }
            list.add(bean);
        }
        service.updateOrder(list);
        return Responses.ok();
    }

    @DeleteMapping
    @RequiresPermissions("role:delete")
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids) {
        service.delete(ids);
        return Responses.ok();
    }
}