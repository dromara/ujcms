package com.ujcms.cms.ext.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.composition.LinkSlotCatalog;
import com.ujcms.cms.core.composition.ManifestService;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.ValidUtils;
import com.ujcms.cms.ext.domain.LinkGroup;
import com.ujcms.cms.ext.service.LinkGroupService;
import com.ujcms.cms.ext.service.args.LinkGroupArgs;
import com.ujcms.common.db.order.MoveOrderParams;
import com.ujcms.common.web.Entities;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http404Exception;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.cms.ext.domain.LinkGroup.NOT_FOUND;
import static com.ujcms.common.query.QueryUtils.getQueryMap;

/**
 * 链接组 Controller
 *
 * @author PONY
 */
@RestController("backendLinkGroupController")
@RequestMapping(BACKEND_API + "/ext/link-group")
public class LinkGroupController {
    private final LinkGroupService service;
    private final ManifestService manifestService;

    public LinkGroupController(LinkGroupService service, ManifestService manifestService) {
        this.service = service;
        this.manifestService = manifestService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('linkGroup:list','*')")
    public List<LinkGroup> list(HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        LinkGroupArgs args = LinkGroupArgs.of(getQueryMap(request.getQueryString()));
        args.siteId(site.getId()).queryLinkCount();
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('linkGroup:show','*')")
    public LinkGroup show(@PathVariable("id") Long id) {
        Site site = Contexts.getCurrentSite();
        LinkGroup linkGroup = Optional.ofNullable(service.select(id))
                .orElseThrow(() -> new Http404Exception(NOT_FOUND + id));
        ValidUtils.dataInSite(linkGroup.getSiteId(), site.getId());
        return linkGroup;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('linkGroup:create','*')")
    @OperationLog(module = "linkGroup", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody @Valid LinkGroup bean) {
        Site site = Contexts.getCurrentSite();
        LinkGroup linkGroup = new LinkGroup();
        Entities.copy(bean, linkGroup);
        linkGroup.setSiteId(site.getId());
        validateBean(linkGroup, null);
        service.insert(linkGroup);
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('linkGroup:update','*')")
    @OperationLog(module = "linkGroup", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody @Valid LinkGroup bean) {
        Site site = Contexts.getCurrentSite();
        LinkGroup linkGroup = Optional.ofNullable(service.select(bean.getId()))
                .orElseThrow(() -> new Http404Exception(NOT_FOUND + bean.getId()));
        String origAlias = linkGroup.getAlias();
        // 来源三元组由种子导入器专管（theme-seed-export E4f / D44）：控制台编辑不得改写
        Entities.copy(bean, linkGroup, "siteId", "origin", "originId", "originHash");
        ValidUtils.dataInSite(linkGroup.getSiteId(), site.getId());
        validateBean(linkGroup, origAlias);
        service.update(linkGroup);
        return Responses.ok();
    }

    @PostMapping("update-order")
    @PreAuthorize("hasAnyAuthority('linkGroup:update','*')")
    @OperationLog(module = "linkGroup", operation = "updateOrder", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateOrder(@RequestBody @Valid MoveOrderParams params) {
        Site site = Contexts.getCurrentSite();
        LinkGroup fromBean = Optional.ofNullable(service.select(params.getFromId()))
                .orElseThrow(() -> new Http404Exception(NOT_FOUND + params.getFromId()));
        LinkGroup toBean = Optional.ofNullable(service.select(params.getToId()))
                .orElseThrow(() -> new Http404Exception(NOT_FOUND + params.getToId()));
        ValidUtils.dataInSite(fromBean.getSiteId(), site.getId());
        ValidUtils.dataInSite(toBean.getSiteId(), site.getId());
        service.moveOrder(fromBean.getId(), toBean.getId());
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('linkGroup:delete','*')")
    @OperationLog(module = "linkGroup", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids) {
        Site site = Contexts.getCurrentSite();
        for (Long id : ids) {
            LinkGroup bean = Optional.ofNullable(service.select(id))
                    .orElseThrow(() -> new Http404Exception(NOT_FOUND + id));
            ValidUtils.dataInSite(bean.getSiteId(), site.getId());
            service.delete(bean.getId());
        }
        return Responses.ok();
    }

    @GetMapping("alias-exist")
    @PreAuthorize("hasAnyAuthority('linkGroup:validation','*')")
    public boolean aliasExist(@NotBlank String alias) {
        Site site = Contexts.getCurrentSite();
        return service.existsByAlias(alias, site.getId());
    }

    /**
     * 当前站点主题声明的展示位词汇表（link-groups D7）：桌面 ∪ 手机主题并集（按名去重，
     * 桌面标签优先）——展示位可能由任一主题的模板渲染，且非活动主题的取值保持可选，
     * 换主题后组的 slot 值不丢。
     */
    @GetMapping("slots")
    @PreAuthorize("hasAnyAuthority('linkGroup:list','*')")
    public List<LinkSlotCatalog.Slot> slots() {
        Site site = Contexts.getCurrentSite();
        Map<String, LinkSlotCatalog.Slot> byName = new LinkedHashMap<>();
        for (LinkSlotCatalog.Slot slot : manifestService.getSnapshot()
                .getThemeLinkSlots(site.getThemeKey()).getSlots()) {
            byName.putIfAbsent(slot.name(), slot);
        }
        for (LinkSlotCatalog.Slot slot : manifestService.getSnapshot()
                .getThemeLinkSlots(site.getMobileThemeKey()).getSlots()) {
            byName.putIfAbsent(slot.name(), slot);
        }
        return List.copyOf(byName.values());
    }

    private void validateBean(LinkGroup bean, @Nullable String origAlias) {
        if (!bean.getAlias().equals(origAlias) && service.existsByAlias(bean.getAlias(), bean.getSiteId())) {
            throw new Http400Exception("alias exist: " + bean.getAlias());
        }
    }
}
