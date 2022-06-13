package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.ChannelBuffer;
import com.ujcms.cms.core.service.ChannelBufferService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.args.ChannelArgs;
import com.ujcms.cms.core.web.directive.ChannelListDirective;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.util.query.QueryUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.util.query.QueryUtils.QUERY_PREFIX;

/**
 * 栏目前台 接口
 *
 * @author PONY
 */
@RestController
@RequestMapping(API + "/channel")
public class ChannelController {
    private ConfigService configService;
    private ChannelService channelService;
    private ChannelBufferService bufferService;

    public ChannelController(ConfigService configService, ChannelService channelService,
                             ChannelBufferService bufferService) {
        this.configService = configService;
        this.channelService = channelService;
        this.bufferService = bufferService;
    }

    @GetMapping
    public List<Channel> list(HttpServletRequest request) {
        Integer defaultSiteId = configService.getUnique().getDefaultSiteId();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        ChannelArgs args = ChannelArgs.of(QueryUtils.getQueryMap(params, QUERY_PREFIX));
        ChannelListDirective.assemble(args, params, defaultSiteId);
        args.customsQueryMap(QueryUtils.getCustomsQueryMap(params));
        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);
        return channelService.selectList(args, offset, limit);
    }

    @GetMapping("/{id:[\\d]}")
    public Channel show(@PathVariable Integer id) {
        return channelService.select(id);
    }

    @GetMapping("/alias/{alias}")
    public Channel alias(@PathVariable String alias, Integer siteId) {
        if (siteId == null) {
            siteId = configService.getUnique().getDefaultSiteId();
        }
        return channelService.findBySiteIdAndAlias(siteId, alias);
    }

    @GetMapping("/view/{id:[\\d]}")
    public long view(@PathVariable Integer id) {
        return bufferService.updateViews(id, 1);
    }

    @GetMapping("/buffer/{id:[\\d]}")
    public ChannelBuffer buffer(@PathVariable Integer id) {
        return bufferService.select(id);
    }
}
