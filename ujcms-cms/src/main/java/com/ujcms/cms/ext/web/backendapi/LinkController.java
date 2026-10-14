package com.ujcms.cms.ext.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.ValidUtils;
import com.ujcms.cms.ext.domain.Link;
import com.ujcms.cms.ext.service.LinkService;
import com.ujcms.cms.ext.service.args.LinkArgs;
import com.ujcms.common.db.order.MoveOrderParams;
import com.ujcms.common.web.Entities;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http404Exception;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.cms.ext.domain.Link.NOT_FOUND;
import static com.ujcms.common.query.QueryUtils.getQueryMap;

/**
 * 链接 Controller
 *
 * @author PONY
 */
@RestController("backendLinkController")
@RequestMapping(BACKEND_API + "/ext/link")
public class LinkController {
    private final LinkService service;

    public LinkController(LinkService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('link:list','*')")
    public List<Link> list(HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        LinkArgs args = LinkArgs.of(getQueryMap(request.getQueryString()));
        args.siteId(site.getId());
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('link:show','*')")
    public Link show(@PathVariable("id") Long id) {
        Site site = Contexts.getCurrentSite();
        Link link = Optional.ofNullable(service.select(id))
                .orElseThrow(() -> new Http404Exception(NOT_FOUND + id));
        ValidUtils.dataInSite(link.getSiteId(), site.getId());
        return link;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('link:create','*')")
    @OperationLog(module = "link", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody @Valid Link bean) {
        Site site = Contexts.getCurrentSite();
        Link link = new Link();
        Entities.copy(bean, link);
        link.setSiteId(site.getId());
        validateBean(link);
        service.insert(link);
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('link:update','*')")
    @OperationLog(module = "link", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody @Valid Link bean) {
        Site site = Contexts.getCurrentSite();
        Link link = Optional.ofNullable(service.select(bean.getId()))
                .orElseThrow(() -> new Http404Exception(NOT_FOUND + bean.getId()));
        Entities.copy(bean, link, "siteId");
        ValidUtils.dataInSite(link.getSiteId(), site.getId());
        validateBean(link);
        service.update(link);
        return Responses.ok();
    }

    @PostMapping("update-order")
    @PreAuthorize("hasAnyAuthority('link:update','*')")
    @OperationLog(module = "link", operation = "updateOrder", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateOrder(@RequestBody @Valid MoveOrderParams params) {
        Site site = Contexts.getCurrentSite();
        Link fromBean = Optional.ofNullable(service.select(params.getFromId()))
                .orElseThrow(() -> new Http404Exception(NOT_FOUND + params.getFromId()));
        Link toBean = Optional.ofNullable(service.select(params.getToId()))
                .orElseThrow(() -> new Http404Exception(NOT_FOUND + params.getToId()));
        ValidUtils.dataInSite(fromBean.getSiteId(), site.getId());
        ValidUtils.dataInSite(toBean.getSiteId(), site.getId());
        service.moveOrder(fromBean.getId(), toBean.getId());
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('link:delete','*')")
    @OperationLog(module = "link", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids) {
        Site site = Contexts.getCurrentSite();
        for (Long id : ids) {
            Link bean = Optional.ofNullable(service.select(id))
                    .orElseThrow(() -> new Http404Exception(NOT_FOUND + id));
            ValidUtils.dataInSite(bean.getSiteId(), site.getId());
            service.delete(bean.getId());
        }
        return Responses.ok();
    }

    /**
     * linkUrl / channelId / articleId 三选一（link-groups D2；种子导入侧由 schema oneOf 校验）
     */
    private void validateBean(Link bean) {
        int provided = 0;
        if (StringUtils.isNotBlank(bean.getLinkUrl())) {
            provided++;
        }
        if (bean.getChannelId() != null) {
            provided++;
        }
        if (bean.getArticleId() != null) {
            provided++;
        }
        if (provided != 1) {
            throw new Http400Exception("exactly one of linkUrl/channelId/articleId is required");
        }
    }
}
