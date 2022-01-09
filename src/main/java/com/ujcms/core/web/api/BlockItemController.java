package com.ujcms.core.web.api;

import com.ujcms.core.domain.BlockItem;
import com.ujcms.core.service.BlockItemService;
import com.ujcms.core.service.GlobalService;
import com.ujcms.core.web.directive.BlockItemListDirective;
import com.ofwise.util.query.QueryUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

import static com.ujcms.core.support.UrlConstants.API;

/**
 * 区块项前台 接口
 *
 * @author PONY
 */
@RestController
@RequestMapping(API + "/block-item")
public class BlockItemController {
    private GlobalService globalService;
    private BlockItemService service;

    public BlockItemController(GlobalService globalService, BlockItemService service) {
        this.globalService = globalService;
        this.service = service;
    }

    @GetMapping
    public List<BlockItem> list(HttpServletRequest request) {
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        Integer defaultSiteId = globalService.getUnique().getDefaultSiteId();
        return BlockItemListDirective.query(params, defaultSiteId, service);
    }

}
