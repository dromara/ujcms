package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.DictType;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.DictTypeService;
import com.ujcms.cms.core.service.args.DictTypeArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.commons.web.Entities;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Responses.Body;
import com.ujcms.commons.web.exception.Http400Exception;
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
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_GLOBAL;
import static com.ujcms.cms.core.support.Contexts.getCurrentSiteId;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.cms.core.web.support.ValidUtils.dataInSite;
import static com.ujcms.cms.core.web.support.ValidUtils.globalPermission;
import static com.ujcms.commons.query.QueryUtils.getQueryMap;

/**
 * 字典类型 Controller
 *
 * @author PONY
 */
@RestController("backendDictTypeController")
@RequestMapping(BACKEND_API + "/core/dict-type")
public class DictTypeController {
    private final DictTypeService service;

    public DictTypeController(DictTypeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('dictType:list','*')")
    public Object list(HttpServletRequest request) {
        DictTypeArgs args = DictTypeArgs.of(getQueryMap(request.getQueryString()))
                .scopeSiteId(getCurrentSiteId());
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('dictType:show','*')")
    public Object show(@PathVariable Long id) {
        DictType bean = service.select(id);
        if (bean == null) {
            throw new Http400Exception(DictType.NOT_FOUND + id);
        }
        return bean;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('dictType:create','*')")
    @OperationLog(module = "dictType", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody @Valid DictType bean) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        DictType dictType = new DictType();
        Entities.copy(bean, dictType);
        validateBean(bean, false, null, user);
        service.insert(dictType, site.getId());
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('dictType:update','*')")
    @OperationLog(module = "dictType", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody @Valid DictType bean) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        DictType dictType = service.select(bean.getId());
        if (dictType == null) {
            throw new Http400Exception(DictType.NOT_FOUND + bean.getId());
        }
        boolean origGlobal = dictType.isGlobal();
        String origAlias = dictType.getAlias();
        Entities.copy(bean, dictType, "siteId");
        validateBean(dictType, origGlobal, origAlias, user);
        service.update(dictType, site.getId());
        return Responses.ok();
    }

    @PutMapping("order")
    @PreAuthorize("hasAnyAuthority('dictType:update','*')")
    @OperationLog(module = "dictType", operation = "updateOrder", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateOrder(@RequestBody Long[] ids) {
        Long siteId = Contexts.getCurrentSiteId();
        User user = Contexts.getCurrentUser();
        List<DictType> list = new ArrayList<>();
        for (Long id : ids) {
            DictType bean = service.select(id);
            if (bean == null) {
                throw new Http400Exception(DictType.NOT_FOUND + id);
            }
            dataInSite(bean.getSiteId(), siteId);
            globalPermission(bean.isGlobal(), user.hasGlobalPermission());
            list.add(bean);
        }
        service.updateOrder(list);
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('dictType:delete','*')")
    @OperationLog(module = "dictType", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids) {
        Long siteId = Contexts.getCurrentSiteId();
        User user = Contexts.getCurrentUser();
        for (Long id : ids) {
            DictType bean = service.select(id);
            if (bean == null) {
                throw new Http400Exception(DictType.NOT_FOUND + id);
            }
            dataInSite(bean.getSiteId(), siteId);
            globalPermission(bean.isGlobal(), user.hasGlobalPermission());
            service.delete(id);
        }
        return Responses.ok();
    }

    @GetMapping("alias-exist")
    @PreAuthorize("hasAnyAuthority('dictType:validation','*')")
    public boolean aliasExist(@NotBlank String alias, int scope) {
        Long siteId = SCOPE_GLOBAL != scope ? getCurrentSiteId() : null;
        return service.existsByAlias(alias, siteId);
    }

    private void validateBean(DictType bean, boolean origGlobal, @Nullable String origAlias, User currentUser) {
        dataInSite(bean.getSiteId(), getCurrentSiteId());
        globalPermission(origGlobal || bean.isGlobal(), currentUser.hasGlobalPermission());
        if (!bean.getAlias().equals(origAlias) && aliasExist(bean.getAlias(), bean.getScope())) {
            throw new Http400Exception("alias exist: " + bean.getAlias());
        }
    }
}