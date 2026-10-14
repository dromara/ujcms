package com.ujcms.cms.core.security;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理终端访问时段限制（登录时段限制，等保三级 访问控制 可选项 / G5 配套）。
 *
 * <p>对后台路径（{@link AdminPaths}）按**服务器本地时间**判定当前是否落在允许的访问时段内，否则返回 403。
 * 持续生效（会话中途超出时段同样拦截），而非仅登录时校验。
 *
 * <p><strong>基于存在性</strong>：{@code ujcms.security.admin-access-window} 为空即关闭、放行全部；非空即开启。
 * 仅 yaml 配置（不放后台），以保证误配后可改 yaml 重启自救。属可选项——非高风险项，默认关闭，仅在测评机构
 * 明确要求登录时段限制时启用。
 *
 * <p>格式：逗号分隔的多个时段，每段为 {@code <星期> <起>-<止>}：
 * <ul>
 *   <li>星期：{@code MON/TUE/WED/THU/FRI/SAT/SUN}，可写区间 {@code MON-FRI}（区间须 起≤止）；</li>
 *   <li>时间：24 小时制 {@code HH:mm}，区间 {@code 起-止} 取 [起, 止)（须 起&lt;止，不跨零点；跨零点请拆成两段）。</li>
 * </ul>
 * 例：{@code MON-FRI 08:00-20:00, SAT 09:00-12:00}。非法格式在启动时即抛出（fail-loud）。
 *
 * @author PONY
 * @see com.ujcms.cms.core.support.SecurityProperties#getAdminAccessWindow()
 */
public class AdminAccessWindowFilter extends OncePerRequestFilter {
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<String, DayOfWeek> DAY_ABBR = Map.of(
            "MON", DayOfWeek.MONDAY, "TUE", DayOfWeek.TUESDAY, "WED", DayOfWeek.WEDNESDAY,
            "THU", DayOfWeek.THURSDAY, "FRI", DayOfWeek.FRIDAY, "SAT", DayOfWeek.SATURDAY,
            "SUN", DayOfWeek.SUNDAY);

    /** 单个允许时段：一组星期 + 一个日内时间区间 [start, end)。 */
    record Window(Set<DayOfWeek> days, LocalTime start, LocalTime end) {
        boolean matches(DayOfWeek day, LocalTime time) {
            return days.contains(day) && !time.isBefore(start) && time.isBefore(end);
        }
    }

    private final List<Window> windows;

    public AdminAccessWindowFilter(String adminAccessWindow) {
        this.windows = parse(adminAccessWindow);
    }

    /** 解析时段配置；空返回空列表（关闭），格式非法抛出 {@link IllegalArgumentException}（启动期 fail-loud）。 */
    static List<Window> parse(String spec) {
        if (StringUtils.isBlank(spec)) {
            return Collections.emptyList();
        }
        List<Window> list = new ArrayList<>();
        for (String segment : StringUtils.split(spec, ",")) {
            String entry = segment.trim();
            if (entry.isEmpty()) {
                continue;
            }
            String[] parts = entry.split("\\s+");
            if (parts.length != 2) {
                throw new IllegalArgumentException("非法的访问时段配置（应为 \"<星期> HH:mm-HH:mm\"）：" + entry);
            }
            Set<DayOfWeek> days = parseDays(parts[0]);
            String[] times = parts[1].split("-");
            if (times.length != 2) {
                throw new IllegalArgumentException("非法的时间区间（应为 HH:mm-HH:mm）：" + entry);
            }
            LocalTime start = parseTime(times[0], entry);
            LocalTime end = parseTime(times[1], entry);
            if (!start.isBefore(end)) {
                throw new IllegalArgumentException("访问时段起始须早于结束（不跨零点）：" + entry);
            }
            list.add(new Window(days, start, end));
        }
        return list;
    }

    private static Set<DayOfWeek> parseDays(String token) {
        String[] range = token.toUpperCase().split("-");
        if (range.length == 1) {
            return EnumSet.of(day(range[0], token));
        }
        if (range.length == 2) {
            int from = day(range[0], token).getValue();
            int to = day(range[1], token).getValue();
            if (from > to) {
                throw new IllegalArgumentException("非法的星期区间（须 起≤止）：" + token);
            }
            EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
            for (int v = from; v <= to; v++) {
                days.add(DayOfWeek.of(v));
            }
            return days;
        }
        throw new IllegalArgumentException("非法的星期配置：" + token);
    }

    private static DayOfWeek day(String abbr, String token) {
        DayOfWeek d = DAY_ABBR.get(abbr.trim().toUpperCase());
        if (d == null) {
            throw new IllegalArgumentException("非法的星期缩写（应为 MON..SUN）：" + token);
        }
        return d;
    }

    private static LocalTime parseTime(String time, String entry) {
        try {
            return LocalTime.parse(time.trim(), HHMM);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("非法的时间（应为 24 小时制 HH:mm）：" + entry, e);
        }
    }

    /** 当前是否启用（配置非空）。 */
    public boolean isEnabled() {
        return !windows.isEmpty();
    }

    /** 给定星期与时间是否落在任一允许时段内。包级可见以便单元测试（不依赖墙钟）。 */
    static boolean isWithin(List<Window> windows, DayOfWeek day, LocalTime time) {
        for (Window window : windows) {
            if (window.matches(day, time)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 关闭（空配置）或非后台路径：放行
        if (windows.isEmpty() || !AdminPaths.isProtected(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (isWithin(windows, now.getDayOfWeek(), now.toLocalTime())) {
            chain.doFilter(request, response);
            return;
        }
        logger.warn("Admin access outside allowed window, denied: " + now + " -> " + request.getRequestURI());
        response.sendError(HttpServletResponse.SC_FORBIDDEN,
                "当前不在允许的管理终端访问时段内 (admin access not allowed at this time)");
    }
}
