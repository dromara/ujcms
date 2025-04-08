package com.ujcms.cms.ext.web.api;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.Example;
import com.ujcms.cms.ext.service.ExampleService;
import com.ujcms.cms.ext.service.args.ExampleArgs;
import com.ujcms.commons.captcha.CaptchaTokenService;
import com.ujcms.commons.query.QueryUtils;
import com.ujcms.commons.web.Entities;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Views;
import com.ujcms.commons.web.exception.Http400Exception;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;
import java.util.Map;

import static com.ujcms.commons.db.MyBatis.springPage;
import static com.ujcms.commons.query.QueryUtils.QUERY_PREFIX;

/**
 * 示例 API Controller
 *
 * @author PONY
 */
// @Tag(name = "ExampleController", description = "示例接口")
// @RestController
// @RequestMapping({API + "/example", FRONTEND_API + "/example"})
public class ExampleController {
    private final CaptchaTokenService captchaTokenService;
    private final ExampleService service;

    public ExampleController(CaptchaTokenService captchaTokenService, ExampleService service) {
        this.captchaTokenService = captchaTokenService;
        this.service = service;
    }

    @Operation(summary = "示例列表_ExampleList")
    @Parameter(in = ParameterIn.QUERY, name = "name", description = "示例名称",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping
    public List<Example> list(String name, HttpServletRequest request) {
        // 获取查询参数
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        // 获取标签其它查询参数。非必须，可用 `ExampleArgs args = ExampleArgs.of();` 代替。
        ExampleArgs args = ExampleArgs.of(QueryUtils.getQueryMap(params, QUERY_PREFIX));
        // 加入name查询条件
        args.name(name);
        // 数据起始位置
        int offset = Directives.getOffset(params);
        // 数据最大条数
        int limit = Directives.getLimit(params);
        // 返回接口数据
        return service.selectList(args, offset, limit);
    }

    @Operation(summary = "示例分页_ExamplePage")
    @Parameter(in = ParameterIn.QUERY, name = "name", description = "示例名称",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping("/page")
    public Page<Example> page(String name, HttpServletRequest request) {
        // 获取查询参数
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        // 获取标签其它查询参数。非必须，可用 `ExampleArgs args = ExampleArgs.of();` 代替。
        ExampleArgs args = ExampleArgs.of(QueryUtils.getQueryMap(params, QUERY_PREFIX));
        // 加入name查询条件
        args.name(name);
        // 第几页
        int page = Directives.getPage(params);
        // 每页条数
        int pageSize = Directives.getPageSize(params);
        // 返回接口数据
        return springPage(service.selectPage(args, page, pageSize));
    }

    @Operation(summary = "示例对象_Example")
    @ApiResponses(value = {@ApiResponse(description = "投票对象")})
    @GetMapping("/{id:[\\d]+}")
    public Example show(@Parameter(description = "示例ID") @PathVariable Long id) {
        return service.select(id);
    }

    @Operation(summary = "提交示例")
    @PostMapping
    public ResponseEntity<Responses.Body> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "示例对象")
            @RequestBody @Valid ExampleParams params) {
        if (!captchaTokenService.validateCaptcha(params.captchaToken, params.captcha)) {
            throw new Http400Exception("Captcha is incorrect.");
        }
        Example example = new Example();
        Entities.copy(params, example);
        service.insert(example);
        return Responses.ok();
    }

    @Schema(name = "ExampleController.ExampleParams", description = "示例参数")
    public static class ExampleParams extends Example {
        private static final long serialVersionUID = 1L;

        @Schema(description = "验证码")
        private String captcha;
        @Schema(description = "验证码TOKEN")
        private String captchaToken;

        public String getCaptcha() {
            return captcha;
        }

        public void setCaptcha(String captcha) {
            this.captcha = captcha;
        }

        public String getCaptchaToken() {
            return captchaToken;
        }

        public void setCaptchaToken(String captchaToken) {
            this.captchaToken = captchaToken;
        }
    }
}
