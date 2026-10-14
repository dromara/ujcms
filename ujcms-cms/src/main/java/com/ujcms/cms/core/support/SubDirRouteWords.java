package com.ujcms.cms.core.support;

import java.util.Set;

/**
 * 前台路由保留字（小写）：前台路由的首个字面路径段（如 {@code search}、{@code login}、{@code channel}）。
 * 字面路径段的匹配优先级高于 {@code /{subDir}}，站点子目录取这些值会导致该站点首页不可达，
 * 因此 {@code SiteService.validateAddress} 据此拒绝。由 Web 层实现，Service 层不直接依赖 Web 层。
 *
 * @author PONY
 */
@FunctionalInterface
public interface SubDirRouteWords {
    /**
     * 获取前台路由保留字
     *
     * @return 小写的保留字集合
     */
    Set<String> routeWords();
}
