package com.ujcms.cms.ext.domain;

import com.ujcms.cms.ext.domain.generated.GeneratedSurveyFeedback;
import org.springframework.lang.Nullable;

/**
 * 调查问卷反馈 实体类
 *
 * @author PONY
 */
public class SurveyFeedback extends GeneratedSurveyFeedback {

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