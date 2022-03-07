package com.ujcms.core.web.backendapi;

import com.ujcms.core.domain.Attachment;
import com.ujcms.core.service.AttachmentService;
import com.ofwise.util.db.MyBatis;
import com.ofwise.util.query.QueryUtils;
import com.ofwise.util.web.Responses;
import com.ofwise.util.web.Responses.Body;
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
import java.util.Map;

import static com.ujcms.core.support.Constants.validPage;
import static com.ujcms.core.support.Constants.validPageSize;
import static com.ujcms.core.support.UrlConstants.BACKEND_API;

/**
 * 附件 Controller
 *
 * @author PONY
 */
@RestController("backendAttachmentController")
@RequestMapping(BACKEND_API + "/core/attachment")
public class AttachmentController {
    private AttachmentService service;

    public AttachmentController(AttachmentService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermissions("attachment:list")
    public Object list(Integer page, Integer pageSize, HttpServletRequest request) {
        Map<String, Object> queryMap = QueryUtils.getQueryMap(request.getQueryString());
        return MyBatis.toPage(service.selectPage(queryMap, validPage(page), validPageSize(pageSize)));
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