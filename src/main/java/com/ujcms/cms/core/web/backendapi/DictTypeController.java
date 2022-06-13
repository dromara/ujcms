package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.DictType;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.DictTypeService;
import com.ujcms.cms.core.service.args.DictTypeArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.util.web.Entities;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Responses.Body;
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
import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_GLOBAL;
import static com.ujcms.cms.core.support.Contexts.getCurrentSiteId;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.util.query.QueryUtils.getQueryMap;

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
        DictTypeArgs args = DictTypeArgs.of(getQueryMap(request.getQueryString()))
                .scopeSiteId(getCurrentSiteId());
        return service.selectList(args);
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
        Site site = Contexts.getCurrentSite();
        DictType dictType = new DictType();
        Entities.copy(bean, dictType);
        service.insert(dictType, site.getId());
        return Responses.ok();
    }

    @PutMapping
    @RequiresPermissions("dictType:update")
    public ResponseEntity<Body> update(@RequestBody DictType bean) {
        Site site = Contexts.getCurrentSite();
        DictType dictType = service.select(bean.getId());
        if (dictType == null) {
            return Responses.notFound("DictType not found. ID = " + bean.getId());
        }
        Entities.copy(bean, dictType, "siteId");
        service.update(dictType, site.getId());
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

    @GetMapping("alias-exist")
    @RequiresPermissions("dictType:validation")
    public boolean aliasExist(@NotBlank String alias, int scope) {
        Integer siteId = SCOPE_GLOBAL != scope ? getCurrentSiteId() : null;
        return service.existsByAlias(alias, siteId);
    }
}