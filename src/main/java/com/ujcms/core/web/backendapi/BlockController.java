package com.ujcms.core.web.backendapi;

import com.ujcms.core.domain.Block;
import com.ujcms.core.domain.Site;
import com.ujcms.core.domain.support.EntityConstants;
import com.ujcms.core.service.BlockService;
import com.ujcms.core.support.Contexts;
import com.ofwise.util.web.Entities;
import com.ofwise.util.web.Responses;
import com.ofwise.util.web.Responses.Body;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.ResponseEntity;
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
import java.util.Map;

import static com.ujcms.core.support.UrlConstants.BACKEND_API;

/**
 * 区块 Controller
 *
 * @author PONY
 */
@RestController("backendBlockController")
@RequestMapping(BACKEND_API + "/core/block")
public class BlockController {
    private BlockService service;

    public BlockController(BlockService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermissions("block:list")
    public Object list(HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        Map<String, Object> queryMap = EntityConstants.scopeSiteQuery(site, request.getQueryString());
        return service.selectList(queryMap);
    }

    @GetMapping("{id}")
    @RequiresPermissions("block:show")
    public Object show(@PathVariable Integer id) {
        Block bean = service.select(id);
        if (bean == null) {
            return Responses.notFound("Block not found. ID = " + id);
        }
        return bean;
    }

    @PostMapping
    @RequiresPermissions("block:create")
    public ResponseEntity<Body> create(@RequestBody Block bean) {
        Block block = new Block();
        Entities.copy(bean, block);
        block.setSiteId(Contexts.getCurrentSiteId());
        service.insert(block);
        return Responses.ok();
    }

    @PutMapping
    @RequiresPermissions("block:update")
    public ResponseEntity<Body> update(@RequestBody Block bean) {
        Block block = service.select(bean.getId());
        if (block == null) {
            return Responses.notFound("Block not found. ID = " + bean.getId());
        }
        Entities.copy(bean, block);
        service.update(block);
        return Responses.ok();
    }

    @PutMapping("order")
    @RequiresPermissions("block:update")
    public ResponseEntity<Body> updateOrder(@RequestBody Integer[] ids) {
        List<Block> list = new ArrayList<>();
        for (Integer id : ids) {
            Block bean = service.select(id);
            if (bean == null) {
                return Responses.notFound("Block not found. ID = " + id);
            }
            list.add(bean);
        }
        service.updateOrder(list);
        return Responses.ok();
    }


    @DeleteMapping
    @RequiresPermissions("block:delete")
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids) {
        service.delete(ids);
        return Responses.ok();
    }
}