package com.ujcms.cms.ext.web.backendapi;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.cms.ext.domain.VisitStat;
import com.ujcms.cms.ext.domain.VisitTrend;
import com.ujcms.cms.ext.service.VisitStatService;
import com.ujcms.cms.ext.service.VisitTrendService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ujcms.cms.ext.domain.VisitStat.*;

/**
 * 访问统计 Controller
 *
 * @author PONY
 */
@RestController("backendVisitController")
@RequestMapping(UrlConstants.BACKEND_API + "/ext/visit")
public class VisitController {
    private final VisitTrendService visitTrendService;
    private final VisitStatService visitStatService;

    public VisitController(VisitTrendService visitTrendService, VisitStatService visitStatService) {
        this.visitTrendService = visitTrendService;
        this.visitStatService = visitStatService;
    }

    @GetMapping("trend-stat")
    @PreAuthorize("hasAnyAuthority('visitTrend:list','backend','*')")
    public List<VisitTrend> trendStat(
            @RequestParam(name = "begin", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime begin,
            @RequestParam(name = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end) {
        Site site = Contexts.getCurrentSite();
        // 12 时
        int maxForMinute = 12 * 60 * 60;
        // 7 天
        int maxForHour = 14 * 24 * 60 * 60;
        // 6 月
        int maxForDay = 6 * 30 * 24 * 60 * 60;
        if (end == null) {
            end = OffsetDateTime.now();
        }
        long second = end.toEpochSecond() - (begin != null ? begin.toEpochSecond() : 0);
        if (second >= maxForDay) {
            return visitTrendService.selectFullList(site.getId(), VisitTrend.PERIOD_MONTH,
                    Optional.ofNullable(begin).map(date -> date.format(MONTH_FORMATTER)).orElse(null),
                    end.format(MONTH_FORMATTER));
        } else if (second >= maxForHour) {
            return visitTrendService.selectFullList(site.getId(), VisitTrend.PERIOD_DAY,
                    Optional.ofNullable(begin).map(date -> date.format(DAY_FORMATTER)).orElse(null),
                    end.format(DAY_FORMATTER));
        } else if (second >= maxForMinute) {
            return visitTrendService.selectFullList(site.getId(), VisitTrend.PERIOD_HOUR,
                    Optional.ofNullable(begin).map(date -> date.format(HOUR_FORMATTER)).orElse(null),
                    end.format(HOUR_FORMATTER));
        } else {
            return visitTrendService.selectFullList(site.getId(), VisitTrend.PERIOD_MINUTE,
                    Optional.ofNullable(begin).map(date -> date.format(MINUTE_FORMATTER)).orElse(null),
                    end.format(MINUTE_FORMATTER));
        }
    }

    @GetMapping("visitor-stat")
    @PreAuthorize("hasAnyAuthority('visitVisitor:list','backend','*')")
    public Map<String, VisitStat> visitorStat(
            @RequestParam(name = "begin", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate begin,
            @RequestParam(name = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        List<VisitStat> list = stat(TYPE_VISITOR, begin, end);
        String newVisitorName = "1";
        String oldVisitorName = "0";
        VisitStat newVisitor = list.stream().filter(item -> newVisitorName.equals(item.getName())).findFirst()
                .orElse(new VisitStat());
        VisitStat oldVisitor = list.stream().filter(item -> oldVisitorName.equals(item.getName())).findFirst()
                .orElse(new VisitStat());
        Map<String, VisitStat> result = new HashMap<>(16);
        result.put("newVisitor", newVisitor);
        result.put("oldVisitor", oldVisitor);
        return result;
    }

    @GetMapping("province-stat")
    @PreAuthorize("hasAnyAuthority('visitProvince:list','backend','*')")
    public List<VisitStat> provinceStat(
            @RequestParam(name = "begin", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate begin,
            @RequestParam(name = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return stat(TYPE_PROVINCE, begin, end);
    }

    @GetMapping("country-stat")
    @PreAuthorize("hasAnyAuthority('visitCountry:list','backend','*')")
    public List<VisitStat> countryStat(
            @RequestParam(name = "begin", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate begin,
            @RequestParam(name = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return stat(TYPE_COUNTRY, begin, end);
    }

    @GetMapping("source-type-stat")
    @PreAuthorize("hasAnyAuthority('visitSourceType:list','backend','*')")
    public List<VisitStat> sourceTypeStat(
            @RequestParam(name = "begin", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate begin,
            @RequestParam(name = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return stat(TYPE_SOURCE_TYPE, begin, end);
    }

    private List<VisitStat> stat(short type, @Nullable LocalDate begin, @Nullable LocalDate end) {
        Site site = Contexts.getCurrentSite();
        return visitStatService.statByDate(site.getId(), type,
                begin != null ? begin.format(DAY_FORMATTER) : null,
                end != null ? end.format(DAY_FORMATTER) : null);
    }
}
