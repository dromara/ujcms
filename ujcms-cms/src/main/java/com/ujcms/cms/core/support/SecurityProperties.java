package com.ujcms.cms.core.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 等保三级 / 密评三级 合规相关的基础设施层开关。
 * <p>
 * 全部默认关闭，开箱即用行为不变。属于“硬/防回滚”项：在 {@code application.yaml} 或
 * Spring Profile {@code level3} 中设置，登录后的管理员无法从后台界面将其关闭。
 * 运行时策略类开关（首次登录强制改密、口令策略、双因子等）仍在 {@code Config.Security}。
 *
 * @author PONY
 * @see com.ujcms.cms.core.domain.Config.Security
 */
@ConfigurationProperties("ujcms.security")
public class SecurityProperties {
    /**
     * 审计完整性：开启后，操作日志 / 登录日志写入时按行计算 HMAC-SM3 签名（密评 日志完整性）。
     */
    private boolean auditIntegrity = false;
    /**
     * 审计防删改：开启后，禁止通过应用删除审计日志（含级联删除用户/站点时的日志），从应用层杜绝绕过。
     */
    private boolean auditImmutable = false;
    /**
     * 审计留存天数：大于 0 时，拒绝删除创建时间在该窗口内的审计日志。0 表示不限制。
     */
    private int auditRetainDays = 0;
    /**
     * 管理终端 IP 白名单（CIDR，逗号分隔）。空表示关闭。Phase 3 启用，此处先声明。
     */
    private String adminIpWhitelist = "";
    /**
     * 管理终端访问时段（登录时段限制，等保三级 访问控制 可选项）。空表示关闭（不限制）。
     * 格式：逗号分隔的多个时段，每段为 {@code <星期> <起>-<止>}，星期用 MON/TUE/WED/THU/FRI/SAT/SUN
     * （可写区间，如 {@code MON-FRI}），时间为 24 小时制 {@code HH:mm}，区间为 [起, 止)。
     * 例：{@code MON-FRI 08:00-20:00, SAT 09:00-12:00}。按服务器本地时间判定，对 {@code /cp/**} 与
     * {@code /api/backend/**} 持续生效（会话中途超出时段亦拦截）。仅 yaml 配置，便于误配后改 yaml 重启自救。
     */
    private String adminAccessWindow = "";
    /**
     * 拒绝默认密钥：开启后，若 {@code ujcms.data-secret-key} / {@code ujcms.password-pepper}
     * 仍为内置默认值则启动失败。Phase 5 启用，此处先声明。
     */
    private boolean rejectDefaultKey = false;
    /**
     * 三权分立（等保三级 访问控制）。开启后：
     * <ol>
     *   <li>超级管理员（id=1）被强制停用且不可重新启用，{@code User.isRoot()} 返回 {@code false}，消除全局权限旁路；</li>
     *   <li>审计权限（{@code operationLog:*} / {@code loginLog:*}）仅可授予审计管理员，禁止授予系统/安全管理员，反之亦然；</li>
     *   <li>禁止单一账号同时拥有系统/安全/审计三类权限（{@code *} 全权限因此不可授予任何账号）。</li>
     * </ol>
     * 属<strong>防回滚</strong>项（约束过权管理员、中和 root），故按设计原则 #1 仅在 yaml / {@code level3} Profile
     * 中设置，登录后的管理员无法从后台界面关闭（D15）。
     */
    private boolean separationOfDuties = false;
    /**
     * 传输安全加固（等保三级 传输 / G6 应用侧，Phase 4）。开启后：发送 HSTS 响应头、会话/记住我 Cookie 置
     * {@code Secure} 与 {@code SameSite=Lax}。仅作应用侧补强与测评勾选——真正的传输保密性由网关 TLS / 国密 SSL 承担。
     * <p>⚠️ 仅当站点确实经 HTTPS 提供时开启；否则 {@code Secure} Cookie 在 HTTP 下不会被浏览器回传，将导致会话异常。
     * 经反向代理时建议同时设置 {@code server.forward-headers-strategy: framework} 以正确识别 X-Forwarded-Proto。
     */
    private boolean httpsEnabled = false;
    /**
     * 应急开关：停用双因子认证（break-glass）。默认关闭。
     * <p>
     * 双因子认证在后台（全局设置）开启后，若短信服务故障、或管理员登记的手机号码有误，可能无人能登录、也就无人能在后台纠正。
     * 此时由有服务器权限的运维人员以 {@code --ujcms.security.disable-two-factor=true} 重启：登录只校验口令，
     * 登录后修正手机号码 / 短信设置，再去掉该参数重启。能改启动参数的人本就拥有高于后台的权限，故不构成提权。
     * <p>
     * 开启期间启动时与每次登录都会记录 WARN 日志。集群需在每个节点上设置。
     */
    private boolean disableTwoFactor = false;

    public boolean isAuditIntegrity() {
        return auditIntegrity;
    }

    public void setAuditIntegrity(boolean auditIntegrity) {
        this.auditIntegrity = auditIntegrity;
    }

    public boolean isAuditImmutable() {
        return auditImmutable;
    }

    public void setAuditImmutable(boolean auditImmutable) {
        this.auditImmutable = auditImmutable;
    }

    public int getAuditRetainDays() {
        return auditRetainDays;
    }

    public void setAuditRetainDays(int auditRetainDays) {
        this.auditRetainDays = auditRetainDays;
    }

    public String getAdminIpWhitelist() {
        return adminIpWhitelist;
    }

    public void setAdminIpWhitelist(String adminIpWhitelist) {
        this.adminIpWhitelist = adminIpWhitelist;
    }

    public String getAdminAccessWindow() {
        return adminAccessWindow;
    }

    public void setAdminAccessWindow(String adminAccessWindow) {
        this.adminAccessWindow = adminAccessWindow;
    }

    public boolean isRejectDefaultKey() {
        return rejectDefaultKey;
    }

    public void setRejectDefaultKey(boolean rejectDefaultKey) {
        this.rejectDefaultKey = rejectDefaultKey;
    }

    public boolean isSeparationOfDuties() {
        return separationOfDuties;
    }

    /**
     * 绑定 {@code ujcms.security.separation-of-duties} 时同步下发到 {@link StaticProps}，供无法注入的领域层
     * （{@link com.ujcms.cms.core.domain.User}）静态读取。镜像 {@link Props#setDataSecretKey(String)} 的做法。
     */
    public void setSeparationOfDuties(boolean separationOfDuties) {
        this.separationOfDuties = separationOfDuties;
        StaticProps.setSeparationOfDuties(separationOfDuties);
    }

    public boolean isHttpsEnabled() {
        return httpsEnabled;
    }

    public void setHttpsEnabled(boolean httpsEnabled) {
        this.httpsEnabled = httpsEnabled;
    }

    public boolean isDisableTwoFactor() {
        return disableTwoFactor;
    }

    public void setDisableTwoFactor(boolean disableTwoFactor) {
        this.disableTwoFactor = disableTwoFactor;
    }
}
