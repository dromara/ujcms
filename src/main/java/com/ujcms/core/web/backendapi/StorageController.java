package com.ujcms.core.web.backendapi;

import com.ujcms.core.domain.Site;
import com.ujcms.core.domain.Storage;
import com.ujcms.core.service.StorageService;
import com.ujcms.core.support.Contexts;
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
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.ujcms.core.support.UrlConstants.BACKEND_API;

/**
 * 存储 Controller
 *
 * @author PONY
 */
@RestController("backendStorageController")
@RequestMapping(BACKEND_API + "/core/storage")
public class StorageController {
    private StorageService service;

    public StorageController(StorageService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermissions("storage:list")
    public Object list(HttpServletRequest request) {
        Map<String, Object> queryMap = QueryUtils.getQueryMap(request.getQueryString());
        return service.selectList(queryMap);
    }

    @GetMapping("{id}")
    @RequiresPermissions("storage:show")
    public Object show(@PathVariable Integer id) {
        Storage bean = service.select(id);
        if (bean == null) {
            return Responses.notFound("Storage not found. ID = " + id);
        }
        return bean;
    }

    @PostMapping
    @RequiresPermissions("storage:create")
    public ResponseEntity<Body> create(@RequestBody @Valid Storage bean) {
        Site site = Contexts.getCurrentSite();
        bean.setSiteId(site.getId());
        service.insert(bean);
        return Responses.ok();
    }

    @PutMapping
    @RequiresPermissions("storage:update")
    public ResponseEntity<Body> update(@RequestBody @Valid Storage bean) {
        Storage storage = service.select(bean.getId());
        if (storage == null) {
            return Responses.notFound("Storage not found. ID = " + bean.getId());
        }
        Entities.copy(bean, storage, "siteId");
        service.update(storage);
        return Responses.ok();
    }

    @PutMapping("order")
    @RequiresPermissions("storage:update")
    public ResponseEntity<Body> updateOrder(@RequestBody Integer[] ids) {
        List<Storage> list = new ArrayList<>();
        for (Integer id : ids) {
            Storage bean = service.select(id);
            if (bean == null) {
                return Responses.notFound("Storage not found. ID = " + id);
            }
            list.add(bean);
        }
        service.updateOrder(list);
        return Responses.ok();
    }

    @DeleteMapping
    @RequiresPermissions("storage:delete")
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids) {
        service.delete(ids);
        return Responses.ok();
    }
}