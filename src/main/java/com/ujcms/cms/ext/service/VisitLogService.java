package com.ujcms.cms.ext.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.core.service.SeqService;
import com.ujcms.cms.ext.domain.VisitLog;
import com.ujcms.cms.ext.domain.VisitPage;
import com.ujcms.cms.ext.domain.VisitStat;
import com.ujcms.cms.ext.domain.VisitTrend;
import com.ujcms.cms.ext.domain.base.VisitLogBase;
import com.ujcms.cms.ext.mapper.VisitLogMapper;
import com.ujcms.cms.ext.service.args.VisitLogArgs;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import static com.ujcms.cms.ext.domain.VisitStat.STAT_MAX_SIZE;

/**
 * 访问日志 Service
 *
 * @author PONY
 */
@Service
public class VisitLogService implements SiteDeleteListener, UserDeleteListener {
    private final VisitLogMapper mapper;
    private final SeqService seqService;

    public VisitLogService(VisitLogMapper mapper, SeqService seqService) {
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(VisitLog bean) {
        bean.setId(seqService.getNextLongVal(VisitLogBase.TABLE_NAME));
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(VisitLog bean) {
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
    public void deleteBeforeDate(OffsetDateTime date) {
        mapper.deleteBeforeDate(date);
    }

    @Nullable
    public VisitLog select(Long id) {
        return mapper.select(id);
    }

    public List<VisitLog> selectList(VisitLogArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), VisitLogBase.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<VisitLog> selectList(VisitLogArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<VisitLog> selectPage(VisitLogArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    /**
     * 统计访客数
     *
     * @param date 在此日期后
     * @return 访客数
     */
    public int countVisitors(OffsetDateTime date) {
        return mapper.countVisitors(date);
    }

    public List<VisitStat> statByType(short type, OffsetDateTime begin, OffsetDateTime end) {
        switch (type) {
            case VisitStat.TYPE_VISITOR:
                return statVisitor(begin, end);
            case VisitStat.TYPE_SOURCE:
                return statSource(begin, end);
            case VisitStat.TYPE_COUNTRY:
                return statCountry(begin, end);
            case VisitStat.TYPE_PROVINCE:
                return statProvince(begin, end);
            case VisitStat.TYPE_DEVICE:
                return statDevice(begin, end);
            case VisitStat.TYPE_OS:
                return statOs(begin, end);
            case VisitStat.TYPE_BROWSER:
                return statBrowser(begin, end);
            case VisitStat.TYPE_SOURCE_TYPE:
                return statSourceType(begin, end);
            default:
                throw new IllegalArgumentException("Unsupported type: " + type);
        }
    }

    /**
     * 统计新老访客
     *
     * @param begin 开始日期
     * @param end   介绍日期
     * @return 统计结果
     */
    public List<VisitStat> statVisitor(OffsetDateTime begin, OffsetDateTime end) {
        return PageMethod.offsetPage(0, STAT_MAX_SIZE, false).doSelectPage(
                () -> mapper.statByName("new_visitor_", begin, end));
    }

    /**
     * 统计来源
     *
     * @param begin 开始日期
     * @param end   介绍日期
     * @return 统计结果
     */
    public List<VisitStat> statSource(OffsetDateTime begin, OffsetDateTime end) {
        return PageMethod.offsetPage(0, STAT_MAX_SIZE, false).doSelectPage(
                () -> mapper.statByName("source_", begin, end));
    }

    /**
     * 统计来源分类
     *
     * @param begin 开始日期
     * @param end   介绍日期
     * @return 统计结果
     */
    public List<VisitStat> statSourceType(OffsetDateTime begin, OffsetDateTime end) {
        return PageMethod.offsetPage(0, STAT_MAX_SIZE, false).doSelectPage(
                () -> mapper.statByName("source_type_", begin, end));
    }

    /**
     * 统计国家
     *
     * @param begin 开始日期
     * @param end   介绍日期
     * @return 统计结果
     */
    public List<VisitStat> statCountry(OffsetDateTime begin, OffsetDateTime end) {
        return PageMethod.offsetPage(0, STAT_MAX_SIZE, false).doSelectPage(
                () -> mapper.statByName("country_", begin, end));
    }

    /**
     * 统计省份
     *
     * @param begin 开始日期
     * @param end   介绍日期
     * @return 统计结果
     */
    public List<VisitStat> statProvince(OffsetDateTime begin, OffsetDateTime end) {
        String country = "中国";
        return PageMethod.offsetPage(0, STAT_MAX_SIZE, false).doSelectPage(
                () -> mapper.statProvince(country, begin, end));
    }

    /**
     * 统计设备
     *
     * @param begin 开始日期
     * @param end   介绍日期
     * @return 统计结果
     */
    public List<VisitStat> statDevice(OffsetDateTime begin, OffsetDateTime end) {
        return PageMethod.offsetPage(0, STAT_MAX_SIZE, false).doSelectPage(
                () -> mapper.statByName("device_", begin, end));
    }

    /**
     * 统计操作系统
     *
     * @param begin 开始日期
     * @param end   介绍日期
     * @return 统计结果
     */
    public List<VisitStat> statOs(OffsetDateTime begin, OffsetDateTime end) {
        return PageMethod.offsetPage(0, STAT_MAX_SIZE, false).doSelectPage(
                () -> mapper.statByName("os_", begin, end));
    }

    /**
     * 统计浏览器
     *
     * @param begin 开始日期
     * @param end   介绍日期
     * @return 统计结果
     */
    public List<VisitStat> statBrowser(OffsetDateTime begin, OffsetDateTime end) {
        return PageMethod.offsetPage(0, STAT_MAX_SIZE, false).doSelectPage(
                () -> mapper.statByName("browser_", begin, end));
    }

    /**
     * 统计受访页面
     *
     * @param begin 开始日期
     * @param end   介绍日期
     * @return 统计结果
     */
    public List<VisitPage> statUrl(OffsetDateTime begin, OffsetDateTime end) {
        return PageMethod.offsetPage(0, STAT_MAX_SIZE, false).doSelectPage(
                () -> mapper.statUrl(begin, end));
    }

    /**
     * 统计入口页面
     *
     * @param begin 开始日期
     * @param end   介绍日期
     * @return 统计结果
     */
    public List<VisitPage> statEntryUrl(OffsetDateTime begin, OffsetDateTime end) {
        return PageMethod.offsetPage(0, STAT_MAX_SIZE, false).doSelectPage(
                () -> mapper.statEntryUrl(begin, end));
    }

    /**
     * 统计趋势
     *
     * @param begin 开始日期
     * @param end   介绍日期
     * @return 统计结果
     */
    public List<VisitTrend> statTrend(OffsetDateTime begin, OffsetDateTime end) {
        return mapper.statTrend(begin, end);
    }

    @Override
    public int deleteListenerOrder() {
        return 100;
    }

    @Override
    public void preSiteDelete(Long siteId) {
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public void preUserDelete(Long userId) {
        mapper.deleteByUserId(userId);
    }
}