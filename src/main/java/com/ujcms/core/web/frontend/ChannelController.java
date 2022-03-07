package com.ujcms.core.web.frontend;

import com.ujcms.core.domain.Channel;
import com.ujcms.core.domain.Site;
import com.ofwise.util.web.exception.Http404Exception;
import com.ujcms.core.service.ChannelService;
import com.ujcms.core.support.Constants;
import com.ujcms.core.web.support.SiteResolver;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

import static com.ujcms.core.support.Frontends.*;
import static com.ujcms.core.support.UrlConstants.CHANNEL;

/**
 * 前台栏目 Controller
 *
 * @author PONY
 */
@Controller("frontendChannelController")
public class ChannelController {
    private ChannelService channelService;
    private SiteResolver siteResolver;

    public ChannelController(ChannelService channelService, SiteResolver siteResolver) {
        this.channelService = channelService;
        this.siteResolver = siteResolver;
    }

    @GetMapping({CHANNEL + "/{alias:[\\w-]+}", CHANNEL + "/{alias:[\\w-]+}_{page:[\\d]+}",
            "/{subDir:[\\w-]+}" + CHANNEL + "/{alias:[\\w-]+}",
            "/{subDir:[\\w-]+}" + CHANNEL + "/{alias:[\\w-]+}_{page:[\\d]+}"})
    public String channel(@PathVariable String alias, @PathVariable(required = false) String subDir,
                          @PathVariable(required = false) Integer page,
                          HttpServletRequest request, Map<String, Object> modelMap) {
        Site site = siteResolver.resolve(request, subDir);
        Channel channel = channelService.findBySiteIdAndAlias(site.getId(), alias);
        if (channel == null) {
            throw new Http404Exception("Channel not found: siteId=" + site.getId() + ", alias=" + alias);
        }
        modelMap.put("channel", channel);
        modelMap.put(PAGE, Constants.validPage(page));
        modelMap.put(PAGE_SIZE, channel.getExt().getPageSize());
        modelMap.put(PAGE_URL_RESOLVER, channel);
        return channel.getTemplate();
    }
}
