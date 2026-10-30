package com.ujcms.cms.ext.web.frontend;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.FrontendController;
import com.ujcms.cms.ext.service.ContributeService;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http403Exception;
import com.ujcms.common.web.exception.Http404Exception;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 会员投稿页面 Controller（docs/spec/member-contribute/plan.md）：我的投稿列表、新建与修改表单。
 * 放在 ext 而不是核心的 {@code SettingController}：表单页要带可投稿栏目与文章模型的字段，核心的设置页只渲染模板
 *
 * @author PONY
 */
@FrontendController("frontendContributeController")
@RequestMapping("/settings/contribute")
public class ContributeController {
    private static final String TEMPLATE_LIST = "mem_settings_contribute";
    private static final String TEMPLATE_FORM = "mem_settings_contribute_form";

    private final ContributeService service;
    private final ChannelService channelService;

    public ContributeController(ContributeService service, ChannelService channelService) {
        this.service = service;
        this.channelService = channelService;
    }

    /**
     * 我的投稿。站点未开放投稿也渲染（列表为空、不显示新建）：会员仍要看得到以前的投稿
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public String list(Site site) {
        return site.assembleTemplate(TEMPLATE_LIST);
    }

    /**
     * 新建投稿。栏目选在地址上（{@code ?channelId=}）：换栏目即重载页面，表单按所选栏目的文章模型渲染自定义字段
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/create")
    public String create(Site site, @RequestParam(required = false) Long channelId, Map<String, Object> modelMap) {
        User user = Contexts.getCurrentUser();
        if (!service.isOpen(site)) {
            throw new Http404Exception("error.contribute.disabled");
        }
        List<Channel> channels = service.listChannels(site, user);
        Channel channel = null;
        if (channelId != null) {
            channel = channelService.selectInSite(channelId, site.getId());
            if (!service.canContribute(site, user, channel)) {
                throw new Http403Exception("error.contribute.channelNotAllowed");
            }
        }
        modelMap.put("contributeChannels", channels);
        modelMap.put("channel", channel);
        modelMap.put("fieldList", channel != null ? service.simpleFields(channel.getArticleModel())
                : Collections.emptyList());
        return site.assembleTemplate(TEMPLATE_FORM);
    }

    /**
     * 修改投稿。非本人 404；状态不可修改 400。栏目不可改，自定义字段按投稿所在栏目的文章模型渲染
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id:\\d+}/edit")
    public String edit(Site site, @PathVariable Long id, Map<String, Object> modelMap) {
        User user = Contexts.getCurrentUser();
        Article article = service.getOwn(site, user, id);
        if (article == null) {
            throw new Http404Exception(Article.NOT_FOUND + id);
        }
        if (!ContributeService.isEditable(article)) {
            throw new Http400Exception("error.contribute.notEditable");
        }
        Channel channel = article.getChannel();
        // 与保存接口同一条规则：关掉投稿的栏目不再收重提，表单页就先说明
        if (!service.canContribute(site, user, channel)) {
            throw new Http403Exception("error.contribute.channelNotAllowed");
        }
        modelMap.put("article", article);
        modelMap.put("contributeChannels", Collections.singletonList(channel));
        modelMap.put("channel", channel);
        modelMap.put("fieldList", service.simpleFields(channel.getArticleModel()));
        return site.assembleTemplate(TEMPLATE_FORM);
    }
}
