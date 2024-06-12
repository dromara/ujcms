package com.ujcms.cms.ext.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.ext.domain.Survey;
import com.ujcms.cms.ext.domain.SurveyFeedback;
import com.ujcms.cms.ext.domain.SurveyItemFeedback;
import com.ujcms.cms.ext.domain.SurveyOptionFeedback;
import com.ujcms.cms.ext.domain.base.SurveyBase;
import com.ujcms.cms.ext.mapper.SurveyMapper;
import com.ujcms.cms.ext.mapper.SurveyOptionMapper;
import com.ujcms.cms.ext.service.args.SurveyArgs;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 调查问卷 Service
 *
 * @author PONY
 */
@Service
public class SurveyService {
    private final SurveyFeedbackService feedbackService;
    private final SurveyOptionMapper optionMapper;
    private final SurveyMapper mapper;

    public SurveyService(SurveyFeedbackService feedbackService, SurveyOptionMapper optionMapper, SurveyMapper mapper) {
        this.feedbackService = feedbackService;
        this.optionMapper = optionMapper;
        this.mapper = mapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void cast(Long id, Map<Long, List<Long>> optionMap, Map<Long, String> essayMap,
                     Long siteId, @Nullable Long userId, String ip, long cookie) {
        mapper.cast(id);
        Survey survey = Optional.ofNullable(select(id)).orElseThrow(() ->
                new IllegalStateException("Survey not found. ID: " + id));
        SurveyFeedback feedback = new SurveyFeedback(survey.getId(), siteId, userId, ip, cookie);
        feedbackService.insert(feedback);
        for (Map.Entry<Long, List<Long>> option : optionMap.entrySet()) {
            Long itemId = option.getKey();
            optionMapper.cast(itemId, option.getValue());
            option.getValue().forEach(optionId -> feedbackService.insertOption(
                    new SurveyOptionFeedback(optionId, feedback.getId(), survey.getId(), itemId)));
        }
        for (Map.Entry<Long, String> essay : essayMap.entrySet()) {
            feedbackService.insertItem(new SurveyItemFeedback(essay.getKey(), feedback.getId(),
                    survey.getId(), essay.getValue()));
        }
    }

    @Nullable
    public Survey select(Long id) {
        return mapper.select(id);
    }

    public List<Survey> selectList(SurveyArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), SurveyBase.TABLE_NAME, "order_desc,id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<Survey> selectList(SurveyArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Survey> selectPage(SurveyArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

}