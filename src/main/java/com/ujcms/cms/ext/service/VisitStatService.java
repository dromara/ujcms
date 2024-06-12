package com.ujcms.cms.ext.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.service.SeqService;
import com.ujcms.cms.ext.domain.VisitStat;
import com.ujcms.cms.ext.domain.base.VisitStatBase;
import com.ujcms.cms.ext.mapper.VisitStatMapper;
import com.ujcms.cms.ext.service.args.VisitStatArgs;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static com.ujcms.cms.ext.domain.VisitStat.DAY_DISPLAY_FORMATTER;
import static com.ujcms.cms.ext.domain.VisitStat.DAY_FORMATTER;

/**
 * 访问统计 Service
 *
 * @author PONY
 */
@Service
public class VisitStatService implements SiteDeleteListener {
    private final VisitStatMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public VisitStatService(VisitStatMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(VisitStat bean) {
        bean.setId(snowflakeSequence.nextId());
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(VisitStat bean) {
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

    @Nullable
    public VisitStat select(Long id) {
        return mapper.select(id);
    }

    public List<VisitStat> selectList(VisitStatArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), VisitStatBase.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<VisitStat> selectList(VisitStatArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<VisitStat> selectPage(VisitStatArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    /**
     * 按日期统计
     *
     * @param siteId 站点ID
     * @param type   类型
     * @param begin  开始日期
     * @param end    介绍日期
     * @return 统计结果
     */
    public List<VisitStat> statByDate(Long siteId, Short type, @Nullable String begin, @Nullable String end) {
        return PageMethod.offsetPage(0, VisitStat.DISPLAY_MAX_SIZE, false)
                .doSelectPage(() -> mapper.statByDate(siteId, type, begin, end));
    }

    public Page<VisitStat> statByDate(Long siteId, Short type, @Nullable String begin, @Nullable String end,
                                      int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> mapper.statByDate(siteId, type, begin, end));
    }

    public List<VisitStat> selectFullList(@Nullable Long siteId, @Nullable Short type,
                                          String begin, String end) {
        VisitStatArgs args = VisitStatArgs.of().siteId(siteId).type(type).begin(begin).end(end).orderByDateString();
        return VisitStat.fillEmptyDate(selectList(args), begin, end, DAY_FORMATTER, DAY_DISPLAY_FORMATTER,
                date -> date.plusDays(1), VisitStat::new);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteBeforeDateString(String dataString) {
        mapper.deleteBeforeDateString(dataString);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteByDateString(String dateString, @Nullable Short type) {
        mapper.deleteByDateString(dateString, type);
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