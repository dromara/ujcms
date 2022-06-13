package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.Attachment;
import com.ujcms.cms.core.service.AttachmentService;
import com.ujcms.cms.core.service.args.AttachmentArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Responses.Body;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static com.ujcms.cms.core.support.Constants.validPage;
import static com.ujcms.cms.core.support.Constants.validPageSize;
import static com.ujcms.util.db.MyBatis.springPage;
import static com.ujcms.util.query.QueryUtils.getQueryMap;

/**
 * 附件 Controller
 *
 * @author PONY
 */
@RestController("backendAttachmentController")
@RequestMapping(UrlConstants.BACKEND_API + "/core/attachment")
public class AttachmentController {
    private AttachmentService service;

    public AttachmentController(AttachmentService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermissions("attachment:list")
    public Object list(Integer page, Integer pageSize, HttpServletRequest request) {
        AttachmentArgs args = AttachmentArgs.of(getQueryMap(request.getQueryString()))
                .siteId(Contexts.getCurrentSiteId());
        return springPage(service.selectPage(args, validPage(page), validPageSize(pageSize)));
    }

    @GetMapping("{id}")
    @RequiresPermissions("attachment:show")
    public Object show(@PathVariable Integer id) {
        Attachment bean = service.select(id);
        if (bean == null) {
            return Responses.notFound("Attachment not found. ID = " + id);
        }
        return bean;
    }

    @DeleteMapping
    @RequiresPermissions("attachment:delete")
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids) {
        service.delete(ids);
        return Responses.ok();
    }
}