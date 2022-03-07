package com.ujcms.core.web.backendapi;

import com.ofwise.util.db.MyBatis;
import com.ofwise.util.query.QueryUtils;
import com.ofwise.util.web.Responses;
import com.ofwise.util.web.Responses.Body;
import com.ujcms.core.domain.Task;
import com.ujcms.core.service.TaskService;
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
import java.util.Map;

import static com.ujcms.core.support.Constants.validPage;
import static com.ujcms.core.support.Constants.validPageSize;
import static com.ujcms.core.support.UrlConstants.BACKEND_API;

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
        Map<String, Object> queryMap = QueryUtils.getQueryMap(request.getQueryString());
        return MyBatis.toPage(service.selectPage(queryMap, validPage(page), validPageSize(pageSize)));
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