package com.ujcms.cms.core.service.args;

import com.ujcms.commons.query.BaseQueryArgs;

import java.util.HashMap;
import java.util.Map;

/**
 * 登录日志查询参数
 *
 * @author PONY
 */
public class LoginLogArgs extends BaseQueryArgs {
    public static LoginLogArgs of() {
        return of(new HashMap<>(16));
    }

    public static LoginLogArgs of(Map<String, Object> queryMap) {
        return new LoginLogArgs(queryMap);
    }

    private LoginLogArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }
}
