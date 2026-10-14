package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.ResourceCategory;
import com.ujcms.cms.core.service.ResourceCategoryService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.ValidUtils;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http404Exception;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;

/**
 * 资源库分类 Controller
 *
 * @author PONY
 */
@RestController("backendResourceCategoryController")
@RequestMapping(BACKEND_API + "/core/resource-category")
public class ResourceCategoryController {
    private final ResourceCategoryService service;

    public ResourceCategoryController(ResourceCategoryService service) {
        this.service = service;
    }

    /**
     * 分类树。scope=my 返回当前用户的个人分类（我的资源库），其它/空返回共享分类树。
     * 浏览资源库（attachment:list）即需要分类树导航，故只读接口对附件列表权限同样放行；增删改仍受 resourceCategory:* 控制。
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('resourceCategory:list','attachment:list','*')")
    public Object list(@RequestParam(required = false) String scope) {
        Long ownerId = "my".equalsIgnoreCase(scope) ? Contexts.getCurrentUser().getId() : null;
        return service.selectTree(Contexts.getCurrentSiteId(), ownerId);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('resourceCategory:show','*')")
    public Object show(@PathVariable Long id) {
        ResourceCategory bean = service.select(id);
        if (bean == null) {
            throw new Http404Exception(ResourceCategory.NOT_FOUND + id);
        }
        ValidUtils.dataInSite(bean.getSiteId(), Contexts.getCurrentSiteId());
        return bean;
    }

    /**
     * 新建分类。scope=my 创建个人分类（owner_id_=当前用户），其它/空创建共享分类。
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('resourceCategory:create','*')")
    @OperationLog(module = "resourceCategory", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody @Valid ResourceCategory bean,
                                       @RequestParam(required = false) String scope) {
        Long siteId = Contexts.getCurrentSiteId();
        Long ownerId = "my".equalsIgnoreCase(scope) ? Contexts.getCurrentUser().getId() : null;
        ResourceCategory target = new ResourceCategory();
        target.setSiteId(siteId);
        target.setName(bean.getName());
        target.setOwnerId(ownerId);
        if (bean.getParentId() != null) {
            ResourceCategory parent = service.select(bean.getParentId());
            if (parent == null) {
                throw new Http404Exception(ResourceCategory.NOT_FOUND + bean.getParentId());
            }
            ValidUtils.dataInSite(parent.getSiteId(), siteId);
            // 子分类必须与上级分类同属一个作用域（共享/个人，且个人分类归属同一用户）
            if (!Objects.equals(parent.getOwnerId(), ownerId)) {
                throw new Http400Exception("parent category scope mismatch: " + bean.getParentId());
            }
            target.setParentId(parent.getId());
        }
        service.insert(target);
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('resourceCategory:update','*')")
    @OperationLog(module = "resourceCategory", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody @Valid ResourceCategory bean) {
        ResourceCategory target = service.select(bean.getId());
        if (target == null) {
            throw new Http404Exception(ResourceCategory.NOT_FOUND + bean.getId());
        }
        ValidUtils.dataInSite(target.getSiteId(), Contexts.getCurrentSiteId());
        checkOwner(target);
        // v1 仅支持重命名，父级/排序变更（move/reorder）延后
        target.setName(bean.getName());
        service.update(target);
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('resourceCategory:delete','*')")
    @OperationLog(module = "resourceCategory", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids) {
        Long siteId = Contexts.getCurrentSiteId();
        for (Long id : ids) {
            ResourceCategory bean = service.select(id);
            if (bean == null) {
                continue;
            }
            ValidUtils.dataInSite(bean.getSiteId(), siteId);
            checkOwner(bean);
            // 非空校验：含子分类、共享附件（共享分类）或收藏（个人分类）时不可删除
            if (service.countChildren(id) > 0 || service.countAttachments(id) > 0 || service.countFavorites(id) > 0) {
                throw new Http400Exception("resource category not empty: " + id);
            }
            service.delete(id);
        }
        return Responses.ok();
    }

    /**
     * 个人分类（owner_id_ 非空）仅允许其所有者操作；共享分类无此限制（由 resourceCategory 权限控制）。
     */
    private void checkOwner(ResourceCategory bean) {
        Long ownerId = bean.getOwnerId();
        if (ownerId != null && !ownerId.equals(Contexts.getCurrentUser().getId())) {
            throw new Http400Exception("personal resource category owned by another user: " + bean.getId());
        }
    }
}
