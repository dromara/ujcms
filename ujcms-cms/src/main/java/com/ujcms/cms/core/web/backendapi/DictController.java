package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Dict;
import com.ujcms.cms.core.domain.DictType;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.DictService;
import com.ujcms.cms.core.service.DictTypeService;
import com.ujcms.cms.core.service.args.DictArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.common.web.Entities;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http404Exception;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.cms.core.web.support.ValidUtils.dataInSite;
import static com.ujcms.cms.core.web.support.ValidUtils.globalPermission;
import static com.ujcms.common.query.QueryUtils.getQueryMap;

/**
 * 字典 Controller
 * <p>
 * 契约（dictionary plan）：条目随类型的可见范围（本站 ∪ 全局）；全局类型的条目属全局数据，
 * 需全局数据权限；{@code sys} 恒由系统骨架写入——系统条目 value/enabled 锁定且不可删；
 * 同一类型内 value 唯一。
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
        DictArgs args = DictArgs.of(getQueryMap(request.getQueryString()))
                .typeId(typeId).scopeSiteId(Contexts.getCurrentSiteId());
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

    @GetMapping("value-exist")
    @PreAuthorize("hasAnyAuthority('dict:validation','*')")
    public boolean valueExist(Long typeId, @NotBlank String value) {
        return service.existsByValue(typeId, value);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('dict:create','*')")
    @OperationLog(module = "dict", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody @Valid Dict bean) {
        User user = Contexts.getCurrentUser();
        Dict dict = new Dict();
        Entities.copy(bean, dict);
        // sys 由系统骨架写入，控制台恒不得设置
        dict.setSys(false);
        validateType(dict.getTypeId(), user);
        validateValue(dict.getTypeId(), dict.getValue(), null);
        service.insert(dict);
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('dict:update','*')")
    @OperationLog(module = "dict", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody @Valid Dict bean) {
        User user = Contexts.getCurrentUser();
        Dict dict = service.select(bean.getId());
        if (dict == null) {
            throw new Http404Exception(Dict.NOT_FOUND + bean.getId());
        }
        validateType(dict.getTypeId(), user);
        String origValue = dict.getValue();
        // typeId/sys 恒不可改；系统条目的 value/enabled 为系统状态，锁定；
        // order 只经排序接口改写：列表行内的启用开关回传整行，其 order 可能早于刚完成的拖拽排序
        String[] excludes = Boolean.TRUE.equals(dict.getSys())
                ? new String[]{"typeId", "sys", "order", "value", "enabled"}
                : new String[]{"typeId", "sys", "order"};
        Entities.copy(bean, dict, excludes);
        validateValue(dict.getTypeId(), dict.getValue(), origValue);
        service.update(dict);
        return Responses.ok();
    }

    @PutMapping("order")
    @PreAuthorize("hasAnyAuthority('dict:update','*')")
    @OperationLog(module = "dict", operation = "updateOrder", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateOrder(@RequestBody Long[] ids) {
        User user = Contexts.getCurrentUser();
        List<Dict> list = new ArrayList<>();
        for (Long id : ids) {
            Dict bean = service.select(id);
            if (bean == null) {
                throw new Http404Exception(Dict.NOT_FOUND + id);
            }
            validateType(bean.getTypeId(), user);
            list.add(bean);
        }
        service.updateOrder(list);
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('dict:delete','*')")
    @OperationLog(module = "dict", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids) {
        User user = Contexts.getCurrentUser();
        ids.forEach(id -> {
            Dict bean = service.select(id);
            if (bean == null) {
                throw new Http404Exception(Dict.NOT_FOUND + id);
            }
            validateType(bean.getTypeId(), user);
            // 系统条目由 DictService.delete 拒绝
            service.delete(id);
        });
        return Responses.ok();
    }

    /**
     * 写路径校验所属类型：须存在、属当前站点或全局；全局类型的条目属全局数据，需全局数据权限
     * （与 {@link DictTypeController} 对类型行的规则一致）。
     */
    private DictType validateType(Long typeId, User user) {
        DictType type = typeService.select(typeId);
        if (type == null) {
            throw new Http404Exception(DictType.NOT_FOUND + typeId);
        }
        dataInSite(type.getSiteId(), Contexts.getCurrentSiteId());
        globalPermission(type.isGlobal(), user.hasGlobalPermission());
        return type;
    }

    private void validateValue(Long typeId, String value, @Nullable String origValue) {
        if (!value.equals(origValue) && service.existsByValue(typeId, value)) {
            throw new Http400Exception("value exist: " + value);
        }
    }
}
