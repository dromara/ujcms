package com.ujcms.cms.ext.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * ImportData 查询参数
 *
 * @author Generator
 */
public class ImportDataArgs extends BaseQueryArgs {
    private ImportDataArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public ImportDataArgs tableName(@Nullable String tableName) {
        if (StringUtils.isNotBlank(tableName)) {
            queryMap.put("EQ_tableName", tableName);
        }
        return this;
    }

    public ImportDataArgs type(@Nullable Short type) {
        if (type != null) {
            queryMap.put("EQ_type_Short", type);
        }
        return this;
    }

    public static ImportDataArgs of() {
        return of(new HashMap<>(16));
    }

    public static ImportDataArgs of(Map<String, Object> queryMap) {
        return new ImportDataArgs(queryMap);
    }
}