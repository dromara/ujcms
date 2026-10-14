package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.security.SeparationOfDuties;
import com.ujcms.cms.core.service.OrgService;
import com.ujcms.cms.core.service.RoleService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.service.args.UserArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.security.Secures;
import com.ujcms.common.web.Entities;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http403Exception;
import com.ujcms.common.web.exception.Http404Exception;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

import static com.ujcms.cms.core.support.Constants.*;
import static com.ujcms.cms.core.support.Constants.validLimit;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.cms.core.web.support.ValidUtils.rankPermission;
import static com.ujcms.common.db.MyBatis.springPage;
import static com.ujcms.common.query.QueryUtils.getQueryMap;

/**
 * 用户 Controller
 *
 * @author PONY
 */
@RestController("backendUserController")
@RequestMapping(BACKEND_API + "/core/user")
public class UserController {
    private final RoleService roleService;
    private final OrgService orgService;
    private final UserService service;
    private final Props props;
    private final SeparationOfDuties separationOfDuties;

    public UserController(RoleService roleService, OrgService orgService, UserService service, Props props,
                          SeparationOfDuties separationOfDuties) {
        this.roleService = roleService;
        this.orgService = orgService;
        this.service = service;
        this.props = props;
        this.separationOfDuties = separationOfDuties;
    }

    /**
     * 列表的查询条件、排序可以关联的字段（docs/spec/article-data-permission/plan.md D24）：用户扩展表是用户自身的字段，
     * 另有组织、用户组的名称
     */
    private static final Set<String> QUERY_JOINS = Set.of("@userExt-*", "org-name", "group-name");

    /**
     * 列表的查询参数：关联按许可名单，手机号 / 邮箱的条件改写到盲索引上（{@link UserService#rewritePiiQuery}）
     */
    private static Map<String, Object> queryMap(HttpServletRequest request) {
        Map<String, Object> queryMap = getQueryMap(request.getQueryString(), QUERY_JOINS);
        UserService.rewritePiiQuery(queryMap);
        return queryMap;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('user:list','*')")
    public Page<User> page(@Nullable Long orgId, @RequestParam(defaultValue = "false") boolean current,
                           Integer page, Integer pageSize, HttpServletRequest request) {
        if (current && orgId == null) {
            orgId = Contexts.getCurrentSite().getOrgId();
        }
        UserArgs args = UserArgs.of(queryMap(request)).orgId(orgId);
        return springPage(service.selectPage(args, validPage(page), validPageSize(pageSize)));
    }

    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('user:list','*')")
    public List<User> list(@Nullable Long orgId, @RequestParam(defaultValue = "false") boolean current,
                           @Nullable Integer offset, @Nullable Integer limit, HttpServletRequest request) {
        if (current && orgId == null) {
            orgId = Contexts.getCurrentSite().getOrgId();
        }
        UserArgs args = UserArgs.of(queryMap(request)).orgId(orgId);
        return service.selectList(args, validOffset(offset), validLimit(limit));
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('user:show','*')")
    public User show(@PathVariable Long id) {
        User bean = service.select(id);
        if (bean == null) {
            throw new Http404Exception(USER_NOT_FOUND + id);
        }
        return bean;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('user:create','*')")
    @OperationLog(module = "user", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody UserParams bean) {
        User currentUser = Contexts.getCurrentUser();
        User user = new User();
        Entities.copy(bean, user, EXCLUDE_FIELDS);
        // customs 是 Map，Entities.copy 不复制非简单值类型，需显式赋值
        user.setCustoms(bean.getCustoms());
        user.setRank((short) (currentUser.getRank() + 1));
        // 后台新建用户：标记首次登录强制改密（仅在 Config.Security.forceChangePasswordOnFirstLogin 开启时于登录处生效）
        user.setPwdMustChange(true);
        validateBean(user, user.getRank(), null, null, null, currentUser);
        service.insert(user, user.getExt(), bean.getOrgIds());
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('user:update','*')")
    @OperationLog(module = "user", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody UserParams bean) {
        Site site = Contexts.getCurrentSite();
        User user = service.select(bean.getId());
        if (user == null) {
            return Responses.notFound(USER_NOT_FOUND + bean.getId());
        }
        User currentUser = Contexts.getCurrentUser();
        Short origRank = user.getRank();
        String origUsername = user.getUsername();
        String origMobile = user.getMobile();
        String origEmail = user.getEmail();
        Entities.copy(bean, user, EXCLUDE_FIELDS);
        // customs 是 Map，Entities.copy 不复制非简单值类型，需显式赋值
        user.setCustoms(bean.getCustoms());
        validateBean(user, origRank, origUsername, origEmail, origMobile, currentUser);
        // 三权分立：变更用户类型时，校验新类型与其已持有角色权限是否冲突（关闭时不访问 roleList，零开销）
        if (separationOfDuties.isEnabled()) {
            separationOfDuties.check(user.getType(), user.getRoleList());
        }
        service.update(user, user.getExt(), bean.isGlobal() ? null : site.getOrgId(), bean.getOrgIds());
        return Responses.ok();
    }

    @PutMapping("permission")
    @PreAuthorize("hasAnyAuthority('user:updatePermission','*')")
    @OperationLog(module = "user", operation = "updatePermission", type = OperationType.UPDATE)
    public ResponseEntity<Body> updatePermission(@RequestBody UserParams bean) {
        User currentUser = Contexts.getCurrentUser();
        User user = service.select(bean.getId());
        if (user == null) {
            return Responses.notFound(USER_NOT_FOUND + bean.getId());
        }
        Short origRank = user.getRank();
        Entities.copyIncludes(bean, user, PERMISSION_FIELDS);
        validatePermission(user.getOrgId(), user.getRank(), origRank, currentUser);
        List<Role> roles = bean.getRoleIds().stream().filter(Objects::nonNull).map(roleService::select)
                .filter(Objects::nonNull).toList();
        roles.forEach(role -> {
            if (role.getRank() < user.getRank()) {
                throw new Http403Exception(String.format("user rank(%d) below then role rank(%d)",
                        bean.getRank(), role.getRank()));
            }
        });
        // 三权分立：校验用户类型与其拟持有权限（所分配角色的并集）是否满足约束（关闭时不做任何校验）
        separationOfDuties.check(user.getType(), roles);
        service.update(user, bean.getRoleIds());
        return Responses.ok();
    }

    public static class UpdateStatusParams {
        @NotNull
        public List<Long> ids;
        @NotNull
        @Min(0)
        @Max(3)
        public Short status;
    }

    @PutMapping("status")
    @PreAuthorize("hasAnyAuthority('user:updateStatus','*')")
    @OperationLog(module = "user", operation = "updateStatus", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateStatus(@RequestBody @Valid UpdateStatusParams params) {
        User currentUser = Contexts.getCurrentUser();
        params.ids.stream().filter(Objects::nonNull).map(service::select).filter(Objects::nonNull).forEach(user -> {
            validatePermission(user.getOrgId(), user.getRank(), user.getRank(), currentUser);
            user.setStatus(params.status);
            service.update(user);
        });
        return Responses.ok();
    }

    public static class UpdatePasswordParams {
        @NotNull
        public Long id;
        @NotBlank
        public String password;
    }

    @PutMapping("password")
    @PreAuthorize("hasAnyAuthority('user:updatePassword','*')")
    @OperationLog(module = "user", operation = "updatePassword", type = OperationType.UPDATE)
    public ResponseEntity<Body> updatePassword(@RequestBody @Valid UpdatePasswordParams params) {
        User currentUser = Contexts.getCurrentUser();
        User user = service.select(params.id);
        if (user == null) {
            return Responses.notFound(USER_NOT_FOUND + params.id);
        }
        validatePermission(user.getOrgId(), user.getRank(), user.getRank(), currentUser);
        String password = Secures.sm2Decrypt(params.password, props.getClientSm2PrivateKey());
        // 管理员重置他人口令：要求该用户下次登录先改密（仅在开启首次登录强制改密时生效）
        service.updatePassword(user, user.getExt(), password, true);
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('user:delete','*')")
    @OperationLog(module = "user", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids) {
        User currentUser = Contexts.getCurrentUser();
        ids.stream().filter(Objects::nonNull).map(service::select).filter(Objects::nonNull).forEach(user ->
                validatePermission(user.getOrgId(), user.getRank(), user.getRank(), currentUser));
        service.delete(ids);
        return Responses.ok();
    }

    @GetMapping("username-exist")
    public boolean usernameExist(@NotBlank String username) {
        return service.selectByUsername(username) != null;
    }

    @GetMapping("email-exist")
    public boolean emailExist(@NotBlank String email) {
        return service.selectByEmail(email) != null;
    }

    @GetMapping("mobile-exist")
    public boolean mobileExist(@NotBlank String mobile) {
        return service.selectByMobile(mobile) != null;
    }

    /** 盲索引回填的批大小。一次性维护操作，按 ID 键集分页逐批独立提交。 */
    private static final int PII_BACKFILL_BATCH_SIZE = 500;

    /**
     * 一次性回填用户手机号 / 邮箱盲索引（并把历史明文转为密文）。仅超级管理员可执行、且记审计日志。
     * <p>
     * 批量、可断点续跑、幂等；遇盲索引冲突或密文无法解密则中止并返回已处理条数与原因（fail-loud），
     * 不会静默丢数据。<strong>不在应用启动时自动运行</strong>。需在数据库迁移（加列 + UNIQUE 索引）
     * 完成后、依赖按索引查询前执行。
     */
    @PostMapping("rebuild-pii-index")
    @PreAuthorize("hasAnyAuthority('*')")
    @OperationLog(module = "user", operation = "rebuildPiiIndex", type = OperationType.OTHER)
    public ResponseEntity<Body> rebuildPiiIndex() {
        long lastId = 0;
        int total = 0;
        try {
            while (true) {
                UserService.PiiBackfillResult result = service.backfillPiiBatch(lastId, PII_BACKFILL_BATCH_SIZE);
                if (result.processed() == 0) {
                    break;
                }
                total += result.processed();
                lastId = result.lastId();
            }
        } catch (RuntimeException e) {
            return Responses.failure("用户 PII 盲索引回填中止，已成功处理 " + total + " 个用户：" + e.getMessage());
        }
        return Responses.ok(new RebuildPiiIndexResult(total));
    }

    /**
     * 盲索引回填结果。
     *
     * @param processed 成功写入索引的用户数
     */
    public record RebuildPiiIndexResult(int processed) {
    }

    private void validatePermission(Long orgId, Short rank, Short origRank, User currentUser) {
        // 没有全局权限且用户不属于当前组织
        if (!currentUser.hasGlobalPermission() &&
                !orgService.isDescendant(currentUser.getOrg().getId(), orgId)) {
            throw new Http403Exception(String.format("user org(ID=%d) not in current org(ID=%d)",
                    orgId, currentUser.getOrgId()));
        }
        // rank权限
        rankPermission(origRank, rank, currentUser.getRank());
    }

    private void validateBean(User user, Short origRank, @Nullable String origUsername, @Nullable String origEmail,
                              @Nullable String origMobile, User currentUser) {
        validatePermission(user.getOrgId(), user.getRank(), origRank, currentUser);
        String username = user.getUsername();
        if (!Objects.equals(username, origUsername) && usernameExist(username)) {
            throw new Http400Exception("username exist: " + username);
        }
        String email = user.getEmail();
        if (!Objects.equals(email, origEmail) && emailExist(email)) {
            throw new Http400Exception("email exist: " + email);
        }
        String mobile = user.getMobile();
        if (!Objects.equals(mobile, origMobile) && mobileExist(mobile)) {
            throw new Http400Exception("mobile exist: " + mobile);
        }
    }

    public static class UserParams extends User {
        private static final long serialVersionUID = 1L;

        private boolean global = false;
        private List<Long> roleIds = new ArrayList<>();
        private List<Long> orgIds = new ArrayList<>();

        public boolean isGlobal() {
            return global;
        }

        public void setGlobal(boolean global) {
            this.global = global;
        }

        public List<Long> getRoleIds() {
            return roleIds;
        }

        public void setRoleIds(List<Long> roleIds) {
            this.roleIds = roleIds;
        }

        public List<Long> getOrgIds() {
            return orgIds;
        }

        public void setOrgIds(List<Long> orgIds) {
            this.orgIds = orgIds;
        }
    }

    /**
     * 需排除的字段。不能直接修改
     */
    private static final String[] EXCLUDE_FIELDS = {"password", "status", "pwdMustChange",
            "created", "loginDate", "loginIp", "loginCount", "errorDate", "errorCount"};
    /**
     * 权限字段
     */
    private static final String[] PERMISSION_FIELDS = {"rank"};

    private static final String USER_NOT_FOUND = "User not found. ID = ";
}