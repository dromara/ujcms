package com.ujcms.cms.ext.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.ext.domain.SurveyFeedback;
import com.ujcms.cms.ext.domain.SurveyItemFeedback;
import com.ujcms.cms.ext.domain.SurveyOptionFeedback;
import com.ujcms.cms.ext.mapper.SurveyFeedbackMapper;
import com.ujcms.cms.ext.mapper.SurveyItemFeedbackMapper;
import com.ujcms.cms.ext.mapper.SurveyOptionFeedbackMapper;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 调查反馈 实体类
 *
 * @author PONY
 */
@Service
public class SurveyFeedbackService {
    private final SnowflakeSequence snowflakeSequence;
    private final SurveyItemFeedbackMapper itemMapper;
    private final SurveyOptionFeedbackMapper optionMapper;
    private final SurveyFeedbackMapper mapper;

    public SurveyFeedbackService(
            SnowflakeSequence snowflakeSequence, SurveyItemFeedbackMapper itemMapper, SurveyOptionFeedbackMapper optionMapper,
            SurveyFeedbackMapper mapper) {
        this.snowflakeSequence = snowflakeSequence;
        this.itemMapper = itemMapper;
        this.optionMapper = optionMapper;
        this.mapper = mapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(SurveyFeedback bean) {
        bean.setId(snowflakeSequence.nextId());
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insertItem(SurveyItemFeedback bean) {
        bean.setId(snowflakeSequence.nextId());
        itemMapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insertOption(SurveyOptionFeedback bean) {
        bean.setId(snowflakeSequence.nextId());
        optionMapper.insert(bean);
    }

    public boolean existsBy(@Nullable Long surveyId, @Nullable OffsetDateTime date,
                            @Nullable Long userId, @Nullable String ip, @Nullable Long cookie) {
        return PageMethod.offsetPage(0, 1, false).<Number>doSelectPage(() ->
                mapper.countBy(surveyId, date, userId, ip, cookie)).iterator().next().intValue() > 0;
    }
}