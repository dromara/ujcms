package com.ujcms.cms.ext.service;

import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.ext.mapper.SurveyFeedbackMapper;
import com.ujcms.cms.ext.mapper.SurveyItemFeedbackMapper;
import com.ujcms.cms.ext.mapper.SurveyOptionFeedbackMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 调查反馈的持久层维护 Service（核心）：反馈表外键引用问卷/条目/选项，问卷定义的写入路径（{@link SurveyService}）
 * 与站点/用户删除都需要级联清理反馈——这些清理不属于任何版本的功能面，与 schema 一同留在核心，
 * 各版本数据库保持一致（升级无需补导）。投票写入、去重查询与结果分页属于调查问卷功能（T2），
 * 在 {@code SurveyFeedbackT2Service}。
 *
 * @author PONY
 */
@Service
public class SurveyFeedbackService implements SiteDeleteListener, UserDeleteListener {
    private final SurveyItemFeedbackMapper itemMapper;
    private final SurveyOptionFeedbackMapper optionMapper;
    private final SurveyFeedbackMapper mapper;

    public SurveyFeedbackService(SurveyItemFeedbackMapper itemMapper, SurveyOptionFeedbackMapper optionMapper,
                                 SurveyFeedbackMapper mapper) {
        this.itemMapper = itemMapper;
        this.optionMapper = optionMapper;
        this.mapper = mapper;
    }

    /**
     * 问卷是否已有反馈记录（演示数据移除守卫：有反馈的问卷只清来源标记不删）
     */
    public boolean existsBySurveyId(Long surveyId) {
        return mapper.countBy(surveyId, null, null, null, null) > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteBySurveyId(Long surveyId) {
        optionMapper.deleteBySurveyId(surveyId, Collections.emptyList());
        itemMapper.deleteBySurveyId(surveyId, Collections.emptyList());
        return mapper.deleteBySurveyId(surveyId);
    }

    /**
     * 删除问卷下不在 {@code itemIds} 内的条目的反馈（条目按缺席删除时先行清理）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteItemBySurveyId(Long surveyId, List<Long> itemIds) {
        optionMapper.deleteBySurveyId(surveyId, itemIds);
        itemMapper.deleteBySurveyId(surveyId, itemIds);
    }

    /**
     * 删除条目下不在 {@code optionIds} 内的选项的反馈
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteOptionByItemId(Long itemId, List<Long> optionIds) {
        optionMapper.deleteByItemId(itemId, optionIds);
    }

    @Override
    public int deleteListenerOrder() {
        // 反馈表外键引用问卷/题目/选项，须先于问卷(200)删除
        return 300;
    }

    @Override
    public void preSiteDelete(Long siteId) {
        optionMapper.deleteBySiteId(siteId);
        itemMapper.deleteBySiteId(siteId);
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public void preUserDelete(Long userId) {
        optionMapper.deleteByUserId(userId);
        itemMapper.deleteByUserId(userId);
        mapper.deleteByUserId(userId);
    }
}
