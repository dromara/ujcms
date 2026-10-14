package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.DictType;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.DictTypeService;
import com.ujcms.cms.core.service.args.DictTypeArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.common.web.Entities;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http404Exception;
import org.springframework.http.ResponseEntity;
import org.jspecify.annotations.Nullable;
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
import java.util.Objects;

import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_GLOBAL;
import static com.ujcms.cms.core.support.Contexts.getCurrentSiteId;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.cms.core.web.support.ValidUtils.dataInSite;
import static com.ujcms.cms.core.web.support.ValidUtils.globalPermission;
import static com.ujcms.common.query.QueryUtils.getQueryMap;

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
                .scopeSiteId(getCurrentSiteId()).queryEntryCount();
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('dictType:show','*')")
    public Object show(@PathVariable Long id) {
        DictType bean = service.select(id);
        if (bean == null) {
            throw new Http404Exception(DictType.NOT_FOUND + id);
        }
        return bean;
    }

    /**
     * 绑定该类型的模型自定义字段。控制台据此说明类型“被谁使用”、为何不可删除或不可改为本站私有。
     * 与类型列表同一权限：名称属于本页必需的只读信息。
     */
    @GetMapping("{id}/usages")
    @PreAuthorize("hasAnyAuthority('dictType:list','*')")
    public List<UsageResult> usages(@PathVariable Long id) {
        DictType bean = service.select(id);
        if (bean == null) {
            throw new Http404Exception(DictType.NOT_FOUND + id);
        }
        Long siteId = getCurrentSiteId();
        dataInSite(bean.getSiteId(), siteId);
        return service.listBindings(id).stream().map(binding -> new UsageResult(
                binding.model().getId(), binding.model().getName(),
                binding.field().getCode(), binding.field().getName(),
                isExternal(binding.model(), siteId))).toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('dictType:create','*')")
    @OperationLog(module = "dictType", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody @Valid DictType bean) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        DictType dictType = new DictType();
        Entities.copy(bean, dictType);
        // sys 由系统骨架写入，控制台恒不得设置
        dictType.setSys(false);
        validateBean(bean, false, null, null, user);
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
            throw new Http404Exception(DictType.NOT_FOUND + bean.getId());
        }
        boolean origGlobal = dictType.isGlobal();
        String origAlias = dictType.getAlias();
        Short origScope = dictType.getScope();
        // 来源三元组由种子导入器专管（model-seeding D8）：控制台编辑不得改写；
        // sys/dataType 创建后恒不可改；系统类型的 alias/scope 为系统契约（按别名取用），锁定；
        // order 只经排序接口改写：编辑表单回传的是打开时的旧值，会把其后的排序改回去
        String[] excludes = Boolean.TRUE.equals(dictType.getSys())
                ? new String[]{"siteId", "sys", "dataType", "order", "alias", "scope", "origin", "originId", "originHash"}
                : new String[]{"siteId", "sys", "dataType", "order", "origin", "originId", "originHash"};
        Entities.copy(bean, dictType, excludes);
        validateBean(dictType, origGlobal, origAlias, origScope, user);
        // 全局类型改为本站私有：其它站点的模型、全局模型仍绑定它时不允许——改后它们的字段即无选项可选
        if (origGlobal && !dictType.isGlobal()
                && service.listBindings(dictType.getId()).stream().anyMatch(it -> isExternal(it.model(), site.getId()))) {
            throw new Http400Exception("scope not allowed: bound by models outside the current site");
        }
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
                throw new Http404Exception(DictType.NOT_FOUND + id);
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
                throw new Http404Exception(DictType.NOT_FOUND + id);
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

    /**
     * 别名的唯一性以“别名 × 共享范围”为命名空间（全局一组、每站一组）：别名未变而共享范围变了，同样要重新校验
     */
    private void validateBean(DictType bean, boolean origGlobal, @Nullable String origAlias,
                              @Nullable Short origScope, User currentUser) {
        dataInSite(bean.getSiteId(), getCurrentSiteId());
        globalPermission(origGlobal || bean.isGlobal(), currentUser.hasGlobalPermission());
        boolean changed = !bean.getAlias().equals(origAlias) || !Objects.equals(bean.getScope(), origScope);
        if (changed && aliasExist(bean.getAlias(), bean.getScope())) {
            throw new Http400Exception("alias exist: " + bean.getAlias());
        }
    }

    /**
     * 绑定方是否在当前站点之外：全局模型（各站共用），或属于其它站点的模型
     */
    private static boolean isExternal(Model model, Long siteId) {
        return model.isGlobal() || !Objects.equals(model.getSiteId(), siteId);
    }

    /**
     * 字典类型的一处使用：模型的某个自定义字段。{@code external} 为 true 表示绑定方在当前站点之外
     */
    public record UsageResult(Long modelId, String modelName, String fieldCode, String fieldName, boolean external) {
    }
}