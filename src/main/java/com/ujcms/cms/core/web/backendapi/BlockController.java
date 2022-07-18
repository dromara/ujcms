package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.BlockItemService;
import com.ujcms.cms.core.service.BlockService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.service.args.BlockArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.util.web.Entities;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Responses.Body;
import com.ujcms.util.web.exception.Http400Exception;
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
import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_GLOBAL;
import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_PRIVATE;
import static com.ujcms.cms.core.support.Contexts.getCurrentSiteId;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.cms.core.web.support.Validations.dataInSite;
import static com.ujcms.cms.core.web.support.Validations.globalPermission;
import static com.ujcms.util.query.QueryUtils.getQueryMap;

/**
 * 区块 Controller
 *
 * @author PONY
 */
@RestController("backendBlockController")
@RequestMapping(BACKEND_API + "/core/block")
public class BlockController {
    private UserService userService;
    private BlockItemService itemService;
    private BlockService service;

    public BlockController(UserService userService, BlockItemService itemService, BlockService service) {
        this.userService = userService;
        this.itemService = itemService;
        this.service = service;
    }

    @GetMapping
    @RequiresPermissions("block:list")
    public Object list(HttpServletRequest request) {
        BlockArgs args = BlockArgs.of(getQueryMap(request.getQueryString()))
                .scopeSiteId(getCurrentSiteId());
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @RequiresPermissions("block:show")
    public Object show(@PathVariable Integer id) {
        Block bean = service.select(id);
        if (bean == null) {
            return Responses.notFound("Block not found. ID = " + id);
        }
        dataInSite(bean.getSiteId(), getCurrentSiteId());
        return bean;
    }

    @PostMapping
    @RequiresPermissions("block:create")
    public ResponseEntity<Body> create(@RequestBody Block bean) {
        User currentUser = Contexts.getCurrentUser(userService);
        Block block = new Block();
        Entities.copy(bean, block, "siteId");
        validateBean(block, false, null, currentUser);
        service.insert(block, getCurrentSiteId());
        return Responses.ok();
    }

    @PutMapping
    @RequiresPermissions("block:update")
    public ResponseEntity<Body> update(@RequestBody Block bean) {
        User user = Contexts.getCurrentUser(userService);
        Block block = service.select(bean.getId());
        if (block == null) {
            return Responses.notFound("Block not found. ID = " + bean.getId());
        }
        boolean origGlobal = bean.isGlobal();
        String origAlias = bean.getAlias();
        Entities.copy(bean, block, "siteId");
        validateBean(block, origGlobal, origAlias, user);
        service.update(block, getCurrentSiteId());
        return Responses.ok();
    }

    @PutMapping("order")
    @RequiresPermissions("block:update")
    public ResponseEntity<Body> updateOrder(@RequestBody Integer[] ids) {
        User currentUser = Contexts.getCurrentUser(userService);
        List<Block> list = new ArrayList<>();
        for (Integer id : ids) {
            Block bean = service.select(id);
            if (bean == null) {
                return Responses.notFound("Block not found. ID = " + id);
            }
            validatePermission(bean.getSiteId(), bean.isGlobal(), currentUser);
            list.add(bean);
        }
        service.updateOrder(list);
        return Responses.ok();
    }

    @DeleteMapping
    @RequiresPermissions("block:delete")
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids) {
        User currentUser = Contexts.getCurrentUser(userService);
        ids.forEach(id -> Optional.ofNullable(id).map(service::select).ifPresent(
                bean -> validatePermission(bean.getSiteId(), bean.isGlobal(), currentUser)));
        service.delete(ids);
        return Responses.ok();
    }

    /**
     * 全局共享数据如果被其它站点使用，则不可以改为本站私有数据
     */
    @GetMapping("scope-not-allowed")
    @RequiresPermissions("block:validation")
    public boolean scopeNotAllowed(int scope, Integer blockId) {
        Integer siteId = getCurrentSiteId();
        return scope == SCOPE_PRIVATE && itemService.existsByBlockId(blockId, siteId);
    }

    @GetMapping("alias-exist")
    @RequiresPermissions("block:validation")
    public boolean aliasExist(@NotBlank String alias, int scope) {
        Integer siteId = SCOPE_GLOBAL != scope ? getCurrentSiteId() : null;
        return service.existsByAlias(alias, siteId);
    }

    private void validatePermission(Integer siteId, boolean isGlobal, User currentUser) {
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