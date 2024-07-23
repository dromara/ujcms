package com.ujcms.cms.ext.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import java.util.HashMap;
import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * SurveyFeedback 查询参数
 *
 * @author Generator
 */
public class SurveyFeedbackArgs extends BaseQueryArgs {
    private SurveyFeedbackArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public SurveyFeedbackArgs siteId(@Nullable Integer siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public static SurveyFeedbackArgs of() {
        return of(new HashMap<>(16));
    }

    public static SurveyFeedbackArgs of(Map<String, Object> queryMap) {
        return new SurveyFeedbackArgs(queryMap);
    }
}