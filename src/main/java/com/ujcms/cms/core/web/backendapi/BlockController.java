package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.BlockItemService;
import com.ujcms.cms.core.service.BlockService;
import com.ujcms.cms.core.service.args.BlockArgs;
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
import java.util.Optional;

import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_GLOBAL;
import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_PRIVATE;
import static com.ujcms.cms.core.support.Contexts.getCurrentSiteId;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.cms.core.web.support.ValidUtils.dataInSite;
import static com.ujcms.cms.core.web.support.ValidUtils.globalPermission;
import static com.ujcms.commons.query.QueryUtils.getQueryMap;

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
        BlockArgs args = BlockArgs.of(getQueryMap(request.getQueryString()))
                .scopeSiteId(getCurrentSiteId());
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
        validateBean(block, false, null, currentUser);
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
        Entities.copy(bean, block, "siteId");
        validateBean(block, origGlobal, origAlias, user);
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
        ids.forEach(id -> Optional.ofNullable(id).map(service::select).ifPresent(
                bean -> validatePermission(bean.getSiteId(), bean.isGlobal(), currentUser)));
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

    private void validatePermission(Long siteId, boolean isGlobal, User currentUser) {
        dataInSite(siteId, getCurrentSiteId());
        globalPermission(isGlobal, currentUser.hasGlobalPermission());
    }

    private void validateBean(Block block, boolean origGlobal, @Nullable String origAlias, User currentUser) {
        validatePermission(block.getSiteId(), block.isGlobal() || origGlobal, currentUser);
        if (scopeNotAllowed(block.getScope(), block.getId())) {
            throw new Http400Exception("scope not allowed " + block.getScope());
        }
        if (!block.getAlias().equals(origAlias) && aliasExist(block.getAlias(), block.getScope())) {
            throw new Http400Exception("alias exist: " + block.getAlias());
        }
    }

}