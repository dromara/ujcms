package com.ujcms.cms.ext.service.args;

import com.ujcms.common.query.BaseQueryArgs;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Vote 查询参数
 *
 * @author Generator
 */
public class VoteArgs extends BaseQueryArgs {
    private VoteArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public VoteArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public VoteArgs enabled(@Nullable Boolean enabled) {
        if (enabled != null) {
            queryMap.put("EQ_enabled_Boolean", enabled);
        }
        return this;
    }

    public VoteArgs withinDate(@Nullable Boolean withinDate) {
        if (withinDate != null) {
            OffsetDateTime now = OffsetDateTime.now();
            if (withinDate) {
                queryMap.put("IsNull_0start_startDate_DateTime", null);
                queryMap.put("GE_0start_startDate_DateTime", now);
                queryMap.put("IsNull_0end_endDate_DateTime", null);
                queryMap.put("LE_0end_endDate_DateTime", now);
            } else {
                queryMap.put("LT_startDate_DateTime", now);
                queryMap.put("GT_endDate_DateTime", now);
            }
        }
        return this;
    }


    public static VoteArgs of() {
        return of(new HashMap<>(16));
    }

    public static VoteArgs of(Map<String, Object> queryMap) {
        return new VoteArgs(queryMap);
    }
}