package com.ujcms.cms.ext.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.ext.domain.VisitStat;
import com.ujcms.cms.ext.domain.VisitTrend;
import com.ujcms.cms.ext.domain.base.VisitTrendBase;
import com.ujcms.cms.ext.mapper.VisitTrendMapper;
import com.ujcms.cms.ext.service.args.VisitTrendArgs;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import static com.ujcms.cms.ext.domain.VisitStat.*;

/**
 * 访问趋势 Service
 *
 * @author PONY
 */
@Service
public class VisitTrendService implements SiteDeleteListener {
    private final VisitTrendMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public VisitTrendService(VisitTrendMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(VisitTrend bean) {
        bean.setId(snowflakeSequence.nextId());
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(VisitTrend bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteBeforeDateString(String dateString, short period) {
        mapper.deleteBeforeDateString(dateString, period);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteByDateString(String dateString) {
        mapper.deleteByDateString(dateString);
    }

    @Nullable
    public VisitTrend select(Long id) {
        return mapper.select(id);
    }

    public List<VisitTrend> selectList(VisitTrendArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), VisitTrendBase.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<VisitTrend> selectList(VisitTrendArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<VisitTrend> selectPage(VisitTrendArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    public List<VisitTrend> statByMonth(OffsetDateTime begin, OffsetDateTime end) {
        return mapper.statByDate(VisitTrend.PERIOD_DAY, begin.format(DAY_FORMATTER), end.format(DAY_FORMATTER));
    }

    public List<VisitTrend> selectFullList(@Nullable Long siteId, short period, @Nullable String begin, String end) {
        VisitTrendArgs args = VisitTrendArgs.of().siteId(siteId).period(period)
                .begin(begin).end(end).orderByDateString();
        switch (period) {
            case VisitTrend.PERIOD_MONTH:
                return VisitStat.fillEmptyDate(selectList(args), begin, end, MONTH_FORMATTER, MONTH_DISPLAY_FORMATTER,
                        date -> date.plusMonths(1), VisitTrend::new);
            case VisitTrend.PERIOD_DAY:
                return VisitStat.fillEmptyDate(selectList(args), begin, end, DAY_FORMATTER, DAY_DISPLAY_FORMATTER,
                        date -> date.plusDays(1), VisitTrend::new);
            case VisitTrend.PERIOD_HOUR:
                return VisitStat.fillEmptyDate(selectList(args), begin, end, HOUR_FORMATTER, HOUR_DISPLAY_FORMATTER,
                        date -> date.plusHours(1), VisitTrend::new);
            case VisitTrend.PERIOD_MINUTE:
                return VisitStat.fillEmptyDate(selectList(args), begin, end, MINUTE_FORMATTER, MINUTE_DISPLAY_FORMATTER,
                        date -> date.plusMinutes(1), VisitTrend::new);
            default:
                throw new IllegalArgumentException("period not supported: " + period);
        }
    }

    @Override
    public int deleteListenerOrder() {
        return 100;
    }

    @Override
    public void preSiteDelete(Long siteId) {
        mapper.deleteBySiteId(siteId);
    }
}