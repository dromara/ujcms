package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.SiteBuffer;
import com.ujcms.util.web.exception.Http404Exception;
import com.ujcms.cms.core.service.SiteBufferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;

/**
 * 站点前台 接口
 *
 * @author PONY
 */
@Tag(name = "SiteController", description = "站点接口")
@RestController
@RequestMapping({API + "/site", FRONTEND_API + "/site"})
public class SiteController {
    private final SiteBufferService bufferService;

    public SiteController(SiteBufferService bufferService) {
        this.bufferService = bufferService;
    }

    @Operation(summary = "获取网站浏览次数")
    @GetMapping("/view/{id:[\\d]+}")
    public long view(@Parameter(description = "站点ID") @PathVariable Integer id) {
        return bufferService.updateViews(id, 1);
    }

    @Operation(summary = "获取站点缓冲对象")
    @GetMapping("/buffer/{id:[\\d]+}")
    public SiteBuffer buffer(@Parameter(description = "站点ID") @PathVariable Integer id) {
        SiteBuffer buffer = bufferService.select(id);
        if (buffer == null) {
            throw new Http404Exception("SiteBuffer not found. id=" + id);
        }
        return buffer;
    }
}
