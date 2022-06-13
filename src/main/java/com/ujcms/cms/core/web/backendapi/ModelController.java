package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.service.ModelService;
import com.ujcms.cms.core.service.args.ModelArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
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
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

import static com.ujcms.cms.core.support.Contexts.getCurrentSiteId;
import static com.ujcms.util.query.QueryUtils.getQueryMap;

/**
 * 模型 Controller
 *
 * @author PONY
 */
@RestController("backendModelController")
@RequestMapping(UrlConstants.BACKEND_API + "/core/model")
public class ModelController {
    private ModelService service;

    public ModelController(ModelService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermissions("model:list")
    public Object list(@Nullable String type, HttpServletRequest request) {
        ModelArgs args = ModelArgs.of(getQueryMap(request.getQueryString()))
                .scopeSiteId(getCurrentSiteId())
                .type(type);
        return service.selectList(args);
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
        Integer siteId = Contexts.getCurrentSiteId();
        Model model = new Model();
        Entities.copy(bean, model);
        service.insert(model, siteId);
        return Responses.ok();
    }

    @PutMapping
    @RequiresPermissions("model:update")
    public ResponseEntity<Body> update(@RequestBody Model bean) {
        Integer siteId = Contexts.getCurrentSiteId();
        Model model = service.select(bean.getId());
        if (model == null) {
            return Responses.notFound("Model not found. ID = " + bean.getId());
        }
        Entities.copy(bean, model, "siteId");
        service.update(model, siteId);
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