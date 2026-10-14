package com.ujcms.cms.core.security;

import com.ujcms.common.web.Servlets;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 管理终端 IP 白名单（等保三级 入侵防范 / G5）。
 *
 * <p>对后台路径（{@code /cp/**} 与 {@code /api/backend/**}）按客户端真实 IP（经
 * {@code ujcms.ip-proxy-depth} 解析的 {@link Servlets#getRemoteAddr}）匹配 CIDR / 单 IP 白名单，
 * 不在白名单内则返回 403。
 *
 * <p><strong>基于存在性（presence-based，D2）</strong>：白名单为空即关闭、放行全部请求；非空即开启。
 * 白名单来自 {@code ujcms.security.admin-ip-whitelist}，仅在 {@code application.yaml} / {@code level3}
 * Profile 中配置（不放后台），以保证误配后可改 yaml 重启自救，不会把运维锁在门外。登录接口
 * （{@code /api/login}）与前台不受限——后台动作均在上述两前缀下。
 *
 * <p>白名单条目非法（无法解析为 IP/CIDR）会在启动时即抛出（fail-loud），避免悄然失效放行所有人。
 *
 * <p>客户端地址经 {@link Servlets#getRemoteAddr} 规范化（IPv4 映射地址按 IPv4 匹配，IPv6 大小写/压缩写法等价）；
 * 无法识别为 IP 的地址一律按不在白名单处理，返回 403 而不是 500。
 *
 * @author PONY
 * @see com.ujcms.cms.core.support.SecurityProperties#getAdminIpWhitelist()
 */
public class AdminIpWhitelistFilter extends OncePerRequestFilter {
    private final List<IpAddressMatcher> matchers;
    private final int proxyDepth;

    /**
     * @param adminIpWhitelist CIDR / 单 IP 列表，逗号分隔；空表示关闭
     * @param proxyDepth       信任的代理层数（{@code ujcms.ip-proxy-depth}）
     */
    public AdminIpWhitelistFilter(String adminIpWhitelist, int proxyDepth) {
        this.matchers = parse(adminIpWhitelist);
        this.proxyDepth = proxyDepth;
    }

    /** 解析白名单为匹配器列表；条目非法时直接抛出 {@link IllegalArgumentException}（启动期 fail-loud）。 */
    static List<IpAddressMatcher> parse(String adminIpWhitelist) {
        if (StringUtils.isBlank(adminIpWhitelist)) {
            return Collections.emptyList();
        }
        List<IpAddressMatcher> list = new ArrayList<>();
        for (String token : StringUtils.split(adminIpWhitelist, ",")) {
            String entry = token.trim();
            if (!entry.isEmpty()) {
                list.add(new IpAddressMatcher(entry));
            }
        }
        return list;
    }

    /** 白名单是否启用（非空）。 */
    public boolean isEnabled() {
        return !matchers.isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 关闭（空白名单）或非后台路径：放行
        if (matchers.isEmpty() || !AdminPaths.isProtected(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        String ip = Servlets.getRemoteAddr(request, proxyDepth);
        for (IpAddressMatcher matcher : matchers) {
            try {
                if (matcher.matches(ip)) {
                    chain.doFilter(request, response);
                    return;
                }
            } catch (IllegalArgumentException e) {
                // 非 IP 字符串（如容器给出的非 IP 直连地址）：任何条目都不可能命中，按不在白名单处理（403 而非 500）
                break;
            }
        }
        logger.warn("Admin terminal IP not in whitelist, denied: " + ip + " -> " + request.getRequestURI());
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "管理终端 IP 不在白名单内 (admin terminal IP not allowed)");
    }
}
