package com.ujcms.cms.ext.domain;

import com.ujcms.cms.ext.domain.base.SurveyItemFeedbackBase;

import java.io.Serializable;

/**
 * 调查问卷条目与调查反馈 关联实体类
 *
 * @author PONY
 */
public class SurveyItemFeedback extends SurveyItemFeedbackBase implements Serializable {
    private static final long serialVersionUID = 1L;
    private SurveyFeedback feedback = new SurveyFeedback();

    public SurveyItemFeedback() {
    }

    public SurveyItemFeedback(Long surveyItemId, Long surveyFeedbackId, Long surveyId, String answer) {
        setSurveyItemId(surveyItemId);
        setSurveyFeedbackId(surveyFeedbackId);
        setSurveyId(surveyId);
        setAnswer(answer);
    }

    public SurveyFeedback getFeedback() {
        return feedback;
    }

    public void setFeedback(SurveyFeedback feedback) {
        this.feedback = feedback;
    }
}