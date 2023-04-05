package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.generator.HtmlGenerator;
import com.ujcms.cms.core.mapper.GroupAccessMapper;
import com.ujcms.cms.core.mapper.RoleArticleMapper;
import com.ujcms.cms.core.mapper.RoleChannelMapper;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.RoleService;
import com.ujcms.cms.core.service.args.ChannelArgs;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.cms.core.web.support.ValidUtils;
import com.ujcms.util.web.Entities;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Responses.Body;
import com.ujcms.util.web.Servlets;
import com.ujcms.util.web.exception.Http404Exception;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.ujcms.cms.core.support.Contexts.getCurrentSiteId;
import static com.ujcms.util.query.QueryUtils.getQueryMap;

/**
 * 栏目 Controller
 *
 * @author PONY
 */
@RestController("backendChannelController")
@RequestMapping(UrlConstants.BACKEND_API + "/core/channel")
public class ChannelController {
    private final HtmlGenerator generator;
    private final RoleService roleService;
    private final ChannelService service;
    private final GroupAccessMapper groupAccessMapper;
    private final RoleArticleMapper roleArticleMapper;
    private final RoleChannelMapper roleChannelMapper;
    private final ResourceLoader resourceLoader;

    public ChannelController(HtmlGenerator generator, RoleService roleService, ChannelService service,
                             GroupAccessMapper groupAccessMapper, RoleArticleMapper roleArticleMapper,
                             RoleChannelMapper roleChannelMapper, ResourceLoader resourceLoader) {
        this.generator = generator;
        this.roleService = roleService;
        this.service = service;
        this.groupAccessMapper = groupAccessMapper;
        this.roleArticleMapper = roleArticleMapper;
        this.roleChannelMapper = roleChannelMapper;
        this.resourceLoader = resourceLoader;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('channel:list','*')")
    public List<Channel> list(@RequestParam(defaultValue = "false") boolean isArticlePermission, Integer siteId,
                              HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        ChannelArgs args = ChannelArgs.of(getQueryMap(request.getQueryString()));
        if (isArticlePermission && !user.hasAllArticlePermission()) {
            args.inArticleRoleIds(user.fetchRoleIds());
        }
        if (siteId == null) {
            siteId = Contexts.getCurrentSiteId();
        }
        args.siteId(siteId);
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('channel:show','*')")
    public Channel show(@PathVariable Integer id) {
        Channel bean = service.select(id);
        if (bean == null) {
            throw new Http404Exception("Channel not found. ID = " + id);
        }
        ValidUtils.dataInSite(bean.getSiteId(), Contexts.getCurrentSiteId());
        return bean;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('channel:create','*')")
    @OperationLog(module = "channel", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody @Valid Channel bean, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        bean.setSiteId(site.getId());
        Channel channel = new Channel();
        Entities.copy(bean, channel);
        List<Integer> groupIds = Collections.emptyList();
        List<Integer> articleRoleIds = Collections.emptyList();
        List<Integer> channelRoleIds = Collections.emptyList();
        if (bean.getParentId() != null) {
            // 按上级栏目给权限
            Channel parent = service.select(bean.getParentId());
            if (parent != null) {
                groupIds = groupAccessMapper.listGroupByChannelId(parent.getId(), null);
                articleRoleIds = roleArticleMapper.listRoleByChannelId(parent.getId(), null);
                channelRoleIds = roleChannelMapper.listRoleByChannelId(parent.getId(), null);
            }
        }
        service.insert(channel, channel.getExt(), groupIds, articleRoleIds, channelRoleIds, bean.getCustoms());
        if (site.getHtml().isAuto()) {
            String taskName = Servlets.getMessage(request, "task.html.channelRelated");
            generator.updateChannelRelatedHtml(channel.getSiteId(), user.getId(), taskName, channel.getId());
        }
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('channel:update','*')")
    @OperationLog(module = "channel", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody @Valid Channel bean, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        Channel channel = service.select(bean.getId());
        if (channel == null) {
            return Responses.notFound("Channel not found. ID = " + bean.getId());
        }
        ValidUtils.dataInSite(bean.getSiteId(), site.getId());
        User user = Contexts.getCurrentUser();
        Entities.copy(bean, channel, "siteId", "parentId", "order");
        service.update(channel, channel.getExt(), bean.getParentId(), null, null, null, bean.getCustoms());
        if (site.getHtml().isAuto()) {
            String taskName = Servlets.getMessage(request, "task.html.channelRelated");
            generator.updateChannelRelatedHtml(channel.getSiteId(), user.getId(), taskName, channel.getId());
        }
        return Responses.ok();
    }

    @PutMapping("order")
    @PreAuthorize("hasAnyAuthority('channel:update','*')")
    @OperationLog(module = "channel", operation = "updateOrder", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateOrder(@RequestBody Integer[] ids) {
        Integer siteId = Contexts.getCurrentSiteId();
        List<Channel> list = new ArrayList<>();
        for (Integer id : ids) {
            Channel bean = service.select(id);
            if (bean == null) {
                return Responses.notFound("Org not found. ID = " + id);
            }
            ValidUtils.dataInSite(bean.getSiteId(), siteId);
            list.add(bean);
        }
        service.updateOrder(list);
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('channel:delete','*')")
    @OperationLog(module = "channel", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        ids.forEach(id -> {
            Channel bean = service.select(id);
            if (bean == null) {
                return;
            }
            ValidUtils.dataInSite(bean.getSiteId(), site.getId());
            service.delete(bean);
        });
        if (site.getHtml().isAuto()) {
            String taskName = Servlets.getMessage(request, "task.html.all");
            generator.updateAllHtml(site.getId(), user.getId(), taskName, site);
        }
        return Responses.ok();
    }

    @GetMapping("channel-templates")
    @PreAuthorize("hasAnyAuthority('channel:list','*')")
    public List<String> channelTemplate() throws IOException {
        Site site = Contexts.getCurrentSite();
        String storagePath = site.getConfig().getTemplateStorage().getPath();
        return getTemplates(storagePath + site.getTheme(), "channel");
    }

    @GetMapping("article-templates")
    @PreAuthorize("hasAnyAuthority('channel:list','*')")
    public List<String> articleTemplate() throws IOException {
        Site site = Contexts.getCurrentSite();
        String storagePath = site.getConfig().getTemplateStorage().getPath();
        return getTemplates(storagePath + site.getTheme(), "article");
    }

    @GetMapping("channel-permissions")
    @PreAuthorize("hasAnyAuthority('channel:list','*')")
    public List<Integer> channelPermissions(@Nullable Integer siteId) {
        User user = Contexts.getCurrentUser();
        return roleService.listChannelPermissions(user.fetchRoleIds(), siteId != null ? siteId : getCurrentSiteId());
    }

    private List<String> getTemplates(String theme, String startWitch) throws IOException {
        List<String> themeList = new ArrayList<>();
        File file = resourceLoader.getResource(theme).getFile();
        if (!file.exists()) {
            return themeList;
        }
        File[] themeFiles = file.listFiles((dir, name) ->
                name.startsWith(startWitch) && name.endsWith(Constants.TEMPLATE_SUFFIX));
        if (themeFiles == null) {
            return themeList;
        }
        for (File themeFile : themeFiles) {
            String name = themeFile.getName();
            themeList.add(themeFile.getName().substring(0, name.indexOf(Constants.TEMPLATE_SUFFIX)));
        }
        return themeList;
    }
}