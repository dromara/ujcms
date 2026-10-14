package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.BlockItemService;
import com.ujcms.cms.core.service.BlockService;
import com.ujcms.cms.core.service.args.BlockArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.common.web.Entities;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.LogicException;
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
import java.util.Optional;

import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_GLOBAL;
import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_PRIVATE;
import static com.ujcms.cms.core.support.Contexts.getCurrentSiteId;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.cms.core.web.support.ValidUtils.dataInSite;
import static com.ujcms.cms.core.web.support.ValidUtils.globalPermission;
import static com.ujcms.common.query.QueryUtils.getQueryMap;

/**
 * 区块 Controller
 *
 * @author PONY
 */
@RestController("backendBlockController")
@RequestMapping(BACKEND_API + "/core/block")
public class BlockController {
    private final BlockItemService itemService;
    private final BlockService service;

    public BlockController(BlockItemService itemService, BlockService service) {
        this.itemService = itemService;
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('block:list','*')")
    public Object list(HttpServletRequest request) {
        Long siteId = getCurrentSiteId();
        BlockArgs args = BlockArgs.of(getQueryMap(request.getQueryString()))
                .scopeSiteId(siteId).queryItemCount(siteId);
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('block:show','*')")
    public Object show(@PathVariable Long id) {
        Block bean = service.select(id);
        if (bean == null) {
            return Responses.notFound(Block.NOT_FOUND + id);
        }
        dataInSite(bean.getSiteId(), getCurrentSiteId());
        return bean;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('block:create','*')")
    @OperationLog(module = "block", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody @Valid Block bean) {
        User currentUser = Contexts.getCurrentUser();
        Block block = new Block();
        Entities.copy(bean, block, "siteId");
        validateBean(block, false, null, null, null, currentUser);
        service.insert(block, getCurrentSiteId());
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('block:update','*')")
    @OperationLog(module = "block", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody @Valid Block bean) {
        User user = Contexts.getCurrentUser();
        Block block = service.select(bean.getId());
        if (block == null) {
            return Responses.notFound(Block.NOT_FOUND + bean.getId());
        }
        boolean origGlobal = block.isGlobal();
        String origAlias = block.getAlias();
        Short origScope = block.getScope();
        Long origModelId = block.getBlockItemModelId();
        // 来源三元组由种子导入器专管：编辑不得改写或清除
        Entities.copy(bean, block, "siteId", "origin", "originId", "originHash");
        validateBean(block, origGlobal, origAlias, origScope, origModelId, user);
        service.update(block, Contexts.getCurrentSiteId());
        return Responses.ok();
    }

    @PutMapping("order")
    @PreAuthorize("hasAnyAuthority('block:update','*')")
    @OperationLog(module = "block", operation = "updateOrder", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateOrder(@RequestBody Long[] ids) {
        User currentUser = Contexts.getCurrentUser();
        List<Block> list = new ArrayList<>();
        for (Long id : ids) {
            Block bean = service.select(id);
            if (bean == null) {
                return Responses.notFound(Block.NOT_FOUND + id);
            }
            validatePermission(bean.getSiteId(), bean.isGlobal(), currentUser);
            list.add(bean);
        }
        service.updateOrder(list);
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('block:delete','*')")
    @OperationLog(module = "block", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids) {
        User currentUser = Contexts.getCurrentUser();
        Long siteId = getCurrentSiteId();
        ids.forEach(id -> Optional.ofNullable(id).map(service::select).ifPresent(bean -> {
            validatePermission(bean.getSiteId(), bean.isGlobal(), currentUser);
            // 删除区块会级联其区块项，而全局区块的区块项分属各站：其它站点仍有区块项时不可删除（与“全局改本站私有”同规），
            // 否则在本站点一下删除，别的站点的内容跟着消失
            if (bean.isGlobal() && itemService.existsByBlockId(bean.getId(), siteId)) {
                throw new LogicException("error.block.usedByOtherSites", bean.getName());
            }
        }));
        service.delete(ids);
        return Responses.ok();
    }

    /**
     * 全局共享数据如果被其它站点使用，则不可以改为本站私有数据
     */
    @GetMapping("scope-not-allowed")
    @PreAuthorize("hasAnyAuthority('block:validation','*')")
    public boolean scopeNotAllowed(int scope, Long blockId) {
        Long siteId = getCurrentSiteId();
        return scope == SCOPE_PRIVATE && itemService.existsByBlockId(blockId, siteId);
    }

    @GetMapping("alias-exist")
    @PreAuthorize("hasAnyAuthority('block:validation','*')")
    public boolean aliasExist(@NotBlank String alias, int scope) {
        Long siteId = SCOPE_GLOBAL != scope ? getCurrentSiteId() : null;
        return service.existsByAlias(alias, siteId);
    }

    @GetMapping("categories")
    @PreAuthorize("hasAnyAuthority('block:list','*')")
    public List<String> categories() {
        return service.listCategories(getCurrentSiteId());
    }

    private void validatePermission(Long siteId, boolean isGlobal, User currentUser) {
        dataInSite(siteId, getCurrentSiteId());
        globalPermission(isGlobal, currentUser.hasGlobalPermission());
    }

    private void validateBean(Block block, boolean origGlobal, @Nullable String origAlias, @Nullable Short origScope,
                              @Nullable Long origModelId, User currentUser) {
        validatePermission(block.getSiteId(), block.isGlobal() || origGlobal, currentUser);
        // 共享范围未修改时不校验，避免存量数据不符合规则（如私有区块已被其它站点使用）导致无法保存
        if (!Objects.equals(block.getScope(), origScope) && scopeNotAllowed(block.getScope(), block.getId())) {
            throw new Http400Exception("scope not allowed " + block.getScope());
        }
        // 别名的唯一性以“别名 × 共享范围”为命名空间（全局一组、每站一组）：别名未变而共享范围变了，同样要重新校验
        boolean changed = !block.getAlias().equals(origAlias) || !Objects.equals(block.getScope(), origScope);
        if (changed && aliasExist(block.getAlias(), block.getScope())) {
            throw new Http400Exception("alias exist: " + block.getAlias());
        }
        // 区块项模型须对区块可见（本站区块：本站或全局；全局区块：全局）。绑定或共享范围未变时不校验，存量数据照常保存
        Long modelId = block.getBlockItemModelId();
        boolean bindingChanged = !Objects.equals(modelId, origModelId) || !Objects.equals(block.getScope(), origScope);
        if (modelId != null && bindingChanged) {
            service.validateModel(block.isGlobal() ? null : getCurrentSiteId(), modelId);
        }
    }

}