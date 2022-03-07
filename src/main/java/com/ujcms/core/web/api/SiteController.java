package com.ujcms.core.web.api;

import com.ujcms.core.domain.SiteBuffer;
import com.ofwise.util.web.exception.Http404Exception;
import com.ujcms.core.service.SiteBufferService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.ujcms.core.support.UrlConstants.API;

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

    @GetMapping("/view/{id}")
    public long view(@PathVariable Integer id) {
        return bufferService.updateViews(id, 1);
    }

    @GetMapping("/buffer/{id}")
    public SiteBuffer buffer(@PathVariable Integer id) {
        SiteBuffer buffer = bufferService.select(id);
        if (buffer == null) {
            throw new Http404Exception("SiteBuffer not found. id=" + id);
        }
        return buffer;
    }
}
