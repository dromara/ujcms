package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.BlockItem;
import com.ujcms.cms.core.service.BlockItemService;
import com.ujcms.cms.core.service.args.BlockItemArgs;
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
import java.util.ArrayList;
import java.util.List;

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.util.query.QueryUtils.getQueryMap;

/**
 * 区块项 Controller
 *
 * @author PONY
 */
@RestController("backendBlockItemController")
@RequestMapping(BACKEND_API + "/core/block-item")
public class BlockItemController {
    private BlockItemService service;

    public BlockItemController(BlockItemService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermissions("blockItem:list")
    public Object list(@Nullable Integer blockId, HttpServletRequest request) {
        BlockItemArgs args = BlockItemArgs.of(getQueryMap(request.getQueryString()))
                .siteId(Contexts.getCurrentSiteId())
                .blockId(blockId);
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @RequiresPermissions("blockItem:show")
    public Object show(@PathVariable Integer id) {
        BlockItem bean = service.select(id);
        if (bean == null) {
            return Responses.notFound("BlockItem not found. ID = " + id);
        }
        return bean;
    }

    @PostMapping
    @RequiresPermissions("blockItem:create")
    public ResponseEntity<Body> create(@RequestBody BlockItem bean) {
        validateBean(bean);
        BlockItem blockItem = new BlockItem();
        Entities.copy(bean, blockItem);
        blockItem.setSiteId(Contexts.getCurrentSiteId());
        service.insert(blockItem);
        return Responses.ok();
    }

    @PutMapping
    @RequiresPermissions("blockItem:update")
    public ResponseEntity<Body> update(@RequestBody BlockItem bean) {
        BlockItem blockItem = service.select(bean.getId());
        if (blockItem == null) {
            return Responses.notFound("BlockItem not found. ID = " + bean.getId());
        }
        Entities.copy(bean, blockItem);
        service.update(blockItem);
        return Responses.ok();
    }

    @PutMapping("order")
    @RequiresPermissions("block:update")
    public ResponseEntity<Body> updateOrder(@RequestBody Integer[] ids) {
        List<BlockItem> list = new ArrayList<>();
        for (Integer id : ids) {
            BlockItem bean = service.select(id);
            if (bean == null) {
                return Responses.notFound("BlockItem not found. ID = " + id);
            }
            list.add(bean);
        }
        service.updateOrder(list);
        return Responses.ok();
    }

    @DeleteMapping
    @RequiresPermissions("blockItem:delete")
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids) {
        service.delete(ids);
        return Responses.ok();
    }

    private void validateBean(BlockItem bean) {
        if (bean.getArticleId() != null
                && service.existsByBlockId(bean.getBlockId(), bean.getArticleId())) {
            throw new Http400Exception("BlockItem duplicate: blockId=" + bean.getBlockId()
                    + ", articleId=" + bean.getArticleId());
        }
    }
}