package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.domain.base.GroupBase;
import com.ujcms.cms.core.domain.base.RoleBase;
import com.ujcms.cms.core.generator.HtmlGenerator;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.GroupService;
import com.ujcms.cms.core.service.RoleService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.service.args.ChannelArgs;
import com.ujcms.cms.core.service.args.GroupArgs;
import com.ujcms.cms.core.service.args.RoleArgs;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.util.web.Entities;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Responses.Body;
import com.ujcms.util.web.Servlets;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.ujcms.util.query.QueryUtils.getQueryMap;

/**
 * 栏目 Controller
 *
 * @author PONY
 */
@RestController("backendChannelController")
@RequestMapping(UrlConstants.BACKEND_API + "/core/channel")
public class ChannelController {
    private HtmlGenerator generator;
    private GroupService groupService;
    private RoleService roleService;
    private UserService userService;
    private ChannelService service;
    private ResourceLoader resourceLoader;

    public ChannelController(HtmlGenerator generator, GroupService groupService, RoleService roleService,
                             UserService userService, ChannelService service, ResourceLoader resourceLoader) {
        this.generator = generator;
        this.groupService = groupService;
        this.roleService = roleService;
        this.userService = userService;
        this.service = service;
        this.resourceLoader = resourceLoader;
    }

    @GetMapping
    @RequiresPermissions("channel:list")
    public Object list(@RequestParam(defaultValue = "false") boolean isArticlePermission, HttpServletRequest request) {
        User user = Contexts.getCurrentUser(userService);
        ChannelArgs args = ChannelArgs.of(getQueryMap(request.getQueryString()));
        if (isArticlePermission && !user.hasAllArticlePermission()) {
            args.inArticleRoleIds(user.fetchRoleIds());
        }
        args.siteId(Contexts.getCurrentSiteId());
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @RequiresPermissions("channel:show")
    public Object show(@PathVariable Integer id) {
        Channel bean = service.select(id);
        if (bean == null) {
            return Responses.notFound("Channel not found. ID = " + id);
        }
        return bean;
    }

    @PostMapping
    @RequiresPermissions("channel:create")
    public ResponseEntity<Body> create(@RequestBody @Valid Channel bean, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        Integer userId = Contexts.getCurrentUserId();
        bean.setSiteId(site.getId());
        Channel channel = new Channel();
        Entities.copy(bean, channel);
        List<Integer> groupIds = Collections.emptyList();
        List<Integer> roleIds = Collections.emptyList();
        if (bean.getParentId() != null) {
            // 按上级栏目给权限
            Channel parent = service.select(bean.getParentId());
            if (parent != null) {
                groupIds = parent.getGroupIds();
                roleIds = parent.getRoleIds();
            }
        } else {
            // 顶级栏目则给所有权限
            GroupArgs groupArgs = GroupArgs.of().allAccessPermission(false);
            groupIds = groupService.selectList(groupArgs).stream().map(GroupBase::getId).collect(Collectors.toList());
            RoleArgs roleArgs = RoleArgs.of().scopeSiteId(bean.getSiteId()).allArticlePermission(false);
            roleIds = roleService.selectList(roleArgs).stream().map(RoleBase::getId).collect(Collectors.toList());
        }
        service.insert(channel, channel.getExt(), groupIds, roleIds, bean.getCustoms());
        if (site.getHtml().isAuto()) {
            String taskName = Servlets.getMessage(request, "task.html.channelRelated");
            generator.updateChannelRelatedHtml(channel.getSiteId(), userId, taskName, channel.getId());
        }
        return Responses.ok();
    }

    @PutMapping
    @RequiresPermissions("channel:update")
    public ResponseEntity<Body> update(@RequestBody @Valid Channel bean, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        Channel channel = service.select(bean.getId());
        if (channel == null) {
            return Responses.notFound("Channel not found. ID = " + bean.getId());
        }
        Integer userId = Contexts.getCurrentUserId();
        Entities.copy(bean, channel, "siteId", "parentId", "order");
        service.update(channel, channel.getExt(), bean.getParentId(), null, null, bean.getCustoms());
        if (site.getHtml().isAuto()) {
            String taskName = Servlets.getMessage(request, "task.html.channelRelated");
            generator.updateChannelRelatedHtml(channel.getSiteId(), userId, taskName, channel.getId());
        }
        return Responses.ok();
    }

    @PutMapping("order")
    @RequiresPermissions("channel:update")
    public ResponseEntity<Body> updateOrder(@RequestBody Integer[] ids) {
        List<Channel> list = new ArrayList<>();
        for (Integer id : ids) {
            Channel bean = service.select(id);
            if (bean == null) {
                return Responses.notFound("Org not found. ID = " + id);
            }
            list.add(bean);
        }
        service.updateOrder(list);
        return Responses.ok();
    }

    @DeleteMapping
    @RequiresPermissions("channel:delete")
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        Integer userId = Contexts.getCurrentUserId();
        service.delete(ids);
        if (site.getHtml().isAuto()) {
            String taskName = Servlets.getMessage(request, "task.html.all");
            generator.updateAllHtml(site.getId(), userId, taskName, site);
        }
        return Responses.ok();
    }

    @GetMapping("channel-templates")
    @RequiresPermissions("channel:list")
    public List<String> channelTemplate() throws IOException {
        Site site = Contexts.getCurrentSite();
        String storagePath = site.getConfig().getTemplateStorage().getPath();
        return getTemplates(storagePath + site.getTheme(), "channel");
    }

    @GetMapping("article-templates")
    @RequiresPermissions("channel:list")
    public List<String> articleTemplate() throws IOException {
        Site site = Contexts.getCurrentSite();
        String storagePath = site.getConfig().getTemplateStorage().getPath();
        return getTemplates(storagePath + site.getTheme(), "article");
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