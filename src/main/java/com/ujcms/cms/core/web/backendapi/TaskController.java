package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.Task;
import com.ujcms.cms.core.service.TaskService;
import com.ujcms.cms.core.service.args.TaskArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Responses.Body;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.BeanUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static com.ujcms.cms.core.support.Constants.validPage;
import static com.ujcms.cms.core.support.Constants.validPageSize;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.util.db.MyBatis.springPage;
import static com.ujcms.util.query.QueryUtils.getQueryMap;

/**
 * 任务 Controller
 *
 * @author PONY
 */
@RestController("backendTaskController")
@RequestMapping(BACKEND_API + "/core/task")
public class TaskController {
    private TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermissions("task:list")
    public Object list(Integer page, Integer pageSize, HttpServletRequest request) {
        TaskArgs args = TaskArgs.of(getQueryMap(request.getQueryString()))
                .siteId(Contexts.getCurrentSiteId());
        return springPage(service.selectPage(args, validPage(page), validPageSize(pageSize)));
    }

    @GetMapping("{id}")
    @RequiresPermissions("task:show")
    public Object show(@PathVariable Integer id) {
        Task bean = service.select(id);
        if (bean == null) {
            return Responses.notFound("Task not found. ID = " + id);
        }
        return bean;
    }

    @PostMapping
    @RequiresPermissions("task:create")
    public ResponseEntity<Body> create(@RequestBody Task bean) {
        Task task = new Task();
        BeanUtils.copyProperties(bean, task);
        service.insert(task);
        return Responses.ok();
    }

    @PutMapping
    @RequiresPermissions("task:update")
    public ResponseEntity<Body> update(@RequestBody Task bean) {
        Task task = service.select(bean.getId());
        if (task == null) {
            return Responses.notFound("Task not found. ID = " + bean.getId());
        }
        BeanUtils.copyProperties(bean, task);
        service.update(task);
        return Responses.ok();
    }

    @DeleteMapping
    @RequiresPermissions("task:delete")
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids) {
        service.delete(ids);
        return Responses.ok();
    }
}