package com.ujcms.cms.ext.domain;

import com.ujcms.cms.ext.domain.base.SurveyOptionFeedbackBase;

import java.io.Serializable;

/**
 * 调查问卷选项与调查反馈 关联实体类
 *
 * @author PONY
 */
public class SurveyOptionFeedback extends SurveyOptionFeedbackBase implements Serializable {
    private static final long serialVersionUID = 1L;
    private SurveyFeedback feedback = new SurveyFeedback();

    public SurveyOptionFeedback() {
    }

    public SurveyOptionFeedback(Long surveyOptionId, Long surveyFeedbackId, Long surveyId, Long surveyItemId) {
        setSurveyOptionId(surveyOptionId);
        setSurveyFeedbackId(surveyFeedbackId);
        setSurveyId(surveyId);
        setSurveyItemId(surveyItemId);
    }

    public SurveyFeedback getFeedback() {
        return feedback;
    }

    public void setFeedback(SurveyFeedback feedback) {
        this.feedback = feedback;
    }
}