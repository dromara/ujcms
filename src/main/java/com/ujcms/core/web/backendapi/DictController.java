package com.ujcms.core.web.backendapi;

import com.ujcms.core.domain.Dict;
import com.ujcms.core.service.DictService;
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

    @GetMapping
    @RequiresPermissions("dict:list")
    public Object list(Integer typeId, HttpServletRequest request) {
        Map<String, Object> queryMap = QueryUtils.getQueryMap(request.getQueryString());
        if (typeId != null) {
            queryMap.put("EQ_typeId_Int", typeId.toString());
        }
        return service.selectList(queryMap);
    }

    @GetMapping("{id}")
    @RequiresPermissions("dict:show")
    public Object show(@PathVariable Integer id) {
        Dict bean = service.select(id);
        if (bean == null) {
            return Responses.notFound("Dict not found. ID = " + id);
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