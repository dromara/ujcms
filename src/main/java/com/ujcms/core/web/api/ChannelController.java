package com.ujcms.core.web.api;

import com.ujcms.core.domain.Channel;
import com.ujcms.core.domain.ChannelBuffer;
import com.ujcms.core.service.ChannelBufferService;
import com.ujcms.core.service.ChannelService;
import com.ujcms.core.service.GlobalService;
import com.ujcms.core.web.directive.ChannelListDirective;
import com.ujcms.core.web.support.Directives;
import com.ofwise.util.query.QueryUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

import static com.ujcms.core.support.UrlConstants.API;
import static com.ofwise.util.query.QueryUtils.QUERY_PREFIX;

/**
 * 栏目前台 接口
 *
 * @author PONY
 */
@RestController
@RequestMapping(API + "/channel")
public class ChannelController {
    private GlobalService globalService;
    private ChannelService channelService;
    private ChannelBufferService bufferService;

    public ChannelController(GlobalService globalService, ChannelService channelService,
                             ChannelBufferService bufferService) {
        this.globalService = globalService;
        this.channelService = channelService;
        this.bufferService = bufferService;
    }

    @GetMapping
    public List<Channel> list(HttpServletRequest request) {
        Integer defaultSiteId = globalService.getUnique().getDefaultSiteId();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        Map<String, Object> queryMap = QueryUtils.getQueryMap(params, QUERY_PREFIX);
        ChannelListDirective.assemble(queryMap, params, defaultSiteId);
        Map<String, String> customsQueryMap = QueryUtils.getCustomsQueryMap(params);
        Integer offset = Directives.findOffset(params);
        Integer limit = Directives.findLimit(params);
        return channelService.selectList(queryMap, customsQueryMap, offset, limit);
    }

    @GetMapping("/{id}")
    public Channel show(@PathVariable Integer id) {
        return channelService.select(id);
    }

    @GetMapping("/alias/{alias}")
    public Channel alias(@PathVariable String alias, Integer siteId) {
        if (siteId == null) {
            siteId = globalService.getUnique().getDefaultSiteId();
        }
        return channelService.findBySiteIdAndAlias(siteId, alias);
    }

    @GetMapping("/view/{id}")
    public long view(@PathVariable Integer id) {
        return bufferService.updateViews(id, 1);
    }

    @GetMapping("/buffer/{id}")
    public ChannelBuffer buffer(@PathVariable Integer id) {
        return bufferService.select(id);
    }
}
