package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.Org;
import com.ujcms.cms.core.service.OrgService;
import com.ujcms.cms.core.service.args.OrgArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.util.web.Entities;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Responses.Body;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.util.query.QueryUtils.getQueryMap;

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
    public Object list(@RequestParam(defaultValue = "false") boolean current,
                       @Nullable Integer parentId,
                       HttpServletRequest request) {
        if (current && parentId == null) {
            parentId = Contexts.getCurrentSite().getOrgId();
        }
        OrgArgs args = OrgArgs.of(getQueryMap(request.getQueryString())).parentId(parentId);
        return service.selectList(args);
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
        Org org = new Org();
        Entities.copy(bean, org);
        service.insert(org);
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