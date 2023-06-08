package com.ujcms.cms.core.service.args;

import com.ujcms.commons.query.BaseQueryArgs;

import java.util.HashMap;
import java.util.Map;

/**
 * 短信查询参数
 *
 * @author PONY
 */
public class ShortMessageArgs extends BaseQueryArgs {
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
