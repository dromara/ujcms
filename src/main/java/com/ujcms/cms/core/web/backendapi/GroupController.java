package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.Group;
import com.ujcms.cms.core.service.GroupService;
import com.ujcms.cms.core.service.args.GroupArgs;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Responses.Body;
import com.ujcms.util.web.exception.Http404Exception;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
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

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.util.query.QueryUtils.getQueryMap;

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
    public List<Group> list(@Nullable Short type, HttpServletRequest request) {
        GroupArgs args = GroupArgs.of(getQueryMap(request.getQueryString()))
                .type(type);
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @RequiresPermissions("group:show")
    public Group show(@PathVariable Integer id) {
        Group bean = service.select(id);
        if (bean == null) {
            throw new Http404Exception("Group not found. ID = " + id);
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