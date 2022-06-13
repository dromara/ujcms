package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.service.BlockItemService;
import com.ujcms.cms.core.domain.BlockItem;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.web.directive.BlockItemListDirective;
import com.ujcms.util.query.QueryUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

import static com.ujcms.cms.core.support.UrlConstants.API;

/**
 * 区块项前台 接口
 *
 * @author PONY
 */
@RestController
@RequestMapping(API + "/block-item")
public class BlockItemController {
    private ConfigService configService;
    private BlockItemService service;

    public BlockItemController(ConfigService configService, BlockItemService service) {
        this.configService = configService;
        this.service = service;
    }

    @GetMapping
    public List<BlockItem> list(HttpServletRequest request) {
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        Integer defaultSiteId = configService.getUnique().getDefaultSiteId();
        return BlockItemListDirective.query(params, defaultSiteId, service);
    }

}
