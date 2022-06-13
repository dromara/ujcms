package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.SiteBuffer;
import com.ujcms.util.web.exception.Http404Exception;
import com.ujcms.cms.core.service.SiteBufferService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.ujcms.cms.core.support.UrlConstants.API;

/**
 * 站点前台 接口
 *
 * @author PONY
 */
@RestController
@RequestMapping(API + "/site")
public class SiteController {
    private SiteBufferService bufferService;

    public SiteController(SiteBufferService bufferService) {
        this.bufferService = bufferService;
    }

    @GetMapping("/view/{id:[\\d]}")
    public long view(@PathVariable Integer id) {
        return bufferService.updateViews(id, 1);
    }

    @GetMapping("/buffer/{id:[\\d]}")
    public SiteBuffer buffer(@PathVariable Integer id) {
        SiteBuffer buffer = bufferService.select(id);
        if (buffer == null) {
            throw new Http404Exception("SiteBuffer not found. id=" + id);
        }
        return buffer;
    }
}
