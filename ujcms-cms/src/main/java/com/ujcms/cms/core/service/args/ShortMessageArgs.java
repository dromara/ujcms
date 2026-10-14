package com.ujcms.cms.core.service.args;

import com.ujcms.common.query.BaseQueryArgs;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 短信查询参数
 *
 * @author PONY
 */
public class ShortMessageArgs extends BaseQueryArgs {
    public ShortMessageArgs receiver(String receiver) {
        queryMap.put("EQ_receiver", receiver);
        return this;
    }

    public ShortMessageArgs usage(short usage) {
        queryMap.put("EQ_usage_Short", usage);
        return this;
    }

    public ShortMessageArgs status(short status) {
        queryMap.put("EQ_status_Short", status);
        return this;
    }

    public ShortMessageArgs geSendDate(OffsetDateTime begin) {
        queryMap.put("GE_sendDate_DateTime", begin);
        return this;
    }

    public static ShortMessageArgs of() {
        return of(new HashMap<>(16));
    }

    public static ShortMessageArgs of(Map<String, Object> queryMap) {
        return new ShortMessageArgs(queryMap);
    }

    private ShortMessageArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }
}
