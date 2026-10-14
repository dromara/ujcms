package com.ujcms.cms.core.security;

import com.ujcms.cms.core.security.AdminAccessWindowFilter.Window;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理终端访问时段：解析（多段、星期区间、边界、非法 fail-loud）、时段判定（含 [起,止) 边界）、
 * 过滤器放行/拦截（空配置放行、非后台路径放行、非当日时段 403）。
 */
class AdminAccessWindowFilterTest {

    // region parse
    @Test
    void parse_emptyIsOff() {
        assertTrue(AdminAccessWindowFilter.parse("").isEmpty());
        assertTrue(AdminAccessWindowFilter.parse("   ").isEmpty());
        assertFalse(new AdminAccessWindowFilter("").isEnabled());
    }

    @Test
    void parse_multipleWindows() {
        List<Window> windows = AdminAccessWindowFilter.parse("MON-FRI 08:00-20:00, SAT 09:00-12:00");
        assertEquals(2, windows.size());
        assertTrue(new AdminAccessWindowFilter("MON-FRI 08:00-20:00").isEnabled());
    }

    @Test
    void parse_rejectsMalformed() {
        assertThrows(IllegalArgumentException.class, () -> AdminAccessWindowFilter.parse("garbage"));
        assertThrows(IllegalArgumentException.class, () -> AdminAccessWindowFilter.parse("MON 99:00-10:00"));
        assertThrows(IllegalArgumentException.class, () -> AdminAccessWindowFilter.parse("XYZ 08:00-09:00"));
        assertThrows(IllegalArgumentException.class, () -> AdminAccessWindowFilter.parse("MON 20:00-08:00"));
        assertThrows(IllegalArgumentException.class, () -> AdminAccessWindowFilter.parse("MON 08:00-08:00"));
        assertThrows(IllegalArgumentException.class, () -> AdminAccessWindowFilter.parse("FRI-MON 08:00-09:00"));
    }
    // endregion

    // region isWithin
    @Test
    void isWithin_dayRangeAndTimeBoundary() {
        List<Window> w = AdminAccessWindowFilter.parse("MON-FRI 08:00-20:00");
        assertTrue(AdminAccessWindowFilter.isWithin(w, DayOfWeek.MONDAY, LocalTime.of(8, 0)));   // 含起
        assertTrue(AdminAccessWindowFilter.isWithin(w, DayOfWeek.FRIDAY, LocalTime.of(19, 59)));
        assertFalse(AdminAccessWindowFilter.isWithin(w, DayOfWeek.MONDAY, LocalTime.of(7, 59)));
        assertFalse(AdminAccessWindowFilter.isWithin(w, DayOfWeek.MONDAY, LocalTime.of(20, 0))); // 不含止
        assertFalse(AdminAccessWindowFilter.isWithin(w, DayOfWeek.SATURDAY, LocalTime.of(10, 0)));
    }

    @Test
    void isWithin_multipleWindows() {
        List<Window> w = AdminAccessWindowFilter.parse("MON-FRI 08:00-20:00, SAT 09:00-12:00");
        assertTrue(AdminAccessWindowFilter.isWithin(w, DayOfWeek.SATURDAY, LocalTime.of(10, 0)));
        assertFalse(AdminAccessWindowFilter.isWithin(w, DayOfWeek.SATURDAY, LocalTime.of(12, 0)));
        assertFalse(AdminAccessWindowFilter.isWithin(w, DayOfWeek.SUNDAY, LocalTime.of(10, 0)));
    }
    // endregion

    // region filter
    private int run(AdminAccessWindowFilter filter, String uri) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, new MockFilterChain());
        return resp.getStatus();
    }

    @Test
    void filter_emptyConfigPassesEverything() throws Exception {
        AdminAccessWindowFilter filter = new AdminAccessWindowFilter("");
        assertEquals(HttpServletResponse.SC_OK, run(filter, "/cp/index"));
        assertEquals(HttpServletResponse.SC_OK, run(filter, "/api/backend/core/user"));
    }

    @Test
    void filter_nonAdminPathAlwaysPasses() throws Exception {
        // 用一个绝不包含今天的时段：非后台路径仍应放行
        String notToday = abbr(LocalDate.now().getDayOfWeek().plus(1));
        AdminAccessWindowFilter filter = new AdminAccessWindowFilter(notToday + " 00:00-23:59");
        assertEquals(HttpServletResponse.SC_OK, run(filter, "/article/1"));
        assertEquals(HttpServletResponse.SC_OK, run(filter, "/api/login"));
    }

    @Test
    void filter_blocksAdminPathOutsideWindow() throws Exception {
        // 时段设在“明天”这个星期几，今天必不在其内 → 后台路径 403
        String notToday = abbr(LocalDate.now().getDayOfWeek().plus(1));
        AdminAccessWindowFilter filter = new AdminAccessWindowFilter(notToday + " 00:00-23:59");
        assertEquals(HttpServletResponse.SC_FORBIDDEN, run(filter, "/cp/index"));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, run(filter, "/api/backend/core/user"));
    }
    // endregion

    private static String abbr(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "MON";
            case TUESDAY -> "TUE";
            case WEDNESDAY -> "WED";
            case THURSDAY -> "THU";
            case FRIDAY -> "FRI";
            case SATURDAY -> "SAT";
            case SUNDAY -> "SUN";
        };
    }
}
