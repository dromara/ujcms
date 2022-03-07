package com.ujcms.core.web.backendapi;

import com.ujcms.core.domain.Model;
import com.ujcms.core.domain.Site;
import com.ujcms.core.domain.support.EntityConstants;
import com.ujcms.core.service.ModelService;
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
 * 模型 Controller
 *
 * @author PONY
 */
@RestController("backendModelController")
@RequestMapping(BACKEND_API + "/core/model")
public class ModelController {
    private ModelService service;

    public ModelController(ModelService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermissions("model:list")
    public Object list(HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        Map<String, Object> queryMap = EntityConstants.scopeSiteQuery(site, request.getQueryString());
        return service.selectList(queryMap);
    }

    @GetMapping("{id}")
    @RequiresPermissions("model:show")
    public Object show(@PathVariable Integer id) {
        Model bean = service.select(id);
        if (bean == null) {
            return Responses.notFound("Model not found. ID = " + id);
        }
        return bean;
    }

    @PostMapping
    @RequiresPermissions("model:create")
    public ResponseEntity<Body> create(@RequestBody Model bean) {
        Model model = new Model();
        Entities.copy(bean, model);
        model.setSiteId(Contexts.getCurrentSiteId());
        service.insert(model);
        return Responses.ok();
    }

    @PutMapping
    @RequiresPermissions("model:update")
    public ResponseEntity<Body> update(@RequestBody Model bean) {
        Model model = service.select(bean.getId());
        if (model == null) {
            return Responses.notFound("Model not found. ID = " + bean.getId());
        }
        Entities.copy(bean, model);
        service.update(model);
        return Responses.ok();
    }

    @PutMapping("order")
    @RequiresPermissions("model:update")
    public ResponseEntity<Body> updateOrder(@RequestBody Integer[] ids) {
        List<Model> list = new ArrayList<>();
        for (Integer id : ids) {
            Model bean = service.select(id);
            if (bean == null) {
                return Responses.notFound("Model not found. ID = " + id);
            }
            list.add(bean);
        }
        service.updateOrder(list);
        return Responses.ok();
    }

    @DeleteMapping
    @RequiresPermissions("model:delete")
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids) {
        service.delete(ids);
        return Responses.ok();
    }
}