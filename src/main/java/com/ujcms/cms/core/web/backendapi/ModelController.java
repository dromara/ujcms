package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ModelService;
import com.ujcms.cms.core.service.args.ModelArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.commons.web.Entities;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Responses.Body;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
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
import static com.ujcms.cms.core.web.support.ValidUtils.dataInSite;
import static com.ujcms.cms.core.web.support.ValidUtils.globalPermission;
import static com.ujcms.commons.query.QueryUtils.getQueryMap;

/**
 * 模型 Controller
 *
 * @author PONY
 */
@RestController("backendModelController")
@RequestMapping(UrlConstants.BACKEND_API + "/core/model")
public class ModelController {
    private final ModelService service;

    public ModelController(ModelService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('model:list','*')")
    public Object list(@Nullable String type, HttpServletRequest request) {
        ModelArgs args = ModelArgs.of(getQueryMap(request.getQueryString()))
                .scopeSiteId(getCurrentSiteId())
                .type(type);
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('model:show','*')")
    public Object show(@PathVariable Long id) {
        User user = Contexts.getCurrentUser();
        Model bean = service.select(id);
        if (bean == null) {
            return Responses.notFound(Model.NOT_FOUND + id);
        }
        dataInSite(bean.getSiteId(), getCurrentSiteId());
        globalPermission(bean.isGlobal(), user.hasGlobalPermission());
        return bean;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('model:create','*')")
    @OperationLog(module = "model", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody Model bean) {
        Long siteId = Contexts.getCurrentSiteId();
        User user = Contexts.getCurrentUser();
        Model model = new Model();
        Entities.copy(bean, model);
        globalPermission(bean.isGlobal(), user.hasGlobalPermission());
        service.insert(model, siteId);
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('model:update','*')")
    @OperationLog(module = "model", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody Model bean) {
        Long siteId = Contexts.getCurrentSiteId();
        User user = Contexts.getCurrentUser();
        Model model = service.select(bean.getId());
        if (model == null) {
            return Responses.notFound(Model.NOT_FOUND + bean.getId());
        }
        boolean origGlobal = model.isGlobal();
        Entities.copy(bean, model, "siteId");
        dataInSite(model.getSiteId(), siteId);
        globalPermission(origGlobal || model.isGlobal(), user.hasGlobalPermission());
        service.update(model, siteId);
        return Responses.ok();
    }

    @PutMapping("order")
    @PreAuthorize("hasAnyAuthority('model:update','*')")
    @OperationLog(module = "model", operation = "updateOrder", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateOrder(@RequestBody Long[] ids) {
        Long siteId = Contexts.getCurrentSiteId();
        User user = Contexts.getCurrentUser();
        List<Model> list = new ArrayList<>();
        for (Long id : ids) {
            Model bean = service.select(id);
            if (bean == null) {
                return Responses.notFound(Model.NOT_FOUND + id);
            }
            dataInSite(bean.getSiteId(), siteId);
            globalPermission(bean.isGlobal(), user.hasGlobalPermission());
            list.add(bean);
        }
        service.updateOrder(list);
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('model:delete','*')")
    @OperationLog(module = "model", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids) {
        Long siteId = Contexts.getCurrentSiteId();
        User user = Contexts.getCurrentUser();
        for (Long id : ids) {
            Model bean = service.select(id);
            if (bean == null) {
                return Responses.notFound(Model.NOT_FOUND + id);
            }
            dataInSite(bean.getSiteId(), siteId);
            globalPermission(bean.isGlobal(), user.hasGlobalPermission());
            service.delete(id);
        }
        return Responses.ok();
    }
}