package com.ujcms.cms.core.web.frontend;

import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Group;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.GroupService;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.util.web.exception.Http401Exception;
import com.ujcms.util.web.exception.Http403Exception;
import com.ujcms.util.web.exception.Http404Exception;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

import static com.ujcms.cms.core.support.Frontends.*;
import static com.ujcms.cms.core.support.UrlConstants.CHANNEL;

/**
 * 前台栏目 Controller
 *
 * @author PONY
 */
@Controller("frontendChannelController")
public class ChannelController {
    private final ChannelService channelService;
    private final GroupService groupService;
    private final SiteResolver siteResolver;

    public ChannelController(ChannelService channelService, GroupService groupService, SiteResolver siteResolver) {
        this.channelService = channelService;
        this.groupService = groupService;
        this.siteResolver = siteResolver;
    }

    @GetMapping({CHANNEL + "/{alias:[\\w-]+}", CHANNEL + "/{alias:[\\w-]+}/{page:[\\d]+}",
            "/{subDir:[\\w-]+}" + CHANNEL + "/{alias:[\\w-]+}",
            "/{subDir:[\\w-]+}" + CHANNEL + "/{alias:[\\w-]+}/{page:[\\d]+}"})
    public String channel(@PathVariable String alias, @PathVariable(required = false) String subDir,
                          @PathVariable(required = false) Integer page,
                          HttpServletRequest request, Map<String, Object> modelMap) {
        Site site = siteResolver.resolve(request, subDir);
        Channel channel = channelService.findBySiteIdAndAlias(site.getId(), alias);
        if (channel == null) {
            throw new Http404Exception("Channel not found: siteId=" + site.getId() + ", alias=" + alias);
        }
        validateChannel(channel);
        modelMap.put("channel", channel);
        modelMap.put(PAGE, Constants.validPage(page));
        modelMap.put(PAGE_SIZE, channel.getExt().getPageSize());
        modelMap.put(PAGE_URL_RESOLVER, channel);
        return channel.getTemplate();
    }

    private void validateChannel(Channel channel) {
        User user = Contexts.findCurrentUser();
        if (user == null) {
            Group anonymous = groupService.getAnonymous();
            if (!anonymous.getAllAccessPermission()) {
                List<Integer> anonChannelIds = groupService.listAccessPermissions(Group.ANONYMOUS_ID, channel.getSiteId());
                if (!anonChannelIds.contains(channel.getId())) {
                    throw new Http401Exception();
                }
            }
            return;
        }
        if (user.hasAllAccessPermission()) {
            return;
        }
        List<Integer> channelIds = groupService.listAccessPermissions(user.getGroupId(), channel.getSiteId());
        if (!channelIds.contains(channel.getId())) {
            throw new Http403Exception("Channel access forbidden. ID: " + channel.getId());
        }
    }
}
