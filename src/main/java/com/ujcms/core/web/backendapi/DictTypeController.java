package com.ujcms.core.web.backendapi;

import com.ujcms.core.domain.DictType;
import com.ujcms.core.domain.Site;
import com.ujcms.core.domain.support.EntityConstants;
import com.ujcms.core.service.DictTypeService;
import com.ujcms.core.support.Contexts;
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
 * 字典类型 Controller
 *
 * @author PONY
 */
@RestController("backendDictTypeController")
@RequestMapping(BACKEND_API + "/core/dict-type")
public class DictTypeController {
    private DictTypeService service;

    public DictTypeController(DictTypeService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermissions("dictType:list")
    public Object list(HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        Map<String, Object> queryMap = EntityConstants.scopeSiteQuery(site, request.getQueryString());
        return service.selectList(queryMap);
    }

    @GetMapping("{id}")
    @RequiresPermissions("dictType:show")
    public Object show(@PathVariable Integer id) {
        DictType bean = service.select(id);
        if (bean == null) {
            return Responses.notFound("DictType not found. ID = " + id);
        }
        return bean;
    }

    @PostMapping
    @RequiresPermissions("dictType:create")
    public ResponseEntity<Body> create(@RequestBody DictType bean) {
        Entities.emptyToNull(bean);
        Site site = Contexts.getCurrentSite();
        bean.setSiteId(site.getId());
        service.insert(bean);
        return Responses.ok();
    }

    @PutMapping
    @RequiresPermissions("dictType:update")
    public ResponseEntity<Body> update(@RequestBody DictType bean) {
        DictType dictType = service.select(bean.getId());
        if (dictType == null) {
            return Responses.notFound("DictType not found. ID = " + bean.getId());
        }
        Entities.copy(bean, dictType, "siteId");
        service.update(dictType);
        return Responses.ok();
    }

    @PutMapping("order")
    @RequiresPermissions("dictType:update")
    public ResponseEntity<Body> updateOrder(@RequestBody Integer[] ids) {
        List<DictType> list = new ArrayList<>();
        for (Integer id : ids) {
            DictType bean = service.select(id);
            if (bean == null) {
                return Responses.notFound("DictType not found. ID = " + id);
            }
            list.add(bean);
        }
        service.updateOrder(list);
        return Responses.ok();
    }

    @DeleteMapping
    @RequiresPermissions("dictType:delete")
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids) {
        service.delete(ids);
        return Responses.ok();
    }
}