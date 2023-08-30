package com.ujcms.cms.ext.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.service.SeqService;
import com.ujcms.cms.ext.domain.SurveyFeedback;
import com.ujcms.cms.ext.domain.SurveyItemFeedback;
import com.ujcms.cms.ext.domain.SurveyOptionFeedback;
import com.ujcms.cms.ext.domain.base.SurveyFeedbackBase;
import com.ujcms.cms.ext.domain.base.SurveyItemFeedbackBase;
import com.ujcms.cms.ext.domain.base.SurveyOptionFeedbackBase;
import com.ujcms.cms.ext.mapper.SurveyFeedbackMapper;
import com.ujcms.cms.ext.mapper.SurveyItemFeedbackMapper;
import com.ujcms.cms.ext.mapper.SurveyOptionFeedbackMapper;
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
    private final SeqService seqService;
    private final SurveyItemFeedbackMapper itemMapper;
    private final SurveyOptionFeedbackMapper optionMapper;
    private final SurveyFeedbackMapper mapper;

    public SurveyFeedbackService(SeqService seqService,
                                 SurveyItemFeedbackMapper itemMapper, SurveyOptionFeedbackMapper optionMapper,
                                 SurveyFeedbackMapper mapper) {
        this.seqService = seqService;
        this.itemMapper = itemMapper;
        this.optionMapper = optionMapper;
        this.mapper = mapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(SurveyFeedback bean) {
        bean.setId(seqService.getNextVal(SurveyFeedbackBase.TABLE_NAME));
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insertItem(SurveyItemFeedback bean) {
        bean.setId(seqService.getNextVal(SurveyItemFeedbackBase.TABLE_NAME));
        itemMapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insertOption(SurveyOptionFeedback bean) {
        bean.setId(seqService.getNextVal(SurveyOptionFeedbackBase.TABLE_NAME));
        optionMapper.insert(bean);
    }

    public boolean existsBy(@Nullable Integer surveyId, @Nullable OffsetDateTime date,
                            @Nullable Integer userId, @Nullable String ip, @Nullable Long cookie) {
        return PageMethod.offsetPage(0, 1, false).<Number>doSelectPage(() ->
                mapper.countBy(surveyId, date, userId, ip, cookie)).iterator().next().intValue() > 0;
    }
}