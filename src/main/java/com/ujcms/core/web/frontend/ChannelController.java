package com.ujcms.core.web.frontend;

import com.ujcms.core.domain.Channel;
import com.ujcms.core.domain.Site;
import com.ujcms.core.exception.Http404Exception;
import com.ujcms.core.service.ChannelService;
import com.ujcms.core.support.Frontends;
import com.ujcms.core.web.support.SiteResolver;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletRequest;

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

    @GetMapping({CHANNEL + "/{alias:[\\w-]+}", "/{subDir:[\\w-]+}" + CHANNEL + "/{alias:[\\w-]+}"})
    public String channel(@PathVariable String alias, @PathVariable(required = false) String subDir,
                          HttpServletRequest request, Model modelMap) {
        Site site = siteResolver.resolve(request, subDir);
        Channel channel = channelService.findBySiteIdAndAlias(site.getId(), alias);
        if (channel == null) {
            throw new Http404Exception("Channel not found: siteId=" + site.getId() + ", alias=" + alias);
        }
        modelMap.addAttribute("channel", channel);
        modelMap.addAttribute(Frontends.PAGE_SIZE, channel.getExt().getPageSize());
        String template = channel.getTemplate();
        if (template == null) {
            throw new Http404Exception("Channel template not set. id=" + channel.getId());
        }
        return template;
    }
}
