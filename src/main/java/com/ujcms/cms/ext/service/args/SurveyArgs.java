package com.ujcms.cms.ext.service.args;

import com.ujcms.commons.query.BaseQueryArgs;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * 调查问卷 查询参数
 *
 * @author Generator
 */
public class SurveyArgs extends BaseQueryArgs {
    private SurveyArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public SurveyArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public SurveyArgs enabled(@Nullable Boolean enabled) {
        if (enabled != null) {
            queryMap.put("EQ_enabled_Boolean", enabled);
        }
        return this;
    }

    public SurveyArgs withinDate(@Nullable Boolean withinDate) {
        if (withinDate != null) {
            OffsetDateTime now = OffsetDateTime.now();
            if (withinDate) {
                queryMap.put("IsNull_1_beginDate_DateTime", null);
                queryMap.put("GE_1_beginDate_DateTime", now);
                queryMap.put("IsNull_2_endDate_DateTime", null);
                queryMap.put("LE_2_endDate_DateTime", now);
            } else {
                queryMap.put("LT_beginDate_DateTime", now);
                queryMap.put("GT_endDate_DateTime", now);
            }
        }
        return this;
    }

    public static SurveyArgs of() {
        return of(new HashMap<>(16));
    }

    public static SurveyArgs of(Map<String, Object> queryMap) {
        return new SurveyArgs(queryMap);
    }
}