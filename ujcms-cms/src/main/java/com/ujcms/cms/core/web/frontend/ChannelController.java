package com.ujcms.cms.core.web.frontend;

import static com.ujcms.cms.core.support.Frontends.LAYOUT_VARIANT;
import static com.ujcms.cms.core.support.Frontends.PAGE;
import static com.ujcms.cms.core.support.Frontends.TEMPLATE_NAME;
import static com.ujcms.cms.core.support.Frontends.PAGE_SIZE;
import static com.ujcms.cms.core.support.Frontends.PAGE_URL_RESOLVER;
import static com.ujcms.cms.core.support.UrlConstants.CHANNEL;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.ujcms.cms.core.composition.LayoutVariantCatalog;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Group;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.GroupService;
import com.ujcms.cms.core.service.LayoutVariantService;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.FrontendController;
import com.ujcms.common.web.exception.Http401Exception;
import com.ujcms.common.web.exception.Http403Exception;
import com.ujcms.common.web.exception.Http404Exception;

/**
 * 前台栏目 Controller
 *
 * @author PONY
 */
@FrontendController("frontendChannelController")
public class ChannelController {
    private final ChannelService channelService;
    private final GroupService groupService;
    private final LayoutVariantService layoutVariantService;

    public ChannelController(ChannelService channelService, GroupService groupService,
                             LayoutVariantService layoutVariantService) {
        this.channelService = channelService;
        this.groupService = groupService;
        this.layoutVariantService = layoutVariantService;
    }

    @GetMapping({CHANNEL + "/{slug:[\\w-]+}", CHANNEL + "/{slug:[\\w-]+}/{page:[\\d]+}"})
    public String channel(Site site, @PathVariable String slug, @PathVariable(required = false) Integer page,
                          Map<String, Object> modelMap) {
        Channel channel = channelService.findBySiteIdAndSlug(site.getId(), slug);
        if (channel == null) {
            throw new Http404Exception("Channel not found: siteId=" + site.getId() + ", slug=" + slug);
        }
        short type = channel.getType();
        if (type == Channel.TYPE_LINK || type == Channel.TYPE_LINK_CHILD) {
            return "redirect:" + channel.getUrl();
        }
        validateChannel(channel);
        modelMap.put("channel", channel);
        modelMap.put(PAGE, Constants.validPage(page));
        modelMap.put(PAGE_SIZE, channel.getPageSize());
        modelMap.put(PAGE_URL_RESOLVER, channel);
        // 模板轨道渲染精度（template-track-variants D1/D2）：显式模板选择 > 默认；活动变体按有效模板
        // 反查主题变体绑定。规则收口于共享 helper（preview-url-resolver §4.2 D6），与 PreviewUrlResolver 永不漂移。
        LayoutVariantService.EffectiveTemplate effective = layoutVariantService.effectiveChannelTemplate(site, channel);
        if (effective.variant() != null) {
            modelMap.put(LAYOUT_VARIANT, LayoutVariantCatalog.SURFACE_CHANNEL + ":" + effective.variant().getValue());
        }
        // 渲染上下文钩子（页面构成 §10.2）：插槽标签按未组装模板名解析页面清单
        modelMap.put(TEMPLATE_NAME, effective.template());
        return site.assembleTemplate(effective.template());
    }

    public static boolean hasAccessPermission(Group group, Long siteId, Long channelId,
                                              GroupService groupService) {
        if (Boolean.TRUE.equals(group.getAllAccessPermission())) {
            return true;
        }
        List<Long> channelIds = groupService.listAccessPermissions(group.getId(), siteId);
        return channelIds.contains(channelId);
    }

    private void validateChannel(Channel channel) {
        User user = Contexts.findCurrentUser();
        if (user == null) {
            Group anonGroup = groupService.getAnonymous();
            if (!hasAccessPermission(anonGroup, channel.getSiteId(), channel.getId(), groupService)) {
                throw new Http401Exception();
            }
        } else {
            Group userGroup = user.getGroup();
            if (!hasAccessPermission(userGroup, channel.getSiteId(), channel.getId(), groupService)) {
                throw new Http403Exception("Channel access forbidden. ID: " + channel.getId());
            }
        }
    }
}
