package com.ujcms.cms.ext.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.ValidUtils;
import com.ujcms.cms.ext.domain.FriendLinkType;
import com.ujcms.cms.ext.service.FriendLinkTypeService;
import com.ujcms.cms.ext.service.args.FriendLinkTypeArgs;
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
import java.util.List;
import java.util.Optional;

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.cms.ext.domain.FriendLinkType.NOT_FOUND;
import static com.ujcms.common.query.QueryUtils.getQueryMap;

/**
 * 友情链接类型 Controller
 *
 * @author PONY
 */
@RestController("backendFriendLinkTypeController")
@RequestMapping(BACKEND_API + "/ext/friend-link-type")
public class FriendLinkTypeController {
    private final FriendLinkTypeService service;

    public FriendLinkTypeController(FriendLinkTypeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('friendLinkType:list','*')")
    public List<FriendLinkType> list(HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        FriendLinkTypeArgs args = FriendLinkTypeArgs.of(getQueryMap(request.getQueryString()));
        args.siteId(site.getId());
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('friendLinkType:show','*')")
    public FriendLinkType show(@PathVariable("id") Long id) {
        Site site = Contexts.getCurrentSite();
        FriendLinkType friendLinkType = Optional.ofNullable(service.select(id))
                .orElseThrow(() -> new Http404Exception(NOT_FOUND + id));
        ValidUtils.dataInSite(friendLinkType.getSiteId(), site.getId());
        return friendLinkType;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('friendLinkType:create','*')")
    @OperationLog(module = "friendLinkType", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody @Valid FriendLinkType bean) {
        Site site = Contexts.getCurrentSite();
        FriendLinkType friendLinkType = new FriendLinkType();
        Entities.copy(bean, friendLinkType);
        friendLinkType.setSiteId(site.getId());
        validateBean(friendLinkType, null);
        service.insert(friendLinkType);
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('friendLinkType:update','*')")
    @OperationLog(module = "friendLinkType", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody @Valid FriendLinkType bean) {
        Site site = Contexts.getCurrentSite();
        FriendLinkType friendLinkType = Optional.ofNullable(service.select(bean.getId()))
                .orElseThrow(() -> new Http404Exception(NOT_FOUND + bean.getId()));
        String origAlias = friendLinkType.getAlias();
        Entities.copy(bean, friendLinkType, "siteId");
        ValidUtils.dataInSite(friendLinkType.getSiteId(), site.getId());
        validateBean(friendLinkType, origAlias);
        service.update(friendLinkType);
        return Responses.ok();
    }

    @PostMapping("update-order")
    @PreAuthorize("hasAnyAuthority('friendLinkType:update','*')")
    @OperationLog(module = "friendLinkType", operation = "updateOrder", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateOrder(@RequestBody @Valid MoveOrderParams params) {
        Site site = Contexts.getCurrentSite();
        FriendLinkType fromBean = Optional.ofNullable(service.select(params.getFromId()))
                .orElseThrow(() -> new Http404Exception(NOT_FOUND + params.getFromId()));
        FriendLinkType toBean = Optional.ofNullable(service.select(params.getToId()))
                .orElseThrow(() -> new Http404Exception(NOT_FOUND + params.getToId()));
        ValidUtils.dataInSite(fromBean.getSiteId(), site.getId());
        ValidUtils.dataInSite(toBean.getSiteId(), site.getId());
        service.moveOrder(fromBean.getId(), toBean.getId());
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('friendLinkType:delete','*')")
    @OperationLog(module = "friendLinkType", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids) {
        Site site = Contexts.getCurrentSite();
        for (Long id : ids) {
            FriendLinkType bean = Optional.ofNullable(service.select(id))
                    .orElseThrow(() -> new Http404Exception(NOT_FOUND + id));
            ValidUtils.dataInSite(bean.getSiteId(), site.getId());
            service.delete(bean.getId());
        }
        return Responses.ok();
    }

    @GetMapping("alias-exist")
    @PreAuthorize("hasAnyAuthority('friendLinkType:validation','*')")
    public boolean aliasExist(@NotBlank String alias) {
        Site site = Contexts.getCurrentSite();
        return service.existsByAlias(alias, site.getId());
    }

    private void validateBean(FriendLinkType bean, @Nullable String origAlias) {
        if (!bean.getAlias().equals(origAlias) && service.existsByAlias(bean.getAlias(), bean.getSiteId())) {
            throw new Http400Exception("alias exist: " + bean.getAlias());
        }
    }
}
