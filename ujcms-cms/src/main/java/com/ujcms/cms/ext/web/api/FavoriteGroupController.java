package com.ujcms.cms.ext.web.api;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.ext.domain.FavoriteGroup;
import com.ujcms.cms.ext.service.FavoriteGroupService;
import com.ujcms.cms.ext.service.args.FavoriteGroupArgs;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Views;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http404Exception;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.Length;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;

/**
 * 收藏夹 Controller
 *
 * @author PONY
 */
@Tag(name = "收藏夹接口")
@RestController
@RequestMapping({API + "/favorite-group", FRONTEND_API + "/favorite-group"})
public class FavoriteGroupController {
    private final FavoriteGroupService service;

    public FavoriteGroupController(FavoriteGroupService service) {
        this.service = service;
    }

    @Operation(summary = "收藏夹列表_FavoriteGroupList。获取当前登录用户的收藏夹，未登录返回 401")
    @JsonView(Views.List.class)
    @GetMapping
    public List<FavoriteGroup> list() {
        User user = Contexts.getCurrentUser();
        return service.selectList(FavoriteGroupArgs.of().userId(user.getId()));
    }

    @Operation(summary = "创建收藏夹。未登录返回 401")
    @PostMapping
    public ResponseEntity<Responses.Body> create(@RequestBody @Valid FavoriteGroupParams params) {
        User user = Contexts.getCurrentUser();
        if (StringUtils.isBlank(params.getName())) {
            throw new Http400Exception("FavoriteGroup name is required.");
        }
        FavoriteGroup bean = new FavoriteGroup();
        bean.setName(params.getName().trim());
        service.insert(bean, user.getId());
        return Responses.ok();
    }

    @Operation(summary = "修改收藏夹名称。未登录返回 401。只能修改当前登录用户自己的收藏夹")
    @PutMapping
    public ResponseEntity<Responses.Body> update(@RequestBody @Valid FavoriteGroupParams params) {
        User user = Contexts.getCurrentUser();
        Long id = params.getId();
        if (id == null) {
            throw new Http400Exception("FavoriteGroup id is required.");
        }
        if (StringUtils.isBlank(params.getName())) {
            throw new Http400Exception("FavoriteGroup name is required.");
        }
        FavoriteGroup bean = service.select(id);
        if (bean == null || !bean.getUserId().equals(user.getId())) {
            throw new Http404Exception(FavoriteGroup.NOT_FOUND + id);
        }
        bean.setName(params.getName().trim());
        service.update(bean);
        return Responses.ok();
    }

    @Operation(summary = "删除收藏夹。未登录返回 401。只能删除当前登录用户自己的收藏夹，收藏夹内的收藏移至未分组")
    @DeleteMapping
    public ResponseEntity<Responses.Body> delete(@Parameter(description = "收藏夹ID") @RequestParam Long id) {
        User user = Contexts.getCurrentUser();
        FavoriteGroup bean = service.select(id);
        if (bean == null || !bean.getUserId().equals(user.getId())) {
            throw new Http404Exception(FavoriteGroup.NOT_FOUND + id);
        }
        service.delete(id);
        return Responses.ok();
    }

    @Schema(name = "FavoriteGroupController.FavoriteGroupParams", description = "收藏夹参数")
    public static class FavoriteGroupParams implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 收藏夹ID。创建时不用传
         */
        @Nullable
        @Schema(description = "收藏夹ID。创建时不用传")
        private Long id;

        /**
         * 名称
         */
        @Length(max = 100)
        @NotNull
        @Schema(description = "名称")
        private String name = "";

        @Nullable
        public Long getId() {
            return id;
        }

        public void setId(@Nullable Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
