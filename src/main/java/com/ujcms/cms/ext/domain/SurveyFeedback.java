package com.ujcms.cms.ext.domain;

import com.ujcms.cms.ext.domain.base.SurveyFeedbackBase;
import org.springframework.lang.Nullable;

import java.io.Serializable;

/**
 * 调查问卷反馈 实体类
 *
 * @author PONY
 */
public class SurveyFeedback extends SurveyFeedbackBase implements Serializable {
    private static final long serialVersionUID = 1L;

    public SurveyFeedback() {
    }

    public SurveyFeedback(Long surveyId, Long siteId, @Nullable Long userId, String ip, Long cookie) {
        setSurveyId(surveyId);
        setSiteId(siteId);
        setUserId(userId);
        setIp(ip);
        setCookie(cookie);
    }
}