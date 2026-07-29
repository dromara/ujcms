package com.ujcms.cms.ext.web.api;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.ViewHistory;
import com.ujcms.cms.ext.service.ViewHistoryService;
import com.ujcms.cms.ext.service.args.ViewHistoryArgs;
import com.ujcms.common.query.QueryUtils;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Views;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;
import static com.ujcms.common.db.MyBatis.springPage;

/**
 * 浏览历史 Controller（见 docs/spec/membership/view-history.md §5）。
 * <p>
 * userId 强制取自安全上下文。记录仅由服务端浏览监听器写入，无创建接口。
 *
 * @author PONY
 */
@Tag(name = "浏览历史接口")
@RestController
@RequestMapping({API + "/view-history", FRONTEND_API + "/view-history"})
public class ViewHistoryController {
    private final ViewHistoryService service;

    public ViewHistoryController(ViewHistoryService service) {
        this.service = service;
    }

    @Operation(summary = "浏览历史列表_ViewHistoryList。获取当前登录用户的浏览历史（最近浏览优先），未登录返回 401")
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping
    public List<ViewHistory> list(HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);
        return service.selectList(ViewHistoryArgs.of().userId(user.getId()), offset, limit);
    }

    @Operation(summary = "浏览历史分页_ViewHistoryPage。获取当前登录用户的浏览历史（最近浏览优先），未登录返回 401")
    @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping("/page")
    public Page<ViewHistory> page(HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        int page = Directives.getPage(params);
        int pageSize = Directives.getPageSize(params);
        return springPage(service.selectPage(ViewHistoryArgs.of().userId(user.getId()), page, pageSize));
    }

    @Operation(summary = "删除单条浏览历史。未登录返回 401。只能删除当前登录用户自己的记录，记录不存在时不报错")
    @DeleteMapping
    public ResponseEntity<Responses.Body> remove(@Parameter(description = "文章ID") @RequestParam Long articleId) {
        User user = Contexts.getCurrentUser();
        ViewHistory bean = service.findByArticleIdAndUserId(articleId, user.getId());
        if (bean != null) {
            service.delete(bean.getId());
        }
        return Responses.ok();
    }

    @Operation(summary = "清空浏览历史。未登录返回 401。清空当前登录用户的全部浏览记录")
    @DeleteMapping("/clear")
    public ResponseEntity<Responses.Body> clear() {
        User user = Contexts.getCurrentUser();
        service.deleteByUserId(user.getId());
        return Responses.ok();
    }
}
