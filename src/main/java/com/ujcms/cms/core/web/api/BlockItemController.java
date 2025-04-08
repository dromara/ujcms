package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.BlockItem;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.BlockItemService;
import com.ujcms.cms.core.web.directive.BlockItemListDirective;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.commons.query.QueryUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;

/**
 * 区块项前台 接口
 *
 * @author PONY
 */
@Tag(name = "区块项接口")
@RestController
@RequestMapping({API + "/block-item", FRONTEND_API + "/block-item"})
public class BlockItemController {
    private final SiteResolver siteResolver;
    private final BlockItemService service;

    public BlockItemController(SiteResolver siteResolver, BlockItemService service) {
        this.siteResolver = siteResolver;
        this.service = service;
    }

    @Operation(summary = "区块项列表_BlockItemList")
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "block", description = "区块别名",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "blockId", description = "区块ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "isAllSite", description = "是否获取所有站点区块项列表。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isEnabled", description = "是否启用。可选值：`all`(全部), `false`(禁用), `true`(启用)。默认值：启用",
            schema = @Schema(type = "string", allowableValues = {"all", "false", "true"}, defaultValue = "true"))
    @GetMapping
    public List<BlockItem> list(HttpServletRequest request) {
        Site site = siteResolver.resolve(request);
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        return BlockItemListDirective.query(params, site.getId(), service);
    }

}
