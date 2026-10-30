package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.domain.User;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http403Exception;
import org.springframework.security.core.GrantedAuthority;
import org.jspecify.annotations.Nullable;

/**
 * 校验工具类
 *
 * @author PONY
 */
public final class ValidUtils {
    /**
     * 校验数据的 站点ID 是否和当前 站点ID 一致
     *
     * @param dataSiteId    数据站点ID
     * @param currentSiteId 当前站点ID
     */
    public static void dataInSite(@Nullable Long dataSiteId, Long currentSiteId) {
        if (dataSiteId != null && !dataSiteId.equals(currentSiteId)) {
            throw new Http400Exception(String.format("data site id '%d' not in current site id '%d'", dataSiteId, currentSiteId));
        }
    }

    /**
     * 校验是否有全局数据管理权限
     *
     * @param isGlobal            是否全局数据
     * @param hasGlobalPermission 是否有全局数据权限
     */
    public static void globalPermission(boolean isGlobal, boolean hasGlobalPermission) {
        if (isGlobal && !hasGlobalPermission) {
            throw new Http403Exception("global data forbidden");
        }
    }

    /**
     * 校验用户等级是否低于数据等级
     *
     * @param origRank 数据原等级
     * @param newRank  数据新等级
     * @param userRank 用户等级
     */
    public static void rankPermission(Short origRank, Short newRank, Short userRank) {
        short rank = origRank > newRank ? newRank : origRank;
        if (userRank > rank) {
            throw new Http403Exception(String.format("user rank(%d) below then data rank(%d)", userRank, rank));
        }
    }

    /**
     * 要求当前用户持有某个权限码（或超级权限 {@code *}）。给方法上 {@code @PreAuthorize} 之外、按参数才需要的权限用：
     * 批量新建用户时给了角色，须另有“权限设置”的权限
     */
    public static void requireAuthority(User user, String permission) {
        boolean granted = user.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> permission.equals(authority) || "*".equals(authority));
        if (!granted) {
            throw new Http403Exception("Permission denied: " + permission);
        }
    }

    private ValidUtils() {
    }
}
