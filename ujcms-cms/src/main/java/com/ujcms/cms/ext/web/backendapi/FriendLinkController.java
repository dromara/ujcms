package com.ujcms.cms.ext.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.ValidUtils;
import com.ujcms.cms.ext.domain.FriendLink;
import com.ujcms.cms.ext.service.FriendLinkService;
import com.ujcms.cms.ext.service.args.FriendLinkArgs;
import com.ujcms.common.db.order.MoveOrderParams;
import com.ujcms.common.web.Entities;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.exception.Http404Exception;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.cms.ext.domain.FriendLink.NOT_FOUND;
import static com.ujcms.common.query.QueryUtils.getQueryMap;

/**
 * 友情链接 Controller
 *
 * @author PONY
 */
@RestController("backendFriendLinkController")
@RequestMapping(BACKEND_API + "/ext/friend-link")
public class FriendLinkController {
    private final FriendLinkService service;

    public FriendLinkController(FriendLinkService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('friendLink:list','*')")
    public List<FriendLink> list(HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        FriendLinkArgs args = FriendLinkArgs.of(getQueryMap(request.getQueryString()));
        args.siteId(site.getId());
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('friendLink:show','*')")
    public FriendLink show(@PathVariable("id") Long id) {
        Site site = Contexts.getCurrentSite();
        FriendLink friendLink = Optional.ofNullable(service.select(id))
                .orElseThrow(() -> new Http404Exception(NOT_FOUND + id));
        ValidUtils.dataInSite(friendLink.getSiteId(), site.getId());
        return friendLink;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('friendLink:create','*')")
    @OperationLog(module = "friendLink", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody @Valid FriendLink bean) {
        Site site = Contexts.getCurrentSite();
        FriendLink friendLink = new FriendLink();
        Entities.copy(bean, friendLink);
        friendLink.setSiteId(site.getId());
        service.insert(friendLink);
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('friendLink:update','*')")
    @OperationLog(module = "friendLink", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody @Valid FriendLink bean) {
        Site site = Contexts.getCurrentSite();
        FriendLink friendLink = Optional.ofNullable(service.select(bean.getId()))
                .orElseThrow(() -> new Http404Exception(NOT_FOUND + bean.getId()));
        Entities.copy(bean, friendLink, "siteId");
        ValidUtils.dataInSite(friendLink.getSiteId(), site.getId());
        service.update(friendLink);
        return Responses.ok();
    }

    @PostMapping("update-order")
    @PreAuthorize("hasAnyAuthority('friendLink:update','*')")
    @OperationLog(module = "friendLink", operation = "updateOrder", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateOrder(@RequestBody @Valid MoveOrderParams params) {
        Site site = Contexts.getCurrentSite();
        FriendLink fromBean = Optional.ofNullable(service.select(params.getFromId()))
                .orElseThrow(() -> new Http404Exception(NOT_FOUND + params.getFromId()));
        FriendLink toBean = Optional.ofNullable(service.select(params.getToId()))
                .orElseThrow(() -> new Http404Exception(NOT_FOUND + params.getToId()));
        ValidUtils.dataInSite(fromBean.getSiteId(), site.getId());
        ValidUtils.dataInSite(toBean.getSiteId(), site.getId());
        service.moveOrder(fromBean.getId(), toBean.getId());
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('friendLink:delete','*')")
    @OperationLog(module = "friendLink", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids) {
        Site site = Contexts.getCurrentSite();
        for (Long id : ids) {
            FriendLink bean = Optional.ofNullable(service.select(id))
                    .orElseThrow(() -> new Http404Exception(NOT_FOUND + id));
            ValidUtils.dataInSite(bean.getSiteId(), site.getId());
            service.delete(bean.getId());
        }
        return Responses.ok();
    }
}
