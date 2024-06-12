package com.ujcms.cms.ext.component;

import com.ujcms.cms.core.service.GlobalService;
import com.ujcms.cms.ext.domain.VisitLog;
import com.ujcms.cms.ext.domain.VisitPage;
import com.ujcms.cms.ext.domain.VisitStat;
import com.ujcms.cms.ext.domain.VisitTrend;
import com.ujcms.cms.ext.domain.global.GlobalVisitorCount;
import com.ujcms.cms.ext.mapper.VisitLogMapper;
import com.ujcms.cms.ext.mapper.VisitPageMapper;
import com.ujcms.cms.ext.mapper.VisitStatMapper;
import com.ujcms.cms.ext.mapper.VisitTrendMapper;
import com.ujcms.cms.ext.service.VisitLogService;
import com.ujcms.cms.ext.service.VisitPageService;
import com.ujcms.cms.ext.service.VisitStatService;
import com.ujcms.cms.ext.service.VisitTrendService;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ujcms.cms.core.component.ViewCountService.batchOperator;
import static com.ujcms.cms.ext.domain.VisitStat.*;

/**
 * 访问系统 Service
 *
 * @author PONY
 */
@Component
public class VisitService {
    private final VisitLogService visitLogService;
    private final VisitStatService visitStatService;
    private final VisitPageService visitPageService;
    private final VisitTrendService visitTrendService;
    private final GlobalService globalService;
    private final SnowflakeSequence snowflakeSequence;
    private final SqlSessionTemplate sqlSessionTemplate;

    public VisitService(VisitLogService visitLogService, VisitStatService visitStatService,
                        VisitPageService visitPageService, VisitTrendService visitTrendService,
                        GlobalService globalService, SnowflakeSequence snowflakeSequence,
                        SqlSessionTemplate sqlSessionTemplate) {
        this.visitLogService = visitLogService;
        this.visitStatService = visitStatService;
        this.visitPageService = visitPageService;
        this.visitTrendService = visitTrendService;
        this.globalService = globalService;
        this.snowflakeSequence = snowflakeSequence;
        this.sqlSessionTemplate = sqlSessionTemplate;
    }

    public void updateStat() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startOfDay = now.truncatedTo(ChronoUnit.DAYS);
        // 每天的第一分钟，统计昨天的数据
        if (now.compareTo(startOfDay.plusMinutes(1)) < 0) {
            startOfDay = startOfDay.minusDays(1);
        }
        updateVisitStat(VisitStat.TYPE_VISITOR, startOfDay);
        updateVisitStat(VisitStat.TYPE_SOURCE, startOfDay);
        updateVisitStat(VisitStat.TYPE_COUNTRY, startOfDay);
        updateVisitStat(VisitStat.TYPE_PROVINCE, startOfDay);
        updateVisitStat(VisitStat.TYPE_DEVICE, startOfDay);
        updateVisitStat(VisitStat.TYPE_OS, startOfDay);
        updateVisitStat(VisitStat.TYPE_BROWSER, startOfDay);
        updateVisitStat(VisitStat.TYPE_SOURCE_TYPE, startOfDay);
        updateVisitPageByUrl(startOfDay);
        updateVisitPageByEntryUrl(startOfDay);
        updateVisitTrendByDay(startOfDay);

        // 统计上一分钟数据
        updateVisitTrendByMinute(now.truncatedTo(ChronoUnit.MINUTES).minusMinutes(1));
        OffsetDateTime startOfHour = now.truncatedTo(ChronoUnit.HOURS);
        // 每小时的第一分钟，统计上一个小时的数据
        if (now.compareTo(startOfHour.plusMinutes(1)) < 0) {
            startOfHour = startOfHour.minusHours(1);
        }
        updateVisitTrendByHour(startOfHour);
        OffsetDateTime startOfMonth = startOfDay.withDayOfMonth(1);
        // 每月的第一分钟，统计上一个小时的数据
        if (now.compareTo(startOfMonth.plusMinutes(1)) < 0) {
            startOfMonth = startOfMonth.minusMonths(1);
        }
        updateVisitTrendByMonth(startOfMonth);
    }

    public void updateOnlineVisitors() {
        int onlineMinutes = 20;
        int onlineVisitors = visitLogService.countVisitors(OffsetDateTime.now().minusMinutes(onlineMinutes));
        globalService.updateGlobalData(new GlobalVisitorCount(onlineVisitors));
    }

    public void deleteStatHistory() {
        OffsetDateTime now = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        visitTrendService.deleteBeforeDateString(
                now.minusDays(VisitTrend.DATA_MAX_DAY_FOR_DAY).format(DAY_FORMATTER),
                VisitTrend.PERIOD_DAY);
        visitTrendService.deleteBeforeDateString(
                now.minusDays(VisitTrend.DATA_MAX_DAY_FOR_HOUR).format(HOUR_FORMATTER),
                VisitTrend.PERIOD_HOUR);
        visitTrendService.deleteBeforeDateString(
                now.minusDays(VisitTrend.DATA_MAX_DAY_FOR_MINUTE).format(MINUTE_FORMATTER),
                VisitTrend.PERIOD_MINUTE);
        visitPageService.deleteBeforeDateString(now.minusDays(VisitStat.DATA_MAX_DAY).format(DAY_FORMATTER));
        visitStatService.deleteBeforeDateString(now.minusDays(VisitStat.DATA_MAX_DAY).format(DAY_FORMATTER));
        visitLogService.deleteBeforeDate(now.minusDays(VisitLog.DATA_MAX_DAY));
    }

    public void updateVisitStat(short type, OffsetDateTime startOfDay) {
        String dateString = startOfDay.format(DAY_FORMATTER);
        // 删除原有统计数据
        visitStatService.deleteByDateString(dateString, type);
        List<VisitStat> list = visitLogService.statByType(type, startOfDay, startOfDay.plusDays(1));
        try (SqlSession session = sqlSessionTemplate.getSqlSessionFactory().openSession(ExecutorType.BATCH, false)) {
            VisitStatMapper visitStatMapper = session.getMapper(VisitStatMapper.class);
            // 插入最新统计数据
            batchOperator(session, list, FOREACH_SIZE, visitStatMapper::insertBatch, bean -> {
                bean.setId(snowflakeSequence.nextId());
                bean.setType(type);
                bean.setDateString(dateString);
                return bean;
            });
        }
    }

    public void updateVisitPageByUrl(OffsetDateTime startOfDay) {
        String dateString = startOfDay.format(DAY_FORMATTER);
        // 删除原有统计数据
        visitPageService.deleteByDateString(dateString, VisitPage.TYPE_VISITED_URL);
        List<VisitPage> list = visitLogService.statUrl(startOfDay, startOfDay.plusDays(1));
        try (SqlSession session = sqlSessionTemplate.getSqlSessionFactory().openSession(ExecutorType.BATCH, false)) {
            VisitPageMapper visitPageMapper = session.getMapper(VisitPageMapper.class);
            // 插入最新统计数据
            batchOperator(session, list, FOREACH_SIZE, visitPageMapper::insertBatch, bean -> {
                bean.setId(snowflakeSequence.nextId());
                bean.setDateString(dateString);
                bean.setType(VisitPage.TYPE_VISITED_URL);
                return bean;
            });
        }
    }

    public void updateVisitPageByEntryUrl(OffsetDateTime startOfDay) {
        String dateString = startOfDay.format(DAY_FORMATTER);
        // 删除原有统计数据
        visitPageService.deleteByDateString(dateString, VisitPage.TYPE_ENTRY_URL);
        List<VisitPage> list = visitLogService.statEntryUrl(startOfDay, startOfDay.plusDays(1));
        try (SqlSession session = sqlSessionTemplate.getSqlSessionFactory().openSession(ExecutorType.BATCH, false)) {
            VisitPageMapper visitPageMapper = session.getMapper(VisitPageMapper.class);
            // 插入最新统计数据
            batchOperator(session, list, FOREACH_SIZE, visitPageMapper::insertBatch, bean -> {
                bean.setId(snowflakeSequence.nextId());
                bean.setDateString(dateString);
                bean.setType(VisitPage.TYPE_ENTRY_URL);
                return bean;
            });
        }
    }

    public void updateVisitTrendByMonth(OffsetDateTime startOfMonth) {
        String dateString = startOfMonth.format(MONTH_FORMATTER);
        // 删除原有统计数据
        visitTrendService.deleteByDateString(dateString);
        List<VisitTrend> list = visitTrendService.statByMonth(startOfMonth, startOfMonth.plusMonths(1));
        updateVisitTrend(list, dateString, VisitTrend.PERIOD_MONTH);
    }

    public void updateVisitTrendByDay(OffsetDateTime startOfDay) {
        String dateString = startOfDay.format(DAY_FORMATTER);
        // 删除原有统计数据
        visitTrendService.deleteByDateString(dateString);
        List<VisitTrend> list = visitLogService.statTrend(startOfDay, startOfDay.plusDays(1));
        updateVisitTrend(list, dateString, VisitTrend.PERIOD_DAY);
    }

    public void updateVisitTrendByHour(OffsetDateTime startOfHour) {
        String dateString = startOfHour.format(HOUR_FORMATTER);
        // 删除原有统计数据
        visitTrendService.deleteByDateString(dateString);
        List<VisitTrend> list = visitLogService.statTrend(startOfHour, startOfHour.plusHours(1));
        updateVisitTrend(list, dateString, VisitTrend.PERIOD_HOUR);
    }

    public void updateVisitTrendByMinute(OffsetDateTime startOfMinute) {
        String dateString = startOfMinute.format(MINUTE_FORMATTER);
        // 删除原有统计数据
        visitTrendService.deleteByDateString(dateString);
        List<VisitTrend> list = visitLogService.statTrend(startOfMinute, startOfMinute.plusMinutes(1));
        updateVisitTrend(list, dateString, VisitTrend.PERIOD_MINUTE);
    }

    private void updateVisitTrend(List<VisitTrend> list, String dateString, Short period) {
        try (SqlSession session = sqlSessionTemplate.getSqlSessionFactory().openSession(ExecutorType.BATCH, false)) {
            VisitTrendMapper visitTrendMapper = session.getMapper(VisitTrendMapper.class);
            // 插入最新统计数据
            batchOperator(session, list, FOREACH_SIZE, visitTrendMapper::insertBatch, bean -> {
                bean.setId(snowflakeSequence.nextId());
                bean.setDateString(dateString);
                bean.setPeriod(period);
                return bean;
            });
        }
    }

    public void insertVisitLogIfNotBusy(List<VisitLog> list) {
        // 是否繁忙，如果繁忙则不执行，丢弃数据，防止负载过大
        if (!IS_INSERT_BUSY.getAndSet(true)) {
            try {
                insertVisitLogAndReleaseBusy(list);
            } catch (Exception e) {
                IS_INSERT_BUSY.set(false);
            }
        }
    }

    @Async
    public void insertVisitLogAndReleaseBusy(List<VisitLog> list) {
        doInsertVisitLog(list);
        IS_INSERT_BUSY.set(false);
    }

    @Async
    public void insertVisitLog(List<VisitLog> list) {
        doInsertVisitLog(list);
    }

    private void doInsertVisitLog(List<VisitLog> list) {
        try (SqlSession session = sqlSessionTemplate.getSqlSessionFactory().openSession(ExecutorType.BATCH, false)) {
            VisitLogMapper visitLogMapper = session.getMapper(VisitLogMapper.class);
            // 插入最新统计数据
            batchOperator(session, list, VisitLog.FOREACH_SIZE, visitLogMapper::insertBatch, bean -> {
                bean.setId(snowflakeSequence.nextId());
                return bean;
            });
        }
    }

    public void cacheVisitLog(final VisitLog bean) {
        visitLogCache.add(bean);
        // 预留20条数据空间，也许可以防止并发插入的数据
        int reservedSize = 20;
        if (visitLogCache.size() >= VisitLog.CACHE_SIZE - reservedSize) {
            List<VisitLog> list = visitLogCache;
            visitLogCache = Collections.synchronizedList(new LinkedList<>());
            insertVisitLogIfNotBusy(list);
        }
    }

    public void flushVisitLog() {
        List<VisitLog> list = visitLogCache;
        visitLogCache = Collections.synchronizedList(new LinkedList<>());
        insertVisitLog(list);
    }

    /**
     * 访问日志插入程序是否繁忙
     */
    private static final AtomicBoolean IS_INSERT_BUSY = new AtomicBoolean(false);

    /**
     * 访问缓存。不必每次都插入数据库。
     */
    private List<VisitLog> visitLogCache = Collections.synchronizedList(new LinkedList<>());

}
