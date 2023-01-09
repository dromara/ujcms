package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.ArticleBuffer;
import com.ujcms.cms.core.domain.ArticleStat;
import com.ujcms.cms.core.mapper.ArticleBufferMapper;
import com.ujcms.cms.core.mapper.ArticleStatMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 文章缓冲 Service
 *
 * @author PONY
 */
@Service
public class ArticleBufferService {
    private final ArticleStatMapper statMapper;
    private final ArticleBufferMapper mapper;
    private final SeqService seqService;

    public ArticleBufferService(ArticleStatMapper statMapper, ArticleBufferMapper mapper, SeqService seqService) {
        this.statMapper = statMapper;
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(ArticleBuffer bean) {
        bean.setId(seqService.getNextVal(ArticleBuffer.TABLE_NAME));
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ArticleBuffer bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public long updateViews(Integer id, int viewsToPlus) {
        ArticleBuffer buffer = mapper.select(id);
        if (buffer == null) {
            return 0;
        }
        long views = buffer.getViews() + viewsToPlus;
        int nowDay = (int) LocalDate.now().toEpochDay();
        if (nowDay == buffer.getStatDay()) {
            mapper.plusViews(id, viewsToPlus);
            return views;
        }
        int weekDay = 8, monthDay = 31, quarterDay = 91, yearDay = 366;
        List<Integer> statDays = Arrays.asList(weekDay, monthDay, quarterDay, yearDay);
        List<ArticleStat> historyList = statMapper.listByStatDay(buffer.getId(), statDays);
        int dayViews = buffer.getDayViews();
        buffer.setViews(buffer.getViews() + viewsToPlus);
        buffer.setDayViews(viewsToPlus);
        buffer.setWeekViews(buffer.getWeekViews() - ArticleStat.getExpiredViews(historyList, weekDay) + dayViews);
        buffer.setMonthViews(buffer.getMonthViews() - ArticleStat.getExpiredViews(historyList, monthDay) + dayViews);
        buffer.setQuarterViews(
                buffer.getQuarterViews() - ArticleStat.getExpiredViews(historyList, quarterDay) + dayViews);
        buffer.setYearViews(buffer.getYearViews() - ArticleStat.getExpiredViews(historyList, yearDay) + dayViews);
        buffer.setStatDay(nowDay);
        mapper.update(buffer);
        statMapper.deleteByStatDay(buffer.getId(), yearDay);
        statMapper.insert(new ArticleStat(buffer.getId(), nowDay - 1, dayViews));
        return views;
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public ArticleBuffer select(Integer id) {
        return mapper.select(id);
    }
}