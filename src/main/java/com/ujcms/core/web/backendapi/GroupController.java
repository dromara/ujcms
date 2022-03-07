package com.ujcms.core.web.backendapi;

import com.ujcms.core.domain.Group;
import com.ujcms.core.service.GroupService;
import com.ofwise.util.query.QueryUtils;
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
 * 用户组 Controller
 *
 * @author PONY
 */
@RestController("backendGroupController")
@RequestMapping(BACKEND_API + "/core/group")
public class GroupController {
    private GroupService service;

    public GroupController(GroupService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermissions("group:list")
    public Object list(HttpServletRequest request) {
        Map<String, Object> queryMap = QueryUtils.getQueryMap(request.getQueryString());
        return service.selectList(queryMap);
    }

    @GetMapping("{id}")
    @RequiresPermissions("group:show")
    public Object show(@PathVariable Integer id) {
        Group bean = service.select(id);
        if (bean == null) {
            return Responses.notFound("Group not found. ID = " + id);
        }
        return bean;
    }

    @PostMapping
    @RequiresPermissions("group:create")
    public ResponseEntity<Body> create(@RequestBody Group bean) {
        service.insert(bean);
        return Responses.ok();
    }

    @PutMapping
    @RequiresPermissions("group:update")
    public ResponseEntity<Body> update(@RequestBody Group bean) {
        service.update(bean);
        return Responses.ok();
    }

    @PutMapping("order")
    @RequiresPermissions("group:update")
    public ResponseEntity<Body> updateOrder(@RequestBody Integer[] ids) {
        List<Group> list = new ArrayList<>();
        for (Integer id : ids) {
            Group bean = service.select(id);
            if (bean == null) {
                return Responses.notFound("Role not found. ID = " + id);
            }
            list.add(bean);
        }
        service.updateOrder(list);
        return Responses.ok();
    }

    @DeleteMapping
    @RequiresPermissions("group:delete")
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids) {
        service.delete(ids);
        return Responses.ok();
    }
}