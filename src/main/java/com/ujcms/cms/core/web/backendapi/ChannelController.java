package com.ujcms.cms.core.web.backendapi;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.domain.base.GroupBase;
import com.ujcms.cms.core.domain.base.RoleBase;
import com.ujcms.cms.core.generator.HtmlGenerator;
import com.ujcms.cms.core.mapper.GroupAccessMapper;
import com.ujcms.cms.core.mapper.RoleArticleMapper;
import com.ujcms.cms.core.mapper.RoleChannelMapper;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.GroupService;
import com.ujcms.cms.core.service.RoleService;
import com.ujcms.cms.core.service.args.ChannelArgs;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.cms.core.web.support.ValidUtils;
import com.ujcms.commons.web.Entities;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Responses.Body;
import com.ujcms.commons.web.Servlets;
import com.ujcms.commons.web.Views;
import com.ujcms.commons.web.exception.Http404Exception;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.ujcms.cms.core.support.Contexts.getCurrentSiteId;
import static com.ujcms.commons.query.QueryUtils.getQueryMap;

/**
 * 栏目 Controller
 *
 * @author PONY
 */
@RestController("backendChannelController")
@RequestMapping(UrlConstants.BACKEND_API + "/core/channel")
public class ChannelController {
    private final HtmlGenerator generator;
    private final GroupService groupService;
    private final RoleService roleService;
    private final ChannelService service;
    private final GroupAccessMapper groupAccessMapper;
    private final RoleArticleMapper roleArticleMapper;
    private final RoleChannelMapper roleChannelMapper;
    private final ResourceLoader resourceLoader;

    @Autowired
    public ChannelController(HtmlGenerator generator, GroupService groupService, RoleService roleService,
                             ChannelService service,
                             GroupAccessMapper groupAccessMapper, RoleArticleMapper roleArticleMapper,
                             RoleChannelMapper roleChannelMapper, ResourceLoader resourceLoader) {
        this.generator = generator;
        this.groupService = groupService;
        this.roleService = roleService;
        this.service = service;
        this.groupAccessMapper = groupAccessMapper;
        this.roleArticleMapper = roleArticleMapper;
        this.roleChannelMapper = roleChannelMapper;
        this.resourceLoader = resourceLoader;
    }

    @GetMapping
    @JsonView(Views.List.class)
    @PreAuthorize("hasAnyAuthority('channel:list','*')")
    public List<Channel> list(@RequestParam(defaultValue = "false") boolean isArticlePermission,
                              @RequestParam(defaultValue = "true") boolean isIncludeChildren,
                              @RequestParam(defaultValue = "false") boolean isOnlyParent,
                              Integer parentId, Integer siteId,
                              HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        ChannelArgs args = ChannelArgs.of(getQueryMap(request.getQueryString()));
        if (isArticlePermission && !user.hasAllArticlePermission()) {
            args.articleRoleIds(user.fetchRoleIds());
        }
        if (siteId == null) {
            siteId = Contexts.getCurrentSiteId();
        }
        if (isIncludeChildren) {
            if (parentId != null) {
                args.ancestorId(parentId);
            } else {
                args.siteId(siteId);
            }
        } else {
            if (parentId != null) {
                args.parentId(parentId);
            } else {
                args.siteId(siteId);
                args.parentIdIsNull();
            }
        }
        args.setOnlyParent(isOnlyParent);
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('channel:show','*')")
    public Channel show(@PathVariable Integer id) {
        Channel bean = service.select(id);
        if (bean == null) {
            throw new Http404Exception(CHANNEL_NOT_FOUND + id);
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
        // 默认给所有用户组、角色权限
        List<Integer> groupIds = groupService.listNotAllAccessPermission().stream()
                .map(GroupBase::getId).collect(Collectors.toList());
        List<Integer> articleRoleIds = roleService.listNotAllArticlePermission(site.getId()).stream()
                .map(RoleBase::getId).collect(Collectors.toList());
        List<Integer> channelRoleIds = roleService.listNotAllChannelPermission(site.getId()).stream()
                .map(RoleBase::getId).collect(Collectors.toList());
        Integer parentId = bean.getParentId();
        if (parentId != null) {
            // 按上级栏目给权限
            Channel parent = service.select(parentId);
            if (parent != null) {
                groupIds = groupAccessMapper.listGroupByChannelId(parent.getId(), null);
                articleRoleIds = roleArticleMapper.listRoleByChannelId(parent.getId(), null);
                channelRoleIds = roleChannelMapper.listRoleByChannelId(parent.getId(), null);
            }
        }
        service.insert(channel, channel.getExt(), groupIds, articleRoleIds, channelRoleIds, bean.getCustoms());

        if (site.getHtml().isEnabledAndAuto()) {
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
            return Responses.notFound(CHANNEL_NOT_FOUND + bean.getId());
        }
        ValidUtils.dataInSite(bean.getSiteId(), site.getId());
        User user = Contexts.getCurrentUser();
        Entities.copy(bean, channel, "siteId", "parentId", "order");
        service.update(channel, channel.getExt(), bean.getParentId(), null, null, null, bean.getCustoms());
        if (site.getHtml().isEnabledAndAuto()) {
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
                return Responses.notFound(CHANNEL_NOT_FOUND + id);
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
        for (Integer id : ids) {
            Channel bean = service.select(id);
            if (bean == null) {
                continue;
            }
            ValidUtils.dataInSite(bean.getSiteId(), site.getId());
            delete(bean);
        }
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, "task.html.all");
            generator.updateAllHtml(site.getId(), user.getId(), taskName, site);
        }
        return Responses.ok();
    }

    /**
     * 递归删除栏目。不能放到Service里面递归，这样会因事务太大，导致性能低下。
     *
     * @param bean 栏目
     * @return 删除条数
     */
    private int delete(Channel bean) {
        int deleted = 0;
        for (Channel c : service.listChildren(bean.getId())) {
            deleted += delete(c);
        }
        deleted = service.delete(bean);
        return deleted;
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

    @GetMapping("alias-exist")
    public boolean aliasExist(@NotBlank String alias, Integer siteId) {
        if (siteId == null) {
            siteId = Contexts.getCurrentSiteId();
        }
        return service.existsByAlias(alias, siteId);
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

    private static final String CHANNEL_NOT_FOUND = "Channel not found. ID = ";
}