package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Dict;
import com.ujcms.cms.core.domain.DictType;
import com.ujcms.cms.core.service.DictService;
import com.ujcms.cms.core.service.DictTypeService;
import com.ujcms.cms.core.service.args.DictArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.ValidUtils;
import com.ujcms.commons.web.Entities;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Responses.Body;
import com.ujcms.commons.web.exception.Http400Exception;
import com.ujcms.commons.web.exception.Http404Exception;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.commons.query.QueryUtils.getQueryMap;

/**
 * 字典 Controller
 *
 * @author PONY
 */
@RestController("backendDictController")
@RequestMapping(BACKEND_API + "/core/dict")
public class DictController {
    private final DictService service;
    private final DictTypeService typeService;

    public DictController(DictService service, DictTypeService typeService) {
        this.service = service;
        this.typeService = typeService;
    }

    @GetMapping("list-by-alias")
    @PreAuthorize("hasAnyAuthority('dict:list','*')")
    public List<Dict> listByAlias(String alias, String name) {
        Long siteId = Contexts.getCurrentSiteId();
        return service.listByAlias(alias, name, siteId);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('dict:list','*')")
    public List<Dict> list(Long typeId, HttpServletRequest request) {
        DictArgs args = DictArgs.of(getQueryMap(request.getQueryString())).typeId(typeId);
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('dict:show','*')")
    public Dict show(@PathVariable Long id) {
        Dict bean = service.select(id);
        if (bean == null) {
            throw new Http404Exception(Dict.NOT_FOUND + id);
        }
        return bean;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('dict:create','*')")
    @OperationLog(module = "dict", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody @Valid Dict bean) {
        Dict dict = new Dict();
        Entities.copy(bean, dict);
        validateBean(dict.getTypeId());
        service.insert(dict);
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('dict:update','*')")
    @OperationLog(module = "dict", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody @Valid Dict bean) {
        Dict dict = service.select(bean.getId());
        if (dict == null) {
            throw new Http400Exception(Dict.NOT_FOUND + bean.getId());
        }
        validateBean(dict.getTypeId());
        Entities.copy(bean, dict);
        service.update(dict);
        return Responses.ok();
    }

    @PutMapping("order")
    @PreAuthorize("hasAnyAuthority('dictType:update','*')")
    @OperationLog(module = "dict", operation = "updateOrder", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateOrder(@RequestBody Long[] ids) {
        List<Dict> list = new ArrayList<>();
        for (Long id : ids) {
            Dict bean = service.select(id);
            if (bean == null) {
                throw new Http400Exception(Dict.NOT_FOUND + id);
            }
            validateBean(bean.getTypeId());
            list.add(bean);
        }
        service.updateOrder(list);
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('dict:delete','*')")
    @OperationLog(module = "dict", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids) {
        ids.forEach(id -> {
            Dict bean = service.select(id);
            if (bean == null) {
                throw new Http400Exception(Dict.NOT_FOUND + id);
            }
            validateBean(bean.getTypeId());
            service.delete(id);
        });
        return Responses.ok();
    }

    private void validateBean(Long typeId) {
        DictType type = typeService.select(typeId);
        if (type == null) {
            throw new Http400Exception(DictType.NOT_FOUND + typeId);
        }
        ValidUtils.dataInSite(type.getSiteId(), Contexts.getCurrentSiteId());
    }
}