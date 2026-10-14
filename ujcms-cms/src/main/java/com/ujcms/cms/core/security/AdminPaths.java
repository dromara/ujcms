package com.ujcms.cms.core.security;

/**
 * 管理后台受限路径判定，供管理终端访问控制类过滤器（IP 白名单、时段限制）共享，避免前缀定义漂移。
 *
 * <p><strong>可靠边界是 {@link #BACKEND_API_PREFIX}</strong>：后台接口前缀固定且始终经过应用（即便前后端分离、
 * nginx 反代，接口仍代理到应用），所有后台数据/操作都经此。{@link #CP_PREFIX} 为控制台静态目录，路径可被改名、
 * 也常由 nginx 独立托管而不经过应用，故仅作同域默认部署下的补强（详见 runbook §2.6）。
 *
 * @author PONY
 */
final class AdminPaths {
    /** 控制台静态资源前缀（同域部署时由应用提供；改名或 nginx 独立托管时需在网关另行限制）。 */
    static final String CP_PREFIX = "/cp/";
    /** 后台接口前缀（始终经由应用，是可靠的管理终端访问控制边界）。 */
    static final String BACKEND_API_PREFIX = "/api/backend/";

    /** 是否为受限后台路径（{@code /cp/**} 或 {@code /api/backend/**}）。 */
    static boolean isProtected(String path) {
        return path != null && (path.startsWith(CP_PREFIX) || path.startsWith(BACKEND_API_PREFIX));
    }

    private AdminPaths() {
        throw new IllegalStateException("Utility class");
    }
}
