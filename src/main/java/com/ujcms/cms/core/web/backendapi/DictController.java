package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.Dict;
import com.ujcms.cms.core.service.DictService;
import com.ujcms.cms.core.service.args.DictArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.util.web.Entities;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Responses.Body;
import com.ujcms.util.web.exception.Http404Exception;
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

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.util.query.QueryUtils.getQueryMap;

/**
 * 字典 Controller
 *
 * @author PONY
 */
@RestController("backendDictController")
@RequestMapping(BACKEND_API + "/core/dict")
public class DictController {
    private DictService service;

    public DictController(DictService service) {
        this.service = service;
    }

    @GetMapping("list-by-alias")
    @RequiresPermissions("dict:list")
    public List<Dict> articleSourceList(String alias, String name) {
        Integer siteId = Contexts.getCurrentSiteId();
        return service.listByAlias(alias, name, siteId);
    }

    @GetMapping
    @RequiresPermissions("dict:list")
    public List<Dict> list(Integer typeId, HttpServletRequest request) {
        DictArgs args = DictArgs.of(getQueryMap(request.getQueryString()));
        args.typeId(typeId);
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @RequiresPermissions("dict:show")
    public Dict show(@PathVariable Integer id) {
        Dict bean = service.select(id);
        if (bean == null) {
            throw new Http404Exception("Dict not found. ID = " + id);
        }
        return bean;
    }

    @PostMapping
    @RequiresPermissions("dict:create")
    public ResponseEntity<Body> create(@RequestBody Dict bean) {
        Dict dict = new Dict();
        Entities.copy(bean, dict);
        service.insert(dict);
        return Responses.ok();
    }

    @PutMapping
    @RequiresPermissions("dict:update")
    public ResponseEntity<Body> update(@RequestBody Dict bean) {
        Dict dict = service.select(bean.getId());
        if (dict == null) {
            return Responses.notFound("Dict not found. ID = " + bean.getId());
        }
        Entities.copy(bean, dict);
        service.update(dict);
        return Responses.ok();
    }

    @PutMapping("order")
    @RequiresPermissions("dictType:update")
    public ResponseEntity<Body> updateOrder(@RequestBody Integer[] ids) {
        List<Dict> list = new ArrayList<>();
        for (Integer id : ids) {
            Dict bean = service.select(id);
            if (bean == null) {
                return Responses.notFound("Dict not found. ID = " + id);
            }
            list.add(bean);
        }
        service.updateOrder(list);
        return Responses.ok();
    }

    @DeleteMapping
    @RequiresPermissions("dict:delete")
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids) {
        service.delete(ids);
        return Responses.ok();
    }
}