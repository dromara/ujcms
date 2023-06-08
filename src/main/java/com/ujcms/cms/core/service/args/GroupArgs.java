package com.ujcms.cms.core.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户组查询参数
 *
 * @author PONY
 */
public class GroupArgs extends BaseQueryArgs {
    public GroupArgs allAccessPermission(@Nullable Boolean allAccessPermission) {
        if (allAccessPermission != null) {
            queryMap.put("EQ_allAccessPermission_Boolean", allAccessPermission);
        }
        return this;
    }

    public GroupArgs type(@Nullable Short type) {
        if (type != null) {
            queryMap.put("EQ_type_Short", type);
        }
        return this;
    }

    public static GroupArgs of() {
        return of(new HashMap<>(16));
    }

    public static GroupArgs of(Map<String, Object> queryMap) {
        return new GroupArgs(queryMap);
    }

    private GroupArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }
}
